import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { backendEnvironment } from "./backend-env.mjs";

test("backend에만 파일 설정을 전달하고 명시적 실행 환경을 우선한다", () => {
  const file = join(mkdtempSync(join(tmpdir(), "decision-env-")), ".env");
  writeFileSync(file, 'DECISION_ASSISTANT_AI_ENABLED=true\nLABEL="한글 값 # 유지"\nSPRING_PROFILES_ACTIVE=local\n');
  const inherited = { DECISION_ASSISTANT_AI_ENABLED: "false" };
  assert.deepEqual(backendEnvironment(["bootRun"], inherited, file), {
    DECISION_ASSISTANT_AI_ENABLED: "false", LABEL: "한글 값 # 유지", SPRING_PROFILES_ACTIVE: "local",
  });
  assert.deepEqual(inherited, { DECISION_ASSISTANT_AI_ENABLED: "false" });
  for (const task of ["test", "integrationTest", "build"]) {
    assert.equal(backendEnvironment([task], inherited, file), inherited);
  }
  assert.equal(backendEnvironment(["bootRun"], { SPRING_PROFILES_ACTIVE: "custom" }, file).SPRING_PROFILES_ACTIVE, "custom");
});

test("파일이 없으면 local 기본값을 사용하고 읽기 오류는 숨기지 않는다", () => {
  const directory = mkdtempSync(join(tmpdir(), "decision-env-"));
  assert.deepEqual(backendEnvironment(["bootRun"], {}, join(directory, "missing")), { SPRING_PROFILES_ACTIVE: "local" });
  assert.throws(() => backendEnvironment(["bootRun"], {}, directory), /backend\/\.env를 읽지 못했습니다/);
});
