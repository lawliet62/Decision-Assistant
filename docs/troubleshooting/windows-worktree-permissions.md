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

## 일반 권한 IDE 세션에서의 재검증

2026-11-14 IntelliJ 상시 관리자 실행을 해제한 뒤 같은 ACP 대화를 재개했다. 기본 명령 도구와 sandbox 밖 호스트 모두 관리자 역할 검사 결과가 `False`였다. Windows 프로세스 토큰의 `TokenElevation`을 읽어 IntelliJ와 ACP Codex 프로세스도 `False`임을 직접 확인했다. 새 IDE 프로세스에서 대화가 재개된 상황이며, 새로운 대화의 SessionStart 전체를 별도로 검증한 것은 아니다. sandbox 로그는 `Windows sandbox selection: mxc`를 기록했다. 아래 결과는 기존 소유권·ACL·Git 신뢰 설정을 수동 변경하지 않고 확인했다.

| 항목 | 결과와 범위 |
| --- | --- |
| 새 worktree | 현재 worktree 아래 `.permission-audit/new-worktree`에 일반 사용자 호스트가 동일 저장소의 detached worktree를 생성했다. 루트와 `.git` 파일은 사용자 소유였다. `git -c safe.directory=`를 사용한 status·log·diff와 호스트의 `write-tree`가 성공했다. |
| 기존 소유권 | 기존 linked worktree 19개의 루트와 `.git` 파일은 `Administrators` 소유였다. 원본 루트와 `.git` 디렉터리는 사용자 소유지만, 원본의 `AGENTS.md`, `frontend/package.json`, `.git/config` 표본은 관리자 그룹 소유였다. |
| 기존 ACL | 조사한 루트·Git 경로·소스 표본에 사용자 `FullControl`이 있었다. 원본 `.git`의 sandbox capability SID 대상 deny는 human 사용자의 권한과 구분한다. 모든 하위 파일을 재귀 감사한 결과는 아니다. |
| Git 신뢰 예외 | global 예외는 현재 worktree 한 곳뿐이었다. 현재 worktree도 호스트와 MXC 양쪽에서 `git -c safe.directory= status`가 성공했다. 따라서 이 세션에서는 해당 예외가 정상 동작에 필수라는 근거가 없다. 예외는 제거하지 않았다. |
| 읽기·쓰기 격리 | MXC에서 원본 `AGENTS.md` 읽기와 현재 worktree의 진단 파일 생성·읽기가 성공했다. 원본 및 다른 linked worktree의 기존 `AGENTS.md`를 `FileMode.Open`으로 쓰기 핸들만 열려는 시도는 거부됐다. 파일 내용은 변경하지 않았다. |
| Hook | 점유된 원본을 작업 디렉터리로 지정한 무변경 명령은 실제 PreToolUse Hook에서 차단됐다. 현재 worktree 점유 기록도 유지됐다. MXC의 기존 guard 테스트 7개가 모두 통과해, 이전 관리자 환경에서 실패한 임시 Git fixture 생성·commit 및 HOME 격리 테스트도 통과했다. |
| 오류 740 | 기본 MXC 명령 도구는 정상 동작했다. 일반 사용자 호스트가 시작한 독립 legacy probe(`windows.sandbox=elevated`, `features.prefer_mxc=false`)와 명시적 MXC probe도 모두 성공했다. |

관리자 실행과 sandbox 선택이 함께 바뀌었으므로 오류 740의 단독 원인을 관리자 실행으로 확정하지 않는다. 이전 관리자 환경에서도 독립 legacy probe는 성공했고 기본 명령 도구만 실패했다. 현재는 일반 권한 + MXC의 기본 명령 도구가 정상이라는 점까지 확인했다. 일반 권한의 기본 명령 도구를 legacy로 고정한 비교는 수행하지 않았으며, 이를 위해 동작하는 사용자 설정을 되돌리지 않았다. 기존 read-attributes ACL 경고는 정상 legacy probe에서도 남아 있어 오류 740의 직접 근거가 아니다.

### 예외 최소화와 기존 소유권 처리

새 worktree는 일반 권한으로 생성하고 광역 `safe.directory` 등록은 하지 않는다. 현재 worktree의 예외는 제거 후보이며, 다음은 **아직 실행하지 않은 제안**이다. 단일 값만 제거하므로 다른 Git 설정이나 다른 저장소의 예외를 건드리지 않는다.

```powershell
git config --global --fixed-value --unset-all safe.directory C:/Users/dlwld/.air/worktrees/temporary/decision-assistant-hearty-bison
git status --short --branch
```

관리자 그룹 소유라는 사실만으로 기존 worktree 전체에 `takeown`, `icacls /reset` 또는 재귀 owner 변경을 적용하지 않는다. 실제 실패 경로가 생기면 그 경로의 소유권·상속·deny·사용자 접근을 확인한 뒤 영향과 복구 방법을 먼저 검토한다. 현재 확인한 표본에는 일괄 수정이 필요하다는 증거가 없다. 새 진단 worktree와 관련 Git 메타데이터는 삭제하지 않고 남겼으며, `.permission-audit/.gitignore`로 진단 산출물의 우발적 stage만 방지했다.

### 일반 사용자와 sandbox 밖 실행의 차이

`require_escalated`는 이 환경에서 sandbox 밖 실행을 뜻하며 Windows 관리자 실행과 같지 않다. 실제 호스트 관리자 역할도 `False`였다. MXC 안에서 승인 인증 helper의 PowerShell 실행은 `AuthorizationManager` 오류로, execution policy 조회는 레지스트리 접근 제한으로 실패했다. 같은 helper는 일반 사용자 호스트에서 정상 인증됐다. 개인 로그인·다른 인증 경로·execution policy 변경으로 우회하지 않았다.

