# Business rules

이 문서는 MVP의 사용자에게 관찰 가능한 도메인 동작을 정의한다. `domain.md`의 용어와 aggregate 모델을 보완하며, API·UI·backend 구현·테스트는 아래 규칙을 따라야 한다.

규칙을 변경할 때는 이 문서, 영향받는 API contract, 그리고 해당 규칙을 검증하는 테스트를 함께 갱신한다. 구현 세부사항(저장 방식, 오류 문구, private method)은 이 문서의 대상이 아니다.

## 규칙 읽는 법

- 식별자(`BR-*`)는 변경 논의, PR 설명, 테스트에서 사용하는 안정적인 참조다.
- **동작**은 반드시 지켜야 하는 결과, **이유**는 제품 의도다.
- 이 문서가 제품 범위와 충돌하면 `product.md`의 범위 결정을 우선하고, 장기간 유지할 구조적 trade-off는 ADR로 남긴다.

## Decision과 결과 기록

### BR-LIST-01: 결정 목록은 소유자 범위에서 분류·정렬·페이지 처리한다

- **동작:** 상태 그룹은 기본으로 접고 건수를 표시한다. 복수 상태 선택과 그룹별 페이지 이동을 제공하며 기본 10개·수정일 최신순이다. 생성일·제목·제품군 정렬도 지원하고 동일 값은 ID로 안정적으로 정렬한다. 필터·정렬 변경은 페이지를 초기화하고 상세에서 돌아오면 탐색 상태를 복원한다.
- **이유:** 결정이 늘어도 불필요한 전체 다운로드와 화면 혼잡 없이 필요한 기록을 찾게 한다.

### BR-DELETE-01: 결정 삭제는 원본과 종속 기록을 실제로 제거한다

- **동작:** 소유자가 최신 version으로 확인하면 결정과 종속 데이터·원본 결정에서 생성된 성향 기억을 DB에서 영구 삭제한다. 공유 카탈로그·계정·다른 결정에 복사된 값은 유지한다. 삭제 뒤 늦은 결과가 결정을 재생성하거나 새 알림을 보내지 않는다. 이미 외부로 전송된 요청·메일은 취소하지 않는다.
- 복수 선택은 페이지 이동에도 유지하며 상태 그룹의 현재 페이지 또는 해당 상태의 모든 페이지를 선택할 수 있다. 표시 상태 전체 선택도 제공한다. 전체 선택은 선택 시점의 결정과 version을 확정하며 이후 새로 해당 상태에 들어온 결정은 포함하지 않는다. 일괄 삭제는 성공분을 처리하고 충돌·실패 항목은 이유와 함께 남긴다.
- **이유:** 화면 숨김·보관과 데이터 삭제를 구분하고 사용자의 기록 통제권을 보장한다. 복구와 백업의 경계는 [ADR 0018](decisions/0018-decision-management-and-deletion.md)을 따른다.

### BR-AUTH-01: 초대되고 검증된 사용자만 로그인한다

- **동작:** Auth0가 제공한 verified email이 배포의 exact-email allowlist에 포함될 때만 로컬 사용자를 생성하고 접근을 허용한다. 같은 이메일의 다른 issuer/subject identity는 자동 병합하지 않는다.
- **이유:** 초기 dogfooding의 사용자·AI 비용 범위를 제한하고 이메일 일치만으로 다른 identity가 기존 데이터 ownership을 가져가지 못하게 한다.

### BR-OWN-01: 사용자 데이터는 소유자에게만 보인다

- **동작:** Decision과 이에 연결된 Evaluation, StructureRun, Resolution, OutcomeCheckIn은 Decision 소유자만 조회·변경할 수 있다. 다른 사용자의 resource identifier에는 `404`로 응답한다.
- **이유:** UUID 노출 여부와 무관하게 개인 의사결정 내용과 AI snapshot을 사용자 사이에서 격리하고 resource 존재 여부도 공개하지 않는다.

### BR-DEC-01: Decision은 versioned template의 draft로 시작한다

- **동작:** 새 Decision은 선택한 template의 현재 version을 보존하고 `DRAFT` 상태로 시작한다.
- **이유:** 나중에 template이 바뀌어도 기존 결정의 출발점을 해석할 수 있어야 한다.

### BR-DEC-02: 수정은 최신의 수정 가능한 draft에만 적용한다

- **동작:** draft 갱신은 `expectedVersion`이 현재 version과 일치할 때만 허용한다. `DRAFT`와 `DEFERRED` 상태만 수정할 수 있으며, `CHOSEN`과 `ABANDONED` 상태의 draft는 수정하지 않는다.
- **이유:** 동시 편집으로 인한 덮어쓰기와, 이미 기록된 결정의 사후 변경을 막는다.
- **동작:** 비교 값을 편집하는 동안 화면의 자동 갱신은 저장 전 입력을 덮어쓰지 않는다. 저장 충돌 시 입력을 보존하고 사용자가 최신 내용을 다시 불러올 수 있다.

