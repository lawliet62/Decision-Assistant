# 로컬 배포·브라우저 검증의 실패 단계 구분

2026-11-09의 [MVP 검증](../mvp-readiness.md)에서 확인한 사례다. local smoke는 cloud 자격 증명 없이 동작해야 하고, 브라우저 fixture 실패와 제품 API 실패를 구분해야 한다.

## Compose override가 운영 Secret 참조를 남긴 경우 — 해결

운영 Compose와 local override를 합쳤을 때 offline backend 시작이 실패했다. local profile·secrets mode만 바꾸어도 운영 파일의 `DB_SECRET_VERSION`, `AUTH0_SECRET_VERSION` 환경값은 그대로 병합된다. 기존 secret loader는 offline에서 이 참조를 허용하지 않는다. 이는 DB 연결 실패보다 앞선 설정 검증이다.

local override에 해당 두 값을 빈 문자열로 명시하고 `config --quiet`, Linux 컨테이너 시작, 실제 API 생성·조회·삭제와 DB backup/restore로 검증했다. 운영 loader를 완화하거나 실제 Secret을 로컬에 복사하지 않았다. 관련 파일은 [local override](../../deploy/compose.local.yaml)이며, 다음 진단에서도 profile만 보지 말고 최종 병합 설정의 비밀 아닌 참조를 확인한다. 전체 환경값을 로그에 출력하지 않는다.

## 브라우저 fixture 전송 실패 — 일부 미해결

2026-11-16 정정·재검토 변경 검증에서도 전체 실행 중 `intake-templates` 연결 종료와 목록 로딩 정체가 관찰됐다. 새 완료 그룹의 건수·이력 문구 기대값은 제품 변경에 맞춰 수정했다. 그와 별개로 최초 전체 실행에서 남은 네 개의 로딩·연결 실패는 분리 재실행에서 통과했다. assertion이나 timeout을 완화하지 않았다. 이 결과는 환경 원인을 확정하거나 최초 전체 실행을 통과로 바꾸는 근거가 아니다. [Windows 브라우저 진단](windows-worktree-permissions.md)의 hydration·API 요청 단계 구분을 함께 따른다.

회고 복구 만료 fixture는 DB의 `now()`로 삭제 시각을 만들고 서버의 `Instant.now()`로 판정하던 테스트에서 409 대신 200이 한 차례 관찰됐다. 서로 다른 시계와 정확한 30일 경계를 함께 사용하는 구조를 확인했으며, 실제 시계 차이의 크기는 측정하지 않았다. fixture를 서버와 같은 시계에서 30일보다 1초 이전으로 생성하도록 바꾼 뒤 통과했다. 이는 제품의 30일 정책 변경이 아니라 만료된 fixture를 확실하게 구성하는 조치다.

전체 Chromium 36개 중 기존 재탐색 테스트에서 route callback 내부 추가 fixture HTTP 요청이 timeout에 걸렸다. callback이 제품 응답과 별도의 네트워크 요청에 의존하고 있었다. refresh 버튼 활성화를 확인하고 fixture를 route 등록 전에 취득하여 callback은 응답 전달만 하도록 변경했다. 제품 기대값·assertion·timeout은 약화하지 않았다.

별도 실행에서는 comparison 테스트의 `beforeEach` fixture 생성 POST가 `socket hang up`으로 실패했고 35개는 통과했다. 해당 실패는 제품 화면 조작 이전이며 server 종료·keep-alive 경합 등 구체적 원인은 확인하지 못했다. 자동 재시도를 추가해 숨기지 않고 전체 suite를 다시 실행했다. 반복 시 mock server의 연결/종료 로그와 Playwright trace를 함께 수집한다. 두 네트워크 오류를 같은 원인으로 단정하거나 새 목록 기능의 DB 실패로 분류하지 않는다.
