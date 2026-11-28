package com.decisionassistant.decision;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Component
public class DecisionPurgeWorker {
    static final int BATCH_SIZE = 100;
    private final JdbcTemplate jdbc;
    private final DecisionRetentionService service;
    public DecisionPurgeWorker(JdbcTemplate jdbc, DecisionRetentionService service) { this.jdbc = jdbc; this.service = service; }
    @Scheduled(fixedDelayString = "${decision-assistant.retention.poll-interval:60000}")
    public void purge() { purgeBatch(Instant.now()); }

    /** 만료 결정을 최대 {@value #BATCH_SIZE}개 결정별 transaction으로 삭제하고 실제 삭제 수를 돌려준다. */
    int purgeBatch(Instant now) {
        var ids = jdbc.queryForList("select id from decisions where deleted_at <= ? order by deleted_at, id limit " + BATCH_SIZE,
                UUID.class, cutoff(now));
        int purged = 0;
        for (var id : ids) if (service.purgeExpired(id, now)) purged++;
        return purged;
    }

    long countExpired(Instant now) {
        return jdbc.queryForObject("select count(*) from decisions where deleted_at <= ?", Long.class, cutoff(now));
    }

    private static Timestamp cutoff(Instant now) {
        return Timestamp.from(now.minus(DecisionRetentionService.RETENTION_DAYS, ChronoUnit.DAYS));
    }
}
