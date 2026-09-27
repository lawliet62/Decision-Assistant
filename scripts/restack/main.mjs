import { StackGit } from "./git.mjs";
import { GitHub } from "./github.mjs";
import { RestackService, parseNumbers } from "./service.mjs";

async function main() {
  const action = process.env.RESTACK_ACTION || "reconcile";
  if (!["register", "reconcile", "dry-run"].includes(action)) throw new Error("지원하지 않는 restack 동작");
  if (process.env.GITHUB_ACTIONS !== "true" || process.env.GITHUB_REF !== "refs/heads/main") {
    throw new Error("보호된 main의 GitHub Actions에서만 실행할 수 있습니다");
  }
  const dryRun = action === "dry-run";
  if (!dryRun && process.env.RESTACK_ENABLED !== "true") throw new Error("RESTACK_ENABLED가 true가 아닙니다");
  const github = new GitHub({ repository: process.env.GITHUB_REPOSITORY, token: process.env.RESTACK_TOKEN,
    appSlug: process.env.RESTACK_APP_SLUG });
  if (process.env.GITHUB_EVENT_NAME === "workflow_dispatch") {
    const actor = process.env.GITHUB_TRIGGERING_ACTOR;
    if (!/^[A-Za-z0-9-]+$/.test(actor ?? "")) throw new Error("실행자 확인 실패");
    const { permission } = await github.api(`${github.root}/collaborators/${actor}/permission`);
    if (!["admin", "maintain", "write"].includes(permission)) throw new Error("수동 실행에는 저장소 쓰기 권한이 필요합니다");
  }
  if (!dryRun) await github.preflight();
  const git = new StackGit(`https://github.com/${github.repository}.git`, { token: process.env.RESTACK_TOKEN });
  const service = new RestackService(git, github, { dryRun });
  if (action === "register" || (dryRun && process.env.RESTACK_PRS)) await service.register(parseNumbers(process.env.RESTACK_PRS));
  if (action !== "dry-run" || !process.env.RESTACK_PRS) await service.reconcile();
}

main().catch(error => {
  console.error(error.message);
  process.exitCode = 1;
});
