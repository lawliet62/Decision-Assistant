# Sony의 선택 후 가격과 HTTP client 차이

## 증상과 근거

WF-C710N 공식 페이지가 열리고 가격이 보여도 실제 Firecrawl worker는 PRICE_CONTEXT_MISSING을 반환했다. 공식 사양 인용이 있어도 Java HTTP 조회는 403이었다. [실측 보고서](../evaluations/sony-c710n-2026-11-13/README.md)에 DB·API 결과와 확인 시각을 기록했다.

## 확인된 원인

구매 페이지는 JavaScript로 렌더링되고 색상 선택 후 수량·소계가 나타난다. 최초 rawHtml과 선택 이후 HTML은 다른 상태다. `a.selected_btn`, `a.opt_list`를 클릭한 뒤 `data.actions.scrapes[0].html`을 검증해야 한다. 숨겨진 placeholder를 선택하면 구매 구성이 확인되지 않는다.

선택 후 실패는 단계 코드 PURCHASE_UNAVAILABLE로 좁혔다. 로컬 브라우저의 `/order/sheet`가 Firecrawl에서는 `https://store.sony.co.kr/order/sheet`로 반환됐다. 같은 공식 HTTPS 주소만 정규화한 뒤 실제 worker가 VERIFIED 139,000 KRW를 저장했다. 다른 host·query·HTTP는 거절한다. 이 차이를 모델 성능 문제나 품절로 해석하지 않는다.

사양은 Java HTTP에서 403, 같은 환경 Node fetch에서 200이었다. Java HTTP/1.1과 User-Agent 변경도 성공하지 않았다. 서버 차단 정책의 상세 원인은 확인하지 못했다. 개발 실행의 Node가 공개 고정 페이지를 읽고 같은 Java 오프라인 검증기가 제목·canonical·모델·인용을 대조한다. Windows JVM stdout은 UTF-8을 명시해야 한국어 JSON 근거가 깨지지 않았다.

## 조치와 상태

- 가격 actions는 고정 C710N URL에만 적용한다. 단일 snapshot·동일 URL·상태 200을 요구하고 최초 rawHtml로 대체하지 않는다.
- 사양 원문은 stdin으로만 전달한다. 반영 전에 revision·identity·근거 내용·optimistic version을 대조하며 가격과 가격 시각은 바꾸지 않는다.
- Node helper는 명시적으로 활성화한 기본 local Docker DB로 제한한다. 실제 요청 전에 7일 예약하며 실패도 예약을 소비한다.

한 차례 가격·사양·추천 API 검증은 해결됐다. 주기적 반복과 운영 이식은 미완료다. 다음 실패에서는 snapshot, 모델/옵션/소계, 공식 구매 링크, HTTP 상태를 구분한다. 제목·펌웨어 버전이 바뀌면 확인 시각을 임의로 연장하지 않는다. 코드 유지 비용이 커지면 에이전트 갱신과 비교한다.
