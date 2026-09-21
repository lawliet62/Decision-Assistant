import { readFileSync } from "node:fs";
import { parseEnv } from "node:util";

const defaultFile = new URL("../backend/.env", import.meta.url);

// 파일 설정은 backend 자식 프로세스에만 전달한다. process.env는 변경하지 않는다.
export function backendEnvironment(tasks, inherited = process.env, file = defaultFile) {
  if (!tasks.includes("bootRun")) return inherited;
  let configured = {};
  try {
    configured = parseEnv(readFileSync(file, "utf8"));
  } catch (error) {
    if (error.code !== "ENOENT") throw new Error("backend/.env를 읽지 못했습니다. 파일 접근 권한을 확인하세요.");
  }
  const env = { ...configured, ...inherited };
  return { ...env, SPRING_PROFILES_ACTIVE: env.SPRING_PROFILES_ACTIVE ?? "local" };
}
