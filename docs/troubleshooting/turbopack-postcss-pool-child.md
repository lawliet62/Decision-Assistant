# Turbopack build가 CSS 처리용 Node 자식 프로세스에 연결하지 못하는 경우

## 증상과 기대 결과

Windows에서 frontend의 기본 Turbopack build가 다음 오류로 실패했다([#67](https://github.com/lawliet62/Decision-Assistant/issues/67)).

```text
Failed to write app endpoint /page
Caused by:
- .../app/globals.css [app-client] (css)
- creating new process
- node process exited before we could connect to it with exit code: 0
```

`npm.cmd --prefix frontend run build`는 일반 사용자 권한에서 성공해야 한다. 최초 관측과 MXC·일반 사용자 호스트 비교는 [Windows 임시 worktree에서 로컬 명령이 실패하는 경우](windows-worktree-permissions.md#빌드-검증의-별도-문제)와 #67에 있다.

상태는 미해결이다. 아래 진단은 실패 단계가 어떤 방식으로 동작하는지 확인하고, 한 환경에서 재현되지 않은 조건을 기록했을 뿐이다. 오류를 재현하거나 원인을 확정하지 못했다.

## 실패 단계가 하는 일

Next.js 16.4.5 설치본을 기준으로 한다.

- 확인된 사실: `experimental.turbopackPluginRuntimeStrategy`의 기본값은 `childProcesses`다(`next/dist/server/config-shared.js`). frontend 설정은 이를 바꾸지 않으므로 PostCSS 변환은 별도 Node 자식 프로세스에서 실행된다.
- 확인된 사실: 자식 프로세스의 명령줄은 `"node" <.next/build/chunks/pool_entry-…postcss…js> <port>`다. build 중 `Win32_Process`의 `ExecutablePath`와 `CommandLine`을 수집해 확인했다. **자식은 `process.execPath`가 아니라 `PATH`에서 찾은 `node`로 실행된다.** `next`를 특정 `node.exe`로 직접 실행해도 `PATH`를 바꾸지 않으면 자식은 `PATH`의 다른 Node로 실행됐다.
- 확인된 사실: 자식 쪽 client는 `127.0.0.1`의 `<port>`로 TCP 연결을 연다. socket `error` listener가 없고 `close`에서 `process.exit(0)`을 호출한다. build가 생성한 `.next/build/chunks`의 client 코드에서 확인했다.
- 추정: 부모(native binary)는 `127.0.0.1`의 임시 port에 listener를 열고 자식을 실행한 뒤, 자식의 종료와 연결 수락 중 먼저 일어나는 쪽을 기다린다. native binary에 들어 있는 문자열(`127.0.0.1:0`, `spawning node pooled process`, `accepting connection`, `node process exited before we could connect to it with`)의 순서에서 읽은 것이며 source로 확인하지 않았다.

따라서 이 단계의 통신은 Node의 `ipc` stdio channel이나 Windows named pipe가 아니라 **자식에서 부모로 가는 loopback TCP 연결**이다.

## 2026-11-26 진단: 한 일반 사용자 환경에서 재현되지 않음

조건은 Windows build 10.0.26300, 비관리자, IntelliJ와 MXC 밖의 Claude Code 세션이다. 실행 위치는 commit `58596e9`(branch `feat/decision-title-input`)의 linked worktree다. 이 commit은 base `2fa6d9f`와 비교해 frontend에서는 e2e와 화면 component 2개만 다르고 build 설정·CSS·의존성은 같다. 원본 checkout에서는 build하지 않았다. 설치나 전역 설정 변경은 하지 않았다.

| 대상 | Node (next / PostCSS 자식) | 결과 |
|---|---|---|
| 실제 frontend, `.next` 삭제 후 | 시스템 26.13.1 / 26.13.1 | 3회 모두 성공. 정리 뒤 실행한 1회도 성공했으나 그 실행의 `.next` 삭제 여부는 기록하지 않았다 |
| 실제 frontend, `.next` 삭제 후 | IntelliJ ACP 24.22.1 / 24.22.1 | 3회 모두 성공 |
| 실제 frontend, `.next` 삭제 후 | Codex runtime 24.22.1 / 24.22.1 | 2회 모두 성공 |
| 실제 frontend, `.next` 삭제 후 | ACP 24.22.1 / 시스템 26.13.1 | 3회 중 2회 성공. 1회는 Google Fonts 다운로드 오류로 실패했고 이 문서의 오류가 아니다 |
| 실제 frontend, 다른 Node가 남긴 `.next` 위에서 | 26.13.1, ACP 24.22.1 | 각 1회 성공 |
| #67의 최소 예제(Tailwind PostCSS + standalone) | 26.13.1, ACP 24.22.1, Codex runtime 24.22.1 | 5회 모두 성공 |
| probe: `ipc` channel, loopback TCP, named pipe | 세 runtime 각각 | 각 25회 모두 성공 |
| probe 대조: 닫힌 port로 연결 | 세 runtime 각각 | 25회 모두 자식이 exit code 0으로 종료 |
| 실제 생성된 `pool_entry` 자식을 닫힌 port로 직접 실행 | 시스템 26.13.1 | 3회 모두 exit code 0 |

lint와 typecheck는 시스템 Node 26.13.1로만 각 1회 실행했고 exit code 0이었다. 종료 코드는 실행 시 콘솔에서 확인했으며 보존한 로그에는 남기지 않았다.

이 결과로 말할 수 있는 것은 다음과 같다.

- 확인된 사실: 이 환경에서는 #67이 사용한 Node 24.22.1만으로 오류가 재현되지 않는다. next와 PostCSS 자식이 모두 24.22.1로 실행된 것을 프로세스 수집으로 확인했다.
- 확인된 사실: 이 환경에서는 자식 프로세스 실행, `ipc` channel, loopback TCP, named pipe가 모두 동작한다.
- 확인된 사실: 자식의 loopback 연결이 거부되면 자식은 exit code 0으로 끝난다. 자식 client의 처리를 옮긴 probe와 실제 생성된 자식 양쪽에서 같았다. probe의 stderr에는 `ECONNREFUSED` 뒤에 `close`의 `process.exit(0)`이 실행된 순서가 남았다.
- 추정: 오류 문구의 `exit code: 0`은 자식이 비정상 종료했거나 Node를 찾지 못한 경우보다, 자식의 loopback 연결이 거부·차단·중단된 경우와 맞는다. 위 대조의 부모는 Turbopack의 부모가 아니다. 연결이 성립한 뒤 닫히는 경우도 exit code 0이 되지만 시험하지 않았다.

확인하지 못한 것은 다음과 같다. #67의 후속 검증 번호를 함께 적는다.

- (1) MXC 쪽 비교: MXC 안에서는 아무것도 실행하지 못했다. MXC의 제한과 loopback 연결 실패의 관계는 시험하지 않은 가설이다.
- (1) 호스트 쪽 차이의 분리: #67의 호스트 실패는 commit `965474a` 시점의 linked worktree에서 MXC 밖 일반 사용자 호스트 실행으로 관측됐다. 이번 실행은 다른 commit의 다른 worktree, 다른 실행 환경이므로 runtime, 기존 build 산출물, 실행 환경 중 무엇이 달랐는지 분리하지 못했다. 다른 Node가 남긴 `.next` 위의 실행은 같은 worktree의 산출물을 쓴 각 1회뿐이다.
- (2) probe는 호스트에서만 실행했다.
- (3) Node 22(CI의 version)는 이 호스트에 없어 비교하지 못했다.
- (4) 격리 정책을 유지하는 수정이나 한정된 실행 방법은 찾지도 검증하지도 못했다. 오류가 재현되지 않아 검증할 대상이 없었다.

## 다음에 재현되면 확인할 순서

아래는 실행하지 않은 계획이다.

1. 실패한 세션에서 `where.exe node`와 `node -v`를 기록한다. PowerShell의 `where`는 `Where-Object`의 alias라 아무것도 출력하지 않는다. `next`를 실행한 Node와 `PATH`의 Node가 다를 수 있다.
2. build 중 `node.exe` 프로세스의 `ExecutablePath`와 `CommandLine`을 수집해, 명령줄에 `pool_entry`가 있는 자식이 어떤 Node로 실행됐는지 확인한다. 오류 문구가 자식의 종료 코드를 담고 있으므로 자식 프로세스는 생성된 것이다. 주기적으로 조회하는 수집은 연결이 거부돼 곧바로 끝나는 자식을 놓칠 수 있으므로, 자식이 보이지 않는다는 결과만으로 실패 단계를 판정하지 않는다.
3. 같은 세션에서 loopback TCP probe와 닫힌 port 대조를 실행한다. probe script는 [#67의 2026-11-26 진단 댓글](https://github.com/lawliet62/Decision-Assistant/issues/67#issuecomment-7171028638)에 있다. `ipc` channel이나 named pipe만 시험하면 이 단계와 다른 통신을 시험하게 된다.
4. 오류가 재현된 그 worktree에서 기존 `.next`를 둔 실행과 새 `distDir` 실행을 비교해 기존 산출물의 영향을 분리한다. 원본 checkout에서 대신 build하지 않는다.
5. MXC 안과 밖에서 같은 probe와 #67의 최소 예제를 실행해 비교한다.

관리자 실행, sandbox 전체 해제, 사용자 홈 전체 쓰기 허용, 광역 `safe.directory` 등록, 소유권·ACL 일괄 변경은 해결책으로 전제하지 않는다. 기본 build script와 품질 gate도 바꾸지 않는다.
