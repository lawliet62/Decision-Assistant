# 제품 탐색 A안 실제 비교 평가

이 절차는 테스트 fixture로 확인할 수 없는 실제 검색 누락·수집·가격 검증 병목을 측정한다. [ADR 0013](decisions/0013-discovery-query-evaluation.md)에 따라 기존 방식과 A안을 비교하고 B/C 도입은 결과 이후 결정한다.

[2026-09-30 재실험 보고서](evaluations/discovery-a-2026-09-30/README.md)에 총 30회 결과와 다섯 평가 항목, 원문·앱 판정의 차이를 기록했다. worker 혼입으로 무효인 초기 비교는 별도 기록이며 재실험 집계에 포함하지 않는다.

## v2 검증·설명 개선 평가

[ADR 0014](decisions/0014-evidence-based-discovery-validation.md)의 `official-kr-v2`도 같은 15조건을 한 번씩 평가한다. A안 검색을 유지하며 `--variant a`는 실행 표지다. 별도 commit/JAR hash와 결과 디렉터리로 v1과 구분한다. 전체 상한은 검색 15회·수집 75회·AI 15회, 조건 생성 간격은 65초다. 실패·부분 성공도 결과에 포함하며 중단 시 미실행 조건만 이어간다.

v2는 선택한 호환성도 추천의 필수 조건으로 확인하므로 v1의 `RECOMMENDED` 개수와 같은 기준의 정확도로 비교하지 않는다. 공통 외부 검토 기준과 잘못 충족 처리한 사례, 가격 조건·사양 누락·설명 모순의 변화도 함께 보고한다. 새로운 `fieldEvidence`, `fieldValidation`, `requirementResults` 집계는 필드별 근거 블록 확보·AI 구조화 검증·최종 판정을 구분한다. `AI_OMITTED`여도 서버 추출로 사양을 확보할 수 있고, `BLOCK_MISSING`만으로 이미지 전용 정보라고 단정할 수 없다. HTML·이미지 전체를 평가 파일에 보존하지 않는다.

[v2 15조건 평가 결과](evaluations/discovery-v2-2026-09-30/README.md)에서 검색 15·수집 37·AI 3회, 한도 오류 0건을 확인했다. 반환 후보 3개는 모두 확인 필요이며 기본 추천 2개 이상은 0/15다. 별도의 실제 공식 페이지 파서 진단에서 회원 가격 제외와 RAM·SSD 미달 판정을 확인했지만 검색 후보 확보와 설명 유용성은 여전히 부족하다.

## 후속 우선순위: 공식 상품 연결 복구

2026-10-01 human이 B/C 또는 다른 후속안의 선택·구현을 위임했다. 기존 평가의 URL 불일치 18건·옵션 미지원 9건을 먼저 진단하고, 추가 provider 없이 LG 경로 별칭·QCY 색상 옵션을 지원하는 국소 수정부터 진행한다. B의 AI 검색 계획이나 C의 탐색 범위 확대를 구현한 것으로 해석하지 않는다. 공식 판매처·비교 가격·필수 조건의 기준과 실행당 검색 1·수집 최대 5·AI 최대 1회는 유지한다.

기준 코드의 별도 진단 4조건(노트북·이어폰·휴대폰·모니터 각 1회)과 수정 후 같은 15조건 평가를 구분한다. 진단 상한은 검색 4·수집 20·AI 4회, 후속 평가는 검색 15·수집 75·AI 15회다. 두 실행 모두 명시적 동의·quota 경로를 사용하며 실패 응답을 자동 재시도하지 않는다. 파서 합성 회귀 검증과 실제 provider의 후보·설명 성능을 별도로 기록한다. 이전 날짜와 검색 결과가 달라질 수 있으므로 동일 입력이라는 이유만으로 변화 전체를 코드 효과로 주장하지 않는다.

초기 수정 빌드의 이어폰 3조건에서 색상별 후보가 최종 반환 단계에 중복되는 결함을 발견했다. 해당 실행은 완료된 조건 사이에서 중단하고 진단 자료로 보존한다. 반환 단계까지 수정한 고정 빌드로 최종 15조건을 별도로 실행하며 두 빌드의 결과를 하나의 성능 평가로 합치지 않는다. 초기 수정 진단에 사용한 검색·수집·AI·토큰도 별도 비용으로 보고한다.

