package com.decisionassistant.identity;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration(proxyBeanMethods = false)
@Profile("!local & !test")
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain applicationSecurity(HttpSecurity http,
                                            ClientRegistrationRepository registrations,
                                            OidcApplicationUserService oidcUsers,
                                            AuthProperties properties) throws Exception {
        var authorizationResolver = LoginRetryReauthentication.authorizationRequestResolver(
                registrations, "/oauth2/authorization");

        var logoutSuccessHandler = new OidcClientInitiatedLogoutSuccessHandler(registrations);
        logoutSuccessHandler.setPostLogoutRedirectUri(properties.publicBaseUrl() + "/login");

        var failureHandler = LoginRetryReauthentication.requireOnFailure(new SimpleUrlAuthenticationFailureHandler(
                properties.publicBaseUrl() + "/login?error=access_denied"));

        configureCsrf(http, true);
        http
                .sessionManagement(session -> session
                        .addSessionAuthenticationStrategy(LoginRetryReauthentication.clearOnSuccess()))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/info", "/oauth2/**", "/login/**", "/error")
                        .permitAll()
                        .requestMatchers("/api/openapi", "/swagger-ui/**").denyAll()
                        .anyRequest().authenticated())
                .oauth2Login(oauth -> oauth
                        .authorizationEndpoint(endpoint -> endpoint
                                .authorizationRequestResolver(authorizationResolver))
                        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUsers))
                        .failureHandler(failureHandler))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler(logoutSuccessHandler))
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                request -> request.getRequestURI().startsWith("/api/")))
                .oauth2Client(Customizer.withDefaults());
        return http.build();
    }

    public static void configureCsrf(HttpSecurity http) throws Exception {
        configureCsrf(http, false);
    }

    public static void configureCsrf(HttpSecurity http, boolean secureCookie) throws Exception {
        var repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieName("XSRF-TOKEN");
        repository.setHeaderName("X-XSRF-TOKEN");
        repository.setCookieCustomizer(cookie -> cookie.path("/").sameSite("Lax").secure(secureCookie));
        http.csrf(csrf -> csrf
                        .csrfTokenRepository(repository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class);
    }
}
