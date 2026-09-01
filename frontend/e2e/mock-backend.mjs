import http from "node:http";

const decisionId = "11111111-1111-1111-1111-111111111111";
const optionId = "22222222-2222-2222-2222-222222222222";
const criteria = ["가격", "성능 적합도", "휴대성"].map((name, index) => ({
  id: `33333333-3333-3333-3333-33333333333${index}`,
  name, kind: index === 0 ? "NUMERIC_UTILITY" : "RATING_1_TO_5",
  direction: index === 0 ? "LOWER_BETTER" : "NOT_APPLICABLE", weight: 1, active: true,
  worstAnchor: index === 0 ? 3000000 : undefined, bestAnchor: index === 0 ? 300000 : undefined,
}));
const questions = [
  ["44444444-4444-4444-4444-444444444440", "NUMBER", "context.budget", "이번 구매의 최대 예산은 얼마인가요?"],
  ["44444444-4444-4444-4444-444444444441", "SINGLE_CHOICE", "priority", "이번 결정에서 가장 중요한 기준은 무엇인가요?"],
  ["44444444-4444-4444-4444-444444444442", "TEXT", "context.avoid", "반드시 피하고 싶은 단점이나 조건이 있나요?"],
].map(([id, kind, targetKey, prompt]) => ({ id, kind, targetKey, prompt, impact: "결론에 영향을 주는 정보입니다.", options: kind === "SINGLE_CHOICE" ? criteria.map((item) => item.name) : [], status: "OPEN" }));

function decision() {
  return { id: decisionId, version: 0, title: "휴대할 개발용 노트북을 찾고 있어요", templateKey: "LAPTOP",
    templateVersion: "laptop-v2", status: "DRAFT", context: { intendedUse: "개발", currency: "KRW" },
    options: [{ id: optionId, name: "후보 · example.com", sourceUrl: "https://example.com/laptop" }],
    criteria, values: [], constraints: [], constraintResults: [], resolutions: [], outcomes: [],
    guidance: { intentText: "휴대할 개발용 노트북을 찾고 있어요", aiConsent: false,
      processingStatus: "NEEDS_INPUT", readiness: "PROVISIONAL",
      summary: "첫 후보를 정리했습니다. 비교할 후보를 하나 더 추가하거나 대안 탐색을 사용하세요.",
      questions, evidence: [], candidateSuggestions: [], appliedPreferences: [], reminders: [] },
    createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() };
}

const server = http.createServer((request, response) => {
  const send = (status, body, headers = {}) => { response.writeHead(status, { "content-type": "application/json", ...headers }); response.end(JSON.stringify(body)); };
  if (request.url === "/api/v1/me") return send(200, { id: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", email: "user@example.com" }, { "set-cookie": "XSRF-TOKEN=test-token; Path=/; SameSite=Lax" });
  if (request.method === "POST" && request.url === "/api/v1/decisions") return send(201, decision());
  if (request.method === "GET" && request.url === `/api/v1/decisions/${decisionId}`) return send(200, decision());
  if (request.url === "/api/v1/ai/capabilities") return send(200, { enabled: false, provider: "gemini", model: "test", requiresSensitiveDataAcknowledgement: true });
  return send(404, { detail: "Not found" });
});
server.listen(18080, "127.0.0.1");
