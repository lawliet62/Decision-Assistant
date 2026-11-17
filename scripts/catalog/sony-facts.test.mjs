import { test } from "node:test";
import assert from "node:assert/strict";
import { shouldStartSonyFacts, factReviewsFor, parseSonyCatalogRow } from "./sony-facts.mjs";

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

test("같은 밀리초 안에서도 나노초가 더 이른 확인은 거절한다", () => {
  const report = { checkedAt: "2026-11-13T12:00:00.000Z", facts: [{ name: "ANC 지원" }, { name: "호환성" }] };
  const row = { facts: report.facts.map(f => ({ evidence: { name: f.name, fetchedAt: "2026-11-01T00:00:00Z" } })),
    fact_reviews: { "호환성": "2026-11-13T12:00:00.000000001Z" } };
  assert.equal(factReviewsFor(report, row), null);
});

test("DB 숫자형 Instant의 마지막 나노초를 보존해 이전 확인과 원래 근거를 비교한다", () => {
  const seconds = Date.parse("2026-11-13T12:00:00Z") / 1000;
  const report = { checkedAt: "2026-11-13T12:00:00.000Z", facts: [{ name: "ANC 지원" }, { name: "호환성" }] };
  for (const field of ["original", "review"]) {
    const row = parseSonyCatalogRow(`{"revision":1,"version":3,"facts":[
      {"evidence":{"name":"ANC 지원","fetchedAt":${seconds}${field === "original" ? ".000000001" : ""}}},
      {"evidence":{"name":"호환성","fetchedAt":"2026-11-01T00:00:00Z"}}],
      "fact_reviews":${field === "review" ? `{"ANC 지원":${seconds}.000000001}` : "{}"}}`);
    assert.equal(row.revision, 1);
    assert.equal(row.version, 3);
    assert.equal(factReviewsFor(report, row), null);
    const equal = { ...report, checkedAt: "2026-11-13T12:00:00.000000001Z" };
    assert.deepEqual(factReviewsFor(equal, row), { "ANC 지원": equal.checkedAt, "호환성": equal.checkedAt });
    const later = { ...report, checkedAt: "2026-11-13T12:00:00.001Z" };
    assert.deepEqual(factReviewsFor(later, row), { "ANC 지원": later.checkedAt, "호환성": later.checkedAt });
  }
});

test("동일한 epoch 초와 ISO 시각은 허용하고 잘못된 시각은 확인을 만들지 않는다", () => {
  const report = { checkedAt: "2026-11-13T12:00:00.000Z", facts: [{ name: "ANC 지원" }, { name: "호환성" }] };
  const seconds = Date.parse(report.checkedAt) / 1000;
  const row = { facts: report.facts.map(f => ({ evidence: { name: f.name, fetchedAt: seconds } })), fact_reviews: {} };
  assert.deepEqual(factReviewsFor(report, row), { "ANC 지원": report.checkedAt, "호환성": report.checkedAt });
  for (const invalid of ["invalid", "2026-02-30T00:00:00Z", "2026-11-13T12:00:00.0000000001Z", null, seconds + 0.001]) {
    row.fact_reviews["호환성"] = invalid;
    if (invalid !== null) assert.equal(factReviewsFor(report, row), null);
    assert.equal(factReviewsFor({ ...report, checkedAt: invalid }, row), null);
  }
});
