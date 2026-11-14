# 개발 실행 종료 후 backend가 남는 경우

## 증상과 원인

`npm run dev`에서 frontend가 먼저 종료되거나 실행기에 SIGTERM이 전달되면 backend 포트와 갱신 작업도 종료되어야 한다. PR #61에서 backend 실행을 Node wrapper로 통합했지만, wrapper가 `spawnSync()`로 Gradle을 기다려 종료 신호를 하위 프로세스에 전달하지 못했다. wrapper만 종료되면 Gradle·backend가 남을 수 있다. [Codex 리뷰](https://github.com/lawliet62/Decision-Assistant/pull/61#discussion_r4565290136)에서 지적한 문제다.

2026-11-14에 기존 `588a673`의 wrapper를 격리된 Linux 환경에서 재현했다. 가짜 Gradle 실행기는 실제 TCP 서버와 하위 서버를 실행하고 사양 worker도 별도로 대기한다. wrapper에 SIGINT를 전달한 뒤 backend 포트가 열려 있으면 실패하는 테스트다. 외부 수집·DB·Secret은 사용하지 않는다. 테스트용 프로세스는 마지막에 정리하며 실제 상품 가격·확인 시각은 변경하지 않는다.

## 수정과 검증

- Gradle을 비동기로 실행하고 SIGINT·SIGTERM을 소유한 프로세스 트리에 전달한다. 사양 validator 준비 과정도 같은 경로로 실행하므로 준비 중 취소 후 backend나 worker를 새로 시작하지 않는다.
- POSIX에서는 새 프로세스 그룹을 생성해 신호를 전달하고, 부모보다 늦게 종료하는 하위 서버에도 정리 시간을 준다. 종료를 무시하면 5초 후 SIGKILL로 정리한다. 부모의 종료만으로 정리 성공을 판단하지 않는다.
- Windows에서는 `taskkill /T /F`로 소유 트리를 종료하고 종료를 기다린다. POSIX의 graceful shutdown과 같은 동작이라고 주장하지 않는다.
- wrapper의 Gradle 실행에는 `--no-daemon`을 사용한다. 다른 실행이 재사용하는 daemon을 종료 대상에 포함하지 않기 위한 선택이며 실행마다 JVM 시작 비용이 생긴다. 검증용 direct Gradle 호출의 기존 설정은 바꾸지 않는다.
- `dev.mjs`도 backend·frontend 정리가 끝날 때까지 기다린다. backend wrapper에는 10초를 주어 내부의 5초 정리를 먼저 끝내게 한다. 자연 종료한 Gradle의 종료 코드는 유지한다.

`scripts/gradle-wrapper.test.mjs`는 Linux에서 10개 사례를 검증한다. SIGINT·SIGTERM, frontend 선행 종료, dev 실행기 SIGTERM, 사양 validator·Compose 준비 중 취소, 신호 무시, wrapper와 내부 프로세스의 정리 순서, 종료 코드 보존과 트리 종료 후 포트 해제를 포함한다. Windows에서는 종료 코드와 실제 하위 서버 포트 해제를 검증하고 POSIX 신호 테스트는 실행하지 않는다. `npm run check`와 CI에 포함한다.

## 남는 경계

이 테스트의 서버와 사양 worker는 격리된 대역이며 실제 Spring Boot 종료나 외부 가격·사양 갱신 성공을 증명하지 않는다. SIGKILL·강제 전원 종료처럼 실행기가 정리 코드를 실행할 수 없는 경우는 보장하지 않는다. 예상치 못한 포트 점유가 남으면 현재 실행의 PID·하위 트리를 확인하며 다른 개발 세션의 Java·Node 프로세스를 일괄 종료하지 않는다.

별도 Windows 실측에서는 임시 PostgreSQL과 실제 Spring Boot를 `scripts/gradle.mjs bootRun`으로 기동해 health `UP`을 확인하고, 관리 프로세스 종료 후 18086 포트 해제를 확인했다. 외부 기능은 모두 비활성이고 가격 관측은 0건이었다. 이는 Windows의 트리 종료 실측이며 Linux Spring Boot의 graceful shutdown을 검증한 결과로 확장하지 않는다. 저장소 전체 `npm run check`도 통과했다.
