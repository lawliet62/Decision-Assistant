# ADR 0005: Auth0 OIDC와 Spring BFF session으로 사용자 ownership을 보호한다

- Status: Accepted
- Date: 2026-08-27

## Context

인터넷 배포에는 인증과 개인 Decision 격리가 필요하다. 현재 client는 web browser뿐이고 Next.js가 Spring API를 same-origin으로 proxy한다. 직접 password를 관리하거나 browser에 bearer token을 보관하면 credential lifecycle과 token 탈취 대응이 제품 범위에 추가된다. JWT resource server는 mobile 또는 외부 API client가 없는 현재 구조에는 불필요한 경계를 만든다.

## Decision

Auth0 Universal Login의 Google과 passwordless email OTP를 사용하고 Spring Security를 confidential OIDC client로 둔다. Authorization Code + PKCE 교환은 backend에서 수행하고 session은 PostgreSQL-backed Spring Session에 7일 비활성 만료로 저장한다. Browser는 secure HttpOnly same-origin cookie만 보유하며 변경 요청은 CSRF token을 검증한다.

Domain은 Auth0 subject를 직접 FK로 사용하지 않는다. 로컬 ApplicationUser UUID와 `(issuer, subject)` ExternalIdentity를 분리하고 Decision에 owner UUID를 둔다. 모든 하위 aggregate와 기록은 Decision ownership을 상속하며 repository query에서 현재 owner를 강제한다. PostgreSQL RLS와 aggregate별 중복 owner column은 추가하지 않는다.

첫 배포는 exact-email allowlist로 가입을 제한한다. 같은 이메일의 Google/passwordless identity는 자동 연결하지 않으며 사용자가 두 identity를 모두 재인증하는 연결 기능은 후속 결정으로 남긴다.

## Consequences

Access token이 browser JavaScript에 노출되지 않고 인증·인가·CSRF 책임이 Spring에 모인다. 재배포 후에도 session이 유지되며 별도 Redis는 필요하지 않다. 반면 web 외 client에는 적합하지 않고 session table cleanup과 Auth0 availability에 의존한다. Mobile·외부 API 또는 공유 role이 실제 요구가 되면 OAuth resource server, scope/RBAC, account linking을 새 ADR로 검토한다.
