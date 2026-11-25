package com.decisionassistant.identity;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;

/**
 * 로그인 실패 뒤의 재시도만 Auth0 로그인 화면을 다시 요구한다(BR-AUTH-01, 이슈 #84).
 *
 * <p>실패하면 서버 session에 재인증 표시를 남기고, 표시가 있는 동안의 authorization request에만
 * {@code prompt=login}을 붙인다. 표시는 로그인이 성공할 때 지운다. 요청 parameter는 표시를 읽거나
 * 바꾸지 않으며, 첫 로그인의 authorization request는 기존과 같다.
 */
final class LoginRetryReauthentication {

    static final String SESSION_ATTRIBUTE = LoginRetryReauthentication.class.getName() + ".REQUIRED";

    private LoginRetryReauthentication() {
    }

    static OAuth2AuthorizationRequestResolver authorizationRequestResolver(
            ClientRegistrationRepository registrations, String authorizationRequestBaseUri) {
        var normal = new DefaultOAuth2AuthorizationRequestResolver(registrations, authorizationRequestBaseUri);
        normal.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());

        var reauthentication = new DefaultOAuth2AuthorizationRequestResolver(registrations, authorizationRequestBaseUri);
        reauthentication.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce()
                .andThen(builder -> builder.additionalParameters(parameters -> parameters.put("prompt", "login"))));

        return new OAuth2AuthorizationRequestResolver() {
            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
                return (isRequired(request) ? reauthentication : normal).resolve(request);
            }

            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
                return (isRequired(request) ? reauthentication : normal).resolve(request, clientRegistrationId);
            }
        };
    }

    static AuthenticationFailureHandler requireOnFailure(AuthenticationFailureHandler delegate) {
        return (request, response, exception) -> {
            request.getSession().setAttribute(SESSION_ATTRIBUTE, Boolean.TRUE);
            delegate.onAuthenticationFailure(request, response, exception);
        };
    }

    static SessionAuthenticationStrategy clearOnSuccess() {
        return (authentication, request, response) -> {
            var session = request.getSession(false);
            if (session != null) {
                session.removeAttribute(SESSION_ATTRIBUTE);
            }
        };
    }

    private static boolean isRequired(HttpServletRequest request) {
        var session = request.getSession(false);
        return session != null && Boolean.TRUE.equals(session.getAttribute(SESSION_ATTRIBUTE));
    }
}
