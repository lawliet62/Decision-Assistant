import { test } from "node:test";
import assert from "node:assert/strict";
import { shouldStartSonyFacts, factReviewsFor } from "./sony-facts.mjs";

test("소니 사양 갱신은 명시적으로 켠 로컬 disposable DB 개발 실행에만 연결한다", () => {
  const enabled = { SPRING_PROFILES_ACTIVE: "local", DECISION_ASSISTANT_CATALOG_SPEC_REFRESH_ENABLED: "true" };
  assert.equal(shouldStartSonyFacts(["bootRun"], enabled), true);
  for (const change of [{ SPRING_PROFILES_ACTIVE: "oidc" }, { SPRING_PROFILES_ACTIVE: "local,test" },
    { DB_URL: "jdbc:postgresql://remote/dev" }, { DB_SECRET_VERSION: "1" }, { DB_USERNAME: "other" },
    { DECISION_ASSISTANT_CATALOG_ENABLED: "false" }, { DECISION_ASSISTANT_CATALOG_SPEC_REFRESH_ENABLED: "false" }])
    assert.equal(shouldStartSonyFacts(["bootRun"], { ...enabled, ...change }), false);
  assert.equal(shouldStartSonyFacts(["test"], enabled), false);
  assert.equal(shouldStartSonyFacts(["bootRun"], {}), false);
});

test("사양 확인 시각은 원래 근거와 이전 확인보다 뒤로만 이동한다", () => {
  const report = { checkedAt: "2026-11-13T12:00:00Z", facts: [{ name: "ANC 지원" }, { name: "호환성" }] };
  const row = { facts: report.facts.map(f => ({ evidence: { name: f.name, fetchedAt: "2026-11-01T00:00:00Z" } })), fact_reviews: {} };
  assert.deepEqual(factReviewsFor(report, row), { "ANC 지원": report.checkedAt, "호환성": report.checkedAt });
  assert.equal(factReviewsFor({ ...report, facts: [report.facts[0], report.facts[0]] }, row), null);
  row.fact_reviews["호환성"] = "2026-11-14T00:00:00Z";
  assert.equal(factReviewsFor(report, row), null);
  row.fact_reviews = {};
  row.facts[0].evidence.fetchedAt = "2026-11-14T00:00:00Z";
  assert.equal(factReviewsFor(report, row), null);
});
