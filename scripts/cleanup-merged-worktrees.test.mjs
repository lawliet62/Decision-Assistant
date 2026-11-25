import { test } from "node:test";
import assert from "node:assert/strict";
import {
  decideWorktree, isRegenerable, normalizePath, parseStatus, parseWorktreeList, preStatusKeepReason,
  redact, statusKeepReason, validatePrList,
} from "./cleanup-merged-worktrees.mjs";

const MAIN = "C:/Repo/Decision-Assistant";
const HEAD = "a".repeat(40);
const worktree = (overrides = {}) => ({
  path: `${MAIN}/.worktrees/feature`, head: HEAD, branch: "feat/x",
  detached: false, bare: false, locked: false, prunable: false, ...overrides,
});
const merged = { number: 10, state: "MERGED", headRefName: "feat/x", headRefOid: HEAD };
const clean = { changes: [], ignored: [] };
const input = (overrides = {}) => ({
  worktree: worktree(), mainCheckout: MAIN, currentToplevel: `${MAIN}/.worktrees/current`,
  prs: [merged], status: clean, caseInsensitive: true, ...overrides,
});

test("removes a clean managed worktree whose merged PR head matches local HEAD", () => {
  const decision = decideWorktree(input());
  assert.equal(decision.remove, true);
  assert.match(decision.reason, /#10/);
  assert.equal(decideWorktree(input({ worktree: worktree({ path: `${MAIN}/.claude/worktrees/feature` }) })).remove, true);
});

test("condition 1: only direct children of managed parents qualify", () => {
  for (const path of [
    MAIN,
    "C:/Users/me/.air/worktrees/temporary/decision-assistant-fox",
    `${MAIN}/.worktrees/nested/feature`,
    `${MAIN}/other/feature`,
    "C:/Repo/Decision-Assistant-Other/.worktrees/feature",
  ]) {
    assert.equal(decideWorktree(input({ worktree: worktree({ path }) })).remove, false, path);
  }
  assert.equal(preStatusKeepReason(input({ worktree: worktree({ path: MAIN }) })), "main checkout");
  assert.equal(preStatusKeepReason(input({ worktree: worktree({ detached: true, branch: null }) })), "detached HEAD");
  assert.equal(decideWorktree(input({ worktree: worktree({ locked: true }) })).remove, false);
  assert.equal(decideWorktree(input({ worktree: worktree({ prunable: true }) })).remove, false);
});

test("condition 1: Windows paths compare case- and separator-insensitively", () => {
  const decision = decideWorktree(input({
    worktree: worktree({ path: "c:\\repo\\decision-assistant\\.WORKTREES\\feature\\" }),
    mainCheckout: "C:\\Repo\\Decision-Assistant",
  }));
  assert.equal(decision.remove, true);
  assert.equal(normalizePath("C:\\A\\b\\", false), "C:/A/b");
  assert.equal(normalizePath("C:\\A\\b\\", true), "c:/a/b");
});

test("condition 2: the worktree running the script is kept", () => {
  const reason = preStatusKeepReason(input({ currentToplevel: `${MAIN.toLowerCase()}\\.worktrees\\FEATURE` }));
  assert.equal(reason, "현재 실행 중인 worktree");
});

test("condition 3: only a MERGED PR with the exact head SHA qualifies", () => {
  const other = "b".repeat(40);
  assert.equal(decideWorktree(input({ prs: [] })).remove, false);
  assert.equal(decideWorktree(input({ prs: [{ ...merged, state: "CLOSED" }] })).remove, false);
  assert.equal(decideWorktree(input({ prs: [{ ...merged, state: "OPEN" }] })).remove, false);
  assert.equal(decideWorktree(input({ prs: [{ ...merged, headRefOid: other }] })).remove, false);
  assert.equal(decideWorktree(input({ prs: [{ ...merged, headRefName: "feat/y" }] })).remove, false);
  assert.match(preStatusKeepReason(input({ prs: [{ ...merged, headRefOid: other }] })), /head SHA/);
  const several = [
    { ...merged, number: 1, headRefOid: other },
    { ...merged, number: 2, state: "OPEN" },
    { ...merged, number: 3 },
  ];
  const decision = decideWorktree(input({ prs: several }));
  assert.equal(decision.remove, true);
  assert.match(decision.reason, /#3/);
});

test("condition 4: tracked or untracked changes keep the worktree", () => {
  assert.equal(decideWorktree(input({ status: { changes: ["README.md"], ignored: [] } })).remove, false);
  assert.equal(decideWorktree(input({ status: { changes: [], ignored: [], error: "fatal" } })).remove, false);
  assert.equal(decideWorktree(input({ status: undefined })).remove, false);
  assert.deepEqual(parseStatus(" M a.txt\0?? new/\0R  to.txt\0from.txt\0!! frontend/node_modules/\0"), {
    changes: ["a.txt", "new/", "to.txt"], ignored: ["frontend/node_modules/"],
  });
});

test("condition 5: only regenerable ignored entries are allowed", () => {
  for (const path of [
    "frontend/node_modules/", "frontend/.next/", "backend/build/", "frontend/out/", "frontend/coverage/",
    "backend/.gradle/", "frontend/test-results/", "frontend/playwright-report/", "frontend/tsconfig.tsbuildinfo",
    "frontend/next-env.d.ts", "scripts/__pycache__/", ".claude/settings.local.json", "frontend/node_modules/x/y.js",
  ]) assert.equal(isRegenerable(path), true, path);
  for (const path of ["backend/.env", "frontend/.env.local", ".env", "local-notes/", ".idea/", "build", "notes/out.txt"]) {
    assert.equal(isRegenerable(path), false, path);
  }
  const reason = statusKeepReason({ changes: [], ignored: ["frontend/node_modules/", "backend/.env"] });
  assert.match(reason, /backend\/\.env/);
  assert.equal(decideWorktree(input({ status: { changes: [], ignored: ["backend/.env"] } })).remove, false);
  assert.equal(decideWorktree(input({ status: { changes: [], ignored: ["frontend/node_modules/", "backend/build/"] } })).remove, true);
});

test("parses git worktree porcelain output", () => {
  const output = [
    `worktree ${MAIN}`, `HEAD ${HEAD}`, "branch refs/heads/main", "",
    `worktree ${MAIN}/.worktrees/a b`, `HEAD ${HEAD}`, "detached", "",
    `worktree ${MAIN}/.worktrees/c`, `HEAD ${HEAD}`, "branch refs/heads/feat/c", "locked reason", "",
  ].join("\n");
  const parsed = parseWorktreeList(output);
  assert.equal(parsed.length, 3);
  assert.equal(parsed[0].branch, "main");
  assert.equal(parsed[1].path, `${MAIN}/.worktrees/a b`);
  assert.equal(parsed[1].detached, true);
  assert.equal(parsed[2].branch, "feat/c");
  assert.equal(parsed[2].locked, true);
});

test("validates PR list shape and redacts secrets", () => {
  assert.equal(validatePrList([merged]), true);
  assert.equal(validatePrList({}), false);
  assert.equal(validatePrList([{ number: 1 }]), false);
  assert.equal(redact("token ghs_abc123DEF and eyJhbGci.eyJzdWIi.sig"), "token [REDACTED] and [REDACTED]");
});
