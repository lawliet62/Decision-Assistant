# ADR 0024: Claude Code 하네스와 지침 파일 구성

- Status: Accepted
- Date: 2026-11-25
- Decision: human이 Claude Code의 세션 격리, 리드·subagent 운영, 리뷰 사이클, 지침 파일 배치를 승인했다. `AGENTS.md`는 도구 중립으로 유지한다.

## 배경

개발 agent를 Codex CLI에서 Claude Code로 옮겼고, 이후 두 도구를 병행할 수 있다. 기존 지침은 Codex 전용 worktree guard Hook을 전제로 했고, 규칙이 여러 파일에 반복되면서 길어졌다. 지침이 길수록 개별 지시가 무시될 가능성이 커진다고 판단했다.

## 결정

- 작업 하나(PR 하나 규모)는 리드 세션 하나와 `claude --worktree <작업명>`으로 만든 worktree 하나에서 진행한다. Codex 전용 worktree guard Hook은 Claude Code에 이식하지 않고 내장 worktree 격리를 쓴다. 기존 guard script는 Codex CLI용으로 남긴다.
- subagent는 리드의 worktree를 공유한다. 리드가 파일 소유 범위를 나눠 배정하고 통합을 책임진다. 파일 탐색, 빌드, 긴 테스트 로그가 생기는 구현은 백그라운드 subagent에 위임하고, 병렬 실행 전에는 작업 분해를 human이 승인한다.
- 구현이 끝나면 독립 reviewer subagent가 diff, spec, 지침만 보고 검토한다. blocking 지적만 다음 라운드를 만들며 최대 3회다. 같은 지적이 두 라운드 연속 남으면 human에게 보고한다. CI `validate`는 별도 기준으로 유지한다.
- reviewer에는 Edit·Write 도구를 주지 않고 shell은 허용한다. negative test를 실제로 실행하게 하기 위해서다. shell로 파일을 쓸 수 있으므로 수정 금지는 지시문으로만 강제된다.
- subagent 정의는 `implementer`와 `reviewer` 두 개만 둔다. 탐색은 내장 Explore를 쓴다. 다른 정의는 같은 지시를 반복하게 될 때 추가한다.
- 리드 기본 모델은 `.claude/settings.json`의 `model: opus`로 고정한다. 세션 중 `/model` 전환은 그대로 동작한다. advisor는 human이 사용자 설정에서 켜며 프로젝트 설정에 넣지 않는다. advisor의 조언은 Human Decision Gate의 승인을 대신하지 않는다.
- human이 승인한 프로젝트 SessionStart Hook(`.claude/settings.json`)으로 PR이 merge된 worktree를 정리한다. merge는 GitHub에서 human이 수행해 그 순간을 로컬에서 알 수 없으므로 다음 세션 시작 시 확인한다. main checkout의 `.worktrees/`·`.claude/worktrees/` 바로 아래에 있고, 현재 세션의 worktree가 아니며, 같은 branch의 `MERGED` PR head SHA가 로컬 HEAD와 같고, 추적 변경·untracked 파일이 없으며 ignored 항목이 재생성 가능한 산출물뿐인 worktree만 지운다. `git worktree remove`는 `--force` 없이도 ignored 파일(예: `backend/.env`)을 함께 지우므로 마지막 조건을 둔다. PR 조회는 GitHub 인증 helper로만 하고 실패하면 아무것도 지우지 않는다. 판정은 dry run이 기본인 `npm run worktrees:clean`으로 확인한다.
- Claude Code 전용 규칙은 루트 `CLAUDE.md`에 두고, `CLAUDE.md`는 `@AGENTS.md`로 도구 중립 지침을 불러온다. `frontend/`와 `backend/`에는 `@AGENTS.md` 한 줄만 있는 `CLAUDE.md`를 둔다.

## 대안과 영향

`AGENTS.md` 단일 파일 구성은 Claude Code가 `CLAUDE.md`가 없을 때만 `AGENTS.md`를 읽는 fallback에 의존한다. `CLAUDE.local.md`나 `.claude/CLAUDE.md`가 하나라도 생기면 안전 규칙을 포함한 지침 전체가 경고 없이 빠지고, Claude Code 전용 규칙을 `AGENTS.md`에 넣어야 해 도구 중립이 깨진다. 그래서 명시적 import를 선택했다.

그 대가로 하위 폴더마다 한 줄짜리 `CLAUDE.md`가 필요하다. Claude Code 2.1.330에서는 루트에 `CLAUDE.md`가 있으면 하위 폴더의 `AGENTS.md`만으로는 로드되지 않았다. 새 영역에 `AGENTS.md`를 추가하면 같은 폴더에 `CLAUDE.md`도 추가한다. 확인 방법은 [Claude Code 지침 로드 진단](../troubleshooting/claude-code-instruction-loading.md)에 있다.

Hook을 이식하지 않으므로 같은 worktree에 두 세션이 들어가는 것을 기술적으로 막지 못한다. 세션을 항상 `--worktree`로 시작하는 운영 규칙에 의존한다. `--worktree`가 만드는 branch 이름 `worktree-<작업명>`은 저장소 규칙과 달라 첫 push 전에 바꿔야 한다.

agent teams, Code Mode, agent view는 도입하지 않았다. Claude Code용 custom hook은 merge된 worktree 정리용 SessionStart Hook 하나만 두며, 세션 점유 강제나 도구 호출 차단에는 쓰지 않는다.
