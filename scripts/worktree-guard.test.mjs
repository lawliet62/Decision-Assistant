import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, rmSync, mkdirSync, writeFileSync, readFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { spawn, spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { acquire, release, response, run, worktree, recordPath } from "./worktree-guard.mjs";
import { commonDirectory, dispatch } from "./worktree-guard-dispatch.mjs";

test("worktree ownership persists across events and only its owner can release it", () => {
  const dir = mkdtempSync(join(tmpdir(), "guard-"));
  try {
    const root = worktree(process.cwd());
    const first = { cwd: root, session_id: "first", hook_event_name: "SessionStart" };
    assert.deepEqual(run(first, dir), {});
    assert.deepEqual(run({ ...first, hook_event_name: "PreToolUse" }, dir), {});
    assert.equal(run({ ...first, session_id: "other", hook_event_name: "UserPromptSubmit" }, dir).decision, "block");
    assert.equal(run({ ...first, session_id: "other", hook_event_name: "PreToolUse" }, dir).hookSpecificOutput.permissionDecision, "deny");
    run({ ...first, session_id: "other", hook_event_name: "SessionEnd" }, dir);
    assert.equal(acquire(root, "other", dir), false);
    run({ ...first, hook_event_name: "SessionEnd" }, dir);
    assert.equal(acquire(root, "other", dir), true);
    assert.equal(release(root, "other", dir), true);
  } finally { rmSync(dir, { recursive: true, force: true }); }
});

test("distinct worktrees and subdirectories resolve correctly", () => {
  const dir = mkdtempSync(join(tmpdir(), "guard-git-"));
  try {
    const repo = join(dir, "repo");
    mkdirSync(repo);
    assert.equal(spawnSync("git", ["init", repo]).status, 0);
    assert.equal(spawnSync("git", ["-C", repo, "-c", "user.name=Test", "-c", "user.email=test@example.invalid", "commit", "--allow-empty", "-m", "test"]).status, 0);
    const linked = join(dir, "linked");
    assert.equal(spawnSync("git", ["-C", repo, "worktree", "add", "-b", "other", linked]).status, 0);
    mkdirSync(join(repo, "sub"));
    assert.equal(worktree(repo), worktree(join(repo, "sub")));
    const state = join(dir, "state");
    assert.equal(acquire(worktree(repo), "a", state), true);
    assert.equal(acquire(worktree(linked), "b", state), true);
  } finally { rmSync(dir, { recursive: true, force: true }); }
});

test("damaged records fail closed and decision shapes match hook events", () => {
  const dir = mkdtempSync(join(tmpdir(), "guard-broken-"));
  try {
    writeFileSync(recordPath("root", dir), "");
    assert.throws(() => acquire("root", "a", dir));
    assert.equal(response("SessionStart", false, "blocked").continue, false);
    assert.equal(response("UserPromptSubmit", false, "blocked").decision, "block");
  } finally { rmSync(dir, { recursive: true, force: true }); }
});

test("concurrent processes cannot both acquire the same worktree", async () => {
  const dir = mkdtempSync(join(tmpdir(), "guard-race-"));
  try {
    const moduleUrl = new URL("./worktree-guard.mjs", import.meta.url).href;
    const code = `import { acquire } from ${JSON.stringify(moduleUrl)}; try { console.log(acquire('root', process.argv[1], process.argv[2]) ? 'yes' : 'no'); } catch { console.log('no'); }`;
    const launch = (id) => new Promise((resolve, reject) => {
      const child = spawn(process.execPath, ["--input-type=module", "-e", code, id, dir]);
      let output = "";
      child.stdout.on("data", chunk => { output += chunk; });
      child.on("error", reject);
      child.on("close", () => resolve(output.trim()));
    });
    assert.deepEqual((await Promise.all([launch("a"), launch("b")])).sort(), ["no", "yes"]);
  } finally { rmSync(dir, { recursive: true, force: true }); }
});

test("configured command passes hook input to Node", () => {
  const dir = mkdtempSync(join(tmpdir(), "guard-command-"));
  try {
    const env = { ...process.env, HOME: dir, USERPROFILE: dir };
    const install = spawnSync(process.execPath, [fileURLToPath(new URL("./install-worktree-guard.mjs", import.meta.url))], { cwd: process.cwd(), env, encoding: "utf8" });
    assert.equal(install.status, 0, install.stderr);
    const config = JSON.parse(readFileSync(join(dir, ".codex", "hooks.json"), "utf8"));
    const handler = config.hooks.SessionStart[0].hooks[0];
    const command = handler.command;
    const result = spawnSync(command, { cwd: process.cwd(), shell: true, env, input: JSON.stringify({ cwd: process.cwd(), session_id: "command-test", hook_event_name: "SessionStart" }), encoding: "utf8" });
    assert.equal(result.status, 0, result.stderr);
    assert.deepEqual(JSON.parse(result.stdout), {});
    assert.equal(acquire(worktree(process.cwd()), "other", join(dir, ".codex", "worktree-guard")), false);
  } finally { rmSync(dir, { recursive: true, force: true }); }
});

test("installer refuses to overwrite existing user hooks", () => {
  const dir = mkdtempSync(join(tmpdir(), "guard-existing-"));
  try {
    const configDir = join(dir, ".codex"); mkdirSync(configDir);
    const existing = '{"hooks":{}}';
    writeFileSync(join(configDir, "hooks.json"), existing);
    const result = spawnSync(process.execPath, [fileURLToPath(new URL("./install-worktree-guard.mjs", import.meta.url))], { cwd: process.cwd(), env: { ...process.env, HOME: dir, USERPROFILE: dir }, encoding: "utf8" });
    assert.notEqual(result.status, 0);
    assert.equal(readFileSync(join(configDir, "hooks.json"), "utf8"), existing);
  } finally { rmSync(dir, { recursive: true, force: true }); }
});

test("dispatcher ignores unrelated repositories and preserves linked-worktree identity", () => {
  const dir = mkdtempSync(join(tmpdir(), "guard-dispatch-"));
  try {
    const repo = join(dir, "repo"), unrelated = join(dir, "other"), linked = join(dir, "linked");
    mkdirSync(repo); mkdirSync(unrelated);
    assert.equal(spawnSync("git", ["init", repo]).status, 0);
    assert.equal(spawnSync("git", ["init", unrelated]).status, 0);
    assert.equal(spawnSync("git", ["-C", repo, "-c", "user.name=Test", "-c", "user.email=test@example.invalid", "commit", "--allow-empty", "-m", "test"]).status, 0);
    assert.equal(spawnSync("git", ["-C", repo, "worktree", "add", "-b", "linked", linked]).status, 0);
    const config = join(dir, "repos.json"), state = join(dir, "state");
    writeFileSync(config, JSON.stringify({ commonDirectories: [commonDirectory(repo)] }));
    const input = { cwd: linked, session_id: "a", hook_event_name: "SessionStart" };
    assert.deepEqual(dispatch(input, config, state), {});
    assert.equal(dispatch({ ...input, session_id: "b" }, config, state).continue, false);
    assert.deepEqual(dispatch({ ...input, cwd: unrelated, session_id: "b" }, config, state), {});
  } finally { rmSync(dir, { recursive: true, force: true }); }
});
