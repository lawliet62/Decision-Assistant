# 기존 25개 제품의 기한 도래 가격 갱신

2026-11-17, main `62a58b1`의 기존 수집·검증·저장 코드로 개발 카탈로그를 재조회했다. **25회 시도 중 21회가 VERIFIED**였고, API에서도 같은 21개 제품의 가격과 확인 시각을 확인했다. Jabra 추가 보류와 활성 25개 제품 범위를 유지한다.

## 실행 범위와 비용

개발 PostgreSQL은 정지 상태여서 기존 컨테이너와 데이터를 유지해 시작했다. 25개 제품 모두 48시간 예약이 도래했고, 사양 예약은 아직 도래하지 않았다. 가격 유효기간은 72시간이다. 실행 전 실제 유효 가격은 CT06 한 개였으며, 과거 `price_status=VERIFIED` 행 수와 구분했다.

전체 서버를 원본 DB에 기동하지 않고, `CatalogProduct`만 관리하는 JPA와 기존 `CatalogRefreshWorker`를 사용한 일회성 실행기로 가격 갱신을 수행했다. `hibernate.hbm2ddl.auto=validate`를 적용했다. 원본의 Flyway version 24를 유지하고 결정 보관·삭제 worker를 실행하지 않았다. 운영 환경을 배포하거나 상시 실행을 설정한 것은 아니다.

| 항목 | 실행 결과 |
|---|---:|
| 개발 DB 월 예약 | 79 → 104 / 700 |
| 신규 가격 관측 | 25건 |
| VERIFIED / 검증 실패 | 21 / 4 |
| Firecrawl 요청 | 24회 |
| CT06 직접 HTTP | 1회 |
| 공급자 잔여 크레딧 | 994 → 970 |
| OpenAI 호출 | 0회 |
| 연속 두 번째 worker 실행의 추가 호출 | 0회 |

각 상품의 예약과 관측은 기존 worker가 외부 요청 전에 기록했다. 시도 시각을 앞당기거나 가격을 수동 입력하지 않았다. 실패도 같은 예약을 소비했다. [요약](summary.json), [worker 결과](worker-result.json), [공급자 조회 전](credit-before.json)·[후](credit-after.json)를 따른다. 앱의 UTC 월 예약과 공급자의 청구 기간·잔여 크레딧은 별도 계수다. 추가 구매·충전·유료 운영 리소스 생성은 하지 않았다.

## 실제 가격 결과

JBL TUNE BEAM 2 99,000원, Samsung 버즈4 220,200원, CT06 46,900원의 반복 갱신을 확인했다. 젠하이저의 행사명·canonical 수정은 이번 새 Firecrawl 응답에서도 통과해 **429,000원 VERIFIED**로 저장됐다. 과거 HTML 단독 검증과 실제 provider 응답·DB 반영을 구분한다.

직전 11월 14일 단면의 유효 가격 23개와 비교하면 이번 재조회 후에는 21개로 줄었다. 젠하이저 하나가 회복됐지만 Sony와 Marshall 두 제품이 새로 검증을 통과하지 못했다. 중단 후 유효 가격이 1→21개가 된 것과 직전 회차 대비 커버리지 감소를 구분한다. 실제 가격 커버리지가 전반적으로 개선됐다고 주장하지 않는다. 이번 가격 경로는 LLM을 사용하지 않았으므로 이 실패를 저가 모델의 성능 한계로 설명할 근거도 없다.

| 제품 | 결과 | 확인된 범위 |
|---|---|---|
| Sony WF-C710N 글래스 블루 | PRICE_CONTEXT_MISSING | 내부 거절 단계 OPTION_LIST_CHANGED. 개수·라벨·중복 중 어느 차이인지는 당시 로그에 없음 |
| Marshall MINOR IV 블랙 | UNAVAILABLE | 판매 가능성 검증을 통과하지 못함 |
| Marshall MOTIF II A.N.C. | PRICE_CONTEXT_MISSING | 가격 문맥 검증을 통과하지 못함. 세부 원인은 미확인 |
| QCY HT10 | PRODUCT_MISSING | 등록 상품을 찾지 못함 |

실패한 네 제품은 가격 추천 근거에서 제외됐다. 이 결과만으로 단종이나 실제 품절을 모두 확정하지 않는다. 별도 Sony 브라우저 진단 한 회도 선택 버튼 대기에서 종료됐으며, 가격 검증 성공이나 Firecrawl 응답의 세부 원인으로 해석하지 않는다. Firecrawl을 즉시 재호출하거나 직접 수집을 Sony의 runtime 경로로 추가하지 않았다.

Sony 검증기에는 다음 정상 예약에서 `OPTION_LIST_CHANGED`가 발생할 경우 옵션 개수·고유 개수·기존 네 라벨과의 일치 개수만 남기는 로그를 추가했다. 원문·임의 라벨을 저장하지 않고 기존 네 색상 완전 일치·선택 SKU·수량·가격·구매 영역 검증은 유지한다. 새 진단 로그가 실제 외부 응답에서 동작하는지는 다음 시도에서 확인해야 한다.

