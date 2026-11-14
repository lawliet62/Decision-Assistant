import process from "node:process";
import { backendEnvironment } from "./backend-env.mjs";
import { startManagedProcess } from "./managed-process.mjs";

const isWindows = process.platform === "win32";
const root = process.cwd();
const children = new Set();
let shuttingDown = false;
let shutdown;

function run(command, args, options = {}) {
  const managed = startManagedProcess(command, args, {
    cwd: options.cwd ?? root,
    stdio: "inherit",
    shell: false,
    env: options.env ?? process.env,
    shutdownGraceMs: options.shutdownGraceMs,
  });
  if (options.keepAlive) children.add(managed);
  return managed.completed.then(({ code, signal }) => ({ code: code ?? 1, signal }));
}

function backendCommand() {
  return { command: process.execPath, args: ["scripts/gradle.mjs", "bootRun"] };
}

function frontendCommand() {
  if (isWindows) {
    return {
      command: process.env.ComSpec ?? "cmd.exe",
      args: ["/d", "/s", "/c", "npm run dev"],
    };
  }
  return { command: "npm", args: ["run", "dev"] };
}

function stopChildren() {
  if (shutdown) return shutdown;
  shuttingDown = true;
  shutdown = Promise.all([...children].map(child => child.stop("SIGINT")));
  return shutdown;
}

async function main() {
  console.log("Starting PostgreSQL with Docker Compose...");
  const database = await run("docker", ["compose", "-f", "backend/compose.yaml", "up", "-d"], { keepAlive: true });
  if (shuttingDown) return;
  if (database.code !== 0) {
    throw new Error("PostgreSQL을 시작하지 못했습니다. Docker Desktop이 실행 중인지 확인하세요.");
  }

  console.log("Starting backend and frontend...");
  const backend = backendCommand();
  const backendProcess = run(backend.command, backend.args, {
    cwd: root,
    keepAlive: true,
    env: backendEnvironment(["bootRun"]),
    // wrapper가 Gradle에 주는 5초 유예보다 먼저 wrapper를 강제 종료하지 않는다.
    shutdownGraceMs: 10000,
  });
  const frontend = frontendCommand();
  const frontendProcess = run(frontend.command, frontend.args, {
    cwd: `${root}/frontend`,
    keepAlive: true,
  });

  const firstExit = await Promise.race([backendProcess, frontendProcess]);
  if (!shuttingDown) {
    console.error(`개발 서버가 종료되었습니다 (code=${firstExit.code}).`);
    await stopChildren();
    process.exitCode = firstExit.code;
  }
}

for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, async () => {
    await stopChildren();
  });
}

main().catch(async (error) => {
  console.error(error.message);
  await stopChildren();
  process.exitCode = 1;
});
