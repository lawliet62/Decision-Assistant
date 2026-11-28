import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { changeEntry, collectHistory, formatRange, latestVersion, migrationSummary, nextVersion, renderNotes, schemaVersion } from "./notes.mjs";

const dir = "backend/src/main/resources/db/migration/";
const files = numbers => numbers.map(n => `${dir}V${n}__step_${n}.sql`);

test("version 입력이 비면 가장 높은 semver tag의 patch를 올린다", () => {
  assert.equal(nextVersion(["v0.2.0", "v0.1.9", "v0.10.0-rc", "release"], ""), "v0.2.1");
  assert.equal(nextVersion(["v0.9.0", "v0.10.0"]), "v0.10.1");
  assert.equal(nextVersion(["v1.2.9"], "  "), "v1.2.10");
  assert.equal(latestVersion(["v2.0.0", "v10.0.0", "v9.9.9"]), "v10.0.0");
});

test("입력한 version은 형식과 중복을 검사한다", () => {
  assert.equal(nextVersion(["v0.2.0"], "v0.3.0"), "v0.3.0");
  assert.equal(nextVersion(["v0.2.0"], " v1.0.0 "), "v1.0.0");
  for (const bad of ["0.3.0", "v0.3", "v0.3.0-rc.1", "v01.2.3", "V0.3.0", "v0.3.0 x"]) {
    assert.throws(() => nextVersion(["v0.2.0"], bad), /형식/, bad);
  }
  assert.throws(() => nextVersion(["v0.2.0"], "v0.2.0"), /이미 있는 tag/);
});

test("자동 증가할 tag가 없거나 이미 있으면 실패한다", () => {
  assert.throws(() => nextVersion([], ""), /semver tag가 없어/);
  assert.throws(() => nextVersion(["notes"], ""), /semver tag가 없어/);
});

test("migration이 없으면 미포함과 스키마 유지를 표시한다", () => {
  assert.deepEqual(migrationSummary({ added: [], before: files([1, 2, 30]), after: files([1, 2, 30]) }), {
    included: false, files: [], schema: "스키마 30 유지",
  });
});

test("추가된 migration을 번호 순으로 나열하고 스키마 변경을 표시한다", () => {
  const result = migrationSummary({
    added: [`${dir}V32__b.sql`, `${dir}V31__a.sql`, "backend/src/main/java/Other.java"],
    before: files([2, 10, 30]),
    after: [...files([2, 10, 30]), `${dir}V31__a.sql`, `${dir}V32__b.sql`],
  });
  assert.equal(result.included, true);
  assert.deepEqual(result.files, [`${dir}V31__a.sql`, `${dir}V32__b.sql`]);
  assert.equal(result.schema, "스키마 30 → 32");
});

test("스키마 번호는 문자열이 아니라 숫자로 비교한다", () => {
  assert.equal(schemaVersion(files([9, 30, 4])), 30);
  assert.equal(schemaVersion(["README.md"]), null);
  assert.equal(migrationSummary({ added: files([1]), before: null, after: files([1]) }).schema, "스키마 1");
});

test("squash commit과 GitHub 기본 제목 commit을 PR 번호와 제목으로 바꾼다", () => {
  const sha = "90c5688aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
  assert.equal(changeEntry({ sha, subject: "fix: 로그인 성공 뒤 이동 (Cloud Run) (#103)" }), "#103 fix: 로그인 성공 뒤 이동 (Cloud Run)");
  assert.equal(changeEntry({ sha, subject: "풀 리퀘스트 병합 #101", body: "* fix: 문제 해소\n" }), "#101 fix: 문제 해소");
  assert.equal(changeEntry({ sha, subject: "풀 리퀘스트 병합 #102", body: "* docs: 하나\n\n* chore: 둘\r\n* docs: 셋" }), "#102 docs: 하나 외 2건");
  assert.equal(changeEntry({ sha, subject: "풀 리퀘스트 병합 #9", body: "" }), "#9 (본문에 변경 항목 없음)");
  assert.equal(changeEntry({ sha, subject: "chore: 직접 commit" }), "90c5688 chore: 직접 commit");
});

test("배포 시각은 UTC 범위를 하이픈으로 표시한다", () => {
  assert.equal(formatRange("2026-11-28T13:35:10Z", "2026-11-28T13:38:59Z"), "2026-11-28 13:35-13:38 UTC");
  assert.equal(formatRange("2026-11-28T23:58:00+00:00", "2026-11-29T00:03:00Z"), "2026-11-28 23:58 UTC - 2026-11-29 00:03 UTC");
  assert.throws(() => formatRange("어제", "오늘"), /ISO 8601/);
});

