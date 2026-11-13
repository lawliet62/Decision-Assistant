import { test } from "node:test";
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { mkdtempSync, mkdirSync, copyFileSync, writeFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { once } from "node:events";
import { connect } from "node:net";
import { setTimeout as delay } from "node:timers/promises";
import { startManagedProcess } from "./managed-process.mjs";

const windows = process.platform === "win32";
const source = new URL("./", import.meta.url);
function fixture() {
  const root = mkdtempSync(join(tmpdir(), "gradle-lifecycle-"));
  for (const dir of ["scripts/catalog", "backend", "frontend", "bin"]) mkdirSync(join(root, dir), { recursive: true });
  for (const file of ["gradle.mjs", "dev.mjs", "backend-env.mjs", "managed-process.mjs"])
    copyFileSync(new URL(file, source), join(root, "scripts", file));
  writeFileSync(join(root, "scripts/catalog/sony-facts.mjs"), `
    import { spawnSync } from 'node:child_process';
    export function shouldStartSonyFacts(tasks, env) { return env.TEST_WORKER === 'true'; }
    export function prepareSonyFacts() { spawnSync('./backend/gradlew',['prepareCatalogInspection']); }
    if(process.argv[2] === '--worker') await import('../../fixture.mjs');
  `);
  writeFileSync(join(root, "fixture.mjs"), `
    import { createServer } from 'node:net';
    import { spawn } from 'node:child_process';
    import { once } from 'node:events';
    import { writeFileSync } from 'node:fs';
    const args = process.argv.slice(2);
    const preparation = args.includes('prepareCatalogInspection');
    const worker = args.includes('--worker');
    const grandchild = args.includes('--grandchild');
    if (preparation && process.env.TEST_PREPARE !== 'wait') process.exit(0);
    if (process.env.TEST_EXIT && !preparation && !worker) process.exit(Number(process.env.TEST_EXIT));
    const role = args.includes('--database') ? 'database' : preparation ? 'preparation' : worker ? 'worker' : grandchild ? 'grandchild' : 'backend';
    const server = createServer(s => s.end()); server.listen(0, '127.0.0.1'); await once(server, 'listening');
    console.log(JSON.stringify({ role, pid:process.pid, port:server.address().port }));
    writeFileSync('ready-' + role, '');
    const nested = role === 'backend' ? spawn(process.execPath, ['fixture.mjs','--grandchild'], {stdio:'inherit'}) : null;
    let stopping = false;
    for (const signal of ['SIGINT','SIGTERM']) process.on(signal, () => {
      if (grandchild && process.env.TEST_IGNORE_CHILD === 'true') return;
      if (stopping) return; stopping = true;
      // wrapper가 종료 시점까지 기다리는지 확인하기 위한 실제 비동기 정리.
      setTimeout(() => server.close(() => { console.log(JSON.stringify({stopped:role,signal})); process.exit(0); }), 120);
    });
  `);
  writeFileSync(join(root, "backend/gradlew"), '#!/bin/sh\nexec "$NODE_EXE" fixture.mjs "$@"\n', { mode: 0o755 });
  writeFileSync(join(root, "backend/gradlew.bat"), '@"%NODE_EXE%" fixture.mjs %*\r\n');
  writeFileSync(join(root, "bin/docker"), '#!/bin/sh\nexit 0\n', { mode: 0o755 });
  writeFileSync(join(root, "bin/npm"), '#!/bin/sh\nwhile [ ! -f ../ready-backend ] || [ ! -f ../ready-worker ] || [ ! -f ../ready-grandchild ]; do sleep 0.05; done\nexit 7\n', { mode: 0o755 });
  return root;
}

function run(root, entry, settings = {}) {
  const child = spawn(process.execPath, [`scripts/${entry}.mjs`, ...(entry === "gradle" ? ["bootRun"] : [])], {
    cwd: root, stdio: ["ignore", "pipe", "pipe"],
    env: { ...process.env, PATH: join(root, "bin") + (windows ? ";" : ":") + process.env.PATH,
      NODE_EXE: process.execPath, TEST_WORKER: "false", ...settings },
  });
  let output = "";
  child.stdout.on("data", data => { output += data; });
  child.stderr.on("data", data => { output += data; });
  const done = once(child, "exit");
  return { child, done, output: () => output, records: () => output.split(/\r?\n/).flatMap(s => {
    try { return [JSON.parse(s)]; } catch { return []; }
  }) };
}
async function until(predicate, message) {
  for (let i = 0; i < 150; i++) { if (predicate()) return; await delay(50); }
  assert.fail(message());
}
async function portOpen(port) {
  return new Promise(resolve => {
    const socket = connect({ host: "127.0.0.1", port });
    socket.on("connect", () => { socket.destroy(); resolve(true); });
    socket.on("error", () => resolve(false));
  });
}
async function cleanup(root, running) {
  for (const { pid } of running.records()) if (pid) { try { process.kill(pid, "SIGKILL"); } catch {} }
  try { running.child.kill("SIGKILL"); } catch {}
  rmSync(root, { recursive: true, force: true });
}

test("Gradle의 정상 종료 코드를 wrapper가 보존한다", { timeout: 10000 }, async () => {
  const root = fixture(); const running = run(root, "gradle", { TEST_EXIT: "7" });
  try { assert.equal((await running.done)[0], 7, running.output()); }
  finally { await cleanup(root, running); }
});

for (const signal of ["SIGINT", "SIGTERM"]) {
  test(`${signal}은 Gradle·하위 서버·사양 worker를 종료하고 정리를 기다린다`, { skip: windows, timeout: 15000 }, async () => {
    const root = fixture(); const running = run(root, "gradle", { TEST_WORKER: "true" });
    try {
      await until(() => running.records().filter(r => r.port).length === 3, running.output);
      running.child.kill(signal);
      const [code] = await running.done;
      for (const role of ["backend", "grandchild", "worker"]) {
        const { port } = running.records().find(r => r.role === role);
        assert.equal(await portOpen(port), false, `${role} port=${port}`);
        assert.ok(running.records().some(r => r.stopped === role && r.signal === signal), running.output());
      }
      assert.equal(code, signal === "SIGINT" ? 130 : 143, running.output());
    } finally { await cleanup(root, running); }
  });
}

test("사양 validator 준비 중 종료하면 bootRun과 worker를 시작하지 않는다", { skip: windows, timeout: 15000 }, async () => {
  const root = fixture(); const running = run(root, "gradle", { TEST_WORKER: "true", TEST_PREPARE: "wait" });
  try {
    await until(() => running.records().some(r => r.role === "preparation"), running.output);
    running.child.kill("SIGTERM");
    assert.equal((await running.done)[0], 143, running.output());
    assert.ok(running.records().some(r => r.stopped === "preparation"), running.output());
    assert.ok(!running.records().some(r => ["backend", "worker"].includes(r.role)), running.output());
  } finally { await cleanup(root, running); }
});

test("frontend 선행 종료 시 dev가 backend 트리 정리를 기다린다", { skip: windows, timeout: 15000 }, async () => {
  const root = fixture(); const running = run(root, "dev", { TEST_WORKER: "true" });
  try {
    assert.equal((await running.done)[0], 7, running.output());
    for (const role of ["backend", "grandchild", "worker"]) {
      assert.ok(running.records().some(r => r.stopped === role), running.output());
      const { port } = running.records().find(r => r.role === role);
      assert.equal(await portOpen(port), false, `${role} port=${port}`);
    }
  } finally { await cleanup(root, running); }
});

test("dev에 SIGTERM을 보내면 Gradle 하위 서버와 worker도 종료한다", { skip: windows, timeout: 15000 }, async () => {
  const root = fixture();
  // frontend가 먼저 끝나지 않도록 이 사례에서는 대기시킨다.
  writeFileSync(join(root, "bin/npm"), '#!/bin/sh\nexec sleep 60\n', { mode: 0o755 });
  const running = run(root, "dev", { TEST_WORKER: "true" });
  try {
    await until(() => running.records().filter(r => r.port).length === 3, running.output);
    running.child.kill("SIGTERM");
    await running.done;
    for (const { port } of running.records().filter(r => r.port)) assert.equal(await portOpen(port), false);
  } finally { await cleanup(root, running); }
});

test("Compose 준비 중 취소하면 backend와 frontend를 시작하지 않는다", { skip: windows, timeout: 15000 }, async () => {
  const root = fixture();
  writeFileSync(join(root, "bin/docker"), '#!/bin/sh\nexec "$NODE_EXE" fixture.mjs --database\n', { mode: 0o755 });
  const running = run(root, "dev");
  try {
    await until(() => running.records().some(r => r.role === "database"), running.output);
    running.child.kill("SIGTERM");
    await running.done;
    assert.ok(!running.records().some(r => ["backend", "worker", "grandchild"].includes(r.role)), running.output());
    const { port } = running.records().find(r => r.role === "database");
    assert.equal(await portOpen(port), false);
    assert.ok(!running.output().includes("Starting backend and frontend"), running.output());
  } finally { await cleanup(root, running); }
});

test("종료 신호를 무시하는 하위 서버도 유예 시간 뒤 정리한다", { skip: windows, timeout: 15000 }, async () => {
  const root = fixture(); const running = run(root, "gradle", { TEST_IGNORE_CHILD: "true" });
  try {
    await until(() => running.records().filter(r => r.port).length === 2, running.output);
    running.child.kill("SIGTERM");
    assert.equal((await running.done)[0], 143, running.output());
    for (const { port } of running.records().filter(r => r.port)) assert.equal(await portOpen(port), false);
  } finally { await cleanup(root, running); }
});

test("dev는 wrapper의 강제 정리가 끝나기 전에 wrapper를 종료하지 않는다", { skip: windows, timeout: 15000 }, async () => {
  const root = fixture(); const running = run(root, "dev", { TEST_WORKER: "true", TEST_IGNORE_CHILD: "true" });
  try {
    assert.equal((await running.done)[0], 7, running.output());
    for (const { port } of running.records().filter(r => r.port)) assert.equal(await portOpen(port), false);
    assert.equal(running.records().filter(r => r.port).length, 3, running.output());
  } finally { await cleanup(root, running); }
});

test("관리 프로세스 종료는 Windows에서도 하위 서버의 포트를 해제한다", { timeout: 15000 }, async () => {
  const root = fixture();
  const managed = startManagedProcess(process.execPath, ["fixture.mjs"], {
    cwd: root, env: { ...process.env, TEST_EXIT: "", TEST_IGNORE_CHILD: "false" }, stdio: ["ignore", "pipe", "pipe"],
  });
  let output = "";
  managed.child.stdout.on("data", data => { output += data; });
  managed.child.stderr.on("data", data => { output += data; });
  const records = () => output.split(/\r?\n/).flatMap(s => { try { return [JSON.parse(s)]; } catch { return []; } });
  try {
    await until(() => records().filter(r => r.port).length === 2, () => output);
    await managed.stop("SIGTERM");
    for (const { port } of records().filter(r => r.port)) assert.equal(await portOpen(port), false);
  } finally { await cleanup(root, { child: managed.child, records }); }
});
