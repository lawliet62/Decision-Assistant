# API contract

Backend controller와 DTO annotation이 code-first source다. 실행된 integration test가 `/api/openapi` 결과를 `backend/build/generated/openapi.json`에 저장하며, review 가능한 계약은 `docs/api/openapi.json`에 commit한다.

사용자 API는 `DECISION_ASSISTANT_SESSION` same-origin cookie 인증을 사용한다. `GET /api/v1/me`가 현재 사용자 정보와 readable `XSRF-TOKEN` cookie를 준비하며 browser는 변경 요청에 `X-XSRF-TOKEN` header를 보낸다. 인증되지 않은 API는 `401`, 유효한 사용자가 소유하지 않은 resource는 `404`다. OpenAPI endpoint는 local/test 검증에서만 노출한다.

변경 순서:

1. backend API와 integration test를 변경한다.
2. `backend/gradlew integrationTest`를 실행한다.
3. generated OpenAPI를 `docs/api/openapi.json`에 반영한다.
4. `npm --prefix frontend run api:generate`로 `frontend/shared/api/schema.d.ts`를 재생성한다.
5. `npm run check`으로 drift와 소비자 build를 확인한다.

`openapi-fetch`는 browser client의 request/response type을 제공한다. frontend가 DTO interface를 수기로 복제하지 않는다. breaking change 자동 분류와 API version 추가는 외부 소비자가 생길 때 검토한다.

## 제품명 lookup (additive MVP contract)

- `GET /api/v1/product-lookups/capabilities`: 검색/페이지 수집의 활성 여부, provider 이름과 전송 고지. secret은 반환하지 않는다.
- `POST /api/v1/product-lookups`: `{query, searchConsent: true}`. 인증·CSRF가 필요하며 Decision을 생성하지 않는다. 응답 `{results: [{title, url, description}]}`는 최대 5개다. 검색어·응답 목록은 서버에 보존하지 않는다.
- 기존 Decision 생성에 `selectedSources: [{url, name?}]`를 추가한다. 과거 `sourceUrls`와 동시에 제공하지 않는다. 기존 `POST /decisions/{id}/sources`도 optional `name`을 받는다. 선택한 이름은 표시용이며 상품 동일성이나 검색 snippet을 검증하지 않는다.
- `GuidanceData.webConsent`는 해당 비교의 기존 수집 동의를 화면에 설명하기 위한 값이다. Serper 검색 동의로 재사용하지 않는다. 기존 enum에 `EARBUDS`가 추가되며 기존 template 값의 의미는 바뀌지 않는다.
- 검색 장애는 ProblemDetail의 `code`와 안전한 `detail`을 반환한다. 429/502/503/504별 의미는 [운영 문서](deployment.md)를 따른다. client는 실패를 자동 재시도하지 않는다.

## 추천 용도 선택

`DiscoveryRequest.uses`는 카테고리에서 제공하는 용도 배열(최대 4개)이다. 빈 배열은 용도 미정이며 중복은 제거하고 템플릿 순서로 정규화한다. 새 화면은 `uses`만 보낸다. 기존 `use`는 deprecated 호환 필드로, `uses`가 없거나 null일 때만 사용한다. 두 필드가 함께 있으면 빈 배열을 포함해 `uses`가 우선한다. 기존 JSONB 데이터도 같은 규칙으로 조회·재시도하며 일괄 재작성이나 템플릿 버전 변경은 하지 않는다.

추천 작업의 기존 `discovery.error`는 실패 단계에 맞는 안전한 한국어 안내를 반환한다. 검색 한도·결과 없음, 페이지 수집·응답 변환, AI 처리, 근거 부족, 저장 실패를 구분하되 provider 원문 오류는 반환하지 않는다. 화면은 이 문자열을 표시하고 분기에는 기존 `status`를 사용한다. 진단용 `stage`·`code`는 서버 로그에만 기록하며 API 필드나 상태 enum을 추가하지 않는다.

## 최소 사양 선택

- `GET /api/v1/intake-templates`는 `intake-v2`와 `minimumRequirements` 정의를 반환한다. 항목 ID·라벨·사양 이름·비교 규칙·선택지가 서버의 source of truth다.
- 생성 요청의 `discovery.minimumRequirements`는 최대 3개의 `{항목ID: 선택지ID}` 객체다. 예: `{"memory":"16","storage":"1000"}`. 생략·null·빈 객체는 조건 없음이다. `intake-v1`은 조건 없는 기존 계약으로 생성·조회·재시도를 지원한다.
- 다른 제품군의 항목·알 수 없는 선택지·v1의 최소 조건은 422다. 빈 ID·빈 값·null 값·개수 초과는 400이다. 정규화한 선택은 템플릿 순서로 기존 JSONB에 보존한다.
- `CandidateSuggestionData`와 `ProductFactsData`에 `requirementChecks` 배열을 추가한다. 각 결과는 항목 ID·라벨·요구값, `MEETS|BELOW|UNKNOWN`, 비교에 사용한 `facts`를 포함한다. 선택하지 않으면 빈 배열이다. 이 판정은 후보 승인이나 평가를 차단하지 않는다.
- UNKNOWN 결과의 `unknownReason`은 `MISSING`, `AMBIGUOUS`, `CONFLICT`로 근거 없음·표현 모호함·상충을 구분한다. 판정 가능한 항목에서는 null이다. 자동 수집 정보의 사용자 확인 상태와 조건 충족 여부는 다른 의미다.
- `ProductFactsData`는 직접 추가한 제품도 포함해 Option별 표시 사양을 반환한다. 사용자 확인 수치가 있으면 해당 수치가 자동 추출 사양보다 우선하며 `스토리지` Criterion은 `저장공간` 표시 사양에 연결한다. 프런트 표시와 최소 조건 판정은 같은 사양을 사용한다.
