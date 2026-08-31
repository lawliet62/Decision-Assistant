# Known limitations / deferred decisions

## 구현된 배포 baseline

- Auth0 OIDC Authorization Code + PKCE, PostgreSQL-backed Spring session, CSRF
- exact-email invite allowlist와 모든 Decision 계열 데이터의 user ownership

## 배포 전 반드시 해결

- production secret 관리, TLS, CORS/host 정책, backup/restore
- 개인정보/AI provider 고지와 retention/delete 정책
- rate limit과 AI 비용 한도

## 사용 데이터가 생긴 뒤 결정

- Laptop vertical의 실제 사용자와 acquisition channel
- template version upgrade 정책
- weight elicitation UX와 sensitivity analysis
- 동점 추천 표시와 `ARCHIVED` lifecycle 정책
- Algorithm/LLM 품질 평가 지표와 결합 여부
- outcome reminder와 preference learning
- Google과 이메일 OTP identity의 명시적 account linking

## 의도적으로 단순화한 부분

- AI 호출은 요청 내 동기 실행이며 30초 timeout이다. 실제 latency/traffic이 문제일 때 job queue를 검토한다.
- single instance를 전제로 한다. cache/message broker가 없다.
- source text는 사용자가 붙여 넣고 URL은 fetch하지 않는다.
- end-to-end browser test와 observability dashboard는 첫 배포 흐름이 정해질 때 추가한다.
- 첫 로그인 방식을 바꾸는 account linking UI는 제공하지 않는다. 동일 이메일만으로 identity를 자동 병합하지 않는다.
