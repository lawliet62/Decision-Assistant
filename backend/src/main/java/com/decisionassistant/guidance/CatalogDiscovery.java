package com.decisionassistant.guidance;

import com.decisionassistant.decision.Decision;
import com.decisionassistant.template.IntakeTemplates;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
import static com.decisionassistant.guidance.DiscoveryContracts.*;

@Service
public class CatalogDiscovery {
    private final CatalogProductRepository products;
    private final CandidateSuggestionRepository candidates;
    private final IntakeTemplates templates;
    private final MeterRegistry metrics;
    public CatalogDiscovery(CatalogProductRepository products, CandidateSuggestionRepository candidates,
                            IntakeTemplates templates, MeterRegistry metrics) {
        this.products = products; this.candidates = candidates; this.templates = templates; this.metrics = metrics;
    }
    public static boolean isCatalog(DiscoveryRequest request) { return CatalogProperties.TEMPLATE.equals(request.templateVersion()); }
    public static boolean fresh(OfficialOffer offer, Instant now) {
        return offer != null && offer.fetchedAt() != null && !offer.fetchedAt().isAfter(now)
                && now.isBefore(offer.fetchedAt().plus(Duration.ofHours(24)));
    }
    public static String priceStatus(OfficialOffer offer) { return offer == null ? "UNVERIFIED" : fresh(offer, Instant.now()) ? "VERIFIED" : "EXPIRED"; }
    public void populate(Decision decision, ProductDiscovery run) {
        var start = System.nanoTime();
        var now = Instant.now();
        var previous = candidates.findByDecisionIdOrderByCreatedAtAsc(decision.id());
        var excluded = DiscoveryExclusions.from(previous, decision.options().stream().map(o -> o.name()).toList());
        var dbStart = System.nanoTime();
        var catalog = products.findByActiveTrueOrderByIdAsc();
        metrics.timer("catalog.db.read").record(System.nanoTime() - dbStart, java.util.concurrent.TimeUnit.NANOSECONDS);
        var template = templates.required(decision.templateKey(), run.conditions().templateVersion());
        var selected = new ArrayList<CandidateSuggestion>();
        for (var product : catalog) {
            if (excluded.identities().contains("catalog:" + product.id()) || excluded.containsUrl(product.purchaseUrl())
                    || excluded.names().contains(DiscoveryExclusions.normalizedName(product.name()))) continue;
            var offer = product.usableOffer();
            var facts = new ArrayList<>(product.usableFacts(now));
            if (offer != null && product.priceQuote() != null && !product.priceQuote().isBlank())
                facts.addFirst(new ProductFact("가격", offer.price().toPlainString() + " KRW", offer.purchaseUrl(),
                    product.priceQuote(), offer.fetchedAt(), "UNREVIEWED"));
            var candidate = new CandidateSuggestion(decision.id(), product.name(), product.purchaseUrl(), "", CatalogProperties.VERSION, now);
            candidate.catalogSnapshot("catalog:" + product.id(), offer);
            candidate.discovered(facts);
            candidate.sourceEvidence("운영자가 모델과 공식 사양 출처를 검토한 제품 목록", facts.stream().map(ProductFact::fetchedAt).min(Instant::compareTo).orElse(null));
            candidate.generation(run.generation());
            candidate.reason(reason(decision, run.conditions(), candidate, template));
            selected.add(candidate);
        }
        // 조건을 충족한 후보를 먼저 선택하므로 저가의 미확인 후보가 5개 슬롯을 차지하지 않는다.
        selected.sort(Comparator.comparing((CandidateSuggestion c) -> group(decision, run.conditions(), c, template))
                .thenComparing(DiscoveryRanking.byPrice()));
        candidates.saveAll(selected.stream().limit(5).toList());
        run.catalog();
        if (selected.isEmpty()) run.empty(catalog.isEmpty() ? "CATALOG_EMPTY" : "NO_NEW_PRODUCTS");
        else { run.displayCurrentGeneration(); run.finish(ProductDiscovery.Status.SUCCEEDED, null); }
        metrics.timer("catalog.discovery").record(System.nanoTime() - start, java.util.concurrent.TimeUnit.NANOSECONDS);
    }
    private static DiscoveryRanking.Group group(Decision decision, DiscoveryRequest request, CandidateSuggestion c, IntakeTemplates.IntakeTemplate template) {
        var checks = MinimumRequirementMatcher.check(template.minimumRequirements(), request.selectedRequirements(), c.facts());
        var compatibility = CompatibilityMatcher.check(decision.templateKey(), request.compatibility(), c.facts(), c.officialOffer(), true);
        return DiscoveryRanking.catalogGroup(c.officialOffer(), checks, request.maxBudget(), compatibility);
    }
    public static String reason(Decision decision, DiscoveryRequest request, CandidateSuggestion candidate, IntakeTemplates.IntakeTemplate template) {
        var checks = MinimumRequirementMatcher.check(template.minimumRequirements(), request.selectedRequirements(), candidate.facts());
        var compatibility = CompatibilityMatcher.check(decision.templateKey(), request.compatibility(), candidate.facts(), candidate.officialOffer(), true);
        var input = new DiscoveryInput(template.label(), request.selectedUses(), request.maxBudget(), request.compatibility(), template.facts(), List.of(), List.of());
        return DiscoveryExplanation.explain(input, candidate.officialOffer(), candidate.facts(), checks, compatibility);
    }
}
