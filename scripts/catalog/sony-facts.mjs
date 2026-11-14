import fs from "node:fs";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { execFileSync } from "node:child_process";

const root = fileURLToPath(new URL("../../", import.meta.url));
const productId = "sony-wf-c710n-glass-blue";
const purchaseUrl = "https://store.sony.co.kr/product-view/128838586";
const urls = ["https://www.sony.co.kr/headphones/products/wf-c710n", "https://www.sony.co.kr/electronics/support/software/00369642"];
const runtimeFile = path.join(root, "backend/build/catalog-inspection-runtime.json");
const quote = value => "'" + String(value).replaceAll("'", "''") + "'";

export function shouldStartSonyFacts(tasks, env) {
  const profiles = (env.SPRING_PROFILES_ACTIVE ?? "local").split(",").map(s => s.trim());
  return tasks.includes("bootRun") && profiles.length === 1 && profiles[0] === "local"
    && env.DECISION_ASSISTANT_CATALOG_SPEC_REFRESH_ENABLED?.toLowerCase() === "true"
    && env.DECISION_ASSISTANT_CATALOG_ENABLED?.toLowerCase() !== "false"
    && !env.DB_SECRET_VERSION && (!env.DB_USERNAME || env.DB_USERNAME === "decision_assistant")
    && (!env.DB_URL || ["jdbc:postgresql://localhost:5432/decision_assistant", "jdbc:postgresql://127.0.0.1:5432/decision_assistant"].includes(env.DB_URL));
}

export function prepareSonyFacts(env = process.env) {
  const windows = process.platform === "win32";
  execFileSync(windows ? (process.env.ComSpec ?? "cmd.exe") : "bash",
    windows ? ["/d", "/s", "/c", "backend\\gradlew.bat", "-p", "backend", "prepareCatalogInspection"]
      : ["backend/gradlew", "-p", "backend", "prepareCatalogInspection"],
    { cwd: root, env, encoding: "utf8", timeout: 180000, windowsHide: true, stdio: ["ignore", "pipe", "pipe"] });
}

function sql(command) {
  return execFileSync("docker", ["exec", "-i", "backend-postgres-1", "psql", "-U", "decision_assistant", "-d", "decision_assistant", "-v", "ON_ERROR_STOP=1", "-Atq"],
    { input: command, encoding: "utf8", timeout: 10000, maxBuffer: 1000000, windowsHide: true, stdio: ["pipe", "pipe", "pipe"] }).trim();
}

async function fetchPage(url) {
  const response = await fetch(url, { redirect: "manual", signal: AbortSignal.timeout(15000) });
  if (response.status !== 200) throw new Error("SOURCE_HTTP_" + response.status);
  const reader = response.body.getReader(); const chunks = []; let size = 0;
  while (true) {
    const { value, done } = await reader.read(); if (done) break;
    size += value.length;
    if (size > 2000000) { await reader.cancel(); throw new Error("SOURCE_TOO_LARGE"); }
    chunks.push(value);
  }
  return Buffer.concat(chunks).toString("utf8");
}

export async function inspectSonyFacts() {
  if (!fs.existsSync(runtimeFile)) prepareSonyFacts();
  const pages = {};
  for (const url of urls) pages[url] = await fetchPage(url);
  const checkedAt = new Date().toISOString();
  const runtime = JSON.parse(fs.readFileSync(runtimeFile, "utf8"));
  // JDK argument file로 Windows의 명령행 길이 제한을 피한다. HTML은 stdin으로만 전달한다.
  const argsFile = path.join(root, `backend/build/sony-inspection-${process.pid}.args`);
  const javaQuote = value => '"' + value.replaceAll("\\", "\\\\").replaceAll('"', '\\"') + '"';
  fs.writeFileSync(argsFile, `-cp\n${javaQuote(runtime.classpath)}\ncom.decisionassistant.guidance.CatalogPageInspectionMain\n--sony-spec\n`);
  let output;
  try {
    output = execFileSync(runtime.java, ["@" + argsFile], { input: JSON.stringify({ pages }), encoding: "utf8",
      timeout: 30000, maxBuffer: 1000000, windowsHide: true, stdio: ["pipe", "pipe", "pipe"] });
  } finally { fs.rmSync(argsFile, { force: true }); }
  const line = output.split(/\r?\n/).find(s => s.startsWith("CATALOG_SPEC_INSPECTION="));
  if (!line) throw new Error("VALIDATOR_RESULT_MISSING");
  return { ...JSON.parse(line.slice("CATALOG_SPEC_INSPECTION=".length)), checkedAt };
}