### BR-DEC-03: Decision 내부 참조와 조합은 일관돼야 한다

- **동작:** OptionValue는 같은 Decision의 Option과 Criterion을, ConstraintResult는 같은 Decision의 Option과 Constraint를 참조해야 한다. 각 Option–Criterion 및 Option–Constraint 조합은 하나만 존재할 수 있다.
- **이유:** 입력과 평가 근거가 다른 결정이나 중복 행으로 오염되지 않게 한다.

### BR-DEC-04: Resolution은 실제 결정 상태를 정확히 표현한다

- **동작:** `CHOSEN` resolution은 해당 Decision에 존재하는 selected option을 반드시 가진다. `DEFERRED`와 `ABANDONED` resolution은 selected option을 가지지 않는다. resolution의 종류에 따라 Decision 상태를 각각 `CHOSEN`, `DEFERRED`, `ABANDONED`로 기록한다.
- **이유:** “선택”, “보류”, “포기”를 추천 결과와 구분해 실제 의사결정 사실로 남긴다.

### BR-DEC-05: Outcome은 선택 후에만 누적한다

- **동작:** outcome check-in은 `CHOSEN` 상태의 Decision에만 추가할 수 있으며, 여러 번의 check-in을 시간순 기록으로 보존한다.
- **이유:** 실제 선택과 사후 결과를 연결하면서, 아직 선택되지 않은 후보에 대한 임의의 outcome 기록을 막는다.

### BR-CMP-01: 같은 후보의 비교 조건을 변경하고 확인 근거를 구분한다

- **동작:** 이어폰 비교의 예산·ANC·기본 연결 호환성을 바꿔도 후보와 최초 추천 조건을 유지한다. 선택한 필수 조건의 미달·미확인을 우선 표시하고 모든 비교 후보가 충족하는 조건은 접는다. 우선 표시 정보는 새 점수가 아니며 기존 Criterion 중요도는 기존 평가에만 적용한다.
- **동작:** 직접 확인한 기능은 후보와 기능별로 보존하고 해당 판정에 우선 사용한다. Android 확인을 iOS에 적용하지 않으며, 수집 근거는 별도로 남긴다. 확인값 해제는 수집 근거로 돌아가는 동작이다. 누락·모호·상충은 자동으로 지원이나 미지원으로 바꾸지 않는다.
- **동작:** 후보 보류는 비교 표시에서 잠시 빼는 동작이다. 후보·입력·확인 기록을 삭제하거나 점수 평가에서 자동 제외하지 않으며 복원할 수 있다. 저장 실패·충돌 시 입력을 유지한다.
- **동작:** 가격 차이는 유효한 수집 KRW 가격 또는 직접 확인한 가격끼리 계산한다. 더 비싼 후보의 MEETS와 기준 후보의 BELOW가 확인된 조건만 지원 차이로 설명한다. UNKNOWN을 기능 없음으로 해석하지 않는다.
- **이유:** 카탈로그의 모든 누락을 보완하지 않아도 근거의 한계를 알고 비교할 수 있게 한다.

### BR-CMP-02: 결정 당시의 근거는 이후 변경으로 다시 쓰지 않는다

- **동작:** 신규 선택·보류·포기 기록은 당시 조건, 후보, 평가 입력과 기준, 수집 사양, 직접 확인 및 미확인 판정을 보존한다. 나중의 조건 변경·수집·카탈로그 갱신·가격 만료는 이 기록을 변경하지 않는다. 고정본이 없는 과거 기록은 현재 자료로 소급 생성하지 않는다.
- **이유:** 사용자가 당시 어떤 정보를 알고 무엇을 감수했는지 다시 이해할 수 있어야 한다.

### BR-DEC-06: 선택 후 회고 기회를 만들고 사용자가 통제한다

- **동작:** `CHOSEN` 기록 시 기본 14일·60일 in-app/email reminder를 만든다. 사용자는 개별 reminder를 건너뛰거나 전체 reminder email을 해제할 수 있다. email 재시도는 reminder identifier로 중복 전송을 막는다.
- **이유:** 사용자의 자발적 재방문에만 의존하지 않고 outcome loop를 닫되 알림 통제권을 보장한다.

## 평가와 constraint

### BR-EVAL-01: 평가에는 완전한 비교 입력이 필요하다

