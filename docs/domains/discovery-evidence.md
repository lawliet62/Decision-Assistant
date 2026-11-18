# 제품 탐색·근거

[전체 흐름](../domain.md) · [화면 모음](../ui/README.md)

## 책임과 범위

조건에 맞는 후보를 제안하고 사용자가 승인한 후보를 비교에 연결한다. 공개 근거의 출처·시각·확인 상태를 보존하며 표시 사양과 평가 입력을 분리한다. 기본 카탈로그와 기존 외부 탐색은 같은 개인 후보 모델로 연결되지만 전송 경계와 실행 방식이 다르다.

## 모델과 관계

| 모델 | 의미 있는 속성과 관계 |
|---|---|
| `ProductDiscovery` | Decision별 조건·동의·상태를 저장한다. executionId는 실행, generation은 추천 회차, displayedGeneration은 표시 중인 회차다. |
| `CandidateSuggestion` | 아직 Option이 아닌 제안이다. productIdentity·facts·officialOffer·출처 excerpt·시각·회차를 보존하며 승인하면 optionId로 연결한다. |
| `ProductFact` | 표시 사양의 값·URL·인용·확인 시각·verificationStatus·normalizationVersion이다. 평가 점수나 사용자 확인 수치가 아니다. |
| `MinimumRequirement`, `RequirementCheck` | 선택한 사양 하한·지원 요구와 MEETS/BELOW/UNKNOWN 판정이다. UNKNOWN 이유는 MISSING·AMBIGUOUS·CONFLICT다. |
| `EvidenceImport` | Decision의 Option에 연결한 공개 URL 수집 작업이다. 원문 전체 대신 hash·짧은 excerpt·조회 시각을 보존한다. |
| `DecisionQuestion` | 미확인 질문의 targetKey·선택지·우선순위·답변 상태다. |
| 제품명 lookup 결과 | 저장 모델이 아닌 일시적 검색 결과다. 사용자가 고른 이름·URL만 Option에 연결한다. |

## 생명주기와 실행 경로

| 경로 | 시작과 완료 | 외부 호출 경계 |
|---|---|---|
| 기본 `intake-catalog-v1` | 공유 카탈로그를 읽어 개인 후보를 같은 transaction에서 생성한다. | 추천 요청의 검색·수집·AI 호출 없음. 운영 갱신은 별도다. |
| 기존 `intake-v1/v2` | 후보 0개로 Decision을 만든 뒤 worker가 검색·수집·구조화·검증한다. | 명시적 검색·수집·AI 동의와 provider별 정보 확인이 필요하다. |
| 제품명 검색·직접 추가 | 선택 전 검색은 Decision을 만들지 않는다. 선택 후 이름·URL로 후보를 추가한다. | Serper 검색과 Firecrawl 수집은 별도 동의다. URL 직접 입력은 검색 장애 때도 가능하다. |

외부 탐색의 흐름은 `PENDING → RUNNING → SUCCEEDED / PARTIAL / FAILED`다. 후보가 없는 정상 완료도 SUCCEEDED이며 emptyReason으로 공식 결과 없음·검증 판매가 없음·새 후보 없음 등을 구분한다. 상태 성공만으로 후보 품질이나 외부 검증 성공을 판단하지 않는다.

DRAFT만 편집 가능한 진행 상태다. 완료한 보류도 추천·근거 수정 전에 정정 또는 재검토를 시작해야 한다. 추가 탐색은 generation을 올리고 이전 후보를 제외한다. 실패하면 기존 표시 목록을 유지하며 승인한 후보와 사용자 입력을 바꾸지 않는다. 늦은 결과는 실행 식별자·version으로 새 회차를 덮어쓰지 못하게 한다. 실패 후 재시도는 수동 경로다. 재검토 취소는 시작 전 후보 수락·제외·Option 연결을 복원하고 새 후보는 PROPOSED로 남기지만, 실행 회차나 수집 자료 전체를 되돌리지 않는다. [취소 복원 경계](decision-comparison.md#정정재검토취소)를 따른다.

| 모델 | 상태 전이와 의미 |
|---|---|
| CandidateSuggestion | `PROPOSED → ACCEPTED`는 Option 연결, `PROPOSED → DISMISSED`는 제안 제외다. 제안 상태와 실제 결정 결과는 별개다. |
| EvidenceImport | `PENDING → RUNNING → SUCCEEDED`. 실패 시 2회 시도 전에는 PENDING으로 돌리고 이후 FAILED다. 수동 retry는 시도 수를 초기화한다. |
| DecisionQuestion | `OPEN → ANSWERED / DISMISSED`. 질문의 답변은 대상 입력을 보강한다. |

## 불변 조건과 다른 도메인의 경계

사용자 URL은 공개 HTTP(S) 검증과 명시적 동의를 거쳐 provider adapter에 전달한다. 인증 정보·cookie·private/local host는 허용하지 않는다. 조회 성공이나 검색 발췌를 확인된 평가값으로 취급하지 않는다.

사양 판정은 근거 부족·모호·상충을 UNKNOWN으로 보존한다. 최소 사양 미달은 기존 hard Constraint의 FAIL이나 평가 탈락을 뜻하지 않는다. 화면에서 직접 확인한 가격·기능은 [비교 상태](decision-comparison.md)에 반영하고 사용자 확인값은 자동 수집이 덮어쓰지 않는다. 수집·추천 결과 쓰기는 원본 Decision의 존재·삭제 상태를 재확인한다.

AI 구조화 실행 StructureRun과 평가 결과의 관계는 [평가](evaluation.md)에서 설명한다. 표시 사양이 음질·착용감 등의 주관적 만족도 값을 자동 확정하지 않는다.

## 규칙·구현·검증

- 규칙: [BR-EVD-01-03, BR-DISC-01-05, BR-COST-01](../business-rules.md), [ADR 0009](../decisions/0009-click-first-product-discovery.md), [ADR 0014](../decisions/0014-evidence-based-discovery-validation.md).
- 구현: [DiscoveryService](../../backend/src/main/java/com/decisionassistant/guidance/DiscoveryService.java), [DiscoveryWorker](../../backend/src/main/java/com/decisionassistant/guidance/DiscoveryWorker.java), [EvidenceWorkflowWorker](../../backend/src/main/java/com/decisionassistant/guidance/EvidenceWorkflowWorker.java), [ProductLookupService](../../backend/src/main/java/com/decisionassistant/lookup/ProductLookupService.java).
- 검증: `DiscoveryApiIntegrationTest`, `DiscoveryWorkerTest`, `EvidenceWorkflowWorkerTest`, `MinimumRequirementMatcherTest`, `frontend/e2e/click-discovery.spec.ts`, `frontend/e2e/minimum-requirements.spec.ts`.
