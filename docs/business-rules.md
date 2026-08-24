# Business rules

이 문서는 MVP의 사용자에게 관찰 가능한 도메인 동작을 정의한다. `domain.md`의 용어와 aggregate 모델을 보완하며, API·UI·backend 구현·테스트는 아래 규칙을 따라야 한다.

규칙을 변경할 때는 이 문서, 영향받는 API contract, 그리고 해당 규칙을 검증하는 테스트를 함께 갱신한다. 구현 세부사항(저장 방식, 오류 문구, private method)은 이 문서의 대상이 아니다.

## 규칙 읽는 법

- 식별자(`BR-*`)는 변경 논의, PR 설명, 테스트에서 사용하는 안정적인 참조다.
- **동작**은 반드시 지켜야 하는 결과, **이유**는 제품 의도다.
- 이 문서가 제품 범위와 충돌하면 `product.md`의 범위 결정을 우선하고, 장기간 유지할 구조적 trade-off는 ADR로 남긴다.

## Decision과 결과 기록

### BR-DEC-01: Decision은 versioned template의 draft로 시작한다

- **동작:** 새 Decision은 선택한 template의 현재 version을 보존하고 `DRAFT` 상태로 시작한다.
- **이유:** 나중에 template이 바뀌어도 기존 결정의 출발점을 해석할 수 있어야 한다.

### BR-DEC-02: 수정은 최신의 수정 가능한 draft에만 적용한다

- **동작:** draft 갱신은 `expectedVersion`이 현재 version과 일치할 때만 허용한다. `DRAFT`와 `DEFERRED` 상태만 수정할 수 있으며, `CHOSEN`과 `ABANDONED` 상태의 draft는 수정하지 않는다.
- **이유:** 동시 편집으로 인한 덮어쓰기와, 이미 기록된 결정의 사후 변경을 막는다.

### BR-DEC-03: Decision 내부 참조와 조합은 일관돼야 한다

- **동작:** OptionValue는 같은 Decision의 Option과 Criterion을, ConstraintResult는 같은 Decision의 Option과 Constraint를 참조해야 한다. 각 Option–Criterion 및 Option–Constraint 조합은 하나만 존재할 수 있다.
- **이유:** 입력과 평가 근거가 다른 결정이나 중복 행으로 오염되지 않게 한다.

### BR-DEC-04: Resolution은 실제 결정 상태를 정확히 표현한다

- **동작:** `CHOSEN` resolution은 해당 Decision에 존재하는 selected option을 반드시 가진다. `DEFERRED`와 `ABANDONED` resolution은 selected option을 가지지 않는다. resolution의 종류에 따라 Decision 상태를 각각 `CHOSEN`, `DEFERRED`, `ABANDONED`로 기록한다.
- **이유:** “선택”, “보류”, “포기”를 추천 결과와 구분해 실제 의사결정 사실로 남긴다.

### BR-DEC-05: Outcome은 선택 후에만 누적한다

- **동작:** outcome check-in은 `CHOSEN` 상태의 Decision에만 추가할 수 있으며, 여러 번의 check-in을 시간순 기록으로 보존한다.
- **이유:** 실제 선택과 사후 결과를 연결하면서, 아직 선택되지 않은 후보에 대한 임의의 outcome 기록을 막는다.

## 평가와 constraint

### BR-EVAL-01: 평가에는 완전한 비교 입력이 필요하다

- **동작:** 평가는 Option이 2~5개이고, 활성 Criterion이 하나 이상이며, 모든 Option에 모든 활성 Criterion의 값이 있을 때만 실행한다. 활성 Criterion의 weight는 양수여야 한다.
- **동작:** numeric Criterion에는 서로 다른 worst/best anchor가 필요하다. `HIGHER_BETTER`에서는 best가 worst보다 크고, `LOWER_BETTER`에서는 best가 worst보다 작아야 한다. numeric 값과 1~5 rating은 Criterion 종류에 맞게 제공해야 한다.
- **이유:** 모르는 값을 중립값으로 치환하지 않고, 비교 가능한 입력만 평가한다.

### BR-EVAL-02: Constraint는 점수와 별개의 통과 조건이다

- **동작:** 활성 Constraint는 모든 Option에 대해 `PASS` 또는 `FAIL`로 결정되어야 하며 `UNKNOWN`이면 평가를 막는다. `NUMERIC_MIN`과 `NUMERIC_MAX`는 연결된 numeric Criterion과 threshold로 상태를 계산한다. `BOOLEAN_REQUIRED`와 `MANUAL`은 사용자가 기록한 ConstraintResult로 상태를 확인한다.
- **동작:** `FAIL`인 Constraint가 하나라도 있는 Option은 infeasible이며 algorithm 추천 대상에서 제외한다. 점수가 높아도 이를 뒤집지 않는다.
- **이유:** hard requirement를 soft preference의 가중치나 점수로 희석하지 않는다.

### BR-EVAL-03: Algorithm 결과는 재현 가능하고 설명 가능해야 한다

- **동작:** numeric 값은 worst/best anchor 구간을 0~1 utility로 정규화하고, rating은 1~5를 0~1 utility로 정규화한다. utility는 anchor 바깥의 값도 0~1 범위로 제한한다. 각 Criterion의 기여도는 weight 합계로 정규화해 계산하고 결과에 표시한다.
- **동작:** feasible Option 중 가장 높은 score를 recommendation으로 제시한다. estimated value를 사용한 Option은 결과에서 estimated로 표시한다. feasible Option이 없으면 recommendation을 제시하지 않는다.
- **이유:** 사용자가 점수 차이와 불확실성, constraint 위반을 함께 이해할 수 있어야 한다.

### BR-EVAL-04: 평가는 당시의 immutable snapshot을 기준으로 보존한다