- **동작:** 평가는 Option이 2~5개이고, 활성 Criterion이 하나 이상이며, 모든 Option에 모든 활성 Criterion의 값이 있을 때만 실행한다. 활성 Criterion의 weight는 양수여야 한다.
- **동작:** numeric Criterion에는 서로 다른 worst/best anchor가 필요하다. `HIGHER_BETTER`에서는 best가 worst보다 크고, `LOWER_BETTER`에서는 best가 worst보다 작아야 한다. numeric 값과 1~5 rating은 Criterion 종류에 맞게 제공해야 한다.
- **이유:** 모르는 값을 중립값으로 치환하지 않고, 비교 가능한 입력만 평가한다.
- **동작:** 완전하지 않은 draft에는 각 missing utility를 0~1 범위로 둔 score range와 `PROVISIONAL` 조언을 제공할 수 있다. 이는 Evaluation이나 확정 점수가 아니며 미확인 정보와 함께 표시한다.

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

### BR-AI-02: AI 초안은 사용자 확인 사실과 구분한다

- **동작:** AI와 web extraction은 origin과 `UNREVIEWED` 상태, 출처를 가진 값을 draft에 저장할 수 있다. `USER` 또는 `CONFIRMED` 값, Resolution, Outcome, 확인된 PreferenceMemory는 자동 변경하지 않는다.
- **이유:** 반복 입력은 줄이되 LLM의 추론과 추측이 사용자의 사실 또는 의사결정을 가장하지 않게 한다.

### BR-AI-03: LLM assessment는 algorithm과 독립적이다

- **동작:** 동의한 경우 LLM은 algorithm result를 받지 않고 동일한 immutable input snapshot만 평가한다. Algorithm과 LLM 결과는 임의의 하나의 점수로 결합하지 않으며, 불일치를 그대로 표시한다.
- **이유:** 서로 다른 오류 특성과 관점을 관찰하고, outcome 데이터 없이 근거 없는 결합 weight를 도입하지 않는다.

### BR-AI-04: AI 실패는 deterministic 결과를 무효화하지 않는다

- **동작:** LLM assessment가 실패하거나 비활성화돼도 algorithm result는 유지한다. LLM이 보류(abstain)하면 option을 추천할 수 없으며, 추천할 때는 snapshot에 존재하는 Option만 지정할 수 있다. 성공한 LLM assessment는 재시도하지 않는다.
- **이유:** 외부 AI의 가용성 또는 잘못된 응답이 재현 가능한 평가 결과를 훼손하지 않게 한다.

## Evidence와 MVP 경계

### BR-EVD-01: 사용자 동의로 공개 URL만 통제해 수집한다

- **동작:** 기존 guided 경로는 사용자가 제공하거나 후보 제안에서 승인한 공개 HTTP(S) URL을 수집한다. 클릭 추천은 별도 명시적 동의 후 검색된 공개 URL 최대 5개를 후보 선택 전에 managed provider로 수집한다. 인증 정보·cookie·private/local host·지속 crawling을 허용하지 않으며 원문 전체를 장기 저장하지 않는다. 값에는 URL과 조회 시점을 표시하고 수집 실패는 다른 후보를 훼손하지 않는다.
- **이유:** 입력 부담을 줄이면서 SSRF, 비공개 데이터 수집, 최신성 오인을 제한한다.
- **동작:** 일부 URL 수집이 재시도 후 실패해도 다른 URL의 성공한 근거는 구조화할 수 있다. 대기 중인 수집이 남아 있으면 수집 상태를 유지하고, 모두 실패하면 AI로 빈 근거를 채우지 않고 입력 보강으로 돌아간다.

### BR-EVD-02: 발견된 후보는 사용자가 승인해야 한다

- **동작:** 기존 경로는 후보가 하나 이상일 때 같은 범주의 대안을 최대 3개 제안한다. 클릭 추천은 후보 없이 시작하여 선택 조건과 수집 근거로 최대 5개를 제안한다. 발견 결과는 사용자가 승인하기 전에는 Option이나 Evaluation input이 아니다.
- **이유:** 검색 누락·광고성 결과·지역별 판매 차이가 사용자의 후보군을 몰래 바꾸지 않게 한다.

### BR-DISC-01: 선택 조건만으로 추천을 시작한다

기본 `intake-catalog-v1`에는 BR-CAT-01~03을 적용한다. 아래 외부 검색·전송 규칙은 기존 `intake-v1/v2` 호환 경로에 적용한다.

