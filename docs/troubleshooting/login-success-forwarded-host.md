# Cloud Run에서 로그인 직후 연결 거부 화면으로 이동하는 경우

## 증상과 기대 결과

검증용 Cloud Run 서비스(Next.js ingress container + Spring Boot sidecar, frontend image는 `BACKEND_URL=http://localhost:8080`으로 build)에서 Auth0의 Google 계정을 고른 직후 브라우저가 `ERR_CONNECTION_REFUSED` 화면으로 이동했다(2026-11-28). 새로고침해도 같았고, 서비스 주소를 다시 열면 로그인된 상태였다. callback 처리와 session 생성은 성공했고 그 뒤 redirect 대상만 잘못됐다. 로그인 성공 뒤에는 요청 경로와 무관하게 공개 주소의 `/`로 이동해야 한다.

저장된 요청 때문에 backend 내부 주소로 이동하던 [이전 사례](login-saved-request-redirect.md)와 증상이 비슷하지만 원인이 다르다. 그 수정(`NullRequestCache`) 뒤에도 이동 대상이 상대 경로 `/`였기 때문에 생긴 문제다.

## 원인

로그인 성공 처리기의 기본 이동 대상은 상대 경로 `/`였다. `server.forward-headers-strategy: framework`에서 Spring Boot는 `ForwardedHeaderFilter`를 등록하고 `relativeRedirects`를 `server.tomcat.use-relative-redirects`(기본 `false`)로 설정한다(Spring Boot 4.1.0 `TomcatServletWebServerAutoConfiguration`에서 확인). 따라서 상대 redirect는 forwarded header로 보정한 요청 주소를 기준으로 절대 주소가 된다.

Spring의 forwarded header 처리는 RFC 7239 `Forwarded` header가 있으면 그 값만 쓰고 `X-Forwarded-*`는 쓰지 않는다. `Forwarded`에 `proto=https`만 있고 `host`가 없으면 scheme은 `https`가 되고 port는 지워지지만 host는 backend가 받은 `Host`(`localhost:8080`의 `localhost`)로 남는다. 결과는 `https://localhost/`이고, 브라우저는 사용자 PC의 443 port로 접속하다 연결을 거부당한다.

근거 수준은 다음과 같다.

- 운영에서 관찰: 위 증상. 오류 화면의 주소창은 `https://localhost/`(port 없음)였다(사용자 관찰). request log에서 `GET /login/oauth2/code/auth0`가 한 번 `302`로 응답했다. `Location` 값은 로그에 남지 않는다.
- 로컬 실험에서 관찰: 같은 frontend image의 rewrite는 backend로 `Host: localhost:8080`을 보내고, 원래 host를 `X-Forwarded-Host`에 넣으며, 들어온 `X-Forwarded-Proto`·`X-Forwarded-For`·`Forwarded`를 그대로 전달한다. `X-Forwarded-Port`는 없다.
- 시험에서 재현: [`LoginRetrySecurityTest`](../../backend/src/test/java/com/decisionassistant/identity/LoginRetrySecurityTest.java)에서 `ForwardedHeaderFilter`를 Spring Security 앞에 두고 `Host: localhost:8080`, `Forwarded: for=203.0.113.9;proto=https`, `X-Forwarded-Proto: https`, `X-Forwarded-Host: app.example.test`로 로그인하면 수정 전 `Location`이 `https://localhost/`였다. 운영 주소창 값과 일치한다. 같은 요청에서 `Forwarded`만 빼면(VM의 Caddy 구성) 수정 전에도 `https://app.example.test/`였다.
- 추론(미확인): Cloud Run front end가 `Forwarded: for="...";proto=https`를 추가한다. 운영 요청의 header를 직접 수집하지 않았다. VM 구성에서 같은 코드가 정상 동작한 이유는 Caddy가 `Forwarded` 없이 `X-Forwarded-Host`를 보내기 때문으로 본다.

## 조치와 검증

[`SecurityConfiguration`](../../backend/src/main/java/com/decisionassistant/identity/SecurityConfiguration.java)의 OIDC filter chain에서 로그인 성공 처리기를 `SimpleUrlAuthenticationSuccessHandler(<publicBaseUrl>/)`로 지정했다. 실패 처리기와 logout 처리기처럼 요청의 host·forwarded header가 아니라 `decision-assistant.auth.public-base-url`을 기준으로 한다. `NullRequestCache`, 로그인 성공 시 재인증 표시 해제, session fixation 보호는 바뀌지 않는다.

`forward-headers-strategy` 변경이나 `Forwarded` header를 제거하는 middleware는 택하지 않았다. 다른 proxy 구성에서도 같은 문제가 생길 수 있고, 로그인 뒤 이동할 공개 주소는 이미 설정값으로 정해져 있다.

회귀 시험은 Cloud Run 형태와 Caddy 형태의 header로 로그인했을 때 `Location`이 정확히 `https://app.example.test/`인지 확인한다. 수정 전에는 Cloud Run 형태만 실패했고 수정 후 모두 통과했다. 기존 로그인 성공 시험의 기대값도 상대 경로 `/`에서 공개 주소의 `/`로 바꿨다.

상태는 해결이다. 코드 수정과 회귀 시험이 끝났고, 이 수정을 포함한 Cloud Run 검증 서비스(revision `decision-assistant-00002-dq6`)에서 운영자가 로그인·재로그인이 정상임을 확인했다(2026-11-28). callback 응답의 `Location` 값은 따로 기록하지 않았다.

## 운영에서 확인하는 방법

1. 브라우저 Network에서 **Preserve log**를 켜고 로그인한다. callback 응답의 `Location`이 `<public base URL>/`이어야 한다.
2. 재발하면 실행 중인 backend 버전과 `DECISION_ASSISTANT_PUBLIC_BASE_URL` 값을 확인한다. 다른 redirect가 `localhost`로 가면 그 응답이 상대 경로 redirect인지 확인한다. forwarded header 처리 결과는 이 수정과 무관하게 그대로다.

callback의 `code`·`state` 값과 session cookie 값은 공유 자료에 포함하지 않는다.
