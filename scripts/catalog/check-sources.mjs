import fs from "node:fs";
import { pathToFileURL } from "node:url";

// 운영자가 승인한 두 QCY 사양 페이지만 점검한다. 일치는 사양의 진실성이나 모델 동일성을 증명하지 않는다.
const sources = new Map([
  ["qcy-t13-anc", "https://www.qcy.com/products/qcy-t13-anc"],
  ["qcy-t13-pro", "https://www.qcy.com/products/qcy-t13-pro"],
]);
const MAX_BYTES = 2_000_000;
const normalize = value => value.replace(/<[^>]*>/g, " ").replace(/&nbsp;|&#160;/gi, " ")
  .replace(/&amp;/gi, "&").replace(/\s+/g, " ").trim().toLowerCase();

export async function inspect(entries, fetchPage = fetch, now = new Date()) {
  const results = [];
  for (const [id, url] of sources) {
    const entry = entries.find(item => item.id === id && item.active);
    if (!entry) continue;
    const facts = entry.facts;
    if (!facts.length) continue;
    let page, failure;
    try {
      if (!facts.some(item => item.evidence.sourceUrl === url)) throw new Error("NO_APPROVED_SOURCE");
      const response = await fetchPage(url, { redirect: "manual", signal: AbortSignal.timeout(15000) });
      if (response.status !== 200) throw new Error("HTTP_" + response.status);
      const length = Number(response.headers.get("content-length") || 0);
      if (length > MAX_BYTES) throw new Error("TOO_LARGE");
      const reader = response.body.getReader();
      const chunks = []; let size = 0;
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        size += value.length;
        if (size > MAX_BYTES) { await reader.cancel(); throw new Error("TOO_LARGE"); }
        chunks.push(value);
      }
      const bytes = new Uint8Array(size); let offset = 0;
      for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
      page = normalize(new TextDecoder().decode(bytes));
    } catch (error) { page = null; failure = /^HTTP_\d{3}$|^TOO_LARGE$/.test(error.message) ? error.message : "NETWORK_OR_TIMEOUT"; }
    for (const item of facts) {
      const fact = item.evidence;
      const dueAt = new Date(Date.parse(fact.fetchedAt) + (item.cycle === "HARDWARE" ? 90 : 30) * 86400000);
      results.push({ productId: id, field: fact.name, source: url, reviewedAt: fact.fetchedAt,
        dueAt: dueAt.toISOString(), due: dueAt <= now, dueSoon: now.getTime() >= dueAt.getTime() - 7 * 86400000,
        observation: fact.sourceUrl !== url ? "UNSUPPORTED_SOURCE" : page === null ? "UNAVAILABLE" : page.includes(normalize(fact.quote)) ? "QUOTE_PRESENT" : "QUOTE_MISSING",
        failure: fact.sourceUrl === url && page === null ? failure : null });
    }
  }
  return results;
}

export function summary(results) {
  const lines = ["## QCY 사양 출처 점검", "", "문구 존재는 모델 동일성·사양 정확성의 검증이 아닙니다. 확인 시각은 갱신하지 않습니다.", ""];
  if (!results.length) lines.push("점검할 등록 사양이 없습니다.");
  for (const row of results) lines.push(`- ${row.productId} / ${row.field}: ${row.observation}${row.failure ? ` (${row.failure})` : ""}, 점검 기한 ${row.dueAt}${row.due ? " (점검 기한 초과)" : row.dueSoon ? " (7일 이내 점검 필요)" : ""} · ${row.source}`);
  return lines.join("\n") + "\n";
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const entries = JSON.parse(fs.readFileSync("backend/src/main/resources/catalog/earbuds.json", "utf8"));
  const results = await inspect(entries);
  const output = summary(results);
  process.stdout.write(output);
  if (process.env.GITHUB_STEP_SUMMARY) fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY, output);
  if (results.some(row => row.dueSoon || row.observation !== "QUOTE_PRESENT")) process.exitCode = 1;
}
