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
import static com.decisionassistant.guidance.DiscoveryFailure.*;

@Component
public class DiscoveryWorker {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DiscoveryWorker.class);
    private final ProductDiscoveryRepository runs;
    private final DecisionRepository decisions;
    private final CandidateSuggestionRepository candidates;
    private final ProductLookupService search;
    private final DiscoveryPageCollector collector;
    private final java.util.concurrent.atomic.AtomicBoolean processing = new java.util.concurrent.atomic.AtomicBoolean();
    private final ProductDiscoverer ai;
    private final AiCallExecutor executor;
    private final AiProperties properties;
    private final DiscoveryService service;
    private final IntakeTemplates templates;
    private final TransactionTemplate tx;
    public DiscoveryWorker(ProductDiscoveryRepository runs, DecisionRepository decisions,
                           CandidateSuggestionRepository candidates, ProductLookupService search,
                           DiscoveryPageCollector collector, ObjectProvider<ProductDiscoverer> ai, AiCallExecutor executor,
                           AiProperties properties, DiscoveryService service, IntakeTemplates templates,
                           PlatformTransactionManager manager) {
        this.runs = runs; this.decisions = decisions; this.candidates = candidates; this.search = search;
        this.collector = collector; this.ai = ai.getIfAvailable(); this.executor = executor; this.properties = properties;
        this.service = service; this.templates = templates; tx = new TransactionTemplate(manager);
    }
    private record Work(UUID id, UUID owner, DecisionTypes.TemplateKey category, DiscoveryRequest conditions, UUID executionId,
                        int generation, DiscoveryExclusions excluded) {}

    @Scheduled(scheduler = "discoveryScheduler", fixedDelayString = "${decision-assistant.web.poll-interval:2000}")
    public void processNext() {
        if (!processing.compareAndSet(false, true)) return;
        try { runNext(); } finally { processing.set(false); }
    }

    private void runNext() {
        Work work = tx.execute(status -> {
            runs.failInterrupted(Instant.now().minus(15, ChronoUnit.MINUTES));
            var run = runs.claimNext().orElse(null);
            if (run == null) return null;
            var decision = decisions.findById(run.decisionId()).orElseThrow();
            if (!editable(decision)) { run.finish(ProductDiscovery.Status.FAILED, "종료된 비교입니다."); return null; }
            log.info("product_event=discovery_queue_timing decision_id={} duration_ms={}", decision.id(),
                    Math.max(0, java.time.Duration.between(run.updatedAt(), Instant.now()).toMillis()));
            run.running(); runs.flush();
            var previous = candidates.findByDecisionIdOrderByCreatedAtAsc(decision.id()).stream()
                    .filter(item -> item.discovered() && item.generation() < run.generation()).toList();
            return new Work(decision.id(), decision.ownerUserId(), decision.templateKey(), run.conditions(), run.executionId(), run.generation(),
                    DiscoveryExclusions.from(previous, decision.options().stream().map(DecisionOption::name).toList()));
        });
        if (work == null) return;
        var start = System.nanoTime();
        var stage = Stage.CONFIGURATION;
        try (var trace = new DiscoveryTrace(work.id())) {
            service.validate(work.category(), work.conditions());
            if (ai == null) throw new IllegalStateException("AI unavailable");
            var template = templates.required(work.category(), work.conditions().templateVersion());
            var condition = work.conditions().normalized(template);
            var minimums = MinimumRequirementMatcher.conditions(template.minimumRequirements(), condition.selectedRequirements());
            var query = DiscoveryQuery.build(template, condition, work.excluded());
            stage = Stage.SEARCH;
            trace.enter(stage);
            progress(work, stage);
            var searchResults = search.lookupForOwner(query, work.owner());
            if (searchResults.isEmpty()) {
                empty(work, "NO_OFFICIAL_RESULTS", false);
                return;
            }
            var officialResults = searchResults.stream().filter(item -> OfficialStorePolicy.store(item.url()).isPresent()).toList();
            log.info("product_event=discovery_search_filter decision_id={} public={} official={}", work.id(), searchResults.size(), officialResults.size());
            if (officialResults.isEmpty()) { empty(work, "NO_OFFICIAL_RESULTS", false); return; }
            var unseen = officialResults.stream().filter(item -> !work.excluded().containsUrl(item.url())).toList();
            if (unseen.isEmpty()) { empty(work, "NO_NEW_PRODUCTS", false); return; }
            stage = Stage.COLLECTION;
            trace.enter(stage);
            progress(work, stage);
            var collected = collector.collect(work.id(), unseen, template.facts());
            var collectionFailures = collected.failures();
            log.info("product_event=discovery_collection_results decision_id={} attempted={} collected={} failed={}",
                    work.id(), unseen.size(), collected.pages().size(), collectionFailures.size());
            if (collected.pages().isEmpty()) {
                fail(work, collectionFailures.getFirst(), null);
                return;
            }
            var eligible = collected.pages().stream().filter(page -> page.officialProduct() != null).toList();
            var pages = eligible.stream().filter(page -> !work.excluded().contains(page.officialProduct())).toList();
            log.info("product_event=discovery_offer_validation decision_id={} collected={} eligible={} rejected={}",
                    work.id(), collected.pages().size(), pages.size(), collected.pages().size() - pages.size());
            if (pages.isEmpty()) { empty(work, eligible.isEmpty() ? "NO_VERIFIED_OFFERS" : "NO_NEW_PRODUCTS", !collectionFailures.isEmpty()); return; }
            var input = new DiscoveryInput(template.label(), condition.uses(), condition.maxBudget(),
                    condition.compatibility(), template.facts(), pages, minimums);
            stage = Stage.AI;
            trace.enter(stage);
            progress(work, stage);
            var result = executor.call(() -> {
                log.info("product_event=discovery_provider_call decision_id={} provider=ai model={}", work.id(), properties.model());
                return ai.discover(input);
            });
            log.info("product_event=discovery_ai_usage decision_id={} provider={} model={} input_tokens={} output_tokens={}",
                    work.id(), properties.provider(), properties.model(), result.inputTokens(), result.outputTokens());
            stage = Stage.VALIDATION;
            trace.enter(stage);
            progress(work, stage);
            var verified = validateOfficialProducts(work.id(), result.value(), input);
            var groups = new EnumMap<DiscoveryRanking.Group, Integer>(DiscoveryRanking.Group.class);
            for (var candidate : verified) {
                var checks = MinimumRequirementMatcher.check(template.minimumRequirements(), condition.selectedRequirements(), candidate.facts());
                groups.merge(DiscoveryRanking.group(candidate.officialOffer(), checks, condition.maxBudget()), 1, Integer::sum);
            }
            log.info("product_event=discovery_candidate_validation decision_id={} proposed={} verified={} recommended={} unknown={} below={}",
                    work.id(), result.value() == null || result.value().products() == null ? 0 : result.value().products().size(), verified.size(),
                    groups.getOrDefault(DiscoveryRanking.Group.RECOMMENDED, 0), groups.getOrDefault(DiscoveryRanking.Group.UNKNOWN, 0),
                    groups.getOrDefault(DiscoveryRanking.Group.BELOW, 0));
            var partial = !collectionFailures.isEmpty();
            stage = Stage.PERSISTENCE;
            trace.enter(stage);
            progress(work, stage);
            tx.executeWithoutResult(status -> {
                var run = runs.findById(work.id()).orElseThrow();
                var decision = decisions.lockByIdAndOwner(work.id(), work.owner()).orElseThrow();
                if (!active(run, work)) return;
                if (!editable(decision)) { run.finish(ProductDiscovery.Status.FAILED, "종료된 비교입니다."); return; }
                var existing = candidates.findByDecisionIdOrderByCreatedAtAsc(work.id());
                var names = new HashMap<String, CandidateSuggestion>();
                existing.stream().filter(item -> item.discovered() && item.generation() == work.generation()).forEach(item -> names.put(item.name().toLowerCase(Locale.ROOT), item));
                for (var candidate : verified) {
                    candidate.generation(work.generation());
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
                if (!verified.isEmpty()) run.displayCurrentGeneration();
                run.finish(verified.isEmpty() ? ProductDiscovery.Status.FAILED : partial ? ProductDiscovery.Status.PARTIAL : ProductDiscovery.Status.SUCCEEDED,
                        verified.isEmpty() ? Code.EVIDENCE_INSUFFICIENT.message() : partial ? "일부 정보만 확인했습니다. 직접 추가하거나 다시 시도할 수 있습니다." : null);
            });
            if (verified.isEmpty()) logFailure("discovery_failed", work, new DiscoveryFailure(Stage.VALIDATION, Code.EVIDENCE_INSUFFICIENT), null);
            else log.info("product_event=discovery_finished decision_id={} candidates={} partial={}", work.id(), verified.size(), partial);
        } catch (RuntimeException ex) {
            fail(work, DiscoveryFailure.classify(stage, ex), ex);
        }
    }

    private void fail(Work work, DiscoveryFailure failure, Throwable cause) {
        // 실패 상태 저장까지 불가능한 경우에도 최초 실패 단계를 관측할 수 있게 한다.
        logFailure("discovery_failed", work, failure, cause);
        tx.executeWithoutResult(status -> {
            var run = runs.findById(work.id()).orElseThrow();
            if (active(run, work))
                run.finish(ProductDiscovery.Status.FAILED, failure.message());
        });
    }

    private static boolean active(ProductDiscovery run, Work work) {
        return run.status() == ProductDiscovery.Status.RUNNING && Objects.equals(run.executionId(), work.executionId());
    }
    private void progress(Work work, Stage stage) {
        tx.executeWithoutResult(status -> {
            var run = runs.findById(work.id()).orElseThrow();
            if (!active(run, work)) throw new IllegalStateException("추천 실행이 변경되었습니다.");
            run.stage(stage);
        });
    }
    private void empty(Work work, String reason, boolean partial) {
        tx.executeWithoutResult(status -> {
            var run = runs.findById(work.id()).orElseThrow();
            if (!active(run, work)) return;
            run.empty(reason);
            if (partial) run.finish(ProductDiscovery.Status.PARTIAL, "일부 페이지를 가져오지 못했습니다. 다시 시도할 수 있습니다.");
        });
    }

    static List<CandidateSuggestion> validateOfficialProducts(UUID id, DiscoveryProposal proposal, DiscoveryInput input) {
        var products = new HashMap<String, OfficialProduct>();
        input.pages().stream().filter(page -> page.officialProduct() != null).forEach(page -> products.put(page.url(), page.officialProduct()));
        var accepted = new ArrayList<CandidateSuggestion>();
        for (var candidate : validateProducts(id, proposal, input)) {
            var product = products.get(candidate.url());
            if (product == null || !product.name().equals(candidate.name())) continue;
            candidate.officialProduct(product);
            var facts = new ArrayList<>(candidate.facts().stream()
                    .filter(fact -> !"가격".equals(fact.name()) && candidate.url().equals(fact.sourceUrl())).toList());
            var offer = product.offer();
            facts.addFirst(new ProductFact("가격", offer.price().stripTrailingZeros().toPlainString() + " KRW", offer.purchaseUrl(),
                    product.name() + " · " + offer.priceBasis() + " " + offer.price().toPlainString() + " KRW", offer.fetchedAt(), "UNREVIEWED"));
            candidate.discovered(facts);
            accepted.add(candidate);
        }
        return accepted.stream().sorted(DiscoveryRanking.byPrice()).toList();
    }

    private static void logFailure(String event, Work work, DiscoveryFailure failure, Throwable cause) {
        // 예외 메시지·stack trace에는 URL, 페이지 본문, provider 응답이 포함될 수 있다.
        log.warn("product_event={} decision_id={} stage={} code={} reason={} root_cause={} http_status={}",
                event, work.id(), failure.stage(), failure.code(), cause == null ? "none" : cause.getClass().getSimpleName(),
                DiscoveryFailure.rootCauseType(cause), DiscoveryFailure.httpStatus(cause));
    }

    static List<CandidateSuggestion> validateProducts(UUID decisionId, DiscoveryProposal proposal, DiscoveryInput input) {
        if (proposal == null || proposal.products() == null) return List.of();
        var pages = new HashMap<String, SourcePage>();
        input.pages().forEach(page -> pages.put(page.url(), page));
        var result = new ArrayList<CandidateSuggestion>();
        var names = new HashSet<String>();
        var productNames = proposal.products().stream().filter(Objects::nonNull).map(ProposedProduct::name).filter(Objects::nonNull).toList();
        var rejected = new TreeMap<String, Integer>();
        int proposedFacts = 0;
        for (var product : proposal.products()) {
            if (product == null || product.name() == null || product.name().isBlank() || product.name().length() > 200) continue;
            var source = pages.get(product.sourceUrl());
            if (source == null || !quoted(source, product.modelQuote()) || !product.modelQuote().contains(product.name())) continue;
            if (!names.add(product.name().strip().toLowerCase(Locale.ROOT))) continue;
            var facts = new LinkedHashSet<ProductFact>();
            if (product.facts() != null) for (var fact : product.facts()) {
                proposedFacts++;
                if (fact == null || fact.name() == null || !input.factNames().contains(fact.name()) || fact.value() == null
                        || fact.value().isBlank() || fact.value().length() > 300) { rejected.merge("INVALID_FIELD", 1, Integer::sum); continue; }
                var page = pages.get(fact.sourceUrl());
                if (page == null || !quoted(page, fact.quote()) || !fact.quote().contains(fact.value())
                        || !DiscoveryEvidence.belongsToProduct(page.excerpt(), product.name(), fact.quote(), productNames)) {
                    rejected.merge("UNBOUND_EVIDENCE", 1, Integer::sum); continue;
                }
                if ("가격".equals(fact.name()) && !fact.value().matches("(?is).*(KRW|원|₩|USD|\\$|EUR|€|JPY|¥|GBP|£).*$")) {
                    rejected.merge("CURRENCY_MISSING", 1, Integer::sum); continue;
                }
                facts.add(new ProductFact(fact.name(), fact.value(), page.url(),
                        fact.quote(), page.fetchedAt(), "UNREVIEWED"));
            }
            var candidate = new CandidateSuggestion(decisionId, product.name().strip(), source.url(),
                    truncate(Objects.toString(product.reason(), "조건과 주요 정보를 확인해 보세요."), 500), PROMPT_VERSION, Instant.now());
            candidate.discovered(new ArrayList<>(facts));
            candidate.sourceEvidence(product.modelQuote(), source.fetchedAt()); result.add(candidate);
            if (result.size() == 5) break;
        }
        log.info("product_event=discovery_fact_validation decision_id={} proposed={} accepted={} rejected={}", decisionId, proposedFacts,
                result.stream().mapToInt(item -> item.facts().size()).sum(), rejected);
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
