# ADR 0002: Algorithm과 LLM 결과를 독립 snapshot run으로 저장한다

- Status: Accepted
- Date: 2026-08-19

## Context

명시적 rule과 LLM 판단은 오류 성격과 설명 가능성이 다르며, 초기에는 실제 outcome 데이터가 없어 합리적인 결합 weight를 정할 수 없다. draft는 평가 이후에도 바뀔 수 있다.

## Decision

평가 시 Decision version을 검증하고 immutable JSONB input snapshot을 저장한다. deterministic algorithm을 먼저 실행하고, 동의한 경우 LLM이 동일 snapshot만 독립 평가한다. LLM에는 algorithm result를 제공하지 않으며 두 결과를 임의 점수로 합치지 않는다.

## Consequences

불일치와 실패를 관찰하고 과거 결과를 재현할 수 있다. JSON schema evolution과 저장 비용이 생기므로 prompt/schema/template version metadata를 보존한다. 향후 결합은 resolution/outcome 데이터로 검증한 뒤 별도 ADR로 결정한다.
