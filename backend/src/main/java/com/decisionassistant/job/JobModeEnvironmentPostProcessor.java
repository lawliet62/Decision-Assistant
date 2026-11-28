package com.decisionassistant.job;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.reactive.ReactiveOAuth2ClientAutoConfiguration;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.TreeSet;

/**
 * job switch 하나로 실행 형태를 정한다. 운영자가 다른 환경 변수를 함께 맞추지 않아도 되도록
 * web server, Flyway migration, OIDC client 등록(issuer discovery 호출)을 끈다.
 * 예약 작업과 기동 시 catalog 작업은 {@link JobMode.Inactive} 조건으로 등록하지 않는다.
 */
public final class JobModeEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    static final String SOURCE = "jobMode";
    private static final String EXCLUDE = "spring.autoconfigure.exclude";

    @Override public int getOrder() { return ConfigDataEnvironmentPostProcessor.ORDER + 2; }

    @Override public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!JobMode.active(environment)) return;
        String job = environment.getProperty(JobMode.PROPERTY);
        if (!JobMode.SUPPORTED.contains(job))
            throw new IllegalStateException("Unknown " + JobMode.PROPERTY + " '" + job + "'. Supported jobs: "
                    + String.join(", ", new TreeSet<>(JobMode.SUPPORTED)));
        var values = new LinkedHashMap<String, Object>();
        values.put("spring.main.web-application-type", "none");
        // schema는 web service가 소유한다. job은 migration하지 않고 Hibernate validate만 거친다.
        values.put("spring.flyway.enabled", false);
        // non-web context에서는 servlet·reactive OAuth2 client 자동 구성이 모두 활성화되어 기동 중 issuer discovery를 호출한다.
        String exclude = environment.getProperty(EXCLUDE, "");
        String oauth2Clients = OAuth2ClientAutoConfiguration.class.getName() + "," + ReactiveOAuth2ClientAutoConfiguration.class.getName();
        values.put(EXCLUDE, exclude.isBlank() ? oauth2Clients : exclude + "," + oauth2Clients);
        environment.getPropertySources().addFirst(new MapPropertySource(SOURCE, values));
    }
}
