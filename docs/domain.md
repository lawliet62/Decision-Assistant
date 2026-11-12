# Domain model

## 사전 검토 제품 목록

`CatalogProduct`는 운영자가 등록한 공개 제품·공식 사양·최근 판매 관측이다. 사용자와 무관한 공유 데이터이며 `Decision`의 소유권 경계를 확장하지 않는다. 사양의 출처·인용·확인 시각·점검 주기, 모델 identity와 등록 revision을 보존한다. `catalog_observations`와 `catalog_monthly_usage`는 고정 URL 수집 결과와 호출 한도를 기록하며 사용자 조건을 저장하지 않는다.

후보 생성 시 근거를 개인 `CandidateSuggestion`으로 복사한다. 공용 정보 갱신은 이미 선택한 Option·확인값·Evaluation을 변경하지 않는다. 등록 사양도 `UNREVIEWED`이며 사용자 확인 사실과 다르다. [정보 수명과 운영](catalog-operations.md)을 따른다.

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
- ProductDiscovery: 선택 조건·전송 동의·비동기 추천 상태·실행 metadata를 Decision에 귀속해 보존한다. 후보 0개에서 시작할 수 있다.
- CandidateSuggestion: 검색으로 찾은 후보이며 승인 전에는 Option이 아니다. 새 추천은 주요 사양·원문 인용·조회 시점을 포함하고 승인 시 Option에 연결한다.
- ProductFact: 출처가 있는 표시용 사양이다. 평가용 OptionValue와 분리하고 자동 값은 UNREVIEWED로 표시한다.
- MinimumRequirement: intake-v2에서 선택한 객관적 사양 하한·지원 요구다. 기존 hard Constraint와 별개로 추천·사양 비교에만 적용한다.
- RequirementCheck: 선택 조건과 표시 사양으로 계산한 MEETS/BELOW/UNKNOWN 및 근거다. 검증 완료나 평가 탈락을 뜻하지 않으며, 사용자가 확인 수치를 변경하면 화면과 함께 다시 계산한다.
- Product lookup result: 제품명에 대한 일시적인 웹 검색 결과. 저장된 CandidateSuggestion이나 제품 catalog가 아니며 선택한 이름·URL만 기존 Option으로 연결한다. 검색 발췌는 확인된 OptionValue가 아니다.
- Evaluation: 특정 Decision version의 재현 가능한 run. 현재 draft를 참조해 재계산하지 않는다.
- Comparison.State: 최초 추천과 독립적인 현재 비교 조건, 비교 보류 후보, 직접 확인한 ANC·플랫폼별 호환성이다. 추천 조건·점수·공용 카탈로그를 변경하지 않는다.
- Resolution: 사용자가 선택·보류·포기한 사실과 이유. 신규 기록은 당시 조건·후보·입력·근거·판정의 immutable comparison snapshot을 함께 가진다. 이전 기록의 snapshot은 없을 수 있다.
- OutcomeCheckIn: 시간이 지난 뒤 만족도와 기대 충족 여부를 여러 번 기록한다.
- PreferenceMemory: resolution/outcome에서 제안된 category별 성향 projection이다. `PROPOSED`는 평가에 쓰지 않고 사용자가 확인한 `CONFIRMED`만 다음 Decision의 명시적 기본값이 된다.
- OutcomeReminder: 선택 후 14일·60일 회고를 앱 또는 이메일로 요청하는 일정이다.
- ApplicationUser: Decision ownership의 안정적인 로컬 UUID. 외부 IdP 교체나 identity 추가가 domain FK를 바꾸지 않게 한다.
- ExternalIdentity: Auth0 issuer와 subject의 유일한 조합. 같은 이메일만으로 다른 identity를 자동 연결하지 않는다.

## Invariants

`EARBUDS/earbuds-v1`은 가격(KRW, 최선 50,000 / 최악 500,000)과 음질·착용감·노이즈 캔슬링·배터리 적합도(각 1~5점)를 기본 중요도 1로 시작한다. 네 rating은 사용자 만족 예상이며 사양에서 자동 확정하지 않는다. 최대 예산과 필수 호환성 constraint는 기본 비활성이다. 클릭 추천에서 사용자가 최대 예산을 선택하면 해당 constraint를 활성화한다. 기존 template/평가 snapshot은 변경하지 않는다.

- 평가에는 2~5개 Option, 하나 이상의 활성 Criterion, 모든 활성 cell 값이 필요하다.
- numeric Criterion에는 서로 다른 worst/best anchor가 필요하다.
- `HIGHER_BETTER`는 best가 worst보다 크고 `LOWER_BETTER`는 best가 worst보다 작아야 한다.
- 모든 weight는 양수이며 evaluator가 합계를 normalize한다.
- 활성 Constraint는 모든 Option에 대해 PASS/FAIL이 결정되어야 한다.
- estimated value는 허용하지만 result에 표시한다. missing은 허용하지 않는다.
- 불완전한 draft는 score range와 `PROVISIONAL` 조언을 제공할 수 있지만 immutable Evaluation으로 저장하지 않는다.
- `USER/CONFIRMED` 값은 background import나 AI proposal이 덮어쓰지 않는다.
- Resolution의 selected Option은 해당 Decision에 존재해야 한다.
- 직접 확인한 기능은 해당 후보·기능의 비교 판정에만 우선 적용하며 background import가 덮어쓰지 않는다. 조건 변경은 이미 저장된 Resolution snapshot을 재계산하지 않는다.
- 사용자 resource는 소유한 ApplicationUser만 읽고 변경할 수 있다.
