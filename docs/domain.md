# Domain model

## Aggregate와 lifecycle

`Decision`이 수정 가능한 aggregate root다. Context, Option, Criterion, OptionValue, Constraint, ConstraintResult를 소유하며 optimistic `version`을 가진다.

`ApplicationUser`는 외부 인증 identity와 domain ownership을 분리하는 로컬 사용자다. Auth0 `(issuer, subject)`는 `ExternalIdentity`로 연결하며 이메일은 identity key로 사용하지 않는다. Decision만 `ownerUserId`를 직접 가지고 Evaluation, StructureRun, Resolution, OutcomeCheckIn은 자신의 `decisionId`를 통해 같은 ownership을 상속한다. Template은 모든 사용자에게 공통이다.

사용자에게 관찰 가능한 상태 전이·평가·AI 경계 규칙은 [Business rules](business-rules.md)에서 관리한다.

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
- EvidenceImport: 사용자가 승인한 공개 URL의 비동기 수집 상태와 조회 시점·hash·짧은 excerpt를 보존한다. 원문 전체는 장기 저장하지 않는다.
- DecisionQuestion: 미확인 정보 중 결론에 영향이 큰 질문과 답변 상태다.
- CandidateSuggestion: 검색으로 찾은 대안이며 사용자가 승인하기 전에는 Option이 아니다.
- Evaluation: 특정 Decision version의 재현 가능한 run. 현재 draft를 참조해 재계산하지 않는다.
- Resolution: 사용자가 선택·보류·포기한 사실과 이유.
- OutcomeCheckIn: 시간이 지난 뒤 만족도와 기대 충족 여부를 여러 번 기록한다.
- PreferenceMemory: resolution/outcome에서 제안된 category별 성향 projection이다. `PROPOSED`는 평가에 쓰지 않고 사용자가 확인한 `CONFIRMED`만 다음 Decision의 명시적 기본값이 된다.
- OutcomeReminder: 선택 후 14일·60일 회고를 앱 또는 이메일로 요청하는 일정이다.
- ApplicationUser: Decision ownership의 안정적인 로컬 UUID. 외부 IdP 교체나 identity 추가가 domain FK를 바꾸지 않게 한다.
- ExternalIdentity: Auth0 issuer와 subject의 유일한 조합. 같은 이메일만으로 다른 identity를 자동 연결하지 않는다.

## Invariants

- 평가에는 2~5개 Option, 하나 이상의 활성 Criterion, 모든 활성 cell 값이 필요하다.
- numeric Criterion에는 서로 다른 worst/best anchor가 필요하다.
- `HIGHER_BETTER`는 best가 worst보다 크고 `LOWER_BETTER`는 best가 worst보다 작아야 한다.
- 모든 weight는 양수이며 evaluator가 합계를 normalize한다.
- 활성 Constraint는 모든 Option에 대해 PASS/FAIL이 결정되어야 한다.
- estimated value는 허용하지만 result에 표시한다. missing은 허용하지 않는다.
- 불완전한 draft는 score range와 `PROVISIONAL` 조언을 제공할 수 있지만 immutable Evaluation으로 저장하지 않는다.
- `USER/CONFIRMED` 값은 background import나 AI proposal이 덮어쓰지 않는다.
- Resolution의 selected Option은 해당 Decision에 존재해야 한다.
- 사용자 resource는 소유한 ApplicationUser만 읽고 변경할 수 있다.
