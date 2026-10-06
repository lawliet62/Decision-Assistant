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
        return collect(decisionId, results, facts, List.of());
    }
    Result collect(UUID decisionId, List<WebSearchProvider.SearchResult> results, List<String> facts, List<String> priority) {
        return collect(decisionId, results, facts, priority, OfficialStorePolicy.VERSION);
    }
    Result collect(UUID decisionId, List<WebSearchProvider.SearchResult> results, List<String> facts, List<String> priority, String policyVersion) {
        var tasks = results.stream().limit(5).map(result -> (Callable<Collected>) () -> collectOne(decisionId, result, facts, priority, policyVersion)).toList();
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

    private Collected collectOne(UUID id, WebSearchProvider.SearchResult result, List<String> facts, List<String> priority, String policyVersion) {
        var start = System.nanoTime();
        try {
            var url = urls.validate(result.url());
            log.info("product_event=discovery_provider_call decision_id={} provider=firecrawl", id);
            var page = web.scrapeProduct(url);
            if (page == null || page.markdown() == null || page.markdown().isBlank())
                return failed(id, new DiscoveryFailure(Stage.COLLECTION, Code.COLLECTION_EMPTY), null);
            var fetchedAt = Instant.now();
            var inspection = OfficialProductParser.inspect(url, page, fetchedAt);
            log.info("product_event=discovery_offer_inspection decision_id={} outcome={}", id,
                    inspection.rejection() == null ? "ACCEPTED" : inspection.rejection().name());
            var product = inspection.product();
            var excerpt = DiscoveryEvidence.excerpt(page.markdown(), facts);
            var blocks = new ArrayList<EvidenceBlock>();
            int remaining = 6000;
            // 구조화 데이터의 제품명도 수집 원문이다. 생성한 사양은 추가하지 않는다.
            if (product != null) {
                var heading = "# " + product.name() + "\n";
                remaining -= heading.length() + 32 + Objects.toString(product.priceQuote(), "").length();
                for (var block : inspection.blocks().stream().filter(b -> facts.contains(b.factName()))
                        .sorted(Comparator.comparingInt(b -> priority.contains(b.factName()) ? 0 : 1)).toList()) {
                    int size = block.id().length() + block.productIdentity().length() + block.factName().length()
                            + block.label().length() + block.value().length() + block.quote().length();
                    if (size <= remaining) { blocks.add(block); remaining -= size; }
                    else log.info("product_event=discovery_field_evidence decision_id={} field={} outcome=BUDGET_OMITTED", id, fieldKey(block.factName()));
                }
                var description = product.description().substring(0, Math.min(product.description().length(), Math.min(500, remaining)));
                var metadata = heading + description + "\n\n<!-- evidence boundary -->\n\n";
                excerpt = metadata + excerpt.substring(0, Math.min(excerpt.length(), remaining - description.length()));
                var offer = product.offer();
                product = new OfficialProduct(product.name(), product.identity(), new OfficialOffer(offer.purchaseUrl(), offer.seller(), offer.price(),
                        offer.currency(), offer.imageUrl(), offer.fetchedAt(), offer.priceBasis(), policyVersion), "", product.priceQuote(), product.model());
            }
            for (var field : priority) log.info("product_event=discovery_field_evidence decision_id={} field={} outcome={}", id, fieldKey(field),
                    blocks.stream().anyMatch(b -> b.factName().equals(field)) ? "BLOCK_AVAILABLE" : "BLOCK_MISSING");
            return new Collected(new SourcePage(url, page.title(), excerpt, fetchedAt, product, List.copyOf(blocks)), null);
        } catch (RuntimeException ex) { return failed(id, classify(Stage.COLLECTION, ex), ex); }
        finally { log.info("product_event=discovery_page_timing decision_id={} duration_ms={}", id, (System.nanoTime() - start) / 1_000_000); }
    }
    private Collected failed(UUID id, DiscoveryFailure failure, Throwable cause) {
        log.warn("product_event=discovery_page_failed decision_id={} stage={} code={} root_cause={} http_status={}",
                id, failure.stage(), failure.code(), rootCauseType(cause), httpStatus(cause));
        return new Collected(null, failure);
    }
    @PreDestroy void close() { pool.shutdownNow(); }
    static String fieldKey(String field) {
        return switch (field) { case "메모리" -> "memory"; case "저장공간" -> "storage"; case "ANC 지원" -> "anc";
            case "펜 지원" -> "pen"; case "주사율" -> "refresh_rate"; case "해상도" -> "resolution";
            case "호환성", "OS", "연결 단자" -> "compatibility"; case "가격" -> "price";
            case "전용 앱" -> "app"; case "기기별 추가 기능" -> "extra_features"; case "CPU/GPU" -> "cpu_gpu";
            case "배터리" -> "battery"; case "무게" -> "weight"; case "방수" -> "water_resistance";
            case "화면", "크기" -> "display"; case "형태" -> "form"; case "카메라" -> "camera"; case "패널" -> "panel";
            default -> "other"; };
    }
}
