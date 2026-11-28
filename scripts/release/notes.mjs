// Cloud Run 배포 workflow의 버전 결정과 릴리스 노트 생성. Git 기록과 workflow 실행 결과만 쓴다.
// 사용법:
//   node scripts/release/notes.mjs version [--input vX.Y.Z]
//   node scripts/release/notes.mjs notes --commit <sha> --version <tag> [실행 결과 옵션]
import { execFileSync } from "node:child_process";
import process from "node:process";
import { pathToFileURL } from "node:url";
import { parseArgs } from "node:util";

export const MIGRATION_DIR = "backend/src/main/resources/db/migration/";
const SEMVER = /^v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/;

export function parseVersion(tag) {
  const match = SEMVER.exec(tag);
  return match ? match.slice(1).map(Number) : null;
}

function compareVersions(a, b) {
  for (let i = 0; i < 3; i++) if (a[i] !== b[i]) return a[i] - b[i];
  return 0;
}

export function latestVersion(tags) {
  let best = null;
  for (const tag of tags) {
    const parsed = parseVersion(tag);
    if (parsed && (!best || compareVersions(parsed, best.parsed) > 0)) best = { tag, parsed };
  }
  return best?.tag ?? null;
}

// 입력이 있으면 형식과 중복만 검사하고, 비어 있으면 가장 높은 semver tag의 patch를 1 올린다.
export function nextVersion(tags, input = "") {
  const requested = input.trim();
  if (requested) {
    if (!parseVersion(requested)) throw new Error(`version 형식은 vMAJOR.MINOR.PATCH여야 합니다: ${requested}`);
    if (tags.includes(requested)) throw new Error(`이미 있는 tag입니다: ${requested}`);
    return requested;
  }
  const latest = latestVersion(tags);
  if (!latest) throw new Error("semver tag가 없어 version을 자동으로 정할 수 없습니다. version을 입력하세요.");
  const [major, minor, patch] = parseVersion(latest);
  const next = `v${major}.${minor}.${patch + 1}`;
  if (tags.includes(next)) throw new Error(`자동으로 정한 tag가 이미 있습니다: ${next}`);
  return next;
}

export function schemaVersion(files) {
  let max = null;
  for (const file of files) {
    const match = /(?:^|\/)V(\d+)__[^/]*\.sql$/.exec(file);
    if (match) max = Math.max(max ?? 0, Number(match[1]));
  }
  return max;
}

// added: 직전 릴리스 뒤 추가된 migration 경로, before(직전 릴리스가 없으면 null)/after: 두 시점의 migration 파일 목록
export function migrationSummary({ added, before, after }) {
  const files = added
    .filter(file => file.startsWith(MIGRATION_DIR))
    .sort((a, b) => (schemaVersion([a]) ?? 0) - (schemaVersion([b]) ?? 0));
  const from = before === null ? null : schemaVersion(before);
  const to = schemaVersion(after);
  const label = version => version ?? "없음";
  let schema;
  if (before === null) schema = `스키마 ${label(to)}`;
  else if (from === to) schema = `스키마 ${label(to)} 유지`;
  else schema = `스키마 ${label(from)} → ${label(to)}`;
  return { included: files.length > 0, files, schema };
}

// squash commit은 `제목 (#번호)`, GitHub 기본 제목의 commit은 `풀 리퀘스트 병합 #번호`이고 본문에 `* ` 항목이 있다.
export function changeEntry({ sha, subject, body = "" }) {
  const squash = /^(.*\S)\s+\(#(\d+)\)$/.exec(subject);
  if (squash) return `#${squash[2]} ${squash[1]}`;
  const merged = /^풀 리퀘스트 병합 #(\d+)$/.exec(subject.trim());
  if (merged) {
    const items = body.split(/\r?\n/).map(line => /^\*\s+(.+)$/.exec(line.trim())?.[1]).filter(Boolean);
    if (!items.length) return `#${merged[1]} (본문에 변경 항목 없음)`;
    return `#${merged[1]} ${items[0]}${items.length > 1 ? ` 외 ${items.length - 1}건` : ""}`;
  }
  return `${sha.slice(0, 7)} ${subject}`;
}

export function formatRange(startIso, endIso) {
  const start = new Date(startIso);
  const end = new Date(endIso);
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) throw new Error("배포 시각은 ISO 8601이어야 합니다.");
  const day = date => date.toISOString().slice(0, 10);
  const time = date => date.toISOString().slice(11, 16);
  return day(start) === day(end)
    ? `${day(start)} ${time(start)}-${time(end)} UTC`
    : `${day(start)} ${time(start)} UTC - ${day(end)} ${time(end)} UTC`;
}

// 생성 영역은 실행 결과에서 온 사실이므로 이후 고치지 않는다. 보완은 marker 밖의 별도 절에 쓴다.
export const GENERATED_START = "<!-- generated:start -->";
export const GENERATED_END = "<!-- generated:end -->";

