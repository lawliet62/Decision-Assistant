package com.decisionassistant.identity;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CurrentUserProvider {

    private final ApplicationUserRepository users;
    private final ExternalIdentityRepository identities;
    private final AllowedEmailPolicy allowedEmails;

    public CurrentUserProvider(ApplicationUserRepository users, ExternalIdentityRepository identities,
                               AllowedEmailPolicy allowedEmails) {
        this.users = users;
        this.identities = identities;
        this.allowedEmails = allowedEmails;
    }

    @Transactional(readOnly = true)
    public CurrentUser current() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("인증이 필요합니다.");
        }

        if (authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            var user = users.findById(principal.userId())
                    .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로컬 사용자를 찾을 수 없습니다."));
            return current(user);
        }

        if (authentication.getPrincipal() instanceof OidcUser oidcUser) {
            var issuer = oidcUser.getIssuer() == null ? "" : oidcUser.getIssuer().toString();
            var identity = identities.findByIssuerAndSubject(issuer, oidcUser.getSubject())
                    .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("등록된 identity를 찾을 수 없습니다."));
            var user = users.findById(identity.userId())
                    .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("등록된 사용자를 찾을 수 없습니다."));
            if (!allowedEmails.allows(user.emailNormalized())) {
                throw new AccessDeniedException("현재 allowlist에 포함된 사용자만 접근할 수 있습니다.");
            }
            return current(user);
        }

        throw new AuthenticationCredentialsNotFoundException("지원하지 않는 인증 principal입니다.");
    }

    private CurrentUser current(ApplicationUser user) {
        return new CurrentUser(user.id(), user.email(), user.displayName());
    }
}
