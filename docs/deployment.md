# MVP 배포·운영 runbook

단일 backend + Next.js + PostgreSQL 배포를 전제로 한다. 이 문서는 자동 배포나 외부 유료 구매 권한을 의미하지 않는다. 실제 배포 대상과 secret은 운영자가 주입한다.

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
| `SERPER_API_KEY` | `disabled`; secret manager로 주입, frontend/저장소/로그에 넣지 않음 |
| `DECISION_ASSISTANT_SEARCH_MONTHLY_LIMIT` | `1000`; 1~1000만 허용, 낮춰 시작 가능 |
| `DECISION_ASSISTANT_WEB_ENABLED`, `FIRECRAWL_API_KEY` | `false`, `disabled`; 별도 페이지 수집 및 기존 대안 검색 |
| `DECISION_ASSISTANT_AI_ENABLED` | 기존 기본값 `true`; AI 비용·데이터 정책 검토 전 production에서는 명시적으로 `false` |
| `DECISION_ASSISTANT_REMINDERS_ENABLED` | 기존 기본값 `false`; email 설정을 검증한 경우만 활성화 |

Serper endpoint는 `https://google.serper.dev/search`로 고정한다. 서버에서 POST, `X-API-KEY`, `q/num/gl/hl`을 사용한다. [공식 Serper](https://serper.dev/) 계정/Playground에서 key·잔여 크레딧·계약을 확인한다. 자동 충전은 켜지 않는다. 유료 구매·충전은 별도 human 결정이다.

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