- **동작:** 지원하는 5개 제품군의 versioned intake template로 하나 이상의 용도·최대 예산·호환성을 선택한다. 선택한 용도는 하나의 추천에서 함께 고려하며 중복을 제거하고 템플릿 순서로 저장한다. 미정·제한 없음도 허용하며 자유 텍스트는 필수가 아니다. 조건·검색/수집/AI 동의·민감 정보 비포함 확인을 저장한다. 기존 동의는 새 경로를 허용하지 않는다.
- **동작:** 최초 추천은 Serper 조건 검색 1회, 공개 페이지 최대 5개 수집, AI 구조화 최대 1회로 제한한다. 검색은 기존 월·burst 한도를 공유한다. 메모·사용자 identity는 최초 추천 검색·AI 입력에서 제외한다.
- **동작:** 검색어는 공식 판매처 범위와 제품군 이름에 ANC·전용 펜 필수 선택의 기능 힌트만 더한다. 용도·예산·호환성·숫자형 최소 사양·UI 문구를 검색어에 나열하지 않는다. 전체 선택 조건은 저장·AI 입력·기존 서버 판정에 유지하며, 검색어에서 빠진 조건을 충족으로 간주하지 않는다.
- **동작:** 작업과 후보는 해당 Decision 소유자에게만 보인다. 일부 실패는 확보한 후보를 보존한다. 실패·부분 완료는 현재 작업 version으로 같은 추천 묶음을 재시도한다. 같은 조건으로 다른 제품 찾기는 별도 요청으로 새 묶음을 만든다. 진행 중인 중복 요청은 거절한다.
- **동작:** 추천 실패 안내는 검색 한도·페이지 수집·AI 정리·근거 부족·저장 실패를 구분한다. 공식 검색 결과·비교 가능한 가격이 없는 경우는 별도 빈 결과로 안내한다. 수집한 공식 상품·가격이 없으면 AI를 호출하지 않고, 일부 페이지 수집 실패는 나머지 근거로 추천을 계속한다. provider 원문 오류는 사용자에게 전달하지 않는다.
- **이유:** 타이핑 부담과 외부 호출 비용을 제한하고 새로고침·실패 후에도 비교를 이어간다.

### BR-DISC-02: 주요 정보와 평가값은 구분한다

- **동작:** 제품·사양은 수집한 페이지의 URL·원문 인용과 대조하고 값·통화·조회 시점을 표시한다. 제품명이 같은 인용에 있거나 해당 제품 제목 아래의 동일 근거 블록에 있는 사양만 허용한다. 사양은 미확인 또는 UNREVIEWED이며 OptionValue·만족도 rating을 자동 생성하지 않는다. 원문 전체는 저장하지 않는다.
- **동작:** `official-kr-v2`는 같은 상품에 연결된 구조화 속성·사양 표의 항목/값을 근거 블록으로 사용할 수 있다. 제품 식별·항목·인용을 서버가 대조하며 다른 제품·구성·리뷰의 근거는 제외한다. 분리된 단위와 기본 장착 사양의 정규화는 원문·정규화 버전을 함께 보존하고 UNREVIEWED를 유지한다.
- **동작:** 새 추천 설명은 최종 검증을 통과한 가격·사양·조건 판정으로 서버가 구성한다. 미달·미확인과 용도별 검증 한계를 알리며, 제거된 사양이나 AI의 자유 적합성 주장을 설명에 남기지 않는다.
- **동작:** 같은 비교 글이 여러 제품의 근거가 될 수 있다. 새 추천은 제품명 중복을 제한하고 승인 시 저장된 사양을 Option에 연결한다. 중복 승인으로 Option이 늘어나지 않으며 최대 5개를 유지한다.
- **동작:** 주요 정보 비교와 실제 선택은 점수 평가 없이 가능하다. 용도 선택은 weight를 변경하지 않으며 선택한 최대 예산은 context와 최대 예산 constraint에 반영한다. 기존 검증된 평가의 완전성 규칙은 유지한다.
- **이유:** 사용자가 객관적 정보를 직접 비교하면서 AI 추출·개인 만족도·재현 가능한 평가를 구별하게 한다.

### BR-DISC-03: 최소 사양은 근거와 함께 비교하고 불확실성을 보존한다

