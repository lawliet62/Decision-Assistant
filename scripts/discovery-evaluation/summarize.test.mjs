import { test } from "node:test";
import assert from "node:assert/strict";
import { summarize } from "./summarize.mjs";

const row = (overrides = {}) => ({ caseId: "fixture", decisionId: "fixture", discovery: { status: "FAILED" }, candidates: [],
  metrics: { events: [], searchCalls: 1, collectionCalls: 0, aiCalls: 0, inputTokens: 0, outputTokens: 0,
    usageKnown: true, executionMs: 2000, queueMs: 100 }, ...overrides });

test("검색 오류의 미측정 결과와 실제 빈 검색을 구분한다", () => {
  const failed = row();
  const empty = row({ discovery: { status: "SUCCEEDED", emptyReason: "NO_OFFICIAL_RESULTS" } });
  empty.metrics.events.push({ product_event: "product_lookup_results", returned: "0", unique: "0" });
  const result = summarize([failed, empty]);
  assert.equal(result.funnel[0].returned, null);
  assert.equal(result.funnel[0].official, null);
  assert.equal(result.funnel[1].returned, 0);
  assert.equal(result.funnel[1].official, 0);
  assert.equal(result.withCandidates, 0);
  assert.equal(result.withTwoAppRecommended, 0);
});

test("AI 실패의 미확인 사용량과 앱 추천 수를 과대 해석하지 않는다", () => {
  const failed = row();
  Object.assign(failed.metrics, { aiCalls: 1, usageKnown: false, inputTokens: null, outputTokens: null, executionMs: 130000 });
  const success = row({ candidates: [{ recommendationGroup: "RECOMMENDED" }, { recommendationGroup: "UNKNOWN" }] });
  const result = summarize([failed, success]);
  assert.equal(result.unknownUsageRuns, 1);
  assert.equal(result.knownInputTokens, 0);
  assert.equal(result.withCandidates, 1);
  assert.equal(result.withTwoAppRecommended, 0);
  assert.equal(result.executionMs.median, 66000);
  assert.equal(result.executionMs.over120Seconds, 1);
});

test("실행하지 않은 표본은 0초나 성공으로 집계하지 않는다", () => {
  const result = summarize([]);
  assert.equal(result.completed, 0);
  assert.equal(result.executionMs.median, null);
  assert.equal(result.executionMs.max, null);
});

test("worker 계측이 누락되면 호출 수를 0으로 합산하지 않는다", () => {
  const missing = row();
  Object.assign(missing.metrics, { coverageComplete: false, searchCalls: null, collectionCalls: null, aiCalls: null,
    usageKnown: false, inputTokens: null, outputTokens: null });
  const result = summarize([row(), missing]);
  assert.equal(result.searchCalls, null);
  assert.equal(result.collectionCalls, null);
  assert.equal(result.aiCalls, null);
  assert.equal(result.incompleteInstrumentationRuns, 1);
});
