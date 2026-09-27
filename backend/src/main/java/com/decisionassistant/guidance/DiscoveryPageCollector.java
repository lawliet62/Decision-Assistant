package com.decisionassistant.guidance;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static com.decisionassistant.guidance.DiscoveryContracts.*;
import static com.decisionassistant.guidance.DiscoveryFailure.*;

@Component
public class DiscoveryPageCollector {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DiscoveryPageCollector.class);
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final WebEvidenceProvider web;
    private final PublicUrlPolicy urls;
    public DiscoveryPageCollector(WebEvidenceProvider web, PublicUrlPolicy urls) { this.web = web; this.urls = urls; }
    record Result(List<SourcePage> pages, List<DiscoveryFailure> failures) {}
    private record Collected(SourcePage page, DiscoveryFailure failure) {}

    Result collect(UUID decisionId, List<WebSearchProvider.SearchResult> results, List<String> facts) {
        var tasks = results.stream().limit(5).map(result -> (Callable<Collected>) () -> collectOne(decisionId, result, facts)).toList();
        try {
            var pages = new ArrayList<SourcePage>();
            var failures = new ArrayList<DiscoveryFailure>();
            // invokeAll의 반환 순서는 완료 순서가 아닌 입력 순서다.
            for (var future : pool.invokeAll(tasks)) {
                var result = future.get();
                if (result.page() != null) pages.add(result.page()); else failures.add(result.failure());
            }
            return new Result(List.copyOf(pages), List.copyOf(failures));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("수집 작업이 중단되었습니다.", ex);
        } catch (ExecutionException ex) { throw new IllegalStateException("수집 작업에 실패했습니다.", ex.getCause()); }
    }

    private Collected collectOne(UUID id, WebSearchProvider.SearchResult result, List<String> facts) {
        var start = System.nanoTime();
        try {
            var url = urls.validate(result.url());
            var page = web.scrape(url);
            if (page == null || page.markdown() == null || page.markdown().isBlank())
                return failed(id, new DiscoveryFailure(Stage.COLLECTION, Code.COLLECTION_EMPTY), null);
            return new Collected(new SourcePage(url, page.title(), DiscoveryEvidence.excerpt(page.markdown(), facts), Instant.now()), null);
        } catch (RuntimeException ex) { return failed(id, classify(Stage.COLLECTION, ex), ex); }
        finally { log.info("product_event=discovery_page_timing decision_id={} duration_ms={}", id, (System.nanoTime() - start) / 1_000_000); }
    }
    private Collected failed(UUID id, DiscoveryFailure failure, Throwable cause) {
        log.warn("product_event=discovery_page_failed decision_id={} stage={} code={} root_cause={} http_status={}",
                id, failure.stage(), failure.code(), rootCauseType(cause), httpStatus(cause));
        return new Collected(null, failure);
    }
    @PreDestroy void close() { pool.shutdownNow(); }
}