// preview는 승인 전 미리보기다. 아직 없는 배포 결과를 성공으로 채우지 않고 "배포 후 기록"으로 둔다.
export function renderNotes({ version, commit, previousTag, migration, changes, deployment, checks, preview = false }) {
  const value = text => (text && String(text).trim()) || (preview ? "배포 후 기록" : "기록 없음");
  return [
    GENERATED_START,
    `# ${version}`,
    "",
    `- 배포 커밋 해시: \`${commit}\``,
    `- 마이그레이션: ${migration.included ? "포함" : "미포함"}, ${migration.schema}`,
    ...migration.files.map(file => `  - \`${file}\``),
    `- 배포 시각: ${value(deployment.window)}`,
    "",
    "## 배포 대상",
    "",
    `- Cloud Run 서비스: \`${value(deployment.service)}\` (${value(deployment.region)})`,
    `- 새 revision: \`${value(deployment.revision)}\``,
    `- 배포 전 traffic revision: \`${value(deployment.previousRevision)}\``,
    `- backend image: \`${value(deployment.backendImage)}\``,
    `- frontend image: \`${value(deployment.frontendImage)}\``,
    "",
    `## 포함된 변경 (${previousTag ?? "첫 릴리스"} 이후)`,
    "",
    ...(changes.length ? changes.map(entry => `- ${entry}`) : ["- 변경 없음"]),
    "",
    "## 확인한 항목과 미검증 항목",
    "",
    "확인한 항목(이번 실행에서 배포 workflow가 검사한 결과):",
    "",
    `- 후보 revision: ${value(checks.revision)}`,
    `- 후보 주소 smoke(\`/login\` 200, 미인증 \`/api/v1/me\` 401): ${value(checks.candidateSmoke)}`,
    `- traffic 전환: ${value(checks.traffic)}`,
    `- 공개 주소 smoke: ${value(checks.publicSmoke)}`,
    `- 후보 tag 제거: ${value(checks.tagCleanup)}`,
    "",
    "미검증 항목(자동 배포가 확인하지 않음):",
    "",
    "- Auth0 실제 로그인, 초대 거부, 로그아웃·재로그인",
    "- 계정 간 데이터 격리, CSRF, 결정 생성·수정·삭제 같은 쓰기 흐름",
    "- 이전 revision이 현재 DB schema에서 동작하는지(rollback 가능 여부)",
    "- 백업 생성·복원, 휴지통 30일 만료 삭제(Cloud Run 실행 경로 미구현)",
    "- 로그 보존, 비용·사용량",
    GENERATED_END,
    "",
  ].join("\n");
}

const lines = text => (text ? text.split(/\r?\n/).filter(Boolean) : []);

// 직전 릴리스는 배포 commit에 포함된 semver tag 중 가장 높은 것이다(배포 commit 자체를 가리키는 tag는 제외).
export function collectHistory(ref, cwd = process.cwd()) {
  const git = args => execFileSync("git", args, { cwd, encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] }).trimEnd();
  const migrationFiles = at => lines(git(["ls-tree", "--name-only", at, MIGRATION_DIR]));
  const commit = git(["rev-parse", `${ref}^{commit}`]);
  const candidates = lines(git(["tag", "--list", "--merged", commit]))
    .filter(tag => parseVersion(tag) && git(["rev-list", "-n", "1", tag]) !== commit);
  const previousTag = latestVersion(candidates);
  const raw = git(["log", "--first-parent", "--format=%H%x1f%s%x1f%b%x1e", previousTag ? `${previousTag}..${commit}` : commit]);
  const changes = raw.split("\x1e").map(record => record.trim()).filter(Boolean).map(record => {
    const [sha, subject, body] = record.split("\x1f");
    return changeEntry({ sha, subject, body });
  });
  const added = previousTag
    ? lines(git(["diff", "--name-only", "--diff-filter=A", previousTag, commit, "--", MIGRATION_DIR]))
    : migrationFiles(commit);
  const migration = migrationSummary({ added, before: previousTag ? migrationFiles(previousTag) : null, after: migrationFiles(commit) });
  return { commit, previousTag, changes, migration };
}

function git(args) {
  return execFileSync("git", args, { encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] }).trimEnd();
}

function main(argv) {
  const [command, ...rest] = argv;
  if (command === "version") {
    const { values } = parseArgs({ args: rest, options: { input: { type: "string", default: "" } } });
    console.log(nextVersion(lines(git(["tag", "--list"])), values.input));
    return;
  }
  if (command === "notes") {
    const option = { type: "string" };
    const names = ["commit", "version", "started", "finished", "service", "region", "revision", "previous-revision",
      "backend-image", "frontend-image", "candidate-smoke", "traffic", "public-smoke", "tag-cleanup"];
    const options = { ...Object.fromEntries(names.map(name => [name, option])), preview: { type: "boolean", default: false } };
    const { values } = parseArgs({ args: rest, options });
    if (!values.commit || !values.version) throw new Error("--commit과 --version이 필요합니다.");
    console.log(renderNotes({
      preview: values.preview,
      version: values.version,
      ...collectHistory(values.commit),
      deployment: {
        window: values.started && values.finished ? formatRange(values.started, values.finished) : "",
        service: values.service, region: values.region, revision: values.revision,
        previousRevision: values["previous-revision"],
        backendImage: values["backend-image"], frontendImage: values["frontend-image"],
      },
      checks: {
        revision: values.revision ? `${values.revision} 생성·준비 완료, 서비스 template image가 이번 build digest와 일치` : "",
        candidateSmoke: values["candidate-smoke"], traffic: values.traffic,
        publicSmoke: values["public-smoke"], tagCleanup: values["tag-cleanup"],
      },
    }));
    return;
  }
  throw new Error("사용법: notes.mjs version [--input vX.Y.Z] | notes --commit <sha> --version <tag> ...");
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    main(process.argv.slice(2));
  } catch (error) {
    console.error(error.message);
    process.exit(1);
  }
}
