# 로그인 직후 backend 내부 주소로 이동하는 경우

## 증상과 기대 결과

운영에서 로그인에 성공한 직후 브라우저가 `http://backend:8080/api/v1/me?continue`로 이동해 "페이지를 찾을 수 없음" 화면이 표시됐다. 로그인 자체는 성공한 상태였고, 새 탭에서 서비스 주소를 열면 로그인돼 있었다. 로그인 성공 뒤에는 항상 서비스의 `/`로 이동해야 한다.

## 원인

Spring Security의 기본 request cache(`HttpSessionRequestCache`)가 인증되지 않은 요청을 browser session에 저장하고, 로그인 성공 처리기(`SavedRequestAwareAuthenticationSuccessHandler`)가 저장된 요청으로 redirect했다. `?continue`는 request cache가 붙이는 표시다.

저장된 요청은 브라우저가 아니라 Next.js server가 보낸 것이다. [`frontend/shared/api/server.ts`](../../frontend/shared/api/server.ts)의 `serverApi.me()`는 server component에서 `BACKEND_URL`(운영 container는 `http://backend:8080`, [`deploy/frontend.Dockerfile`](../../deploy/frontend.Dockerfile))로 직접 요청하며, 브라우저의 cookie를 전달하고 `Accept` header는 지정하지 않는다. 지정하지 않으면 Node.js의 `fetch`가 기본값 `Accept: */*`를 붙인다. [`/login` 화면](../../frontend/app/login/page.tsx)은 렌더링할 때마다 이 조회를 한다. 비로그인 `GET /api/v1/me`는 `401`을 받지만 요청은 저장된다. 기본 request cache는 CSRF 보호가 켜져 있으면 `GET`만 저장하고, `Accept`가 `application/json`·`multipart/form-data`·`text/event-stream`인 요청, `X-Requested-With: XMLHttpRequest`, WebSocket upgrade, favicon을 저장하지 않으며 `Accept: */*`는 판단에서 무시한다(Spring Security 7.1.0 `RequestCacheConfigurer.createDefaultSavedRequestMatcher`). `Accept`가 `*/*`이거나 없는 이 `GET` 요청은 어느 제외 조건에도 해당하지 않는다. 저장 URL은 그 요청의 Host 기준이므로 내부 주소가 된다.

발생 조건은 "브라우저가 인증되지 않은 backend session cookie를 가진 채 `/login` 등 server component가 렌더링되는 것"이다. 로그인 거부 뒤 `/login?error=access_denied`로 돌아온 경우가 대표적이지만, 로그인 버튼을 누른 뒤 Auth0 화면에서 뒤로 가기를 한 경우처럼 authorization request로 session만 생긴 경우도 같다. session이 아직 없으면 저장 시 새 session이 생기지만, `serverApi`는 backend 응답의 `Set-Cookie`를 브라우저에 전달하지 않으므로 그 session은 브라우저와 연결되지 않는다(코드에서 확인, 운영에서 관찰하지 않음).

운영에서 Spring이 받는 경로는 [`next.config.ts`](../../frontend/next.config.ts) rewrite의 `/api`, `/oauth2`, `/login/oauth2`, `/logout`뿐이다. 페이지는 모두 Next.js가 제공하므로 Spring이 "원래 요청한 페이지로 복귀"시킬 정당한 대상이 없다.

근거 수준은 다음과 같다.

- 운영에서 관찰: 로그인 성공 직후 `http://backend:8080/api/v1/me?continue`로 이동했다. 익명 `GET /api/v1/me`가 `401`과 함께 session cookie를 발급했다.
- 시험에서 재현: [`LoginRetrySecurityTest`](../../backend/src/test/java/com/decisionassistant/identity/LoginRetrySecurityTest.java)에서 한 session으로 거부되는 callback → `Accept` 없는 비로그인 `GET http://backend:8080/api/v1/me`(`401`) → 허용되는 callback을 실행하면 수정 전 `Location`이 `http://backend:8080/api/v1/me?continue`였다. 거부 없이 authorization request만 보낸 session에서도 같았다. 익명 API 요청 하나가 session을 만드는 것도 수정 전 시험에서 확인했다.
- 미확인: 운영에서 문제의 session이 어떤 순서로 만들어졌는지(거부 뒤인지, Auth0 화면 이탈 뒤인지)는 요청 기록을 수집하지 않아 알 수 없다. 두 경우 모두 이번 수정으로 사라지는 경로다.

## 조치와 검증

[`SecurityConfiguration`](../../backend/src/main/java/com/decisionassistant/identity/SecurityConfiguration.java)의 OIDC filter chain에서 request cache를 `NullRequestCache`로 바꿔 요청을 저장하지 않는다. 로그인 성공 뒤 이동 대상은 기존 기본값 `/` 그대로였다. 이 상대 경로가 Cloud Run 구성에서 잘못된 절대 주소가 되는 문제가 뒤에 확인돼 지금은 공개 주소의 `/`로 보낸다([후속 사례](login-success-forwarded-host.md)). 비로그인 페이지 요청이 로그인 경로로, API 요청이 `401`로 처리되는 동작은 바뀌지 않는다.

frontend의 server-side 조회에 `Accept: application/json`을 붙이는 방법은 택하지 않았다. 같은 조건의 다른 호출자가 생기면 문제가 다시 생기고, 운영에서 Spring이 복귀시킬 페이지가 없다는 구조적 원인을 해결하지 않는다.

회귀 시험은 다음을 확인한다. 수정 전에는 처음 세 항목이 실패하고 마지막 항목도 session이 생겨 실패했으며, 수정 후 모두 통과했다.

- 거부 → 비로그인 내부 주소 조회 → 허용되는 로그인의 `Location`이 정확히 `/`다.
- 거부 없이 authorization request → 비로그인 내부 주소 조회 → 허용되는 로그인의 `Location`이 정확히 `/`다.
- 비로그인 `text/html` 페이지 요청 뒤 로그인해도 그 페이지로 돌아가지 않고 `/`로 간다. 이 시험은 수정 전 "저장된 요청으로 복귀"를 단언하던 시험을 대체한다.
- session 없는 익명 API 요청이 `401`이면서 session을 만들지 않는다. 다만 MockMvc 시험에는 운영의 JDBC session 저장소가 없으므로 Spring Security가 session을 요청하지 않는다는 것까지만 확인한다.

상태는 부분 해결이다. 코드 수정과 회귀 시험은 끝났고, 수정된 운영 릴리스에서의 확인은 아직 하지 않았다.

## 운영에서 확인하는 방법

1. cookie가 없는 상태에서 `GET /api/v1/me`를 보내 `401`이면서 응답에 session cookie(`Set-Cookie`)가 없는지 확인한다. 수정 전에는 session cookie가 발급됐다. CSRF cookie(`XSRF-TOKEN`)는 이 수정과 무관하게 발급될 수 있다.
2. 브라우저 Network에서 **Preserve log**를 켜고, 로그인 거부 뒤 `/login?error=access_denied` 화면 또는 Auth0 화면에서 뒤로 가기를 한 `/login` 화면에서 다시 로그인한다. callback 응답의 `Location`이 `/`이고 `?continue`가 붙은 주소로 이동하지 않아야 한다.
3. 재발하면 callback 직전에 server-side 조회 외에 다른 비로그인 backend 요청이 있었는지와 실행 중인 backend 버전을 확인한다.

session cookie 값, callback의 `code`·`state` 값은 공유 자료에 포함하지 않는다.
