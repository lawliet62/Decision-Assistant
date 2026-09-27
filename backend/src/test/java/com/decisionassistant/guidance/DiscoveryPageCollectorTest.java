package com.decisionassistant.guidance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class DiscoveryPageCollectorTest {
    @Test @Timeout(10)
    void measuresIdenticalDelayedPagesSequentiallyAndInParallel() {
        var provider = new WebEvidenceProvider() {
            public String name() { return "delayed-fixture"; }
            public Page scrape(String url) {
                try { Thread.sleep(120); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
                return new Page("Product", "Product ANC 지원");
            }
        };
        var results = java.util.stream.IntStream.range(0, 5).mapToObj(i -> new WebSearchProvider.SearchResult("product", "https://example.com/" + i, "")).toList();
        var start = System.nanoTime();
        results.forEach(item -> provider.scrape(item.url()));
        var sequential = (System.nanoTime() - start) / 1_000_000;
        var collector = new DiscoveryPageCollector(provider, new PublicUrlPolicy());
        try {
            start = System.nanoTime();
            assertThat(collector.collect(UUID.randomUUID(), results, List.of("ANC 지원")).pages()).hasSize(5);
            System.out.println("동일 지연 fixture 5개: 순차=" + sequential + "ms, 병렬=" + (System.nanoTime() - start) / 1_000_000 + "ms");
            // 시간은 관측값이며 CI 부하에 민감한 비율 assertion 대신 위 테스트에서 동시성 상한과 겹침을 검증한다.
        } finally { collector.close(); }
    }
    @Test @Timeout(10)
    void overlapsTwoPagesPreservesOrderAndNeverCollectsMoreThanFive() throws Exception {
        var firstPair = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var active = new AtomicInteger(); var maximum = new AtomicInteger(); var calls = new AtomicInteger();
        var provider = new WebEvidenceProvider() {
            public String name() { return "fixture"; }
            public Page scrape(String url) {
                calls.incrementAndGet(); maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
                firstPair.countDown();
                try {
                    if (!release.await(3, TimeUnit.SECONDS)) throw new AssertionError("수집이 병렬로 시작되지 않았습니다.");
                    if (url.endsWith("/2")) throw new IllegalStateException("fixture-private-body");
                    return new Page(url, "## Product " + url.charAt(url.length() - 1) + "\nANC 지원");
                } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
                finally { active.decrementAndGet(); }
            }
        };
        var collector = new DiscoveryPageCollector(provider, new PublicUrlPolicy());
        try (var caller = Executors.newSingleThreadExecutor()) {
            var results = java.util.stream.IntStream.range(0, 7).mapToObj(i -> new WebSearchProvider.SearchResult("product", "https://example.com/" + i, "")).toList();
            var future = caller.submit(() -> collector.collect(UUID.randomUUID(), results, List.of("ANC 지원")));
            try { assertThat(firstPair.await(3, TimeUnit.SECONDS)).isTrue(); } finally { release.countDown(); }
            var result = future.get(3, TimeUnit.SECONDS);
            assertThat(maximum).hasValue(2); assertThat(calls).hasValue(5);
            assertThat(result.pages()).extracting(DiscoveryContracts.SourcePage::url)
                    .containsExactly("https://example.com/0", "https://example.com/1", "https://example.com/3", "https://example.com/4");
            assertThat(result.failures()).extracting(DiscoveryFailure::code).containsExactly(DiscoveryFailure.Code.COLLECTION_FAILED);
        } finally { release.countDown(); collector.close(); }
    }

    @Test void findsLaterSpecificationsWithinBudgetWithoutCrossingHeadings() {
        var markdown = "# Shop\n" + "navigation\n".repeat(700) + "\n## Buds A\n적응형 ANC 탑재\n\n## Buds B\nANC 미지원\n";
        var excerpt = DiscoveryEvidence.excerpt(markdown, List.of("ANC 지원"));
        assertThat(excerpt.length()).isLessThanOrEqualTo(6000);
        assertThat(excerpt).contains("## Buds A", "적응형 ANC 탑재", "## Buds B", "ANC 미지원");
        assertThat(DiscoveryEvidence.belongsToProduct(excerpt, "Buds A", "적응형 ANC 탑재", List.of("Buds A", "Buds B"))).isTrue();
        assertThat(DiscoveryEvidence.belongsToProduct(excerpt, "Buds A", "ANC 미지원", List.of("Buds A", "Buds B"))).isFalse();
    }
}