- **동작:** intake-v2는 5개 제품군에 선택형 최소 사양을 제공한다. 수치 하한·해상도·명시적 지원 여부만 판정하고 CPU 성능·음질·배터리 시간은 제외한다. 기본값은 조건 없음이다.
- **동작:** 최소 조건 중 ANC·전용 펜은 후보 발견용 검색 힌트로 사용하고, 전체 최소 조건은 AI 입력과 근거 기반 서버 판정에 보존한다. 호출 수·동의·quota·메모/identity 제외 정책은 유지하며 조건을 자동 완화하거나 재검색하지 않는다. v1 데이터와 재시도는 기존 조건을 유지한다.
- **동작:** 사양 누락·모호함·상충은 UNKNOWN이다. 단일 수치·명확한 단위와 지원 표현을 비교하며 저장 공간은 1TB=1,000GB, 해상도는 가로·세로 각각을 기준으로 한다. 자동 사양의 MEETS는 검증 완료가 아니며 UNREVIEWED를 유지한다.
- **동작:** UNKNOWN에는 근거 없음·표현 모호함·근거 상충의 사유를 표시한다. 명시적인 ANC 탑재·지원 표현과 제조사 공식 인용 `アクティブノイズキャンセリング搭載`을 해석하되 통화 소음 억제나 수동 차음을 ANC 지원으로 취급하지 않는다. 일본어 문구는 전체 인용이 정확히 일치할 때만 인정한다.
- **동작:** 명시적인 ANC 지원 근거에서 최대 감쇠량(`최대 50dB`, `up to 50 dB`)은 지원 여부의 불확실성과 구분하며 감쇠 성능은 판정하지 않는다. 이어폰의 별도 앱 조건 절은 기본 플랫폼 연결 지원 절을 무효화하지 않지만 앱 지원만으로 기본 사용 호환성을 확정하지 않는다.
- **동작:** 이어폰의 별도 Android·iOS·멀티포인트 근거는 선택한 대상별로 판정한다. 다른 대상만 명시한 근거는 선택한 대상의 모호함·상충으로 합치지 않는다. 선택 대상의 지원·미지원 상충과 대상을 특정하지 못한 미확인 근거는 UNKNOWN을 유지한다. 원래 사양과 출처 목록은 보존한다.
- **동작:** 동일 모델의 사양 표에서 확인한 `Multipoint Connection Yes/No`는 멀티포인트 지원/미지원으로 판정한다. 이 근거만으로 Android·iOS 기본 사용 지원을 확정하지 않는다.
- **동작:** v2는 선택한 호환성도 기본 추천의 필수 검사에 포함한다. 제공 OS·영상 입력·이어폰 기본 플랫폼 호환의 확인 여부를 최소 사양과 별도 표시한다. 앱 지원은 이어폰의 기본 사용 지원을 대체하지 않으며, 명시적인 미지원 없이 다른 플랫폼을 지원한다는 이유만으로 미달로 처리하지 않는다. 전용 앱·기기별 기능은 근거와 제약이 있을 때 별도 정보로 제공한다.
- **동작:** 공식 판매처 정책이 적용된 후보는 최소 사양을 모두 충족하고 예산 안에 있을 때만 기본 목록에 둔다. 하나라도 BELOW거나 예산 초과이면 미달 영역, UNKNOWN이 있으면 근거 부족 영역에 분리한다. 영역 안에서는 확인 판매가·제품명 순으로 정렬한다. 미달·미확인 후보도 사용자가 비교에 추가할 수 있다. 승인 후 비교표에서 조건별 근거를 유지하고 사용자 확인 수치는 표시·판정에 함께 우선한다.
- **이유:** 포트폴리오 MVP에서 적은 외부 호출로 조건·근거·판정의 연결을 보여준다. 기존 평가값·가중치·hard Constraint를 자동 변경하지 않는다.

### BR-DISC-04: 공식 판매처와 확인 판매가의 범위를 알린다

- **동작:** [등록 근거가 있는 판매처](official-stores.md)의 한국어 개별 상품 페이지에서 정확한 KRW 판매가를 확인한 제품만 추천한다. HTML의 단일 Product/Offer를 검증하며 가격 범위·조건부 가격·불명확한 구성은 제외한다. 가격은 AI 생성값으로 대체하지 않는다.
- **동작:** v2는 같은 상품 구매 영역의 가격 라벨·금액도 대조한다. 해당 금액의 회원·쿠폰·카드 조건 또는 본문 연결 불명확은 가격 미확인으로 제외한다. 과거 v1 결과에 새 가격·호환성 검증을 소급하지 않는다.
- **동작:** 등록 판매처의 알려진 동일 상품 경로 별칭과 명시적으로 확인된 색상 옵션만 정규화한다. 다른 모델의 canonical·용량/구성 옵션은 같은 상품으로 합치지 않는다. 색상별 별도 판매 페이지도 같은 모델이면 비교 후보 한 개로 취급한다. 구체적인 수집 표현은 [판매처 문서](official-stores.md)를 따른다.
- **동작:** 공식 제품 이미지는 확인한 이미지 호스트에서 브라우저가 referrer 없이 직접 불러온다. 이미지가 없거나 실패하면 대체 영역을 표시한다. 입력 화면에서 이미지 서버로 IP가 전달됨을 알린다.
- **동작:** 홈·조건 입력·결과에 공식 판매처 기준과 다른 판매·할인 경로가 더 저렴할 수 있음을 본문으로 표시한다. 실행의 정책 버전과 개별 공식 offer를 저장하며 기존 추천·직접 추가를 공식 추천으로 소급하지 않는다.
- **이유:** 추천 범위와 가격 정렬의 의미를 명확히 하되 제품 안전성·품질·정품·최저가를 인증하지 않는다.

### BR-DISC-05: 같은 조건의 추가 탐색은 비교와 추천 이력을 보존한다

