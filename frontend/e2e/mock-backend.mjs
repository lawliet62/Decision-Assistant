import http from "node:http";
import { randomUUID } from "node:crypto";

const decisionId = "11111111-1111-1111-1111-111111111111";
const templates = [
  ["LAPTOP", "노트북", "학업", 1000000, "Windows", ["가격", "CPU/GPU", "메모리", "배터리"]],
  ["PHONE", "휴대폰", "일상", 500000, "Android", ["가격", "OS", "화면", "배터리"]],
  ["TABLET", "태블릿", "필기", 300000, "iPadOS", ["가격", "화면", "펜 지원", "무게"]],
  ["MONITOR", "모니터", "사무", 200000, "HDMI", ["가격", "크기", "해상도", "주사율"]],
  ["EARBUDS", "무선 이어폰", "출퇴근", 100000, "iOS", ["가격", "형태", "ANC 지원", "무게"]],
].map(([key, label, use, budget, compatibility, facts]) => ({ key, label, version: "intake-v1", uses: [use, key === "EARBUDS" ? "운동" : key === "TABLET" ? "그림" : "게임"], budgets: [budget], compatibility: [compatibility], facts }));
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
  const template = templates.find(item => item.key === body.templateKey);
  const result = create({ ...body, intentText: template.label + " 비교", selectedSources: [] });
  result.discovery = { version: 1, status: "PENDING", conditions: body.discovery, updatedAt: new Date().toISOString() };
  result.productFacts = [];
  result.guidance.questions = [];
  result.guidance.candidateSuggestions = ["Alpha", "Beta", "Gamma"].map((name, index) => ({
    id: randomUUID(), name: template.label + " " + name, url: "https://example.com/" + name, reason: "주요 정보를 확인하고 비교하세요.", status: "PROPOSED",
    facts: [{ name: "가격", value: (100000 + index * 10000) + " KRW", sourceUrl: "https://example.com/" + name, quote: name + " 제품 기본 구성 가격", fetchedAt: new Date().toISOString(), verificationStatus: "UNREVIEWED" }],
  }));
  return result;
}
const server = http.createServer(async (request, response) => {
  const send = (status, body, headers = {}) => { response.writeHead(status, { "content-type": "application/json", ...headers }); response.end(JSON.stringify(body)); };
  let body = {};
  if (request.method !== "GET") { let raw = ""; for await (const chunk of request) raw += chunk; body = raw ? JSON.parse(raw) : {}; }
  if (request.url === "/api/v1/me") return send(200, { id: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", email: "user@example.com" }, { "set-cookie": "XSRF-TOKEN=test-token; Path=/; SameSite=Lax" });
  if (request.url === "/api/v1/intake-templates") return send(200, templates);
  if (request.method === "GET" && request.url === "/api/v1/decisions") return send(200, current ? [current] : []);
  if (request.url === "/api/v1/product-lookups/capabilities") return send(200, {
    searchEnabled: true, searchProvider: "Serper", searchNotice: "입력한 제품명을 Serper로 전송하는 데 동의합니다.",
    importEnabled: true, importProvider: "Firecrawl", importNotice: "선택한 공개 URL을 Firecrawl로 보내 정보를 가져오는 데 동의합니다.",
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
    current = body.discovery ? createDiscovery(body) : create(body);
    if (body.webConsent) current.guidance.evidence = current.options.map(item => ({
      id: randomUUID(), optionId: item.id, url: item.sourceUrl, status: "FAILED", error: "수집 실패", stale: false,
    }));
    return send(201, current);
  }
  if (request.method === "GET" && request.url === `/api/v1/decisions/${decisionId}`) {
    if (current?.discovery?.status === "PENDING") current.discovery.status = "SUCCEEDED";
    return send(200, current);
  }
  if (request.url === "/api/v1/ai/capabilities") return send(200, { enabled: true, provider: "gemini", model: "test", requiresSensitiveDataAcknowledgement: true });
  if (!current) return send(404, { detail: "Not found" });
  if (request.method === "PATCH" && request.url.includes("/candidate-suggestions/")) {
    const id = request.url.split("/").at(-1);
    const candidate = current.guidance.candidateSuggestions.find(item => item.id === id);
    if (candidate && body.action === "ACCEPT") {
      const added = option(candidate); current.options.push(added);
      current.productFacts.push({ optionId: added.id, facts: candidate.facts });
    }
    current.guidance.candidateSuggestions = current.guidance.candidateSuggestions.filter(item => item.id !== id);
    current.version++; return send(200, current);
  }
  if (request.method === "POST" && request.url.endsWith("/discovery/retry")) {
    current.discovery = { ...current.discovery, version: current.discovery.version + 1, status: "PENDING", error: null };
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
