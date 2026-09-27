const CHECK_NAME = "stack-ready";
const MARKER = "<!-- decision-assistant:stack-status -->";

export class GitHub {
  constructor({ repository, token, appId, appSlug, request = fetch }) {
    if (!/^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(repository ?? "")) throw new Error("유효하지 않은 repository");
    this.repository = repository;
    this.token = token;
    this.appId = Number(appId);
    if (!Number.isSafeInteger(this.appId) || this.appId < 1 || !/^[a-z0-9-]+$/.test(appSlug ?? "")) throw new Error("GitHub App ID/slug 설정이 필요합니다");
    this.bot = `${appSlug}[bot]`;
    this.request = request;
    this.root = `/repos/${repository}`;
  }

  async api(path, method = "GET", body) {
    const response = await this.request(`https://api.github.com${path}`, {
      method, headers: { Accept: "application/vnd.github+json", Authorization: `Bearer ${this.token}`,
        "X-GitHub-Api-Version": "2022-11-28", "Content-Type": "application/json" },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }), signal: AbortSignal.timeout(30_000),
    });
    if (!response.ok) throw new Error(`GitHub ${method} 요청 실패 (${response.status})`);
    return response.status === 204 ? null : response.json();
  }

  async pages(path, key) {
    const entries = [];
    for (let page = 1; ; page++) {
      const response = await this.api(`${path}${path.includes("?") ? "&" : "?"}per_page=100&page=${page}`);
      const values = key ? response[key] : response;
      entries.push(...values);
      if (values.length < 100) return entries;
    }
  }

  pull(number) { return this.api(`${this.root}/pulls/${number}`); }
  openPulls() { return this.pages(`${this.root}/pulls?state=open`); }
  setBase(number, base) { return this.api(`${this.root}/pulls/${number}`, "PATCH", { base }); }

  async preflight() {
    const repository = await this.api(this.root);
    if (repository.default_branch !== "main" || !repository.allow_squash_merge || repository.allow_merge_commit || repository.allow_rebase_merge) {
      throw new Error("main 기본 브랜치 및 squash-only 설정이 필요합니다");
    }
    // 규칙 존재 여부와 App 자신의 bypass 판정을 각각 확인한다.
    const rules = await this.api(`${this.root}/rules/branches/main`);
    if (!rules.some(rule => rule.type === "update") || !rules.some(rule => rule.type === "pull_request") ||
        !rules.some(rule => rule.type === "required_status_checks" && rule.parameters.required_status_checks.some(check => check.context === "validate"))) {
      throw new Error("App의 main 갱신 차단과 기존 PR/validate 보호 설정이 필요합니다");
    }
    for (const rule of rules.filter(r => r.type === "update")) {
      const ruleset = await this.api(`${this.root}/rulesets/${rule.ruleset_id}`);
      // bypass_actors는 ruleset 쓰기 권한이 없으면 생략된다. App에 관리 권한을 추가하지 않는다.
      if (ruleset.enforcement !== "active" || ruleset.current_user_can_bypass !== "never") {
        throw new Error("App이 main 갱신 제한을 우회할 수 없다는 확인이 필요합니다");
      }
    }
  }

  async report(pull, { ready, title, summary }) {
    const checks = await this.pages(`${this.root}/commits/${pull.head.sha}/check-runs?check_name=${CHECK_NAME}`, "check_runs");
    const existing = checks.filter(c => c.app?.id === this.appId && c.external_id === String(pull.number))
      .sort((a, b) => b.id - a.id)[0];
    const body = { name: CHECK_NAME, external_id: String(pull.number), status: "completed",
      conclusion: ready ? "success" : "failure", output: { title, summary } };
    if (existing) await this.api(`${this.root}/check-runs/${existing.id}`, "PATCH", body);
    else await this.api(`${this.root}/check-runs`, "POST", { ...body, head_sha: pull.head.sha });
    return { title, summary };
  }

  async comment(number, message) {
    const body = `${MARKER}\n${message}`;
    const comments = await this.pages(`${this.root}/issues/${number}/comments`);
    const existing = comments.find(c => c.user?.login === this.bot && c.body.startsWith(MARKER));
    if (existing?.body === body) return;
    if (existing) await this.api(`${this.root}/issues/comments/${existing.id}`, "PATCH", { body });
    else await this.api(`${this.root}/issues/${number}/comments`, "POST", { body });
  }

  async validation(pull) {
    // run.pull_requests reflects live PR data, not the original base. Use a recorded job step.
    const runs = await this.pages(`${this.root}/actions/workflows/ci.yml/runs?event=pull_request&head_sha=${pull.head.sha}`, "workflow_runs");
    const run = runs.filter(r => r.event === "pull_request" && r.head_sha === pull.head.sha &&
      r.pull_requests?.some(p => p.number === pull.number))
      .sort((a, b) => b.id - a.id)[0];
    if (!run || run.status !== "completed") return "pending";
    const jobs = await this.pages(`${this.root}/actions/runs/${run.id}/jobs?filter=latest`, "jobs");
    const validate = jobs.find(j => j.name === "validate");
    const snapshot = `Restack snapshot ${pull.head.sha} ${pull.base.sha} ${pull.base.ref}`;
    if (!validate?.steps?.some(s => s.name === snapshot && s.conclusion === "success")) return "pending";
    return run.conclusion === "success" && validate.conclusion === "success" ? "success" : "failure";
  }
}
