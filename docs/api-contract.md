# API contract

## 결정 목록·삭제

`GET /api/v1/decisions`는 배열 대신 `items/page/size/totalElements/totalPages`를 반환한다. frontend와 backend를 같은 release로 갱신한다. `statuses`는 복수 enum(생략 시 전체), `page`는 0부터 1,000,000까지, `size`는 1~50(기본 10), `sort`는 `UPDATED_AT/CREATED_AT/TITLE/TEMPLATE_KEY`, `direction`은 `ASC/DESC`다. 정렬은 소유자·상태로 제한한 전체 결과에 적용하며 ID 오름차순을 보조키로 쓴다. 제품군은 enum 코드 순서, 제목은 DB collation 순서다. 범위를 벗어난 페이지는 마지막 유효 페이지로 보정한다.

`GET /api/v1/decisions/counts`는 소유자별 전체 상태 건수를 0건 상태까지 반환한다. 접힌 그룹은 목록 조회를 하지 않는다.

`DELETE /api/v1/decisions/{id}?expectedVersion=N`은 CSRF·소유권·version을 검증한다. 성공은 204, stale version은 409, 타인/미존재/이미 삭제는 404다. 후보·평가·회고·작업·성향 기억의 실제 삭제 범위는 ADR 0018을 따른다. 새 Flyway V16으로 성향 기억 FK와 목록 index를 추가한다.

Backend controller와 DTO annotation이 code-first source다. 실행된 integration test가 `/api/openapi` 결과를 `backend/build/generated/openapi.json`에 저장하며, review 가능한 계약은 `docs/api/openapi.json`에 commit한다.

사용자 API는 `DECISION_ASSISTANT_SESSION` same-origin cookie 인증을 사용한다. `GET /api/v1/me`가 현재 사용자 정보와 readable `XSRF-TOKEN` cookie를 준비하며 browser는 변경 요청에 `X-XSRF-TOKEN` header를 보낸다. 인증되지 않은 API는 `401`, 유효한 사용자가 소유하지 않은 resource는 `404`다. OpenAPI endpoint는 local/test 검증에서만 노출한다.

변경 순서:

1. backend API와 integration test를 변경한다.
2. `backend/gradlew integrationTest`를 실행한다.
3. generated OpenAPI를 `docs/api/openapi.json`에 반영한다.
4. `npm --prefix frontend run api:generate`로 `frontend/shared/api/schema.d.ts`를 재생성한다.
5. `npm run check`으로 drift와 소비자 build를 확인한다.

`openapi-fetch`는 browser client의 request/response type을 제공한다. frontend가 DTO interface를 수기로 복제하지 않는다. breaking change 자동 분류와 API version 추가는 외부 소비자가 생길 때 검토한다.

## 같은 후보의 비교 조건과 직접 확인

- `DecisionResponse.comparison`은 이어폰에서 현재 settings·confirmations·후보별 price/priceOrigin/checks를 반환한다. 최초 추천의 `discovery.conditions`와 기존 `productFacts.requirementChecks`는 최초 추천 기준을 유지한다. 다른 제품군의 comparison은 null이다.
- `PUT /decisions/{id}/comparison`은 `{expectedVersion, settings: {maxBudget?, ancRequired, compatibility?, focus, deferredOptionIds}}`를 받는다. compatibility는 Android/iOS/멀티포인트 또는 null, focus는 가격/ANC 지원/호환성/배터리/무게다. 빈 예산은 제한 없음이며 양수 1억원 이하만 허용한다. 보류 ID는 현재 후보여야 한다.
- `PUT /decisions/{id}/comparison/confirmation`은 `{expectedVersion, optionId, feature, supported?, note?, sourceUrl?}`를 받는다. feature는 anc/Android/iOS/멀티포인트다. supported true/false는 직접 확인한 지원/미지원이며 null·생략은 해당 확인값 해제다. URL은 공개 URL 검증 후 참조로만 저장한다. 기록 시각은 서버가 정한다.
- 두 PUT은 소유권·CSRF·편집 가능 상태·Decision version을 확인하고 갱신된 DecisionResponse를 반환한다. 외부 호출이나 최초 추천 재실행은 없다.
- `CreateResolutionRequest.expectedVersion`은 additive optional 필드다. 새 화면은 반드시 전송하고 불일치는 409다. `ResolutionData.comparisonSnapshot`은 서버가 생성한 당시의 고정본이며 과거 기록에는 null이다.

## 제품명 lookup (additive MVP contract)

- `GET /api/v1/product-lookups/capabilities`: 검색/페이지 수집의 활성 여부, provider 이름과 전송 고지. secret은 반환하지 않는다.
- `POST /api/v1/product-lookups`: `{query, searchConsent: true}`. 인증·CSRF가 필요하며 Decision을 생성하지 않는다. 응답 `{results: [{title, url, description}]}`는 최대 5개다. 검색어·응답 목록은 서버에 보존하지 않는다.
- 기존 Decision 생성에 `selectedSources: [{url, name?}]`를 추가한다. 과거 `sourceUrls`와 동시에 제공하지 않는다. 기존 `POST /decisions/{id}/sources`도 optional `name`을 받는다. 선택한 이름은 표시용이며 상품 동일성이나 검색 snippet을 검증하지 않는다.
- `GuidanceData.webConsent`는 해당 비교의 기존 수집 동의를 화면에 설명하기 위한 값이다. Serper 검색 동의로 재사용하지 않는다. 기존 enum에 `EARBUDS`가 추가되며 기존 template 값의 의미는 바뀌지 않는다.
- 검색 장애는 ProblemDetail의 `code`와 안전한 `detail`을 반환한다. 429/502/503/504별 의미는 [운영 문서](deployment.md)를 따른다. client는 실패를 자동 재시도하지 않는다.