- **동작:** 명시적 refresh는 최초 저장한 조건과 동의로 새 generation을 만든다. 승인·제외·미선택을 포함한 이전 추천 전체를 모델 식별자·정규화한 이름·공식 상품 URL로 누적 제외한다. 최근 공개 모델명 최대 5개만 검색 보조 조건으로 보내고 전체 제외는 서버에서 적용한다. 직접 편집한 제품명·메모는 검색 조건에 추가하지 않는다.
- **동작:** 실행마다 검색 1회·수집 최대 5개·AI 최대 1회와 기존 quota를 유지한다. 신규 후보가 확보돼야 표시 중인 generation을 교체하며, 실패·새 제품 없음은 이전 목록을 유지한다. 비교에 승인한 Option·사용자 입력·확인값은 변경하지 않는다.
- **동작:** 최초·추가 탐색 모두 검색 결과가 없거나 공식 페이지가 없으면 `NO_OFFICIAL_RESULTS`, 수집한 페이지의 상품·판매가를 검증하지 못하면 `NO_VERIFIED_OFFERS`, 이전 추천을 제외하여 새 후보가 없으면 `NO_NEW_PRODUCTS`로 구분해 안내한다.
- **동작:** 추천 묶음의 최종 상태와 후보는 이력으로 남긴다. retry는 같은 generation을 복구하며 승인한 사양을 덮어쓰지 않는다. 진행 단계 변경은 optimistic version을 증가시키지만 executionId를 유지한다. 이전 실행의 지연 응답은 새 실행에 반영하지 않는다.
- **이유:** 재검색 비용을 명시적으로 통제하면서 비교 내용을 잃지 않고 다른 후보를 확인한다. 별칭·판매자 간 완전한 모델 식별 catalog는 도입하지 않는다.

### BR-CAT-01: 등록 제품 추천은 사용자 후보·외부 전송 없이 시작한다

- **동작:** 기본 경로는 무선 이어폰, 초기 5개·활성 최대 30개 중 최대 5개다. 선택 조건은 DB의 공식 근거로 결정적으로 판정한다. 추천 시 외부 호출은 없고 검색·수집·AI 동의는 false여야 한다. 메모는 개인 비교에만 저장하며 자동 필터로 해석하지 않는다.
- **동작:** 모델·구성이 불확실한 별칭은 결합하지 않는다. ANC·호환성 등의 근거가 없으면 UNKNOWN이며 조건을 자동 완화하지 않는다. 후보 수용은 여전히 사용자 선택이다.
- **이유:** 카탈로그에서 확인 가능한 근거가 없는 경우의 UNKNOWN은 MVP의 정상 동작이다. 제조사의 정보 제공 여부를 별도로 조사하거나 확인할 수 없는 기능 지원을 추론하지 않는다. 사용자가 직접 확인해야 하는 불편을 감수한다.
- **이유:** 후보 없는 클릭 진입을 유지하면서 검색 실패·외부 호출 비용을 제한한다.

### BR-CAT-02: 가격과 고정 사양의 수명을 분리한다

- **동작:** 가격은 확인 시각부터 72시간 미만만 예산 판정에 사용한다. 없는/만료된 가격은 확인 필요이며 다른 조건의 명확한 미달이 없으면 UNKNOWN으로 분류한다. 저장 시각·조회 시각으로 가격 시각을 갱신하지 않는다. 고정 사양은 90일 점검 대상으로 유지하고 SOFTWARE 사양은 30일 이후 새 추천에서 보류한다.
  - **동작:** 공식 판매 URL을 제품별 48시간 간격으로 수집한다. 등록된 URL이나 식별자가 바뀌면 기존 가격은 무효화하되 마지막 시도에 따른 다음 시도 시각은 유지한다. 수집 실패는 이전 시각을 연장하지 않는다. 품절·가격 검증 실패·모델 불일치는 새 추천의 판매 정보를 제외한다. 월 한도와 시도 기록은 호출 전에 commit하며 실패·재시작도 한도를 소비한다.
- **이유:** 느리게 변하는 정보의 운영 비용과 가격 오판 위험을 구분한다. 일중 변동과 판매처 정확성은 보장하지 않는다.

### BR-CAT-03: 공개 등록 정보와 개인 비교 snapshot은 구분한다

- **동작:** 운영자가 검토한 로컬 manifest만 등록하며 수정은 revision 증가를 요구한다. 사양은 공식 URL·인용·확인 시각을 보존하고 UNREVIEWED로 표시한다. URL·identity 변경은 기존 가격을 초기화한다.
- **동작:** 후보 생성 당시의 사양과 가격을 개인 비교에 복사한다. 이후 공용 등록 수정은 개인 확인값·평가를 덮어쓰지 않는다. 과거 후보를 읽을 때에도 가격의 72시간 규칙을 적용한다.
- **이유:** 재사용으로 인한 개인정보 혼합과 과거 결정 근거의 변조를 막는다.

### BR-EVD-03: 제품명 검색은 사용자 선택을 돕는 URL lookup이다

공식 추천의 추가 규칙은 BR-DISC-04를 따른다. 직접 제품명 검색·URL 추가는 공식 추천과 구분한다.

