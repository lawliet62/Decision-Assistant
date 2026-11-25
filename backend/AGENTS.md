# Backend 작업 지침

root `AGENTS.md`가 우선 적용된다. package별 책임은 [Architecture](../docs/architecture.md)를 따른다.

feature package 내부에서 controller/service/entity/repository를 가까이 둔다. 별도 layer 최상위 package나 독립 Gradle module은 실제 경계 필요가 생길 때까지 만들지 않는다.

## 변경 규칙

- JPA는 current draft를 표현한다. 실행 당시 재현이 필요한 evaluation/AI input과 result는 JSONB snapshot으로 보존한다.
- `Decision.version` optimistic concurrency를 쓰며 stale draft 평가나 갱신을 허용하지 않는다.
- deterministic evaluator는 순수 Java로 유지하고 unit test에서 edge case를 검증한다.
- LLM은 저장된 snapshot만 받고 algorithm result는 받지 않는다. AI 실패가 algorithm result를 무효화해서는 안 된다.
- AI 호출 전 명시적 consent를 검증하고 timeout, provider/model, prompt/schema version, token metadata를 기록한다.
- 사용자 입력의 외부 URL 수집은 명시적 동의와 공개 HTTP(S) URL 검증을 거친 provider adapter로만 수행한다. 운영자 등록 카탈로그의 고정 URL 갱신은 [ADR 0016](../docs/decisions/0016-curated-catalog.md)의 별도 경계이며 사용자 조건을 전송하지 않는다. 원문은 저장하지 않고 출처 URL·짧은 발췌·hash·조회 시각만 보존한다.

## 검증

- unit은 `./gradlew test`, PostgreSQL/API integration은 `./gradlew integrationTest`다(Windows는 `gradlew.bat`). 전체 검증은 root의 `npm run check`다.
- API 변경 후 integration test가 `backend/build/generated/openapi.json`을 생성한다. 이를 `docs/api/openapi.json`에 반영한 뒤 frontend에서 `npm run api:generate`를 실행한다.
