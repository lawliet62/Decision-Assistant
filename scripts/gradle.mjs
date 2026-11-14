import process from "node:process";
import { fileURLToPath } from "node:url";
import { backendEnvironment } from "./backend-env.mjs";
import { shouldStartSonyFacts } from "./catalog/sony-facts.mjs";
import { startManagedProcess } from "./managed-process.mjs";

const tasks = process.argv.slice(2);
if (tasks.length === 0) {
  console.error("Usage: node scripts/gradle.mjs <task...>");
  process.exit(2);
}

const windows = process.platform === "win32";
const executable = windows ? (process.env.ComSpec ?? "cmd.exe") : "./backend/gradlew";
const env = backendEnvironment(tasks);
const children = new Set();
let shutdownSignal;
function start(command, args) {
  const managed = startManagedProcess(command, args, { env, windowsHide: true });
  children.add(managed);
  return managed;
}
function gradle(selectedTasks) {
  // 종료 대상 트리에 다른 작업이 재사용할 Gradle daemon을 두지 않는다.
  const args = windows ? ["/d", "/s", "/c", "backend\\gradlew.bat", "-p", "backend", "--no-daemon", ...selectedTasks]
    : ["-p", "backend", "--no-daemon", ...selectedTasks];
  return start(executable, args);
}
async function stopChildren(signal = "SIGTERM") {
  await Promise.all([...children].map(child => child.stop(signal)));
}
for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => {
    shutdownSignal ??= signal;
    stopChildren(signal).catch(error => { console.error(error.message); process.exitCode = 1; });
  });
}

try {
  if (shouldStartSonyFacts(tasks, env)) {
    const preparation = await gradle(["prepareCatalogInspection"]).completed;
    if (!shutdownSignal && preparation.code === 0) {
      const worker = start(process.execPath, [fileURLToPath(new URL("./catalog/sony-facts.mjs", import.meta.url)), "--worker", String(process.pid)]);
      worker.completed.catch(error => { console.error(error.message); });
    } else if (!shutdownSignal) console.error("sony_spec_refresh outcome=VALIDATOR_PREPARATION_FAILED");
  }
  if (!shutdownSignal) {
    const result = await gradle(tasks).completed;
    process.exitCode = result.code ?? 1;
  }
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
} finally {
  await stopChildren(shutdownSignal);
  if (shutdownSignal) process.exitCode = shutdownSignal === "SIGINT" ? 130 : 143;
}
