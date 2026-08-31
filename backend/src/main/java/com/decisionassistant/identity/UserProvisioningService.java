package com.decisionassistant.identity;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class UserProvisioningService {

    private static final OAuth2Error ACCESS_DENIED = new OAuth2Error("access_denied");

    private final ApplicationUserRepository users;
    private final ExternalIdentityRepository identities;
    private final AllowedEmailPolicy allowedEmails;
    private final Clock clock = Clock.systemUTC();

    public UserProvisioningService(ApplicationUserRepository users, ExternalIdentityRepository identities,
                                   AllowedEmailPolicy allowedEmails) {
        this.users = users;
        this.identities = identities;
        this.allowedEmails = allowedEmails;
    }

    @Transactional
    public ApplicationUser provision(OidcUser oidcUser) {
        var email = oidcUser.getEmail();
        var normalized = AllowedEmailPolicy.normalize(email);
        if (!Boolean.TRUE.equals(oidcUser.getEmailVerified()) || !allowedEmails.allows(normalized)) {
            throw denied("허용된 verified email만 로그인할 수 있습니다.");
        }
        var issuer = oidcUser.getIssuer() == null ? "" : oidcUser.getIssuer().toString();
        var subject = oidcUser.getSubject();
        if (issuer.isBlank() || subject == null || subject.isBlank()) {
            throw denied("OIDC issuer와 subject가 필요합니다.");
        }

        var now = Instant.now(clock);
        var existingIdentity = identities.findByIssuerAndSubject(issuer, subject);
        if (existingIdentity.isPresent()) {
            var identity = existingIdentity.orElseThrow();
            var user = users.findById(identity.userId()).orElseThrow();
            users.findByEmailNormalized(normalized)
                    .filter(other -> !other.id().equals(user.id()))
                    .ifPresent(other -> { throw denied("이 이메일은 다른 로그인 방식에 연결되어 있습니다."); });
            user.login(email, normalized, oidcUser.getFullName(), now);
            identity.loggedIn(now);
            return user;
        }

        if (users.findByEmailNormalized(normalized).isPresent()) {
            throw denied("이 이메일은 이미 등록되어 있습니다. 처음 사용한 로그인 방식으로 로그인하세요.");
        }

        try {
            var user = users.saveAndFlush(new ApplicationUser(
                    UUID.randomUUID(), email, normalized, oidcUser.getFullName(), now));
            identities.saveAndFlush(new ExternalIdentity(user.id(), issuer, subject, now));
            return user;
        } catch (DataIntegrityViolationException exception) {
            throw denied("이미 등록된 identity 또는 이메일입니다.");
        }
    }

    private OAuth2AuthenticationException denied(String message) {
        return new OAuth2AuthenticationException(ACCESS_DENIED, message);
    }
}