[상품 연결 복구 15조건 결과](evaluations/discovery-linking-2026-10-01/README.md): 가격 확인 후보는 3개에서 7개로 늘었고 2개 이상 반환은 2/15였다. 모두 사양·호환성 확인 필요여서 조건 확인 후보 2개 이상은 0/15, 설명 평균은 1/3점이다. 검색 15·수집 32·AI 3회, 검색 시간 초과 1건·한도 오류 0건이었다. 같은 공개 HTML을 변경 전후 파서에 전달한 별도 비교에서도 QCY HT16·HT19 복구를 확인했다. 추가 호출 없는 수정이지만 입력 토큰은 24,899에서 52,230으로 늘었다. B/C와 리뷰 수집은 구현하지 않았다.

## 실행

### v3 공식 문서 보완 평가

[ADR 0015](decisions/0015-official-specification-supplement.md)에 따라 기존 15조건을 `--official-specs`로 실행하면 각 생성 요청에 별도 보완 동의를 기록한다. 총 상한은 검색 45·수집 105·AI 15이며 실행당 3·7·1을 넘으면 평가를 중단한다. 보완 대상이 없으면 추가 호출하지 않는다. 최대 수집 수 증가를 고려해 이번 평가의 생성 간격은 `--min-interval-ms 95000`을 사용한다. 보완 실패·URL/모델/사양 미확보와 성공을 `supplementaryDocuments` 및 `failures`로 따로 집계하고 초기 상품 검색 funnel과 혼합하지 않는다. 다른 동의 모드·빌드의 결과는 resume으로 이어 붙일 수 없다.

```text
node scripts/discovery-evaluation/run.mjs --execute-live --variant a --official-specs --min-interval-ms 95000 --source-commit <고정 commit> --jar <고정 JAR> --output <새 결과 디렉터리>
```

아래 1·5·1 설명과 기존 명령은 `--official-specs`를 사용하지 않는 v1/v2 비교 경로다.

`scripts/discovery-evaluation/cases.json`의 15개 공개·합성 조건을 사용한다. 현재 Dev provider/model·공식 판매처 정책·quota를 유지한다. 기존 Decision을 수정하지 않고 조건마다 새 Decision을 만든다. 기본 CI와 일반 test task에서는 실행하지 않는다.

1. Docker PostgreSQL과 `backend/.env`의 local/GCP Dev 설정, ADC, 검색·수집·AI 활성화를 준비한다. 같은 DB를 소비하는 다른 backend를 중단한다. 포트만 다르게 실행해도 작업 큐는 격리되지 않는다. 실행기는 8080 서버가 응답하거나 자기 worker의 완료 계측이 없으면 중단한다. Secret 값은 파일·환경 변수·보고서에 복사하지 않는다.
2. 평가할 revision에서 `node scripts/gradle.mjs bootJar`를 실행하고 JAR를 방식별 독립 경로에 복사한다. 실행 중인 JAR를 다시 빌드해 덮어쓰지 않는다. 기준 방식은 공통 계측만 포함한 commit `1bdc554`, A는 이 변경의 최종 revision이다. 기준 측정 후에 A를 측정하며 각각의 commit/JAR hash를 보존한다. 실행기는 이 PR의 최신 버전을 공통으로 사용한다.
3. `backend/build/discovery-evaluation` 부모 디렉터리를 만들고 다음을 실행한다. variant는 결과의 표지이며 JAR의 알고리즘을 바꾸지 않는다. 반드시 해당 방식으로 빌드한 JAR를 전달한다.

```text
node scripts/discovery-evaluation/run.mjs --execute-live --variant baseline --source-commit 1bdc554 --jar backend/build/discovery-evaluation/baseline.jar --output backend/build/discovery-evaluation/baseline
node scripts/discovery-evaluation/run.mjs --execute-live --variant a --source-commit 994dd90 --jar backend/build/discovery-evaluation/a.jar --output backend/build/discovery-evaluation/a
```

실행기는 빈 포트 `127.0.0.1:18080`에서 JAR를 실행하고 local 인증·CSRF·동의·quota를 거쳐 API로 비교를 생성한다. 같은 포트를 사용하는 E2E와 동시에 실행하지 않는다. 생성 간격은 기본 65초(`--min-interval-ms`, 최소 13초)이며 호출 실패를 자동 재시도하지 않는다. 한도 응답이면 중단하고 미실행 사례를 남긴다. 완료 후 자신이 실행한 JVM만 종료한다. 기존 결과 디렉터리는 덮어쓰지 않는다.

