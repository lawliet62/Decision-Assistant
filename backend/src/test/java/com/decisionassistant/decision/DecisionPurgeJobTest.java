package com.decisionassistant.decision;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DecisionPurgeJobTest {
    private final DecisionPurgeWorker worker = mock(DecisionPurgeWorker.class);
    private final DecisionPurgeJob job = new DecisionPurgeJob(worker);

    @Test void repeatsBatchesUntilNothingIsLeftAndSucceeds() {
        when(worker.purgeBatch(any(Instant.class))).thenReturn(100, 100, 5, 0);
        when(worker.countExpired(any(Instant.class))).thenReturn(0L);
        job.run(null);
        verify(worker, times(4)).purgeBatch(any(Instant.class));
        assertThat(job.getExitCode()).isZero();
    }

    @Test void stopsWithoutProgressAndReportsRemainingAsFailure() {
        when(worker.purgeBatch(any(Instant.class))).thenReturn(0);
        when(worker.countExpired(any(Instant.class))).thenReturn(3L);
        job.run(null);
        verify(worker, times(1)).purgeBatch(any(Instant.class));
        assertThat(job.getExitCode()).isEqualTo(1);
    }

    @Test void failureYieldsNonZeroExitCode() {
        when(worker.purgeBatch(any(Instant.class))).thenReturn(100).thenThrow(new DataAccessResourceFailureException("db down"));
        job.run(null);
        verify(worker, never()).countExpired(any(Instant.class));
        assertThat(job.getExitCode()).isEqualTo(1);
    }

    @Test void doesNotReportSuccessBeforeRunning() {
        assertThat(job.getExitCode()).isEqualTo(1);
    }
}
