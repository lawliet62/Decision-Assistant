# 결정·비교

[전체 흐름](../domain.md) · [화면 모음](../ui/README.md)

## 책임과 범위

개인 결정의 현재 입력을 관리하고 같은 후보의 조건 비교와 실제 판단을 기록한다. 완료 기록을 보존하면서 정정·재검토와 보관·삭제를 제공한다. 점수 계산은 [평가](evaluation.md), 추천 후보 생성은 [탐색·근거](discovery-evidence.md)의 책임이다.

## 모델과 관계

Decision은 optimistic version을 가진 수정 가능한 aggregate root다. ownerUserId가 소유자를, templateKey/templateVersion이 초기 기준의 출처를 식별한다.

| 소유 모델·속성 | 의미 |
|---|---|
| Context (`DecisionContext`) | 목적·예산·통화·자유 메모를 담은 value다. |
| Option (`DecisionOption`) | 실제 선택 가능한 후보와 제품 출처다. 추천 제안은 승인 후에만 Option이 된다. |
| Criterion | soft preference다. numeric utility 또는 1-5 rating, 중요도·anchor·단위·활성 여부를 가진다. |
| OptionValue | 같은 Decision의 Option×Criterion 입력이다. 확인/추정과 사용자/웹/AI origin을 구분한다. |
| Constraint (`DecisionConstraint`) | 후보 통과 여부를 가르는 hard requirement다. 점수 가중치와 별개다. |
| ConstraintResult | 같은 Decision의 Option×Constraint에 대한 PASS/FAIL/UNKNOWN이다. |
| `Comparison.State` | 최초 추천과 독립적인 현재 예산·기능 조건, 후보 보류, 직접 확인한 값이다. |
| Resolution | 선택·보류·구매하지 않음과 이유, 당시 비교 snapshot이다. 회고·알림의 대상이다. |

OptionValue와 ConstraintResult는 Decision 내부 참조를 사용하며 각 조합은 하나만 존재한다. 표시용 ProductFact와 평가용 OptionValue는 별개다.

## 진행·완료·재개의 생명주기

| 저장/API status | phase | result | 허용되는 주요 흐름 |
|---|---|---|---|
| DRAFT | COMPARING | 없음 | 입력 수정, 평가, 선택·보류·구매하지 않음 기록 |
| DEFERRED | DEFERRED | 없음 | 입력 수정과 다시 판단 |
| CHOSEN | COMPLETED | OPTION_SELECTED | 회고, 기록 수정·재검토, 보관 |
| ABANDONED | COMPLETED | NOT_PURCHASED | 결정에 대한 회고, 기록 수정·재검토, 보관 |
| ARCHIVED | ARCHIVED | 없음 | 기존 호환 값이다. 새 보관 기능의 전이 상태로 사용하지 않는다. |

완료 상태에는 새 Resolution을 직접 추가하지 않는다. 최신 version으로 아래 경로를 시작하면 DRAFT로 재개한다. editMode와 revisionBaseId는 최종 완료까지 편집 목적과 원본을 유지한다.

| 재개 경로 | 원본과 후속 기록 | 관련 제한 |
|---|---|---|
| CORRECTION: 기록 수정 | 최종 완료 시 이전 기록을 supersededAt으로 대체 표시한다. | 정정 중 보류 불가, 원본 성향 적용 중단, 정정 후 해당 기억 활용·재확인 제외 |
| RECONSIDERATION: 다시 검토 | 과거 판단을 유효한 이력으로 유지하고 새 판단을 추가한다. | 보류 가능, 과거 유효한 성향을 자동 무효화하지 않음 |

재개 시 기존 미완료 알림을 중단하고 보관 중이었다면 기본 목록으로 꺼낸다. 이전 회고를 새 Resolution에 옮기지 않으며 정정 편집 취소로 이전 draft 전체를 복원하는 기능은 없다.

## 비교 상태와 고정본

조건 변경은 같은 후보를 유지하고 미달·미확인을 우선 표시한다. 후보 보류는 비교 표시에서 잠시 빼는 동작으로 후보·평가 입력을 삭제하지 않으며 점수 평가에서 자동 제외하지 않는다. 직접 확인은 해당 후보·기능·플랫폼에만 적용되고 해제하면 수집 근거로 돌아간다. Android 확인을 iOS로 확장하지 않는다.

