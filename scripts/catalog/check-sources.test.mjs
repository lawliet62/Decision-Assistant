import { test } from "node:test";
import assert from "node:assert/strict";
import { inspect, requiresAttention, summary } from "./check-sources.mjs";

test("점검 대상에 등록되지 않은 활성 상품 사양은 누락하지 않고 미지원으로 보고한다", async () => {
  const item = { id: "new-product", active: true, facts: [{ cycle: "HARDWARE", evidence: {
    name: "ANC", sourceUrl: "https://example.com/product", quote: "ANC", fetchedAt: "2026-10-01T00:00:00Z" } }] };
  const rows = await inspect([item], () => { throw new Error("승인되지 않은 출처 요청"); });
  assert.equal(rows.length, 1);
  assert.equal(rows[0].observation, "UNSUPPORTED_SOURCE");
});

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

test("문구가 있어도 제목 누락·다른 모델·복수 제목은 점검 대상으로 표시한다", async () => {
  const product = { id: "qcy-t13-pro", active: true, facts: [{ cycle: "HARDWARE", evidence: {
    name: "ANC 지원", sourceUrl: "https://www.qcy.com/products/qcy-t13-pro", quote: "50dB Adaptive ANC", fetchedAt: "2026-10-01T00:00:00Z" } }] };
  const inspectHtml = html => inspect([product], async () => new Response(html), new Date("2026-10-02T00:00:00Z"));
  const good = "<title>QCY T13 Pro &ndash; QCY Official</title><p>50dB Adaptive ANC</p>";
  const valid = await inspectHtml(good);
  assert.equal(valid[0].titleCheck, "MATCH");
  assert.equal(requiresAttention(valid), false);
  for (const html of [good.replace("T13 Pro", "T13 ANC"), good.replace(/<title>.*?<\/title>/, ""), good + "<title>다른 제품</title>"]) {
    const rows = await inspectHtml(html);
    assert.equal(rows[0].observation, "QUOTE_PRESENT");
    assert.equal(rows[0].titleCheck, "MISMATCH");
    assert.equal(requiresAttention(rows), true);
  }
});

test("Samsung 모델 고정 출처에서 두 사양을 한 번 점검하고 시각을 연장하지 않는다",async()=>{
 const fs=await import("node:fs");const entry=JSON.parse(fs.readFileSync(new URL("../../backend/src/main/resources/catalog/earbuds.json",import.meta.url),"utf8")).find(e=>e.id==="samsung-galaxy-buds4-white");const before=JSON.stringify(entry);let calls=0;
 const rows=await inspect([entry],async()=>{calls++;return new Response("<title>갤럭시 버즈4 (화이트) | SM-R540NZWAKOO | Samsung 대한민국</title>"+entry.facts.map(f=>f.evidence.quote).join(" "));},new Date("2026-11-13T14:00:00Z"));
 assert.equal(calls,1);assert.equal(rows.length,2);assert.ok(rows.every(r=>r.observation==="QUOTE_PRESENT"&&r.titleCheck==="MATCH"));assert.equal(JSON.stringify(entry),before);
});
