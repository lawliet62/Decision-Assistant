import { test } from "node:test";
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, dirname } from "node:path";
import { StackGit, STATE_REF } from "./git.mjs";
import { GitHub } from "./github.mjs";
import { RestackService, parseNumbers } from "./service.mjs";

function git(cwd, args, input) {
  const result = spawnSync("git", ["-c", "core.autocrlf=false", "-c", "commit.gpgSign=false", ...args], {
    cwd, input, encoding: "utf8", env: { ...process.env, GIT_CONFIG_NOSYSTEM: "1",
      GIT_CONFIG_GLOBAL: process.platform === "win32" ? "NUL" : "/dev/null",
      GIT_AUTHOR_NAME: "Test", GIT_AUTHOR_EMAIL: "test@example.invalid", GIT_COMMITTER_NAME: "Test", GIT_COMMITTER_EMAIL: "test@example.invalid" },
  });
  assert.equal(result.status, 0, result.stderr);
  return result.stdout.trim();
}

function fixture() {
  const directory = mkdtempSync(join(tmpdir(), "restack-test-"));
  const remote = join(directory, "remote.git");
  const work = join(directory, "work");
  mkdirSync(work);
  git(directory, ["init", "--bare", remote]);
  git(work, ["init", "-b", "main"]);
  git(work, ["remote", "add", "origin", remote]);
  function commit(file, content) {
    mkdirSync(dirname(join(work, file)), { recursive: true });
    writeFileSync(join(work, file), content);
    git(work, ["add", "--", file]);
    git(work, ["commit", "-m", `test: ${file}`]);
    return git(work, ["rev-parse", "HEAD"]);
  }
  const main = commit("shared.txt", "base\n");
  git(work, ["switch", "-c", "feat/a"]);
  const a = commit("a.txt", "A\n");
  git(work, ["switch", "-c", "feat/b"]);
  const b = commit("b.txt", "B\n");
  git(work, ["switch", "-c", "feat/c"]);
  const c = commit("c.txt", "C\n");
  git(work, ["push", "origin", "main", "feat/a", "feat/b", "feat/c"]);
  const repo = { full_name: "owner/repository" };
  const pulls = new Map([a, b, c].map((sha, i) => [i + 1, {
    number: i + 1, state: "open", merged: false,
    head: { sha, ref: `feat/${"abc"[i]}`, repo },
    base: { ref: i === 0 ? "main" : `feat/${"abc"[i - 1]}`, sha: i === 0 ? main : [a, b][i - 1], repo },
  }]));
  const reports = new Map();
  const comments = new Map();
  const api = {
    repository: repo.full_name,
    async pull(n) {
      const p = structuredClone(pulls.get(n));
      if (!p.merged && p.state === "open") {
        p.head.sha = git(directory, ["--git-dir", remote, "rev-parse", `refs/heads/${p.head.ref}`]);
      }
      const baseResult = spawnSync("git", ["--git-dir", remote, "rev-parse", `refs/heads/${p.base.ref}`], { encoding: "utf8" });
      if (baseResult.status === 0) p.base.sha = baseResult.stdout.trim();
      return p;
    },
    async openPulls() { return [...pulls.values()].filter(p => p.state === "open"); },
    async setBase(n, base) { pulls.get(n).base.ref = base; },
    async report(p, r) { reports.set(p.number, r); },
    async comment(n, message) { comments.set(n, message); },
    async validation() { return "success"; },
  };
  function squash(number, { deleteBranch = true, autoRetarget = true } = {}) {
    const p = pulls.get(number);
    git(work, ["fetch", "origin"]);
    git(work, ["switch", "main"]);
    git(work, ["merge", "--ff-only", "origin/main"]);
    git(work, ["merge", "--squash", `origin/${p.head.ref}`]);
    git(work, ["commit", "-m", `squash PR ${number}`]);
    git(work, ["push", "origin", "main"]);
    p.head.sha = git(work, ["rev-parse", `origin/${p.head.ref}`]);
    p.merge_commit_sha = git(work, ["rev-parse", "HEAD"]);
    p.merged = true;
    p.state = "closed";
    if (deleteBranch) git(work, ["push", "origin", "--delete", p.head.ref]);
    if (autoRetarget) for (const child of pulls.values()) if (child.base.ref === p.head.ref) child.base.ref = "main";
  }
  const client = () => new StackGit(remote, { allowFile: true });
  const service = (g = client(), options = {}) => new RestackService(g, api, { log() {}, ...options });
  const refs = () => git(directory, ["ls-remote", remote]);
  return { directory, remote, work, main, a, b, c, commit, pulls, api, reports, comments, squash, client, service, refs };
}

