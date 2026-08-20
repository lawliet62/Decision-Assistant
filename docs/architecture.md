# System architecture

## 현재 구조

```text
Browser
  → Next.js (UI, same-origin /api proxy)
    → Spring Boot modular monolith
      → PostgreSQL (relational current state + JSONB run snapshots)
      → OpenAI API (optional, explicit consent)
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

- 현재는 local-only이며 인증이 없다. 인터넷 배포 전에 인증·인가와 tenant ownership이 Critical blocker다.
- source URL은 문자열로만 저장하며 backend가 접근하지 않아 SSRF 경로를 만들지 않는다.
- AI request storage는 provider 설정에서 끄고 timeout/retry를 제한한다.
- Actuator는 health/info만 노출한다. secret은 환경 변수로만 주입한다.

관련 결정은 [ADR 목록](decisions/)을 참고한다.
