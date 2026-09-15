# MVP 배포·운영 runbook

단일 backend + Next.js + PostgreSQL 배포를 전제로 한다. 이 문서는 자동 배포나 외부 유료 구매 권한을 의미하지 않는다. 실제 배포 대상은 아직 미정이다. Secret은 GCP Secret Manager에서 backend가 직접 조회하며 리소스·IAM·운영 Identity 준비는 운영자가 수행한다.

## 출시 전 확인

1. human이 PR을 검토·merge한다. `npm run check`와 `npm --prefix frontend run test:e2e`가 통과해야 한다.
2. production은 `oidc` profile, Auth0 client/issuer, exact-email allowlist와 public HTTPS origin을 구성한다. `local` profile을 외부로 공개하지 않는다. 세션 cookie Secure/HttpOnly, CSRF, same-origin `/api` proxy를 유지한다.
3. PostgreSQL 백업을 만들고 별도 DB에서 restore와 Flyway migration을 검증한다. V7은 `search_monthly_usage(month, calls)`만 추가한다. 기존 결정/평가 데이터 변환은 없다. EARBUDS 데이터가 생긴 뒤 이를 모르는 구버전으로 되돌리지 않는다. 장애 시 기능 flag를 끄는 forward recovery를 우선한다.
4. 개인정보·provider 전송 고지, 보존/삭제 정책, TLS/host 설정, DB backup/restore 책임자를 확정한다. 이 정책 승인과 실제 infrastructure 검증은 코드 테스트로 대체할 수 없다.
5. 실제 key가 있는 staging에서 아래 smoke를 실행한다. mock 테스트는 provider의 실제 가용성·검색 품질을 보장하지 않는다.

## Server-only configuration

| 환경 변수 | 기본값 / 역할 |
| --- | --- |
| `DECISION_ASSISTANT_SEARCH_ENABLED` | `false`; Serper lookup 활성화 |
| `SERPER_SECRET_VERSION` | 활성화 시 Dev/Prod의 `serper-api-key` 숫자 버전 필수 |
| `DECISION_ASSISTANT_SEARCH_MONTHLY_LIMIT` | `1000`; 1~1000만 허용, 낮춰 시작 가능 |
| `DECISION_ASSISTANT_WEB_ENABLED`, `FIRECRAWL_SECRET_VERSION` | 기본 비활성; 활성화 시 `firecrawl-api-key` 버전 필수 |
| `DECISION_ASSISTANT_AI_ENABLED` | 기본값 `false`; 활성화 시 선택한 provider의 Secret 버전 필수 |
| `DECISION_ASSISTANT_REMINDERS_ENABLED` | 기존 기본값 `false`; email 설정을 검증한 경우만 활성화 |

