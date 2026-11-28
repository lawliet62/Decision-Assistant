# 로그인 session이 DB가 아니라 인스턴스 메모리에만 있는 경우

## 증상과 기대 결과

Cloud Run 검증 서비스에서 Auth0 로그인이 끝난 직후에도 Neon의 `spring_session`·`spring_session_attributes`가 비어 있었다(2026-11-28). [ADR 0005](../decisions/0005-auth0-bff-session-and-ownership.md)와 [ADR 0025](../decisions/0025-cloud-run-neon-deployment.md)는 session을 PostgreSQL에 7일 비활성 만료로 저장한다고 정했다. 그래야 인스턴스가 0개로 줄거나 새 revision이 배포된 뒤에도 로그인이 유지된다.

메모리 session에서는 로그인이 인스턴스 수명에 묶이고 `spring.session.timeout: 7d`도 적용되지 않는다. Tomcat 기본 비활성 만료(30분)가 적용된다고 보지만 운영에서 확인하지는 않았다(추론). cookie의 `Max-Age=7d`는 Tomcat에도 적용되므로 브라우저 cookie만 보고는 구분할 수 없다.

## 원인

Spring Boot 4는 session 자동 구성을 `spring-boot-autoconfigure`에서 별도 module로 분리했다. `backend/build.gradle`은 처음부터(2026-08-31, Spring Boot 4.1.0) Spring Session 라이브러리 `org.springframework.session:spring-session-jdbc`만 선언했고 Boot의 session 자동 구성 module이 없었다. 그래서 `JdbcIndexedSessionRepository`와 `SessionRepositoryFilter`가 등록되지 않았고, `spring.session.*` 설정은 읽는 곳 없이 남았다. 기동 오류나 경고는 없다.

근거 수준은 다음과 같다.

- 운영에서 관찰(리드 세션 보고): 13:03:15 UTC에 OAuth2 callback이 `302`로 응답했고, 13:04:01 UTC에 두 표의 행 수가 0이었다.
- 산출물에서 확인: `spring-boot-autoconfigure-4.1.0.jar`에는 session 관련 class가 없다. `spring-boot-dependencies-4.1.0` BOM은 `spring-boot-session`, `spring-boot-session-jdbc`, `spring-boot-starter-session-jdbc`(모두 4.1.0)를 관리한다. `SessionAutoConfiguration`은 `spring-boot-session-4.1.0.jar`(`org.springframework.boot.session.autoconfigure`), `JdbcSessionAutoConfiguration`은 `spring-boot-session-jdbc-4.1.0.jar`(`org.springframework.boot.session.jdbc.autoconfigure`)에 있다. 두 jar의 configuration metadata에 `spring.session.timeout`, `spring.session.jdbc.initialize-schema`, `spring.session.jdbc.cleanup-cron`(기본 `0 * * * * *`)이 그대로 있다. 수정 전 `runtimeClasspath`에는 `spring-session-jdbc`·`spring-session-core` 4.1.0만 있었다.
- 시험에서 재현: [`JdbcSessionIntegrationTest`](../../backend/src/test/java/com/decisionassistant/identity/JdbcSessionIntegrationTest.java)는 운영 OIDC filter chain을 실제 HTTP로 기동한다. 수정 전에는 authorization request 뒤 `spring_session`이 0행이었고, 저장된 행을 지워도 같은 cookie로 `GET /api/v1/me`가 `200`이었다(세 시험 모두 실패).
- 추론(미확인): VM 운영에서도 같은 build였으므로 session은 처음부터 메모리에 있었다고 본다. VM DB의 표는 확인하지 않았다.

## 조치와 검증

`org.springframework.session:spring-session-jdbc`를 Boot BOM이 관리하는 `org.springframework.boot:spring-boot-starter-session-jdbc`로 바꿨다. starter가 `spring-boot-session-jdbc`와 `spring-session-jdbc`를 함께 가져오므로 직접 의존성은 지웠다. 설정·schema·보안 코드는 바꾸지 않았다. 표는 Flyway `V3__authentication_and_ownership.sql`이 만들고 `spring.session.jdbc.initialize-schema: never`를 유지한다.

수정 후 `JdbcSessionIntegrationTest`가 다음을 확인한다.

