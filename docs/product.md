# Product scope

## 문제 정의

Decision Assistant는 사용자를 대신해 정답을 고르는 추천 엔진이 아니라, 비교형 개인 의사결정을 명시적으로 구조화하고 결정 당시의 근거와 사후 결과를 연결하는 도구다.

## MVP boundary

지원한다.

- 단일 사용자의 2~5개 Option 비교
- 초대된 사용자의 Google 또는 이메일 OTP 로그인과 개인별 데이터 격리
- Criterion weight와 numeric/rating value
- hard Constraint와 불확실성 표시
- Laptop purchase template 및 내부 실험용 Custom template
- 재현 가능한 algorithm result와 독립적인 LLM assessment
- 최종 선택·보류·포기와 여러 outcome check-in

지원하지 않는다.

- 협업, 공개 공유, 관리자용 사용자 관리 UI
- web crawling, 가격/재고 자동 갱신, domain catalog
- pairwise comparison, 확률 모델, 자동 preference learning
- 범용 대화형 agent나 LLM의 자동 draft 변경

## 핵심 가설과 성공 신호

첫 vertical은 “여러 laptop 후보를 이미 가진 사용자가 trade-off를 정리하는 상황”이다. 사용자가 draft를 완성하고 평가를 실행하며, 결과를 이해한 뒤 실제 선택과 outcome을 기록하는지가 핵심 신호다. 추천 클릭률만으로 correctness를 판단하지 않는다.

## 제품 원칙

- 모르는 값은 중립값으로 숨기지 않고 평가를 막거나 estimated로 표시한다.
- Constraint 위반 후보와 점수 차이의 근거를 노출한다.
- Algorithm과 LLM이 불일치할 수 있으며 이를 숨기지 않는다.
- AI proposal은 사용자가 검토·수정한 뒤에만 draft가 된다.
