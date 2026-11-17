# OpenAPI 속성 순서만 달라지는 계약 drift

## 카탈로그 갱신 검증에서의 관측

2026-11-17 로컬 `npm run check`는 backend unit 245개·integration 116개를 통과한 뒤 `OpenAPI contract drift detected`로 중단됐다. [실제 차이](../evaluations/catalog-refresh-2026-11-17/contract-order-check.json)는 `/components/schemas/DecisionSummary/properties`의 마지막 두 키 순서였다. 저장된 계약은 result·phase, 생성물은 phase·result였다. 객체 키 순서를 제외한 JSON 값·구조·배열은 같았다. 같은 main commit `62a58b1`의 CI run 43847751434는 성공 상태였다.

`scripts/check-contract.mjs`의 canonical 함수는 JSON.parse 후 JSON.stringify를 사용하므로 공백은 제거하지만 객체 키 순서는 유지한다. 따라서 이 사례에서는 순서 차이도 계약 변경으로 판정한다. 생성 순서가 달라진 환경·실행 순서상의 원인은 확인하지 않았으므로 특정 운영체제나 JDK 문제로 단정하지 않는다.

진단할 때는 실제 값·타입·배열 차이를 먼저 확인하고, 내용이 같을 때 객체 키 목록의 순서를 별도로 대조한다. 비교용 객체 키 정렬은 배열 순서를 보존해야 한다. 저장된 계약을 덮어쓰거나 검사를 통과시키기 위한 무조건 재생성으로 실제 API 변경 여부를 숨기지 않는다.

## 결정 이유 PR에서의 처리

같은 날 단독 API 테스트와 전체 통합 테스트가 생성한 `DecisionSummary` schema에서도 `phase`, `result` 순서 교환을 확인했다. 두 JSON의 `git diff --no-index`에서 값·타입 변경 없이 해당 두 property의 위치만 바뀌었다.

record의 생성자 필드와 별도 getter로 제공하는 property의 순서 의존을 피하도록 `DecisionDtos.DecisionSummary`에 `JsonPropertyOrder`를 명시했다. 이는 해당 schema의 생성 순서를 안정화하는 국소 수정이며, 특정 환경이 원인이라는 입증은 아니다. API export 테스트로 OpenAPI와 frontend schema를 재생성한 뒤 계약 검사와 CI 전체 검증을 확인한다.

카탈로그 갱신 PR에서는 계약 검사나 생성 타입을 수정하지 않았다. 결정 이유 PR의 국소 수정도 전역 계약 검사를 느슨하게 만들지 않는다. 객체 키 순서 오탐을 제거하되 실제 값·타입·배열 차이를 계속 검출하도록 검사 자체를 개선하는 작업은 별도 후보이며, Issue 생성과 구현은 별도 승인을 따른다.