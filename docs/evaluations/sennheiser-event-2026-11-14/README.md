# 젠하이저 상품 731 행사명 변경 검증

## 확인한 원인과 대응

2026-11-14 KST의 실제 조회에서 구매 URL의 HTTP 200·최종 URL·상품 ID `cafe24_bnispace_1_731`은 유지됐다. canonical 경로에 `젠사페-`, 제목·ProductGroup·색상별 이름에는 `[젠사페]`가 추가됐다. 기존 검증기는 canonical 허용 목록에서 먼저 거절하며, 그것만 확대해도 이름 검증에서 다시 거절한다. 상품 번호만 비교하거나 임의 행사명을 지우는 방식은 사용하지 않는다.

확인한 일반형과 행사형의 정확한 canonical·이름 쌍만 허용한다. Offer도 같은 canonical 경로를 사용해야 하며 다섯 색상의 SKU·재고·금액, 구매 영역의 표시 금액·버튼 대조는 유지한다. 사양도 같은 이름의 제목·ProductGroup과 단일 사양 표를 요구한다. 등록 구매 URL·identity·manifest·revision은 바꾸지 않는다.

## 실제 입력 검증

[관측 메타데이터](observations.json)는 2026-11-13T16:09:59.600Z의 결과다. 원문 HTML은 메모리에서만 처리했다. 공개 URL의 직접 HTTP와 격리 Chromium 렌더링으로 확인했으며 Firecrawl 응답을 사용한 결과는 아니다.

- 렌더링 HTML → 기존 가격 파서: `VERIFIED`, 동일 identity, 429,000 KRW.
- 직접 HTTP HTML → 사양 검증기: ANC와 이어버드 IP54 모두 일치.
- 원래 형태의 기존 테스트와 행사형·인코딩 URL, 이름/경로 혼합, 다른 상품·재고·가격·사양 불일치 회귀 테스트를 함께 실행한다.

DB 가격·사양 확인 시각을 갱신하지 않았고 유료 provider 호출은 0회다. 48시간 가격 시도와 7일 사양 예약은 유지한다. 다음 실제 worker 실행에서 Firecrawl 응답·DB 저장·추천 반영까지 확인해야 한다. 이 결과로 개발 DB의 기존 실패 상태가 해소됐다거나 주기 운영 전체가 검증됐다고 주장하지 않는다.

전체 저장소 검증인 `npm run check`를 통과했다. 단위 테스트, PostgreSQL 통합 테스트, OpenAPI 계약, lint·typecheck와 production build를 포함한다.
