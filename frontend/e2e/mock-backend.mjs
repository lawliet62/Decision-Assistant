import http from "node:http";
import { randomUUID } from "node:crypto";
import { comparisonFixture, recalculate } from "./comparison-fixture.mjs";

const decisionId = "11111111-1111-1111-1111-111111111111";
const numeric = (id, label, factName, values, unit) => ({ id, label, factName,
  choices: values.map(value => ({ id: String(value), label: `${value === 1000 ? "1TB" : value + unit} 이상` })) });
const support = (id, label, factName) => ({ id, label, factName, choices: [{ id: "required", label: "지원 필수" }] });
const storage = numeric("storage", "저장 공간", "저장공간", [128, 256, 512], "GB");
const refresh = numeric("refreshRate", "화면 주사율", "주사율", [60, 90, 120], "Hz");
const minimumTemplates = {
  LAPTOP: [numeric("memory", "메모리", "메모리", [8, 16, 32], "GB"), numeric("storage", "저장 공간", "저장공간", [256, 512, 1000], "GB")],
  PHONE: [storage, refresh],
  TABLET: [storage, refresh, support("pen", "전용 펜 지원", "펜 지원")],
  MONITOR: [{ id: "resolution", label: "해상도", factName: "해상도", choices: [{ id: "fhd", label: "FHD 이상" }, { id: "qhd", label: "QHD 이상" }, { id: "uhd", label: "4K UHD 이상" }] }, numeric("refreshRate", "화면 주사율", "주사율", [60, 120, 144, 165], "Hz")],
  EARBUDS: [support("anc", "ANC 지원", "ANC 지원")],
};
const templates = [
  ["LAPTOP", "노트북", "학업", 1000000, "Windows", ["가격", "CPU/GPU", "메모리", "배터리"]],
  ["PHONE", "휴대폰", "일상", 500000, "Android", ["가격", "OS", "화면", "배터리"]],
  ["TABLET", "태블릿", "필기", 300000, "iPadOS", ["가격", "화면", "펜 지원", "무게"]],
  ["MONITOR", "모니터", "사무", 200000, "HDMI", ["가격", "크기", "해상도", "주사율"]],
  ["EARBUDS", "무선 이어폰", "출퇴근", 100000, "iOS", ["가격", "형태", "ANC 지원", "무게"]],
].map(([key, label, use, budget, compatibility, facts]) => ({ key, label, version: "intake-v2", uses: [use, key === "EARBUDS" ? "운동" : key === "TABLET" ? "그림" : "게임"], budgets: [budget], compatibility: [compatibility], facts: [...new Set([...facts, ...minimumTemplates[key].map(item => item.factName)])], minimumRequirements: minimumTemplates[key] }));
const criteria = ["가격", "음질", "착용감", "노이즈 캔슬링", "배터리 적합도"].map((name, index) => ({
  id: `33333333-3333-3333-3333-33333333333${index}`, name,
  kind: index === 0 ? "NUMERIC_UTILITY" : "RATING_1_TO_5",
  direction: index === 0 ? "LOWER_BETTER" : "NOT_APPLICABLE", weight: 1, active: true,
  worstAnchor: index === 0 ? 500000 : undefined, bestAnchor: index === 0 ? 50000 : undefined,
  unit: index === 0 ? "KRW" : undefined,
}));
const questions = [
  ["NUMBER", "context.budget", "이번 구매의 최대 예산은 얼마인가요?"],
  ["SINGLE_CHOICE", "priority", "이번 결정에서 가장 중요한 기준은 무엇인가요?"],
  ["TEXT", "context.avoid", "반드시 피하고 싶은 단점이나 조건이 있나요?"],
].map(([kind, targetKey, prompt]) => ({ id: randomUUID(), kind, targetKey, prompt,
  impact: "비교 참고 정보입니다.", options: kind === "SINGLE_CHOICE" ? criteria.map(item => item.name) : [], status: "OPEN" }));
