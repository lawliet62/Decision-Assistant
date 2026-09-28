import { spawnSync } from "node:child_process";
import { mkdtempSync, mkdirSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

export const STATE_REF = "refs/heads/automation/stack-state";
export const SHA = /^[0-9a-f]{40}$/;

export function requireSha(value) {
  if (!SHA.test(value ?? "")) throw new Error("유효하지 않은 commit SHA");
  return value;
}

// All mutations happen in a disposable repository, never in the developer's checkout.
export class StackGit {
  constructor(remote, { token, directory, allowFile = false } = {}) {
    this.directory = directory ?? mkdtempSync(join(tmpdir(), "decision-restack-"));
    this.remote = remote;
    this.allowFile = allowFile;
    this.env = { ...process.env, GIT_TERMINAL_PROMPT: "0", GIT_CONFIG_NOSYSTEM: "1",
      GIT_CONFIG_GLOBAL: process.platform === "win32" ? "NUL" : "/dev/null",
      GIT_AUTHOR_NAME: "Decision Assistant Restack", GIT_AUTHOR_EMAIL: "restack@users.noreply.github.com",
      GIT_COMMITTER_NAME: "Decision Assistant Restack", GIT_COMMITTER_EMAIL: "restack@users.noreply.github.com" };
    // Do not allow inherited Git settings to redirect our repository/index or inject commands.
    for (const key of Object.keys(this.env)) {
      if (/^GIT_(DIR|WORK_TREE|INDEX_FILE|OBJECT_DIRECTORY|ALTERNATE_OBJECT_DIRECTORIES|CONFIG_COUNT|CONFIG_KEY_.*|CONFIG_VALUE_.*|CONFIG_PARAMETERS|SSH.*|ASKPASS)$/.test(key)) delete this.env[key];
    }
    delete this.env.GH_TOKEN;
    delete this.env.GITHUB_TOKEN;
    delete this.env.RESTACK_TOKEN;
    mkdirSync(join(this.directory, "empty-hooks"), { recursive: true });
    this.hooks = join(this.directory, "empty-hooks");
    this.repo = join(this.directory, "repo");
    mkdirSync(this.repo, { recursive: true });
    if (token) {
      // The token is supplied only to Git children, never stored in the remote/config.
      const helper = join(this.directory, "askpass.sh");
      writeFileSync(helper, '#!/bin/sh\ncase "$1" in *Username*) printf "%s\\n" x-access-token ;; *) printf "%s\\n" "$RESTACK_GIT_TOKEN" ;; esac\n', { mode: 0o700 });
      this.env.GIT_ASKPASS = helper;
      this.env.RESTACK_GIT_TOKEN = token;
    }
    this.run(["init", "--quiet"]);
    this.run(["remote", "add", "origin", remote]);
  }

  run(args, { input, cwd = this.repo, allowFailure = false } = {}) {
    const result = spawnSync("git", ["-c", `core.hooksPath=${this.hooks}`, "-c", "credential.helper=",
      "-c", "protocol.allow=never", "-c", "protocol.https.allow=always",
      ...(this.allowFile ? ["-c", "protocol.file.allow=always"] : []),
      "-c", "rerere.enabled=false", "-c", "commit.gpgSign=false", "-c", "core.autocrlf=false", ...args],
    { cwd, env: this.env, input, encoding: "utf8", maxBuffer: 16 * 1024 * 1024, timeout: 120_000 });
    if (result.error || (result.status !== 0 && !allowFailure)) {
      // Avoid echoing credentials or untrusted Git output into privileged Actions logs.
      throw new Error(`Git ${args[0]} 실패 (exit ${result.status ?? "timeout"})`);
    }
    return allowFailure ? result : result.stdout.trim();
  }

  branch(name) {
    if (typeof name !== "string" || name === "main" || name.startsWith("automation/") || name.startsWith("-")) throw new Error("자동 갱신할 수 없는 브랜치");
    this.run(["check-ref-format", `refs/heads/${name}`]);
    return name;
  }

  fetch() {
    this.run(["fetch", "--quiet", "--no-tags", "origin", "+refs/heads/*:refs/remotes/origin/*"]);
  }

  ref(ref) {
    const result = this.run(["rev-parse", "--verify", ref], { allowFailure: true });
    return result.status === 0 ? requireSha(result.stdout.trim()) : null;
  }

  remoteRefs() {
    return new Map(this.run(["ls-remote", "--heads", "origin"]).split("\n").filter(Boolean)
      .map(line => { const [sha, ref] = line.split(/\s+/); return [ref, requireSha(sha)]; }));
  }

  ancestor(older, newer) {
    return this.run(["merge-base", "--is-ancestor", requireSha(older), requireSha(newer)], { allowFailure: true }).status === 0;
  }

  ensureObject(sha) {
    requireSha(sha);
    if (this.run(["cat-file", "-e", `${sha}^{commit}`], { allowFailure: true }).status !== 0) {
      this.run(["fetch", "--quiet", "--no-tags", "origin", sha]);
    }
  }

  range(base, head) {
    if (!this.ancestor(base, head)) throw new Error("기존 기반이 head의 조상이 아닙니다");
    if (this.run(["rev-list", "--merges", `${base}..${head}`])) throw new Error("merge commit이 있는 stack은 수동 정리가 필요합니다");
    const commits = this.run(["rev-list", "--reverse", `${base}..${head}`]).split("\n").filter(Boolean);
    if (!commits.length) throw new Error("고유 변경이 없는 PR입니다");
    for (const commit of commits) {
      if (!this.run(["diff-tree", "--no-commit-id", "--name-only", "-r", "--no-renames", commit])) throw new Error("빈 commit은 자동 처리하지 않습니다");
      if (this.run(["diff-tree", "--no-commit-id", "--name-only", "-r", "--no-renames", commit, "--", ".github/workflows"])) {
        throw new Error("workflow 변경은 수동 restack이 필요합니다");
      }
    }
    return commits;
  }

  replay(base, head, onto) {
    requireSha(onto);
    const before = this.range(base, head);
    this.run(["checkout", "--quiet", "--detach", head]);
    const result = this.run(["rebase", "--onto", onto, base, "--reapply-cherry-picks", "--empty=stop",
      "--no-autosquash", "--no-autostash", "--no-update-refs", "--no-rebase-merges"], { allowFailure: true });
    if (result.status !== 0) {
      const conflicts = this.run(["diff", "--name-only", "--diff-filter=U", "-z"], { allowFailure: true }).stdout
        .split("\0").filter(Boolean).slice(0, 20);
      this.run(["rebase", "--abort"], { allowFailure: true });
      throw new Error(`rebase 충돌 또는 빈 변경: 원격을 갱신하지 않았습니다${conflicts.length ? ` (${JSON.stringify(conflicts)})` : ""}`);
    }
    const after = this.ref("HEAD");
    if (this.range(onto, after).length !== before.length) throw new Error("재배치 중 commit 수가 달라졌습니다");
    return after;
  }

  loadState() {
    const sha = this.ref("refs/remotes/origin/automation/stack-state");
    if (!sha) return { sha: null, state: { version: 1, stacks: [] } };
    const state = JSON.parse(this.run(["show", `${sha}:state.json`]));
    if (state.version !== 1 || !Array.isArray(state.stacks)) throw new Error("지원하지 않는 stack 상태 형식");
    return { sha, state };
  }

  stateCommit(state, parent) {
    const blob = this.run(["hash-object", "-w", "--stdin"], { input: JSON.stringify(state, null, 2) + "\n" });
    const tree = this.run(["mktree"], { input: `100644 blob ${blob}\tstate.json\n` });
    return this.run(["commit-tree", tree, ...(parent ? ["-p", requireSha(parent)] : []), "-m", "chore: stack 자동화 상태 갱신"]);
  }

  publish(state, stateSha, updates = [], backups = []) {
    const next = this.stateCommit(state, stateSha);
    const changes = [...updates.map(u => ({ ref: `refs/heads/${this.branch(u.branch)}`, old: requireSha(u.old), next: requireSha(u.next) })),
      ...backups.map(b => {
        if (!/^refs\/tags\/restack\/\d+\/[0-9a-f]{40}\/\d+$/.test(b.ref)) throw new Error("유효하지 않은 복구 참조");
        return { ref: b.ref, old: null, next: requireSha(b.sha) };
      }), { ref: STATE_REF, old: stateSha, next }];
    this.run(["push", "--atomic", ...changes.map(c => `--force-with-lease=${c.ref}:${c.old ?? ""}`),
      "origin", ...changes.map(c => `${c.next}:${c.ref}`)]);
    this.run(["update-ref", "refs/remotes/origin/automation/stack-state", next]);
    return next;
  }
}
