import { spawn } from "node:child_process";
import process from "node:process";
import { backendEnvironment } from "./backend-env.mjs";

const isWindows = process.platform === "win32";
const root = process.cwd();
const children = new Set();
let shuttingDown = false;

function run(command, args, options = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      cwd: options.cwd ?? root,
      stdio: "inherit",
      shell: false,
      env: options.env ?? process.env,
    });

    if (options.keepAlive) {
      children.add(child);
      child.once("close", () => children.delete(child));
    }

    child.once("error", reject);
    child.once("close", (code, signal) => resolve({ code: code ?? 1, signal }));
  });
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

async function stopChildren() {
  if (shuttingDown) return;
  shuttingDown = true;
  for (const child of children) {
    child.kill("SIGINT");
  }
}

async function main() {
  console.log("Starting PostgreSQL with Docker Compose...");
  const database = await run("docker", ["compose", "-f", "backend/compose.yaml", "up", "-d"]);
  if (database.code !== 0) {
    throw new Error("PostgreSQL을 시작하지 못했습니다. Docker Desktop이 실행 중인지 확인하세요.");
  }

  console.log("Starting backend and frontend...");
  const backend = backendCommand();
  const backendProcess = run(backend.command, backend.args, {
    cwd: root,
    keepAlive: true,
    env: backendEnvironment(["bootRun"]),
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