- **동작:** 평가는 최신 `expectedVersion`으로 만든 input snapshot과 hash, deterministic algorithm result를 저장한다. 이후 Decision draft가 변경돼도 과거 evaluation은 재계산하거나 변경하지 않는다.
- **이유:** 과거 추천과 실제 선택·outcome을 같은 결정 시점의 근거로 비교할 수 있어야 한다.

## AI 경계

### BR-AI-01: AI 호출에는 명시적 동의와 provider별 데이터 확인이 필요하다

- **동작:** structure proposal, LLM assessment, assessment retry는 명시적 AI 전송 동의가 있어야 한다. AI 실행 시 전송되는 현재 Decision 정보와 source text를 사용자에게 고지한다. Gemini Developer API를 사용할 때는 개인 식별·금융·건강·비공개 업무 정보가 없음을 추가로 확인해야 한다.
- **이유:** 사용자가 외부 provider 전송 여부와 무료 티어의 데이터 사용 위험을 통제해야 한다.

### BR-AI-02: Structure proposal은 draft를 자동으로 바꾸지 않는다

- **동작:** AI는 Criterion·value·constraint 제안과 누락 정보·가정을 반환할 수 있지만, Decision draft를 자동 저장하거나 변경하지 않는다. 사용자가 제안을 검토·수정해 draft에 반영한다.
- **이유:** LLM의 추론과 추측이 사용자의 사실 입력 또는 의사결정을 덮어쓰지 않게 한다.

### BR-AI-03: LLM assessment는 algorithm과 독립적이다

- **동작:** 동의한 경우 LLM은 algorithm result를 받지 않고 동일한 immutable input snapshot만 평가한다. Algorithm과 LLM 결과는 임의의 하나의 점수로 결합하지 않으며, 불일치를 그대로 표시한다.
- **이유:** 서로 다른 오류 특성과 관점을 관찰하고, outcome 데이터 없이 근거 없는 결합 weight를 도입하지 않는다.

### BR-AI-04: AI 실패는 deterministic 결과를 무효화하지 않는다

- **동작:** LLM assessment가 실패하거나 비활성화돼도 algorithm result는 유지한다. LLM이 보류(abstain)하면 option을 추천할 수 없으며, 추천할 때는 snapshot에 존재하는 Option만 지정할 수 있다. 성공한 LLM assessment는 재시도하지 않는다.
- **이유:** 외부 AI의 가용성 또는 잘못된 응답이 재현 가능한 평가 결과를 훼손하지 않게 한다.

## Evidence와 MVP 경계

### BR-EVD-01: 사용자 제공 URL은 근거 참조일 뿐이다

- **동작:** Option과 value의 URL·source text는 사용자가 제공한 evidence reference로 저장한다. 서버는 URL을 자동으로 fetch하거나 가격·재고 등 외부 정보를 갱신하지 않는다.
- **이유:** 근거의 출처를 보존하면서도 자동 수집, 최신성 보장, SSRF 경로를 MVP 범위에 포함하지 않는다.

### BR-SCP-01: preference model과 자동 agent는 MVP 범위 밖이다

- **동작:** pairwise comparison, 확률 모델, 자동 preference learning, 범용 대화형 agent, LLM의 자동 draft 변경을 제공하지 않는다. outcome 데이터가 쌓인 뒤 학습 또는 Algorithm/LLM 결합을 검토할 때는 별도 제품 결정과 ADR가 필요하다.
- **이유:** 현재 MVP는 사용자의 명시적 비교와 실제 outcome 기록을 축적·검증하는 단계다.

### BR-SCP-02: Gemini 무료 티어는 저민감 dogfooding에만 사용한다

- **동작:** Gemini Developer API 무료 티어에는 제품 정보와 일반 구매 메모만 보낸다. 개인 식별 정보, 연락처·주소·주문 번호, 인증·금융 정보, 건강 정보, 비공개 업무·고객 정보는 입력하거나 전송하지 않는다.
- **이유:** 무료 티어에서는 전송 콘텐츠가 provider 제품 개선에 사용될 수 있다. 민감 데이터 처리나 production 배포에는 별도 retention/delete 정책과 provider 검토가 필요하다.

## 현재 검증 상태

이 표는 규칙의 전체 테스트 명세가 아니라, 현재 자동 검증의 시작점이다. 규칙을 변경하면 영향을 받는 행의 테스트를 추가하거나 보강한다.

| 규칙 | 현재 자동 검증 |
| --- | --- |
| BR-EVAL-01 | `SnapshotFactoryTest.blocksEvaluationWhenAnActiveValueIsMissing` |
| BR-EVAL-02, BR-EVAL-03 | `AlgorithmEvaluatorTest.appliesConstraintsAnchorsWeightsAndEstimatedFlag`, `AlgorithmEvaluatorTest.clampsNumericUtilityOutsideAnchors` |
| BR-EVAL-04 | `DecisionApiIntegrationTest.savesCompleteDraftAndCreatesReproducibleAlgorithmEvaluation` |
| BR-AI-01, BR-SCP-02 | `AiDataPolicyTest`, `DecisionApiIntegrationTest.bootsMigratesDatabaseAndCreatesLaptopDecision` |
| BR-DEC-01~05, BR-AI-02~04, BR-EVD-01, BR-SCP-01 | 명시적 규칙 단위 테스트 연결이 아직 없다. 해당 동작을 변경할 때 추가한다. |

## 아직 정하지 않은 동작

- 동점인 feasible Option의 추천·표시 정책
- `ARCHIVED` 상태로 전이하는 조건과 그 이후 허용 동작

이 항목은 현재 구현 세부 동작에 의존하지 않는다. 제품 또는 API에서 의미 있는 차이를 만들게 되면 먼저 규칙을 결정하고 이 문서를 갱신한다.
