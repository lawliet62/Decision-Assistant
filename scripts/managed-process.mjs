import { spawn } from "node:child_process";
import { setTimeout as delay } from "node:timers/promises";

/** 자신이 생성한 프로세스 트리만 종료하고 close까지 기다린다. */
export function startManagedProcess(command, args, options = {}) {
  const windows = process.platform === "win32";
  const { shutdownGraceMs = 5000, ...spawnOptions } = options;
  const child = spawn(command, args, { stdio: "inherit", shell: false, ...spawnOptions, detached: !windows });
  let closed = false;
  let stopping;
  const signalGroup = signal => {
    if (!child.pid) return;
    try { process.kill(-child.pid, signal); }
    catch (error) { if (error.code !== "ESRCH") throw error; }
  };
  const completed = new Promise((resolve, reject) => {
    child.once("error", reject);
    child.once("close", (code, signal) => {
      closed = true;
      resolve({ code, signal });
    });
  });
  async function stop(signal = "SIGTERM") {
    if (stopping) return stopping;
    if (closed) return completed;
    stopping = (async () => {
      if (windows && child.pid) {
        // Windows의 child.kill()은 wrapper만 강제 종료하므로 /T로 소유 트리 전체를 종료한다.
        await new Promise((resolve, reject) => {
          const killer = spawn("taskkill", ["/pid", String(child.pid), "/T", "/F"], { windowsHide: true, stdio: "ignore" });
          killer.once("error", reject);
          killer.once("close", code => {
            if (code === 0 || closed) resolve();
            else reject(new Error("프로세스 트리 종료에 실패했습니다 (taskkill)."));
          });
        });
        return completed;
      }
      signalGroup(signal);
      const deadline = Date.now() + shutdownGraceMs + 1000;
      const force = setTimeout(() => signalGroup("SIGKILL"), shutdownGraceMs);
      try {
        const result = await completed;
        // group leader보다 늦게 정리되는 서버에도 유예 시간을 준다.
        while (Date.now() < deadline) {
          try { process.kill(-child.pid, 0); }
          catch (error) { if (error.code === "ESRCH") break; throw error; }
          await delay(25);
        }
        return result;
      }
      finally { clearTimeout(force); }
    })();
    return stopping;
  }
  return { child, completed, stop };
}
