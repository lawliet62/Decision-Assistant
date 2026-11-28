package com.decisionassistant;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.session.SessionRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 같은 application을 job mode와 일반 mode로 직접 기동해 Cloud Run Job 일회 실행 경로를 검증한다. */
@Tag("integration") @Testcontainers @ExtendWith(OutputCaptureExtension.class)
class DecisionPurgeJobIntegrationTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");
    private static final String JOB = "--decision-assistant.job=purge-expired-decisions";
    // issuer discovery가 일어나면 연결 거부로 기동이 실패하는 OIDC 등록. 실제 Auth0에는 연결하지 않는다.
    private static final String[] OIDC = {
            "--spring.security.oauth2.client.registration.auth0.provider=auth0",
            "--spring.security.oauth2.client.registration.auth0.client-id=job-test",
            "--spring.security.oauth2.client.registration.auth0.client-secret=job-test",
            "--spring.security.oauth2.client.registration.auth0.authorization-grant-type=authorization_code",
            "--spring.security.oauth2.client.registration.auth0.redirect-uri=http://localhost/login/oauth2/code/auth0",
            "--spring.security.oauth2.client.registration.auth0.scope=openid",
            "--spring.security.oauth2.client.provider.auth0.issuer-uri=http://127.0.0.1:9/issuer"};
    static JdbcTemplate jdbc;
    static UUID owner = UUID.randomUUID();

    @BeforeAll static void migrate() {
        // job은 migration하지 않으므로 web service가 만든 schema를 먼저 준비한다.
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).load().migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        jdbc.update("insert into app_users(id, email, email_normalized, display_name, created_at, last_login_at) values (?, 'job@example.test', 'job@example.test', 'Job', now(), now())", owner);
    }

    @BeforeEach void reset() { jdbc.update("delete from decisions"); }

    @Test void jobPurgesEveryExpiredBatchAndLeavesOtherDecisionsThenIsANoOp(CapturedOutput output) {
        seed("expired", 205, "now() - interval '31 days'", false);
        seed("recent-trash", 3, "now() - interval '29 days'", false);
        seed("active", 2, null, false);
        seed("archived", 1, null, true);
        long history = count("select count(*) from flyway_schema_history");
        long catalog = count("select count(*) from catalog_products");

        for (int run = 0; run < 2; run++) {
            var args = new ArrayList<>(List.of(JOB, "--decision-assistant.catalog.enabled=true"));
            args.addAll(List.of(OIDC));
            try (var context = start(args.toArray(String[]::new))) {
                assertThat(context).isNotInstanceOf(WebApplicationContext.class);
                assertThat(context.getBeansOfType(ScheduledTaskHolder.class)).isEmpty();
                assertThat(context.getBeanNamesForType(Flyway.class)).isEmpty();
                assertThat(context.getBeanNamesForType(com.decisionassistant.guidance.CatalogImporter.class)).isEmpty();
                assertThat(context.getBeanNamesForType(com.decisionassistant.guidance.CatalogStartupRefresh.class)).isEmpty();
                assertThat(context.getBeanNamesForType(ClientRegistrationRepository.class)).isEmpty();
                assertThat(context.getBeanNamesForType(ReactiveClientRegistrationRepository.class)).isEmpty();
                // JDBC session 자동 구성은 servlet web application에서만 켜지므로 job은 session 저장소·정리 작업이 없다.
                assertThat(context.getBeanNamesForType(SessionRepository.class)).isEmpty();
                assertThat(SpringApplication.exit(context)).isZero();
            }
            assertThat(titled("expired")).isZero();
            assertThat(titled("recent-trash")).isEqualTo(3);
            assertThat(titled("active")).isEqualTo(2);
            assertThat(titled("archived")).isEqualTo(1);
        }
        assertThat(count("select count(*) from flyway_schema_history")).isEqualTo(history);
        assertThat(count("select count(*) from catalog_products")).isEqualTo(catalog);
        assertThat(output.getOut())
                .contains("product_event=decision_purge_run outcome=success purged=205 remaining=0")
                .contains("product_event=decision_purge_run outcome=success purged=0 remaining=0")
                .contains("product_event=decision_deleted decision_id=");
    }

    @Test void failedPurgeYieldsNonZeroExitCode(CapturedOutput output) {
        seed("expired", 2, "now() - interval '31 days'", false);
        jdbc.execute("create function reject_decision_delete() returns trigger language plpgsql as $$ begin raise exception 'blocked by test'; end $$");
        jdbc.execute("create trigger reject_decision_delete before delete on decisions for each row execute function reject_decision_delete()");
        try (var context = start(JOB)) {
            assertThat(SpringApplication.exit(context)).isNotZero();
        } finally {
            jdbc.execute("drop trigger reject_decision_delete on decisions");
            jdbc.execute("drop function reject_decision_delete()");
        }
        assertThat(titled("expired")).isEqualTo(2);
        assertThat(output.getOut()).contains("product_event=decision_purge_run outcome=failed purged=0 remaining=unknown");
    }

    @Test void unknownJobNameFailsStartup() {
        assertThatThrownBy(() -> start("--decision-assistant.job=purge-everything"))
                .hasStackTraceContaining("Unknown decision-assistant.job 'purge-everything'");
    }

    @Test void normalModeKeepsWebServerAndScheduledPurgeWorker() {
        try (var context = start()) {
            assertThat(context).isInstanceOf(WebApplicationContext.class);
            var tasks = context.getBeansOfType(ScheduledTaskHolder.class).values().stream()
                    .flatMap(holder -> holder.getScheduledTasks().stream())
                    .map(task -> task.getTask().getRunnable().toString()).toList();
            assertThat(tasks).anyMatch(task -> task.contains("DecisionPurgeWorker.purge"));
            // 만료 session 정리는 @Scheduled가 아니라 JDBC session 저장소가 자체 scheduler로 매분 0초에 실행한다.
            var sessions = context.getBean(JdbcIndexedSessionRepository.class);
            assertThat(ReflectionTestUtils.getField(sessions, "cleanupCron"))
                    .isEqualTo(JdbcIndexedSessionRepository.DEFAULT_CLEANUP_CRON);
            assertThat(ReflectionTestUtils.getField(sessions, "taskScheduler")).isNotNull();
            assertThat(context.containsBean("decisionPurgeJob")).isFalse();
            assertThat(context.getBeanNamesForType(Flyway.class)).isNotEmpty();
        }
    }

    @Test void nonWebStartupWithoutJobModeWouldCallOidcDiscovery() {
        // job mode가 OIDC client 자동 구성을 제외하는 이유: web server만 끄면 기동 중 issuer discovery를 호출한다.
        var args = new ArrayList<>(List.of("--spring.main.web-application-type=none"));
        args.addAll(List.of(OIDC));
        assertThatThrownBy(() -> start(args.toArray(String[]::new)))
                .hasStackTraceContaining("127.0.0.1:9");
    }

    private static ConfigurableApplicationContext start(String... extra) {
        var args = new ArrayList<>(List.of("--spring.profiles.active=test", "--spring.main.banner-mode=off", "--server.port=0",
                "--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword()));
        args.addAll(List.of(extra));
        return SpringApplication.run(DecisionAssistantApplication.class, args.toArray(String[]::new));
    }

    private static void seed(String title, int count, String deletedAt, boolean archived) {
        jdbc.update("insert into decisions(id, owner_user_id, title, template_key, template_version, status, created_at, updated_at, deleted_at, archived_at) "
                + "select gen_random_uuid(), ?, ?, 'EARBUDS', 'fixture', 'DRAFT', now(), now(), " + (deletedAt == null ? "null" : deletedAt)
                + ", " + (archived ? "now()" : "null") + " from generate_series(1, ?)", owner, title, count);
    }

    private static long titled(String title) { return jdbc.queryForObject("select count(*) from decisions where title = ?", Long.class, title); }
    private static long count(String sql) { return jdbc.queryForObject(sql, Long.class); }
}
