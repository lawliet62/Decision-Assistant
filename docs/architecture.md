# System architecture

## 현재 구조

```text
Browser
  → Next.js (UI, same-origin API/OIDC proxy)
    → Spring Boot BFF (Auth0 OIDC client, CSRF, authorization)
      → Auth0 Universal Login (Google + email OTP)
      → PostgreSQL (user ownership + JDBC session + domain data)
      → Gemini Developer API (dogfooding default, optional, explicit consent)
      → OpenAI API (optional alternative)
```

단일 backend 배포 단위 안에서 `decision`, `template`, `evaluation`, `ai` feature package를 사용한다. message broker, cache, vector database, microservice는 현재 문제에 필요하지 않다.

## 데이터 경계

- Decision aggregate: 사용자가 계속 수정하는 current state. Option, Criterion, value, Constraint를 관계형 table로 저장한다.
- Evaluation: 특정 `decisionVersion`의 immutable input snapshot, hash, algorithm result, optional LLM result를 저장한다.
- StructureRun: LLM에 실제 전달한 input과 proposal, 실행 metadata를 저장한다. proposal은 Decision을 자동 변경하지 않는다.
- Resolution/OutcomeCheckIn: 선택과 사후 회고를 append한다.

## 요청 흐름

1. 사용자가 versioned template로 Decision을 만든다.
2. draft 저장은 expected version을 검사한다.
3. 평가 시 완전성과 constraint 상태를 검증하고 immutable snapshot을 만든다.
4. algorithm은 동기적으로 항상 먼저 실행한다.
5. 동의한 경우에만 LLM이 같은 snapshot을 독립 평가한다. 장애 시 algorithm result는 남는다.

## Security/operations baseline

- Spring이 OIDC Authorization Code + PKCE와 server-side session을 소유한다. 브라우저에는 `HttpOnly`, `Secure`, `SameSite=Lax` session cookie만 두고 access token을 노출하지 않는다.
- Session은 PostgreSQL에 저장하고 7일 비활성 만료한다. 변경 요청은 same-origin CSRF cookie/header를 검증하며 CORS를 열지 않는다.
- Auth0의 verified email이 exact allowlist에 있을 때만 로컬 사용자를 provision한다. 동일 이메일의 다른 identity는 자동 연결하지 않는다.
- 모든 사용자 resource 조회는 현재 local user UUID로 scope한다. Decision 하위 데이터는 Decision ownership을 상속하며 타인 resource는 `404`로 응답한다.
- `local` profile만 고정 개발 사용자를 제공하고 loopback에 bind한다. 기본/production 실행에는 인증 우회가 없다.
- source URL은 문자열로만 저장하며 backend가 접근하지 않아 SSRF 경로를 만들지 않는다.
- AI request storage는 provider 설정에서 끄고 timeout/retry를 제한한다. Gemini 무료 티어 dogfooding에는 저민감 구매 정보만 허용하며, 서버와 UI가 민감 정보 비포함 확인을 요구한다.
- Actuator는 health/info만 노출한다. secret은 환경 변수로만 주입한다.

관련 결정은 [ADR 목록](decisions/)을 참고한다.