MXC의 backend 테스트는 사용자 공용 Gradle cache의 `.zip.lck` 쓰기에서 거부됐다. 해당 파일은 사용자 소유이고 사용자 `FullControl`이 있어 관리자 소유권 손상과는 구분한다. 공용 cache와 네트워크가 필요한 명령은 현재 worktree를 유지한 채 승인된 일반 사용자 호스트에서 실행한다. 원본 checkout에서 대신 빌드하거나 사용자 홈 전체에 sandbox 쓰기를 허용하지 않는다.

MXC는 기본 실행의 경로 격리를 제공하지만 sandbox 밖 명령은 같은 Windows 사용자 권한으로 실행된다. Hook도 작업 디렉터리 중심의 충돌 방지이며 절대경로 쓰기를 모두 통제하는 OS 보안 경계가 아니다. 따라서 sandbox 밖 실행은 자기 task의 경로·공용 Git metadata·필요한 cache에 한정하고 다른 세션의 경로를 쓰기 대상으로 삼지 않는다. 관측한 쓰기 거부 결과를 sandbox 밖 실행까지 보장하는 것으로 확대하지 않는다.

### 빌드 검증의 별도 문제

일반 권한의 현재 worktree에서 의존성 설치와 frontend lint·typecheck가 성공했다. 기본 MXC build는 Google Fonts 다운로드에서 실패했다. 같은 build를 일반 사용자 호스트에서 실행하면 Turbopack이 `globals.css` 처리용 Node 자식 프로세스가 연결 전에 exit code 0으로 종료됐다는 오류를 냈다. 오류 제목의 `Failed to write app endpoint`만으로 ACL 문제라고 판단하지 않는다. 같은 사용자·같은 worktree에서 [공식 webpack 선택 옵션](https://nextjs.org/docs/app/api-reference/cli/next)을 사용한 `npm.cmd --prefix frontend run build -- --webpack`은 성공했다. 기본 build script, dependency, 품질 gate는 바꾸지 않았다. 기본 Turbopack 경로의 원인은 아직 미해결이다.

전체 검사와 별도 backend 검사를 동시에 시작한 진단 실행에서는 공유 test-results 파일 충돌로 전체 검사가 실패했다. 이 결과는 환경의 권한 결함을 입증하지 않는다. 같은 worktree의 Gradle 검증은 반드시 순차 실행하며, 이후 검증 결과는 해당 재실행을 기준으로 판단한다.

순차 재실행한 `node scripts/gradle.mjs test integrationTest`는 성공했다. XML 결과의 단위 테스트 244개와 통합 테스트 98개는 실패·오류·skip이 모두 0이었고, 이어 실행한 `node scripts/check-contract.mjs`도 통과했다. Node 검증 48개와 Sony 검증 2개도 통과했다. Gradle wrapper 검증은 2개 통과·8개 기존 플랫폼 skip이었다. frontend lint·typecheck 및 webpack build 통과와 기본 Turbopack build 미해결을 함께 보고하며, 원래의 `npm run check` 전체 통과로 바꾸어 표현하지 않는다.

## 이전 관리자 환경에서의 오류 740 재현

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

## 이전 관리자 환경의 Git 소유권 오류

MXC probe에서 해당 worktree와 `.git` 파일의 소유자는 `BUILTIN/Administrators`, 실행 사용자는 일반 사용자로 나타났으며 Git이 조회를 거부했다. 이는 파일 읽기 실패나 Hook 차단과 별개였다. 신뢰하는 해당 worktree만 다음과 같이 등록한 뒤 MXC의 `git status --short --branch` 성공을 확인했다.

```powershell
git config --global --add safe.directory C:/Users/dlwld/.air/worktrees/temporary/decision-assistant-hearty-bison
```

위 경로는 재현한 worktree의 예다. 새 worktree에는 실제 경로와 소유자를 다시 확인한다. `safe.directory=*`로 모든 저장소를 신뢰하지 않는다. 반복해서 관리자 소유로 생성된다면 worktree를 만드는 IDE의 실행 권한을 먼저 확인한다. 이 재현에서는 소유권이 관리자 그룹인 사실은 확인했으나 생성 당시 IDE 권한까지 확정하지 않았다.

## 이전 진단에서 남겼던 후속 검증

기존 `node --test scripts/worktree-guard.test.mjs`는 호스트 실행에서 7개 모두 통과했지만, MXC에서는 4개만 통과했다. MXC가 새로 만든 임시 저장소도 `Administrators` 소유로 나타나 Git commit이 거부됐으며, HOME을 바꾸는 설치 테스트는 사용자 `safe.directory` 예외를 읽지 못했다. 현재 worktree의 신뢰 등록은 이 임시 저장소 문제를 해결하지 않는다. 따라서 셸·읽기·현재 worktree Git 조회의 개선과 전체 테스트 환경 복구를 구분한다. IDE를 일반 사용자로 다시 실행한 뒤 새 ACP 세션에서 재검증할 필요가 있다. 이것이 임시 저장소의 소유권까지 해결하는지는 이 세션에서 검증하지 못했다. 테스트 assertion이나 Git 소유권 검사 전체를 해제해 통과시키지 않는다.

새 ACP 세션에서 기본 명령 도구로 `Get-Location`, `git status --short --branch`, 필요한 원본 파일의 읽기를 확인한다. 계속 오류 740이 발생하면 CLI 버전, Windows build, 선택된 sandbox, 실패 단계와 `CODEX_HOME/.sandbox/`의 관련 로그를 수집한다. 자격 증명, 전체 환경 변수, `.sandbox-secrets/` 내용은 수집하지 않는다. 현재 worktree의 명령이 실패한다고 원본 checkout에서 빌드를 대신 실행하지 않는다.