test("릴리스 노트는 실행 결과가 없는 항목을 성공으로 채우지 않는다", () => {
  const notes = renderNotes({
    version: "v0.2.1", commit: "c".repeat(40), previousTag: "v0.2.0",
    migration: { included: false, files: [], schema: "스키마 30 유지" },
    changes: ["#103 fix: 예시"],
    deployment: { window: "", service: "decision-assistant", region: "asia-southeast1" },
    checks: { candidateSmoke: "smoke 통과: https://candidate.example (시도 1회)" },
  });
  assert.match(notes, /배포 커밋 해시: `c{40}`/);
  assert.match(notes, /마이그레이션: 미포함/);
  assert.match(notes, /배포 시각: 기록 없음/);
  assert.match(notes, /후보 주소 smoke.*: smoke 통과: https:\/\/candidate\.example/);
  assert.match(notes, /공개 주소 smoke: 기록 없음/);
  assert.match(notes, /## 포함된 변경 \(v0\.2\.0 이후\)\n\n- #103 fix: 예시/);
  assert.match(notes, /미검증 항목/);
});

test("job image 갱신 결과를 확인한 항목에 그대로 적고, 없으면 성공으로 채우지 않는다", () => {
  const base = {
    version: "v0.3.0", commit: "e".repeat(40), previousTag: "v0.2.1",
    migration: { included: false, files: [], schema: "스키마 30 유지" }, changes: [],
    deployment: { backupImage: "r/backup@sha256:abc" },
  };
  const notes = renderNotes({ ...base, checks: { purgeJob: "갱신함(decision-assistant-purge)", backupJob: "건너뜀(job 없음)" } });
  assert.match(notes, /- backup image: `r\/backup@sha256:abc`/);
  assert.match(notes, /휴지통 만료 삭제 job image 갱신\(backend image\): 갱신함\(decision-assistant-purge\)/);
  assert.match(notes, /백업 job image 갱신\(backup image\): 건너뜀\(job 없음\)/);
  assert.match(notes, /백업 job과 휴지통 만료 삭제 job의 실제 실행 결과, 백업 복원/);
  assert.doesNotMatch(notes, /실행 경로 미구현/);

  const missing = renderNotes({ ...base, checks: {} });
  assert.match(missing, /휴지통 만료 삭제 job image 갱신\(backend image\): 기록 없음/);
  assert.match(missing, /백업 job image 갱신\(backup image\): 기록 없음/);
  assert.match(renderNotes({ ...base, checks: {}, preview: true }), /백업 job image 갱신\(backup image\): 배포 후 기록/);
});

test("Git 기록에서 직전 릴리스 뒤 추가된 migration과 변경 목록을 모은다", () => {
  const repo = mkdtempSync(join(tmpdir(), "release-notes-"));
  try {
    const git = (...args) => execFileSync("git", ["-c", "user.name=test", "-c", "user.email=test@example.invalid",
      "-c", "commit.gpgsign=false", "-c", "tag.gpgsign=false", ...args], { cwd: repo, encoding: "utf8" }).trim();
    const commit = (message, file) => {
      if (file) {
        mkdirSync(join(repo, dir), { recursive: true });
        writeFileSync(join(repo, dir, file), "select 1;\n");
      } else writeFileSync(join(repo, "README.md"), message);
      git("add", "-A");
      git("commit", "-q", "-m", message);
    };
    git("init", "-q");
    commit("base", "V9__a.sql");
    commit("second", "V10__b.sql");
    git("tag", "v0.1.0");
    git("tag", "v0.0.9");
    commit("docs: 문서만 (#4)");
    const unchanged = collectHistory("HEAD", repo);
    assert.equal(unchanged.previousTag, "v0.1.0");
    assert.deepEqual(unchanged.changes, ["#4 docs: 문서만"]);
    assert.deepEqual(unchanged.migration, { included: false, files: [], schema: "스키마 10 유지" });

    commit("feat: 새 표 (#5)", "V11__c.sql");
    commit("풀 리퀘스트 병합 #6\n\n* fix: 하나\n\n* docs: 둘", "V12__d.sql");
    const head = git("rev-parse", "HEAD");
    git("tag", "v0.1.1");
    const changed = collectHistory("HEAD", repo);
    assert.equal(changed.commit, head);
    assert.equal(changed.previousTag, "v0.1.0", "배포 commit 자체의 tag는 직전 릴리스가 아니다");
    assert.deepEqual(changed.changes, ["#6 fix: 하나 외 1건", "#5 feat: 새 표", "#4 docs: 문서만"]);
    assert.deepEqual(changed.migration, { included: true, files: [`${dir}V11__c.sql`, `${dir}V12__d.sql`], schema: "스키마 10 → 12" });
  } finally {
    rmSync(repo, { recursive: true, force: true });
  }
});

test("생성 영역을 marker로 감싸고, 미리보기는 배포 결과를 성공으로 채우지 않는다", () => {
  const input = {
    version: "v0.2.1", commit: "d".repeat(40), previousTag: "v0.2.0",
    migration: { included: true, files: [`${dir}V31__a.sql`], schema: "스키마 30 → 31" },
    changes: [], deployment: {}, checks: {},
  };
  const notes = renderNotes(input);
  assert.ok(notes.startsWith("<!-- generated:start -->\n# v0.2.1"));
  assert.ok(notes.trimEnd().endsWith("<!-- generated:end -->"));
  assert.equal(notes.split("<!-- generated:start -->").length, 2);
  assert.match(notes, /마이그레이션: 포함, 스키마 30 → 31\n {2}- `backend\/src\/main\/resources\/db\/migration\/V31__a\.sql`/);
  assert.match(notes, /- 변경 없음/);

  const preview = renderNotes({ ...input, preview: true });
  assert.match(preview, /공개 주소 smoke: 배포 후 기록/);
  assert.match(preview, /배포 시각: 배포 후 기록/);
  assert.doesNotMatch(preview, /통과|기록 없음/);
});
