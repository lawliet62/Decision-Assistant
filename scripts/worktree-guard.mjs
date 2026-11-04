import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdirSync, openSync, writeFileSync, closeSync, readFileSync, unlinkSync, realpathSync } from "node:fs";
import { homedir } from "node:os";
import { join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

// State is shared by all worktrees, not stored in a worktree or Git metadata.
export function worktree(cwd) {
  const result = spawnSync("git", ["-C", cwd, "rev-parse", "--show-toplevel"], { encoding: "utf8" });
  if (result.status !== 0) throw new Error("Git worktree를 확인할 수 없습니다.");
  const path = realpathSync(resolve(result.stdout.trim()));
  return process.platform === "win32" ? path.toLowerCase() : path;
}

export function recordPath(root, stateDir) {
  return join(stateDir, `${createHash("sha256").update(root).digest("hex")}.json`);
}

export function acquire(root, session, stateDir) {
  mkdirSync(stateDir, { recursive: true });
  const file = recordPath(root, stateDir);
  let fd;
  try {
    fd = openSync(file, "wx", 0o600);
  } catch (error) {
    if (error.code !== "EEXIST") throw error;
    const owner = JSON.parse(readFileSync(file, "utf8"));
    return owner.root === root && owner.session === session;
  }
  try {
    writeFileSync(fd, JSON.stringify({ root, session, createdAt: new Date().toISOString() }));
  } finally {
    closeSync(fd);
  }
  return true;
}

export function release(root, session, stateDir) {
  const file = recordPath(root, stateDir);
  let owner;
  try { owner = JSON.parse(readFileSync(file, "utf8")); }
  catch (error) { if (error.code === "ENOENT") return false; throw error; }
  if (owner.root !== root || owner.session !== session) return false;
  unlinkSync(file);
  return true;
}

export function response(event, allowed, reason) {
  if (allowed) return {};
  if (event === "PreToolUse") return { hookSpecificOutput: { hookEventName: event, permissionDecision: "deny", permissionDecisionReason: reason } };
  if (event === "UserPromptSubmit") return { decision: "block", reason };
  return { continue: false, stopReason: reason, systemMessage: reason };
}

export function run(input, stateDir = join(homedir(), ".codex", "worktree-guard")) {
  if (!input.session_id || !input.cwd || !input.hook_event_name) throw new Error("필수 Hook 입력이 없습니다.");
  const root = worktree(input.cwd);
  if (input.hook_event_name === "SessionEnd") {
    release(root, input.session_id, stateDir);
    return {};
  }
  const allowed = acquire(root, input.session_id, stateDir);
  return response(input.hook_event_name, allowed, "이 워크트리는 다른 Codex 세션이 점유 중입니다. 별도 워크트리를 사용하세요.");
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const stateDir = join(homedir(), ".codex", "worktree-guard");
  if (process.argv[2] === "inspect" || process.argv[2] === "release") {
    try {
      const root = worktree(process.cwd());
      const file = recordPath(root, stateDir);
      const owner = JSON.parse(readFileSync(file, "utf8"));
      if (process.argv[2] === "inspect") console.log(JSON.stringify(owner, null, 2));
      else {
        if (!process.argv[3] || !release(root, process.argv[3], stateDir)) throw new Error("세션 ID가 점유 기록과 일치하지 않습니다.");
        console.log("점유 기록을 해제했습니다.");
      }
    } catch (error) { console.error(error.message); process.exitCode = 1; }
  } else {
    let input;
    try {
      input = JSON.parse(readFileSync(0, "utf8"));
      console.log(JSON.stringify(run(input)));
    } catch (error) {
      const reason = `워크트리 점유를 확인하지 못했습니다: ${error.message}`;
      console.log(JSON.stringify(response(input?.hook_event_name ?? "SessionStart", false, reason)));
    }
  }
}
