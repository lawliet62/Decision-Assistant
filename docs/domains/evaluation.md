# 평가

[전체 흐름](../domain.md) · [화면 모음](../ui/README.md)

## 책임과 범위

특정 Decision version의 입력으로 재현 가능한 결정론적 평가를 만들고 선택적으로 대규모 언어 모델(LLM)의 독립 평가를 함께 보존한다. 표시 사양의 조건 판정이나 실제 선택을 대신하지 않으며 점수 평가 없이도 비교·결정 완료가 가능하다.

## 모델과 관계

| 모델 | 의미 있는 속성과 관계 |
|---|---|
| DecisionSnapshot | Decision ID·version, 후보·기준·값·필수 조건 등 실행 당시 입력이다. |
| Evaluation | decisionId로 소유권을 따르고 decisionVersion·inputSnapshot·inputHash·algorithmResult를 보존한다. |
| AlgorithmResult | 후보별 utility·가중 기여도·점수·feasibility와 추천·설명을 담는다. |
| llmStatus/llmResult | 동일 snapshot의 독립 LLM 상태·결과다. provider·model·prompt/schema version·지연·token metadata를 남긴다. |
| StructureRun | 현재 Decision 정보·source text를 구조화하기 위한 별도 실행이다. 입력 snapshot·proposal·상태·metadata를 보존하며 Evaluation 점수가 아니다. |

Evaluation과 Resolution은 서로 다른 고정본이다. Evaluation은 평가 입력·결과를, Resolution은 판단 시점의 비교 조건·근거까지 보존한다. 사용자가 알고리즘 추천과 다른 후보를 선택할 수 있다.

과거 판단의 선택적 AI 요약·현재 조건에 따른 설명은 Evaluation이나 StructureRun과 별개인 [MemoryInsight](history-reuse.md)다. 과거 기록 하나를 해석하고 현재 메모에 선택 적용하며 평가 점수·weight·사양을 자동 변경하지 않는다.

## 입력 준비와 계산

1. 최신 expectedVersion의 수정 가능한 Decision인지 확인한다.
2. Option 2-5개, 활성 Criterion 1개 이상, 양수 weight, 모든 활성 cell 값을 검증한다.
3. numeric anchor가 서로 다르고 방향에 맞는지 확인한다. HIGHER_BETTER는 best > worst, LOWER_BETTER는 best < worst다. rating은 1-5다.
4. 활성 Constraint의 모든 후보가 PASS/FAIL이어야 한다. UNKNOWN은 평가를 막고 하나라도 FAIL인 후보는 infeasible로 추천에서 제외한다.
5. numeric은 anchor 구간, rating은 1-5를 0-1 utility로 정규화하고 범위 밖은 제한한다. weight 합계로 정규화한 기여도를 합산한다.
6. snapshot·hash·algorithm result를 저장한다. feasible 후보가 없으면 추천하지 않고 estimated 입력 사용은 결과에 표시한다.

누락값을 중립값으로 채우지 않는다. 불완전한 draft에는 missing utility를 0-1로 둔 score range와 PROVISIONAL 조언을 제공할 수 있지만 확정 Evaluation으로 저장하지 않는다. MinimumRequirement의 MEETS/BELOW/UNKNOWN은 Constraint의 PASS/FAIL/UNKNOWN과 별개다.

## 실행 생명주기와 AI 경계

| 실행 | 상태·실패 동작 |
|---|---|
| 결정론적 평가 | 동기 실행을 먼저 완료해 저장한다. 외부 AI 실패가 결과를 무효화하지 않는다. |
| LLM 미요청 | NOT_REQUESTED. 동의 없이 호출하지 않는다. |
| LLM 요청 | `PENDING → SUCCEEDED / FAILED`. 실패는 명시적 동의 후 재시도할 수 있고 성공한 평가는 재시도하지 않는다. |
| 구조화 실행 | `StructureRun: PENDING → SUCCEEDED / FAILED`. proposal은 자동으로 사용자 확인 사실이나 완료 기록을 변경하지 않는다. |

LLM에는 같은 input snapshot만 전달하며 algorithm result를 전달하지 않는다. 두 결과는 한 점수로 합치지 않고 불일치를 표시한다. LLM 추천은 snapshot에 존재하는 후보만 지정할 수 있으며 abstain이면 후보를 추천할 수 없다. 구조화 AI에는 현재 활성 기준 이름·타입·단위를 전달하되 기준 목록이 없는 과거 snapshot도 유지한다.

원본 draft가 바뀌어도 과거 평가 입력·알고리즘 결과를 다시 계산하지 않는다. LLM 상태·결과 기록은 별도 후속 처리이며 삭제와 경합할 때 부모 존재·삭제 상태를 재확인한다. 구조화·평가·재시도에는 AI 동의가 필요하고 Gemini 무료 티어는 민감 정보 비포함 확인이 추가로 필요하다.

## 규칙·구현·검증

- 규칙: [BR-EVAL-01-04, BR-AI-01-04](../business-rules.md), [ADR 0002](../decisions/0002-independent-evaluation-snapshots.md).
- 구현: [AlgorithmEvaluator](../../backend/src/main/java/com/decisionassistant/evaluation/AlgorithmEvaluator.java), [ReadinessAnalyzer](../../backend/src/main/java/com/decisionassistant/evaluation/ReadinessAnalyzer.java), [EvaluationService](../../backend/src/main/java/com/decisionassistant/evaluation/EvaluationService.java), [StructureService](../../backend/src/main/java/com/decisionassistant/ai/StructureService.java), [DecisionTaskWriter](../../backend/src/main/java/com/decisionassistant/decision/DecisionTaskWriter.java).
- 검증: `AlgorithmEvaluatorTest`, `DecisionApiIntegrationTest`, `AiDeletionIntegrationTest`, `frontend/e2e/guided-decision.spec.ts`.