test("3단 stack의 순차 squash, 부모 삭제와 자동 retarget, 복구 참조, 재실행", async () => {
  const f = fixture();
  await f.service().register([1, 2, 3]);
  await f.service().reconcile();
  assert.equal(f.reports.get(1).ready, true);
  assert.equal(f.reports.get(2).ready, false);
  const originalTree = git(f.work, ["rev-parse", `${f.c}^{tree}`]);
  f.squash(1);
  await f.service().reconcile();
  assert.equal(f.pulls.get(2).base.ref, "main");
  assert.equal(f.pulls.get(3).base.ref, "feat/b");
  assert.equal(f.reports.get(2).ready, true);
  assert.equal(f.reports.get(3).ready, false);
  let g = f.client(); g.fetch();
  let { state } = g.loadState();
  const [b, c] = state.stacks[0].nodes;
  assert.notEqual(b.head, f.b);
  assert.equal(c.boundary, b.head);
  assert.equal(g.run(["rev-parse", `${c.head}^{tree}`]), originalTree);
  assert.match(f.refs(), new RegExp(`refs/tags/restack/1/${f.a}/2`));
  const stable = f.refs();
  await f.service().reconcile();
  assert.equal(f.refs(), stable);
  f.squash(2, { autoRetarget: false });
  await f.service().reconcile();
  assert.equal(f.pulls.get(3).base.ref, "main");
  assert.equal(f.reports.get(3).ready, true);
  f.squash(3);
  await f.service().reconcile();
  g = f.client(); g.fetch();
  assert.equal(g.loadState().state.stacks[0].nodes.length, 0);
  assert.equal(g.run(["rev-parse", "refs/remotes/origin/main^{tree}"]), originalTree);
});

test("뒤쪽 PR에서 충돌하면 앞 PR을 포함한 원격 refs를 모두 보존한다", async () => {
  const f = fixture();
  // C owns a change in a line that another independent main commit will change.
  f.commit("shared.txt", "child\n");
  git(f.work, ["push", "origin", "feat/c"]);
  await f.service().register([1, 2, 3]);
  f.squash(1);
  f.commit("shared.txt", "main change\n");
  git(f.work, ["push", "origin", "main"]);
  const before = f.refs();
  await assert.rejects(f.service().reconcile(), /수동 조치/);
  assert.equal(f.refs(), before);
  assert.equal(f.reports.get(2).ready, false);
  assert.match(f.comments.get(3), /rebase 충돌/);
});

test("API 부분 실패 후 journal에서 재개하고 커밋을 다시 재작성하지 않는다", async () => {
  const f = fixture();
  await f.service().register([1, 2, 3]);
  f.squash(1, { autoRetarget: false });
  const original = f.api.setBase;
  f.api.setBase = async () => { throw new Error("API fixture failure"); };
  await assert.rejects(f.service().reconcile(), /수동 조치/);
  let g = f.client(); g.fetch();
  const first = g.loadState().state.stacks[0];
  assert.ok(first.journal);
  f.api.setBase = original;
  await f.service().reconcile();
  g = f.client(); g.fetch();
  const next = g.loadState().state.stacks[0];
  assert.equal(next.journal, null);
  assert.deepEqual(next.nodes.map(n => n.head), first.nodes.map(n => n.head));
  assert.equal(f.pulls.get(2).base.ref, "main");
});