## 데이터 보존과 API 검증

[실행 전](before.json)·[후](after.json)는 공개 카탈로그 metadata의 PostgreSQL JSON이다. 숫자형 Instant를 부동소수점으로 재직렬화하지 않았다. 사양 예약·revision·확인 시각의 실제 Instant는 보존됐다. JBL facts의 숫자 소수점 자릿수와 Sony fact_reviews의 ISO 문자열·숫자 표현만 달라져 원문 해시·문자열 비교는 달랐다. JBL 정규화 후 해시가 이전 값과 일치하고, 확인 시각은 원래 숫자 문자열과 BigInt 나노초로 대조했다. [보존 검증](invariants.json)을 따른다. 확인하지 않은 사양의 시각을 연장하지 않았다.

원본 결정 행의 비공개 digest는 실행 전후 일치했고 digest 자체는 보고서에 저장하지 않았다. 원본 schema version도 24로 유지했다. 카탈로그의 공개 행만 별도 일회성 PostgreSQL에 복사해 최신 backend API를 실행했다. 원본 결정·사용자 데이터는 복사하지 않았다.

검증 후 원본 DB의 월 예약 104회와 결정 데이터 보존을 다시 확인했다. 다른 연결이 없어 기존 PostgreSQL을 최초의 정지 상태로 복원했다. 비공개 digest와 일회성 실행기 source·인자 파일은 제거했다.

60만원·추가 필수 조건 없음·외부 동의 false로 실제 API를 호출하고 추가 탐색을 수행했다. 후보 25개 중 **RECOMMENDED 21개**, 나머지 4개는 UNKNOWN이었다. 21개 모두 실제 갱신 DB와 같은 가격·확인 시각을 반환했다. [API 결과](api-result.json)를 따른다. 초기 검증 스크립트의 discovery 버전 및 DELETE expectedVersion 전달 오류는 올바른 API 계약으로 수정해 재검증했다. 외부 수집은 추가하지 않았다. 임시 결정은 DELETE 204로 휴지통 처리했고, 검증 backend 종료 후 일회성 DB 컨테이너도 제거했다.

## 주간 사양 출처 점검

[11월 16일 예약 실행](https://github.com/lawliet62/Decision-Assistant/actions/runs/43859149400)을 확인했다. 38개 항목 중 35개는 QUOTE_PRESENT였고, HT10 HTTP 404 한 항목·Sony HTTP 403 두 항목 때문에 실패 상태였다. [관측 요약](source-check.json)을 따른다. 문구 존재는 사양 검증이 아니며 이 workflow는 DB 확인 시각을 갱신하지 않는다. 예약 설정은 월요일 03:17 UTC이고 이 실행의 생성 시각은 11:35:55 UTC였다. 두 시각의 차이를 기록하되 지연 원인을 단정하지 않는다.

## 검증과 다음 단계

- 현재 Sony 검증기 기존 unit test 3개 통과. 수용 조건은 유지했다.
- 로컬 전체 검사에서 backend unit 245개·PostgreSQL integration 116개, frontend lint·typecheck가 통과했다. `npm run check`는 OpenAPI 계약 단계에서 중단됐다. `DecisionSummary.properties`의 phase·result 순서만 다르고 JSON 값·구조는 같았다. 같은 기준 main의 [CI](https://github.com/lawliet62/Decision-Assistant/actions/runs/43847751434)는 성공 상태다. 계약·생성 타입·검사 코드는 변경하지 않았으며 [차이 관측](contract-order-check.json)과 [별도 진단](../../troubleshooting/openapi-property-order.md)을 남겼다. frontend production build는 별도로 실행해 통과했다.
- worker 재실행의 추가 호출 0, 관측 증가 25, 예약 증가 25와 가격·시각·사양 보존을 대조했다.
- 격리 API에서 25개 후보·21개 추천과 실제 가격·시각 일치를 확인했다. 이 검증은 추가 필수 조건을 설정한 모든 조합을 평가한 것은 아니다.
- 다음 가격 예약은 **2026-11-19 18:43:08 KST 이후**, 첫 사양 예약은 **11월 20일 20:45:12 KST 이후**다. 지금 추가 가격·사양 조회를 실행하지 않는다.

원문 HTML·비밀·원본 사용자 데이터는 보존하지 않았다. 네 실패의 추가 진단과 실제 기한 도래 사양 갱신은 남아 있어 전체 Goal을 완료로 처리하지 않는다. 실행 중단 기간에도 일정대로 갱신되는 상시 운영을 입증한 것은 아니다. 재사용할 진단은 [운영 검증 troubleshooting](../../troubleshooting/catalog-refresh-invariants.md)에 정리한다.