let current;
let listFixture;
let discoveryHistoryCandidates = [];
let pendingCandidates;
const option = (source) => ({ id: randomUUID(), name: source.name || "후보 · example.com", sourceUrl: source.url });
function create(body) {
  return { id: decisionId, version: 0, title: body.intentText, templateKey: body.templateKey,
    templateVersion: "earbuds-v1", status: "DRAFT", context: { intendedUse: body.intentText, currency: "KRW" },
    options: (body.selectedSources || body.sourceUrls.map(url => ({ url }))).map(option),
    criteria, values: [], constraints: [
      { id: "budget", name: "최대 예산", kind: "NUMERIC_MAX", criterionId: criteria[0].id, unit: "KRW", active: false },
      { id: "compatibility", name: "필수 호환성", kind: "MANUAL", active: false },
    ], constraintResults: [], resolutions: [], outcomes: [],
    guidance: { intentText: body.intentText, aiConsent: false, webConsent: Boolean(body.webConsent),
      processingStatus: body.intentText.includes("slow") ? "IMPORTING" : "NEEDS_INPUT", readiness: "PROVISIONAL",
      summary: "비교할 제품의 정보와 출처를 확인하세요.", questions: structuredClone(questions),
      evidence: [], candidateSuggestions: [], appliedPreferences: [], reminders: [] },
    createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() };
}
function createDiscovery(body) {
  const catalog = body.discovery.templateVersion === "intake-catalog-v1";
  const template = templates.find(item => item.key === body.templateKey);
  const result = create({ ...body, intentText: template.label + " 비교", selectedSources: [] });
  result.discovery = { version: 1, status: "PENDING", policyVersion: "official-kr-v2", generation: 0, displayedGeneration: 0, history: [], conditions: body.discovery, updatedAt: new Date().toISOString() };
  if (catalog) result.discovery.policyVersion = "catalog-v1";
  result.productFacts = [];
  result.guidance.questions = [];
  result.guidance.candidateSuggestions = ["Alpha", "Beta", "Gamma"].map((name, index) => ({
    id: randomUUID(), name: template.label + " " + name, url: "https://example.com/" + name, reason: "주요 정보를 확인하고 비교하세요.", status: "PROPOSED",
    facts: [{ name: "가격", value: (100000 + index * 10000) + " KRW", sourceUrl: "https://example.com/" + name, quote: name + " 제품 기본 구성 가격", fetchedAt: new Date().toISOString(), verificationStatus: "UNREVIEWED" }],
  }));
  // 브라우저 테스트의 고정 사례: 충족 / 미달 / 근거 없음. 실제 판정기는 backend 테스트에서 검증한다.
  result.guidance.candidateSuggestions.forEach((candidate, index) => {
    const actuals = index === 0
      ? { memory: "32 GB", storage: "1 TB", refreshRate: "165 Hz", resolution: "4K UHD", anc: "지원", pen: "전용 펜 지원" }
      : { memory: "4 GB", storage: "64 GB", refreshRate: "30 Hz", resolution: "1280x720", anc: "미지원", pen: "미지원" };
    candidate.requirementChecks = template.minimumRequirements.filter(item => body.discovery.minimumRequirements?.[item.id]).map(item => {
      const fact = { name: item.factName, value: actuals[item.id], sourceUrl: candidate.url, quote: candidate.name + " " + item.factName + " " + actuals[item.id], fetchedAt: new Date().toISOString(), verificationStatus: "UNREVIEWED" };
      if (index < 2) candidate.facts.push(fact);
      return { id: item.id, label: item.label, requiredValue: item.choices.find(choice => choice.id === body.discovery.minimumRequirements[item.id]).label,
        status: ["MEETS", "BELOW", "UNKNOWN"][index], facts: index < 2 ? [fact] : [] };
    });
    candidate.officialOffer = { purchaseUrl: `https://qcystore.co.kr/product/fixture/${100 + index}/`, seller: "QCY 공식몰", price: 100000 + index * 10000, currency: "KRW", imageUrl: index === 0 ? "https://qcystore.co.kr/web/product/fixture.jpg" : undefined, fetchedAt: new Date().toISOString(), policyVersion: "official-kr-v2" };
    if (body.discovery.compatibility) {
      candidate.compatibilityCheck = { id: "compatibility", label: "기본 연결·사용 호환성", requiredValue: body.discovery.compatibility,
        status: ["MEETS", "BELOW", "UNKNOWN"][index], facts: [], unknownReason: index === 2 ? "MISSING" : undefined };
      candidate.reason = index === 2 ? "기본 연결·사용 호환성을 수집 근거로 확인하지 못했습니다." : "조건별 지원 근거와 제한 사항을 확인하세요.";
    }
    candidate.sourceKind = "OFFICIAL_STORE";
    candidate.recommendationGroup = candidate.requirementChecks.length || candidate.compatibilityCheck ? ["RECOMMENDED", "BELOW", "UNKNOWN"][index] : "RECOMMENDED";
    if (catalog) {
      candidate.officialOffer.policyVersion = "catalog-v1";
      candidate.priceStatus = index === 2 ? "EXPIRED" : "VERIFIED";
      if (index === 2) { candidate.officialOffer.fetchedAt = "2020-01-01T00:00:00Z"; candidate.recommendationGroup = "UNKNOWN"; }
    }
  });
  return result;
}
const server = http.createServer(async (request, response) => {
  const send = (status, body, headers = {}) => { response.writeHead(status, { "content-type": "application/json", ...headers }); response.end(JSON.stringify(body)); };
  let body = {};
  if (request.method !== "GET") { let raw = ""; for await (const chunk of request) raw += chunk; body = raw ? JSON.parse(raw) : {}; }
  const url = new URL(request.url, "http://localhost");
  if (request.method === "POST" && url.pathname === "/__test/decision-list") {
    listFixture = Array.from({ length: body.empty ? 0 : 15 }, (_, i) => ({
      id: `99999999-9999-9999-9999-${String(i).padStart(12, "0")}`, version: 0,
      title: `결정 ${String(i + 1).padStart(2, "0")}`, templateKey: i % 2 ? "LAPTOP" : "EARBUDS",
      status: i < 11 ? "DRAFT" : i < 13 ? "CHOSEN" : i === 13 ? "DEFERRED" : "ABANDONED",
      createdAt: new Date(Date.UTC(2026, 0, i + 1)).toISOString(), updatedAt: new Date(Date.UTC(2026, 1, i + 1)).toISOString(),
    }));
    return send(200, { ok: true });
  }
  if (request.method === "GET" && url.pathname === "/api/v1/decisions/counts") {
    const rows = (listFixture ?? (current ? [current] : [])).filter(d => !d.deletedAt && !d.archivedAt);
    return send(200, ["DRAFT", "CHOSEN", "DEFERRED", "ABANDONED", "ARCHIVED"].map(status => ({ status, count: rows.filter(d => d.status === status).length })));
  }
  if (request.method === "GET" && url.pathname === "/api/v1/decisions/selection") {
    const selected = url.searchParams.getAll("statuses").flatMap(s => s.split(","));
    return send(200, (listFixture ?? (current ? [current] : [])).filter(d => !d.deletedAt && !d.archivedAt && (!selected.length || selected.includes(d.status))));
  }
  if (request.method === "GET" && url.pathname === "/api/v1/decisions") {
    const selected = url.searchParams.getAll("statuses").flatMap(s => s.split(","));
    const rows = (listFixture ?? (current ? [current] : [])).filter(d => !d.deletedAt && !d.archivedAt && (!selected.length || selected.includes(d.status)));
    const field = { UPDATED_AT: "updatedAt", CREATED_AT: "createdAt", TITLE: "title", TEMPLATE_KEY: "templateKey" }[url.searchParams.get("sort")] ?? "updatedAt";
    const factor = url.searchParams.get("direction") === "ASC" ? 1 : -1;
    rows.sort((a, b) => factor * String(a[field]).localeCompare(String(b[field])) || a.id.localeCompare(b.id));
    const size = Number(url.searchParams.get("size") ?? 10);
    const totalPages = Math.ceil(rows.length / size);
    const page = Math.max(0, Math.min(Number(url.searchParams.get("page") ?? 0), totalPages - 1));
    return send(200, { items: rows.slice(page * size, (page + 1) * size), page, size, totalElements: rows.length, totalPages });
  }
  if (url.pathname === "/api/v1/decisions/recovery-notice") return send(200, { restoredTo: null });
  if (url.pathname === "/api/v1/decisions/management") {
    const view = url.searchParams.get("view");
    const rows = (listFixture ?? []).filter(d => view === "TRASH" ? d.deletedAt : !d.deletedAt && (view === "ARCHIVE" ? d.archivedAt : !d.archivedAt));
    const page = Number(url.searchParams.get("page") ?? 0);
    return send(200, { items: rows.slice(page * 10, page * 10 + 10).map(d => ({ ...d, personalizationEnabled: d.personalizationEnabled ?? true, recoverable: !!d.deletedAt, recoverableUntil: d.deletedAt ? new Date(Date.parse(d.deletedAt) + 30 * 86400000).toISOString() : null })), page, hasNext: rows.length > page * 10 + 10 });
  }
  if (request.method === "PATCH" && url.pathname.endsWith("/retention")) {
    const row = listFixture?.find(d => url.pathname === `/api/v1/decisions/${d.id}/retention`);
    if (!row) return send(404, {});
    if (row.version !== body.expectedVersion) return send(409, {});
    if (body.action === "ARCHIVE") row.archivedAt = new Date().toISOString();
    if (body.action === "UNARCHIVE") row.archivedAt = null;
    if (body.action === "RESTORE") row.deletedAt = null;
    if (body.action === "PERSONALIZATION_ON") row.personalizationEnabled = true;
    if (body.action === "PERSONALIZATION_OFF") row.personalizationEnabled = false;
    row.version++;
    return send(200, row);
  }
  const listRow = listFixture?.find(d => !d.deletedAt && url.pathname === `/api/v1/decisions/${d.id}`);
  if (listRow && request.method === "GET") return send(200, { ...create({ intentText: listRow.title, templateKey: listRow.templateKey, selectedSources: [] }), ...listRow });
  if (request.method === "DELETE" && url.pathname.startsWith("/api/v1/decisions/")) {
    if (!listRow) return send(404, { detail: "결정을 찾을 수 없습니다." });
    if (Number(url.searchParams.get("expectedVersion")) !== listRow.version) return send(409, { detail: "결정이 변경되었습니다." });
    listRow.deletedAt = new Date().toISOString();
    listRow.version++;
    return send(204);
  }
  if (request.url === "/api/v1/me") return send(200, { id: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", email: "user@example.com" }, { "set-cookie": "XSRF-TOKEN=test-token; Path=/; SameSite=Lax" });
  if (request.url === "/api/v1/intake-templates") return send(200, templates);
  if (request.url === "/api/v1/product-lookups/capabilities") return send(200, {
    searchEnabled: true, searchProvider: "Serper", searchNotice: "입력한 제품명을 Serper로 전송하는 데 동의합니다.",
    importEnabled: true, importProvider: "Firecrawl", importNotice: "선택한 공개 URL을 Firecrawl로 보내 정보를 가져오는 데 동의합니다.",
    discoveryPolicy: { version: "official-kr-v2", scope: "KOREAN_OFFICIAL_STORES", priceBasis: "OBSERVED_KRW_PRICE" },
  });
  if (request.method === "POST" && request.url === "/api/v1/product-lookups") {
    if (!body.searchConsent) return send(400, { detail: "Consent required" });
    if (body.query === "limit") return send(429, { detail: "이번 달 검색 한도에 도달했습니다. URL 직접 추가를 이용하세요." });
    return send(200, { results: body.query === "none" ? [] : [
      { title: "Buds Pro", url: "https://example.com/buds-pro", description: "Pro 모델 제품 페이지" },
      { title: "Buds 2", url: "https://example.com/buds-2", description: "2세대 제품 페이지" },
      { title: "Buds Lite", url: "https://example.com/buds-lite", description: "Lite 모델 제품 페이지" },
    ] });
  }
  if (request.method === "POST" && request.url === "/api/v1/decisions") {
    listFixture = undefined;
    current = body.discovery ? createDiscovery(body) : create(body);
    if (body.title === "조건 비교 브라우저 검증 (가상제품)") comparisonFixture(current);
    discoveryHistoryCandidates = current.guidance.candidateSuggestions; pendingCandidates = undefined;
    if (body.webConsent) current.guidance.evidence = current.options.map(item => ({
      id: randomUUID(), optionId: item.id, url: item.sourceUrl, status: "FAILED", error: "수집 실패", stale: false,
    }));
    return send(201, current);
  }
  if (request.method === "GET" && request.url === `/api/v1/decisions/${decisionId}`) {
    if (current?.discovery?.status === "PENDING") {
      current.discovery.status = "SUCCEEDED";
      if (pendingCandidates) {
        current.guidance.candidateSuggestions = pendingCandidates;
        discoveryHistoryCandidates = pendingCandidates;
        current.discovery.displayedGeneration = current.discovery.generation;
        pendingCandidates = undefined;
      }
    }
    return send(200, current);
  }
  if (request.url === "/api/v1/ai/capabilities") return send(200, { enabled: true, provider: "gemini", model: "test", requiresSensitiveDataAcknowledgement: true });
  if (!current) return send(404, { detail: "Not found" });
  if (request.method === "PUT" && request.url.endsWith("/comparison")) {
    if (body.expectedVersion !== current.version) return send(409, { detail: "최신 비교를 확인하세요." });
    current.comparison.settings = body.settings; current.version++;
    return send(200, recalculate(current));
  }
  if (request.method === "PUT" && request.url.endsWith("/comparison/confirmation")) {
    if (body.expectedVersion !== current.version) return send(409, { detail: "최신 비교를 확인하세요." });
    current.comparison.confirmations = current.comparison.confirmations.filter(item => item.optionId !== body.optionId || item.feature !== body.feature);
    if (body.supported != null) current.comparison.confirmations.push({ ...body, confirmedAt: new Date().toISOString() });
    current.version++; return send(200, recalculate(current));
  }
  if (request.method === "PATCH" && request.url.includes("/candidate-suggestions/")) {
    const id = request.url.split("/").at(-1);
    const candidate = current.guidance.candidateSuggestions.find(item => item.id === id);
    if (candidate && body.action === "ACCEPT") {
      const added = option(candidate); current.options.push(added);
      current.productFacts.push({ optionId: added.id, facts: candidate.facts, requirementChecks: candidate.requirementChecks, officialOffer: candidate.officialOffer, sourceKind: candidate.sourceKind, compatibilityCheck: candidate.compatibilityCheck });
    }
    if (candidate) candidate.status = body.action === "ACCEPT" ? "ACCEPTED" : "DISMISSED";
    current.guidance.candidateSuggestions = current.guidance.candidateSuggestions.filter(item => item.id !== id);
    current.version++; return send(200, current);
  }
  if (request.method === "POST" && request.url.endsWith("/discovery/retry")) {
    current.discovery = { ...current.discovery, version: current.discovery.version + 1, status: "PENDING", error: null };
    return send(200, current);
  }
  if (request.method === "POST" && request.url.endsWith("/discovery/refresh")) {
    if (body.expectedVersion !== current.discovery.version || ["PENDING", "RUNNING"].includes(current.discovery.status)) return send(409, { detail: "추천 상태가 변경되었습니다." });
    const generation = current.discovery.generation + 1;
    current.discovery.history.push({ generation: current.discovery.displayedGeneration, status: "SUCCEEDED", policyVersion: current.discovery.policyVersion, candidates: structuredClone(discoveryHistoryCandidates) });
    pendingCandidates = createDiscovery({ templateKey: current.templateKey, discovery: current.discovery.conditions }).guidance.candidateSuggestions;
    pendingCandidates.forEach(candidate => { candidate.name += ` 추가 ${generation}`; });
    current.discovery = { ...current.discovery, generation, version: current.discovery.version + 1, status: "PENDING", stage: "SEARCH", error: null };
    return send(200, current);
  }
  if (request.method === "POST" && request.url.endsWith("/sources")) {
    if (!current.options.some(item => item.sourceUrl === body.url)) current.options.push(option(body));
    current.version++; return send(201, current);
  }
  if (request.method === "POST" && request.url.endsWith("/retry")) {
    current.guidance.evidence.forEach(item => { item.status = "SUCCEEDED"; delete item.error; });
    current.version++; return send(200, current);
  }
  if (request.method === "POST" && request.url.endsWith("/answer")) {
    const id = request.url.split("/").at(-2);
    current.guidance.questions = current.guidance.questions.filter(item => item.id !== id);
    current.version++; return send(200, current);
  }
  if (request.method === "PUT" && request.url.endsWith("/draft")) {
    if (body.expectedVersion !== current.version) return send(409, { detail: "최신 내용을 다시 불러오세요." });
    Object.assign(current, body, { version: current.version + 1 });
    current.guidance.readiness = current.values.length === current.options.length * criteria.length ? "VERIFIED_READY" : "PROVISIONAL";
    return send(200, current);
  }
  if (request.method === "POST" && request.url.endsWith("/evaluations")) return send(201, {
    id: randomUUID(), decisionId, decisionVersion: current.version, inputHash: "test-hash", llmStatus: "NOT_REQUESTED",
    algorithmResult: { recommendedOptionId: current.options[0].id, options: current.options.map(item => ({
      optionId: item.id, optionName: item.name, score: 80, feasible: true, contributions: [], failedConstraints: [],
    })) },
  });
  if (request.method === "POST" && request.url.endsWith("/resolutions")) {
    const resolution = { id: randomUUID(), ...body, createdAt: new Date().toISOString() };
    if (current.comparison) resolution.comparisonSnapshot = structuredClone({ schemaVersion: "comparison-v1", capturedAt: resolution.createdAt, options: current.options, criteria: current.criteria, values: current.values, productFacts: current.productFacts, comparison: current.comparison });
    current.status = body.kind; current.resolutions.unshift(resolution); current.version++;
    return send(201, resolution);
  }
  if (request.method === "POST" && request.url.endsWith("/outcome-check-ins")) {
    const outcome = { id: randomUUID(), ...body, createdAt: new Date().toISOString() };
    current.outcomes.unshift(outcome); return send(201, outcome);
  }
  return send(404, { detail: "Not found" });
});
server.listen(18080, "127.0.0.1");