test("동시 push와 atomic 거부가 브랜치·백업·상태의 부분 갱신을 막는다", async () => {
  const f = fixture();
  const g = f.client(); g.fetch();
  const loaded = g.loadState();
  git(f.work, ["switch", "feat/b"]);
  const changed = f.commit("b.txt", "B concurrent\n");
  git(f.work, ["push", "origin", "feat/b"]);
  const before = f.refs();
  assert.throws(() => g.publish(loaded.state, loaded.sha,
    [{ branch: "feat/b", old: f.b, next: f.c }, { branch: "feat/c", old: f.c, next: f.b }],
    [{ ref: `refs/tags/restack/1/${f.a}/2`, sha: f.b }]), /Git push 실패/);
  assert.equal(f.refs(), before);
  assert.match(f.refs(), new RegExp(changed));
});

test("서버가 atomic push를 지원하지 않으면 일반 push로 대체하지 않는다", () => {
  const f = fixture();
  git(f.directory, ["--git-dir", f.remote, "config", "receive.advertiseAtomic", "false"]);
  const g = f.client(); g.fetch();
  const before = f.refs();
  assert.throws(() => g.publish({ version: 1, stacks: [] }, null), /Git push 실패/);
  assert.equal(f.refs(), before);
});

test("dry-run은 실제 재배치를 검증하지만 refs와 PR을 변경하지 않는다", async () => {
  const f = fixture();
  const beforeRegistration = f.refs();
  await f.service(undefined, { dryRun: true }).register([1, 2, 3]);
  assert.equal(f.refs(), beforeRegistration);
  await f.service().register([1, 2, 3]);
  f.squash(1, { autoRetarget: false });
  const before = f.refs();
  await f.service(undefined, { dryRun: true }).reconcile();
  assert.equal(f.refs(), before);
  assert.equal(f.pulls.get(2).base.ref, "feat/a");
  assert.equal(f.comments.size, 0);
});

test("등록 뒤 부모 수정은 추정하지 않고 정지하며 명시적 전체 재등록만 허용한다", async () => {
  const f = fixture();
  await f.service().register([1, 2, 3]);
  git(f.work, ["switch", "feat/a"]);
  f.commit("a.txt", "changed A\n");
  git(f.work, ["push", "origin", "feat/a"]);
  f.squash(1);
  const before = f.refs();
  await assert.rejects(f.service().reconcile(), /수동 조치/);
  assert.equal(f.refs(), before);
});

test("등록: fork, 잘못된 base, 분기형 중복, workflow 변경과 main 갱신을 거부한다", async () => {
  const f = fixture();
  f.pulls.get(3).head.repo = { full_name: "other/repo" };
  await assert.rejects(f.service().register([1, 2, 3]), /같은 저장소/);
  f.pulls.get(3).head.repo = f.pulls.get(1).head.repo;
  f.pulls.get(2).base.ref = "main";
  await assert.rejects(f.service().register([1, 2, 3]), /base는/);
  f.pulls.get(2).base.ref = "feat/a";
  await f.service().register([1, 2, 3]);
  await assert.rejects(f.service().register([1, 2]), /다른 stack/);
  const g = f.client(); g.fetch();
  assert.throws(() => g.publish({ version: 1, stacks: [] }, null, [{ branch: "main", old: f.main, next: f.a }]), /자동 갱신/);
  f.commit(".github/workflows/untrusted.yml", "name: unsafe\n");
  git(f.work, ["push", "origin", "feat/c"]);
  await assert.rejects(f.service().register([1, 2, 3]), /workflow 변경/);
});

test("빈 변경과 merge commit은 자동 재배치 대상에서 제외한다", () => {
  const f = fixture();
  git(f.work, ["commit", "--allow-empty", "-m", "empty"]);
  const empty = git(f.work, ["rev-parse", "HEAD"]);
  git(f.work, ["switch", "-c", "feat/side", f.b]);
  f.commit("side.txt", "side\n");
  git(f.work, ["switch", "feat/c"]);
  git(f.work, ["merge", "--no-ff", "feat/side", "-m", "merge"]);
  const merged = git(f.work, ["rev-parse", "HEAD"]);
  git(f.work, ["push", "origin", "feat/c"]);
  const g = f.client(); g.fetch();
  assert.throws(() => g.range(f.c, empty), /빈 commit/);
  assert.throws(() => g.range(f.b, merged), /merge commit/);
});

