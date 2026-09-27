package com.decisionassistant.guidance;

import java.util.UUID;

final class DiscoveryTrace implements AutoCloseable {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DiscoveryTrace.class);
    private final UUID id;
    private final long start = System.nanoTime();
    private long phaseStart = start;
    private DiscoveryFailure.Stage phase = DiscoveryFailure.Stage.CONFIGURATION;
    DiscoveryTrace(UUID id) { this.id = id; }
    void enter(DiscoveryFailure.Stage next) { record(); phase = next; phaseStart = System.nanoTime(); }
    private void record() { log.info("product_event=discovery_stage_timing decision_id={} stage={} duration_ms={}", id, phase, (System.nanoTime() - phaseStart) / 1_000_000); }
    @Override public void close() {
        record(); log.info("product_event=discovery_total_timing decision_id={} duration_ms={}", id, (System.nanoTime() - start) / 1_000_000);
    }
}
