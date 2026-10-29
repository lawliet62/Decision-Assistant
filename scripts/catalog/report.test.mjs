import { test } from "node:test";
import assert from "node:assert/strict";
import { summarize } from "./report.mjs";

test("실패와 미관측 구간을 정답으로 만들지 않고 느린 갱신의 가용성 손실을 센다", () => {
  const rows = Array.from({ length: 8 }, (_, day) => ({ product_id: "a", attempted_at: new Date(Date.UTC(2026, 9, day + 1)).toISOString(),
    completed_at: new Date(Date.UTC(2026, 9, day + 1, 0, 1)).toISOString(), outcome: day === 2 ? "COLLECTION_FAILED" : "VERIFIED", price: 30000 + day * 100 }));
  const result = summarize(rows);
  assert.equal(result.attempts, 8);
  assert.equal(result.outcomes.COLLECTION_FAILED, 1);
  assert.equal(result.cadence[0].references, 7);
  assert.equal(result.cadence[0].available, 7);
  assert.equal(result.cadence[1].available, 3);
  assert.equal(result.cadence[2].available, 2);
});
test("같은 날 추가 관측의 예산 오판과 사양 점검 기한을 기록한다", () => {
  const rows = [{ product_id: "a", attempted_at: "2026-10-01T00:00:00Z", completed_at: "2026-10-01T00:01:00Z", outcome: "VERIFIED", price: 29000 },
    { product_id: "a", attempted_at: "2026-10-01T12:00:00Z", completed_at: "2026-10-01T12:01:00Z", outcome: "VERIFIED", price: 31000 }];
  const products = [{ id: "a", facts: [{ cycle: "SOFTWARE", evidence: { name: "호환성", fetchedAt: "2026-09-01T00:00:00Z" } },
    { cycle: "HARDWARE", evidence: { name: "ANC", fetchedAt: "2026-09-01T00:00:00Z" } }] }];
  const result = summarize(rows, products, new Date("2026-10-01T00:00:00Z"));
  assert.equal(result.cadence[0].priceMismatches, 1);
  assert.equal(result.cadence[0].falseBudgetPasses, 1);
  assert.deepEqual(result.reviewsDue.map(r => r.field), ["호환성"]);
});

test("품절을 관측한 시점에 남은 가격을 잘못된 판매 가능 표시로 센다", () => {
  const rows = [{ product_id: "a", attempted_at: "2026-10-01T00:00:00Z", completed_at: "2026-10-01T00:01:00Z", outcome: "VERIFIED", price: 29000 },
    { product_id: "a", attempted_at: "2026-10-01T12:00:00Z", completed_at: "2026-10-01T12:01:00Z", outcome: "UNAVAILABLE", price: null }];
  const result = summarize(rows);
  assert.equal(result.cadence[0].unavailableReferences, 1);
  assert.equal(result.cadence[0].falseAvailable, 1);
  assert.equal(result.cadence[0].references, 1);
});

test("사양 보고서는 유효한 DB 확인 시각을 적용하고 미래·과거·잘못된 값은 무시한다", () => {
  const fact = name => ({ cycle: "SOFTWARE", evidence: { name, fetchedAt: "2026-09-01T00:00:00Z" } });
  const numericOriginal = { cycle: "SOFTWARE", evidence: { name: "숫자 원본", fetchedAt: Date.parse("2026-09-01T00:00:00Z") / 1000 } };
  const products = [{ id: "a", facts: [fact("숫자"), fact("문자열"), fact("미래"), fact("과거"), fact("오류"), fact("없음"), numericOriginal],
    fact_reviews: { "숫자": Date.parse("2026-09-25T00:00:00Z") / 1000, "문자열": "2026-09-25T00:00:00Z",
      "미래": "2026-10-02T00:00:00Z", "과거": "2026-08-01T00:00:00Z", "오류": "invalid" } }];
  const result = summarize([], products, new Date("2026-10-01T00:00:00Z"));
  assert.deepEqual(result.reviewsDue.map(r => r.field), ["미래", "과거", "오류", "없음", "숫자 원본"]);
  assert.throws(() => summarize([], [{ id: "a", facts: [{ cycle: "SOFTWARE", evidence: { name: "오류", fetchedAt: null } }] }]), /사양 확인 시각이 잘못/);
});
