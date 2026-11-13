import { spawnSync } from "node:child_process";
import { spawn } from "node:child_process";
import process from "node:process";
import { fileURLToPath } from "node:url";
import { backendEnvironment } from "./backend-env.mjs";
import { prepareSonyFacts, shouldStartSonyFacts } from "./catalog/sony-facts.mjs";

const tasks = process.argv.slice(2);
if (tasks.length === 0) {
  console.error("Usage: node scripts/gradle.mjs <task...>");
  process.exit(2);
}

const isWindows = process.platform === "win32";
const wrapper = isWindows ? "backend\\gradlew.bat" : "./backend/gradlew";
const executable = isWindows ? (process.env.ComSpec ?? "cmd.exe") : wrapper;
const args = isWindows
  ? ["/d", "/s", "/c", wrapper, "-p", "backend", ...tasks]
  : ["-p", "backend", ...tasks];
const env = backendEnvironment(tasks);
let factWorker;
if (shouldStartSonyFacts(tasks, env)) {
  try {
    prepareSonyFacts(env);
    factWorker = spawn(process.execPath, [fileURLToPath(new URL("./catalog/sony-facts.mjs", import.meta.url)), "--worker", String(process.pid)],
      { env, stdio: "inherit", windowsHide: true });
  } catch { console.error("sony_spec_refresh outcome=VALIDATOR_PREPARATION_FAILED"); }
}
process.on("exit", () => factWorker?.kill());
let result;
try { result = spawnSync(executable, args, { stdio: "inherit", shell: false, env }); }
finally { factWorker?.kill(); }

if (result.error) {
  console.error(result.error.message);
  process.exit(1);
}
process.exit(result.status ?? 1);