test("CI 실패 후 자동 rollback하지 않고 독립 PR은 정상 처리한다", async () => {
  const f = fixture();
  await f.service().reconcile();
  assert.equal(f.reports.get(1).ready, true);
  assert.equal(f.reports.get(2).ready, false);
  await f.service().register([1, 2, 3]);
  f.squash(1);
  f.api.validation = async () => "failure";
  await f.service().reconcile();
  const after = f.refs();
  assert.equal(f.reports.get(2).title, "CI 실패");
  await f.service().reconcile();
  assert.equal(f.refs(), after);
});

test("충돌을 수동 해결한 뒤에는 main에 병합된 prefix만 빼고 열린 stack을 다시 등록한다", async () => {
  const f = fixture();
  await f.service().register([1, 2, 3]);
  f.squash(1);
  const g = f.client(); g.fetch();
  const main = g.ref("refs/remotes/origin/main");
  const b = g.replay(f.a, f.b, main);
  const c = g.replay(f.b, f.c, b);
  g.run(["push", "--atomic", `--force-with-lease=refs/heads/feat/b:${f.b}`,
    `--force-with-lease=refs/heads/feat/c:${f.c}`, "origin", `${b}:refs/heads/feat/b`, `${c}:refs/heads/feat/c`]);
  await f.service().register([2, 3]);
  await f.service().reconcile();
  assert.equal(f.reports.get(2).ready, true);
  assert.equal(f.reports.get(3).ready, false);
  await assert.rejects(f.service().register([2]), /다른 stack/);
});

test("일반 main 보호만으로 활성화하지 않고 App의 main 갱신 제한까지 확인한다", async () => {
  let rules = [{ type: "pull_request" }, { type: "required_status_checks", parameters: { required_status_checks: [{ context: "validate" }] } }];
  let bypass = "never";
  const api = new GitHub({ repository: "owner/repo", token: "fixture", appId: 7, appSlug: "restack",
    request: async url => ({ ok: true, json: async () => url.endsWith("/rules/branches/main") ? rules : url.includes("/rulesets/") ?
      { enforcement: "active", current_user_can_bypass: bypass } :
      { default_branch: "main", allow_squash_merge: true, allow_merge_commit: false, allow_rebase_merge: false } }) });
  await assert.rejects(api.preflight(), /main 갱신 차단/);
  rules = [...rules, { type: "update", ruleset_id: 123 }];
  await api.preflight();
  for (bypass of ["always", "pull_requests_only", undefined]) {
    await assert.rejects(api.preflight(), /우회할 수 없다는/);
  }
});

test("한 stack의 예상 밖 head 변경이 다른 독립 stack의 restack을 막지 않는다", async () => {
  const f = fixture();
  git(f.work, ["switch", "-c", "feat/d", "main"]);
  const d = f.commit("d.txt", "D\n");
  git(f.work, ["switch", "-c", "feat/e"]);
  const e = f.commit("e.txt", "E\n");
  git(f.work, ["push", "origin", "feat/d", "feat/e"]);
  const repo = f.pulls.get(1).head.repo;
  f.pulls.set(4, { number: 4, state: "open", merged: false, head: { ref: "feat/d", sha: d, repo }, base: { ref: "main", sha: f.main, repo } });
  f.pulls.set(5, { number: 5, state: "open", merged: false, head: { ref: "feat/e", sha: e, repo }, base: { ref: "feat/d", sha: d, repo } });
  await f.service().register([1, 2, 3]);
  await f.service().register([4, 5]);
  git(f.work, ["switch", "feat/a"]);
  f.commit("a.txt", "changed\n");
  git(f.work, ["push", "origin", "feat/a"]);
  f.squash(4);
  await assert.rejects(f.service().reconcile(), /수동 조치/);
  assert.equal(f.reports.get(1).ready, false);
  assert.equal(f.reports.get(5).ready, true);
  assert.equal(f.pulls.get(5).base.ref, "main");
});

