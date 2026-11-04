import { copyFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { homedir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { commonDirectory } from "./worktree-guard-dispatch.mjs";

const common = commonDirectory(process.cwd());
if (!common) throw new Error("Git 저장소에서 실행해야 합니다.");
const base = join(homedir(), ".codex");
const runtime = join(base, "worktree-guard-runtime");
const hookFile = join(base, "hooks.json");
const configFile = join(base, "worktree-guard-repos.json");
const marker = "worktree-guard managed hooks";
if (existsSync(hookFile) && JSON.parse(readFileSync(hookFile, "utf8")).description !== marker) {
  throw new Error("기존 사용자 hooks.json을 덮어쓰지 않습니다. 수동 통합이 필요합니다.");
}
const config = existsSync(configFile) ? JSON.parse(readFileSync(configFile, "utf8")) : { commonDirectories: [] };
if (!Array.isArray(config.commonDirectories)) throw new Error("저장소 설정이 올바르지 않습니다.");
if (!config.commonDirectories.includes(common)) config.commonDirectories.push(common);
mkdirSync(runtime, { recursive: true });
const source = dirname(fileURLToPath(import.meta.url));
for (const file of ["worktree-guard.mjs", "worktree-guard-dispatch.mjs"]) copyFileSync(join(source, file), join(runtime, file));
writeFileSync(configFile, JSON.stringify(config, null, 2) + "\n");
const program = join(runtime, "worktree-guard-dispatch.mjs");
const quoted = process.platform === "win32" ? `"${program}"` : `'${program.replaceAll("'", "'\\''")}'`;
const command = `node ${quoted}`;
const hooks = {};
for (const event of ["SessionStart", "UserPromptSubmit", "PreToolUse", "SessionEnd"]) {
  hooks[event] = [{ hooks: [{ type: "command", command, ...(event === "SessionEnd" ? { timeout: 3 } : {}) }] }];
}
writeFileSync(hookFile, JSON.stringify({ description: marker, hooks }, null, 2) + "\n");
console.log("사용자 Hook을 설치했습니다. Codex /hooks에서 검토하고 신뢰하세요.");
