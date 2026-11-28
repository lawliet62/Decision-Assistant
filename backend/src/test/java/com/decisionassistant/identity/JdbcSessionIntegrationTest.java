package com.decisionassistant.identity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.WebAttributes;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 운영 OIDC filter chain을 Spring Boot의 session 자동 구성과 함께 실제 HTTP로 기동해, 로그인 session이
 * PostgreSQL의 {@code spring_session} 표에 저장되고 그 행으로만 인증이 이어지는지 검증한다.
 * Auth0에는 연결하지 않는다. token 교환과 ID token 해석만 fixture로 바꾸고 사용자 provision은 실제 경로를 쓴다.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.security.oauth2.client.registration.auth0.provider=auth0",
        "spring.security.oauth2.client.registration.auth0.client-id=" + JdbcSessionIntegrationTest.CLIENT_ID,
        "spring.security.oauth2.client.registration.auth0.client-secret=fixture-secret",
        "spring.security.oauth2.client.registration.auth0.authorization-grant-type=authorization_code",
        "spring.security.oauth2.client.registration.auth0.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}",
        "spring.security.oauth2.client.registration.auth0.scope=openid,profile,email",
        // issuer-uri를 두지 않아 기동 중 discovery를 호출하지 않는다.
        "spring.security.oauth2.client.provider.auth0.authorization-uri=" + JdbcSessionIntegrationTest.ISSUER + "/authorize",
        "spring.security.oauth2.client.provider.auth0.token-uri=" + JdbcSessionIntegrationTest.ISSUER + "/oauth/token",
        "spring.security.oauth2.client.provider.auth0.jwk-set-uri=" + JdbcSessionIntegrationTest.ISSUER + "/.well-known/jwks.json",
        "spring.security.oauth2.client.provider.auth0.user-name-attribute=sub",
        "decision-assistant.auth.allowed-emails=" + JdbcSessionIntegrationTest.ALLOWED_EMAIL,
        "decision-assistant.auth.public-base-url=" + JdbcSessionIntegrationTest.APP_URL})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(JdbcSessionIntegrationTest.OidcFixture.class)
