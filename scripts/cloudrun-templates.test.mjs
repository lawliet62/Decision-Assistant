// deploy/cloudrun의 Cloud Run 정의 template 정적 검사. YAML 문법과 Cloud Run schema는 gcloud가 적용할 때 검사하므로
// 여기서는 placeholder·image 고정·env 일관성처럼 template을 고칠 때 깨지기 쉬운 약속만 본다.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const read = name => readFileSync(new URL(`../deploy/cloudrun/${name}`, import.meta.url), "utf8");
const service = read("service.template.yaml");
const purge = read("purge-job.template.yaml");
const backup = read("backup-job.template.yaml");

const body = text => text.split(/\r?\n/).filter(line => !line.trimStart().startsWith("#"));
const placeholders = text => [...new Set(body(text).join("\n").match(/\$\{[A-Z0-9_]+\}/g) ?? [])].map(p => p.slice(2, -1)).sort();
const render = text => text.replace(/\$\{([A-Z0-9_]+)\}/g, (_, name) => `fake-${name.toLowerCase()}`);
// `- { name: X, value: Y }` 형식의 env만 모은다. valueFrom env는 따로 검사한다.
const env = text => Object.fromEntries(body(text)
  .map(line => /^\s*- \{ name: ([A-Z0-9_]+), value: (.+) \}\s*$/.exec(line))
  .filter(Boolean)
  .map(([, name, value]) => [name, value.replace(/^'(.*)'$/, "$1")]));
const backendEnv = () => {
  const start = service.indexOf("- name: backend");
  assert.ok(start > 0, "service template에 backend container가 있어야 합니다");
  return env(service.slice(start));
};

test("job template의 placeholder는 정해진 목록뿐이고 주석에는 쓰지 않는다", () => {
  assert.deepEqual(placeholders(purge), ["ALLOWED_EMAILS", "AUTH0_CLIENT_ID", "AUTH0_ISSUER_URI", "AUTH0_SECRET_VERSION",
    "BACKEND_IMAGE", "DB_SECRET_VERSION", "DB_USERNAME", "DEV_PROJECT_ID", "NEON_HOST", "PROD_PROJECT_ID", "RUNTIME_SERVICE_ACCOUNT"]);
  assert.deepEqual(placeholders(backup), ["BACKUP_BUCKET", "BACKUP_IMAGE", "DB_SECRET_VERSION", "DB_USERNAME", "NEON_HOST",
    "RUNTIME_SERVICE_ACCOUNT"]);
  for (const text of [purge, backup]) {
    for (const line of text.split(/\r?\n/).filter(l => l.trimStart().startsWith("#"))) assert.doesNotMatch(line, /\$\{/, line);
    assert.doesNotMatch(render(text), /\$\{/);
  }
});

test("job template은 Cloud Run Job 형식이고 image는 운영자가 넣는 digest placeholder다", () => {
  for (const [text, name, image] of [[purge, "decision-assistant-purge", "BACKEND_IMAGE"], [backup, "decision-assistant-backup", "BACKUP_IMAGE"]]) {
    assert.match(text, /^apiVersion: run\.googleapis\.com\/v1\nkind: Job\nmetadata:\n {2}name: (\S+)$/m);
    assert.match(text, new RegExp(`^ {2}name: ${name}$`, "m"));
    assert.equal(body(text).filter(line => /^\s+image:/.test(line)).join(), `              image: '\${${image}}'`);
    assert.match(text, /serviceAccountName: '\$\{RUNTIME_SERVICE_ACCOUNT\}'/);
    assert.match(text, /maxRetries: \d+/);
    assert.match(text, /timeoutSeconds: \d+/);
  }
});

test("purge job env는 서비스 backend env에서 공개 주소만 빼고 job mode와 비활성 기능을 둔다", () => {
  const serviceEnv = backendEnv();
  const jobEnv = env(purge);
  assert.equal(jobEnv.DECISION_ASSISTANT_JOB, "purge-expired-decisions");
  assert.deepEqual(Object.keys(serviceEnv).filter(name => !(name in jobEnv)), ["DECISION_ASSISTANT_PUBLIC_BASE_URL"]);
  assert.deepEqual(Object.keys(jobEnv).filter(name => !(name in serviceEnv)), ["DECISION_ASSISTANT_JOB"]);
  const differs = Object.keys(jobEnv).filter(name => name in serviceEnv && serviceEnv[name] !== jobEnv[name]).sort();
  assert.deepEqual(differs, ["DECISION_ASSISTANT_CATALOG_ENABLED", "SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE"]);
  for (const flag of ["CATALOG_ENABLED", "CATALOG_REFRESH_ENABLED", "CATALOG_SPEC_REFRESH_ENABLED", "AI_ENABLED", "SEARCH_ENABLED",
    "WEB_ENABLED", "REMINDERS_ENABLED"]) assert.equal(jobEnv[`DECISION_ASSISTANT_${flag}`], "false", flag);
});

test("backup job은 libpq env와 숫자 version으로 고정한 secret 참조만 쓰고 시험용 endpoint를 두지 않는다", () => {
  assert.deepEqual(env(backup), {
    PGHOST: "${NEON_HOST}", PGDATABASE: "decision_assistant", PGUSER: "${DB_USERNAME}", PGSSLMODE: "require",
    BACKUP_BUCKET: "${BACKUP_BUCKET}",
  });
  assert.match(backup, /- name: PGPASSWORD\n\s+valueFrom:\n\s+secretKeyRef: \{ name: db-password, key: '\$\{DB_SECRET_VERSION\}' \}/);
  assert.equal((body(backup).join("\n").match(/secretKeyRef/g) ?? []).length, 1);
  assert.doesNotMatch(body(backup).join("\n"), /latest|BACKUP_TEST_ENDPOINTS|BASE_URL/);
  assert.match(env(purge).DB_URL, /sslmode=require$/);
});
