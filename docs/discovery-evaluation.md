# 제품 탐색 A안 실제 비교 평가

이 절차는 테스트 fixture로 확인할 수 없는 실제 검색 누락·수집·가격 검증 병목을 측정한다. [ADR 0013](decisions/0013-discovery-query-evaluation.md)에 따라 기존 방식과 A안을 비교하고 B/C 도입은 결과 이후 결정한다.

[2026-09-30 초기 관찰 보고서](evaluations/discovery-a-2026-09-30/README.md)는 worker 혼입으로 무효인 비교와 확인 가능한 단일 사례를 구분해 기록한다. 개선 효과 판정은 재실험까지 보류한다.

## 실행

`scripts/discovery-evaluation/cases.json`의 15개 공개·합성 조건을 사용한다. 현재 Dev provider/model·공식 판매처 정책·quota를 유지한다. 기존 Decision을 수정하지 않고 조건마다 새 Decision을 만든다. 기본 CI와 일반 test task에서는 실행하지 않는다.

1. Docker PostgreSQL과 `backend/.env`의 local/GCP Dev 설정, ADC, 검색·수집·AI 활성화를 준비한다. 같은 DB를 소비하는 다른 backend를 중단한다. 포트만 다르게 실행해도 작업 큐는 격리되지 않는다. 실행기는 8080 서버가 응답하거나 자기 worker의 완료 계측이 없으면 중단한다. Secret 값은 파일·환경 변수·보고서에 복사하지 않는다.
2. 평가할 revision에서 `node scripts/gradle.mjs bootJar`를 실행하고 JAR를 방식별 독립 경로에 복사한다. 실행 중인 JAR를 다시 빌드해 덮어쓰지 않는다. 기준 방식은 공통 계측만 포함한 commit `1bdc554`, A는 이 변경의 최종 revision이다. 기준 측정 후에 A를 측정하며 각각의 commit/JAR hash를 보존한다. 실행기는 이 PR의 최신 버전을 공통으로 사용한다.
3. `backend/build/discovery-evaluation` 부모 디렉터리를 만들고 다음을 실행한다. variant는 결과의 표지이며 JAR의 알고리즘을 바꾸지 않는다. 반드시 해당 방식으로 빌드한 JAR를 전달한다.

```text
node scripts/discovery-evaluation/run.mjs --execute-live --variant baseline --source-commit 1bdc554 --jar backend/build/discovery-evaluation/baseline.jar --output backend/build/discovery-evaluation/baseline
node scripts/discovery-evaluation/run.mjs --execute-live --variant a --source-commit 994dd90 --jar backend/build/discovery-evaluation/a.jar --output backend/build/discovery-evaluation/a
```

실행기는 빈 포트 `127.0.0.1:18080`에서 JAR를 실행하고 local 인증·CSRF·동의·quota를 거쳐 API로 비교를 생성한다. 같은 포트를 사용하는 E2E와 동시에 실행하지 않는다. 생성 간격은 최소 13초이며 호출 실패를 자동 재시도하지 않는다. 한도 응답이면 중단하고 미실행 사례를 남긴다. 완료 후 자신이 실행한 JVM만 종료한다. 기존 결과 디렉터리는 덮어쓰지 않는다.

각 방식 최대 검색 15회·수집 75페이지·AI 15회다. 120초는 관찰 기준이며 실행 중단 조건이 아니다. UI/평가 runner의 관찰 timeout과 실제 provider 사용량은 다르며, 취소가 과금 취소를 뜻하지 않는다.

## 결과와 평가

- `manifest.json`: commit, 작업 diff/JAR/입력 hash, 시작·종료, 모델 설정, 완료·미실행 수.
- `created.jsonl`: 합성 사례와 생성한 Decision의 대응. 중간 중단 시 기존 실행 확인에 사용한다.
- `results.jsonl`: 최종 discovery·후보·인용과 단계별 계측. 호출 전 종료는 token 0, AI 호출 후 사용량 응답이 없으면 null이다.
- `events.jsonl`: 고정 `product_event`의 개수·시간·사유 코드만 보존한다. HTML·Markdown 전체, 일반 SDK 로그, Secret은 보존하지 않는다.

`node scripts/discovery-evaluation/summarize.mjs <results.jsonl>`로 단계별 개수·시간을 집계한다. 완료 계측이 없는 실행은 호출 수를 null로 두고 유효 비교에서 제외한다. 다른 worker가 같은 DB에서 처리한 결과는 요청한 알고리즘의 결과라는 보장이 없다.

평가 보고서에는 아래 기준과 사용자용 빈 평가란을 둔다. API의 `RECOMMENDED`와 외부 검토 결과는 별도로 기록한다. 현재 서버 그룹 판정은 예산·최소 사양 기반이며 호환성 전체를 자동 확정하는 판정기가 아니다.

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
