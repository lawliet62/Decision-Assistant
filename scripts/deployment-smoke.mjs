// 로컬 Compose synthetic DB 전용: 승인된 loopback 주소 외에는 요청하지 않는다.
import assert from "node:assert/strict";
import { mkdir, writeFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { execFileSync } from "node:child_process";
const origin = process.env.DECISION_SMOKE_ORIGIN ?? "http://127.0.0.1:3180";
assert(["http://127.0.0.1:3180", "http://127.0.0.1:3101"].includes(origin), "local smoke origin only");
const created = [];
let cookie = "";
let csrf = "";
async function call(path, method = "GET", body) {
  const response = await fetch(origin + path, { method, headers: { cookie, "content-type": "application/json", ...(csrf ? { "X-XSRF-TOKEN": csrf } : {}) }, body: body ? JSON.stringify(body) : undefined });
  const issued = response.headers.getSetCookie();
  if (issued.length) {
    cookie = issued.map(value => value.split(";")[0]).join("; ");
    csrf = decodeURIComponent(cookie.match(/XSRF-TOKEN=([^;]+)/)?.[1] ?? csrf);
  }
  assert(response.ok, `${method} ${path}: ${response.status}`);
  return response.status === 204 ? undefined : response.json();
}
const report = { measuredAt: new Date().toISOString(), origin, externalProviders: false };
for (let attempt = 0; attempt < 90; attempt++) {
  const ready = await fetch(origin + "/api/v1/me", { signal: AbortSignal.timeout(2000) }).then(r => r.ok).catch(() => false);
  if (ready) break;
  if (attempt === 89) throw new Error("로컬 배포가 90회 확인 안에 준비되지 않았습니다.");
  await new Promise(resolve => setTimeout(resolve, 1000));
}
try {
  const me = await call("/api/v1/me");
  assert.equal(me.id, "00000000-0000-0000-0000-000000000001", "local smoke identity required");
  const capabilities = await call("/api/v1/product-lookups/capabilities");
  assert.equal(capabilities.catalogEnabled, true);
  assert.equal(capabilities.searchEnabled, false);
  assert.equal(capabilities.importEnabled, false);
  for (let i = 0; i < 12; i++) {
    const d = await call("/api/v1/decisions", "POST", { title: `로컬 배포 검증 ${String(i).padStart(2, "0")}`, templateKey: "EARBUDS" });
    created.push(d.id);
  }
  const templates = await call("/api/v1/intake-templates");
  const template = templates.find(item => item.key === "EARBUDS");
  const catalog = await call("/api/v1/decisions", "POST", { templateKey: "EARBUDS", discovery: { templateVersion: template.version, uses: [], maxBudget: 200000, minimumRequirements: {}, searchConsent: false, collectionConsent: false, aiConsent: false, sensitiveDataAcknowledged: false } });
  created.push(catalog.id);
  assert(catalog.guidance.candidateSuggestions.length > 0, "실제 카탈로그 후보 필요");
  const first = await call("/api/v1/decisions?statuses=DRAFT&size=10&sort=TITLE&direction=ASC");
  assert.equal(first.items.length, 10);
  assert(first.totalPages >= 2);
  const item = await call(`/api/v1/decisions/${created[0]}`);
  await call(`/api/v1/decisions/${item.id}/resolutions`, "POST", { expectedVersion: item.version, kind: "ABANDONED", rationale: "synthetic smoke" });
  const finished = await call(`/api/v1/decisions/${item.id}`);
  assert.equal(finished.status, "ABANDONED");
  const multi = await call("/api/v1/decisions?statuses=DRAFT,ABANDONED&size=50");
  assert(multi.items.some(d => d.id === item.id));
  const selection = await call("/api/v1/decisions/selection?statuses=DRAFT&statuses=ABANDONED");
  assert.equal(selection.length, 13);
  assert(selection.some(d => d.id === item.id && d.version === finished.version));
  assert.equal((await call("/api/v1/decisions/selection?statuses=ABANDONED")).length, 1);
  const times = [];
  for (let batch = 0; batch < 20; batch++) await Promise.all(Array.from({ length: 5 }, async () => {
    const start = performance.now();
    await call("/api/v1/decisions?statuses=DRAFT&size=10");
    times.push(performance.now() - start);
  }));
  times.sort((a, b) => a - b);
  report.listRequests = times.length;
  report.concurrency = 5;
  report.listP95Ms = Math.round(times[Math.ceil(times.length * 0.95) - 1]);
  report.catalogCandidates = catalog.guidance.candidateSuggestions.length;
  // 별도 DB에 실제 dump/restore한 뒤, snapshot 이후 삭제를 재적용한다.
  const container = "decision-mvp-smoke-postgres-1";
  const docker = args => execFileSync("docker", args, { encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] });
  const restoreDb = "smoke_restore_" + Date.now();
  const dump = `/tmp/${restoreDb}.dump`;
  docker(["exec", "--user", "postgres", container, "pg_dump", "-U", "postgres", "-Fc", "-f", dump, "decision_assistant"]);
  await call(`/api/v1/decisions/${item.id}?expectedVersion=${finished.version}`, "DELETE");
  created.splice(created.indexOf(item.id), 1);
  docker(["exec", "--user", "postgres", container, "createdb", "-U", "postgres", restoreDb]);
  try {
    docker(["exec", "--user", "postgres", container, "pg_restore", "-U", "postgres", "--exit-on-error", "--no-owner", "-d", restoreDb, dump]);
    const sql = text => docker(["exec", "--user", "postgres", container, "psql", "-U", "postgres", "-d", restoreDb, "-v", "ON_ERROR_STOP=1", "-tAc", text]).trim();
    assert.match(item.id, /^[0-9a-f-]{36}$/);
    assert.equal(sql(`select count(*) from decisions where id = '${item.id}'`), "1");
    sql(`delete from decisions where id = '${item.id}'`);
    assert.equal(sql(`select count(*) from decisions where id = '${item.id}'`), "0");
    assert.equal(sql("select count(*) from flyway_schema_history where version = '16' and success"), "1");
    report.backupRestoreAndDeletionReplay = true;
  } finally {
    docker(["exec", "--user", "postgres", container, "dropdb", "-U", "postgres", restoreDb]);
    docker(["exec", "--user", "postgres", container, "rm", "--", dump]);
  }
  const require = createRequire(new URL("../frontend/package.json", import.meta.url));
  const { chromium } = require("@playwright/test");
  const browser = await chromium.launch();
  try {
    const page = await browser.newPage();
    await mkdir("backend/build/deployment-smoke", { recursive: true });
    await page.goto(origin + "/?open=DRAFT,ABANDONED");
    await page.getByRole("region", { name: "비교 중", exact: true }).getByRole("listitem").first().waitFor();
    const group = page.getByRole("region", { name: "비교 중", exact: true });
    const statusToggle = group.getByRole("button", { name: "이 상태 전체 선택" });
    await statusToggle.click();
    await page.waitForFunction(() => document.querySelector('[aria-label="비교 중"] button[aria-pressed="true"]') !== null);
    assert.equal(await statusToggle.getAttribute("aria-pressed"), "true");
    await statusToggle.click();
    assert.equal(await statusToggle.getAttribute("aria-pressed"), "false");
    report.browserSelectionToggle = true;
    await page.screenshot({ path: "backend/build/deployment-smoke/desktop.png", fullPage: true });
    await page.setViewportSize({ width: 375, height: 812 });
    await page.screenshot({ path: "backend/build/deployment-smoke/mobile.png", fullPage: true });
    assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
  } finally { await browser.close(); }
} finally {
  for (const id of created) {
    const latest = await call(`/api/v1/decisions/${id}`);
    await call(`/api/v1/decisions/${id}?expectedVersion=${latest.version}`, "DELETE");
    assert.equal((await fetch(`${origin}/api/v1/decisions/${id}`, { headers: { cookie } })).status, 404);
  }
}
await mkdir("backend/build/deployment-smoke", { recursive: true });
await writeFile("backend/build/deployment-smoke/result.json", JSON.stringify(report, null, 2));
console.log(JSON.stringify(report, null, 2));
