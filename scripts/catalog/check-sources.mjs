import fs from "node:fs";
import { pathToFileURL } from "node:url";

// 운영자가 승인한 고정 사양 페이지만 점검한다. 일치는 사양의 진실성이나 모델 동일성을 증명하지 않는다.
const sources = [
  ["jbl-tune-beam2-black", ["https://kr.jbl.com/TUNE-BEAM-2.html", "JBL Tune Beam 2 | 트루 와이어리스 노이즈 캔슬링 이어버드"]],
  ["samsung-galaxy-buds4-white", ["https://www.samsung.com/sec/buds/galaxy-buds4/buy/?modelCode=SM-R540NZWAKOO", "갤럭시 버즈4 (화이트) | SM-R540NZWAKOO | Samsung 대한민국"]],
  ["bose-qc-earbuds-ice-blue", ["https://www.bose.com/p/earbuds/bose-quietcomfort-headphones/QCEARB24-HEADPHONEIN.html", "QuietComfort Earbuds | Bose"]],
  ["audio-technica-ac5tw", ["https://audio-technica.co.kr/product/ath-ac5tw/921/", "ATH-AC5TW - (주)세기AT"]],
  ["audio-technica-sq1tw2nc", ["https://www.audio-technica.co.jp/corp/news/detail/1350", "完全ワイヤレスイヤホン『ATH-SQ1TW2NC』が登場｜オーディオテクニカ"]],
  ["apple-airpods-pro-3", ["https://www.apple.com/kr/airpods-pro/specs/", "AirPods Pro 3 - 제품 사양 - Apple (KR)"]],
  ["apple-airpods-5-wireless", ["https://www.apple.com/kr/airpods-5/specs/", "AirPods 5 - 제품 사양 - Apple (KR)"]],
  ["qcy-t13-anc", ["https://www.qcy.com/products/qcy-t13-anc", "QCY T13 ANC True Wireless Earbuds, 30H Playtime – QCY Official"]],
  ["qcy-t13-pro", ["https://www.qcy.com/products/qcy-t13-pro", "QCY T13 Pro – QCY Official"]],
  ["qcy-ht16", ["https://qcystore.co.kr/product/detail.html?product_no=990", "QCY HT16 멜로버즈 무선 블루투스 이어폰 ANC 노이즈캔슬링 블랙 - QCY스토어"]],
  ["qcy-ht20", ["https://qcystore.co.kr/product/detail.html?product_no=1028", "QCY HT20 멜로버즈 블루투스 무선 이어폰 노이즈캔슬링 AI마이크 C타입 커널형 게이밍 - QCY스토어"]],
  ["qcy-ht10-hires", ["https://qcystore.co.kr/product/detail.html?product_no=924", "QCY HT10 Hi-Res ANC APP 노이즈캔슬링 블루투스 이어폰 LDAC / 블랙 - QCY스토어"]],
  ["qcy-ht08-pro-plus", ["https://qcystore.co.kr/article/%EB%A7%A4%EA%B1%B0%EC%A7%84/8/16170/", "QCY, 적응형 노이즈 캔슬링 블루투스 이어폰 ‘HT08 멜로버즈 프로 플러스’ 출시 도매특판 - QCY스토어"]],
  ["qcy-ct06", ["https://qcystore.co.kr/product/detail.html?product_no=994", "QCY CT06 오픈형 귀찌 무선 블루투스 이어폰 러닝 자전거 운동용 이어커프 - QCY스토어"]],
  ["anker-p31i-d1202", ["https://www.ankerjapan.com/products/d1202", "Soundcore P31i | 完全ワイヤレスイヤホンの製品情報 | Anker Japan 公式オンラインストア"]],
  ["anker-aeroclip-a3388", ["https://ankerkorea.co.kr/product/detail.html?product_no=45", "에어로클립 오픈형 러닝 블루투스이어폰 A3388"]],
  ["anker-liberty5-a3957", ["https://www.soundcore.com/products/a3957-liberty-5-tws-earbuds", ["soundcore Liberty 5 | Noise-Cancelling Earbuds with Dolby 3D Audio", "soundcore Liberty 5 | Noise-Cancelling Earbuds with Dolby Audio"]]],
  ["anker-liberty5-pro-d1203", ["https://www.ankerjapan.com/products/d1203", "Soundcore Liberty 5 Pro | ワイヤレスイヤホンの製品情報 | Anker Japan 公式オンラインストア"]],
  ["anker-p41i-a3937", ["https://www.ankerjapan.com/products/a3937", "Soundcore P41i | 完全ワイヤレスイヤホンの製品情報 | Anker Japan 公式オンラインストア"]],
  ["qcy-t29-app", ["https://qcystore.co.kr/product/detail.html?product_no=709", "QCY T29 APP 블루투스이어폰 오픈형 13mm 드라이버 - QCY스토어"]],
  ["qcy-t17-app", ["https://qcystore.co.kr/product/detail.html?product_no=563", "QCY T17APP 블루투스이어폰 커널형 무선이어폰 저지연 큐씨와이 초경량 블루투스5.3버전 블랙 - QCY스토어"]],
  ["qcy-ht19", ["https://www.qcy.com/products/qcy-melobuds-n60", "QCY MeloBuds N60: Affordable Wireless Earbuds with Rich Sound – QCY Official"]],
  ["marshall-minor-iv-black", ["https://www.marshall.com/kr/ko/product/minor-iv?pid=1006653", "Minor IV Black - 이어버드 | Marshall.com"]],
  ["marshall-motif-ii-anc", ["https://www.marshall.com/kr/ko/product/motif-ii-anc?pid=1006450", "Motif II A.N.C. Black - 노이즈 캔슬링 이어버드 | Marshall.com"]],
  ["sennheiser-momentum-tw5", ["https://kr.sennheiser-hearing.com/product/detail.html?product_no=731", "한국어 쇼핑몰 | 모멘텀 트루 와이어리스 5"]],
  ["sony-wf-c710n-glass-blue", ["https://www.sony.co.kr/headphones/products/wf-c710n", "WF-C710N 무선 노이즈 캔슬링 이어폰 | 30시간 배터리 | 소니코리아"]],
  ["sony-wf-c710n-glass-blue", ["https://www.sony.co.kr/electronics/support/software/00369642", "펌웨어 업데이트 버전 1.0.5 | Sony KR"]],
];
const MAX_BYTES = 2_000_000;
const normalize = value => value.replace(/<[^>]*>/g, " ").replace(/&nbsp;|&#160;/gi, " ")
  .replace(/&amp;/gi, "&").replace(/\s+/g, " ").trim().toLowerCase();
const observedTitle = html => {
  const titles = [...html.matchAll(/<title(?:\s[^>]*)?>([\s\S]*?)<\/title\s*>/gi)];
  if (titles.length !== 1 || titles[0][1].length > 500 || /[<>\u0000-\u001f]/.test(titles[0][1].replace(/[\r\n\t]/g, ""))) return null;
  const title = titles[0][1].replace(/&(?:ndash|#8211|#x2013);/gi, "–").replace(/\s+/g, " ").trim();
  return title;
};

export async function inspect(entries, fetchPage = fetch, now = new Date()) {
  const results = [];
  // 등록된 사양이 점검 목록에서 빠져도 정상으로 보고하지 않는다.
  for (const entry of entries.filter(item => item.active)) {
    for (const item of entry.facts) {
      const fact = item.evidence;
      if (sources.some(([id, [url]]) => id === entry.id && url === fact.sourceUrl)) continue;
      const dueAt = new Date(Date.parse(fact.fetchedAt) + (item.cycle === "HARDWARE" ? 90 : 30) * 86400000);
      results.push({ productId: entry.id, field: fact.name, source: fact.sourceUrl, reviewedAt: fact.fetchedAt,
        dueAt: dueAt.toISOString(), due: dueAt <= now, dueSoon: now.getTime() >= dueAt.getTime() - 7 * 86400000,
        observation: "UNSUPPORTED_SOURCE", titleCheck: "UNAVAILABLE", failure: null });
    }
  }
  for (const [id, [url, expectedTitle]] of sources) {
    const entry = entries.find(item => item.id === id && item.active);
    if (!entry) continue;
    const facts = entry.facts.filter(item => item.evidence.sourceUrl === url);
    if (!facts.length) continue;
    let page, titleCheck, failure, title = null;
    try {
      if (!facts.some(item => item.evidence.sourceUrl === url)) throw new Error("NO_APPROVED_SOURCE");
      const response = await fetchPage(url, { redirect: "manual", signal: AbortSignal.timeout(15000) });
      if (response.status !== 200) throw new Error("HTTP_" + response.status);
      const length = Number(response.headers.get("content-length") || 0);
      if (length > MAX_BYTES) throw new Error("TOO_LARGE");
      const reader = response.body.getReader();
      const chunks = []; let size = 0;
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        size += value.length;
        if (size > MAX_BYTES) { await reader.cancel(); throw new Error("TOO_LARGE"); }
        chunks.push(value);
      }
      const bytes = new Uint8Array(size); let offset = 0;
      for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
      const html = new TextDecoder().decode(bytes);
      title = observedTitle(html);
      titleCheck = (Array.isArray(expectedTitle) ? expectedTitle : [expectedTitle]).includes(title) ? "MATCH" : "MISMATCH";
      page = normalize(html);
    } catch (error) { page = null; titleCheck = "UNAVAILABLE"; failure = /^HTTP_\d{3}$|^TOO_LARGE$/.test(error.message) ? error.message : "NETWORK_OR_TIMEOUT"; }
    for (const item of facts) {
      const fact = item.evidence;
      const dueAt = new Date(Date.parse(fact.fetchedAt) + (item.cycle === "HARDWARE" ? 90 : 30) * 86400000);
      results.push({ productId: id, field: fact.name, source: fact.sourceUrl, reviewedAt: fact.fetchedAt,
        dueAt: dueAt.toISOString(), due: dueAt <= now, dueSoon: now.getTime() >= dueAt.getTime() - 7 * 86400000,
        observation: fact.sourceUrl !== url ? "UNSUPPORTED_SOURCE" : page === null ? "UNAVAILABLE" : page.includes(normalize(fact.quote)) ? "QUOTE_PRESENT" : "QUOTE_MISSING",
        titleCheck: fact.sourceUrl === url ? titleCheck : "UNAVAILABLE", observedTitle: title, failure: fact.sourceUrl === url && page === null ? failure : null });
    }
  }
  return results;
}

export function summary(results) {
  const lines = ["## 카탈로그 사양 출처 점검", "", "제목과 문구 일치는 모델 동일성·사양 정확성의 검증이 아닙니다. 확인 시각은 갱신하지 않습니다.", ""];
  if (!results.length) lines.push("점검할 등록 사양이 없습니다.");
  for (const row of results) lines.push(`- ${row.productId} / ${row.field}: ${row.observation}${row.failure ? ` (${row.failure})` : ""}, 제목 ${row.titleCheck}, 점검 기한 ${row.dueAt}${row.due ? " (점검 기한 초과)" : row.dueSoon ? " (7일 이내 점검 필요)" : ""} · ${row.source}`);
  for (const row of results.filter(row => row.titleCheck === "MISMATCH" && row.observedTitle != null)) {
    const safe = JSON.stringify(row.observedTitle).replace(/[<>&`]/g, c => "\\u" + c.charCodeAt(0).toString(16).padStart(4, "0"));
    lines.push(`- ${row.productId} / ${row.field} 관측 제목: \`${safe}\``);
  }
  return lines.join("\n") + "\n";
}

export const requiresAttention = results => results.some(row => row.dueSoon || row.observation !== "QUOTE_PRESENT" || row.titleCheck !== "MATCH");

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const entries = JSON.parse(fs.readFileSync("backend/src/main/resources/catalog/earbuds.json", "utf8"));
  const results = await inspect(entries);
  const output = summary(results);
  process.stdout.write(output);
  if (process.env.GITHUB_STEP_SUMMARY) fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY, output);
  if (requiresAttention(results)) process.exitCode = 1;
}
