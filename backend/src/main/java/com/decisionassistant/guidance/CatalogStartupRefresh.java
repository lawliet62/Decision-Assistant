package com.decisionassistant.guidance;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import java.time.Instant;

/** 초기 manifest 등록이 끝난 뒤 기한이 된 제품을 예약 스레드에서 확인한다. job mode에서는 실행하지 않는다. */
@Component @org.springframework.context.annotation.Conditional(com.decisionassistant.job.JobMode.Inactive.class)
public class CatalogStartupRefresh {
    private final TaskScheduler scheduler;
    private final CatalogRefreshWorker worker;

    CatalogStartupRefresh(@Qualifier("taskScheduler") TaskScheduler scheduler, CatalogRefreshWorker worker) {
        this.scheduler = scheduler;
        this.worker = worker;
    }

    @EventListener(ApplicationReadyEvent.class)
    void afterStartup() { scheduler.schedule(worker::refreshDue, Instant.now()); }
}
