import { requireSha } from "./git.mjs";

function sameRepository(pull, repository) {
  if (pull.head.repo?.full_name !== repository || pull.base.repo?.full_name !== repository) throw new Error("같은 저장소의 PR만 지원합니다");
}

function unchanged(pull, node) {
  if (pull.head.ref !== node.branch || pull.head.sha !== node.head) throw new Error(`#${node.number}: 등록 이후 head가 변경됐습니다. 수동 확인 후 다시 등록하세요`);
}

export function parseNumbers(input) {
  if (!/^\d+(,\d+)*$/.test(input ?? "")) throw new Error("기반 순서의 PR 번호를 쉼표로 구분하세요 (예: 21,22,23)");
  const numbers = input.split(",").map(Number);
  if (numbers.some(n => !Number.isSafeInteger(n) || n < 1) || new Set(numbers).size !== numbers.length) throw new Error("PR 번호가 중복되거나 유효하지 않습니다");
  return numbers;
}

export class RestackService {
  constructor(git, github, { dryRun = false, log = console.log } = {}) {
    this.git = git;
    this.github = github;
    this.dryRun = dryRun;
    this.log = log;
  }

  async register(numbers) {
    this.git.fetch();
    const loaded = this.git.loadState();
    const pulls = await Promise.all(numbers.map(n => this.github.pull(n)));
    const refs = this.git.remoteRefs();
    const nodes = [];
    for (let i = 0; i < pulls.length; i++) {
      const pull = pulls[i];
      sameRepository(pull, this.github.repository);
      if (pull.state !== "open" || pull.merged) throw new Error("열린 PR만 등록할 수 있습니다");
      const branch = this.git.branch(pull.head.ref);
      if (nodes.some(n => n.branch === branch)) throw new Error("브랜치 중복은 허용하지 않습니다");
      const base = i === 0 ? "main" : nodes[i - 1].branch;
      if (pull.base.ref !== base) throw new Error(`#${pull.number}: base는 ${base}여야 합니다`);
      if (refs.get(`refs/heads/${branch}`) !== pull.head.sha) throw new Error("등록 중 원격 head가 변경됐습니다");
      const boundary = i === 0 ? this.git.run(["merge-base", requireSha(pull.base.sha), requireSha(pull.head.sha)]) : nodes[i - 1].head;
      this.git.range(boundary, pull.head.sha);
      nodes.push({ number: pull.number, branch, base, boundary, head: pull.head.sha });
    }
    const overlaps = loaded.state.stacks.filter(s => s.nodes.some(n => nodes.some(v => v.number === n.number || v.branch === n.branch)));
    // Explicit registration can recover the whole remaining open chain after manual fixes.
    if (overlaps.length > 1) throw new Error("다른 stack과 겹칩니다");
    for (const previous of overlaps) {
      if (previous.journal) throw new Error("미완료 transaction을 먼저 복구하세요");
      const offset = previous.nodes.findIndex(n => n.number === numbers[0]);
      if (offset < 0 || previous.nodes.slice(offset).map(n => n.number).join(",") !== numbers.join(",")) {
        throw new Error("다른 stack과 겹치거나 열린 후속 PR이 누락됐습니다");
      }
      for (const landed of previous.nodes.slice(0, offset)) {
        const pull = await this.github.pull(landed.number);
        sameRepository(pull, this.github.repository);
        if (!pull.merged || pull.base.ref !== "main") throw new Error("누락한 선행 PR이 main에 병합되지 않았습니다");
        this.git.ensureObject(pull.merge_commit_sha);
        if (!this.git.ancestor(pull.merge_commit_sha, refs.get("refs/heads/main"))) throw new Error("선행 PR 병합 결과를 확인할 수 없습니다");
      }
    }
    const stack = { id: overlaps[0]?.id ?? numbers[0], nodes, journal: null };
    const state = { ...loaded.state, stacks: [...loaded.state.stacks.filter(s => !overlaps.includes(s)), stack] };
    if (!this.dryRun) {
      await this.assertSnapshot(nodes, refs.get("refs/heads/main"));
      this.git.publish(state, loaded.sha);
    }
    this.log(`stack 등록 검증: ${numbers.join(" → ")}${this.dryRun ? " (dry-run)" : ""}`);
  }

