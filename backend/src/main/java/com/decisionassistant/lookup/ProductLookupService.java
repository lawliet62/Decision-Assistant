package com.decisionassistant.lookup;

import com.decisionassistant.common.InvalidDecisionException;
import com.decisionassistant.guidance.PublicUrlPolicy;
import com.decisionassistant.guidance.WebSearchProvider;
import com.decisionassistant.identity.CurrentUserProvider;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
public class ProductLookupService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProductLookupService.class);
    private final WebSearchProvider provider;
    private final SearchProperties properties;
    private final SearchQuota quota;
    private final CurrentUserProvider currentUser;
    private final PublicUrlPolicy urls;
    private final MeterRegistry metrics;
    private final Semaphore concurrent = new Semaphore(2);
    private final Map<UUID, List<Instant>> recent = new HashMap<>();

    public ProductLookupService(@Qualifier("serperSearchProvider") WebSearchProvider provider,
                                SearchProperties properties, SearchQuota quota,
                                CurrentUserProvider currentUser, PublicUrlPolicy urls, MeterRegistry metrics) {
        this.provider = provider;
        this.properties = properties;
        this.quota = quota;
        this.currentUser = currentUser;
        this.urls = urls;
        this.metrics = metrics;
    }

    public List<WebSearchProvider.SearchResult> lookup(String query) {
        var start = System.nanoTime();
        var acquired = false;
        var outcome = "success";
        try {
            var user = currentUser.current();
            if (!properties.configured()) throw new LookupException(503, "SEARCH_DISABLED", "제품 검색이 꺼져 있습니다. URL 직접 추가를 이용하세요.");
            acquired = concurrent.tryAcquire();
            if (!acquired) throw new LookupException(429, "SEARCH_BUSY", "다른 검색을 처리 중입니다. 잠시 후 다시 시도하세요.");
            admit(user.id(), Instant.now());
            quota.reserve();
            var results = new LinkedHashMap<String, WebSearchProvider.SearchResult>();
            for (var item : provider.search(query, 5)) {
                try {
                    var url = urls.validate(item.url());
                    results.putIfAbsent(url, new WebSearchProvider.SearchResult(item.title(), url, item.description()));
                } catch (InvalidDecisionException ignored) { /* Never offer private or non-HTTP URLs. */ }
            }
            return results.values().stream().limit(5).toList();
        } catch (LookupException exception) {
            outcome = exception.code();
            throw exception;
        } catch (RuntimeException exception) {
            outcome = "internal_error";
            throw exception;
        } finally {
            if (acquired) concurrent.release();
            metrics.counter("product.lookup.requests", "outcome", outcome).increment();
            metrics.timer("product.lookup.duration", "outcome", outcome).record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            log.info("product_event=product_lookup_finished outcome={} duration_ms={}", outcome,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        }
    }

    // Single-instance burst protection only; the monetary cap is durable and cross-instance in PostgreSQL.
    synchronized void admit(UUID user, Instant now) {
        recent.values().forEach(times -> times.removeIf(time -> !time.isAfter(now.minusSeconds(60))));
        recent.values().removeIf(List::isEmpty);
        var times = recent.computeIfAbsent(user, ignored -> new ArrayList<>());
        if (times.size() >= 5) throw new LookupException(429, "SEARCH_RATE_LIMIT", "검색은 1분에 5회까지 가능합니다. 잠시 후 다시 시도하세요.");
        times.add(now);
    }
}
