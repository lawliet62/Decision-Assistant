# MVP 배포·운영 runbook

단일 backend + Next.js + PostgreSQL 배포를 전제로 한다. 이 문서는 자동 배포나 외부 유료 구매 권한을 의미하지 않는다. 실제 배포 대상은 아직 미정이다. Secret은 GCP Secret Manager에서 backend가 직접 조회하며 리소스·IAM·운영 Identity 준비는 운영자가 수행한다.

## 출시 전 확인

1. human이 PR을 검토·merge한다. `npm run check`와 `npm --prefix frontend run test:e2e`가 통과해야 한다.
2. production은 `oidc` profile, Auth0 client/issuer, exact-email allowlist와 public HTTPS origin을 구성한다. `local` profile을 외부로 공개하지 않는다. 세션 cookie Secure/HttpOnly, CSRF, same-origin `/api` proxy를 유지한다.
3. PostgreSQL 백업을 만들고 별도 DB에서 restore와 Flyway migration을 검증한다. V7은 `search_monthly_usage(month, calls)`만 추가한다. 기존 결정/평가 데이터 변환은 없다. EARBUDS 데이터가 생긴 뒤 이를 모르는 구버전으로 되돌리지 않는다. 장애 시 기능 flag를 끄는 forward recovery를 우선한다.
4. 개인정보·provider 전송 고지, 보존/삭제 정책, TLS/host 설정, DB backup/restore 책임자를 확정한다. 이 정책 승인과 실제 infrastructure 검증은 코드 테스트로 대체할 수 없다.
5. 실제 key가 있는 staging에서 아래 smoke를 실행한다. mock 테스트는 provider의 실제 가용성·검색 품질을 보장하지 않는다.

## 클릭 추천 운영 확인

검색·수집·AI 설정이 모두 활성화되어야 클릭 추천을 시작한다. 하나라도 비활성이면 직접 제품명·URL 추가를 제공한다. 새로운 secret/provider/배포 단위는 필요하지 않다.

추천 지연은 `discovery_queue_timing`, `discovery_stage_timing`, `discovery_page_timing`, `discovery_total_timing`의 `duration_ms`로 구분한다. `discovery_fact_validation`의 proposed/accepted/rejected 개수는 사양 추출 누락과 검증 탈락을 구별한다. 각 페이지의 URL·본문은 로그에 남기지 않는다. 추천 전용 scheduler는 1건씩, 수집기는 최대 2개씩 처리하며 병렬 완료 순서가 후보 순서를 바꾸지 않는다.

- V8은 추천 작업·표시용 사양과 출처 metadata를 추가하고 새 추천의 중복 제약을 분리한다. 기존 결정·평가 snapshot은 변환하지 않는다. 운영 적용 전 백업·복원 검증을 수행한다.
- 실제 Dev smoke: 5개 제품군에서 용도·예산을 선택하고 동의 후 추천 → 2개 승인 → 주요 사양·출처·조회 시점 확인 → 평가 없이 선택 기록을 수행한다. 수집 실패 시 확보된 결과·직접 추가·수동 재시도를 확인한다.
- 기본 추천 1회는 Serper 1회, 공개 페이지 최대 5개, AI 구조화 최대 1회다. 공식 문서 보완에 별도 동의한 v3 요청은 최대 검색 3·수집 7·AI 1이며 최대 2모델에 각각 검색·수집을 한 번 추가한다. 기존 Serper 월·burst 한도를 공유하고 보완 한도 오류는 추가 요청을 중단한다. 보완 실패에도 확보한 후보를 PARTIAL로 보존한다. 추천용 SDK와 구조화 출력의 자동 재시도는 비활성화한다.
- 추천 검색은 제품명 lookup과 월 1,000회 및 burst 제한을 공유한다. AI/Firecrawl 전체 계정 비용 상한을 대체하지 않는다.
- `product_event=discovery_finished/discovery_failed/discovery_page_failed`와 `product.lookup.requests`를 관측한다. 실패 로그의 `decision_id`, `stage`, `code`, `reason`, `root_cause`, `http_status`로 단계를 찾는다. `reason`과 `root_cause`는 예외 종류이며 메시지·stack trace·URL·provider 원문 응답은 기록하지 않는다. 추천 행에 provider/model, prompt/schema version, token/latency를 보존한다. 조건 원문·구매 메모·Secret을 로그에 추가하지 않는다.
- 실패나 부분 완료는 사용자만 재시도한다. 재시작으로 RUNNING에 남은 작업은 15분 경과 후 다음 worker 점검에서 실패로 전환되며 자동 외부 재호출은 하지 않는다.
- 기능을 끌 때는 기존 검색·수집·AI flag를 사용한다. 새 migration을 되돌리는 방식보다 forward recovery를 우선한다. 기능 비활성화는 이미 저장한 비교·사양 열람을 막지 않는다.

추천 실패 시 아래 순서로 확인한다. 여러 페이지가 모두 실패하면 화면은 첫 페이지의 실패 종류를 안내하며, 개별 원인은 `discovery_page_failed` 로그에서 확인한다.

| `stage` / 대표 `code` | 확인 사항 |
| --- | --- |
| `CONFIGURATION` / `CONFIGURATION_UNAVAILABLE` | 검색·수집·AI 기능 설정과 저장된 조건의 유효성 |
| `SEARCH` / `SEARCH_MONTHLY_LIMIT`, `SEARCH_BUSY`, `SEARCH_TIMEOUT`, `SEARCH_NO_RESULTS`, `SEARCH_FAILED` | 월 한도·burst·provider 상태·검색 결과 유무 |
| `COLLECTION` / `COLLECTION_RESPONSE_INVALID` | HTTP 성공 후 JSON 변환·응답 구조 오류. Boot가 사용하는 Jackson 타입과 adapter의 응답 타입이 일치하는지 확인 |
| `COLLECTION` / `COLLECTION_TIMEOUT`, `COLLECTION_EMPTY`, `COLLECTION_FAILED` | 수집 지연·빈 본문·provider HTTP 상태. 일부 페이지 성공은 부분 추천으로 이어짐 |
| `AI` / `AI_TIMEOUT`, `AI_RESPONSE_INVALID`, `AI_FAILED` | AI 호출 지연·구조화 응답 변환·provider 실패 |
| `VALIDATION` / `EVIDENCE_INSUFFICIENT` | 수집 원문에서 제품명·출처·인용 근거를 확인할 수 있는지 점검 |
| `PERSISTENCE` / `PERSISTENCE_FAILED` | DB 연결·트랜잭션 오류. 실패 상태 저장도 불가능하면 로그를 먼저 확인하고 기존 중단 작업 복구 규칙을 적용 |

`ProviderResponseConversionTest`는 로컬 HTTP fixture와 Boot의 실제 `RestClient` JSON converter로 Firecrawl·Resend 응답을 검사한다. 실제 provider 가용성은 별도 smoke로 확인한다. 수정 배포 후 과거 FAILED 작업은 자동 재실행되지 않으며 사용자가 명시적으로 다시 시도한다.

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
