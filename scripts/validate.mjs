import { spawnSync } from "node:child_process";
import process from "node:process";

const mode = process.argv[2] ?? "full";
if (!new Set(["quick", "full"]).has(mode)) {
  console.error("Usage: node scripts/validate.mjs [quick|full]");
  process.exit(2);
}

const isWindows = process.platform === "win32";
const gradle = isWindows ? "backend\\gradlew.bat" : "./backend/gradlew";
const npm = isWindows ? "npm.cmd" : "npm";

const commands = [
  [process.execPath, ["--test", "scripts/backend-env.test.mjs", "scripts/restack/restack.test.mjs", "scripts/discovery-evaluation/summarize.test.mjs", "scripts/discovery-evaluation/plan.test.mjs", "scripts/catalog/report.test.mjs", "scripts/catalog/check-sources.test.mjs", "scripts/worktree-guard.test.mjs"]],
  [gradle, ["-p", "backend", "test"]],
  [npm, ["--prefix", "frontend", "run", "lint"]],
  [npm, ["--prefix", "frontend", "run", "typecheck"]],
];

if (mode === "full") {
  commands.push(
    [gradle, ["-p", "backend", "integrationTest"]],
    [process.execPath, ["scripts/check-contract.mjs"]],
    [npm, ["--prefix", "frontend", "run", "build"]],
  );
}

for (const [command, args] of commands) {
  console.log(`> ${command} ${args.join(" ")}`);
  const windowsShim = isWindows && /\.(?:bat|cmd)$/i.test(command);
  const executable = windowsShim ? (process.env.ComSpec ?? "cmd.exe") : command;
  const executableArgs = windowsShim ? ["/d", "/s", "/c", command, ...args] : args;
  const result = spawnSync(executable, executableArgs, { stdio: "inherit", shell: false });
  if (result.error) {
    console.error(result.error.message);
    process.exit(1);
  }
  if (result.status !== 0) {
    process.exit(result.status ?? 1);
  }
}
