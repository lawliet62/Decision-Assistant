package com.decisionassistant.guidance;

import com.decisionassistant.lookup.ProductLookupService;
import com.decisionassistant.template.IntakeTemplates.IntakeTemplate;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;
import static com.decisionassistant.guidance.DiscoveryContracts.*;
import static com.decisionassistant.guidance.DiscoveryFailure.*;

@Component
public class DiscoverySupplementer {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DiscoverySupplementer.class);
    private final ProductLookupService search;
    private final WebEvidenceProvider web;
    private final PublicUrlPolicy urls;
    public DiscoverySupplementer(ProductLookupService search, WebEvidenceProvider web, PublicUrlPolicy urls) {
        this.search = search; this.web = web; this.urls = urls;
    }
    record Result(Map<String, List<ProductFact>> factsByPurchaseUrl, List<DiscoveryFailure> failures) {}
    Result collect(UUID id, UUID owner, DiscoveryRequest conditions, IntakeTemplate template, List<SourcePage> pages) {
        var found = new LinkedHashMap<String, List<ProductFact>>();
        var failures = new ArrayList<DiscoveryFailure>();
        if (!Boolean.TRUE.equals(conditions.officialEvidenceConsent())) return new Result(Map.of(), List.of());
        var seen = new HashSet<String>();
        var requestedDocuments = new HashSet<String>();
        int attempts = 0;
        for (var page : pages.stream().sorted(Comparator.comparing(p -> p.officialProduct().offer().price())).toList()) {
            var product = page.officialProduct();
            if (product.model() == null) continue;
            var missing = missingFields(conditions, template, page);
            if (missing.isEmpty()) continue;
            if (!seen.add(product.model().brand() + ":" + product.model().code())) continue;
            if (attempts == 2) break;
            attempts++;
            var stage = Stage.SEARCH;
            try {
                var results = search.lookupForOwner(OfficialSpecificationPolicy.query(product.model()), owner);
                var result = results.stream().filter(r -> OfficialSpecificationPolicy.allows(r.url(), product.model()))
                        .filter(r -> !requestedDocuments.contains(r.url()))
                        .sorted(Comparator.comparingInt(r -> r.url().contains("/products/") ? 0 : 1)).findFirst().orElse(null);
                if (result == null) { outcome(id, "NO_OFFICIAL_DOCUMENT"); continue; }
                var url = urls.validate(result.url());
                requestedDocuments.add(url);
                stage = Stage.COLLECTION;
                log.info("product_event=discovery_provider_call decision_id={} provider=firecrawl", id);
                var collected = web.scrapeProduct(url);
                var inspected = OfficialSpecificationParser.inspect(url, collected, product, template.facts(), Instant.now());
                outcome(id, inspected.outcome());
                if (!inspected.facts().isEmpty()) found.put(page.url(), inspected.facts());
            } catch (RuntimeException ex) {
                var failure = classify(stage, ex);
                failures.add(failure);
                var httpStatus = ex instanceof com.decisionassistant.lookup.LookupException lookup ? lookup.status() : httpStatus(ex);
                log.warn("product_event=discovery_supplement_failed decision_id={} stage={} code={} root_cause={} http_status={}",
                        id, stage, failure.code(), rootCauseType(ex), httpStatus);
                // 계정·burst 한도에 걸리면 다음 후보의 추가 요청도 중단한다.
                if (Objects.equals(httpStatus, 429)) break;
            }
        }
        return new Result(Map.copyOf(found), List.copyOf(failures));
    }
    private static List<String> missingFields(DiscoveryRequest conditions, IntakeTemplate template, SourcePage page) {
        var facts = page.evidenceBlocks().stream().map(b -> SpecNormalizer.fact(b, page.url(), page.fetchedAt())).toList();
        var missing = new ArrayList<String>();
        for (var check : MinimumRequirementMatcher.check(template.minimumRequirements(), conditions.selectedRequirements(), facts)) {
            if (check.status() == RequirementStatus.UNKNOWN) template.minimumRequirements().stream().filter(r -> r.id().equals(check.id()))
                    .findFirst().ifPresent(r -> missing.add(r.factName()));
        }
        var compatibility = CompatibilityMatcher.check(template.key(), conditions.compatibility(), facts, page.officialProduct().offer());
        if (compatibility != null && compatibility.status() == RequirementStatus.UNKNOWN)
            missing.add(switch (template.key()) { case EARBUDS -> "호환성"; case MONITOR -> "연결 단자"; default -> "OS"; });
        return List.copyOf(missing);
    }
    private static void outcome(UUID id, String outcome) {
        log.info("product_event=discovery_supplement_result decision_id={} outcome={}", id, outcome);
    }
}
