# ADR 0003: Backend code-first OpenAPI를 versioned contract로 사용한다

- Status: Accepted
- Date: 2026-08-19

## Context

한 팀이 Spring controller와 Next.js client를 함께 변경한다. 별도 spec-first 편집 과정은 초기 속도를 늦추지만 수기 TypeScript DTO는 쉽게 drift한다.

## Decision

Backend DTO/controller에서 OpenAPI를 생성하고 integration test가 실제 application contract를 export한다. canonical spec을 repository에 commit하고 `openapi-typescript`로 frontend type을 생성한다.

## Consequences

review에서 계약 변경을 볼 수 있고 frontend가 compile-time feedback을 얻는다. backend annotation 품질이 계약 품질을 좌우한다. 외부 API consumer와 독립 release cycle이 생기면 spec-first 또는 compatibility checker를 재검토한다.
