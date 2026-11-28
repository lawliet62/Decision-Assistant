package com.decisionassistant.decision;

import com.decisionassistant.job.JobMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Cloud Run Job 일회 실행 경로(ADR 0025 결정 4). 만료 결정이 남지 않을 때까지 기존 batch를 반복한다.
 * 선택 결과가 없거나 한 batch에서 하나도 삭제하지 못하면 멈춰 무한 반복하지 않는다.
 * 만료 결정이 남거나 실행이 실패하면 종료 코드 1을 남긴다.
 */
@Component
@ConditionalOnProperty(name = JobMode.PROPERTY, havingValue = JobMode.PURGE_EXPIRED_DECISIONS)
class DecisionPurgeJob implements ApplicationRunner, ExitCodeGenerator {
    private static final Logger log = LoggerFactory.getLogger(DecisionPurgeJob.class);
    private final DecisionPurgeWorker worker;
    private volatile int exitCode = 1;

    DecisionPurgeJob(DecisionPurgeWorker worker) { this.worker = worker; }

    @Override public void run(ApplicationArguments args) {
        int purged = 0;
        try {
            Instant now;
            int batch;
            do {
                now = Instant.now();
                batch = worker.purgeBatch(now);
                purged += batch;
            } while (batch > 0);
            long remaining = worker.countExpired(now);
            exitCode = remaining == 0 ? 0 : 1;
            log.info("product_event=decision_purge_run outcome={} purged={} remaining={}",
                    remaining == 0 ? "success" : "incomplete", purged, remaining);
        } catch (RuntimeException failure) {
            exitCode = 1;
            // DB 오류 메시지에 행 값이 포함될 수 있으므로 예외 종류만 남긴다.
            log.error("product_event=decision_purge_run outcome=failed purged={} remaining=unknown error={}",
                    purged, failure.getClass().getName());
        }
    }

    @Override public int getExitCode() { return exitCode; }
}
