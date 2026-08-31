import { spawnSync } from "node:child_process";
import process from "node:process";

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
const env = tasks.includes("bootRun")
  ? { ...process.env, SPRING_PROFILES_ACTIVE: process.env.SPRING_PROFILES_ACTIVE ?? "local" }
  : process.env;
const result = spawnSync(executable, args, { stdio: "inherit", shell: false, env });

if (result.error) {
  console.error(result.error.message);
  process.exit(1);
}
process.exit(result.status ?? 1);