class JdbcSessionIntegrationTest {
    static final String ISSUER = "https://issuer.example.test";
    static final String APP_URL = "https://app.example.test";
    static final String CLIENT_ID = "fixture-client";
    static final String ALLOWED_EMAIL = "session@example.test";
    private static final String SUBJECT = "fixture-session-user";
    private static final String SESSION_COOKIE = "DECISION_ASSISTANT_SESSION";
    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final AtomicReference<String> NEXT_EMAIL = new AtomicReference<>(ALLOWED_EMAIL);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    /** redirect를 따르지 않는다. Secure cookie도 직접 보내기 위해 cookie는 수동으로 다룬다. */
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        jdbc.update("delete from spring_session");
        NEXT_EMAIL.set(ALLOWED_EMAIL);
    }

    @Test
    void oidcLoginIsStoredInDatabaseReusedByApiAndRemovedByLogout() throws Exception {
        var authorization = get("/oauth2/authorization/auth0", null);
        assertThat(authorization.statusCode()).isEqualTo(302);
        var anonymousSetCookie = setCookie(authorization, SESSION_COOKIE);
        assertSessionCookieAttributes(anonymousSetCookie);
        var anonymous = cookiePair(anonymousSetCookie);
        assertThat(storedSessionIds()).as("authorization request를 담은 session은 DB에 저장된다").hasSize(1);
        assertThat(storedSessionIds()).containsExactly(sessionId(anonymous));
        assertThat(storedAttributes(sessionId(anonymous)).keySet())
                .singleElement().asString().endsWith("AUTHORIZATION_REQUEST");

        var callback = get(callbackPath(authorization), anonymous);
        assertThat(callback.statusCode()).isEqualTo(302);
        assertThat(location(callback)).isEqualTo(APP_URL + "/");
        var loggedInSetCookie = setCookie(callback, SESSION_COOKIE);
        assertSessionCookieAttributes(loggedInSetCookie);
        var loggedIn = cookiePair(loggedInSetCookie);
        var id = sessionId(loggedIn);
        assertThat(id).as("session fixation 보호로 로그인 시 session id가 바뀐다").isNotEqualTo(sessionId(anonymous));
        assertThat(storedSessionIds()).containsExactly(id);

        var row = jdbc.queryForMap(
                "select max_inactive_interval, principal_name from spring_session where session_id = ?", id);
        assertThat(((Number) row.get("max_inactive_interval")).longValue())
                .as("spring.session.timeout 7d").isEqualTo(Duration.ofDays(7).toSeconds());
        assertThat(row.get("principal_name")).isEqualTo(SUBJECT);

        // 기본 serializer(JDK 직렬화)로 저장된 값을 그대로 되읽을 수 있어야 다른 인스턴스·재기동 뒤에도 쓸 수 있다.
        var attributes = storedAttributes(id);
        assertThat(attributes).containsOnlyKeys(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        var context = (SecurityContext) attributes.get(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(context.getAuthentication()).isInstanceOf(OAuth2AuthenticationToken.class);
        var user = (OidcUser) context.getAuthentication().getPrincipal();
        assertThat(user.getSubject()).isEqualTo(SUBJECT);
        assertThat(user.getEmail()).isEqualTo(ALLOWED_EMAIL);
        assertThat(user.getIdToken().getTokenValue()).isNotBlank();

        var me = get("/api/v1/me", loggedIn);
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).contains(ALLOWED_EMAIL);
        var csrf = cookiePair(setCookie(me, CSRF_COOKIE));
        var csrfValue = csrf.substring(csrf.indexOf('=') + 1);

        var logout = send(HttpRequest.newBuilder(uri("/logout"))
                .header("Cookie", loggedIn + "; " + csrf)
                .header("X-XSRF-TOKEN", csrfValue)
                .POST(HttpRequest.BodyPublishers.noBody()));
        assertThat(logout.statusCode()).isEqualTo(302);
        assertThat(storedSessionIds()).as("logout은 저장된 session 행을 지운다").isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from spring_session_attributes", Long.class)).isZero();
        assertThat(get("/api/v1/me", loggedIn).statusCode()).isEqualTo(401);
    }

    @Test
    void deniedLoginStoresSerializableRetryMarkerInDatabase() throws Exception {
        NEXT_EMAIL.set("blocked@example.test");
        var authorization = get("/oauth2/authorization/auth0", null);
        var session = cookiePair(setCookie(authorization, SESSION_COOKIE));

        var callback = get(callbackPath(authorization), session);
        assertThat(callback.statusCode()).isEqualTo(302);
        assertThat(location(callback)).isEqualTo(APP_URL + "/login?error=access_denied");

        var attributes = storedAttributes(sessionId(session));
        assertThat(attributes).containsOnlyKeys(LoginRetryReauthentication.SESSION_ATTRIBUTE, WebAttributes.AUTHENTICATION_EXCEPTION);
        assertThat(attributes.get(LoginRetryReauthentication.SESSION_ATTRIBUTE)).isEqualTo(Boolean.TRUE);
        assertThat(attributes.get(WebAttributes.AUTHENTICATION_EXCEPTION)).isInstanceOf(OAuth2AuthenticationException.class);

        var retry = get("/oauth2/authorization/auth0", session);
        assertThat(queryParam(location(retry), "prompt")).as("DB에 저장된 재인증 표시를 다음 요청이 읽는다").isEqualTo("login");
    }

    @Test
    void authenticationEndsWhenStoredSessionRowIsGone() throws Exception {
        var authorization = get("/oauth2/authorization/auth0", null);
        var anonymous = cookiePair(setCookie(authorization, SESSION_COOKIE));
        var loggedIn = cookiePair(setCookie(get(callbackPath(authorization), anonymous), SESSION_COOKIE));
        assertThat(get("/api/v1/me", loggedIn).statusCode()).isEqualTo(200);

        // 인스턴스 메모리가 아니라 DB 행이 session의 유일한 저장소다. 행이 없으면 같은 cookie로도 인증되지 않는다.
        jdbc.update("delete from spring_session where session_id = ?", sessionId(loggedIn));
        assertThat(get("/api/v1/me", loggedIn).statusCode()).isEqualTo(401);
    }

    private static void assertSessionCookieAttributes(String setCookie) {
        var attributes = Arrays.stream(setCookie.split(";")).skip(1)
                .map(value -> value.trim().toLowerCase(Locale.ROOT)).toList();
        assertThat(attributes).contains("path=/", "secure", "httponly", "samesite=lax",
                "max-age=" + Duration.ofDays(7).toSeconds());
    }

    private List<String> storedSessionIds() {
        return jdbc.queryForList("select session_id from spring_session", String.class);
    }

    private Map<String, Object> storedAttributes(String sessionId) {
        var deserializer = new DeserializingConverter(getClass().getClassLoader());
        var attributes = new LinkedHashMap<String, Object>();
        jdbc.query("""
                select a.attribute_name, a.attribute_bytes from spring_session_attributes a
                join spring_session s on s.primary_id = a.session_primary_id where s.session_id = ?""",
                rs -> { attributes.put(rs.getString(1), deserializer.convert(rs.getBytes(2))); }, sessionId);
        return attributes;
    }

    private HttpResponse<String> get(String path, String cookie) throws Exception {
        var request = HttpRequest.newBuilder(uri(path)).GET();
        if (cookie != null) request.header("Cookie", cookie);
        return send(request);
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static String callbackPath(HttpResponse<String> authorization) {
        var state = queryParam(location(authorization), "state");
        return "/login/oauth2/code/auth0?code=fixture-code&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8);
    }

    private static String location(HttpResponse<String> response) {
        return response.headers().firstValue("Location").orElseThrow();
    }

    private static String queryParam(String url, String name) {
        var value = UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst(name);
        return value == null ? null : URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String setCookie(HttpResponse<String> response, String name) {
        return response.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith(name + "="))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError(name + " cookie가 발급되지 않았습니다: " + response.headers().map()));
    }

    private static String cookiePair(String setCookie) {
        return setCookie.split(";", 2)[0];
    }

    /** Spring Session의 기본 cookie 값은 session id의 Base64다. */
    private static String sessionId(String cookiePair) {
        var value = cookiePair.substring(cookiePair.indexOf('=') + 1);
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static String nonceHash(String nonce) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(nonce.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class OidcFixture {
        /** 운영 chain은 {@code @Profile("!local & !test")}이므로 test profile에서 같은 구성 method로 만든다. */
        @Bean
        SecurityFilterChain applicationSecurity(HttpSecurity http, ClientRegistrationRepository registrations,
                                                OidcApplicationUserService oidcUsers, AuthProperties properties)
                throws Exception {
            return new SecurityConfiguration().applicationSecurity(http, registrations, oidcUsers, properties);
        }

        /** ID token 값은 authorization request nonce의 hash다. decoder가 이를 nonce claim으로 되돌린다. */
        @Bean
        OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> accessTokenResponseClient() {
            return grantRequest -> {
                var nonce = (String) grantRequest.getAuthorizationExchange().getAuthorizationRequest()
                        .getAttributes().get(OidcParameterNames.NONCE);
                return OAuth2AccessTokenResponse.withToken("fixture-access-token")
                        .tokenType(OAuth2AccessToken.TokenType.BEARER)
                        .expiresIn(60)
                        .additionalParameters(Map.of(OidcParameterNames.ID_TOKEN, nonceHash(nonce)))
                        .build();
            };
        }

        @Bean
        JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory() {
            return registration -> token -> {
                var now = Instant.now();
                return Jwt.withTokenValue(token)
                        .header("alg", "RS256")
                        .issuer(ISSUER)
                        .subject(SUBJECT)
                        .audience(List.of(CLIENT_ID))
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(300))
                        .claim("nonce", token)
                        .claim("email", NEXT_EMAIL.get())
                        .claim("email_verified", true)
                        .claim("name", "Session Fixture")
                        .build();
            };
        }
    }
}
