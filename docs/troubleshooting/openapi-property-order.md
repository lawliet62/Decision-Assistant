# JSON 속성 순서만 달라 OpenAPI 계약 검사가 실패한 경우

2026-11-17 로컬 `npm run check`는 backend unit 245개·integration 116개를 통과한 뒤 `OpenAPI contract drift detected`로 중단됐다. [실제 차이](../evaluations/catalog-refresh-2026-11-17/contract-order-check.json)는 `/components/schemas/DecisionSummary/properties`의 마지막 두 키 순서였다. 저장된 계약은 result·phase, 생성물은 phase·result였다. 객체 키 순서를 제외한 JSON 값·구조·배열은 같았다. 같은 main commit `62a58b1`의 CI run 43847751434는 성공 상태였다.

`scripts/check-contract.mjs`의 canonical 함수는 JSON.parse 후 JSON.stringify를 사용하므로 공백은 제거하지만 객체 키 순서는 유지한다. 따라서 이 사례에서는 순서 차이도 계약 변경으로 판정한다. 생성 순서가 달라진 환경·실행 순서상의 원인은 확인하지 않았으므로 특정 운영체제나 JDK 문제로 단정하지 않는다.

진단할 때는 실제 값·타입·배열 차이를 먼저 확인하고, 내용이 같을 때 객체 키 목록의 순서를 별도로 대조한다. 비교용 객체 키 정렬은 배열 순서를 보존해야 한다. 저장된 계약을 덮어쓰거나 검사를 통과시키기 위한 무조건 재생성으로 실제 API 변경 여부를 숨기지 않는다.

이번 가격 갱신 PR에서는 계약 검사나 생성 타입을 수정하지 않았다. 객체 키 순서 오탐을 제거하되 실제 값·타입·배열 차이를 계속 검출하는 검사는 별도 개선 후보로 남긴다. GitHub Issue 생성이나 구현은 별도로 승인해야 한다.
