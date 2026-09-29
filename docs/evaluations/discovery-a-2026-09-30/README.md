# 제품 탐색 A안 초기 관찰 — 비교 실험 무효

기록일: 2026-09-30 KST. **A의 구현 검증은 통과했지만, 예정한 기존 방식 15회·A 15회의 유효한 비교 평가는 완료되지 않았다.** 아래 수치를 개선율이나 모델 정확도로 해석하지 않는다.

## 실험 상태와 무효 사유

평가 서버를 별도 포트에서 실행했지만 기존 개발 backend와 같은 PostgreSQL 작업 큐를 사용했다. 두 worker가 작업을 나눠 처리해 일부 결과의 코드 revision과 계측을 보장할 수 없었다. 이는 평가 환경 구성 오류다. 기준 방식 15건 중 6건만 평가 worker의 완료 계측이 있고, A는 생성 3건·최종 응답 저장 2건 중 1건만 완료 계측이 있다. A 실행을 중단했으며 나머지 12개 조건은 시작하지 않았다.

초기 비교 전체를 `comparisonValid: false`로 보존한다. 미측정 호출·token은 0이 아닌 `null`로 정정했다. `initial-*.jsonl`은 API 응답과 고정 이벤트로 만든 보존본이며, 계측의 존재와 비교 유효성은 별개다. 기존 개발 서버는 중단하지 않았다. 개발 backend의 일시 중단·복구와 추가 30회 측정에 대한 승인을 기다린다. 추가 호출 상한은 검색 30회·수집 150페이지·AI 30회이며 원래 실험에 더해 발생한다.

재발 방지를 위해 실행기에 8080 개발 서버 응답 확인과 자기 worker의 완료 계측 확인을 추가했다. 실행 중 JAR가 덮어써지지 않도록 방식별 별도 JAR와 source commit을 사용한다. 다른 포트의 worker까지 자동으로 찾아내는 장치는 아니므로 같은 DB를 소비하는 다른 backend가 없음을 실행 전에 확인해야 한다. [재실행 절차](../../discovery-evaluation.md)를 따른다.

## 요청한 다섯 항목의 현재 평가

| 항목 | 현재 확인한 결과 | 해석 제한 |
| --- | --- | --- |
| 비교 가능한 후보 2개 이상 확보 비율 | 전체 비율 산출 보류. 완료 계측이 있는 A의 `earbuds-1`은 후보 1개, 필수 조건·호환성까지 확인된 후보 0개 | 단일 사례이며 비교 실험 전체가 무효 |
| 필수 조건 오판·근거 없는 주장 | 해당 후보의 ANC는 서버가 `UNKNOWN/MISSING`으로 처리해 충족 오판은 관찰되지 않음. 네 가지 용도에 적합하다는 AI 설명은 근거 부족 | 다른 사례의 정확성이나 전체 오판율을 뜻하지 않음 |
| 후보 탈락 단계 | 해당 A 사례: 검색 5 → 공식 범위 5 → 수집 5 → 상품/가격 검증 2 → 최종 후보 1 → 조건 충족 확정 0 | 상품/가격 파서의 `VARIANT_UNSUPPORTED` 3건. 최종 후보 1개가 된 이유까지 이벤트만으로 확정할 수 없음 |
| 대기·호출·token·한도 | 해당 A 사례: 관찰 31.020초, queue 0.629초, 실행 29.305초; 검색 1·수집 5·AI 1; 입력 9,840·출력 524 token. 이 사례의 한도 오류 없음 | 전체 호출 합계는 미측정 실행 때문에 알 수 없음. 120초 초과율과 시간 분포 비교 보류 |
| 설명 유용성 | 해당 A 후보 0/3점: 용도를 나열한 적합성 주장에 확인된 사양의 연결 근거와 미확인 사항 설명이 없음 | 출처 검토에 대한 agent 평가이며 청음·착용·통화 실험이 아님 |

기준 방식의 최종 응답은 15건 모두 후보가 없었으며 `NO_OFFICIAL_RESULTS` 10건, `NO_VERIFIED_OFFERS` 5건이었다. 다만 9건의 worker 계측이 없어 유효한 기준 성능으로 사용하지 않는다. 완료 계측이 있는 6건 중 5건은 검색 반환 0건이었다. 나머지 `phone-3`은 검색 5·공식 4·수집 4·검증된 상품/가격 0건으로, 파서 사유는 `PRODUCT_MISSING` 2, `UNAVAILABLE` 1, `RESOLVED_URL_MISMATCH` 1이었다. 검색어 이외에도 수집 이후의 병목이 존재할 수 있다는 관찰까지만 가능하다.

## 원래 이어폰 조건의 출처 검토

입력은 출퇴근·운동·통화·음악, Android, ANC 필수, 예산 미지정이다. 평가 worker가 처리한 A 결과는 QCY T13 ANC APP 화이트 한 개였고 가격은 28,200원이다. [공식 판매 페이지](https://qcystore.co.kr/product/detail.html?product_no=633)의 상품명과 판매가 표시는 2026-09-30 KST에 직접 확인했다. 짧은 확인 문구는 `QCY T13 ANC APP`, `28,200원`이다.

API에 보존된 사실은 가격뿐이며 ANC 사양 인용이 없다. 상품명에 ANC가 있어도 현재 구조화 사양에는 누락되어 서버의 최소 조건 판정은 `UNKNOWN/MISSING`이다. Android 호환성과 네 가지 용도별 성능은 이 근거만으로 확정할 수 없다. AI의 “출퇴근, 운동, 통화, 음악 용도에 적합한 ANC 지원 무선 이어폰으로 확인됩니다.”라는 문장은 사양 근거를 제시하지 않는다. 상품명과 가격의 확인을 용도별 적합성의 검증으로 확대하지 않았다.

이 관찰은 A만으로 사용자가 원하는 설명 품질까지 해결되었다는 증거가 아니다. 유효한 재실험 이후 검색·상품/가격 파서·사양 구조화·설명 중 반복되는 병목을 먼저 판단한다. 파서나 설명 문제를 해결하려고 같은 실험 도중 수용 기준·AI 프롬프트를 바꾸지 않는다. B/C와 리뷰 수집은 보류한다.

## 재현 자료와 검증

- [입력 15개](../../../scripts/discovery-evaluation/cases.json)
- [기준 manifest](initial-baseline-manifest.json), [기준 결과 보존본](initial-baseline.jsonl)
- [A manifest](initial-a-manifest.json), [A 결과 보존본](initial-a.jsonl)
- [사용자 평가표](human-review.md)
- 코드 검증: `npm run check` 통과(unit, PostgreSQL integration, lint/typecheck/build, OpenAPI drift), 집계 테스트 4개 통과.
- 브라우저 검증: 관련 E2E 21개 통과. 기존 개발 frontend와 충돌하지 않도록 별도 checkout에서 Webpack으로 실행했다. 처음 18개 통과 후 잘못된 테스트 화면 가정 3개를 수정해 해당 3개를 다시 통과했다. 정상 production build는 기본 Turbopack으로 검증했다.

JSONL 집계 명령은 진단용이며 위 무효 판정을 뒤집지 않는다.

```text
node scripts/discovery-evaluation/summarize.mjs docs/evaluations/discovery-a-2026-09-30/initial-baseline.jsonl
node scripts/discovery-evaluation/summarize.mjs docs/evaluations/discovery-a-2026-09-30/initial-a.jsonl
```
