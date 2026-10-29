import fs from "node:fs";
import { pathToFileURL } from "node:url";

const instantMillis = value => typeof value === "number" ? value * 1000 : typeof value === "string" ? Date.parse(value) : NaN;

// DB에서 내보낸 관측만 사용한다. 실패와 미관측 구간을 실제 가격으로 추정하지 않는다.
export function summarize(observations, products = [], now = new Date()) {
  const outcomes = {};
  for (const row of observations) outcomes[row.outcome] = (outcomes[row.outcome] ?? 0) + 1;
  const ordered = [...observations].sort((a, b) => Date.parse(a.attempted_at) - Date.parse(b.attempted_at));
  const cadence = [1, 3, 7].map(days => {
    const state = new Map();
    let references = 0, available = 0, priceMismatches = 0, budgetComparisons = 0, falseBudgetPasses = 0, unavailableReferences = 0, falseAvailable = 0;
    for (const row of ordered) {
      const time = Date.parse(row.attempted_at);
      let item = state.get(row.product_id);
      if (!item || time >= item.next) {
        const prior = item?.price;
        item = { next: time + days * 86400000,
          price: row.outcome === "VERIFIED" ? { value: Number(row.price), at: Date.parse(row.completed_at) } : row.outcome === "COLLECTION_FAILED" ? prior : undefined };
        state.set(row.product_id, item);
      }
      if (row.outcome === "UNAVAILABLE") {
        unavailableReferences++;
        const at = Date.parse(row.completed_at);
        if (item.price && at >= item.price.at && at - item.price.at < 86400000) falseAvailable++;
      }
      if (row.outcome !== "VERIFIED") continue;
      references++;
      // 수집 완료 시점 이후의 가상 조회. 24시간 가격 유효 정책은 주기와 별도로 고정한다.
      const at = Date.parse(row.completed_at);
      if (!item.price || at < item.price.at || at - item.price.at >= 86400000) continue;
      available++;
      if (item.price.value !== Number(row.price)) priceMismatches++;
      for (const budget of [30000, 50000, 100000, 200000]) {
        budgetComparisons++;
        if (item.price.value <= budget && Number(row.price) > budget) falseBudgetPasses++;
      }
    }
    return { days, references, available, priceMismatches, budgetComparisons, falseBudgetPasses, unavailableReferences, falseAvailable };
  });
  const reviewsDue = products.flatMap(product => (product.facts ?? []).filter(f => {
    const original = instantMillis(f.evidence.fetchedAt);
    if (!Number.isFinite(original)) throw new Error(`사양 확인 시각이 잘못됐습니다: ${product.id} / ${f.evidence.name}`);
    const review = product.fact_reviews?.[f.evidence.name];
    // PostgreSQL JSONB의 Instant는 숫자(초) 또는 ISO 문자열일 수 있다.
    const reviewed = instantMillis(review);
    const effective = Number.isFinite(reviewed) && reviewed > original && reviewed <= now.getTime() ? reviewed : original;
    return effective + (f.cycle === "HARDWARE" ? 90 : 30) * 86400000 <= now.getTime();
  })
    .map(f => ({ productId: product.id, field: f.evidence.name, cycle: f.cycle })));
  const times = ordered.map(o => Date.parse(o.attempted_at)).filter(Number.isFinite);
  return { generatedAt: now.toISOString(), attempts: observations.length, outcomes,
    observedSpanDays: times.length < 2 ? 0 : (Math.max(...times) - Math.min(...times)) / 86400000,
    reviewsDue, cadence,
    limitations: ["관측 시점 사이의 변동은 알 수 없습니다.", "갱신에 사용한 관측에서의 일치는 독립 검증이 아닙니다.",
      "수집 실패는 실제 가격·재고 미확인이며 정상 표본 분모에 포함하지 않습니다.", "3일·7일 정책도 가격 유효기간은 24시간입니다. 관측된 가용성과 정확도를 함께 비교하세요.",
      "단기 표본의 불일치 0건은 안전한 주기의 입증이 아닙니다. 수동 점검 시간은 별도로 기록하세요."] };
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  if (!process.argv[2]) throw new Error("사용법: node scripts/catalog/report.mjs observations.json [products.json]");
  const observations = JSON.parse(fs.readFileSync(process.argv[2], "utf8"));
  const products = process.argv[3] ? JSON.parse(fs.readFileSync(process.argv[3], "utf8")) : [];
  console.log(JSON.stringify(summarize(observations, products), null, 2));
}
