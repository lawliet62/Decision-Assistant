# ADR 0004: Gemini Developer API를 저민감 dogfooding 기본 provider로 둔다

- Status: Accepted
- Date: 2026-08-24

## Context

MVP에서는 AI가 deterministic evaluation을 대체하지 않고 구조화 proposal과 독립 assessment만 제공한다. dogfooding 단계에서 최고 성능보다 호출 비용, structured output 안정성, latency, 실제 workflow 유용성을 검증해야 한다. Gemini Developer API 무료 티어는 이 실험에 적합하지만, 전송 콘텐츠가 provider 제품 개선에 사용될 수 있고 rate limit이 제한된다.

## Decision

AI는 기본 비활성화 상태를 유지한다. 활성화할 때 기본 provider는 Gemini Developer API이며, `gemini-2.5-flash-lite`를 기본 모델로 사용한다. OpenAI는 환경 설정으로 선택할 수 있는 대안 provider로 유지한다.

Gemini 무료 티어에는 제품 정보와 일반 구매 메모만 전송한다. 개인 식별·금융·건강·비공개 업무 정보는 금지하며, Gemini 요청에는 외부 전송 동의와 민감 정보 비포함 확인을 모두 요구한다. provider fallback은 도입하지 않는다.

## Consequences

Gemini와 OpenAI adapter는 동일한 `DecisionStructurer`와 `DecisionAssessor` contract를 구현하며, 실행 기록에 provider/model/prompt/schema/token metadata를 남긴다. 무료 tier의 rate limit 또는 provider 실패는 AI run 실패로 기록하되 deterministic result는 유지한다.

실제 사용량, structured output 실패율, 사용자의 proposal 수정률, 개인정보/retention 요구가 관찰되면 유료 모델 또는 provider 정책을 별도 ADR로 재검토한다.
