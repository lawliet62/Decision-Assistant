package com.decisionassistant.identity;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(LogoutSecurityTest.WebConfig.class)
class LogoutSecurityTest {
    private static final String ISSUER = "https://issuer.example.test";
    private static final String APP_URL = "https://app.example.test";
    private static final String LOGOUT_ENDPOINT = ISSUER + "/oidc/logout";

    @Autowired
    WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void rotatedCookieRejectsStaleFormAndCurrentFormInvalidatesExistingSession() throws Exception {
        var session = new MockHttpSession(context.getServletContext());
        var now = Instant.now();
        var idToken = new OidcIdToken("fixture-id-token", now, now.plusSeconds(60), Map.of("sub", "fixture-user"));
        var user = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")), idToken);
        var authentication = new OAuth2AuthenticationToken(user, user.getAuthorities(), "auth0");
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(authentication));

        var currentToken = new Cookie("XSRF-TOKEN", "rotated-token");
        mvc.perform(post("/logout").secure(true).session(session).cookie(currentToken).param("_csrf", "old-token"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/me").secure(true).session(session)).andExpect(status().isOk());

        mvc.perform(post("/logout").secure(true).session(session).cookie(currentToken).param("_csrf", currentToken.getValue()))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", startsWith(LOGOUT_ENDPOINT + "?")));

        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/v1/me").secure(true).session(session)).andExpect(status().isUnauthorized());
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
                    .clientId("fixture-client")
                    .clientSecret("fixture-secret")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri(APP_URL + "/login/oauth2/code/auth0")
                    .scope("openid")
                    .authorizationUri(ISSUER + "/authorize")
                    .tokenUri(ISSUER + "/oauth/token")
                    .providerConfigurationMetadata(Map.of("end_session_endpoint", LOGOUT_ENDPOINT))
                    .build();
            return new InMemoryClientRegistrationRepository(auth0);
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
