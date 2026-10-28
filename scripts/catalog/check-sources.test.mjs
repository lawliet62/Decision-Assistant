import { test } from "node:test";
import assert from "node:assert/strict";
import { inspect, summary } from "./check-sources.mjs";

const source = "https://www.qcy.com/products/qcy-t13-anc";
const entry = { id: "qcy-t13-anc", active: true, facts: [
  { cycle: "SOFTWARE", evidence: { name: "호환성", sourceUrl: source, quote: "Compatible system: iOS/Android", fetchedAt: "2026-10-01T00:00:00Z" } },
  { cycle: "HARDWARE", evidence: { name: "ANC 지원", sourceUrl: source, quote: "Active Noise Cancellation", fetchedAt: "2026-10-01T00:00:00Z" } },
] };
test("출처를 제품당 한 번 조회하고 일치와 점검 기한을 별도로 기록한다", async () => {
  let calls = 0;
  const rows = await inspect([entry], async url => { calls++; assert.equal(url, source); return new Response("<p>Compatible system: iOS/Android</p>"); }, new Date("2026-11-01T00:00:00Z"));
  assert.equal(calls, 1);
  assert.deepEqual(rows.map(row => [row.observation, row.due]), [["QUOTE_PRESENT", true], ["QUOTE_MISSING", false]]);
  assert.match(summary(rows), /정확성의 검증이 아닙니다/);
});
test("소프트웨어 기한 7일 전부터 점검 필요로 보고한다", async () => {
  const rows = await inspect([entry], async () => new Response("Compatible system: iOS/Android"), new Date("2026-10-24T00:00:00Z"));
  assert.equal(rows[0].due, false);
  assert.equal(rows[0].dueSoon, true);
  assert.equal(rows[1].dueSoon, false);
});
test("차단·리다이렉트·전송 실패는 모두 미확인으로 기록한다", async () => {
  for (const fn of [async () => new Response("", { status: 302 }), async () => { throw Error("network"); }]) {
    assert.deepEqual((await inspect([entry], fn)).map(row => row.observation), ["UNAVAILABLE", "UNAVAILABLE"]);
  }
});
test("비활성 제품과 등록되지 않은 출처를 자동 요청하지 않는다", async () => {
  let called = 0;
  const fetcher = url => { called++; assert.equal(url, source); return Promise.resolve(new Response("")); };
  assert.deepEqual(await inspect([{ ...entry, active: false }], fetcher), []);
  const rows = await inspect([{ ...entry, facts: [{ ...entry.facts[0], evidence: { ...entry.facts[0].evidence, sourceUrl: "https://example.com/" } }] }], fetcher);
  assert.equal(rows[0].observation, "UNSUPPORTED_SOURCE");
  assert.equal(called, 0);
});

test("Apple의 승인된 사양 페이지도 점검하며 확인 시각을 변경하지 않는다", async () => {
  const apple = { id: "apple-airpods-pro-3", active: true, facts: [{ cycle: "HARDWARE", evidence: { name: "ANC 지원", sourceUrl: "https://www.apple.com/kr/airpods-pro/specs/", quote: "액티브 노이즈 캔슬링", fetchedAt: "2026-10-28T00:00:00Z" } }] };
  const before = JSON.stringify(apple);
  const rows = await inspect([apple], async url => { assert.equal(url, apple.facts[0].evidence.sourceUrl); return new Response("액티브 노이즈 캔슬링"); });
  assert.equal(rows[0].observation, "QUOTE_PRESENT");
  assert.equal(JSON.stringify(apple), before);
});
