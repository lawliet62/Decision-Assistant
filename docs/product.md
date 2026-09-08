# Product scope

## 문제 정의

Decision Assistant는 사용자를 대신해 정답을 고르는 추천 엔진이 아니라, 비교형 개인 의사결정을 명시적으로 구조화하고 결정 당시의 근거와 사후 결과를 연결하는 도구다.

## MVP boundary

지원한다.

- 단일 사용자의 개인 전자기기(Laptop, Phone, Tablet, Monitor, 완전 무선 이어폰) 1~5개 후보 intake와 2~5개 Option 비교
- 초대된 사용자의 Google 또는 이메일 OTP 로그인과 개인별 데이터 격리
- Criterion weight와 numeric/rating value
- hard Constraint와 불확실성 표시
- 한 문장과 제품명 검색 → 정확한 결과 선택으로 시작하는 비교 문서. 공개 URL 직접 입력은 fallback
- 사용자 동의 후 공개 페이지 수집과 최대 3개의 대안 후보 제안
- 불완전 입력의 잠정 조언과 완전한 입력의 재현 가능한 평가
- 회고 reminder와 사용자가 확인·수정·삭제하는 category별 성향 기억
- 재현 가능한 algorithm result와 독립적인 LLM assessment
- 최종 선택·보류·포기와 여러 outcome check-in

지원하지 않는다.

- 협업, 공개 공유, 관리자용 사용자 관리 UI
- 인증 페이지 crawling, 지속적인 가격/재고 감시, 최저가 보장, domain catalog
- pairwise comparison, 확률 모델, 숨은 가중치 변경이나 자동 학습 모델
- 범용 대화형 agent와 사용자 확인값을 덮어쓰는 LLM 변경

## 핵심 가설과 성공 신호

대상은 “비교할 개인 전자기기 제품명을 알고 있는 사용자가 trade-off를 정리하는 상황”이다. 사용자가 제품명을 URL로 바꾸기 위해 별도 검색·복사할 필요를 줄인다. 검색 결과는 최대 5개이고 정확한 제품·세대·페이지는 사용자가 선택한다. 첫 잠정 조언까지 필요한 시간과 답변 수, 실제 선택·outcome 기록이 핵심 신호다. 추천 클릭률만으로 correctness를 판단하지 않는다.

이번 범위는 마지막 MVP 기능 확장이다. 이후에는 배포·운영·안정화·문서화에 집중한다. 시장 전체의 최적 후보 탐색, 가격·재고·공식 유통 정확성, 후기 aggregation, catalog/entity resolution subsystem, preference learning, evaluator 교체, 모바일 앱·협업·실시간 알림은 추가하지 않는다.

## 제품 원칙

- 모르는 값은 중립값으로 숨기지 않고 평가를 막거나 estimated로 표시한다.
- Constraint 위반 후보와 점수 차이의 근거를 노출한다.
- Algorithm과 LLM이 불일치할 수 있으며 이를 숨기지 않는다.
- AI proposal은 출처가 있는 미확인 draft가 될 수 있지만 사용자 확인 사실이나 실제 선택을 자동 변경하지 않는다.
- 웹·AI 추출값은 출처와 `UNREVIEWED` 상태로 저장할 수 있지만 사용자 확인값으로 취급하지 않는다.
- 질문은 모든 빈칸이 아니라 결론을 바꿀 수 있는 정보부터 최대 3개씩 제시한다.
