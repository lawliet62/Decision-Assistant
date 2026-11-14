# Jabra 가격 갱신의 실제 Firecrawl 재검증

2026-11-14 20:06 KST, PR #41 초안을 최신 main에 맞춘 뒤 Jabra Evolve2 Buds 상품 2162의 USB-A·UC·무선 충전 패드 미포함 구성을 실제 Firecrawl과 격리 PostgreSQL에서 검증했다. 결과는 `PRICE_CONTEXT_MISSING`이며 가격은 저장되지 않았다. 실측 뒤 Jabra 추가를 보류하고 기존 25개 제품 갱신에 집중하기로 결정했다. 개발 DB에 제품을 추가하지 않으며 등록·runtime 지원 코드는 이번 병합 대상에서 제외한다.

## 실행과 비용

- 11월 12일의 다음 조회 가능 시각인 11월 14일 14:43 KST 이후에 실행했다.
- 외부 호출 전에 개발 DB의 월 예약을 78회에서 79회로 늘렸다. 한도는 700회다. [예약](reservation.json)을 남겼다.
- 첫 기동은 로컬 DB 평문 입력 검사에서 중단됐다. 외부 요청과 격리 DB migration이 시작되기 전이므로 검증용 DB 설정을 맞춘 뒤 동일한 예약으로 진행했다. 제품 수집을 재호출한 것은 아니다.
- 격리 DB에서 Jabra만 활성화하고 실제 `CatalogRefreshWorker.refreshNext()`를 연속 두 번 실행했다. Firecrawl 요청·관측·월 예약은 각각 한 번이었다.
- 공급자 잔여 크레딧은 995에서 [994](credit-after.json)로 감소했다. [Credit Usage API](https://docs.firecrawl.dev/api-reference/endpoint/credit-usage)로 확인했다. 추가 구매·충전·OpenAI 호출은 없었다.
- 개발 DB의 기존 25개 제품과 가격·사양 시각·제품별 예약은 변경하지 않았다. 개발 DB에는 전역 호출 예약 1회만 추가했다.

## 관측과 판단

[진단](diagnostic.json)의 `PURCHASE_AREA`는 검증기의 첫 거절 단계다. 입력 크기·canonical·ProductGroup·구성·정가·할인가·할인 기간·옵션 검사를 통과했지만 구매 영역 검사는 통과하지 못했다. `#actionBuy`는 두 개였다. 어느 하위 조건이 실패했는지, 수집기가 어떤 속성을 변경했는지까지는 계측하지 않았으므로 확정하지 않는다.

재고 검사에서 요구하는 `var option_stock_data = '`와 `var product_sale_price` 표식도 응답에 없었다. 재고 단계는 앞선 거절 때문에 실행되지 않았으며, 구매 영역 조건만 수정하면 최종 검증까지 통과한다고 볼 수 없다. 모든 script가 제거됐다고 단정하지 않는다. JSON-LD script 한 개는 존재했다.

직전 직접 HTTP 진단에서는 같은 고정 URL의 HTML을 동일 검증기에 넣었을 때 `VERIFIED`, 359,100원이었다. 이번 Firecrawl 응답에서는 위와 같이 거절됐다. 직접 HTTP 성공이나 합성 fixture 통과로 실제 provider 응답의 적합성을 대신 판정하면 안 된다.

[실제 DB 결과](worker-result.json)는 가격·통화·offer가 모두 NULL이며 실패 상태만 저장됐음을 보여준다. 다음 시도 예약은 **2026-11-16 20:06:38 KST**다. 이는 최소 재시도 경계이며, 같은 구조의 실패를 확인하기 위한 반복 호출 일정으로 삼지 않는다. 수집 응답에서 필요한 구매·재고 근거를 확보할 방안을 검토한 뒤 다시 실행한다.

## 보류 결정과 최종 적용 범위

실측을 위해 최신 main의 Sony·JBL·Samsung 검증, Apple iOS 근거와 브랜드 필드를 보존하면서 충돌을 해결했고 Jabra에는 `Jabra`·`KNOWN`을 명시했다. 실측 후 보류 결정에 따라 최종 diff에서 Jabra 등록·runtime·전용 테스트·출처 허용 목록 추가를 제거했다. 기존 초안 구현은 Git 이력 `bf16248`에 보존된다. 과거 브랜드 migration 테스트는 V17 도입 당시의 27개 제품 fixture로 고정했다. 신규 브랜드를 현재 manifest에서 읽어 V16 DB에 소급 삽입하면 당시 존재하지 않던 제품 때문에 과거 migration 검증이 실패하기 때문이다. 기존 데이터 보존·제약 검증은 유지한다.

가격을 추정하거나 구매·재고 검증을 생략하지 않는다. 현재 구현으로 Firecrawl 자동 갱신이 성공했다고 안내하지 않는다. 기존 직접 HTTP 예외는 CT06으로 제한되어 있으므로 Jabra runtime까지 확대하려면 ADR 0016의 수집 경계와 비용·유지보수 부담을 검토해 명시적으로 결정해야 한다. 읽기 전용 직접 HTTP 진단과 상시 갱신 provider 추가는 별개다.

25개 제품·10개 브랜드는 기존 개발 카탈로그에서 유지된다. 현재 Jabra 한 제품의 등록을 위해 에이전트 기반 상시 수집을 도입할 근거는 부족하다. 유지보수 비용이 커질 때에는 정적 근거 수집에 한정한 에이전트 보조를 재검토하되, 가격 검증 경계를 대신하게 하지 않는다.

원문 HTML과 비밀값은 보고서에 저장하지 않았다. 실패 단계와 개수, 공개 URL, DB의 해당 제품 관측과 크레딧 metadata만 보존한다. 격리 backend는 종료했고 검증용 PostgreSQL container는 정지했다. 개발 PostgreSQL은 기존 상태로 유지한다.

Jabra를 다시 추가하려면 근거 보존 방안과 실제 provider 검증부터 재검토한다. 11월 16일의 최소 재시도 경계가 지났다는 이유만으로 Jabra 수집을 재개하지 않는다. 기존 제품의 다음 가격 갱신 대상은 Sony WF-C710N이며 개발 DB 예약은 **2026-11-15 20:11:57 KST**다.

## 검증

- 실측용 초안에서 `npm run check` 통과: scripts·backend unit/integration·frontend lint/typecheck/build·API contract 검증.
- Jabra runtime·등록을 제거한 최종 범위에서 backend unit과 `CatalogBrandMigrationIntegrationTest`를 다시 실행해 통과했다. 신규 제품 때문에 V18 제약 검증이 실패하던 문제는 과거 27개 제품 fixture 고정으로 해결했고 기존 migration은 수정하지 않았다.
- 실측 JSON 간 요청 1회·관측 1건·가격 미저장·48시간 예약·크레딧 1 감소의 일관성과 최종 manifest의 활성 25개·Jabra 미등록을 대조했다.