- **동작:** 인증된 사용자가 제품명 전송에 동의한 요청만 Serper로 보낸다. 요청마다 최대 200자의 query 하나만 전달하며 Decision의 의도·메모·사용자 identity를 보내지 않는다. 응답은 공개 HTTP(S) 페이지 최대 5개이며 부적절한 URL과 중복 URL을 제외하므로 3개 미만일 수도 있다.
- **동작:** 검색 결과는 서버에 저장하지 않는다. 사용자가 선택한 이름·URL만 기존 Option에 연결하고 검색 발췌를 검증 값으로 저장하지 않는다. 동일 URL 추가는 중복 후보를 만들지 않는다. 5개 한도, ownership, 수정 가능한 상태, 별도 웹 수집 동의를 그대로 적용한다. URL 직접 입력은 검색 장애·비활성·한도 초과 시에도 동작한다.
- **이유:** 별도 catalog/상품 식별 시스템 없이 사용자의 검색·복사 부담만 제거한다. 검색 순서는 추천 순위가 아니며 세대·옵션·유통·가격 정확성을 자동 확정하지 않는다.

### BR-COST-01: 제품 검색은 월 상한 안에서만 호출한다

- **동작:** 배포 DB를 공유하는 서버 전체에서 UTC 월 1,000회까지 Serper 호출을 허용한다. 운영 설정은 1~1,000 사이로 낮출 수 있다. 호출 전에 사용량을 원자적으로 예약하고 provider 실패·timeout도 소비한다. 자동 재시도·자동 충전하지 않는다. 단일 instance의 사용자별 5회/분과 동시 2건 제한으로 burst를 제어한다.
- **이유:** 불확실한 응답에도 과금 가능성이 있으므로 상한을 보수적으로 보장한다. 이 한도는 별도 Firecrawl·AI·Resend 호출이나 앱 밖의 API key 사용량을 제한하지 않는다.

### BR-TPL-01: 이어폰은 기존 evaluator와 명시적인 초기 가정을 쓴다

- **동작:** `earbuds-v1`은 가격 최선 50,000원·최악 500,000원, 음질·착용감·노이즈 캔슬링·배터리 적합도 각 1~5점, 기본 중요도 모두 1이다. rating은 사용자 입력이며 evidence worker는 AI가 제안한 이어폰 rating을 자동 적용하지 않는다. 최대 예산·필수 호환성은 선택적으로 활성화한다.
- **이유:** anchor와 선형 utility는 검증된 인간 만족도 모델이 아닌 MVP 초기 가정이다. 새로운 evaluator·preference model을 만들지 않는다.

### BR-PREF-01: 성향 기억은 검토 가능한 projection이다

- **동작:** outcome은 category·criterion·근거 Decision을 가진 `PROPOSED` PreferenceMemory를 만들 수 있다. 사용자가 확인한 기억만 같은 category의 다음 Decision 기본 weight에 적용하고 적용 내역을 표시한다. category를 넘는 자동 전이, vector retrieval, 숨은 weight 변경은 하지 않는다.
- **이유:** 개인화의 근거와 통제권을 사용자에게 남기면서 실제 outcome을 다음 선택에 재사용한다.

### BR-SCP-01: 범용 agent와 자동 학습 모델은 범위 밖이다

- **동작:** pairwise comparison, 확률 모델, 자동 학습 모델, 범용 대화형 agent, 인증 페이지 수집, 지속적 가격 감시를 제공하지 않는다. Algorithm/LLM 결합은 outcome 데이터로 검증한 뒤 별도 ADR로 결정한다.
- **이유:** guided 구매 결정과 inspectable memory의 품질을 먼저 검증한다.

### BR-SCP-02: Gemini 무료 티어는 저민감 dogfooding에만 사용한다

- **동작:** Gemini Developer API 무료 티어에는 제품 정보와 일반 구매 메모만 보낸다. 개인 식별 정보, 연락처·주소·주문 번호, 인증·금융 정보, 건강 정보, 비공개 업무·고객 정보는 입력하거나 전송하지 않는다.
- **이유:** 무료 티어에서는 전송 콘텐츠가 provider 제품 개선에 사용될 수 있다. 민감 데이터 처리나 production 배포에는 별도 retention/delete 정책과 provider 검토가 필요하다.

## 현재 검증 상태

이 표는 규칙의 전체 테스트 명세가 아니라, 현재 자동 검증의 시작점이다. 규칙을 변경하면 영향을 받는 행의 테스트를 추가하거나 보강한다.

