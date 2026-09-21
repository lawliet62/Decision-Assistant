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
