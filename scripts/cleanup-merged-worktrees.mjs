// PR이 merge된 로컬 worktree를 정리한다. 기본은 dry run이며 `--apply`일 때만 삭제한다.
// `--hook`은 `--apply`와 같지만 짧은 요약만 출력하고 상세 결과를 공통 Git 디렉터리의 로그에 남긴다.
import { spawnSync } from "node:child_process";
import { appendFileSync, existsSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";

export const REPOSITORY = "lawliet62/Decision-Assistant";
export const AGENT_HELPER = "C:\\Users\\dlwld\\Tools\\github-agent\\agent.ps1";
export const MANAGED_PARENTS = [".worktrees", ".claude/worktrees"];
const REGENERABLE_DIRECTORIES = new Set(["node_modules", ".next", "build", "out", "coverage", ".gradle", "test-results", "playwright-report", "__pycache__"]);

export function normalizePath(path, caseInsensitive = process.platform === "win32") {
  const normalized = String(path).replace(/\\/g, "/").replace(/\/+/g, "/").replace(/\/$/, "");
  return caseInsensitive ? normalized.toLowerCase() : normalized;
}

export function parseWorktreeList(porcelain) {
  const worktrees = [];
  for (const block of porcelain.split(/\r?\n\r?\n/)) {
    const lines = block.split(/\r?\n/).filter(Boolean);
    if (lines.length === 0) continue;
    const entry = { path: null, head: null, branch: null, detached: false, bare: false, locked: false, prunable: false };
    for (const line of lines) {
      const [key, ...rest] = line.split(" ");
      const value = rest.join(" ");
      if (key === "worktree") entry.path = value;
      else if (key === "HEAD") entry.head = value;
      else if (key === "branch") entry.branch = value.replace(/^refs\/heads\//, "");
      else if (key === "detached") entry.detached = true;
      else if (key === "bare") entry.bare = true;
      else if (key === "locked") entry.locked = true;
      else if (key === "prunable") entry.prunable = true;
    }
    if (entry.path) worktrees.push(entry);
  }
  return worktrees;
}

// `git status --porcelain -z --ignored` 출력을 추적·untracked 변경과 ignored 항목으로 나눈다.
export function parseStatus(output) {
  const changes = [];
  const ignored = [];
  const records = output.split("\0");
  for (let index = 0; index < records.length; index += 1) {
    const record = records[index];
    if (!record) continue;
    const code = record.slice(0, 2);
    const path = record.slice(3);
    if (code === "!!") ignored.push(path);
    else {
      changes.push(path);
      if (code[0] === "R" || code[0] === "C") index += 1;
    }
  }
  return { changes, ignored };
}

export function isRegenerable(path) {
  const normalized = String(path).replace(/\\/g, "/");
  if (normalized === ".claude/settings.local.json") return true;
  const isDirectory = normalized.endsWith("/");
  const segments = normalized.replace(/\/$/, "").split("/");
  const name = segments.at(-1);
  if (!isDirectory && (name.endsWith(".tsbuildinfo") || name === "next-env.d.ts")) return true;
  const directories = isDirectory ? segments : segments.slice(0, -1);
  return directories.some((segment) => REGENERABLE_DIRECTORIES.has(segment));
}

// 위치·branch·PR 조건만 판정한다. 통과하면 null, 아니면 유지 이유를 반환한다.
export function preStatusKeepReason({ worktree, mainCheckout, currentToplevel, prs, caseInsensitive }) {
  const normalize = (path) => normalizePath(path, caseInsensitive);
  const path = normalize(worktree.path);
  const main = normalize(mainCheckout);
  if (worktree.bare) return "bare 저장소";
  if (path === main) return "main checkout";
  const parent = path.slice(0, path.lastIndexOf("/"));
  if (!MANAGED_PARENTS.some((managed) => parent === `${main}/${normalize(managed)}`)) {
    return "관리 위치(.worktrees/, .claude/worktrees/) 바로 아래가 아님";
  }
  if (path === normalize(currentToplevel)) return "현재 실행 중인 worktree";
  if (worktree.detached || !worktree.branch) return "detached HEAD";
  if (worktree.locked) return "locked worktree";
  if (worktree.prunable) return "디렉터리가 없는 prunable worktree";
  if (!worktree.head) return "HEAD를 알 수 없음";
  const sameBranch = prs.filter((pr) => pr.headRefName === worktree.branch);
  if (sameBranch.length === 0) return "같은 branch 이름의 PR 없음";
  if (matchingMergedPr(worktree, prs)) return null;
  const states = [...new Set(sameBranch.map((pr) => pr.state))].join(", ");
  const mergedNumbers = sameBranch.filter((pr) => pr.state === "MERGED").map((pr) => `#${pr.number}`);
  if (mergedNumbers.length > 0) return `MERGED PR(${mergedNumbers.join(", ")})의 head SHA가 로컬 HEAD와 다름`;
  return `merge된 PR 없음(${states})`;
}

export function matchingMergedPr(worktree, prs) {
  return prs.find((pr) => pr.headRefName === worktree.branch && pr.state === "MERGED" && pr.headRefOid === worktree.head) ?? null;
}

// 작업 트리 상태 조건만 판정한다. 통과하면 null, 아니면 유지 이유를 반환한다.
export function statusKeepReason(status) {
  if (status.error) return `git status 실패: ${status.error}`;
  if (status.changes.length > 0) return `추적 변경 또는 untracked 파일 있음: ${preview(status.changes)}`;
  const kept = status.ignored.filter((path) => !isRegenerable(path));
  if (kept.length > 0) return `재생성할 수 없는 ignored 항목 있음: ${preview(kept)}`;
  return null;
}

// 수집한 데이터만으로 삭제 여부를 결정한다. status는 사전 조건을 통과했을 때만 필요하다.
export function decideWorktree(input) {
  const pre = preStatusKeepReason(input);
  if (pre) return { remove: false, reason: pre };
  if (!input.status) return { remove: false, reason: "작업 트리 상태를 확인하지 않음" };
  const reason = statusKeepReason(input.status);
  if (reason) return { remove: false, reason };
  const pr = matchingMergedPr(input.worktree, input.prs);
  return { remove: true, reason: `PR #${pr.number} MERGED, head SHA 일치, 작업 트리 깨끗함` };
}

export function validatePrList(value) {
  if (!Array.isArray(value)) return false;
  return value.every((pr) => pr && typeof pr === "object" && Number.isInteger(pr.number)
    && typeof pr.state === "string" && typeof pr.headRefName === "string" && typeof pr.headRefOid === "string");
}

export function redact(text) {
  return String(text)
    .replace(/\b(gh[pousr]_|github_pat_)[A-Za-z0-9_]+/g, "[REDACTED]")
    .replace(/\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]*/g, "[REDACTED]");
}

function preview(paths) {
  const shown = paths.slice(0, 5).join(", ");
  return paths.length > 5 ? `${shown} 외 ${paths.length - 5}개` : shown;
}

function git(args, cwd) {
  const result = spawnSync("git", args, { cwd, encoding: "utf8", maxBuffer: 64 * 1024 * 1024 });
  if (result.error) return { ok: false, stdout: "", error: result.error.message };
  return { ok: result.status === 0, stdout: result.stdout, error: (result.stderr || "").trim() };
}

function fetchPrs() {
  if (process.platform !== "win32" || !existsSync(AGENT_HELPER)) {
    return { error: `GitHub helper를 찾을 수 없습니다: ${AGENT_HELPER}` };
  }
  const result = spawnSync("powershell.exe", [
    "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", AGENT_HELPER,
    "exec", "gh", "pr", "list", "--repo", REPOSITORY, "--state", "all", "--limit", "300",
    "--json", "number,state,headRefName,headRefOid",
  ], { encoding: "utf8", timeout: 90_000, maxBuffer: 16 * 1024 * 1024 });
  if (result.error) return { error: `GitHub helper 실행 실패: ${redact(result.error.message)}` };
  if (result.status !== 0) {
    const detail = redact((result.stderr || "").trim().split(/\r?\n/)[0] ?? "").slice(0, 200);
    return { error: `GitHub helper가 exit ${result.status}로 실패했습니다${detail ? `: ${detail}` : ""}` };
  }
  let parsed;
  try { parsed = JSON.parse(result.stdout.trim()); } catch { return { error: "GitHub helper 출력이 JSON이 아닙니다." }; }
  if (!validatePrList(parsed)) return { error: "GitHub helper 출력이 예상한 PR 목록 형식이 아닙니다." };
  return { prs: parsed };
}

function collectStatus(path) {
  const result = git(["-C", path, "status", "--porcelain", "-z", "--ignored"]);
  if (!result.ok) return { error: redact(result.error.split(/\r?\n/)[0] ?? "").slice(0, 200) || "알 수 없는 오류", changes: [], ignored: [] };
  return parseStatus(result.stdout);
}

export function main(argv = process.argv.slice(2)) {
  const hook = argv.includes("--hook");
  const apply = hook || argv.includes("--apply");
  const unknown = argv.filter((arg) => arg !== "--hook" && arg !== "--apply");
  const lines = [];
  const log = (line) => { lines.push(line); if (!hook) console.log(line); };
  let logFile = null;

  const finish = (summary, exitCode) => {
    if (hook) {
      console.log(`[worktree cleanup] ${summary}`);
      if (logFile) {
        try { appendFileSync(logFile, `== ${new Date().toISOString()} ==\n${lines.join("\n")}\n${summary}\n\n`); } catch { /* 로그 실패는 무시한다 */ }
      }
      return 0;
    }
    if (summary) console.log(summary);
    return exitCode;
  };

  if (unknown.length > 0) {
    console.error("Usage: node scripts/cleanup-merged-worktrees.mjs [--apply | --hook]");
    return hook ? 0 : 2;
  }

  const common = git(["rev-parse", "--path-format=absolute", "--git-common-dir"]);
  const toplevel = git(["rev-parse", "--show-toplevel"]);
  if (!common.ok || !toplevel.ok) return finish("Git 저장소를 확인할 수 없어 아무것도 삭제하지 않았습니다.", 1);
  const commonDir = common.stdout.trim();
  const mainCheckout = dirname(commonDir);
  const currentToplevel = toplevel.stdout.trim();
  logFile = join(commonDir, "worktree-cleanup.log");

  const listed = git(["worktree", "list", "--porcelain"]);
  if (!listed.ok) return finish("worktree 목록을 읽지 못해 아무것도 삭제하지 않았습니다.", 1);
  const worktrees = parseWorktreeList(listed.stdout);

  const fetched = fetchPrs();
  if (fetched.error) {
    log(fetched.error);
    return finish("PR 조회에 실패해 아무것도 삭제하지 않았습니다.", 1);
  }
  const prs = fetched.prs;
  log(`${apply ? "적용" : "dry run"}: main checkout ${mainCheckout}, worktree ${worktrees.length}개, PR ${prs.length}건 조회`);

  const targets = [];
  for (const worktree of worktrees) {
    const input = { worktree, mainCheckout, currentToplevel, prs };
    const label = `${worktree.path}${worktree.branch ? ` (${worktree.branch})` : ""}`;
    if (preStatusKeepReason(input)) {
      log(`[유지] ${label} - ${preStatusKeepReason(input)}`);
      continue;
    }
    const decision = decideWorktree({ ...input, status: collectStatus(worktree.path) });
    if (!decision.remove) {
      log(`[유지] ${label} - ${decision.reason}`);
      continue;
    }
    log(`[삭제 대상] ${label} - ${decision.reason}`);
    targets.push(worktree);
  }

  if (!apply) return finish(`삭제 대상 ${targets.length}개. 실제로 삭제하려면 --apply를 붙여 실행하세요.`, 0);

  let removed = 0;
  let failed = 0;
  for (const worktree of targets) {
    const removal = git(["worktree", "remove", worktree.path], mainCheckout);
    if (!removal.ok) {
      failed += 1;
      log(`[실패] ${worktree.path} - worktree remove: ${redact(removal.error.split(/\r?\n/)[0] ?? "")}`);
      continue;
    }
    const branch = git(["branch", "-D", worktree.branch], mainCheckout);
    if (!branch.ok) {
      failed += 1;
      log(`[실패] ${worktree.path} - worktree는 삭제했으나 branch ${worktree.branch} 삭제 실패: ${redact(branch.error.split(/\r?\n/)[0] ?? "")}`);
      continue;
    }
    removed += 1;
    log(`[삭제] ${worktree.path} (${worktree.branch})`);
  }
  const summary = `merge된 worktree ${removed}개 삭제, 실패 ${failed}개${hook ? `. 상세: ${logFile}` : ""}`;
  return finish(summary, failed > 0 ? 1 : 0);
}

if (process.argv[1] && normalizePath(fileURLToPath(import.meta.url)) === normalizePath(resolve(process.argv[1]))) {
  try {
    process.exitCode = main();
  } catch (error) {
    const hook = process.argv.includes("--hook");
    console.log(`[worktree cleanup] 예기치 않은 오류로 중단했습니다: ${redact(error?.message ?? error)}`);
    process.exitCode = hook ? 0 : 1;
  }
}
