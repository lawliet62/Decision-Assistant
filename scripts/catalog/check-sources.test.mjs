import { test } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import { inspect, requiresAttention, summary } from "./check-sources.mjs";

test("소니의 두 출처를 각각 한 번 점검하고 미등록 출처는 조회하지 않는다", async () => {
  const manifest = JSON.parse(fs.readFileSync(new URL("../../backend/src/main/resources/catalog/earbuds.json", import.meta.url), "utf8"));
  const entry = manifest.find(item => item.id === "sony-wf-c710n-glass-blue");
  const titles = ["WF-C710N 무선 노이즈 캔슬링 이어폰 | 30시간 배터리 | 소니코리아", "펌웨어 업데이트 버전 1.0.5 | Sony KR"];
  const calls = [];
  const fetchPage = async url => {
    calls.push(url);
    const i = entry.facts.findIndex(item => item.evidence.sourceUrl === url);
    return new Response(`<title>${titles[i]}</title><p>${entry.facts[i].evidence.quote}</p>`);
  };
  const result = await inspect([entry], fetchPage, new Date("2026-11-13T12:00:00Z"));
  assert.equal(calls.length, 2);
  assert.equal(new Set(calls).size, 2);
  assert.deepEqual(result.map(item => [item.observation, item.titleCheck]), [["QUOTE_PRESENT", "MATCH"], ["QUOTE_PRESENT", "MATCH"]]);
  const unknown = structuredClone(entry);
  unknown.facts[1].evidence.sourceUrl = "https://unapproved.example/spec";
  calls.length = 0;
  const partial = await inspect([unknown], fetchPage, new Date("2026-11-13T12:00:00Z"));
  assert.equal(calls.length, 1);
  assert.equal(partial.filter(item => item.observation === "UNSUPPORTED_SOURCE").length, 1);
});

test("점검 대상에 등록되지 않은 활성 상품 사양은 누락하지 않고 미지원으로 보고한다", async () => {
  const item = { id: "new-product", active: true, facts: [{ cycle: "HARDWARE", evidence: {
    name: "ANC", sourceUrl: "https://example.com/product", quote: "ANC", fetchedAt: "2026-10-01T00:00:00Z" } }] };
  const rows = await inspect([item], () => { throw new Error("승인되지 않은 출처 요청"); });
  assert.equal(rows.length, 1);
  assert.equal(rows[0].observation, "UNSUPPORTED_SOURCE");
});

const source = "https://www.qcy.com/products/qcy-t13-anc";
test("Liberty 5는 확인한 두 제목만 허용하고 문구 누락은 계속 보고한다", async () => {
  const entries = JSON.parse(fs.readFileSync(new URL("../../backend/src/main/resources/catalog/earbuds.json", import.meta.url), "utf8"));
  const product = entries.find(e => e.id === "anker-liberty5-a3957");
  const before = JSON.stringify(product);
  for (const suffix of ["Dolby 3D Audio", "Dolby Audio", "Dolby Other"]) {
    let calls = 0;
    const rows = await inspect([product], async () => { calls++; return new Response(`<title>soundcore Liberty 5 | Noise-Cancelling Earbuds with ${suffix}</title>` + product.facts.map(f=>f.evidence.quote).join(" ")); }, new Date("2026-11-14T00:00:00Z"));
    assert.equal(calls, 1);
    assert.ok(rows.every(r => r.titleCheck === (suffix === "Dolby Other" ? "MISMATCH" : "MATCH")));
  }
  const missing = await inspect([product], async () => new Response("<title>soundcore Liberty 5 | Noise-Cancelling Earbuds with Dolby Audio</title>"));
  assert.ok(missing.every(r=>r.observation === "QUOTE_MISSING"));
  assert.equal(JSON.stringify(product), before);
});

test("불일치 제목은 길이를 제한하고 summary에서 코드로 표시한다", async () => {
  const product = { id:"qcy-t13-pro",active:true,facts:[{cycle:"HARDWARE",evidence:{name:"ANC",sourceUrl:"https://www.qcy.com/products/qcy-t13-pro",quote:"ANC",fetchedAt:"2026-11-01T00:00:00Z"}}] };
  const rows=await inspect([product],async()=>new Response("<title>Other `title` & [link]</title>ANC"));
  assert.equal(rows[0].observedTitle,"Other `title` & [link]");
  assert.ok(summary(rows).includes("\\u0060title\\u0060"));
  assert.ok(summary(rows).includes("\\u0026"));
  const large=await inspect([product],async()=>new Response("<title>"+"x".repeat(501)+"</title>ANC"));
  assert.equal(large[0].observedTitle,null);
  assert.equal(large[0].titleCheck,"MISMATCH");
});
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

test("JBL 제조사 출처에서 두 사양을 한 번 조회하고 확인 시각은 보존한다", async () => {
  const fs = await import("node:fs");
  const entry = JSON.parse(fs.readFileSync(new URL("../../backend/src/main/resources/catalog/earbuds.json",import.meta.url),"utf8")).find(e=>e.id==="jbl-tune-beam2-black");
  const before=JSON.stringify(entry);let calls=0;
  const results=await inspect([entry],async ()=>{calls++;return new Response("<title>JBL Tune Beam 2 | 트루 와이어리스 노이즈 캔슬링 이어버드</title>"+entry.facts.map(f=>f.evidence.quote).join(" "));},new Date("2026-11-13T13:00:00Z"));
  assert.equal(calls,1);assert.equal(results.length,2);
  assert.ok(results.every(r=>r.observation==="QUOTE_PRESENT"&&r.titleCheck==="MATCH"));assert.equal(JSON.stringify(entry),before);
});
