package com.decisionassistant.decision;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Component
public class DecisionPurgeWorker {
    private final JdbcTemplate jdbc;
    private final DecisionRetentionService service;
    public DecisionPurgeWorker(JdbcTemplate jdbc, DecisionRetentionService service) { this.jdbc = jdbc; this.service = service; }
    @Scheduled(fixedDelayString = "${decision-assistant.retention.poll-interval:60000}")
    public void purge() {
        var now = Instant.now();
        var ids = jdbc.queryForList("select id from decisions where deleted_at <= ? order by deleted_at, id limit 100",
                UUID.class, java.sql.Timestamp.from(now.minus(DecisionRetentionService.RETENTION_DAYS, ChronoUnit.DAYS)));
        for (var id : ids) service.purgeExpired(id, now);
    }
}
