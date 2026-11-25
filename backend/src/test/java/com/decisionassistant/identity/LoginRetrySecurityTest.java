package com.decisionassistant.identity;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 접근 거부 이후의 재로그인만 Auth0 로그인 화면을 다시 요구하는지 OIDC filter chain 전체로 검증한다(이슈 #84).
 */
@SpringJUnitWebConfig(LoginRetrySecurityTest.WebConfig.class)
class LoginRetrySecurityTest {
    private static final String ISSUER = "https://issuer.example.test";
    private static final String APP_URL = "https://app.example.test";
    private static final String CLIENT_ID = "fixture-client";
    private static final String AUTHORIZE = APP_URL + "/oauth2/authorization/auth0";
    private static final String DENIED_TARGET = APP_URL + "/login?error=access_denied";

    @Autowired
    WebApplicationContext context;

    @Autowired
    OidcApplicationUserService oidcUsers;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        reset(oidcUsers);
    }

    @Test
    void firstLoginInFreshSessionSendsNoPromptAndKeepsPkce() throws Exception {
        var params = authorize(new MockHttpSession(context.getServletContext()));

        assertThat(params).doesNotContainKey("prompt");
        assertThat(params.getFirst("code_challenge_method")).isEqualTo("S256");
        assertThat(params.getFirst("code_challenge")).isNotBlank();
        assertThat(params.getFirst("state")).isNotBlank();
        assertThat(params.getFirst("nonce")).isNotBlank();
    }

    @Test
    void retryAfterDenialRequiresLoginPromptUntilLoginSucceeds() throws Exception {
        var session = new MockHttpSession(context.getServletContext());
        denyNextLogin();

        var denied = authorize(session);
        assertThat(denied).doesNotContainKey("prompt");
        callback(session, denied, DENIED_TARGET);

        var retry = authorize(session);
        assertThat(retry.getFirst("prompt")).isEqualTo("login");
        assertThat(retry.getFirst("code_challenge_method")).isEqualTo("S256");
        assertThat(retry.getFirst("code_challenge")).isNotBlank();
        assertThat(retry.getFirst("nonce")).isNotBlank();
        assertThat(retry.getFirst("state")).isNotEqualTo(denied.getFirst("state"));

        // Auth0 화면에서 돌아오지 않고 다시 로그인을 눌러도 계속 로그인 화면을 요구한다.
        var abandonedRetry = authorize(session);
        assertThat(abandonedRetry.getFirst("prompt")).isEqualTo("login");

        allowNextLogin();
        var sessionId = session.getId();
        mvc.perform(get(APP_URL + "/login/oauth2/code/auth0")
                        .session(session)
                        .param("code", "fixture-code")
                        .param("state", abandonedRetry.getFirst("state")))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/"));
        assertThat(session.getId()).as("session fixation 보호는 유지된다").isNotEqualTo(sessionId);
        mvc.perform(get("/api/v1/me").secure(true).session(session)).andExpect(status().isOk());

        // 같은 browser session에서 이후 로그인 요청은 다시 기본 동작으로 돌아간다.
        assertThat(authorize(session)).doesNotContainKey("prompt");
    }

    @Test
    void logoutAfterRecoveredLoginStartsNextLoginWithoutPrompt() throws Exception {
        var session = new MockHttpSession(context.getServletContext());
        denyNextLogin();
        callback(session, authorize(session), DENIED_TARGET);
        allowNextLogin();
        callback(session, authorize(session), "/");

        mvc.perform(post("/logout").secure(true).session(session)
                        .cookie(new Cookie("XSRF-TOKEN", "fixture-csrf"))
                        .param("_csrf", "fixture-csrf"))
                .andExpect(status().isFound());
        assertThat(session.isInvalid()).isTrue();

        assertThat(authorize(new MockHttpSession(context.getServletContext()))).doesNotContainKey("prompt");
    }

    @Test
    void successfulLoginStillReturnsToSavedRequest() throws Exception {
        var session = new MockHttpSession(context.getServletContext());
        mvc.perform(get(APP_URL + "/decisions/fixture").session(session).accept(MediaType.TEXT_HTML))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/oauth2/authorization/auth0"));

        allowNextLogin();
        // MockMvc는 절대 https URI에도 기본 port 80을 붙이므로 host·port가 아닌 저장된 경로만 비교한다.
        mvc.perform(get(APP_URL + "/login/oauth2/code/auth0")
                        .session(session)
                        .param("code", "fixture-code")
                        .param("state", authorize(session).getFirst("state")))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", endsWith("/decisions/fixture?continue")));
    }

    @Test
    void auth0ErrorResponseAlsoRequiresLoginPromptOnRetry() throws Exception {
        var session = new MockHttpSession(context.getServletContext());
        var first = authorize(session);

        mvc.perform(get(APP_URL + "/login/oauth2/code/auth0")
                        .session(session)
                        .param("error", "access_denied")
                        .param("state", first.getFirst("state")))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", DENIED_TARGET));

        assertThat(authorize(session).getFirst("prompt")).isEqualTo("login");
    }

    @Test
    void denialInOneSessionDoesNotAffectAnotherSession() throws Exception {
        var deniedSession = new MockHttpSession(context.getServletContext());
        denyNextLogin();
        callback(deniedSession, authorize(deniedSession), DENIED_TARGET);

        var otherSession = new MockHttpSession(context.getServletContext());
        assertThat(authorize(otherSession)).doesNotContainKey("prompt");
        assertThat(authorize(deniedSession).getFirst("prompt")).isEqualTo("login");
    }

    @Test
    void requestParametersCannotRemovePromptOrInjectAuthorizationParameters() throws Exception {
        var fresh = new MockHttpSession(context.getServletContext());
        var injected = authorize(fresh, Map.of(
                "prompt", "none",
                "max_age", "0",
                "login_hint", "someone@example.test",
                "connection", "google-oauth2",
                "reauthenticate", "true"));
        assertThat(injected).doesNotContainKeys("prompt", "max_age", "login_hint", "connection", "reauthenticate");

        var session = new MockHttpSession(context.getServletContext());
        denyNextLogin();
        callback(session, authorize(session), DENIED_TARGET);
        var retry = authorize(session, Map.of("prompt", "none", "reauthenticate", "false"));
        assertThat(retry.get("prompt")).containsExactly("login");
        assertThat(retry).doesNotContainKey("reauthenticate");
    }

    private MultiValueMap<String, String> authorize(MockHttpSession session) throws Exception {
        return authorize(session, Map.of());
    }

    private MultiValueMap<String, String> authorize(MockHttpSession session, Map<String, String> extraParams)
            throws Exception {
        var request = get(AUTHORIZE).session(session);
        extraParams.forEach(request::param);
        var location = mvc.perform(request)
                .andExpect(status().isFound())
                .andReturn().getResponse().getHeader("Location");
        assertThat(location).startsWith(ISSUER + "/authorize?");
        var raw = UriComponentsBuilder.fromUriString(location).build().getQueryParams();
        var decoded = new LinkedMultiValueMap<String, String>();
        raw.forEach((key, values) -> values.forEach(value ->
                decoded.add(key, value == null ? null : URLDecoder.decode(value, StandardCharsets.UTF_8))));
        return decoded;
    }

    private void callback(MockHttpSession session, MultiValueMap<String, String> authorization, String expectedTarget)
            throws Exception {
        mvc.perform(get(APP_URL + "/login/oauth2/code/auth0")
                        .session(session)
                        .param("code", "fixture-code")
                        .param("state", authorization.getFirst("state")))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", expectedTarget));
    }

    private void denyNextLogin() {
        when(oidcUsers.loadUser(any(OidcUserRequest.class)))
                .thenThrow(new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.ACCESS_DENIED)));
    }

    private void allowNextLogin() {
        reset(oidcUsers);
        when(oidcUsers.loadUser(any(OidcUserRequest.class))).thenAnswer(invocation -> {
            OidcUserRequest request = invocation.getArgument(0);
            return new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")), request.getIdToken());
        });
    }

    /**
     * 테스트용 ID token 값은 authorization request nonce의 hash다. decoder가 이를 nonce claim으로 되돌린다.
     */
    private static String nonceHash(String nonce) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(nonce.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfiguration.class, IdentityController.class})
    static class WebConfig {
        @Bean
        AuthProperties authProperties() {
            return new AuthProperties(List.of("fixture@example.test"), APP_URL);
        }

        @Bean
        InMemoryClientRegistrationRepository registrations() {
            var auth0 = ClientRegistration.withRegistrationId("auth0")
                    .clientId(CLIENT_ID)
                    .clientSecret("fixture-secret")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri(APP_URL + "/login/oauth2/code/auth0")
                    .scope("openid")
                    .authorizationUri(ISSUER + "/authorize")
                    .tokenUri(ISSUER + "/oauth/token")
                    .jwkSetUri(ISSUER + "/.well-known/jwks.json")
                    .providerConfigurationMetadata(Map.of("end_session_endpoint", ISSUER + "/oidc/logout"))
                    .build();
            return new InMemoryClientRegistrationRepository(auth0);
        }

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
                        .subject("fixture-user")
                        .audience(List.of(CLIENT_ID))
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(60))
                        .claim("nonce", token)
                        .build();
            };
        }

        @Bean
        OidcApplicationUserService oidcUsers() {
            return mock(OidcApplicationUserService.class);
        }

        @Bean
        CurrentUserProvider currentUser() {
            var provider = mock(CurrentUserProvider.class);
            when(provider.current()).thenReturn(new CurrentUser(UUID.randomUUID(), "fixture@example.test", "Fixture User"));
            return provider;
        }
    }
}