| 규칙 | 현재 자동 검증 |
| --- | --- |
| BR-LIST-01, BR-DELETE-01 | `DecisionApiIntegrationTest`, `DiscoveryApiIntegrationTest.lateAiCompletionCannotRecreateADeletedDecision`, `frontend/e2e/decision-list.spec.ts` |
| BR-CMP-01~02 | `ComparisonTest`, `DecisionApiIntegrationTest.comparisonConditionsConfirmationAndResolutionSnapshotSurviveReopening`, `frontend/e2e/comparison-flow.spec.ts` |
| BR-AUTH-01 | `UserProvisioningServiceTest` |
| BR-CAT-01~03 | `CatalogPolicyTest`, `CatalogApiIntegrationTest`, `frontend/e2e/catalog.spec.ts`, `scripts/catalog/report.test.mjs` |
| BR-OWN-01 | `DecisionApiIntegrationTest.isolatesEveryDecisionOperationByOwner`, `DecisionApiIntegrationTest.requiresAuthenticationAndCsrf` |
| BR-EVAL-01 | `SnapshotFactoryTest.blocksEvaluationWhenAnActiveValueIsMissing` |
| BR-EVAL-02, BR-EVAL-03 | `AlgorithmEvaluatorTest.appliesConstraintsAnchorsWeightsAndEstimatedFlag`, `AlgorithmEvaluatorTest.clampsNumericUtilityOutsideAnchors` |
| BR-EVAL-04 | `DecisionApiIntegrationTest.savesCompleteDraftAndCreatesReproducibleAlgorithmEvaluation` |
| BR-AI-01, BR-SCP-02 | `AiDataPolicyTest`, `DecisionApiIntegrationTest.bootsMigratesDatabaseAndCreatesLaptopDecision` |
| BR-DEC-01~05, BR-AI-03~04, BR-SCP-01 | 명시적 규칙 단위 테스트 연결이 아직 없다. 해당 동작을 변경할 때 추가한다. |
| BR-DEC-06, BR-AI-02, BR-EVD-01~02, BR-PREF-01 | `DecisionApiIntegrationTest.createsGuidedDecisionAnswersCriticalQuestionsAndBuildsPreferenceMemory` |
| BR-EVD-01~02 | `EvidenceWorkflowWorkerTest` (부분 실패·빈 페이지·재시도 종료·AI 실패 격리·후보 승인 경계) |
| BR-EVD-03, BR-COST-01 | `ProductLookupServiceTest`, `SerperSearchProviderTest`, `SearchQuotaIntegrationTest`, `ProductLookupApiIntegrationTest`, `DecisionApiIntegrationTest.lookupRequiresAuthenticationCsrfAndExplicitConsentEvenWhenDisabled` |
| BR-DISC-01~02 | `DiscoveryWorkerTest`, `DiscoveryFailureTest`, `ProviderResponseConversionTest`, `DiscoveryApiIntegrationTest`, `frontend/e2e/click-discovery.spec.ts` |
| BR-DISC-03 | `MinimumRequirementMatcherTest`, `DiscoveryApiIntegrationTest.minimumRequirementsPersistAndRemainAdvisoryWithBoundedExternalCalls`, `DiscoveryApiIntegrationTest.invalidMinimumRequirementsAndLegacyVersionCannotTriggerProviders`, `frontend/e2e/minimum-requirements.spec.ts` |
| BR-DISC-04 | `OfficialProductParserTest`, `OfficialProductLinkingTest`, `ProviderResponseConversionTest.productScrapeConvertsHtmlAndFinalUrlWithOneRequest`, `DiscoveryApiIntegrationTest`, `frontend/e2e/click-discovery.spec.ts` |
| BR-DISC-02~04 (v2) | `DiscoveryVerificationTest`, `DiscoveryApiIntegrationTest.compatibilityIsRequiredWithoutMinimumSpecsAndLegacyPolicyKeepsItsOriginalMeaning`, `frontend/e2e/minimum-requirements.spec.ts` |
| BR-DISC-05 | `DiscoveryExclusionsTest`, `DiscoveryApiIntegrationTest.refreshExcludesAllPreviousProductsAndPreservesAcceptedInputAndHistory`, `DiscoveryApiIntegrationTest.failedRefreshKeepsPreviousListAndRetryUsesSameGeneration`, `DiscoveryApiIntegrationTest.concurrentRefreshRequestsCreateOneNewGeneration`, `DiscoveryApiIntegrationTest.lateAiCompletionCannotOverwriteANewerExecution`, `frontend/e2e/click-discovery.spec.ts` |
| BR-TPL-01 | `DecisionApiIntegrationTest.earbudsAndSelectedSourcesReuseExistingLifecycle`, `EvidenceWorkflowWorkerTest.earbudsNeverAutoFillSubjectiveRatings` |
| BR-DEC-02, BR-EVD-03 | `frontend/e2e/guided-decision.spec.ts` (선택→평가→회고·한도 fallback·polling/충돌 입력 보존·수집 재시도) |

## 아직 정하지 않은 동작

- 동점인 feasible Option의 추천·표시 정책
- `ARCHIVED` 상태로 전이하는 조건과 그 이후 허용 동작

이 항목은 현재 구현 세부 동작에 의존하지 않는다. 제품 또는 API에서 의미 있는 차이를 만들게 되면 먼저 규칙을 결정하고 이 문서를 갱신한다.
