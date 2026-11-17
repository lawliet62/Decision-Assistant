package com.decisionassistant;

import com.decisionassistant.identity.AppUserPrincipal;
import com.decisionassistant.identity.ApplicationUser;
import com.decisionassistant.identity.ApplicationUserRepository;
import com.decisionassistant.identity.SecurityConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Import(DecisionApiIntegrationTest.TestSecurityConfiguration.class)
class DecisionApiIntegrationTest {

    static final UUID TEST_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    static final AppUserPrincipal TEST_USER = new AppUserPrincipal(
            TEST_USER_ID, "owner@example.com", "Owner");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    com.decisionassistant.decision.DecisionRetentionService retention;

    @Autowired
    com.decisionassistant.preference.PreferenceMemoryRepository preferenceMemories;

    @Autowired
    MockMvc mvc;

    @Autowired
    ApplicationUserRepository users;

    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired javax.sql.DataSource dataSource;
    @Autowired com.decisionassistant.decision.DecisionRepository decisionRepository;
    @Autowired com.decisionassistant.notification.OutcomeReminderRepository reminderRepository;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Test @org.junit.jupiter.api.Timeout(15)
    void concurrentDeletesAndAConcurrentUpdateCannotSilentlyDeleteANewerVersion() throws Exception {
        var owner = createOtherUser();
        var id = seedDecision(owner, "동시 수정", "DRAFT", "EARBUDS");
        try (var connection = dataSource.getConnection(); var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("select id from decisions where id = ? for update")) {
                statement.setObject(1, id); statement.executeQuery().close();
            }
            var started = new java.util.concurrent.CountDownLatch(1);
            var deletion = pool.submit(() -> { started.countDown(); return deleteStatus(owner, id, 0); });
            org.assertj.core.api.Assertions.assertThat(started.await(3, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            try {
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> deletion.get(200, java.util.concurrent.TimeUnit.MILLISECONDS))
                        .isInstanceOf(java.util.concurrent.TimeoutException.class);
                try (var statement = connection.prepareStatement("update decisions set version = version + 1 where id = ?")) {
                    statement.setObject(1, id); statement.executeUpdate();
                }
            } finally { connection.commit(); }
            org.assertj.core.api.Assertions.assertThat(deletion.get(5, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(409);
            var first = pool.submit(() -> deleteStatus(owner, id, 1));
            var second = pool.submit(() -> deleteStatus(owner, id, 1));
            org.assertj.core.api.Assertions.assertThat(List.of(first.get(5, java.util.concurrent.TimeUnit.SECONDS), second.get(5, java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(204, 404);
        }
    }

    @Test @org.junit.jupiter.api.Timeout(15)
    void aReminderAlreadySelectedBeforeDeletionDoesNotSendAfterDeletionCommits() throws Exception {
        var owner = createOtherUser();
        var id = seedDecision(owner, "삭제와 알림", "CHOSEN", "EARBUDS");
        var reminder = reminderRepository.saveAndFlush(new com.decisionassistant.notification.OutcomeReminder(id,
                Instant.now().minusSeconds(60), com.decisionassistant.notification.OutcomeReminder.Channel.EMAIL, Instant.now()));
        var delivery = org.mockito.Mockito.mock(com.decisionassistant.notification.ReminderDeliveryPort.class);
        // 실제 repository가 선택한 엔티티를 넘기되 조회 시점을 latch로 고정한다.
        var selected = new java.util.concurrent.CountDownLatch(1);
        var selectedRepository = org.mockito.Mockito.mock(com.decisionassistant.notification.OutcomeReminderRepository.class);
        org.mockito.Mockito.when(selectedRepository.findTop10ByStatusAndDueAtLessThanEqualOrderByDueAtAsc(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> { selected.countDown(); return List.of(reminder); });
        var worker = new com.decisionassistant.notification.OutcomeReminderWorker(selectedRepository, decisionRepository, users, delivery,
                new com.decisionassistant.notification.ReminderProperties(true, "https://example.com", "fixture", "fixture@example.com"), "https://example.com");
        try (var connection = dataSource.getConnection(); var pool = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("delete from decisions where id = ?")) {
                statement.setObject(1, id); statement.executeUpdate();
            }
            var future = pool.submit(() -> new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(tx -> worker.sendDue()));
            try {
                org.assertj.core.api.Assertions.assertThat(selected.await(3, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            } finally { connection.commit(); }
            future.get(5, java.util.concurrent.TimeUnit.SECONDS);
        }
        org.mockito.Mockito.verifyNoInteractions(delivery);
        org.assertj.core.api.Assertions.assertThat(reminderRepository.findById(reminder.id())).isEmpty();
    }

    @Test @org.junit.jupiter.api.Timeout(15)
    void aReminderSelectedBeforeReconsiderationCannotSendForTheOldResolution() throws Exception {
        var created = revisionFixture(); var id = UUID.fromString(created.path("id").asText());
        var resolved = resolveRevision(id.toString(), "CHOSEN", created.path("options").get(0).path("id").asText());
        reminderRepository.saveAndFlush(new com.decisionassistant.notification.OutcomeReminder(id, Instant.now(), com.decisionassistant.notification.OutcomeReminder.Channel.EMAIL, Instant.now()));
        var seededReminder = reminderRepository.findByDecisionIdOrderByDueAtAsc(id).getFirst(); seededReminder.resolution(UUID.fromString(resolved.path("id").asText())); reminderRepository.saveAndFlush(seededReminder);
        var reminder = reminderRepository.findByDecisionIdOrderByDueAtAsc(id).stream()
                .filter(r -> r.channel() == com.decisionassistant.notification.OutcomeReminder.Channel.EMAIL).findFirst().orElseThrow();
        reminder.reschedule(Instant.now().minusSeconds(60)); reminderRepository.saveAndFlush(reminder);
        var selected = new java.util.concurrent.CountDownLatch(1);
        var selectedRepository = org.mockito.Mockito.mock(com.decisionassistant.notification.OutcomeReminderRepository.class);
        org.mockito.Mockito.when(selectedRepository.findTop10ByStatusAndDueAtLessThanEqualOrderByDueAtAsc(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> { selected.countDown(); return List.of(reminder); });
        org.mockito.Mockito.when(selectedRepository.pendingForDelivery(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> reminderRepository.pendingForDelivery(call.getArgument(0), call.getArgument(1)));
        var delivery = org.mockito.Mockito.mock(com.decisionassistant.notification.ReminderDeliveryPort.class);
        var worker = new com.decisionassistant.notification.OutcomeReminderWorker(selectedRepository, decisionRepository, users, delivery,
                new com.decisionassistant.notification.ReminderProperties(true, "https://example.com", "fixture", "fixture@example.com"), "https://example.com");
        try (var connection = dataSource.getConnection(); var pool = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            connection.setAutoCommit(false);
            // 새 선택이 먼저 확정되고 worker는 이전 resolution의 엔티티를 이미 읽은 순서를 재현한다.
            try (var statement = connection.prepareStatement("update decisions set current_resolution_id = ?, version = version + 1 where id = ?")) {
                statement.setObject(1, UUID.randomUUID()); statement.setObject(2, id); statement.executeUpdate();
            }
            var future = pool.submit(() -> new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(tx -> worker.sendDue()));
            try { org.assertj.core.api.Assertions.assertThat(selected.await(3, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); }
            finally { connection.commit(); }
            future.get(5, java.util.concurrent.TimeUnit.SECONDS);
        }
        org.mockito.Mockito.verifyNoInteractions(delivery);
        org.assertj.core.api.Assertions.assertThat(reminderRepository.findById(reminder.id()).orElseThrow().resolutionId().toString())
                .isEqualTo(resolved.path("id").asText());
    }

    private int deleteStatus(AppUserPrincipal owner, UUID id, int version) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/decisions/{id}", id)
                .param("expectedVersion", Integer.toString(version)).with(signedIn(owner)).with(csrf())).andReturn().getResponse().getStatus();
    }

    @Test
    void pagesFiltersAndSortsOnlyOwnedDecisionSummaries() throws Exception {
        var owner = createOtherUser();
        var ids = new java.util.ArrayList<UUID>();
        for (int i = 0; i < 13; i++) ids.add(seedDecision(owner, "같은 제목", "DRAFT", "EARBUDS"));
        seedDecision(owner, "완료", "CHOSEN", "LAPTOP");
        seedDecision(owner, "보류", "DEFERRED", "PHONE");
        seedDecision(createOtherUser(), "다른 사용자", "DRAFT", "EARBUDS");
        mvc.perform(get("/api/v1/decisions/selection").with(signedIn(owner)).param("statuses", "DRAFT", "CHOSEN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(14))
                .andExpect(jsonPath("$[0].id").isNotEmpty()).andExpect(jsonPath("$[0].version").value(0));
        mvc.perform(get("/api/v1/decisions/selection").with(signedIn(owner)).param("statuses", "ARCHIVED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/decisions/selection")).andExpect(status().isUnauthorized());
        var seen = new java.util.HashSet<String>();
        for (int page = 0; page < 2; page++) {
            var response = mapper.readTree(mvc.perform(get("/api/v1/decisions").with(signedIn(owner))
                            .param("statuses", "DRAFT").param("page", String.valueOf(page)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(13))
                    .andExpect(jsonPath("$.totalPages").value(2))
                    .andExpect(jsonPath("$.items.length()").value(page == 0 ? 10 : 3))
                    .andReturn().getResponse().getContentAsString());
            response.path("items").forEach(item -> org.assertj.core.api.Assertions.assertThat(seen.add(item.path("id").asText())).isTrue());
        }
        org.assertj.core.api.Assertions.assertThat(seen).hasSize(13);
        jdbc.update("update decisions set created_at = '2025-01-01', updated_at = '2027-01-01' where id = ?", ids.getFirst());
        jdbc.update("update decisions set created_at = '2027-01-01', updated_at = '2025-01-01' where id = ?", ids.get(1));
        mvc.perform(get("/api/v1/decisions").with(signedIn(owner)).param("sort", "CREATED_AT").param("direction", "ASC"))
                .andExpect(jsonPath("$.items[0].id").value(ids.getFirst().toString()));
        mvc.perform(get("/api/v1/decisions").with(signedIn(owner)).param("sort", "UPDATED_AT").param("direction", "ASC"))
                .andExpect(jsonPath("$.items[0].id").value(ids.get(1).toString()));
        mvc.perform(get("/api/v1/decisions").with(signedIn(owner)).param("statuses", "DRAFT", "CHOSEN"))
                .andExpect(jsonPath("$.totalElements").value(14));
        mvc.perform(get("/api/v1/decisions").with(signedIn(owner)).param("statuses", "ARCHIVED"))
                .andExpect(jsonPath("$.items.length()").value(0)).andExpect(jsonPath("$.totalPages").value(0));
        mvc.perform(get("/api/v1/decisions").with(signedIn(owner)).param("statuses", "DRAFT").param("page", "999"))
                .andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.items.length()").value(3));
        mvc.perform(get("/api/v1/decisions/counts").with(signedIn(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.status == 'DRAFT')].count").value(13))
                .andExpect(jsonPath("$[?(@.status == 'CHOSEN')].count").value(1));
        for (var sort : List.of("UPDATED_AT", "CREATED_AT", "TITLE", "TEMPLATE_KEY")) {
            var asc = mapper.readTree(mvc.perform(get("/api/v1/decisions").with(signedIn(owner))
                    .param("sort", sort).param("direction", "ASC").param("size", "50"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("items");
            var desc = mapper.readTree(mvc.perform(get("/api/v1/decisions").with(signedIn(owner))
                    .param("sort", sort).param("direction", "DESC").param("size", "50"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("items");
            org.assertj.core.api.Assertions.assertThat(asc.size()).isEqualTo(15);
            org.assertj.core.api.Assertions.assertThat(desc.size()).isEqualTo(15);
            var field = switch (sort) { case "UPDATED_AT" -> "updatedAt"; case "CREATED_AT" -> "createdAt"; case "TITLE" -> "title"; default -> "templateKey"; };
            org.assertj.core.api.Assertions.assertThat(asc.get(0).path(field)).isEqualTo(desc.get(14).path(field));
        }
        for (var params : List.of(new String[]{"page", "-1"}, new String[]{"page", "2147483647"}, new String[]{"size", "0"}, new String[]{"size", "51"},
                new String[]{"sort", "invalid"}, new String[]{"statuses", "invalid"}, new String[]{"direction", "invalid"})) {
            mvc.perform(get("/api/v1/decisions").with(signedIn(owner)).param(params[0], params[1]))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/v1/decisions/counts")).andExpect(status().isUnauthorized());
    }

    @Test
    void trashesThenPurgesOwnedAggregateAndDerivedMemoriesWithoutTouchingOtherDecisions() throws Exception {
        var owner = createOtherUser();
        var id = seedDecision(owner, "삭제할 결정", "CHOSEN", "EARBUDS");
        var keep = seedDecision(owner, "유지할 결정", "DRAFT", "EARBUDS");
        var option = UUID.randomUUID();
        var outcome = UUID.randomUUID();
        jdbc.update("insert into decision_options(id, decision_id, name) values (?, ?, '후보')", option, id);
        jdbc.update("insert into outcome_check_ins(id, decision_id, satisfaction, expectation_status, created_at) values (?, ?, 4, 'MET', now())", outcome, id);
        jdbc.update("insert into resolutions(id, decision_id, kind, selected_option_id, created_at) values (?, ?, 'CHOSEN', ?, now())", UUID.randomUUID(), id, option);
        jdbc.update("insert into evaluations(id, decision_id, decision_version, input_snapshot, input_hash, algorithm_result, llm_status, created_at) values (?, ?, 0, '{}', 'fixture', '{}', 'PENDING', now())", UUID.randomUUID(), id);
        jdbc.update("insert into structure_runs(id, decision_id, decision_version, input_snapshot, status, created_at) values (?, ?, 0, '{}', 'PENDING', now())", UUID.randomUUID(), id);
        jdbc.update("insert into evidence_imports(id, decision_id, option_id, url, status, created_at, updated_at) values (?, ?, ?, 'https://example.com', 'PENDING', now(), now())", UUID.randomUUID(), id, option);
        jdbc.update("insert into candidate_suggestions(id, decision_id, name, url, reason, status, created_at) values (?, ?, '후보', 'https://example.com', 'fixture', 'PROPOSED', now())", UUID.randomUUID(), id);
        jdbc.update("insert into decision_questions(id, decision_id, kind, target_key, prompt, status, priority, created_at) values (?, ?, 'TEXT', 'fixture', '질문', 'OPEN', 1, now())", UUID.randomUUID(), id);
        jdbc.update("insert into product_discoveries(decision_id, conditions, status, updated_at, prompt_version, schema_version) values (?, '{}', 'RUNNING', now(), 'fixture', 'fixture')", id);
        jdbc.update("insert into outcome_reminders(id, decision_id, due_at, status, channel, created_at) values (?, ?, now(), 'PENDING', 'EMAIL', now())", UUID.randomUUID(), id);
        for (var state : List.of("PROPOSED", "CONFIRMED", "DISMISSED")) {
            jdbc.update("insert into preference_memories(id, owner_user_id, statement, strength, status, confidence, source_decision_id, source_outcome_id, created_at, updated_at) values (?, ?, '기억', 1, ?, 'LOW', ?, ?, now(), now())", UUID.randomUUID(), owner.userId(), state, id, outcome);
        }
        var catalogBefore = jdbc.queryForObject("select count(*) from catalog_products", Long.class);
        var delete = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/decisions/{id}", id).param("expectedVersion", "0");
        mvc.perform(delete.with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/decisions/{id}", id)
                .param("expectedVersion", "0").with(signedIn(owner))).andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/decisions/{id}", id)
                .param("expectedVersion", "0").with(signedIn(createOtherUser())).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/decisions/{id}", id)
                .param("expectedVersion", "99").with(signedIn(owner)).with(csrf())).andExpect(status().isConflict());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from decisions where id = ?", Long.class, id)).isEqualTo(1);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/decisions/{id}", id)
                .param("expectedVersion", "0").with(signedIn(owner)).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/decisions/{id}", id).with(signedIn(owner))).andExpect(status().isNotFound());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from preference_memories where owner_user_id = ?", Long.class, owner.userId())).isEqualTo(3);
        // 만료 전에는 유지하고 기한 경과 후 기존 cascade를 검증한다.
        retention.purgeExpired(id, java.time.Instant.now());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from decisions where id = ?", Long.class, id)).isEqualTo(1);
        jdbc.update("update decisions set deleted_at = now() - interval '31 days' where id = ?", id);
        retention.purgeExpired(id, java.time.Instant.now());
        for (var table : List.of("decision_options", "criteria", "option_values", "decision_constraints", "constraint_results", "resolutions", "outcome_check_ins", "evaluations", "structure_runs", "evidence_imports", "candidate_suggestions", "decision_questions", "product_discoveries", "outcome_reminders")) {
            org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from " + table + " where decision_id = ?", Long.class, id)).as(table).isZero();
        }
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from preference_memories where owner_user_id = ?", Long.class, owner.userId())).isZero();
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from catalog_products", Long.class)).isEqualTo(catalogBefore);
        mvc.perform(get("/api/v1/decisions/{id}", keep).with(signedIn(owner))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/decisions/{id}", id).with(signedIn(owner))).andExpect(status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/decisions/{id}", id)
                .param("expectedVersion", "0").with(signedIn(owner)).with(csrf())).andExpect(status().isNotFound());
    }


    @Test
    void archiveTrashRestoreAndPersonalizationRespectOwnerVersionAndExpiry() throws Exception {
        var owner = createOtherUser();
        var id = seedDecision(owner, "완료 기록", "CHOSEN", "EARBUDS");
        var draft = seedDecision(owner, "진행 중", "DRAFT", "EARBUDS");
        jdbc.update("insert into preference_memories(id, owner_user_id, template_key, statement, strength, status, confidence, source_decision_id, created_at, updated_at) values (?, ?, 'EARBUDS', '가격 중시', 2, 'CONFIRMED', 'LOW', ?, now(), now())", UUID.randomUUID(), owner.userId(), id);
        var endpoint = "/api/v1/decisions/" + id + "/retention";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(endpoint)
                .with(signedIn(createOtherUser())).with(csrf()).contentType("application/json")
                .content("{\"expectedVersion\":0,\"action\":\"ARCHIVE\"}")).andExpect(status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/decisions/" + draft + "/retention")
                .with(signedIn(owner)).with(csrf()).contentType("application/json")
                .content("{\"expectedVersion\":0,\"action\":\"ARCHIVE\"}")).andExpect(status().isUnprocessableEntity());
        changeRetention(owner, id, 0, "ARCHIVE", 200);
        assertPersonalizationCount(owner, 1);
        mvc.perform(get("/api/v1/decisions/management").param("view", "ARCHIVE").with(signedIn(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(id.toString()));
        mvc.perform(get("/api/v1/decisions/selection").with(signedIn(owner)))
                .andExpect(jsonPath("$[*].id", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(id.toString()))));
        changeRetention(owner, id, 0, "UNARCHIVE", 409);
        changeRetention(owner, id, 1, "PERSONALIZATION_OFF", 200);
        assertPersonalizationCount(owner, 0);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select personalization_enabled from decisions where id = ?", Boolean.class, id)).isFalse();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/decisions/" + id)
                .param("expectedVersion", "2").with(signedIn(owner)).with(csrf())).andExpect(status().isNoContent());
        changeRetention(owner, id, 3, "RESTORE", 200);
        assertPersonalizationCount(owner, 0);
        // 복구는 이전 보관 여부와 개인화 설정을 보존한다.
        mvc.perform(get("/api/v1/decisions/management").param("view", "ARCHIVE").with(signedIn(owner)))
                .andExpect(jsonPath("$.items[0].personalizationEnabled").value(false));
        changeRetention(owner, id, 4, "PERSONALIZATION_ON", 200);
        assertPersonalizationCount(owner, 1);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/decisions/" + id)
                .param("expectedVersion", "5").with(signedIn(owner)).with(csrf())).andExpect(status().isNoContent());
        assertPersonalizationCount(owner, 0);
        org.assertj.core.api.Assertions.assertThat(preferenceMemories.findAllByOwnerUserIdOrderByUpdatedAtDesc(owner.userId())).isEmpty();
        // 만료 판정과 같은 애플리케이션 시계를 사용한다. Docker DB 시계와의 차이를 경계 판정에 섞지 않는다.
        jdbc.update("update decisions set deleted_at = ? where id = ?",
                java.sql.Timestamp.from(Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS).minusSeconds(1)), id);
        changeRetention(owner, id, 6, "RESTORE", 409);
    }

    private void assertPersonalizationCount(AppUserPrincipal owner, int expected) {
        org.assertj.core.api.Assertions.assertThat(preferenceMemories.findByOwnerUserIdAndStatusAndTemplateKey(
                owner.userId(), com.decisionassistant.preference.PreferenceMemory.Status.CONFIRMED,
                com.decisionassistant.decision.DecisionTypes.TemplateKey.EARBUDS)).hasSize(expected);
    }

    private void changeRetention(AppUserPrincipal owner, UUID id, int version, String action, int expected) throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/decisions/" + id + "/retention")
                .with(signedIn(owner)).with(csrf()).contentType("application/json")
                .content("{\"expectedVersion\":" + version + ",\"action\":\"" + action + "\"}"))
                .andExpect(status().is(expected));
    }

    private UUID seedDecision(AppUserPrincipal owner, String title, String status, String template) {
        var id = UUID.randomUUID();
        jdbc.update("insert into decisions(id, owner_user_id, title, template_key, template_version, status, created_at, updated_at) values (?, ?, ?, ?, 'fixture', ?, '2026-01-01', '2026-01-01')", id, owner.userId(), title, template, status);
        return id;
    }

    @Test
    void correctionPreservesSnapshotsAndOutcomesAndExcludesOldPersonalization() throws Exception {
        var created = revisionFixture();
        var id = created.path("id").asText();
        var first = resolveRevision(id, "CHOSEN", created.path("options").get(0).path("id").asText());
        var old = readDecision(id);
        mvc.perform(post("/api/v1/decisions/{id}/outcome-check-ins", id).with(signedIn()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"satisfaction":4,"expectationStatus":"MET","workedWell":"착용감",
                 "resolutionId":"%s","expectedVersion":%s}
                """.formatted(first.path("id").asText(), old.path("version"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.resolutionId").value(first.path("id").asText()));
        var memory = preferenceMemories.findAllByOwnerUserIdOrderByUpdatedAtDesc(TEST_USER_ID).stream()
                .filter(m -> m.sourceDecisionId().toString().equals(id)).findFirst().orElseThrow();
        memory.confirm(Instant.now()); preferenceMemories.saveAndFlush(memory);
        var editing = startRevision(id, "CORRECTION", old.path("version").asLong());
        org.assertj.core.api.Assertions.assertThat(preferenceMemories.findByOwnerUserIdAndStatusAndTemplateKey(
                TEST_USER_ID, com.decisionassistant.preference.PreferenceMemory.Status.CONFIRMED,
                com.decisionassistant.decision.DecisionTypes.TemplateKey.EARBUDS))
                .noneMatch(m -> m.id().equals(memory.id()));
        startRevisionStatus(id, "CORRECTION", old.path("version").asLong(), 409);
        mvc.perform(post("/api/v1/decisions/{id}/resolutions", id).with(signedIn()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"DEFERRED\"}"))
                .andExpect(status().isConflict());
        var draft = mapper.createObjectNode();
        for (var field : List.of("title", "context", "options", "criteria", "values", "constraints", "constraintResults"))
            draft.set(field, editing.path(field));
        draft.put("expectedVersion", editing.path("version").asLong());
        draft.put("title", "정정한 제목");
        mvc.perform(put("/api/v1/decisions/{id}/draft", id).with(signedIn()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(draft))).andExpect(status().isOk());
        var corrected = resolveRevision(id, "ABANDONED", null);
        org.assertj.core.api.Assertions.assertThat(corrected.path("editMode").asText()).isEqualTo("CORRECTION");
        org.assertj.core.api.Assertions.assertThat(corrected.path("previousResolutionId").asText()).isEqualTo(first.path("id").asText());
        var finalDecision = readDecision(id);
        org.assertj.core.api.Assertions.assertThat(finalDecision.path("phase").asText()).isEqualTo("COMPLETED");
        org.assertj.core.api.Assertions.assertThat(finalDecision.path("result").asText()).isEqualTo("NOT_PURCHASED");
        var previous = finalDecision.path("resolutions").get(1);
        org.assertj.core.api.Assertions.assertThat(previous.path("supersededAt").isNull()).isFalse();
        org.assertj.core.api.Assertions.assertThat(previous.path("comparisonSnapshot")).isEqualTo(first.path("comparisonSnapshot"));
        org.assertj.core.api.Assertions.assertThat(previous.path("comparisonSnapshot").path("title").asText()).isEqualTo(created.path("title").asText());
        org.assertj.core.api.Assertions.assertThat(corrected.path("comparisonSnapshot").path("title").asText()).isEqualTo("정정한 제목");
        org.assertj.core.api.Assertions.assertThat(finalDecision.path("outcomes").get(0).path("resolutionId").asText()).isEqualTo(first.path("id").asText());
        org.assertj.core.api.Assertions.assertThat(preferenceMemories.findByIdAndOwnerUserId(memory.id(), TEST_USER_ID)).isEmpty();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/preferences/{id}", memory.id())
                .with(signedIn()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"CONFIRM\"}"))
                .andExpect(status().isNotFound());
        org.assertj.core.api.Assertions.assertThat(reminderRepository.findByDecisionIdOrderByDueAtAsc(UUID.fromString(id)))
                .allMatch(r -> r.status() != com.decisionassistant.notification.OutcomeReminder.Status.PENDING);
        mvc.perform(post("/api/v1/decisions/{id}/resolutions", id).with(signedIn()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"DEFERRED\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/decisions/{id}/outcome-check-ins", id).with(signedIn()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"satisfaction":3,"expectationStatus":"MET","resolutionId":"%s","expectedVersion":%s}
                """.formatted(first.path("id").asText(), finalDecision.path("version")))).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/decisions/{id}/outcome-check-ins", id).with(signedIn()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"satisfaction":4,"expectationStatus":"MET","notes":"구매하지 않아도 괜찮았습니다.","resolutionId":"%s","expectedVersion":%s}
                """.formatted(corrected.path("id").asText(), finalDecision.path("version")))).andExpect(status().isCreated());
    }

    @Test
    void reconsiderationPreservesValidHistoryAndCanCompleteWithAnotherOption() throws Exception {
        var created = revisionFixture(); var id = created.path("id").asText();
        var first = resolveRevision(id, "ABANDONED", null);
        var completed = readDecision(id);
        mvc.perform(post("/api/v1/decisions/{id}/revisions", id).with(signedIn(createOtherUser())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"RECONSIDERATION\",\"expectedVersion\":0}"))
                .andExpect(status().isNotFound());
        startRevisionStatus(id, "RECONSIDERATION", 0, 409);
        var editing = startRevision(id, "RECONSIDERATION", completed.path("version").asLong());
        org.assertj.core.api.Assertions.assertThat(editing.path("status").asText()).isEqualTo("DRAFT");
        org.assertj.core.api.Assertions.assertThat(editing.path("resolutions").get(0).path("comparisonSnapshot")).isEqualTo(first.path("comparisonSnapshot"));
        var deferred = resolveRevision(id, "DEFERRED", null);
        startRevision(id, "RECONSIDERATION", readDecision(id).path("version").asLong());
        var second = resolveRevision(id, "CHOSEN", created.path("options").get(1).path("id").asText());
        org.assertj.core.api.Assertions.assertThat(second.path("editMode").asText()).isEqualTo("RECONSIDERATION");
        org.assertj.core.api.Assertions.assertThat(second.path("previousResolutionId").asText()).isEqualTo(deferred.path("id").asText());
        var result = readDecision(id);
        org.assertj.core.api.Assertions.assertThat(result.path("result").asText()).isEqualTo("OPTION_SELECTED");
        org.assertj.core.api.Assertions.assertThat(result.path("resolutions").get(2).path("supersededAt").isNull()).isTrue();
        org.assertj.core.api.Assertions.assertThat(reminderRepository.findByDecisionIdOrderByDueAtAsc(UUID.fromString(id)))
                .isEmpty();
    }

    @Test
    void legacySnapshotsAndUnlinkedOutcomesSurviveCorrectionWithoutRevivingOldReminders() throws Exception {
        var created = revisionFixture(); var id = created.path("id").asText();
        var first = resolveRevision(id, "CHOSEN", created.path("options").get(0).path("id").asText());
        mvc.perform(post("/api/v1/decisions/{id}/outcome-check-ins", id).with(signedIn()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"satisfaction\":4,\"expectationStatus\":\"MET\",\"workedWell\":\"착용감\"}"))
                .andExpect(status().isCreated());
        var uuid = UUID.fromString(id);
        jdbc.update("update decisions set current_resolution_id = null where id = ?", uuid);
        jdbc.update("update outcome_check_ins set resolution_id = null where decision_id = ?", uuid);
        reminderRepository.saveAndFlush(new com.decisionassistant.notification.OutcomeReminder(uuid, Instant.now(), com.decisionassistant.notification.OutcomeReminder.Channel.EMAIL, Instant.now()));
        jdbc.update("update outcome_reminders set resolution_id = null where decision_id = ?", uuid);
        jdbc.update("update resolutions set comparison_snapshot = jsonb_set(comparison_snapshot - 'title', '{schemaVersion}', '\"comparison-v1\"') where decision_id = ?", uuid);
        var before = readDecision(id);
        org.assertj.core.api.Assertions.assertThat(before.path("currentResolutionId").asText()).isEqualTo(first.path("id").asText());
        org.assertj.core.api.Assertions.assertThat(before.path("resolutions").get(0).path("comparisonSnapshot").path("title").isNull()).isTrue();
        startRevision(id, "CORRECTION", before.path("version").asLong());
        resolveRevision(id, "CHOSEN", created.path("options").get(1).path("id").asText());
        var after = readDecision(id);
        org.assertj.core.api.Assertions.assertThat(after.path("outcomes").get(0).path("resolutionId").isNull()).isTrue();
        org.assertj.core.api.Assertions.assertThat(after.path("resolutions").get(1).path("comparisonSnapshot").path("schemaVersion").asText()).isEqualTo("comparison-v1");
        org.assertj.core.api.Assertions.assertThat(preferenceMemories.findAllByOwnerUserIdOrderByUpdatedAtDesc(TEST_USER_ID))
                .noneMatch(m -> uuid.equals(m.sourceDecisionId()));
        var legacy = reminderRepository.findByDecisionIdOrderByDueAtAsc(uuid).stream().filter(r -> r.resolutionId() == null).findFirst().orElseThrow();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/decisions/{id}/reminders/{reminder}", id, legacy.id())
                .with(signedIn()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"RESCHEDULE\",\"dueAt\":\"2030-01-01T00:00:00Z\"}"))
                .andExpect(status().isConflict());
    }

    private tools.jackson.databind.JsonNode revisionFixture() throws Exception {
        return mapper.readTree(mvc.perform(post("/api/v1/decisions").with(signedIn()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"intentText":"결정 정정 검증", "templateKey":"EARBUDS", "webConsent":false,
                 "selectedSources":[{"url":"https://example.com/alpha", "name":"Alpha"},
                                    {"url":"https://example.com/beta", "name":"Beta"}]}
                """)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private tools.jackson.databind.JsonNode readDecision(String id) throws Exception {
        return mapper.readTree(mvc.perform(get("/api/v1/decisions/{id}", id).with(signedIn()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private tools.jackson.databind.JsonNode resolveRevision(String id, String kind, String option) throws Exception {
        var body = mapper.createObjectNode().put("kind", kind).put("rationale", "기록 " + kind)
                .put("expectedVersion", readDecision(id).path("version").asLong());
        if (option != null) body.put("selectedOptionId", option);
        return mapper.readTree(mvc.perform(post("/api/v1/decisions/{id}/resolutions", id).with(signedIn()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private tools.jackson.databind.JsonNode startRevision(String id, String mode, long version) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/v1/decisions/{id}/revisions", id).with(signedIn()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"%s\",\"expectedVersion\":%d,\"notPurchased\":true}".formatted(mode, version)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private void startRevisionStatus(String id, String mode, long version, int expected) throws Exception {
        mvc.perform(post("/api/v1/decisions/{id}/revisions", id).with(signedIn()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"%s\",\"expectedVersion\":%d,\"notPurchased\":true}".formatted(mode, version)))
                .andExpect(status().is(expected));
    }

    tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();

    @BeforeEach
    void createTestUser() {
        users.findById(TEST_USER_ID).orElseGet(() -> users.save(new ApplicationUser(
                TEST_USER_ID, TEST_USER.email(), TEST_USER.email().toLowerCase(), TEST_USER.displayName(), Instant.now())));
    }

    @Test
    void earbudsAndSelectedSourcesReuseExistingLifecycle() throws Exception {
        var json = mvc.perform(post("/api/v1/decisions").with(signedIn()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"intentText":"출퇴근용 무선 이어폰", "templateKey":"EARBUDS", "webConsent":false,
                         "selectedSources":[{"url":"https://example.com/buds", "name":"Buds Pro"}]}
                        """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.templateVersion").value("earbuds-v1"))
                .andExpect(jsonPath("$.criteria.length()").value(5)).andExpect(jsonPath("$.criteria[0].bestAnchor").value(50000))
                .andExpect(jsonPath("$.criteria[0].worstAnchor").value(500000))
                .andExpect(jsonPath("$.options[0].name").value("Buds Pro"))
                .andExpect(jsonPath("$.constraints.length()").value(2))
                .andExpect(jsonPath("$.guidance.evidence.length()").value(0))
                .andReturn().getResponse().getContentAsString();
        var id = mapper.readTree(json).path("id").asText();
        mvc.perform(post("/api/v1/decisions/{id}/sources", id).with(signedIn()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"url":"https://example.com/buds", "name":"Duplicate"}
                        """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.options.length()").value(1));
        mvc.perform(post("/api/v1/decisions/{id}/sources", id).with(signedIn()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"url":"https://example.com/other", "name":"Other Buds"}
                        """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.options.length()").value(2))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void comparisonConditionsConfirmationAndResolutionSnapshotSurviveReopening() throws Exception {
        var created = mapper.readTree(mvc.perform(post("/api/v1/decisions").with(signedIn()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"intentText":"비교 흐름 검증", "templateKey":"EARBUDS", "webConsent":false,
                 "selectedSources":[{"url":"https://example.com/alpha", "name":"Alpha"},
                                    {"url":"https://example.com/beta", "name":"Beta"}]}
                """)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        var id = created.path("id").asText();
        var optionId = created.path("options").get(0).path("id").asText();
        var settings = """
                {"maxBudget":100000,"ancRequired":true,"compatibility":"iOS","focus":"ANC 지원","deferredOptionIds":["%s"]}
                """.formatted(optionId);
        var changed = mapper.readTree(mvc.perform(put("/api/v1/decisions/{id}/comparison", id).with(signedIn()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":" + created.path("version") + ",\"settings\":" + settings + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.comparison.options[0].checks[0].status").value("UNKNOWN"))
                .andExpect(jsonPath("$.comparison.options[0].checks[1].status").value("UNKNOWN"))
                .andExpect(jsonPath("$.comparison.options[0].checks[2].status").value("UNKNOWN"))
                .andReturn().getResponse().getContentAsString());
        mvc.perform(put("/api/v1/decisions/{id}/comparison", id).with(signedIn()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":" + created.path("version") + ",\"settings\":" + settings + "}"))
                .andExpect(status().isConflict());
        var confirmed = mapper.readTree(mvc.perform(put("/api/v1/decisions/{id}/comparison/confirmation", id).with(signedIn()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"expectedVersion":%s,"optionId":"%s","feature":"anc","supported":true,
                 "note":"설명서에서 직접 확인", "sourceUrl":"https://example.com/manual"}
                """.formatted(changed.path("version"), optionId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.comparison.options[0].checks[0].status").value("MEETS"))
                .andExpect(jsonPath("$.comparison.options[0].checks[1].status").value("UNKNOWN"))
                .andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/v1/decisions/{id}/resolutions", id).with(signedIn()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"kind":"DEFERRED","rationale":"iOS 확인 대기","expectedVersion":%s}
                """.formatted(confirmed.path("version"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.comparisonSnapshot.comparison.options[0].checks[1].status").value("UNKNOWN"));
        startRevision(id, "RECONSIDERATION", readDecision(id).path("version").asLong());
        var reopened = mapper.readTree(mvc.perform(get("/api/v1/decisions/{id}", id).with(signedIn()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.comparison.confirmations[0].note").value("설명서에서 직접 확인"))
                .andReturn().getResponse().getContentAsString());
        mvc.perform(put("/api/v1/decisions/{id}/comparison/confirmation", id).with(signedIn()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"expectedVersion":%s,"optionId":"%s","feature":"anc","supported":false,"note":"다른 모델의 설명서였음"}
                """.formatted(reopened.path("version"), optionId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.comparison.options[0].checks[0].status").value("BELOW"))
                .andExpect(jsonPath("$.resolutions[0].comparisonSnapshot.comparison.options[0].checks[0].status").value("MEETS"));
    }

    @Test
    void lookupRequiresAuthenticationCsrfAndExplicitConsentEvenWhenDisabled() throws Exception {
        mvc.perform(post("/api/v1/product-lookups").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"Buds\",\"searchConsent\":true}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/product-lookups").with(signedIn()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"Buds\",\"searchConsent\":true}")).andExpect(status().isForbidden());
        for (var body : List.of("{\"query\":\"Buds\"}", "{\"query\":\"Buds\",\"searchConsent\":false}",
                "{\"query\":\" \",\"searchConsent\":true}")) {
            mvc.perform(post("/api/v1/product-lookups").with(signedIn()).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/product-lookups").with(signedIn()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"Buds\",\"searchConsent\":true}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("SEARCH_DISABLED"));
        mvc.perform(get("/api/v1/product-lookups/capabilities").with(signedIn()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.searchEnabled").value(false))
                .andExpect(jsonPath("$.searchProvider").value("Serper"));
    }

    @Test
    void reasonsPurchaseReconsiderationAndMemoryRespectHistoryAndOwnership() throws Exception {
        var created = revisionFixture(); var id = created.path("id").asText();
        var option = created.path("options").get(0).path("id").asText();
        mvc.perform(post("/api/v1/decisions/{id}/resolutions", id).with(signedIn()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"kind":"CHOSEN","selectedOptionId":"%s","reasonIds":["budget","features"],"primaryReasonId":"budget","rationale":"직접 확인"}
                """.formatted(option))).andExpect(status().isCreated()).andExpect(jsonPath("$.reasonIds.length()").value(2));
        var saved = readDecision(id);
        mvc.perform(post("/api/v1/decisions/{id}/revisions", id).with(signedIn()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"expectedVersion":%s,"mode":"RECONSIDERATION"}
                """.formatted(saved.path("version")))).andExpect(status().isConflict());
        startRevision(id, "RECONSIDERATION", saved.path("version").asLong());
        var active = readDecision(id);
        var edited = mapper.createObjectNode();
        for (var field : List.of("context", "options", "criteria", "values", "constraints", "constraintResults")) edited.set(field, active.path(field));
        edited.put("title", "취소할 편집 제목"); edited.put("expectedVersion", active.path("version").asLong());
        mvc.perform(put("/api/v1/decisions/{id}/draft", id).with(signedIn()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(edited))).andExpect(status().isOk());
        active = readDecision(id);
        mvc.perform(post("/api/v1/decisions/{id}/revisions/cancel", id).with(signedIn()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":" + active.path("version") + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CHOSEN"))
                .andExpect(jsonPath("$.title").value(created.path("title").asText()));
        var next = revisionFixture(); var nextId = next.path("id").asText();
        mvc.perform(get("/api/v1/decisions/{id}/memories", nextId).with(signedIn()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].reasonIds[0]").value("budget"));
        mvc.perform(get("/api/v1/decisions/{id}/memories", nextId).with(signedIn(createOtherUser())))
                .andExpect(status().isNotFound());
        jdbc.update("update decisions set personalization_enabled = false where id = ?", UUID.fromString(id));
        var excluded = mvc.perform(get("/api/v1/decisions/{id}/memories", nextId).with(signedIn()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(excluded).doesNotContain(id);
        jdbc.update("update decisions set personalization_enabled = true, deleted_at = now() where id = ?", UUID.fromString(id));
        var deleted = mvc.perform(get("/api/v1/decisions/{id}/memories", nextId).with(signedIn()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(deleted).doesNotContain(id);
        jdbc.update("update decisions set deleted_at = null where id = ?", UUID.fromString(id));
        var beforePurchase = readDecision(id);
        mvc.perform(put("/api/v1/decisions/{id}/purchase", id).with(signedIn()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"expectedVersion":%s,"resolutionId":"%s","status":"PURCHASED"}
                """.formatted(beforePurchase.path("version"), beforePurchase.path("currentResolutionId").asText())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resolutions[0].purchaseStatus").value("PURCHASED"));
        startRevisionStatus(id, "RECONSIDERATION", readDecision(id).path("version").asLong(), 409);
        mvc.perform(post("/api/v1/decisions/{id}/resolutions", nextId).with(signedIn()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"DEFERRED\",\"reasonIds\":[\"budget\"]}"))
                .andExpect(status().isUnprocessableEntity());
        resolveRevision(nextId, "DEFERRED", null);
        org.assertj.core.api.Assertions.assertThat(readDecision(nextId).path("phase").asText()).isEqualTo("COMPLETED");
    }

    @Test
    void bootsMigratesDatabaseAndCreatesLaptopDecision() throws Exception {
        var response = mvc.perform(post("/api/v1/decisions").with(signedIn()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"새 노트북", "templateKey":"LAPTOP"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.criteria.length()").value(7))
                .andExpect(jsonPath("$.constraints.length()").value(5))
                .andReturn().getResponse().getContentAsString();

        var id = mapper.readTree(response).get("id").asText();
        mvc.perform(get("/api/v1/decisions/{id}", id).with(signedIn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("새 노트북"));
        mvc.perform(get("/api/v1/ai/capabilities").with(signedIn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("gemini"))
                .andExpect(jsonPath("$.requiresSensitiveDataAcknowledgement").value(true));
        mvc.perform(post("/api/v1/decisions/{id}/structure-proposals", id).with(signedIn()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":0,"aiConsent":true,"sensitiveDataAcknowledged":false}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.violations[0]").value(org.hamcrest.Matchers.containsString("Gemini API")));
        var openApi = mvc.perform(get("/api/openapi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andReturn().getResponse().getContentAsString();
        var output = Path.of("build", "generated", "openapi.json");
        Files.createDirectories(output.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), mapper.readTree(openApi));
    }

    @Test
    void savesCompleteDraftAndCreatesReproducibleAlgorithmEvaluation() throws Exception {
        var created = mapper.readTree(mvc.perform(post("/api/v1/decisions").with(signedIn()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"개발용 노트북", "templateKey":"LAPTOP"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        var decisionId = created.get("id").asText();

        var firstOptionId = UUID.randomUUID().toString();
        var secondOptionId = UUID.randomUUID().toString();
        var options = mapper.createArrayNode();
        options.add(option(firstOptionId, "Option A"));
        options.add(option(secondOptionId, "Option B"));

        var criteria = (tools.jackson.databind.node.ArrayNode) created.get("criteria");
        for (var criterion : criteria) {
            if ("NUMERIC_UTILITY".equals(criterion.get("kind").asText())) {
                var node = (tools.jackson.databind.node.ObjectNode) criterion;
                if ("LOWER_BETTER".equals(criterion.get("direction").asText())) {
                    node.put("worstAnchor", 100).put("bestAnchor", 0);
                } else {
                    node.put("worstAnchor", 0).put("bestAnchor", 100);
                }
            }
        }
        var values = mapper.createArrayNode();
        for (var optionId : new String[]{firstOptionId, secondOptionId}) {
            for (var criterion : criteria) {
                var value = mapper.createObjectNode()
                        .put("optionId", optionId)
                        .put("criterionId", criterion.get("id").asText())
                        .put("certainty", optionId.equals(firstOptionId) ? "CONFIRMED" : "ESTIMATED");
                if ("NUMERIC_UTILITY".equals(criterion.get("kind").asText())) {
                    value.put("numericValue", optionId.equals(firstOptionId) ? 60 : 80);
                } else {
                    value.put("rating", optionId.equals(firstOptionId) ? 3 : 5);
                }
                values.add(value);
            }
        }
        var constraints = (tools.jackson.databind.node.ArrayNode) created.get("constraints");
        constraints.forEach(node -> ((tools.jackson.databind.node.ObjectNode) node).put("active", false));

        var draft = mapper.createObjectNode()
                .put("expectedVersion", 0)
                .put("title", "개발용 노트북");
        draft.set("context", created.get("context"));
        draft.set("options", options);
        draft.set("criteria", criteria);
        draft.set("values", values);
        draft.set("constraints", constraints);
        draft.set("constraintResults", mapper.createArrayNode());

        var updated = mapper.readTree(mvc.perform(put("/api/v1/decisions/{id}/draft", decisionId).with(signedIn()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(draft)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andReturn().getResponse().getContentAsString());

        var evaluation = mapper.readTree(mvc.perform(post("/api/v1/decisions/{id}/evaluations", decisionId)
                        .with(signedIn()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":%d,"includeLlm":false,"aiConsent":false}
                                """.formatted(updated.get("version").asLong())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decisionVersion").value(1))
                .andExpect(jsonPath("$.algorithmResult.recommendedOptionId").value(secondOptionId))
                .andExpect(jsonPath("$.algorithmResult.options[1].usesEstimatedValues").value(true))
                .andExpect(jsonPath("$.llmStatus").value("NOT_REQUESTED"))
                .andReturn().getResponse().getContentAsString());

        var other = createOtherUser();

        mvc.perform(get("/api/v1/decisions/{decisionId}/evaluations/{evaluationId}",
                        decisionId, evaluation.get("id").asText()).with(signedIn(other)))
                .andExpect(status().isNotFound());
    }

    @Test
    void createsGuidedDecisionAnswersCriticalQuestionsAndBuildsPreferenceMemory() throws Exception {
        var created = mapper.readTree(mvc.perform(post("/api/v1/decisions").with(signedIn()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "intentText":"카메라와 배터리가 중요한 새 스마트폰을 고르고 있어요.",
                                  "sourceUrls":["https://example.com/phone-a"],
                                  "templateKey":"PHONE",
                                  "webConsent":false,
                                  "aiConsent":false
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.templateKey").value("PHONE"))
                .andExpect(jsonPath("$.options.length()").value(1))
                .andExpect(jsonPath("$.guidance.processingStatus").value("NEEDS_INPUT"))
                .andExpect(jsonPath("$.guidance.readiness").value("PROVISIONAL"))
                .andExpect(jsonPath("$.guidance.questions.length()").value(3))
                .andReturn().getResponse().getContentAsString());
        var decisionId = created.get("id").asText();
        var optionId = created.get("options").get(0).get("id").asText();
        var budgetQuestion = created.get("guidance").get("questions").get(0).get("id").asText();

        mvc.perform(post("/api/v1/decisions/{id}/questions/{questionId}/answer", decisionId, budgetQuestion)
                        .with(signedIn()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\":\"1500000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context.budget").value(1500000));

        mvc.perform(post("/api/v1/decisions/{id}/sources", decisionId).with(signedIn()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.org/phone-b\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.options.length()").value(2));

        mvc.perform(post("/api/v1/decisions/{id}/resolutions", decisionId).with(signedIn()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"CHOSEN\",\"selectedOptionId\":\"%s\",\"rationale\":\"배터리\"}".formatted(optionId)))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/decisions/{id}/outcome-check-ins", decisionId).with(signedIn()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"satisfaction":4,"expectationStatus":"MET","wouldChooseAgain":true,
                                 "workedWell":"배터리 적합도","disappointedBy":"가격"}
                                """))
                .andExpect(status().isCreated());

        var memories = mapper.readTree(mvc.perform(get("/api/v1/preferences").with(signedIn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("PROPOSED"))
                .andReturn().getResponse().getContentAsString());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/preferences/{id}", memories.get(0).get("id").asText())
                        .with(signedIn()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"CONFIRM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void requiresAuthenticationAndCsrf() throws Exception {
        mvc.perform(get("/api/v1/decisions"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/decisions").with(signedIn())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"CSRF 없는 요청", "templateKey":"CUSTOM"}
                                """))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/me").with(signedIn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TEST_USER_ID.toString()))
                .andExpect(jsonPath("$.email").value(TEST_USER.email()));
    }

    @Test
    void isolatesEveryDecisionOperationByOwner() throws Exception {
        var created = mapper.readTree(mvc.perform(post("/api/v1/decisions").with(signedIn()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Owner only", "templateKey":"CUSTOM"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        var decisionId = created.get("id").asText();
        var other = createOtherUser();

        mvc.perform(put("/api/v1/decisions/{id}/comparison", decisionId).with(signedIn(other)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"expectedVersion":0,"settings":{"ancRequired":false,"focus":"가격","deferredOptionIds":[]}}
                """))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/decisions/{id}/comparison/confirmation", decisionId).with(signedIn(other)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"expectedVersion":0,"optionId":"00000000-0000-0000-0000-000000000001","feature":"anc","supported":true}
                """))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/decisions").with(signedIn(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/decisions/{id}", decisionId).with(signedIn(other)))
                .andExpect(status().isNotFound());

        var draft = mapper.createObjectNode().put("expectedVersion", 0).put("title", "Forbidden update");
        draft.set("context", created.get("context"));
        draft.set("options", created.get("options"));
        draft.set("criteria", created.get("criteria"));
        draft.set("values", created.get("values"));
        draft.set("constraints", created.get("constraints"));
        draft.set("constraintResults", created.get("constraintResults"));
        mvc.perform(put("/api/v1/decisions/{id}/draft", decisionId).with(signedIn(other)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(draft)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/decisions/{id}/evaluations", decisionId).with(signedIn(other)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":0,"includeLlm":false,"aiConsent":false}
                                """))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/decisions/{id}/structure-proposals", decisionId)
                        .with(signedIn(other)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion":0,"aiConsent":true,"sensitiveDataAcknowledged":true}
                                """))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/decisions/{id}/resolutions", decisionId).with(signedIn(other)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"DEFERRED"}
                                """))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/decisions/{id}/outcome-check-ins", decisionId)
                        .with(signedIn(other)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"satisfaction":3,"expectationStatus":"MET"}
                                """))
                .andExpect(status().isNotFound());
    }

    private tools.jackson.databind.node.ObjectNode option(String id, String name) {
        return mapper.createObjectNode().put("id", id).put("name", name);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor signedIn() {
        return signedIn(TEST_USER);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor signedIn(AppUserPrincipal principal) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private AppUserPrincipal createOtherUser() {
        var id = UUID.randomUUID();
        var principal = new AppUserPrincipal(id, "other-" + id + "@example.com", "Other");
        users.save(new ApplicationUser(
                id, principal.email(), principal.email().toLowerCase(), principal.displayName(), Instant.now()));
        return principal;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestSecurityConfiguration {
        @Bean
        SecurityFilterChain testSecurity(HttpSecurity http) throws Exception {
            SecurityConfiguration.configureCsrf(http);
            http.authorizeHttpRequests(authorize -> authorize
                            .requestMatchers("/api/openapi", "/actuator/health", "/actuator/info").permitAll()
                            .anyRequest().authenticated())
                    .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                            new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                            request -> request.getRequestURI().startsWith("/api/")));
            return http.build();
        }
    }
}