- authorization request의 session이 DB에 저장되고, 로그인 callback에서 session id가 바뀐 뒤(session fixation 보호) 그 행만 남는다. `max_inactive_interval`은 604800초(7일), `principal_name`은 OIDC subject다.
- 로그인 뒤 저장된 속성은 `SPRING_SECURITY_CONTEXT` 하나다. 기본 JDK 직렬화로 되읽으면 `OAuth2AuthenticationToken`과 `OidcUser`(ID token 포함)다. 로그인 거부 뒤에는 재인증 표시(`Boolean`)와 `SPRING_SECURITY_LAST_EXCEPTION`(`OAuth2AuthenticationException`)이 저장되고 다음 authorization request가 이를 읽어 `prompt=login`을 붙인다.
- session cookie는 수정 전과 같은 이름(`DECISION_ASSISTANT_SESSION`)과 속성(`Path=/`, `Secure`, `HttpOnly`, `SameSite=Lax`, `Max-Age=604800`)을 유지한다. 값은 Spring Session 기본값인 session id의 Base64다.
- 저장된 session으로 `GET /api/v1/me`가 `200`이고, 행을 지우면 같은 cookie로 `401`이다. logout은 행을 지우고 이후 요청은 `401`이다.

[`DecisionPurgeJobIntegrationTest`](../../backend/src/test/java/com/decisionassistant/DecisionPurgeJobIntegrationTest.java)는 job mode에 `SessionRepository` bean이 없고, web mode에서는 JDBC session 저장소가 만료 정리 cron을 가진다는 것을 확인한다. `JdbcSessionAutoConfiguration`은 servlet web application에서만 켜진다.

빌드한 jar를 `local` profile로 빈 PostgreSQL 17에 실행하면 매분 0초에 `DELETE FROM SPRING_SESSION WHERE EXPIRY_TIME < $1`가 실행됐다(2026-11-28 로컬 관찰). `local` profile은 요청마다 고정 사용자를 넣고 저장하지 않으며 CSRF token도 cookie에 두므로 session을 만드는 경로가 없다. 그래서 jar로는 행 생성까지 확인하지 않았다.

상태는 코드 수정과 회귀 시험 기준으로 해결이다. 이 수정을 배포한 서비스에서 로그인 뒤 행이 생기는지는 아직 확인하지 않았다.

## 운영 동작

- 만료 session 정리는 `@Scheduled`가 아니라 `JdbcIndexedSessionRepository`가 자체 `ThreadPoolTaskScheduler`로 매분 0초에 `delete ... where expiry_time < ?`를 실행한다. Cloud Run 서비스는 요청이 없을 때 CPU를 할당하지 않으므로(`run.googleapis.com/cpu-throttling: 'true'`) 이 정리는 요청을 처리하는 동안에만 진행되고, 인스턴스가 0개면 실행되지 않는다고 본다(추론). 만료된 행은 읽을 때도 만료로 처리되므로 정리가 늦어도 로그인이 연장되지는 않는다.
- session cookie가 있는 요청은 session을 읽을 때 DB를 조회하고, 마지막 접근 시각이 바뀌므로 요청 끝에 `spring_session`을 한 번 갱신한다. 인증이 필요한 API 요청마다 쓰기가 하나 늘어난다. Neon compute는 이미 요청 처리 중에 깨어 있으므로 유휴 상태를 깨우는 별도 원인은 아니라고 보지만 CU 사용량은 측정하지 않았다(추론).
- session 행에는 로그인한 사용자의 OIDC ID token과 claim(email 등)이 직렬화되어 있고, 일일 `pg_dump` 백업에도 포함된다.
- job mode(`decision-assistant.job=purge-expired-decisions`)는 non-web이므로 session 저장소와 정리 scheduler가 없다.

## 다음에 감지하는 방법

1. 로그인한 뒤 `select count(*) from spring_session`이 1 이상인지 확인한다. 값만 세고 `attribute_bytes`나 cookie 값은 공유 자료에 넣지 않는다.
2. Spring Boot를 올리거나 의존성을 바꿀 때 `spring.session.*`처럼 자동 구성에 기대는 설정이 있으면, 해당 자동 구성 class가 `runtimeClasspath`의 어느 jar에 있는지 확인한다. Boot 4에서는 기술별 `spring-boot-<기술>` module이나 `spring-boot-starter-<기술>`이 필요하다.
3. `JdbcSessionIntegrationTest`가 실패하면 session 저장 경로가 바뀐 것이다. MockMvc 기반 보안 시험(`LoginRetrySecurityTest`, `LogoutSecurityTest`)은 `MockHttpSession`을 쓰므로 이 문제를 잡지 못한다.
