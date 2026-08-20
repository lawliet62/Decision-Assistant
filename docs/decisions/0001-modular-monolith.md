# ADR 0001: Modular monolith로 시작한다

- Status: Accepted
- Date: 2026-08-19

## Context

1인 개발 초기 제품이며 domain boundary도 검증 중이다. frontend와 backend 분리는 필요하지만 backend 내부 독립 배포나 비동기 메시징 요구는 없다.

## Decision

Spring Boot 단일 application을 feature package(`decision`, `template`, `evaluation`, `ai`)로 구성하고 PostgreSQL 하나를 사용한다.

## Consequences

로컬 실행·transaction·integration test가 단순하다. package dependency가 흐려질 위험은 test나 실제 마찰이 생길 때 ArchUnit 또는 Gradle module로 강화한다. microservice 분리는 독립 scale/ownership/deployment 요구가 관찰될 때만 검토한다.
