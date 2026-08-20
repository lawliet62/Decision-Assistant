# Domain model

## Aggregate와 lifecycle

`Decision`이 수정 가능한 aggregate root다. Context, Option, Criterion, OptionValue, Constraint, ConstraintResult를 소유하며 optimistic `version`을 가진다.

```text
Decision draft ──evaluate──> Evaluation(snapshot + results)
       │
       └──resolve──> Resolution ──check in──> OutcomeCheckIn(s)
```

## 핵심 용어

- Context: 목적·예산·자유 메모. 독립 entity가 아니라 Decision의 value다.
- Option: 사용자가 실제로 선택 가능한 후보.
- Criterion: 모든 Option을 비교하는 soft preference. numeric utility 또는 1~5 rating을 사용한다.
- Constraint: 후보를 feasible/infeasible로 가르는 hard requirement. 점수 가중치와 다르다.
- Evidence: 현재 별도 entity가 아니다. Option source text/URL과 value source note/URL로 보존한다.
- Evaluation: 특정 Decision version의 재현 가능한 run. 현재 draft를 참조해 재계산하지 않는다.
- Resolution: 사용자가 선택·보류·포기한 사실과 이유.
- OutcomeCheckIn: 시간이 지난 뒤 만족도와 기대 충족 여부를 여러 번 기록한다.
- User Preference: MVP에서는 독립 모델이 아니다. outcome 데이터가 충분해질 때 projection/learning model로 검토한다.

## Invariants

- 평가에는 2~5개 Option, 하나 이상의 활성 Criterion, 모든 활성 cell 값이 필요하다.
- numeric Criterion에는 서로 다른 worst/best anchor가 필요하다.
- `HIGHER_BETTER`는 best가 worst보다 크고 `LOWER_BETTER`는 best가 worst보다 작아야 한다.
- 모든 weight는 양수이며 evaluator가 합계를 normalize한다.
- 활성 Constraint는 모든 Option에 대해 PASS/FAIL이 결정되어야 한다.
- estimated value는 허용하지만 result에 표시한다. missing은 허용하지 않는다.
- Resolution의 selected Option은 해당 Decision에 존재해야 한다.
