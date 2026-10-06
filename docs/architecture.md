# System architecture

## 현재 구조

```text
Browser
  → Next.js (UI, same-origin API/OIDC proxy)
    → Spring Boot BFF (Auth0 OIDC client, CSRF, authorization)
      → Auth0 Universal Login (Google + email OTP)
      → PostgreSQL (user ownership + JDBC session + domain data)
      → Firecrawl API (public URL scrape/search, optional, explicit consent)
      → Serper API (product-name lookup / condition discovery, explicit consent)
      → Gemini Developer API (dogfooding default, optional, explicit consent)
      → OpenAI API (optional alternative)
      → Resend API (outcome reminder email, optional)
```

단일 backend 배포 단위 안에서 `decision`, `template`, `evaluation`, `ai` feature package를 사용한다. message broker, cache, vector database, microservice는 현재 문제에 필요하지 않다.

## 데이터 경계

웹 페이지 수집은 `WebEvidenceProvider`, 검색은 `WebSearchProvider`로 분리한다. Firecrawl adapter는 두 port를, Serper adapter는 검색 port만 구현한다. 기존 guided intake의 자동 대안 검색은 Firecrawl에 고정하며 제품명 lookup 설정이 기존 동의의 전송 대상을 바꾸지 않는다. 제품명 lookup은 인증·CSRF·별도 동의 후 동기 실행한다. 사용자 선택 전에는 Decision/Option/Evidence를 만들지 않는다. 선택 후 기존 create/addSource API와 비동기 evidence worker를 사용한다.

V7은 익명 월별 검색 호출 수만 보존한다. PostgreSQL atomic upsert를 독립 transaction에서 commit한 뒤 외부 호출한다. timeout도 사용량을 소비하며 자동 재시도·환불은 없다. 월 1,000회 이하의 durable cap과 단일 instance의 사용자별 5회/분·동시 2건 제한을 적용한다. query/result는 서버에 저장하지 않으며 로그와 metric label에도 넣지 않는다. 자세한 운영 경계는 [배포 runbook](deployment.md), 결정 이유는 [ADR 0007](decisions/0007-final-mvp-product-lookup.md)을 따른다.

- Decision aggregate: 사용자가 계속 수정하는 current state. Option, Criterion, value, Constraint를 관계형 table로 저장한다.
- Evaluation: 특정 `decisionVersion`의 immutable input snapshot, hash, algorithm result, optional LLM result를 저장한다.
- StructureRun: LLM에 실제 전달한 input과 proposal, 실행 metadata를 저장한다. proposal은 Decision을 자동 변경하지 않는다.
- 구조화 AI에는 현재 활성 비교 기준의 이름·타입·단위를 전달한다. 기존 `StructureRun` input snapshot에 기준 목록이 없어도 읽을 수 있으며 과거 snapshot을 재작성하지 않는다.
- Resolution/OutcomeCheckIn: 선택과 사후 회고를 append한다.

## 클릭 추천 작업

`ProductDiscovery`는 기존 모놀리스의 scheduled worker에서 처리한다. 소유권과 명시적 동의를 확인한 Decision만 시작하며, `FOR UPDATE SKIP LOCKED`로 claim한 뒤 외부 호출을 transaction 밖에서 실행한다. 상태와 version으로 중복 실행·늦은 완료를 제어하고 실패는 수동 재시도한다. 기본 검색 1회·페이지 최대 5개·AI 1회이며, 별도 공식 문서 보완 동의가 있으면 가격 검증 후보 최대 2개에 검색·수집을 각각 한 번 추가한다. Serper quota는 제품명 lookup과 공유한다. 보완 문서는 서버가 동일 모델·사양 영역을 검증하며 추가 AI 호출이나 전체 문서 전송을 하지 않는다. 표시용 사양과 모델 연결 근거는 CandidateSuggestion에 저장하고 승인 시 Option과 연결한다. 평가값과 과거 snapshot을 변경하지 않는다. [ADR 0009](decisions/0009-click-first-product-discovery.md)와 [ADR 0015](decisions/0015-official-specification-supplement.md)에 동의·저장·실패 경계를 기록한다.

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
- source URL은 공개 HTTP(S)만 허용하고 managed provider adapter에 전달한다. 인증 정보, cookie, screenshot, private/local host는 허용하지 않으며 원문 전체는 DB에 저장하지 않는다.
- AI request storage는 provider 설정에서 끄고 timeout/retry를 제한한다. Gemini 무료 티어 dogfooding에는 저민감 구매 정보만 허용하며, 서버와 UI가 민감 정보 비포함 확인을 요구한다.
- evidence import와 reminder는 PostgreSQL status row를 scheduled worker가 처리한다. broker를 추가하지 않고 실패·재시작 후 수동 retry와 idempotent email 전송을 지원한다.
- Actuator는 health/info만 노출한다. 실제 Secret은 backend 초기 설정 단계에서 GCP Secret Manager를 직접 조회해 JVM 메모리에 바인딩한다. local은 Developer ADC로 Dev 프로젝트, 운영은 서비스 Identity로 Prod 프로젝트만 접근한다. 평문 파일/환경 변수 fallback과 일반 CI의 Secret 접근은 없다. 비밀 아닌 설정은 기존 YAML/환경 변수를 사용한다. 상세 전략은 [ADR 0008](decisions/0008-identity-based-secret-management.md), 절차는 [runbook](deployment.md#secret-management)을 따른다.

관련 결정은 [ADR 목록](decisions/)을 참고한다.
