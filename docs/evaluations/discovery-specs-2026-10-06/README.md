# 공식 문서 사양 보완 15조건 평가 — 2026-10-06

**별도 동의·호출 제한·동일 모델 공식 문서 파싱은 구현했지만, 이번 실제 평가에서 조건 확인 후보와 설명 품질의 개선은 확인하지 못했다.** 8개 가격 후보가 반환됐고 모두 UNKNOWN이다. 조건을 확인한 서로 다른 후보 2개 이상은 **0/15**, 보완 사양 채택은 **0건**, 설명 평균은 **1.0/3점**이다.

## 실행과 비교 기준

[ADR 0015](../../decisions/0015-official-specification-supplement.md)의 승인 범위로 기존 고정 15조건을 각 1회 실행했다. 코드 `5b62099`, JAR `5d3c5d94d8cf7bf530ddb9c66854213a54b1ba9d392c4b71a458c4c4a6effd99`, 시작 시 작업 diff는 없었다. `gemini/gemini-3.5-flash-lite`와 A안 최초 검색을 유지하고 `officialEvidenceConsent=true`를 저장했다. 조건 생성 간격은 95초, 전체 상한은 검색 45·수집 105·AI 15회다. 시작 2026-10-06T01:56:50.839Z, 종료 2026-10-06T02:19:23.872Z; 중단·재실행·미실행은 없다.

직전 [상품 연결 복구 평가](../discovery-linking-2026-10-01/README.md)와 입력은 같지만 실행 날짜·검색 결과가 다르다. 전체 후보·시간·토큰 차이를 코드 효과로 단정하지 않는다. 구매 후보가 없는 조건에는 보완 검색을 하지 않으며 초기 등록은 실제 구조를 확인한 QCY다.

## 1. 비교 후보 확보

| 항목 | 직전 v2 | 이번 v3 |
| --- | ---: | ---: |
| 후보가 있는 조건 | 3/15 | 3/15 |
| 가격 후보 2개 이상인 조건 | 2/15 | 2/15 |
| 반환 가격 후보 | 7 | 8 |
| 조건 확인 후보 2개 이상 | 0/15 | 0/15 |
| 공식 문서로 보완한 사양 | 해당 없음 | 0 |
| 설명 평균 | 1.0/3 | 1.0/3 |

후보 수는 각 조건의 반환 수를 합한 값이며 같은 모델의 조건 간 반복을 포함한다. 한 조건 안에서는 색상 중복을 별도 비교 모델로 세지 않았다. 앱의 RECOMMENDED와 출처 기준 검토 모두 조건 확인 후보가 없었다. [후보별 agent 평가](assessment.json)와 [사용자용 빈 평가표](human-review.md)를 구분한다.

## 2. 오판과 근거 없는 주장

반환한 8개 후보는 가격 외 사양을 확정하지 않았다. 잘못된 MEETS와 근거 없는 성능·사용감 주장은 응답 검토에서 발견하지 못했다. 미확인을 숨기거나 상품명의 ANC·Bluetooth·앱 OS를 근거로 승격하지 않았다. **충족 판정 표본이 없으므로 정확도 100%라는 의미는 아니다.** 가격·인용은 수집 시점의 응답 기준이며 현재 재고·배송비·최저가를 보장하지 않는다.

## 3. 탈락 단계와 보완 경로

| 조건 | 검색 반환 / 공식 URL | 최초 수집 / 가격 검증 | 반환 후보 / 기본 추천 | 상태·빈 결과 |
| --- | ---: | ---: | ---: | --- |
| earbuds-1 | 5 / 5 | 5 / 5 | 3 / 0 | SUCCEEDED |
| earbuds-2 | 5 / 5 | 5 / 5 | 4 / 0 | PARTIAL |
| earbuds-3 | 5 / 5 | 5 / 1 | 1 / 0 | SUCCEEDED |
| laptop-1 | 5 / 4 | 4 / 0 | 0 / 0 | SUCCEEDED · NO_VERIFIED_OFFERS |
| laptop-2 | 5 / 4 | 4 / 0 | 0 / 0 | SUCCEEDED · NO_VERIFIED_OFFERS |
| laptop-3 | 5 / 4 | 4 / 0 | 0 / 0 | SUCCEEDED · NO_VERIFIED_OFFERS |
| phone-1 | 5 / 0 | 0 / 0 | 0 / 0 | SUCCEEDED · NO_OFFICIAL_RESULTS |
| phone-2 | 5 / 0 | 0 / 0 | 0 / 0 | SUCCEEDED · NO_OFFICIAL_RESULTS |
| phone-3 | 5 / 0 | 0 / 0 | 0 / 0 | SUCCEEDED · NO_OFFICIAL_RESULTS |
| tablet-1 | 5 / 3 | 3 / 0 | 0 / 0 | SUCCEEDED · NO_VERIFIED_OFFERS |
| tablet-2 | 5 / 3 | 3 / 0 | 0 / 0 | SUCCEEDED · NO_VERIFIED_OFFERS |
| tablet-3 | 5 / 0 | 0 / 0 | 0 / 0 | SUCCEEDED · NO_OFFICIAL_RESULTS |
| monitor-1 | 5 / 0 | 0 / 0 | 0 / 0 | SUCCEEDED · NO_OFFICIAL_RESULTS |
| monitor-2 | 5 / 0 | 0 / 0 | 0 / 0 | SUCCEEDED · NO_OFFICIAL_RESULTS |
| monitor-3 | 5 / 0 | 0 / 0 | 0 / 0 | SUCCEEDED · NO_OFFICIAL_RESULTS |

