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