Serper endpoint는 `https://google.serper.dev/search`로 고정한다. 서버에서 POST, `X-API-KEY`, `q/num/gl/hl`을 사용한다. [공식 Serper](https://serper.dev/) 계정/Playground에서 key·잔여 크레딧·계약을 확인한다. 자동 충전은 켜지 않는다. 유료 구매·충전은 별도 human 결정이다.

## Secret management

전략은 [ADR 0008](decisions/0008-identity-based-secret-management.md)을 따른다. **코드 구현과 실제 cloud provisioning/production 검증은 별개**다. 이번 변경은 프로젝트·Secret·IAM을 생성하거나 운영 호스팅을 선택하지 않는다.

### Configuration contract

`local` 기본은 `offline`; 운영은 `oidc` + `gcp`만 허용한다. `local`/`test`와 `oidc`를 혼합하지 않는다. 외부 기능은 기본 비활성이고 활성화한 기능의 Secret이 없으면 기동을 거부한다. `test`는 cloud-free fixture 전용이며 배포에 사용하지 않는다.

- `DECISION_ASSISTANT_SECRETS_MODE`: `offline` 또는 `gcp`. offline은 local/test 전용이며 provider의 실제 키로 실행하는 우회 모드가 아니다.
- `DECISION_ASSISTANT_SECRETS_DEV_PROJECT_ID`, `DECISION_ASSISTANT_SECRETS_PROD_PROJECT_ID`: 서로 다른 GCP 프로젝트 ID. local은 Dev, oidc는 Prod로 경로를 구성한다. 임의 프로젝트 경로나 `latest`를 Secret 버전에 입력할 수 없다.
- YAML의 동일 설정은 `decision-assistant.secrets.mode`, `dev-project-id`, `prod-project-id`, `versions.<secret-name>`이다. 실제 payload를 YAML, CLI 인자, 환경 변수에 직접 지정하지 않는다. 기존 `*_API_KEY`, `AUTH0_CLIENT_SECRET`, `DB_PASSWORD`와 canonical Spring Secret 설정이 남으면 기동을 거부한다.

| Secret 이름 | 버전 설정 | 필요한 경우 | 바인딩 대상 |
| --- | --- | --- | --- |
| `gemini-api-key` | `GEMINI_SECRET_VERSION` | AI + Gemini | `spring.ai.google.genai.api-key` |
| `openai-api-key` | `OPENAI_SECRET_VERSION` | AI + OpenAI | `spring.ai.openai.api-key` |
| `serper-api-key` | `SERPER_SECRET_VERSION` | 제품명 검색 | `decision-assistant.search.api-key` |
| `firecrawl-api-key` | `FIRECRAWL_SECRET_VERSION` | 웹 수집 | `decision-assistant.web.api-key` |
| `resend-api-key` | `RESEND_SECRET_VERSION` | 메일 알림 | `decision-assistant.reminders.resend-api-key` |
| `auth0-client-secret` | `AUTH0_SECRET_VERSION` | oidc 운영 | `spring.security.oauth2.client.registration.auth0.client-secret` |
| `db-password` | `DB_SECRET_VERSION` | 운영 필수, 공유 Dev DB 선택 | `spring.datasource.password` |

이름은 고정하며 개별 Secret으로 저장한다. 참조는 `projects/{선택된 프로젝트}/secrets/{이름}/versions/{숫자}`다. 로컬 Compose DB는 개발용 고정 비밀번호를 사용한다. 공유 Dev DB는 Compose 연동을 끄고 Dev `db-password` 버전을 지정한다. 운영에서 개발 Compose를 실행하거나 connection details로 Secret 설정을 우회하지 않는다.

`DB_URL`/`DB_USERNAME`, Auth0 issuer/client ID, feature flag, model, timeout, Secret ID/버전은 Configuration이다. DB URL에 비밀번호를 포함하지 않는다. 운영 allowlist는 개인정보·접근 정책이므로 비밀 아닌 값이어도 공개 저장소에 실제 목록을 넣지 않는다. Qodana의 CI token은 GitHub Secret에 유지한다.

### Provisioning / IAM checklist (operator)

1. 별도 Dev/Prod 프로젝트와 결제를 준비하고 Secret Manager API를 활성화한다. 저장소에는 실제 provider key나 서비스 계정 JSON key를 추가하지 않는다.
2. 사용할 Secret만 생성한다. 별도 residency 요구가 없다는 현재 전제에서는 자동 복제와 Google 관리 암호화를 사용한다. 실제 값 입력은 콘솔 등 안전한 관리 경로로 수행하고 shell 인자·history·PR에 값을 남기지 않는다.
3. Dev/Prod에 다른 외부 API key, DB 사용자/비밀번호, 필요한 경우 별도 Auth0 application을 사용한다. 같은 값을 두 프로젝트에 복사하는 것은 환경 격리가 아니다. 외부 서비스가 key별 권한/할당량 격리를 제공하는지는 별도 확인한다.
4. 개발자는 Dev의 필요한 Secret에만 `roles/secretmanager.secretAccessor`; Prod runtime은 전용 서비스 계정에 필요한 Prod Secret의 같은 역할만 부여한다. 앱에 Secret 생성/갱신/삭제/list/IAM 관리 권한이나 Editor/Owner를 부여하지 않는다. 상위 프로젝트의 상속 권한까지 검토한다.
5. Secret 관리자는 runtime reader와 역할을 분리한다. 같은 human이 프로젝트 관리자도 맡는다면 일상 개발 ADC Identity와 관리 Identity를 구분한다. IAM 정책 변경과 Secret payload 갱신은 앱이 하지 않는다.
6. Dev/Prod에서 Secret Manager의 Data Access 감사 로그를 활성화하고 접근 Identity를 확인한다. Admin Activity만으로 payload 읽기 감사가 완성된 것으로 간주하지 않는다. 로그 접근 권한과 비용도 확인한다. 별도 SIEM/조직 governance는 만들지 않는다.
7. 운영 호스팅 결정 후 attached service account 또는 Workload Identity Federation을 연결한다. 앱은 운영에서 Compute Engine 계열 ADC 또는 External Account ADC를 허용하고 사용자 ADC/static service-account key는 거부한다. 허용 credential 종류 검사는 IAM 정책 검증을 대체하지 않는다. 실제 호스팅의 Identity 연결·scope·trust 조건은 그때 검증한다.

### Startup / failure / rotation

- Config data 이후, datasource/OIDC/AI bean 생성 이전에 필요한 Secret만 조회한다. 전체 조회 완료 전에는 payload를 property source에 설치하지 않는다. SDK client는 초기 로딩 후 닫고 값은 JVM 메모리에서 사용한다. 파일/환경으로 export하거나 매 API 요청마다 조회하지 않는다.
- ADC 탐색·토큰 획득·SDK 작업을 포함한 대기 상한은 30초다. 각 AccessSecretVersion RPC는 최대 5초, `UNAVAILABLE`/`DEADLINE_EXCEEDED`만 최대 2회 시도한다. SDK 작업 전체가 상한을 넘으면 취소 요청 후 startup 실패한다. 권한/미존재/비활성 버전은 자동 fallback하지 않는다.
- 성공 로그는 `secret_bootstrap outcome=success duration_ms=...`; 실패는 안전한 `Secret bootstrap failed: CODE`로 보고한다. SDK 원문·payload·credential 파일·전체 설정을 출력하지 않는다. startup 이전 실패이므로 HTTP endpoint/metric 존재를 전제하지 않는다. 기존 Actuator는 health/info만 유지하고 env/configprops/heapdump를 공개하지 않는다.
- `CONFIGURATION_INVALID`는 설정 형식, `DISTINCT_PROJECT_IDS_REQUIRED`는 프로젝트 목록, `VERSION_REQUIRED_*`는 숫자 버전, `PLAINTEXT_INPUT_*`는 이전 설정 충돌을 확인한다. `IDENTITY_TYPE_NOT_ALLOWED`/`AUTHENTICATION_OR_CLIENT_UNAVAILABLE`는 ADC, `GCP_PERMISSION_DENIED`는 IAM/API/quota project, `GCP_NOT_FOUND`/`GCP_FAILED_PRECONDITION`은 Secret/버전 상태, `TIMEOUT`은 네트워크/인증/서비스 상태를 확인한다.
- 장애 중에도 이미 기동한 프로세스는 보유한 키로 동작할 수 있다. 정상 인스턴스를 무조건 재시작하지 않는다. 기동 실패를 무한 재시도로 덮거나 권한을 넓히지 않는다. 복구는 설정/IAM/서비스를 고친 후 재시작한다. 선택적 기능을 명시적으로 끄고 재시작하는 것은 허용하지만 DB/Auth0 Secret을 우회하지 않는다.
- 새 외부 key를 발급하고 새 Secret 버전 등록 → Dev 검증 → Prod 숫자 참조 변경/재시작 → provider smoke 순으로 교체한다. 단순히 새 버전 등록만으로 기존 프로세스가 갱신되지 않는다. 잘못된 배포는 여전히 유효한 이전 key의 버전 참조로 롤백한다. 폐기된 외부 key나 파괴된 버전으로는 복구할 수 없다.
- 접근 권한 회수/Secret 버전 비활성화만으로 이미 읽힌 외부 key가 무효화되지는 않는다. 유출 시 provider에서 key를 폐기·재발급하고 앱을 재시작한다. 이전 버전은 사용 종료를 검증한 후 비활성화하고 복구 기간 후 파괴한다. 자동 rotation infrastructure는 없다.

### Migration / acceptance checklist

- [ ] 테스트: ADC/Prod 접근 없이 `npm run check`와 mock E2E 성공.
- [ ] Dev: 필요한 기능만 켜서 숫자 버전으로 기동·실제 provider smoke 성공. 비활성 provider Secret은 불필요함 확인.
- [ ] 격리: Dev Identity로 Prod Secret 접근이 거부되고 Prod runtime에 Dev 권한이 없음 확인. 일상 Identity에 상속된 Owner/Editor가 없음 확인.
- [ ] 실패: 가짜/비활성 버전, 잘못된 Identity, 접근 거부, 네트워크 실패 시 제한 시간 안에 안전한 오류로 기동 실패하고 평문 fallback 없음 확인.
- [ ] 운영: 호스팅 Identity, OIDC/DB/AI client 바인딩, TLS·백업·실제 provider smoke 확인. 이 단계 전에는 Production 전환 완료로 표시하지 않음.
- [ ] 정리: 이전 실행 환경/로컬 파일/배포 설정의 실제 key 제거; 존재했던 키의 복제·노출 범위를 검토하고 필요 시 재발급. 실제 값을 출력하며 확인하지 않음.

Secret Manager 접근 권한과 provider key 유효성은 다른 문제다. 조회 성공이 provider 인증 성공을 보장하지 않으며 위 smoke가 필요하다. 무료 한도는 결제 계정 전체 합산이고 Disabled 버전도 과금 대상이다. 불필요한 버전 누적·재시작 반복·감사 로그 비용을 확인한다. 비용 알림은 강제 지출 상한이 아니다.

공식 참고: [SDK](https://docs.cloud.google.com/secret-manager/docs/reference/libraries), [운영 권장사항](https://docs.cloud.google.com/secret-manager/docs/best-practices), [IAM](https://docs.cloud.google.com/secret-manager/docs/manage-access-to-secrets), [감사 로그](https://docs.cloud.google.com/secret-manager/docs/audit-logging), [가격](https://cloud.google.com/secret-manager/pricing).

## 비용과 실패 처리

- Serper는 연결 timeout 3초, 요청 timeout 8초, redirect 미추적, 자동 재시도 없음. 결과가 적거나 없으면 그대로 안내하며 AI로 채우지 않는다.
- UTC 월별 예약 카운터는 외부 요청 전에 commit된다. 같은 DB를 공유하는 process와 동시 요청 모두 월 상한을 공유하며 재시작해도 초기화되지 않는다. timeout·provider 오류도 이미 과금될 수 있으므로 차감한다.
- 이 앱은 key의 전체 계정 사용량을 조회하지 않는다. 다른 배포가 다른 DB로 같은 key를 쓰거나 사람이 직접 API를 호출하면 앱의 상한 밖에서 소비된다. MVP key는 이 배포 전용으로 관리한다.
- 사용자별 5회/분·동시 2건은 single-instance burst 제한이다. replica를 늘릴 때는 별도 검토한다.
- `SEARCH_DISABLED`(503), `SEARCH_MONTHLY_LIMIT`/`SEARCH_RATE_LIMIT`/`SEARCH_BUSY`(429), `SEARCH_TIMEOUT`(504), `SEARCH_PROVIDER_UNAVAILABLE`(502)를 구분한다. 브라우저는 재시도 버튼/검색과 URL fallback을 제공하며 조용히 다른 provider로 전환하지 않는다.
- provider의 raw 오류·key·query를 사용자 응답이나 로그에 쓰지 않는다. HTTP wire/body logging과 proxy request-body logging도 production에서 켜지 않는다.
- Firecrawl/AI/Resend는 이 1,000회 cap에 포함되지 않는다. 해당 계정의 사용량·상한·과금 알림을 따로 확인한다. 별도 비용 통제가 준비되지 않으면 해당 feature flag를 끄고 직접 입력 MVP로 운영한다.

## 관측과 incident 대응

- `product_event=product_lookup_finished`의 `outcome`, `duration_ms`로 성공·한도·실패와 지연을 관찰한다. query/user/URL은 기록하지 않는다.
- Micrometer `product.lookup.requests`, `product.lookup.duration`은 낮은 cardinality outcome label만 갖는다. 기존 Actuator 공개 범위는 health/info 그대로이며 metrics endpoint를 인터넷에 추가 노출하지 않는다. exporter/dashboard 구축은 배포 환경 결정 후 운영 범위다.
- 운영 DB의 read-only 점검: `SELECT month, calls FROM search_monthly_usage ORDER BY month DESC LIMIT 3;`. 카운터를 임의로 낮추거나 지워 한도를 우회하지 않는다. 월 경계는 한국 시간이 아닌 UTC다.
- 장애/비정상 사용 시 먼저 `DECISION_ASSISTANT_SEARCH_ENABLED=false`로 전환한다. API는 fail closed이고 URL 추가는 계속 가능하다. 노출된 key는 provider에서 폐기·재발급하고 secret만 교체한다.

## Staging smoke (실제 외부 호출 소량 발생)

1. allowlist 계정으로 로그인한다. Serper 동의 전 검색 버튼이 비활성인지, 미동의 API가 거부되는지 확인한다.
2. 저민감 제품명 하나를 검색한다. 결과 0~5개와 모델·세대·출처 URL을 확인하고 하나를 선택한다. 후보명이 유지되고 snippet이 검증 값으로 들어가지 않는지 확인한다.
3. Firecrawl 동의를 별도로 켜고 비교를 생성한다. 수집 상태·조회 시점·출처·부분 실패/재시도를 확인한다. AI를 사용할 때는 현재 provider 고지와 민감정보 확인을 거친다. 이어폰의 네 주관 rating은 비어 있어야 한다.
4. 두 후보의 값을 직접 보강하고 저장→평가→실제 선택→회고를 실행한다. 평가와 선택은 별개이며 새로고침 후 선택/회고가 보존돼야 한다.
5. 검색 비활성 또는 테스트용 작은 한도에 도달한 환경에서 URL fallback을 확인한다. timeout/오류는 자동 재시도하지 않는지 카운터·로그를 확인한다.

가격·재고·공식 유통·검색 결과의 동일 상품 여부는 보장하지 않는다. production provider smoke, 인증/TLS/백업 복구 확인 없이 실제 배포 완료로 표시하지 않는다.
