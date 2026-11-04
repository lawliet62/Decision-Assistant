import { spawnSync } from "node:child_process";
import { readFileSync, realpathSync } from "node:fs";
import { homedir } from "node:os";
import { join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { response, run } from "./worktree-guard.mjs";

export function commonDirectory(cwd) {
  const result = spawnSync("git", ["-C", cwd, "rev-parse", "--path-format=absolute", "--git-common-dir"], { encoding: "utf8" });
  if (result.status !== 0) return null;
  const path = realpathSync(resolve(result.stdout.trim()));
  return process.platform === "win32" ? path.toLowerCase() : path;
}

export function dispatch(input, configFile, stateDir) {
  const config = JSON.parse(readFileSync(configFile, "utf8"));
  if (!Array.isArray(config.commonDirectories)) throw new Error("저장소 설정이 올바르지 않습니다.");
  const common = commonDirectory(input.cwd);
  if (!common || !config.commonDirectories.includes(common)) return {};
  return run(input, stateDir);
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  let input;
  try {
    input = JSON.parse(readFileSync(0, "utf8"));
    const base = join(homedir(), ".codex");
    console.log(JSON.stringify(dispatch(input, join(base, "worktree-guard-repos.json"), join(base, "worktree-guard"))));
  } catch (error) {
    console.log(JSON.stringify(response(input?.hook_event_name ?? "SessionStart", false, `워크트리 보호 설정 오류: ${error.message}`)));
  }
}
