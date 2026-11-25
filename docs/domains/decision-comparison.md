# 결정·비교

[전체 흐름](../domain.md) · [화면 모음](../ui/README.md)

## 책임과 범위

개인 결정의 현재 입력을 관리하고 같은 후보의 비교와 실제 판단을 기록한다. 제품 선택·보류·포기에서 필수 흐름이 끝나며 구매와 회고는 선택 사항이다. 완료 고정본을 보존하면서 구매 전 정정·재검토·취소와 보관·삭제를 제공한다. 현재 정책은 [ADR 0022](../decisions/0022-decision-reasons-and-reusable-history.md), 점수 계산은 [평가](evaluation.md), 후보 생성은 [탐색·근거](discovery-evidence.md)를 따른다.

## 모델과 관계

Decision은 optimistic version을 가진 수정 가능한 aggregate root다. ownerUserId가 소유자를, templateKey/templateVersion이 초기 기준의 출처를 식별한다. 제목은 두 생성 화면에서 선택으로 지정하며, 비워 두면 생성 경로별 자동 제목을 쓴다([API contract](../api-contract.md#결정-생성-제목)).

| 소유 모델·속성 | 의미 |
|---|---|
| Context (`DecisionContext`) | 목적·예산·통화·자유 메모를 담은 value다. 명시적으로 선택한 과거 기록의 출처 포함 참고 메모도 여기에 저장한다. |
| Option (`DecisionOption`) | 실제 선택 가능한 후보와 제품 출처다. 추천 제안은 승인 후에만 Option이 된다. |
| Criterion | soft preference다. numeric utility 또는 1-5 rating, 중요도·anchor·단위·활성 여부를 가진다. |
| OptionValue | 같은 Decision의 Option×Criterion 입력이다. 확인/추정과 사용자/웹/AI origin을 구분한다. |
| Constraint (`DecisionConstraint`) | 후보 통과 여부를 가르는 hard requirement다. 점수 가중치와 별개다. |
| ConstraintResult | 같은 Decision의 Option×Constraint에 대한 PASS/FAIL/UNKNOWN이다. |
| `Comparison.State` | 최초 추천과 독립적인 현재 조건, 후보 보류·직접 확인 값이다. |
| Resolution | 결과·선택 후보·이유와 당시 비교 snapshot이다. 회고·기존 알림·별도 구매 기록의 대상이다. |
| `DecisionReasons.Reason` | 결과별 안정적인 ID·label·kind를 가진 공용 이유 템플릿이다. 사용자 판단이며 제품 사양이 아니다. |
| `revisionDraft / revisionCandidates` | 재검토 시작 전 입력과 추천 후보 수락·제외·Option 연결을 보존하는 임시 복원본이다. 과거 Resolution 고정본과 별개다. |

OptionValue와 ConstraintResult는 Decision 내부 참조를 사용하며 각 조합은 하나만 존재한다. 표시용 ProductFact와 평가용 OptionValue는 별개다.

## 진행·완료의 생명주기

| 저장/API status | phase | result | 현재 의미 |
|---|---|---|---|
| DRAFT | COMPARING | null | 직접 입력 수정·평가·완료가 가능한 유일한 진행 상태 |
| CHOSEN | COMPLETED | OPTION_SELECTED | 제품 선택 완료. 실제 구매와 별개 |
| DEFERRED | COMPLETED | null | 보류로 완료. 다시 검토를 시작해야 편집 가능 |
| ABANDONED | COMPLETED | NOT_PURCHASED | 포기로 완료. UI의 ‘포기’와 기존 result 값을 구분 |
| ARCHIVED | ARCHIVED | null | 기존 호환 값. 새 보관 기능의 전이 상태가 아님. 목록은 해당 결정이 있을 때만 ‘보관됨’ 그룹으로 표시 |

기존 enum의 DecisionPhase.DEFERRED는 호환 값으로 남아 있지만 DEFERRED status의 현재 phase 계산은 COMPLETED다. 기존 보류 기록도 완료 그룹으로 읽으며 이유·구매 여부를 소급 추정하지 않는다. DecisionResult.NOT_PURCHASED와 아래 PurchaseStatus.NOT_PURCHASED는 이름이 같아도 각각 판단 결과와 선택 후 구매 기록이다.

### 완료 이유

Resolution은 reasonIds(복수 이유)·primaryReasonId(선택한 대표 이유)·rationale(자유 설명)를 가진다. 이유·대표 이유·설명은 모두 선택 사항이다. 이유 ID는 해당 결과 kind의 템플릿에 속해야 하고 중복될 수 없으며 대표 이유는 선택 목록 안에 있어야 한다. ID는 기존 의미를 유지하고 의미 확장은 새 ID를 사용한다.

제품 선택에는 현재 Decision의 selectedOptionId가 필요하고 보류·포기에는 지정하지 않는다. 이유를 골랐다고 사양 지원 여부·조건 판정·평가 weight를 바꾸지 않는다.

### 구매 상태

현재 CHOSEN Resolution에 최신 Decision version과 resolutionId로 기록한다.

| purchaseStatus | 의미·변경 경계 |
|---|---|
| UNRECORDED | 구매 미기록. 미구매로 간주하지 않음 |
| PLANNED | 구매 예정 |
| NOT_PURCHASED | 선택 후 구매하지 않기로 함 |
| PURCHASED | 구매함. 이후 구매 상태 변경·정정·재검토 차단 |

구매 전 상태는 현재 선택의 구매 기록으로 갱신할 수 있다. PURCHASED 이후 반품·환불이나 다시 편집하는 경로는 현재 범위 밖이다. 제품 선택 완료 자체는 구매 상태를 자동 확정하지 않는다.

## 정정·재검토·취소

완료 상태에 새 Resolution을 직접 추가하지 않는다. 모든 완료 결과에서 미구매 명시 확인(notPurchased=true)과 최신 expectedVersion으로 CORRECTION 또는 RECONSIDERATION을 시작해 DRAFT로 재개한다. editMode·revisionBaseId는 최종 완료 또는 취소까지 편집 목적과 원본을 식별한다.

| 재개 경로 | 입력과 원본 이력 | 완료 시 효과 |
|---|---|---|
| CORRECTION: 기록 수정 | 원본의 결과·선택 제품·복수 이유·대표 이유·메모로 판단 입력 초기화 | 새 선택·보류·포기로 완료하면 이전 기록을 supersededAt으로 대체 표시하고 해당 성향 활용·재확인을 제외 |
| RECONSIDERATION: 다시 검토 | 새 판단 입력으로 검토하고 과거 근거·회고 보존 | 과거 판단을 유효하게 남기고 새 기록 추가. 과거 유효한 성향 자동 무효화 없음 |

재개 시 기존 미완료 알림을 중단하고 보관 중이었다면 기본 목록으로 꺼낸다. 정정 중에는 원본 성향·원문 기록의 활용을 중단한다. 정정 중 보류도 완료 결과로 선택할 수 있다. 이전 회고를 새 Resolution에 옮기지 않는다.

취소는 최신 version과 임시 revisionDraft를 확인해 이전 제목·Context·후보·기준·값·조건·비교 입력과 원래 결론을 복원한다. 추천 후보의 기존 수락·제외·Option 연결도 복원하고, 검토 중 새 추천 후보는 PROPOSED로 남긴다. 수집 자료·추천 실행 묶음 전체를 삭제하거나 되돌리는 작업은 아니다. 취소는 과거 고정본을 변경하지 않으며 version을 과거 값으로 되돌리지 않는다.

새 완료 또는 취소 시 임시 복원본을 해제한다. 이전 버전에서 이미 시작한 검토는 revisionDraft가 없어 취소할 수 없다. draft 복원본은 있지만 후보 상태 복원본만 없으면 끊긴 수락 연결을 PROPOSED로 되돌리는 보수적 복구를 수행한다.

## 비교 상태와 고정본

조건 변경은 같은 후보를 유지하고 미달·미확인을 우선 표시한다. 후보 보류는 비교 표시에서 잠시 빼는 동작으로 후보·평가 입력을 삭제하거나 점수 평가에서 자동 제외하지 않는다. **후보 비교 보류와 결정 결과 DEFERRED는 서로 다르다.** 직접 확인은 해당 후보·기능·플랫폼에만 적용되고 해제하면 수집 근거로 돌아간다.

조건 저장·초안 갱신은 최신 expectedVersion으로 수행하며 polling·충돌이 저장 전 입력을 덮어쓰지 않게 한다. 신규 Resolution은 조건·후보·기준·입력·사양·직접 확인·판정을 고정한다. comparison-v2는 당시 제목도 보존한다. 고정본이나 제목이 없는 과거 기록을 현재 데이터로 소급 채우지 않는다.

## 기록 보관·삭제 수명

archivedAt, deletedAt, personalizationEnabled는 진행 상태와 독립적이다. 세 완료 상태 모두 보관할 수 있으며 결과와 활용 설정을 유지한다. 삭제 시 즉시 일반 조회·평가·worker·개인화·과거 원문 참고에서 제외하고 30일 안에는 삭제 전 상태·보관 여부·활용 설정으로 복구한다. 기한 경과 후 원본·종속 기록·원본 성향을 연쇄 영구 삭제한다.

다른 결정에 이미 복사한 값·참고 메모, 공유 카탈로그, 계정은 제거하지 않는다. 삭제와 외부 작업 완료·알림은 부모 잠금으로 순서를 보장하지만 이미 외부 전송한 요청·메일은 취소하지 않는다. 백업 수명과 복원 공개 조건은 [ADR 0020](../decisions/0020-decision-retention-and-personalization.md)을 따른다.

## 템플릿과 초기값

템플릿은 공유된 초기 기준·조건 생성 규칙이며 기존 Decision과 Evaluation을 소급 변경하지 않는다. 평가 templateVersion과 추천 intake templateVersion은 별개다. 이유 템플릿은 초기 Criterion을 만드는 제품군 템플릿과도 다르다.

| templateKey/version | 초기 비교 기준과 조건 |
|---|---|
| LAPTOP/laptop-v2 | 가격·무게·배터리·메모리·스토리지 numeric과 성능·디스플레이 rating. 예산·메모리·스토리지·무게·호환성 조건은 기본 비활성 |
| PHONE/phone-v1, TABLET/tablet-v1, MONITOR/monitor-v1 | 가격 numeric과 성능·휴대성·배터리·디스플레이·생태계 rating. 예산·호환성 조건은 기본 비활성 |
| EARBUDS/earbuds-v1 | 가격(KRW, best 50,000 / worst 500,000), 음질·착용감·노이즈 캔슬링·배터리 적합도 rating. 기본 weight 1, 예산·호환성 조건 비활성 |
| CUSTOM/custom-v1 | 초기 기준·조건 자동 추가 없음 |

rating은 1-5의 예상 만족도이며 사양으로 자동 확정하지 않는다. 추천에서 최대 예산을 선택하면 해당 constraint를 활성화한다. 기존 확인 성향의 기본 weight 적용은 [개인화](outcome-personalization.md), 새 원문 참고는 [과거 판단 기록 활용](history-reuse.md)을 따른다.

## 규칙·구현·검증

- 규칙: [BR-REASON-01, BR-DEC-01-06, BR-CMP-01-02, BR-DELETE-01](../business-rules.md), [ADR 0017](../decisions/0017-comparison-and-resolution-context.md), [ADR 0021](../decisions/0021-decision-completion-and-revisions.md), [ADR 0022](../decisions/0022-decision-reasons-and-reusable-history.md).
- 구현: [Decision](../../backend/src/main/java/com/decisionassistant/decision/Decision.java), [DecisionService](../../backend/src/main/java/com/decisionassistant/decision/DecisionService.java), [Resolution](../../backend/src/main/java/com/decisionassistant/decision/Resolution.java), [DecisionReasons](../../backend/src/main/java/com/decisionassistant/decision/DecisionReasons.java), [DecisionTemplateFactory](../../backend/src/main/java/com/decisionassistant/template/DecisionTemplateFactory.java).
- 검증: `DecisionApiIntegrationTest.reasonsPurchaseReconsiderationAndMemoryRespectHistoryAndOwnership`, 같은 테스트의 후보 선택 취소 복원 사례, `frontend/e2e/decision-reasons.spec.ts`, `frontend/e2e/decision-revisions.spec.ts`, `frontend/e2e/decision-list.spec.ts`.