최초 검색에서 반환 75개·공식 URL 33개, 수집 33개·가격 검증 11개였다. 가격 검증 수는 페이지 단위이므로 최종 모델 중복 제거 전 수다. 상품 검사 사유는 `ACCEPTED` 11건, `PRODUCT_MISSING` 1건, `VARIANT_UNSUPPORTED` 2건, `UNAVAILABLE` 1건, `CANONICAL_URL_MISMATCH` 12건, `RESOLVED_URL_MISMATCH` 6건이다.

보완 대상 5회에 검색을 추가했고 문서 수집은 2회 시도했다. 결과는 `CANONICAL_URL_MISMATCH` 1건, `NO_OFFICIAL_DOCUMENT` 3건이다. provider 실패는 `COLLECTION_FAILED` 1건이며 보완 수집의 502에도 기존 가격 후보를 PARTIAL로 보존했다. canonical 불일치 원문은 저장하지 않아 다른 모델·지역 경로·사이트 변환 중 무엇이 원인인지는 확정하지 않는다. 검색 미확보를 공식 문서 자체의 부재로 해석하지 않는다.

[별도 공개 HTML 진단](source-review.md)에서는 T13 ANC의 공식 제품 문서에서 ANC·호환 플랫폼 2개 사양을 채택했다. 이 성공은 실제 평가의 문서 도달·수집 성공으로 합산하지 않는다. 실제 경로는 검색 미확보·canonical 검증·수집 장애에서 막혔다.

## 4. 시간과 사용량

| 항목 | 직전 v2 | 이번 v3 |
| --- | ---: | ---: |
| 검색 / 수집 / AI 호출 | 15 / 32 / 3 | 20 / 35 / 3 |
| 입력 / 출력 token | 52,230 / 5,662 | 51,864 / 5,783 |
| queue 중앙값 / 최대 | 1,097 / 1,616 ms | 1094 / 1923 ms |
| 실행 중앙값 / 최대 | 13,604 / 30,933 ms | 14798 / 39896 ms |
| 사용자 관찰 중앙값 / 최대 | 15,391 / 33,147 ms | 15631 / 42420 ms |
| 429 한도 오류 | 0 | 0 |

실행 120초 초과 0건, 사용량 미확인 0건, 완료 계측 누락 0건이다. 95초 생성 간격은 개별 응답 시간에 포함하지 않는다. 보완 문서 원문을 AI에 보내지 않으며 AI 호출 수 상한은 그대로 1회다. 이번 추가 검색 5회·문서 수집 2회가 사양 채택으로 이어지지 않아 비용 대비 이득을 입증하지 못했다. token은 실측이며 금액·무료 잔여 quota로 환산하지 않았다. 별도 공개 HTTP 진단 5회와 웹 도구 확인은 서비스 provider 호출 밖의 작업이다.

## 5. 설명 유용성과 다음 병목

8개 설명 모두 가격·예산·미확인 안내 수준으로 1점이다. 확인된 사양과 선택 용도를 연결하는 2·3점 설명은 없으며 후보가 없는 조건은 해당 없음이다. 이전 평가 대비 가격 후보 수 변동만으로 추천 품질이 향상됐다고 주장하지 않는다.

다음 개선의 우선순위는 **실제 보완 검색 결과와 수집된 canonical의 연결 실패를 소량의 진단으로 분리하는 것**이다. 다른 모델·세대를 합치는 방식으로 거절을 없애지 않는다. 공식 문서에 도달하지 못하는 문제와 최초 구매 페이지의 상품·가격 탈락은 별개로 다뤄야 한다. 미등록 브랜드·지역 별칭·OCR·리뷰·새 provider 확장은 이번 구현에 포함하지 않았다.

## 재현 자료와 검증

- [manifest](manifest.json), [응답·계측](results.jsonl), [집계](summary.json), [이벤트](events.jsonl), [생성 대응](created.jsonl)
- [공식 문서 직접 진단](source-check.json), [출처 검토](source-review.md), [후보별 평가](assessment.json), [사용자 평가표](human-review.md)
- 로컬 전체 `npm run check` 통과 후 마지막 보강을 backend unit 166개·integration 42개, frontend lint/typecheck·OpenAPI drift, Chromium E2E 28개로 다시 확인했다. 구현 커밋의 [CI 전체 검사와 브라우저 테스트](https://github.com/lawliet62/Decision-Assistant/actions/runs/37401782888)도 통과했다.
- E2E에서 설정 로딩 전 동의 입력이 초기화되는 현상을 발견해 로딩 완료 전 입력을 비활성화했다. 지연 응답 검증을 추가한 뒤 전체 E2E가 통과했다. 테스트 삭제·skip·검증 약화는 없다.

각 조건 한 번의 작은 표본이며 서비스 전반의 품질 보증이나 무작위 대조 실험이 아니다.