  async assertSnapshot(nodes, main) {
    const refs = this.git.remoteRefs();
    if (refs.get("refs/heads/main") !== main) throw new Error("실행 중 main이 변경됐습니다. 다시 실행하세요");
    for (const node of nodes) {
      if (refs.get(`refs/heads/${node.branch}`) !== node.head) throw new Error("실행 중 후속 브랜치가 변경됐습니다");
      const pull = await this.github.pull(node.number);
      sameRepository(pull, this.github.repository);
      unchanged(pull, node);
      if (pull.state !== "open" || pull.base.ref !== node.base) throw new Error("실행 중 PR 상태 또는 base가 변경됐습니다");
    }
  }

  async finishJournal(stack) {
    if (!stack.journal) return;
    for (const node of stack.nodes) {
      const pull = await this.github.pull(node.number);
      sameRepository(pull, this.github.repository);
      unchanged(pull, node);
      if (pull.state !== "open") throw new Error("PR 상태가 바뀌어 transaction 완료를 중단했습니다");
      const expectedOldBase = stack.journal.bases[String(node.number)];
      if (pull.base.ref !== node.base) {
        // GitHub can also retarget children automatically after deleting a merged branch.
        if (pull.base.ref !== expectedOldBase) throw new Error("transaction 중 base가 예상과 다르게 바뀌었습니다");
        if (!this.dryRun) await this.github.setBase(node.number, node.base);
      }
    }
    stack.journal = null;
  }

  async process(stack, state, stateSha) {
    if (stack.journal) {
      await this.finishJournal(stack);
      if (!this.dryRun) stateSha = this.git.publish(state, stateSha);
    }
    if (!stack.nodes.length) return stateSha;
    const root = stack.nodes[0];
    const parent = await this.github.pull(root.number);
    sameRepository(parent, this.github.repository);
    unchanged(parent, root);
    if (!parent.merged) {
      if (parent.state !== "open") throw new Error("선행 PR이 병합 없이 닫혔습니다");
      return stateSha;
    }
    if (parent.base.ref !== "main") throw new Error("선행 PR이 main 이외의 브랜치에 병합됐습니다");
    const main = this.git.ref("refs/remotes/origin/main");
    this.git.ensureObject(parent.merge_commit_sha);
    if (!this.git.ancestor(parent.merge_commit_sha, main)) throw new Error("병합 결과가 현재 main의 조상이 아닙니다");
    const parents = this.git.run(["rev-list", "--parents", "-n", "1", parent.merge_commit_sha]).split(" ");
    if (parents.length !== 2) throw new Error("squash merge 결과를 확인할 수 없습니다");
    const remaining = stack.nodes.slice(1);
    const oldNodes = [];
    for (let i = 0; i < remaining.length; i++) {
      const node = remaining[i];
      const pull = await this.github.pull(node.number);
      sameRepository(pull, this.github.repository);
      unchanged(pull, node);
      if (pull.state !== "open") throw new Error("후속 PR이 이미 닫혔습니다");
      if (pull.base.ref !== node.base && !(i === 0 && pull.base.ref === "main")) throw new Error("후속 PR base가 변경됐습니다");
      oldNodes.push({ ...node, base: pull.base.ref });
    }
    // No refs or PR metadata are changed until *all* descendants have replayed successfully.
    const updates = [];
    const backups = [];
    const newNodes = [];
    let onto = main;
    for (const node of oldNodes) {
      this.git.ensureObject(node.boundary);
      const next = this.git.replay(node.boundary, node.head, onto);
      updates.push({ branch: node.branch, old: node.head, next });
      backups.push({ ref: `refs/tags/restack/${stack.id}/${root.head}/${node.number}`, sha: node.head });
      newNodes.push({ ...node, head: next, boundary: onto, base: newNodes.length ? newNodes.at(-1).branch : "main" });
      onto = next;
    }
    await this.assertSnapshot(oldNodes, main);
    // A merged PR head is immutable for our purposes; re-read before publishing as well.
    unchanged(await this.github.pull(root.number), root);
    stack.nodes = newNodes;
    stack.journal = { parent: root.number, bases: Object.fromEntries(oldNodes.map(n => [String(n.number), n.base])) };
    if (this.dryRun) {
      this.log(`stack ${stack.id}: #${root.number} 병합 후 ${updates.length}개 PR 재배치 가능`);
      return stateSha;
    }
    stateSha = this.git.publish(state, stateSha, updates, backups);
    await this.finishJournal(stack);
    return this.git.publish(state, stateSha);
  }