test("PR 번호 입력은 명령/표현식 대신 숫자 목록만 허용한다", () => {
  assert.deepEqual(parseNumbers("21,22,23"), [21, 22, 23]);
  assert.deepEqual(parseNumbers("23"), [23]);
  for (const bad of ["", "1,1", "0,2", "1,2;echo x", "1,$(pwd)", "1,2\n", "1,9007199254740992"]) {
    assert.throws(() => parseNumbers(bad));
  }
});

test("현재 head/base의 최신 CI만 신뢰하고 재실행 중·실패를 통과시키지 않는다", async () => {
  const head = "a".repeat(40), base = "b".repeat(40);
  const pull = { number: 2, head: { sha: head }, base: { ref: "main", sha: base } };
  const run = { id: 1, event: "pull_request", head_sha: head, status: "completed", conclusion: "success",
    pull_requests: [{ number: 2, head: { sha: head }, base: { ref: "main", sha: base } }] };
  let runs = [run];
  const jobs = [{ name: "validate", conclusion: "success", steps: [
    { name: `Restack snapshot ${head} ${base} main`, conclusion: "success" },
  ] }];
  const api = new GitHub({ repository: "owner/repo", token: "fixture", appId: 1, appSlug: "restack",
    request: async url => ({ ok: true, json: async () => url.includes("/jobs?") ? { jobs } : { workflow_runs: runs } }) });
  assert.equal(await api.validation(pull), "success");
  assert.equal(await api.validation({ ...pull, base: { ...pull.base, sha: "c".repeat(40) } }), "pending");
  assert.equal(await api.validation({ ...pull, base: { ...pull.base, ref: "feat/a" } }), "pending");
  // GitHub can retroactively update the PR association of an old successful run.
  run.pull_requests[0].base.sha = "c".repeat(40);
  assert.equal(await api.validation({ ...pull, base: { ...pull.base, sha: "c".repeat(40) } }), "pending");
  runs = [run, { ...run, id: 2, status: "in_progress", conclusion: null }];
  assert.equal(await api.validation(pull), "pending");
  runs[1].status = "completed"; runs[1].conclusion = "failure";
  assert.equal(await api.validation(pull), "failure");
});

test("GitHub 상태는 App 소유 check/comment만 수정하고 토큰을 메시지에 노출하지 않는다", async () => {
  const requests = [];
  const api = new GitHub({ repository: "owner/repo", token: "fixture-secret", appId: 7, appSlug: "restack",
    request: async (url, options) => {
      requests.push({ url, options });
      return { ok: true, json: async () => url.includes("check-runs?") ? { check_runs: [
        { id: 1, app: { id: 9 }, external_id: "2" }, { id: 2, app: { id: 7 }, external_id: "2" },
      ] } : url.endsWith("comments?per_page=100&page=1") ? [
        { id: 3, user: { login: "human" }, body: "<!-- decision-assistant:stack-status -->" },
      ] : {} };
    } });
  await api.report({ number: 2, head: { sha: "a".repeat(40) } }, { ready: false, title: "대기", summary: "대기" });
  await api.comment(2, "대기");
  assert.ok(requests.some(r => r.url.endsWith("/check-runs/2") && r.options.method === "PATCH"));
  assert.ok(requests.some(r => r.url.endsWith("/issues/2/comments") && r.options.method === "POST"));
  assert.ok(requests.every(r => !r.options.body?.includes("fixture-secret")));
});

test("권한 있는 실행은 main 코드만 사용하며 PR 코드·artifact를 실행하지 않는다", () => {
  const workflow = readFileSync(new URL("../../.github/workflows/restack.yml", import.meta.url), "utf8");
  assert.match(workflow, /ref: main/);
  assert.match(workflow, /persist-credentials: false/);
  assert.doesNotMatch(workflow, /pull_request_target|download-artifact|permission-workflows|npm (ci|run)|gh pr merge/);
  assert.match(workflow, /cancel-in-progress: false/);
  assert.match(workflow, /RESTACK_ENABLED/);
  assert.equal(STATE_REF, "refs/heads/automation/stack-state");
});