조건 저장·초안 갱신은 최신 expectedVersion으로 수행한다. 저장 충돌이나 polling이 저장 전 입력을 덮어쓰지 않게 한다. 신규 Resolution은 조건·후보·기준·입력·사양·직접 확인·판정을 고정한다. comparison-v2는 당시 제목도 보존한다. 고정본이나 제목이 없는 과거 기록을 현재 데이터로 소급 채우지 않는다.

## 기록 보관·삭제 수명

archivedAt, deletedAt, personalizationEnabled는 진행 상태와 독립적이다. 완료 결정을 보관해도 판단 결과와 활용 설정은 유지한다. 삭제 시 즉시 일반 조회·평가·worker·개인화에서 제외하고 30일 안에는 삭제 전 상태·보관 여부·활용 설정으로 복구한다. 기한 경과 후 원본·종속 기록·원본 성향을 연쇄 영구 삭제한다.

다른 결정에 이미 복사한 값, 공유 카탈로그, 계정은 제거하지 않는다. 삭제와 외부 작업 완료·알림은 부모 잠금으로 순서를 보장하지만 이미 외부 전송한 요청·메일은 취소하지 않는다. 백업 수명과 복원 공개 조건은 [ADR 0020](../decisions/0020-decision-retention-and-personalization.md)을 따른다.

## 템플릿과 초기값

템플릿은 공유된 초기 기준·조건 생성 규칙이며 기존 Decision과 Evaluation을 소급 변경하지 않는다. 평가 templateVersion과 추천 intake templateVersion은 별개다.

| templateKey/version | 초기 비교 기준과 조건 |
|---|---|
| LAPTOP/laptop-v2 | 가격·무게·배터리·메모리·스토리지 numeric과 성능·디스플레이 rating. 예산·메모리·스토리지·무게·호환성 조건은 기본 비활성이다. |
| PHONE/phone-v1, TABLET/tablet-v1, MONITOR/monitor-v1 | 가격 numeric과 성능·휴대성·배터리·디스플레이·생태계 rating을 공유한다. 예산·호환성 조건은 기본 비활성이다. |
| EARBUDS/earbuds-v1 | 가격(KRW, best 50,000 / worst 500,000), 음질·착용감·노이즈 캔슬링·배터리 적합도 rating. 기본 weight는 1, 예산·호환성 조건은 비활성이다. |
| CUSTOM/custom-v1 | 초기 기준·조건을 자동 추가하지 않는다. |

rating은 1-5의 예상 만족도이며 사양으로 자동 확정하지 않는다. 추천에서 최대 예산을 선택하면 해당 constraint를 활성화한다. 새 결정 생성 시 확인 성향은 [개인화](outcome-personalization.md)의 규칙으로 기본 weight에 반영한다. 전체 anchor 수치는 아래 factory를 확인한다.

## 규칙·구현·검증

- 규칙: [BR-DEC-01-06, BR-CMP-01-02, BR-LIST-01, BR-DELETE-01, BR-TPL-01](../business-rules.md), [ADR 0017](../decisions/0017-comparison-and-resolution-context.md), [ADR 0021](../decisions/0021-decision-completion-and-revisions.md).
- 구현: [Decision](../../backend/src/main/java/com/decisionassistant/decision/Decision.java), [DecisionService](../../backend/src/main/java/com/decisionassistant/decision/DecisionService.java), [Comparison](../../backend/src/main/java/com/decisionassistant/decision/Comparison.java), [DecisionRetentionService](../../backend/src/main/java/com/decisionassistant/decision/DecisionRetentionService.java), [DecisionTemplateFactory](../../backend/src/main/java/com/decisionassistant/template/DecisionTemplateFactory.java).
- 검증: `DecisionApiIntegrationTest`, `frontend/e2e/comparison-flow.spec.ts`, `frontend/e2e/decision-revisions.spec.ts`, `frontend/e2e/decision-list.spec.ts`.