  async reconcile() {
    this.git.fetch();
    let { state, sha } = this.git.loadState();
    const errors = new Map();
    for (const id of state.stacks.map(s => s.id)) {
      try {
        sha = await this.process(state.stacks.find(s => s.id === id), state, sha);
      } catch (error) {
        errors.set(id, error.message);
        this.log(`stack ${id}: ${error.message}`);
        // A failed publish or a successful publish followed by an API failure can both occur.
        // Reload durable state instead of committing partially mutated in-memory objects.
        if (!this.dryRun) {
          this.git.fetch();
          ({ state, sha } = this.git.loadState());
        }
      }
    }
    if (!this.dryRun) await this.report(state, errors);
    if (errors.size) throw new Error(`${errors.size}개 stack에 수동 조치 또는 재실행이 필요합니다`);
  }

  async report(state, errors) {
    const pulls = await this.github.openPulls();
    for (const summary of pulls) {
      const pull = await this.github.pull(summary.number);
      const stack = state.stacks.find(s => s.nodes.some(n => n.number === pull.number));
      if (!stack) {
        const independent = pull.base.ref === "main" && pull.head.repo?.full_name === this.github.repository;
        await this.github.report(pull, { ready: independent, title: independent ? "독립 PR" : "stack 등록 필요",
          summary: independent ? "main 대상 독립 PR입니다. validate 결과를 별도로 확인하세요." : "부모 작업 브랜치를 대상으로 하는 PR은 stack을 등록하세요." });
        continue;
      }
      const index = stack.nodes.findIndex(n => n.number === pull.number);
      const node = stack.nodes[index];
      const mismatch = node.head !== pull.head.sha || node.branch !== pull.head.ref || node.base !== pull.base.ref;
      const error = errors.get(stack.id) ?? (mismatch ? "등록 정보와 PR의 head/base가 다릅니다" : stack.journal ? "원격 갱신 후 PR 설정 복구 중" : null);
      const ci = error ? "pending" : await this.github.validation(pull);
      const ready = !error && index === 0 && ci === "success";
      const title = error ? "수동 조치 필요" : index > 0 ? "부모 병합 대기" : ci === "failure" ? "CI 실패" : ready ? "병합 준비" : "CI 대기";
      const message = `**${title}**\n\n병합 순서: ${stack.nodes.map(n => `#${n.number}`).join(" → ")}\n\nhead: \`${node.branch}\` → base: \`${node.base}\`\n\n${error ?? (index > 0 ? "선행 PR을 main에 squash merge하면 자동 restack합니다." : ready ? "현재 head/base의 CI가 통과했습니다. 사용자가 검토 후 squash merge하세요." : "현재 head/base의 validate 실행 결과를 확인하세요. 이전 성공 결과는 재사용하지 않습니다.")}`;
      await this.github.report(pull, { ready, title, summary: message });
      await this.github.comment(pull.number, message);
    }
  }
}
