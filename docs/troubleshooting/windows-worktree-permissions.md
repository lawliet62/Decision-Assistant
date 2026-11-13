# Windows 임시 worktree에서 로컬 명령이 실패하는 경우

## 기대 동작과 실행 위치

임시 worktree도 로컬 파일 시스템의 checkout이다. 탐색·검증·개발 명령은 해당 task의 worktree에서 실행하며, 필요한 원본 파일의 읽기는 별도로 허용한다. 원본 checkout으로 이동해 검증하거나 원본의 `node_modules`를 현재 worktree의 설치 상태로 간주하지 않는다. [worktree 점유 절차](../development.md#codex-worktree-점유)를 따른다.

## 실패 단계를 먼저 구분한다

| 증상 | 확인된 실패 단계 | 대응 |
| --- | --- | --- |
| `Command blocked by PreToolUse hook`와 다른 세션 점유 안내 | 셸 실행 전 Hook의 작업 디렉터리 점유 검사 | 자기 worktree를 `workdir`로 유지한다. 원본의 필요한 파일만 절대경로로 읽는다. 다른 세션의 점유 기록을 삭제하지 않는다. |
| `CreateProcessAsUserW failed: 740` | 명령 본문 실행 전 Windows 프로세스 시작 | 최소 셸 명령과 독립 sandbox probe를 비교한다. 파일 ACL 문제로 단정하지 않는다. |
| `fatal: detected dubious ownership` | 셸은 시작됐지만 Git 소유권 검사 실패 | 실제 소유자를 확인한다. 승인된 해당 worktree만 `safe.directory`로 등록한다. |
| `Get-Content` 등에서 특정 파일의 접근 거부 | 프로세스 시작 후 파일 접근 | active permission profile, managed policy, 해당 파일의 Windows ACL을 확인한다. |
| `next` 없음, `npm`을 찾을 수 없음 | 의존성 설치 또는 PATH | 해당 worktree의 의존성과 실제 실행 환경을 확인한다. 권한 상승으로 대체하지 않는다. |

## 오류 740의 재현과 개선 범위

2026-11-14 IntelliJ ACP의 Codex CLI 0.166.0, `windows.sandbox = "elevated"`에서 임시 worktree의 `Get-Location`과 `cmd.exe /c pwd`가 모두 오류 740으로 실패했다. `functions.exec_command`가 명령을 시작하기 전 실패했으므로 원본 저장소 읽기 시도가 원인이 아니었다.

같은 버전·같은 worktree의 독립 `codex sandbox -P :workspace`는 PowerShell과 cmd 실행에 성공했다. 이 차이는 현재 에이전트 명령 도구의 실행 경로에 문제가 있음을 보여주지만, 정확한 Windows 토큰·프로세스 속성 원인까지 확정하지는 못했다. 로그의 read-attributes ACL 경고도 독립 probe 성공 시 함께 나타났으므로 그 경고만으로 원인이라고 판단하지 않는다.

[공식 Windows sandbox 문서](https://learn.chatgpt.com/docs/windows/windows-sandbox)의 호환성 probe를 사용한다.

```powershell
codex -c windows.sandbox=mxc sandbox --include-managed-config --permission-profile :workspace -- cmd.exe /d /c echo MXC_OK
$LASTEXITCODE
```

이 PC에서는 `MXC_OK`, 종료 코드 0을 확인했다. MXC 안에서 임시 worktree의 셸·Node 실행 및 원본 checkout의 파일 읽기도 성공했다. 지원이 확인된 환경에서는 사용자 `config.toml`의 기존 `[features]`에 다음을 병합한다. 기존 파일을 백업하며 섹션을 중복 생성하지 않는다.

```toml
[features]
prefer_mxc = true
```

기존 `windows.sandbox = "elevated"`는 MXC가 지원되지 않을 때의 fallback으로 유지한다. 이 사례에서는 설정 후 별도 override 없는 `codex sandbox -P :workspace`의 셸·Git 조회가 성공했다. 실행 중인 ACP 세션은 이전 sandbox 선택을 유지할 수 있으므로 새 세션에서 기본 명령 도구를 다시 확인해야 한다. 독립 probe의 성공을 기존 세션 명령 도구의 복구 완료로 보고하지 않는다. 샌드박스 전체 해제나 사용자 홈 전체 쓰기 권한 확대는 이 조치에 포함되지 않는다.

## Git 소유권 오류

MXC probe에서 해당 worktree와 `.git` 파일의 소유자는 `BUILTIN/Administrators`, 실행 사용자는 일반 사용자로 나타났으며 Git이 조회를 거부했다. 이는 파일 읽기 실패나 Hook 차단과 별개였다. 신뢰하는 해당 worktree만 다음과 같이 등록한 뒤 MXC의 `git status --short --branch` 성공을 확인했다.

```powershell
git config --global --add safe.directory C:/Users/dlwld/.air/worktrees/temporary/decision-assistant-hearty-bison
```

위 경로는 재현한 worktree의 예다. 새 worktree에는 실제 경로와 소유자를 다시 확인한다. `safe.directory=*`로 모든 저장소를 신뢰하지 않는다. 반복해서 관리자 소유로 생성된다면 worktree를 만드는 IDE의 실행 권한을 먼저 확인한다. 이 재현에서는 소유권이 관리자 그룹인 사실은 확인했으나 생성 당시 IDE 권한까지 확정하지 않았다.

## 후속 검증

기존 `node --test scripts/worktree-guard.test.mjs`는 호스트 실행에서 7개 모두 통과했지만, MXC에서는 4개만 통과했다. MXC가 새로 만든 임시 저장소도 `Administrators` 소유로 나타나 Git commit이 거부됐으며, HOME을 바꾸는 설치 테스트는 사용자 `safe.directory` 예외를 읽지 못했다. 현재 worktree의 신뢰 등록은 이 임시 저장소 문제를 해결하지 않는다. 따라서 셸·읽기·현재 worktree Git 조회의 개선과 전체 테스트 환경 복구를 구분한다. IDE를 일반 사용자로 다시 실행한 뒤 새 ACP 세션에서 재검증할 필요가 있다. 이것이 임시 저장소의 소유권까지 해결하는지는 이 세션에서 검증하지 못했다. 테스트 assertion이나 Git 소유권 검사 전체를 해제해 통과시키지 않는다.

새 ACP 세션에서 기본 명령 도구로 `Get-Location`, `git status --short --branch`, 필요한 원본 파일의 읽기를 확인한다. 계속 오류 740이 발생하면 CLI 버전, Windows build, 선택된 sandbox, 실패 단계와 `CODEX_HOME/.sandbox/`의 관련 로그를 수집한다. 자격 증명, 전체 환경 변수, `.sandbox-secrets/` 내용은 수집하지 않는다. 현재 worktree의 명령이 실패한다고 원본 checkout에서 빌드를 대신 실행하지 않는다.
