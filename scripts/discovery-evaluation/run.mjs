import { spawn, execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { appendFileSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { createServer } from "node:net";
import { once } from "node:events";
import { setTimeout as delay } from "node:timers/promises";
import { parseArgs } from "node:util";
import { backendEnvironment } from "../backend-env.mjs";

// 수동 Dev 실험 전용. API를 통해 동의·소유권·quota를 그대로 적용한다.
const { values } = parseArgs({ options: {
  "execute-live": { type: "boolean", default: false },
  variant: { type: "string" }, jar: { type: "string" }, output: { type: "string" },
  "source-commit": { type: "string" },
} });
if (!values["execute-live"] || !["baseline", "a"].includes(values.variant) || !values.jar || !values.output) {
  throw new Error("명시적 실행 필요: --execute-live --variant baseline|a --jar <bootJar> --output <새 결과 디렉터리>");
}
const cases = JSON.parse(readFileSync(new URL("./cases.json", import.meta.url), "utf8"));
const jar = resolve(values.jar);
const output = resolve(values.output);
const env = backendEnvironment(["bootRun"]);
if (env.SPRING_PROFILES_ACTIVE !== "local") throw new Error("local 프로필에서만 실행합니다.");
const port = 18080;
const base = `http://127.0.0.1:${port}`;
// 같은 DB의 다른 scheduler가 작업을 claim하면 알고리즘/계측이 섞인다.
// 일반 개발 서버가 켜져 있으면 실험을 시작하지 않는다. 다른 포트의 worker도 실행 전 중단해야 한다.
const developmentServerRunning = await fetch("http://127.0.0.1:8080/api/v1/me", { signal: AbortSignal.timeout(1500) })
  .then(() => true, () => false);
if (developmentServerRunning) throw new Error("8080 개발 서버가 실행 중입니다. 같은 DB의 다른 backend를 중단한 뒤 평가하세요.");
const probe = createServer();
probe.listen(port, "127.0.0.1");
await once(probe, "listening");
await new Promise(resolveClose => probe.close(resolveClose));
mkdirSync(output); // 기존 실험을 덮어쓰거나 자동 재실행하지 않는다.
const sha256 = data => createHash("sha256").update(data).digest("hex");
if (values["source-commit"] && !/^[a-f0-9]{7,40}$/.test(values["source-commit"])) throw new Error("source-commit은 Git commit hash여야 합니다.");
const manifest = {
  variant: values.variant, startedAt: new Date().toISOString(),
  commit: execFileSync("git", ["rev-parse", values["source-commit"] ?? "HEAD"], { encoding: "utf8" }).trim(),
  runnerCommit: execFileSync("git", ["rev-parse", "HEAD"], { encoding: "utf8" }).trim(),
  diffSha256: sha256(execFileSync("git", ["diff", "HEAD"])), jarSha256: sha256(readFileSync(jar)),
  casesSha256: sha256(JSON.stringify(cases)),
  provider: env.DECISION_ASSISTANT_AI_PROVIDER ?? "gemini",
  model: env.DECISION_ASSISTANT_AI_MODEL ?? env.GEMINI_MODEL ?? "gemini-3.5-flash-lite",
  maxRuns: 15, maxSearchCalls: 15, maxCollectionCalls: 75, maxAiCalls: 15,
};
writeFileSync(resolve(output, "manifest.json"), JSON.stringify(manifest, null, 2) + "\n");
const java = spawn("java", ["-jar", jar], {
  env: { ...env, SERVER_PORT: String(port), SERVER_ADDRESS: "127.0.0.1" },
  stdio: ["ignore", "pipe", "pipe"], windowsHide: true,
});
const events = [];
let lastDiagnostics = [];
function consume(stream) {
  let pending = "";
  stream.setEncoding("utf8");
  stream.on("data", chunk => {
    pending += chunk;
    const lines = pending.split(/\r?\n/);
    pending = lines.pop();
    for (const line of lines) {
      // 일반 JVM/SDK 로그에는 원문이 섞일 수 있어 고정 product_event만 저장한다.
      const marker = line.indexOf("product_event=");
      if (marker >= 0) {
        const event = Object.fromEntries([...line.slice(marker).matchAll(/([a-z_]+)=([^\s]+)/g)].map(match => [match[1], match[2]]));
        events.push(event);
        appendFileSync(resolve(output, "events.jsonl"), JSON.stringify(event) + "\n");
      }
      if (/secret_bootstrap|Secret bootstrap failed|Started DecisionAssistantApplication/.test(line)) {
        lastDiagnostics = [...lastDiagnostics.slice(-3), line.replace(/.*(secret_bootstrap|Secret bootstrap failed|Started DecisionAssistantApplication)/, "$1")];
      }
    }
  });
}
consume(java.stdout); consume(java.stderr);
let launchError;
java.on("error", error => { launchError = error.code; });
const cookies = new Map();
async function request(path, body) {
  const headers = { cookie: [...cookies].map(([key, value]) => `${key}=${value}`).join("; ") };
  if (body) {
    headers["Content-Type"] = "application/json";
    headers["X-XSRF-TOKEN"] = decodeURIComponent(cookies.get("XSRF-TOKEN") ?? "");
  }
  const response = await fetch(base + path, { method: body ? "POST" : "GET", headers,
    body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(10000) });
  for (const cookie of response.headers.getSetCookie()) {
    const [pair] = cookie.split(";"); const index = pair.indexOf("=");
    cookies.set(pair.slice(0, index), pair.slice(index + 1));
  }
  if (!response.ok) throw new Error(`API ${path.split("/").slice(0, 4).join("/")} HTTP ${response.status}`);
  return response.json();
}
function metrics(id) {
  const selected = events.filter(event => event.decision_id === id);
  const calls = provider => selected.filter(event => event.product_event === "discovery_provider_call" && event.provider === provider).length;
  const event = name => selected.find(item => item.product_event === name);
  const usage = event("discovery_ai_usage");
  const coverageComplete = Boolean(event("discovery_total_timing"));
  const aiCalls = calls("ai");
  return {
    coverageComplete,
    searchCalls: coverageComplete ? calls("serper") : null,
    collectionCalls: coverageComplete ? calls("firecrawl") : null,
    aiCalls: coverageComplete ? aiCalls : null,
    inputTokens: usage ? Number(usage.input_tokens) : coverageComplete && aiCalls === 0 ? 0 : null,
    outputTokens: usage ? Number(usage.output_tokens) : coverageComplete && aiCalls === 0 ? 0 : null,
    usageKnown: Boolean(usage) || coverageComplete && aiCalls === 0,
    queueMs: event("discovery_queue_timing") ? Number(event("discovery_queue_timing").duration_ms) : null,
    executionMs: event("discovery_total_timing") ? Number(event("discovery_total_timing").duration_ms) : null,
    events: selected,
  };
}
let completed = 0;
let previousStart = 0;
try {
  let ready = false;
  for (let i = 0; i < 120; i++) {
    if (launchError || java.exitCode !== null) throw new Error(`서버 시작 실패 (${launchError ?? java.exitCode}): ${lastDiagnostics.join(" | ")}`);
    try { await request("/api/v1/me"); ready = true; break; } catch { await delay(1000); }
  }
  if (!ready) throw new Error("서버 시작 대기 초과");
  for (const scenario of cases) {
    await delay(Math.max(0, previousStart + 13000 - Date.now()));
    const body = {
      title: `[탐색 평가 ${values.variant}] ${scenario.id}`, templateKey: scenario.category,
      discovery: { templateVersion: "intake-v2", uses: scenario.uses, maxBudget: scenario.budget,
        compatibility: scenario.compatibility, minimumRequirements: scenario.requirements,
        searchConsent: true, collectionConsent: true, aiConsent: true, sensitiveDataAcknowledged: true },
    };
    previousStart = Date.now();
    let decision = await request("/api/v1/decisions", body);
    appendFileSync(resolve(output, "created.jsonl"), JSON.stringify({ caseId: scenario.id, decisionId: decision.id }) + "\n");
    const deadline = Date.now() + 16 * 60000;
    while (["PENDING", "RUNNING"].includes(decision.discovery.status)) {
      if (Date.now() > deadline) throw new Error(`작업 관찰 시간 초과: ${scenario.id}`);
      await delay(1000);
      decision = await request(`/api/v1/decisions/${decision.id}`);
    }
    // 완료 응답보다 로그가 늦게 도착할 수 있지만, 없으면 0회 호출로 해석하지 않는다.
    for (let i = 0; i < 20 && !events.some(event => event.decision_id === decision.id && event.product_event === "discovery_total_timing"); i++) await delay(250);
    const result = { caseId: scenario.id, variant: values.variant, decisionId: decision.id,
      observedAt: new Date().toISOString(), wallMs: Date.now() - previousStart,
      discovery: decision.discovery, candidates: decision.guidance.candidateSuggestions, metrics: metrics(decision.id) };
    appendFileSync(resolve(output, "results.jsonl"), JSON.stringify(result) + "\n");
    completed++;
    console.log(JSON.stringify({ caseId: scenario.id, status: decision.discovery.status, emptyReason: decision.discovery.emptyReason,
      candidates: result.candidates.length, ms: result.wallMs }));
    if (!result.metrics.coverageComplete) throw new Error("평가 worker의 완료 계측이 없습니다. 다른 backend의 작업 처리 가능성이 있어 실험을 중단합니다.");
    if (result.metrics.events.some(event => event.code === "SEARCH_MONTHLY_LIMIT" || event.http_status === "429")) {
      throw new Error("계정 한도 응답으로 실험 중단. 자동 재시도하지 않습니다.");
    }
  }
} catch (error) {
  manifest.stopReason = error.message;
  process.exitCode = 1;
  console.error(error.message);
} finally {
  manifest.completed = completed;
  manifest.unexecuted = cases.slice(completed).map(item => item.id);
  manifest.finishedAt = new Date().toISOString();
  writeFileSync(resolve(output, "manifest.json"), JSON.stringify(manifest, null, 2) + "\n");
  if (java.exitCode === null) java.kill();
}
