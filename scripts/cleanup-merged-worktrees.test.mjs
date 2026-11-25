import { test } from "node:test";
import assert from "node:assert/strict";
import {
  buildHookMessage, decideWorktree, defaultAirRoot, hookOutput, isRegenerable, normalizePath, parseStatus,
  parseWorktreeList, preStatusKeepReason, redact, statusKeepReason, validatePrList, worktreeLocation,
} from "./cleanup-merged-worktrees.mjs";

const MAIN = "C:/Repo/Decision-Assistant";
const AIR = "C:/Users/me/.air/worktrees/temporary";
const HEAD = "a".repeat(40);
const worktree = (overrides = {}) => ({
  path: `${MAIN}/.worktrees/feature`, head: HEAD, branch: "feat/x",
  detached: false, bare: false, locked: false, prunable: false, ...overrides,
});
const merged = { number: 10, state: "MERGED", headRefName: "feat/x", headRefOid: HEAD };
const clean = { changes: [], ignored: [] };
const input = (overrides = {}) => ({
  worktree: worktree(), mainCheckout: MAIN, currentToplevel: `${MAIN}/.worktrees/current`,
  prs: [merged], status: clean, airRoot: AIR, caseInsensitive: true, ...overrides,
});
const airInput = (overrides = {}) => input({ worktree: worktree({ path: `${AIR}/decision-assistant-fox` }), ...overrides });

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
    ".idea/", "backend/.idea/workspace.xml", "decision-assistant.iml", "backend/backend.iml",
  ]) assert.equal(isRegenerable(path), true, path);
  for (const path of ["backend/.env", "frontend/.env.local", ".env", "local-notes/", ".iml/", "build", "notes/out.txt"]) {
    assert.equal(isRegenerable(path), false, path);
  }
  const reason = statusKeepReason({ changes: [], ignored: ["frontend/node_modules/", "backend/.env"] });
  assert.match(reason, /backend\/\.env/);
  assert.equal(decideWorktree(input({ status: { changes: [], ignored: ["backend/.env"] } })).remove, false);
  assert.equal(decideWorktree(input({ status: { changes: [], ignored: ["frontend/node_modules/", "backend/build/"] } })).remove, true);
});

test("Air: only direct children of the Air root are classified as Air worktrees", () => {
  assert.equal(worktreeLocation(airInput()), "air");
  assert.equal(worktreeLocation(input({ worktree: worktree({ path: "c:\\users\\ME\\.AIR\\worktrees\\Temporary\\fox\\" }) })), "air");
  for (const path of [
    AIR,
    `${AIR}/fox/.worktrees/nested`,
    `${AIR}/fox/nested`,
    "C:/Users/me/.air/worktrees/fox",
    "C:/Users/me/.air/worktrees/temporary-other/fox",
    "C:/Users/other/.air/worktrees/temporary/fox",
  ]) {
    assert.equal(worktreeLocation(input({ worktree: worktree({ path }) })), null, path);
    const decision = decideWorktree(input({ worktree: worktree({ path }) }));
    assert.equal(decision.remove, false, path);
    assert.equal(decision.air, undefined, path);
  }
  assert.equal(worktreeLocation(airInput({ airRoot: undefined })), null);
  assert.equal(normalizePath(defaultAirRoot("C:\\Users\\me"), true), "c:/users/me/.air/worktrees/temporary");
});

test("Air: a merged clean worktree is reported as ready, a merged dirty one as dirty", () => {
  const ready = decideWorktree(airInput());
  assert.equal(ready.remove, false);
  assert.equal(ready.air, "ready");
  assert.equal(ready.pr, 10);
  const dirty = decideWorktree(airInput({ status: { changes: ["README.md"], ignored: [] } }));
  assert.equal(dirty.air, "dirty");
  assert.equal(decideWorktree(airInput({ status: { changes: [], ignored: ["backend/.env"] } })).air, "dirty");
  assert.equal(decideWorktree(airInput({ status: { changes: [], ignored: ["frontend/node_modules/"] } })).air, "ready");
});

test("Air: worktrees without an exact merged head, detached or unchecked are not reported", () => {
  for (const overrides of [
    { prs: [] },
    { prs: [{ ...merged, state: "OPEN" }] },
    { prs: [{ ...merged, headRefOid: "b".repeat(40) }] },
    { worktree: worktree({ path: `${AIR}/fox`, detached: true, branch: null }) },
    { status: undefined },
    { status: { changes: [], ignored: [], error: "fatal" } },
  ]) {
    const decision = decideWorktree(airInput(overrides));
    assert.equal(decision.remove, false);
    assert.equal(decision.air, undefined);
  }
});

test("Air: decideWorktree never removes an Air worktree", () => {
  const statuses = [clean, { changes: ["a"], ignored: [] }, { changes: [], ignored: ["x/.env"] }, { changes: [], ignored: [], error: "e" }, undefined];
  const prLists = [[merged], [], [{ ...merged, state: "CLOSED" }], [{ ...merged, headRefOid: "c".repeat(40) }]];
  for (const status of statuses) {
    for (const prs of prLists) {
      for (const flags of [{}, { locked: true }, { prunable: true }, { detached: true }]) {
        const decision = decideWorktree(airInput({ status, prs, worktree: worktree({ path: `${AIR}/fox`, ...flags }) }));
        assert.equal(decision.remove, false);
      }
    }
  }
});

test("hook message summarizes removals and Air notices, or is empty", () => {
  assert.equal(buildHookMessage({}), null);
  assert.equal(hookOutput(null), null);
  assert.equal(buildHookMessage({ removed: 2, failed: 0 }), "worktree 정리: 2개 삭제, 실패 0개.");
  const airReady = ["a", "b", "c", "d", "e", "f", "g"].map((name, index) => ({ name, pr: index + 1 }));
  const message = buildHookMessage({ airReady, airDirty: [{ name: "young-parrot", pr: 85 }] });
  assert.equal(message, "Air 세션 완료로 정리 가능: a(#1), b(#2), c(#3), d(#4), e(#5) 외 2개 / merge됐지만 로컬 변경 있음: young-parrot(#85)");
  assert.match(buildHookMessage({ removed: 1, failed: 1, logFile: "L" }), /실패 1개\. 상세: L$/);
  assert.equal(buildHookMessage({ error: "PR 조회 실패", logFile: "L" }), "worktree 정리: PR 조회 실패 상세: L");
  const line = hookOutput(message);
  assert.equal(line.includes("\n"), false);
  assert.deepEqual(JSON.parse(line), { systemMessage: message });
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
