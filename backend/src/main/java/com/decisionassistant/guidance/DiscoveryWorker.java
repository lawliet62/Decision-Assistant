package com.decisionassistant.guidance;

import com.decisionassistant.ai.*;
import com.decisionassistant.decision.*;
import com.decisionassistant.lookup.ProductLookupService;
import com.decisionassistant.template.IntakeTemplates;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.decisionassistant.guidance.DiscoveryContracts.*;

@Component
public class DiscoveryWorker {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DiscoveryWorker.class);
    private final ProductDiscoveryRepository runs;
    private final DecisionRepository decisions;
    private final CandidateSuggestionRepository candidates;
    private final ProductLookupService search;
    private final WebEvidenceProvider web;
    private final ProductDiscoverer ai;
    private final AiCallExecutor executor;
    private final AiProperties properties;
    private final DiscoveryService service;
    private final IntakeTemplates templates;
    private final PublicUrlPolicy urls;
    private final TransactionTemplate tx;
    public DiscoveryWorker(ProductDiscoveryRepository runs, DecisionRepository decisions,
                           CandidateSuggestionRepository candidates, ProductLookupService search,
                           WebEvidenceProvider web, ObjectProvider<ProductDiscoverer> ai, AiCallExecutor executor,
                           AiProperties properties, DiscoveryService service, IntakeTemplates templates,
                           PublicUrlPolicy urls, PlatformTransactionManager manager) {
        this.runs = runs; this.decisions = decisions; this.candidates = candidates; this.search = search;
        this.web = web; this.ai = ai.getIfAvailable(); this.executor = executor; this.properties = properties;
        this.service = service; this.templates = templates; this.urls = urls; tx = new TransactionTemplate(manager);
    }
    private record Work(UUID id, UUID owner, DecisionTypes.TemplateKey category, DiscoveryRequest conditions, long version) {}

    @Scheduled(fixedDelayString = "${decision-assistant.web.poll-interval:2000}")
    public void processNext() {
        Work work = tx.execute(status -> {
            runs.failInterrupted(Instant.now().minus(15, ChronoUnit.MINUTES));
            var run = runs.claimNext().orElse(null);
            if (run == null) return null;
            var decision = decisions.findById(run.decisionId()).orElseThrow();
            if (!editable(decision)) { run.finish(ProductDiscovery.Status.FAILED, "종료된 비교입니다."); return null; }
            run.running(); runs.flush();
            return new Work(decision.id(), decision.ownerUserId(), decision.templateKey(), run.conditions(), run.version());
        });
        if (work == null) return;
        var start = System.nanoTime();
        try {
            service.validate(work.category(), work.conditions());
            if (ai == null) throw new IllegalStateException("AI unavailable");
            var template = templates.required(work.category());
            var condition = work.conditions();
            var query = template.label() + " 제품 비교 한국 " + Objects.toString(condition.use(), "")
                    + (condition.maxBudget() == null ? "" : " " + condition.maxBudget().toPlainString() + "원 이하")
                    + " " + Objects.toString(condition.compatibility(), "");
            var pages = new ArrayList<SourcePage>();
            var failed = false;
            for (var result : search.lookupForOwner(query, work.owner())) {
                try {
                    var url = urls.validate(result.url());
                    var page = web.scrape(url);
                    if (page == null || page.markdown() == null || page.markdown().isBlank()) { failed = true; continue; }
                    pages.add(new SourcePage(url, page.title(), truncate(page.markdown(), 6000), Instant.now()));
                } catch (RuntimeException ex) { failed = true; }
            }
            if (pages.isEmpty()) throw new IllegalStateException("No usable source pages");
            var input = new DiscoveryInput(template.label(), condition.use(), condition.maxBudget(),
                    condition.compatibility(), template.facts(), pages);
            var result = executor.call(() -> ai.discover(input));
            var verified = validateProducts(work.id(), result.value(), input);
            var partial = failed || verified.size() < 2;
            tx.executeWithoutResult(status -> {
                var run = runs.findById(work.id()).orElseThrow();
                var decision = decisions.lockByIdAndOwner(work.id(), work.owner()).orElseThrow();
                if (run.status() != ProductDiscovery.Status.RUNNING || run.version() != work.version()) return;
                if (!editable(decision)) { run.finish(ProductDiscovery.Status.FAILED, "종료된 비교입니다."); return; }
                var existing = candidates.findByDecisionIdOrderByCreatedAtAsc(work.id());
                var names = new HashMap<String, CandidateSuggestion>();
                existing.stream().filter(CandidateSuggestion::discovered).forEach(item -> names.put(item.name().toLowerCase(Locale.ROOT), item));
                for (var candidate : verified) {
                    var key = candidate.name().toLowerCase(Locale.ROOT);
                    var previous = names.get(key);
                    if (previous != null) {
                        // 승인한 정보와 제외 의사는 재시도로 변경하지 않는다.
                        if (previous.status() == CandidateSuggestion.Status.PROPOSED) previous.refreshDiscovery(candidate);
                    } else if (names.size() < 5) {
                        candidates.save(candidate); names.put(key, candidate);
                    }
                }
                run.usage(properties.provider(), properties.model(), result.inputTokens(), result.outputTokens(),
                        (System.nanoTime() - start) / 1_000_000);
                run.finish(verified.isEmpty() ? ProductDiscovery.Status.FAILED : partial ? ProductDiscovery.Status.PARTIAL : ProductDiscovery.Status.SUCCEEDED,
                        verified.isEmpty() ? "제품을 확인할 근거가 부족합니다. 조건을 바꾸거나 직접 추가하세요." : partial ? "일부 정보만 확인했습니다. 직접 추가하거나 다시 시도할 수 있습니다." : null);
            });
            log.info("product_event=discovery_finished decision_id={} candidates={} partial={}", work.id(), verified.size(), partial);
        } catch (RuntimeException ex) {
            tx.executeWithoutResult(status -> {
                var run = runs.findById(work.id()).orElseThrow();
                if (run.status() == ProductDiscovery.Status.RUNNING && run.version() == work.version())
                    run.finish(ProductDiscovery.Status.FAILED, "추천 정보를 가져오지 못했습니다. 서비스 상태·검색 한도를 확인하거나 직접 추가하세요.");
            });
            log.warn("product_event=discovery_failed decision_id={} reason={}", work.id(), ex.getClass().getSimpleName());
        }
    }

    static List<CandidateSuggestion> validateProducts(UUID decisionId, DiscoveryProposal proposal, DiscoveryInput input) {
        if (proposal == null || proposal.products() == null) return List.of();
        var pages = new HashMap<String, SourcePage>();
        input.pages().forEach(page -> pages.put(page.url(), page));
        var result = new ArrayList<CandidateSuggestion>();
        var names = new HashSet<String>();
        for (var product : proposal.products()) {
            if (product == null || product.name() == null || product.name().isBlank() || product.name().length() > 200) continue;
            var source = pages.get(product.sourceUrl());
            if (source == null || !quoted(source, product.modelQuote()) || !product.modelQuote().contains(product.name())) continue;
            if (!names.add(product.name().strip().toLowerCase(Locale.ROOT))) continue;
            var facts = new LinkedHashMap<String, ProductFact>();
            if (product.facts() != null) for (var fact : product.facts()) {
                if (fact == null || fact.name() == null || !input.factNames().contains(fact.name()) || fact.value() == null
                        || fact.value().isBlank() || fact.value().length() > 300) continue;
                var page = pages.get(fact.sourceUrl());
                if (page == null || !quoted(page, fact.quote()) || !fact.quote().contains(fact.value())
                        || !fact.quote().contains(product.name())) continue;
                if ("가격".equals(fact.name()) && !fact.value().matches("(?is).*(KRW|원|₩|USD|\\$|EUR|€|JPY|¥|GBP|£).*$")) continue;
                facts.putIfAbsent(fact.name(), new ProductFact(fact.name(), fact.value(), page.url(),
                        fact.quote(), page.fetchedAt(), "UNREVIEWED"));
            }
            var candidate = new CandidateSuggestion(decisionId, product.name().strip(), source.url(),
                    truncate(Objects.toString(product.reason(), "조건과 주요 정보를 확인해 보세요."), 500), "discovery-v1", Instant.now());
            candidate.discovered(new ArrayList<>(facts.values()));
            candidate.sourceEvidence(product.modelQuote(), source.fetchedAt()); result.add(candidate);
            if (result.size() == 5) break;
        }
        return result;
    }
    private static boolean quoted(SourcePage page, String quote) {
        return quote != null && !quote.isBlank() && quote.length() <= 600 && page.excerpt().contains(quote);
    }
    private static String truncate(String text, int max) { return text.substring(0, Math.min(max, text.length())); }
    private static boolean editable(Decision decision) {
        return decision.status() == DecisionTypes.DecisionStatus.DRAFT || decision.status() == DecisionTypes.DecisionStatus.DEFERRED;
    }
}
