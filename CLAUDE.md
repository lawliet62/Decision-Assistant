@AGENTS.md

# Claude Code 전용 운영 규칙

`AGENTS.md`는 도구 중립 지침이고, 이 파일에는 Claude Code에만 해당하는 규칙만 둔다. 배경과 대안은 [ADR 0024](docs/decisions/0024-claude-code-harness.md)에 있다. 새 영역에 `AGENTS.md`를 추가하면 같은 폴더에 `@AGENTS.md` 한 줄만 있는 `CLAUDE.md`도 추가한다.

## 세션과 worktree

- 작업 하나(PR 하나 규모)는 리드 세션 하나와 worktree 하나에서 진행한다. `claude --worktree <작업명>`이나 JetBrains Air가 만든 worktree에서 시작했으면 그 worktree를 그대로 쓴다.
- 원본 checkout에서 시작된 세션은 변경 작업 전에 리드가 EnterWorktree 도구로 `.claude/worktrees/<작업명>`을 만들어 옮긴다. 사용자에게 명령 실행을 요청하지 않는다.
- 도구가 만든 branch(`worktree-<작업명>`, `air/...`)는 첫 push 전에 `git branch -m <type>/<short-kebab-description>`으로 바꾼다.
- subagent는 리드의 worktree를 공유한다. `isolation: worktree`는 쓰지 않는다. 리드가 subagent별 파일 소유 범위를 나눠 배정하고 통합을 책임진다.
- 같은 worktree에서 Gradle 검증을 동시에 실행하지 않는다. 결과 파일이 충돌한다([사례](docs/troubleshooting/parallel-gradle-test-output.md)). subagent를 병렬로 돌릴 때는 리드가 검증 순서를 지정하고, 통합 후 전체 검증을 리드가 책임진다.

## 리드 세션

이 절은 리드 세션에만 적용하며 subagent에는 적용하지 않는다.

- 리드는 질문 답변, 설계 논의, 몇 줄 수정 같은 짧은 작업만 직접 한다. 파일 탐색, 빌드, 긴 테스트 로그가 생기는 구현은 에이전트가 하나만 필요해도 백그라운드 `implementer` subagent에 위임한다. 탐색과 조사는 내장 Explore subagent를 쓴다.
- 여러 subagent로 나누는 것은 컨텍스트 오염 방지, 병렬 탐색, 특화가 필요할 때만 한다. 분해 기준은 작업 유형이 아니라 컨텍스트 단위다. 예: "기능 A와 그 테스트"를 한 subagent에 준다.
- 병렬 실행 전에 작업 분해(subagent 수, 역할, 파일 범위)를 사용자에게 보여 주고 승인을 받는다.
- subagent가 Human Decision Gate 대상 선택을 보고하면 그 선택을 대신 결정하지 않고 사용자에게 보고한다.
- Human Decision Gate 후보로 판단되면 advisor와 상담한 뒤 사용자에게 보고한다. advisor가 설정돼 있지 않으면 바로 보고한다. advisor의 조언은 사람의 승인을 대신하지 않는다.

## 리뷰 사이클

- 구현이 끝나면 `reviewer` subagent가 diff, spec, 지침만 보고 검토한다. 구현자의 추론 과정은 전달하지 않는다.
- 다음 라운드는 blocking 지적이 남아 있을 때만 진행한다. non-blocking 지적은 PR 본문에 기록만 한다.
- 라운드는 최대 3회다. 같은 지적이 두 라운드 연속 해결되지 않으면 즉시 사용자에게 보고한다. 보고 형식: 미해결 지적 / 시도한 수정 / 리드 추천안 / 사용자가 고를 선택지.
- reviewer 통과는 CI `validate`를 대신하지 않는다. `validate`는 별도의 객관적 기준으로 유지한다.
