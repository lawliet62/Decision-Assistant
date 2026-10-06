# 공식 문서 보완 출처 검토

## 범위와 방법

고정 빌드의 실제 provider 실행과, 구현 중 공개 HTML을 직접 읽은 parser 진단을 구분한다. 실제 실행은 [results.jsonl](results.jsonl)의 응답·짧은 인용과 [events.jsonl](events.jsonl)의 거절/실패 사유로 검토한다. 수집 당시 전체 HTML은 보관하지 않아 실패한 문서의 본문을 사후 재구성하지 않는다.

직접 진단은 Python 표준 HTTP/HTML parser 2회와 Java HttpClient 3회, 총 공개 HTTP 5회였다. 웹 도구의 공식 페이지 확인은 이 수와 별도이며 Serper·Firecrawl·Gemini 사용량에 섞지 않는다. 같은 문서를 진단 중 재조회했으며 이는 15조건의 성공 사례 재실행이 아니다.

## 공식 QCY 문서의 실제 구조

[QCY T13 ANC 제품 문서](https://www.qcy.com/products/qcy-t13-anc)는 상품 제목 외 본문에 다른 모델 카드도 포함한다. 전체 본문을 하나의 제품 사양으로 취급하지 않고 `.specification-section`의 `Product Name`을 구매 모델과 다시 대조한다. `.spec-details`에는 줄바꿈으로 구분한 호환 플랫폼과 ANC 항목이 있다.

최종 직접 진단은 2026-10-06 01:50:20 UTC에 `ACCEPTED`로 끝났다. [source-check.json](source-check.json)에 짧은 사양·모델 인용, 실제 URL, 조회 시각과 근거 영역 hash를 보존했다. 앱 최소 OS 대신 제품 자체의 `Compatible system`을 읽었고 ENC·바람 소음 억제를 ANC 근거로 바꾸지 않았다. 중간 진단에서는 다른 상품의 하위 제목까지 포함해 `MODEL_AMBIGUOUS`가 발생했으며, 모델명을 다시 확인한 사양 영역으로 좁힌 뒤 통과했다.

[공식 지원 링크](https://www.qcy.com/pages/support-center-t13-anc)는 같은 slug의 [지원 호스트](https://support.qcy.com/support-center-t13-anc)로 이동한다. 앱 OS 지원과 일반 FAQ는 모델별 기본 호환성의 근거로 삼지 않는다. [QCY KOREA HT16 안내](https://www.qcykorea.com/default/product/product01.php?com_board_basic=read_form&com_board_id=10&com_board_idx=8&sub=01)도 등록 범위이지만 이미지 내용이나 HT16/MeloBuds N50 별칭을 자동 연결하지 않는다.

이 진단은 과거에 가격을 확인한 T13 ANC 구매 모델명을 연결 입력으로 사용했다. 현재 판매가를 다시 검증한 서비스 실행이 아니며 실제 검색 결과에서 이 문서에 도달하거나 조건 확인 후보가 증가했다는 증거로 세지 않는다.

## 실제 이어폰 3조건의 응답 검토

반환한 가격 후보는 조건별 3·4·1개다. T13 ANC·HT16·HT19가 첫 두 조건에 반복되고, 두 번째 조건의 T13PRO ANC와 세 번째 조건의 T17APP가 추가되어 조건 간 중복을 제외하면 모델명은 5개다. 각 후보의 구매 영역 가격 인용과 반환 금액은 일치했고, 예산이 있는 조건의 반환 가격은 그 예산 이내였다. 이는 수집 당시 응답 검토이며 별도 실시간 가격 재조회는 하지 않았다.

실제 보완 검색 5회 중 3회는 등록된 공식 문서 결과를 확보하지 못했다. 수집한 문서 한 건은 `CANONICAL_URL_MISMATCH`로 제외했고, 다른 한 건은 Firecrawl의 502 응답으로 실패했다. 이 502는 두 번째 조건을 PARTIAL로 만들었지만 확보한 가격 후보 4개는 유지했다. 실패한 문서의 HTML·URL을 저장하지 않았으므로 canonical 불일치의 구체적인 원인을 별칭이나 다른 모델이라고 단정하지 않는다.

최종 후보의 사실은 가격뿐이며 사양·호환성은 UNKNOWN이다. 일부 AI 가격 인용이 `QUOTE_MISMATCH`로 거절되어도 서버가 구매 영역과 대조한 공식 offer 가격은 따로 보존된다. 최종 설명은 가격·예산·미확인 사항과 실제 통화 품질·착용감의 검증 한계를 안내한다. 사양과 용도를 연결할 근거가 없어 8개 설명에 각각 1점을 부여했다. 잘못된 MEETS나 근거 없는 성능 주장은 응답에서 발견하지 못했으며, 충족 판정이 없어 정확도 검증의 표본은 부족하다.
