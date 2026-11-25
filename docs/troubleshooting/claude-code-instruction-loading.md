# Claude Code에서 지침 파일이 로드되지 않는 경우

2026-11-25 Windows의 Claude Code 2.1.330에서 `AGENTS.md`와 `CLAUDE.md`의 로드 조건을 확인했다. 공식 문서의 설명과 실제 동작이 한 곳에서 달랐다.

## 확인된 동작

| 구성 | 루트 지침 | 하위 폴더 `AGENTS.md` |
|---|---|---|
| `CLAUDE.md` 없음, `AGENTS.md`만 있음 | `AGENTS.md`가 로드된다 | 그 폴더의 파일을 Read 도구로 열 때 로드된다 |
| 루트 `CLAUDE.md`가 `@AGENTS.md`를 import | 둘 다 로드된다 | **로드되지 않는다** |
| 위 구성에 하위 폴더 `CLAUDE.md`(`@AGENTS.md`) 추가 | 둘 다 로드된다 | Read 도구로 열 때 로드된다 |

공식 문서는 하위 폴더에 `CLAUDE.md`가 없으면 그 폴더의 `AGENTS.md`를 읽는다고 설명한다. 실제로는 루트에 `CLAUDE.md`가 있으면 읽지 않았다. 원인이 의도된 동작인지 결함인지는 확인하지 못했다.

그 밖에 확인한 사실은 다음과 같다.

- 하위 폴더 지침은 Read 도구로 파일을 열 때만 붙는다. 검색 결과만 보고 바로 수정하면 로드되지 않으므로, 작업 영역의 `AGENTS.md`를 먼저 읽는다는 루트 규칙을 유지한다.
- 원본 checkout 안에 있는 worktree(`.worktrees/<이름>`)에서 세션을 시작해도 원본의 지침 파일은 함께 로드되지 않았다. worktree의 파일만 로드됐다.
- 원본 checkout에서 시작한 뒤 worktree로 옮긴 세션의 하위 agent는 원본의 지침을 먼저 받고, worktree의 파일을 Read한 뒤에야 worktree의 지침을 추가로 받았다. 검토 세션 한 번에서 관찰한 결과다. 지침 파일을 바꾸는 작업은 worktree에서 세션을 시작해 확인한다.
- 세션 scratchpad 안에 만든 `CLAUDE.md`와 `AGENTS.md`는 로드되지 않는다. debug 로그에 `not loading ... is in a session's scratchpad`가 남는다. 이 위치의 fixture로는 로드 동작을 실험할 수 없다.
- 문서에 따르면 `CLAUDE.local.md`나 `.claude/CLAUDE.md`가 있으면 `AGENTS.md` fallback이 꺼진다. 이 항목은 실험으로 확인하지 못했다.

## 진단 방법

작업 worktree에서 다음을 실행해 `Memory Files` 목록을 본다.

```powershell
claude -p "/context" | Select-String -Pattern "Memory|AGENTS\.md|CLAUDE\.md"
```

하위 폴더 지침은 시작 시점의 목록에 없다. 그 폴더의 파일을 Read한 뒤에 붙는지 확인한다. 로드 여부의 근거가 필요하면 `--debug-file <경로>`로 로그를 남기고 `CLAUDE.md`와 `agents-md`를 검색한다.

Claude Code 버전이 바뀌면 이 표를 다시 확인한다. 하위 폴더 `AGENTS.md`가 루트 `CLAUDE.md`와 함께 로드되기 시작하면 `frontend/CLAUDE.md`와 `backend/CLAUDE.md`는 중복 로드 여부를 확인한 뒤 정리한다.
