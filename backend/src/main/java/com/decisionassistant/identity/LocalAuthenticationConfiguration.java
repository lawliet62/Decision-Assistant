package com.decisionassistant.identity;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

import java.time.Instant;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
@Profile("local")
public class LocalAuthenticationConfiguration {

    public static final UUID LOCAL_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final String LOCAL_EMAIL = "local@decision-assistant.test";

    @Bean
    AppUserPrincipal localPrincipal() {
        return new AppUserPrincipal(LOCAL_USER_ID, LOCAL_EMAIL, "Local Developer");
    }

    @Bean
    CommandLineRunner initializeLocalUser(ApplicationUserRepository users) {
        return args -> users.findById(LOCAL_USER_ID).orElseGet(() -> users.save(new ApplicationUser(
                LOCAL_USER_ID, LOCAL_EMAIL, AllowedEmailPolicy.normalize(LOCAL_EMAIL), "Local Developer", Instant.now())));
    }

    @Bean
    SecurityFilterChain localSecurity(HttpSecurity http, AppUserPrincipal principal) throws Exception {
        SecurityConfiguration.configureCsrf(http, false);
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .addFilterBefore(new LocalAuthenticationFilter(principal), AnonymousAuthenticationFilter.class)
                .logout(logout -> logout.logoutSuccessUrl("/"));
        return http.build();
    }
}