## 추천 용도 선택

`DiscoveryRequest.uses`는 카테고리에서 제공하는 용도 배열(최대 4개)이다. 빈 배열은 용도 미정이며 중복은 제거하고 템플릿 순서로 정규화한다. 새 화면은 `uses`만 보낸다. 기존 `use`는 deprecated 호환 필드로, `uses`가 없거나 null일 때만 사용한다. 두 필드가 함께 있으면 빈 배열을 포함해 `uses`가 우선한다. 기존 JSONB 데이터도 같은 규칙으로 조회·재시도하며 일괄 재작성이나 템플릿 버전 변경은 하지 않는다.

추천 작업의 `discovery.error`는 실패 단계에 맞는 안전한 한국어 안내를 반환한다. 검색 한도, 페이지 수집·응답 변환, AI 처리, 근거 부족, 저장 실패를 구분하되 provider 원문 오류는 반환하지 않는다. 처리 중인 `stage`는 대기·검색·수집·정리·검증·저장의 화면 안내에 사용한다. 상세 실패 code는 서버 로그에만 기록한다.

`LookupCapabilities.discoveryPolicy`는 현재 추천 정책을 알린다. `DiscoveryData.policyVersion`은 해당 실행의 정책이며 과거 데이터는 null이다. `executionId`는 한 실행에서 진행 단계가 바뀌어 version이 증가해도 유지되고 명시적 재시도마다 바뀐다. 늦게 끝난 이전 실행은 새 실행의 결과를 덮어쓰지 않는다.

공식 검색 결과가 없으면 최초·추가 탐색 모두 `SUCCEEDED` + `emptyReason=NO_OFFICIAL_RESULTS`, 수집했으나 공식 상품·비교 가능한 가격을 확인하지 못하면 `NO_VERIFIED_OFFERS`를 반환한다. `NO_NEW_PRODUCTS`는 이전 추천을 제외하여 새 후보가 없을 때 사용한다. 일부 페이지 수집이 실패했다면 `PARTIAL`을 유지한다. 자동 추가 검색은 없다.

`CandidateSuggestionData`와 `ProductFactsData`의 nullable `officialOffer`는 공식 구매 URL·판매처·KRW 가격·선택적 이미지·조회 시점·가격 기준·정책 버전을 포함한다. `sourceKind`는 `VIDEO|BLOG|OFFICIAL_STORE|WEB`이다. 공식 offer가 없는 기존 자료에는 공식 판매처 표시를 부여하지 않는다. 후보의 `recommendationGroup`은 `RECOMMENDED|UNKNOWN|BELOW|LEGACY`이며 기본 추천은 모든 최소 사양과 예산을 충족한 제품을 가격·이름 순으로 표시한다.

`POST /decisions/{decisionId}/discovery/refresh`는 `{expectedVersion}`으로 같은 비교·같은 저장 조건의 새 추천 묶음을 만든다. 소유권·CSRF·편집 가능한 상태·현재 추천 version을 검사한다. 진행 중이거나 오래된 version이면 409다. `retry`는 실패·부분 완료인 현재 묶음을 복구하고 generation을 증가시키지 않는다.

`DiscoveryData.generation`은 시도 중인 묶음(최초 0), `displayedGeneration`은 현재 보이는 후보 묶음이다. 실패·새 제품 없음(`NO_NEW_PRODUCTS`)에는 표시 중인 묶음을 변경하지 않는다. `history`는 표시 중인 묶음을 제외한 추천 묶음의 최종 상태·정책·후보를 담으며 승인·제외 상태도 보존한다. 진행/상태와 후보 저장은 executionId를 확인한 트랜잭션으로 처리한다. 추천 그룹의 예산 기준은 편집한 context가 아니라 최초 저장한 추천 조건이다.

## 최소 사양 선택

- `GET /api/v1/intake-templates`는 `intake-v2`와 `minimumRequirements` 정의를 반환한다. 항목 ID·라벨·사양 이름·비교 규칙·선택지가 서버의 source of truth다.
- 생성 요청의 `discovery.minimumRequirements`는 최대 3개의 `{항목ID: 선택지ID}` 객체다. 예: `{"memory":"16","storage":"1000"}`. 생략·null·빈 객체는 조건 없음이다. `intake-v1`은 조건 없는 기존 계약으로 생성·조회·재시도를 지원한다.
- 다른 제품군의 항목·알 수 없는 선택지·v1의 최소 조건은 422다. 빈 ID·빈 값·null 값·개수 초과는 400이다. 정규화한 선택은 템플릿 순서로 기존 JSONB에 보존한다.
- `CandidateSuggestionData`와 `ProductFactsData`에 `requirementChecks` 배열을 추가한다. 각 결과는 항목 ID·라벨·요구값, `MEETS|BELOW|UNKNOWN`, 비교에 사용한 `facts`를 포함한다. 선택하지 않으면 빈 배열이다. 이 판정은 후보 승인이나 평가를 차단하지 않는다.
- UNKNOWN 결과의 `unknownReason`은 `MISSING`, `AMBIGUOUS`, `CONFLICT`로 근거 없음·표현 모호함·상충을 구분한다. 판정 가능한 항목에서는 null이다. 자동 수집 정보의 사용자 확인 상태와 조건 충족 여부는 다른 의미다.
- `ProductFactsData`는 직접 추가한 제품도 포함해 Option별 표시 사양을 반환한다. 사용자 확인 수치가 있으면 해당 수치가 자동 추출 사양보다 우선하며 `스토리지` Criterion은 `저장공간` 표시 사양에 연결한다. 프런트 표시와 최소 조건 판정은 같은 사양을 사용한다.