2026-09-30 재실험에서 초기 13초 간격으로 수집이 이어지던 중 Firecrawl 429가 발생했다. [공식 한도 문서](https://docs.firecrawl.dev/rate-limits)는 Free의 `/scrape`를 분당 10회로 설명한다. 실제 계정 플랜·429의 상세 원인을 확정한 것은 아니지만, 보수적으로 생성 간격을 늘린다. 이 간격은 서로 다른 평가 조건 사이에만 적용되며 개별 요청의 대기 시간에는 포함하지 않는다. 전체 실험 시간에는 포함한다.

한도 상태를 검토한 뒤 승인된 호출 예산 안에서 미실행 조건만 이어갈 때는 `--resume-from <이전 결과 디렉터리>`와 새로운 `--output`을 명시한다. 이전 manifest/JAR/입력 hash·완료 계측·생성 기록을 검사하며, 실패·부분 성공을 포함해 이미 결과가 나온 조건은 재실행하지 않는다. 이어간 결과는 이전 결과와 합칠 때 조건 ID 중복 없이 합치고 실행 구간과 생성 간격 변경을 보고한다. 호출 상한은 구간마다 새 예산으로 해석하지 않고 원래 15개 조건 전체에 적용한다.

각 방식 최대 검색 15회·수집 75페이지·AI 15회다. 120초는 관찰 기준이며 실행 중단 조건이 아니다. UI/평가 runner의 관찰 timeout과 실제 provider 사용량은 다르며, 취소가 과금 취소를 뜻하지 않는다.

## 결과와 평가

- `manifest.json`: commit, 작업 diff/JAR/입력 hash, 시작·종료, 모델 설정, 완료·미실행 수.
- `created.jsonl`: 합성 사례와 생성한 Decision의 대응. 중간 중단 시 기존 실행 확인에 사용한다.
- `results.jsonl`: 최종 discovery·후보·인용과 단계별 계측. 호출 전 종료는 token 0, AI 호출 후 사용량 응답이 없으면 null이다.
- `events.jsonl`: 고정 `product_event`의 개수·시간·사유 코드만 보존한다. HTML·Markdown 전체, 일반 SDK 로그, Secret은 보존하지 않는다.

`node scripts/discovery-evaluation/summarize.mjs <results.jsonl>`로 단계별 개수·시간을 집계한다. 완료 계측이 없는 실행은 호출 수를 null로 두고 유효 비교에서 제외한다. 다른 worker가 같은 DB에서 처리한 결과는 요청한 알고리즘의 결과라는 보장이 없다.

평가 보고서에는 아래 기준과 사용자용 빈 평가란을 둔다. API의 `RECOMMENDED`와 외부 검토 결과는 별도로 기록한다. v1 서버 그룹 판정은 예산·최소 사양 기반이며, v2는 선택한 호환성 근거도 확인한다. 어느 버전도 선택하지 않은 개별 기기·모든 부가 기능의 호환성까지 보장하지 않는다.

| 항목 | 기준 |
| --- | --- |
| 비교 후보 2개 이상 | 공식 상품·판매가·제품군·예산·최소 사양·호환성을 출처로 확인한 서로 다른 모델. 색상 중복 제외, 미확인 별도 |
| 오판·근거 없는 주장 | 충족 판정과 출처의 불일치, 근거 없는 사양/용도 적합성 주장. 후보가 없으면 검증 대상 없음이며 정확도 100%로 해석하지 않음 |
| 탈락 단계 | 검색 반환, 공개 URL/중복, 공식 범위, 수집, 상품/가격 파서, AI 구조화 검증, 조건 미달/미확인 |
| 시간·사용량 | queue/실행/사용자 관찰 시간, 중앙값·최댓값·120초 초과, provider 호출·token·한도 오류 |
| 설명 유용성 | 0 없음/근거 없음, 1 사양 반복/일반론, 2 사양과 용도 연결, 3 연결 설명과 한계·미확인 사항 제시. 후보 없음은 해당 없음 |

공식 판매 페이지와 정확한 인용을 직접 검토하되 청음·착용·벤치마크를 수행한 것으로 표현하지 않는다. 출처 확인이 불가능하면 미확인으로 남긴다. 외부 검토에 사용한 짧은 발췌·URL·확인 시각만 보존한다.

## 해석

동일 모델·계정 설정으로 가능한 가까운 시간에 실행한다. 전체 시도 대비 결과 확보율과 provider 오류 없는 사례의 비율을 함께 제시한다. 사례별 1회이므로 검색 순위·재고·모델 응답의 시간 변동과 소표본 한계가 있다. 통계적 우위를 주장하지 않는다.

수집·가격 파싱 병목이면 B/C를 바로 구현하지 않고 해당 문제를 별도 제안한다. 검색 계획이 병목이면 B, 공식 검색 범위가 병목이면 C를 후속 후보로 기록한다. 실험 중 다른 파서·프롬프트·판정을 함께 변경하지 않는다.
