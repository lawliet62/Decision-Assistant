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
- category를 넘는 preference transfer와 실제 outcome 기반 confidence 승급 정책
- Google과 이메일 OTP identity의 명시적 account linking

## 의도적으로 단순화한 부분

- AI 호출은 요청 내 동기 실행이며 30초 timeout이다. 실제 latency/traffic이 문제일 때 job queue를 검토한다.
- single instance를 전제로 한다. cache/message broker가 없다.
- 공개 URL 수집은 Firecrawl 구성 시에만 동작하며 사이트 차단·동적 콘텐츠·추출 오류가 있을 수 있다. 실패하면 사용자가 검증 입력을 보강한다.
- 대안 탐색은 최대 3개를 제안할 뿐 전체 시장, 최저가, 재고 완전성을 보장하지 않는다.
- guided decision 핵심 경로에는 mock backend 기반 end-to-end browser test가 있다. 실제 Firecrawl·Resend까지 포함한 staging smoke test와 observability dashboard는 첫 배포 흐름이 정해질 때 추가한다.
- reminder의 `SENT`는 Resend API가 요청을 접수했다는 뜻이다. 실제 배달·반송을 반영하는 webhook reconciliation은 아직 구현하지 않았다.
- 첫 로그인 방식을 바꾸는 account linking UI는 제공하지 않는다. 동일 이메일만으로 identity를 자동 병합하지 않는다.
