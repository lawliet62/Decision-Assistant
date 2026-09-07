import http from "node:http";
import { randomUUID } from "node:crypto";

const decisionId = "11111111-1111-1111-1111-111111111111";
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
const server = http.createServer(async (request, response) => {
  const send = (status, body, headers = {}) => { response.writeHead(status, { "content-type": "application/json", ...headers }); response.end(JSON.stringify(body)); };
  let body = {};
  if (request.method !== "GET") { let raw = ""; for await (const chunk of request) raw += chunk; body = raw ? JSON.parse(raw) : {}; }
  if (request.url === "/api/v1/me") return send(200, { id: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", email: "user@example.com" }, { "set-cookie": "XSRF-TOKEN=test-token; Path=/; SameSite=Lax" });
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
    current = create(body);
    if (body.webConsent) current.guidance.evidence = current.options.map(item => ({
      id: randomUUID(), optionId: item.id, url: item.sourceUrl, status: "FAILED", error: "수집 실패", stale: false,
    }));
    return send(201, current);
  }
  if (request.method === "GET" && request.url === `/api/v1/decisions/${decisionId}`) return send(200, current);
  if (request.url === "/api/v1/ai/capabilities") return send(200, { enabled: false, provider: "gemini", model: "test", requiresSensitiveDataAcknowledgement: true });
  if (!current) return send(404, { detail: "Not found" });
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
