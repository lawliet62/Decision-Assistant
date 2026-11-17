# OpenAPI 필드 순서만 달라지는 계약 drift

2026-11-17 단독 API 테스트와 전체 통합 테스트에서 생성한 `DecisionSummary` schema의 `phase`, `result` 순서가 바뀌어 `check-contract.mjs`가 실패했다. 두 JSON의 `git diff --no-index`에서 값·타입 변경 없이 해당 두 property의 위치만 교환된 것을 확인했다.

계약 검사는 객체 키 순서도 포함해 비교한다. record의 생성자 필드와 별도 getter로 제공하는 property가 섞인 곳은 reflection 순서에 의존하지 않도록 `JsonPropertyOrder`를 명시한다. 이번 수정은 `DecisionDtos.DecisionSummary`에 적용한다. 전역 계약 검사를 느슨하게 만들거나 실제 계약 차이를 무시하지 않는다.

수정 후 API export 테스트로 OpenAPI를 다시 생성하고 frontend schema를 갱신한 뒤 `node scripts/check-contract.mjs`와 CI 전체 검증을 확인한다.