export async function refreshSonyFacts() {
  const rowText = sql(`select row_to_json(p) from (select id,revision,purchase_url,expected_identity,version,active,facts,fact_reviews from catalog_products where id=${quote(productId)}) p;`);
  if (!rowText) return { outcome: "SKIPPED_NOT_REGISTERED" };
  const row = JSON.parse(rowText);
  if (!row.active || row.purchase_url !== purchaseUrl || !Number.isSafeInteger(row.version)) return { outcome: "SKIPPED_REGISTRATION_CHANGED" };
  const reserved = sql(`insert into catalog_spec_attempts(product_id,next_attempt_at)
    select id, now()+interval '7 days' from catalog_products where id=${quote(productId)} and version=${Number(row.version)} and active
    on conflict(product_id) do update set next_attempt_at=excluded.next_attempt_at where catalog_spec_attempts.next_attempt_at<=now()
    returning product_id;`);
  if (!reserved) return { outcome: "SKIPPED_NOT_DUE" };
  const report = await inspectSonyFacts();
  if (report.verified !== true || report.productId !== productId || report.revision !== row.revision || report.identity !== row.expected_identity)
    return { outcome: "UNVERIFIED", sources: report.sources };
  const reviews = factReviewsFor(report, row);
  if (!reviews) return { outcome: "STALE_OR_INVALID_OBSERVATION" };
  const references = report.facts.map(evidence => ({ evidence }));
  const updated = sql(`set standard_conforming_strings=on;
    update catalog_products set fact_reviews=fact_reviews || ${quote(JSON.stringify(reviews))}::jsonb, version=version+1
    where id=${quote(productId)} and active and revision=${Number(report.revision)} and version=${Number(row.version)}
    and purchase_url=${quote(report.purchaseUrl)} and expected_identity=${quote(report.identity)}
    and facts @> ${quote(JSON.stringify(references))}::jsonb returning id;`);
  return { outcome: updated === productId ? "VERIFIED" : "REGISTRATION_OR_VERSION_CHANGED", productId, checkedAt: report.checkedAt };
}

export function factReviewsFor(report, row) {
  const checked = Date.parse(report.checkedAt);
  const timestamp = value => typeof value === "number" ? value * 1000 : Date.parse(value);
  if (!Number.isFinite(checked) || !Array.isArray(report.facts) || report.facts.length !== 2
    || new Set(report.facts.map(f => f.name)).size !== 2) return null;
  for (const proof of report.facts) {
    const originals = row.facts.filter(f => f.evidence.name === proof.name);
    if (originals.length !== 1 || !Number.isFinite(timestamp(originals[0].evidence.fetchedAt))
      || checked < timestamp(originals[0].evidence.fetchedAt)
      || (row.fact_reviews[proof.name] != null && (!Number.isFinite(timestamp(row.fact_reviews[proof.name])) || checked < timestamp(row.fact_reviews[proof.name])))) return null;
  }
  return Object.fromEntries(report.facts.map(f => [f.name, report.checkedAt]));
}

async function main() {
  const mode = process.argv[2] ?? "--check";
  if (mode === "--check") { console.log(JSON.stringify(await inspectSonyFacts())); return; }
  if (!["--once", "--worker"].includes(mode) || !shouldStartSonyFacts(["bootRun"], process.env)) throw new Error("LOCAL_SPEC_REFRESH_CONFIGURATION_REQUIRED");
  if (mode === "--worker" && process.argv[3]) {
    const parentPid = Number(process.argv[3]);
    if (!Number.isSafeInteger(parentPid) || parentPid <= 0) throw new Error("INVALID_PARENT_PID");
    setInterval(() => {
      try { process.kill(parentPid, 0); } catch { process.exit(0); }
    }, 1000).unref();
  }
  const tick = async () => {
    try {
      const result = await refreshSonyFacts();
      if (mode === "--once" || !result.outcome.startsWith("SKIPPED")) console.log(JSON.stringify(result));
      if (mode === "--once" && !["VERIFIED", "SKIPPED_NOT_DUE"].includes(result.outcome)) process.exitCode = 1;
    } catch (error) {
      const reason = /^SOURCE_HTTP_\d{3}$|^SOURCE_TOO_LARGE$|^VALIDATOR_RESULT_MISSING$/.test(error.message) ? error.message : "DATABASE_SOURCE_OR_VALIDATOR_FAILED";
      console.error("sony_spec_refresh outcome=" + reason);
      if (mode === "--once") process.exitCode = 1;
    }
  };
  await tick();
  if (mode === "--worker") setInterval(tick, 3600000);
}

if (process.argv[1] && pathToFileURL(path.resolve(process.argv[1])).href === import.meta.url) {
  main().catch(() => { console.error("sony_spec_refresh outcome=SOURCE_OR_VALIDATOR_FAILED"); process.exitCode = 1; });
}
