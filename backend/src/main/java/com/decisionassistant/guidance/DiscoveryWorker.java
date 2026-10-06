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
    private final DiscoverySupplementer supplementer;
    private final java.util.concurrent.atomic.AtomicBoolean processing = new java.util.concurrent.atomic.AtomicBoolean();
    private final ProductDiscoverer ai;
    private final AiCallExecutor executor;
    private final AiProperties properties;
    private final DiscoveryService service;
    private final IntakeTemplates templates;
    private final TransactionTemplate tx;
    public DiscoveryWorker(ProductDiscoveryRepository runs, DecisionRepository decisions,
                           CandidateSuggestionRepository candidates, ProductLookupService search,
                           DiscoveryPageCollector collector, DiscoverySupplementer supplementer, ObjectProvider<ProductDiscoverer> ai, AiCallExecutor executor,
                           AiProperties properties, DiscoveryService service, IntakeTemplates templates,
                           PlatformTransactionManager manager) {
        this.runs = runs; this.decisions = decisions; this.candidates = candidates; this.search = search;
        this.collector = collector; this.supplementer = supplementer; this.ai = ai.getIfAvailable(); this.executor = executor; this.properties = properties;
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
            var priority = new ArrayList<>(template.minimumRequirements().stream().filter(d -> condition.selectedRequirements().containsKey(d.id()))
                    .map(com.decisionassistant.template.MinimumRequirements.MinimumRequirement::factName).toList());
            if (condition.compatibility() != null) priority.add(switch (work.category()) { case EARBUDS -> "호환성"; case MONITOR -> "연결 단자"; default -> "OS"; });
            var collected = collector.collect(work.id(), unseen, template.facts(), priority, OfficialStorePolicy.version(condition));
            var collectionFailures = new ArrayList<>(collected.failures());
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
            var supplements = supplementer.collect(work.id(), work.owner(), condition, template, pages);
            collectionFailures.addAll(supplements.failures());
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
            var verified = validateOfficialProducts(work.id(), result.value(), input, supplements.factsByPurchaseUrl());
            var groups = new EnumMap<DiscoveryRanking.Group, Integer>(DiscoveryRanking.Group.class);
            for (var candidate : verified) {
                var checks = MinimumRequirementMatcher.check(template.minimumRequirements(), condition.selectedRequirements(), candidate.facts());
                var compatibility = CompatibilityMatcher.check(work.category(), condition.compatibility(), candidate.facts(), candidate.officialOffer());
                var loggedChecks = new ArrayList<>(checks);
                if (compatibility != null) loggedChecks.add(compatibility);
                for (var check : loggedChecks) log.info("product_event=discovery_requirement_check decision_id={} field={} status={} reason={}",
                        work.id(), check.id(), check.status(), check.unknownReason() == null ? "none" : check.unknownReason());
                candidate.reason(DiscoveryExplanation.explain(input, candidate.officialOffer(), candidate.facts(), checks, compatibility));
                groups.merge(DiscoveryRanking.group(candidate.officialOffer(), checks, condition.maxBudget(), compatibility), 1, Integer::sum);
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
        return validateOfficialProducts(id, proposal, input, Map.of());
    }
    static List<CandidateSuggestion> validateOfficialProducts(UUID id, DiscoveryProposal proposal, DiscoveryInput input,
                                                             Map<String, List<ProductFact>> supplements) {
        var products = new HashMap<String, OfficialProduct>();
        input.pages().stream().filter(page -> page.officialProduct() != null).forEach(page -> products.put(page.url(), page.officialProduct()));
        var accepted = new ArrayList<CandidateSuggestion>();
        for (var candidate : validateProducts(id, proposal, input)) {
            var product = products.get(candidate.url());
            if (product == null || !product.name().equals(candidate.name())) continue;
            candidate.officialProduct(product);
            var facts = new ArrayList<>(candidate.facts().stream()
                    .filter(fact -> !"가격".equals(fact.name()) && candidate.url().equals(fact.sourceUrl())).toList());
            var source = input.pages().stream().filter(p -> p.url().equals(candidate.url())).findFirst().orElseThrow();
            for (var block : source.evidenceBlocks()) {
                if (!block.productIdentity().equals(product.identity()) || !input.factNames().contains(block.factName())) continue;
                var fact = SpecNormalizer.fact(block, source.url(), source.fetchedAt());
                if (facts.stream().noneMatch(f -> f.name().equals(fact.name()) && f.quote().equals(fact.quote()))) facts.add(fact);
                if (fact.normalizationVersion() != null) log.info("product_event=discovery_field_validation decision_id={} field={} outcome=NORMALIZED", id, DiscoveryPageCollector.fieldKey(fact.name()));
            }
            var offer = product.offer();
            // 보완 문서는 별도의 서버 검증 경로에서 온 사양만 추가한다. AI의 임의 교차 출처 연결은 허용하지 않는다.
            facts.addAll(supplements.getOrDefault(source.url(), List.of()).stream()
                    .filter(f -> !"가격".equals(f.name()) && input.factNames().contains(f.name()) && f.officialEvidence() != null
                            && product.model() != null && product.model().code().equals(f.officialEvidence().model())).toList());
            facts.addFirst(new ProductFact("가격", offer.price().stripTrailingZeros().toPlainString() + " KRW", offer.purchaseUrl(),
                    product.priceQuote() == null ? product.name() + " · " + offer.priceBasis() + " " + offer.price().toPlainString() + " KRW" : product.priceQuote(), offer.fetchedAt(), "UNREVIEWED"));
            candidate.discovered(facts);
            accepted.add(candidate);
        }
        var models = new LinkedHashMap<String, CandidateSuggestion>();
        // 같은 색상 그룹의 별도 판매 페이지는 검증된 후보 중 저렴한 한 페이지만 남긴다.
        // 서로 다른 페이지의 사양이나 가격을 합치지 않는다.
        accepted.stream().sorted(DiscoveryRanking.byPrice()).forEach(candidate -> models.putIfAbsent(candidate.productIdentity(), candidate));
        return List.copyOf(models.values());
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
                var block = page == null || fact.evidenceId() == null ? null : page.evidenceBlocks().stream()
                        .filter(b -> b.id().equals(fact.evidenceId())).findFirst().orElse(null);
                String rejection = page == null ? "SOURCE_MISSING" : null;
                if (rejection == null && fact.evidenceId() != null) {
                    if (block == null || !Objects.equals(block.quote(), fact.quote())) rejection = "QUOTE_MISMATCH";
                    else if (page.officialProduct() == null || !page.officialProduct().name().equals(product.name())
                            || !page.officialProduct().identity().equals(block.productIdentity()) || !page.url().equals(product.sourceUrl())) rejection = "PRODUCT_MISMATCH";
                    else if (!block.factName().equals(fact.name())) rejection = "FIELD_MISMATCH";
                    else if (!block.value().contains(fact.value())) rejection = "VALUE_MISMATCH";
                } else if (rejection == null) {
                    if (!quoted(page, fact.quote())) rejection = "QUOTE_MISMATCH";
                    else if (!fact.quote().contains(fact.value())) rejection = "VALUE_MISMATCH";
                    else if (!DiscoveryEvidence.belongsToProduct(page.excerpt(), product.name(), fact.quote(), productNames)) rejection = "PRODUCT_MISMATCH";
                    else if (!SpecNormalizer.fieldMatches(fact.name(), fact.value(), fact.quote())) rejection = "FIELD_MISMATCH";
                }
                if (rejection != null) {
                    rejected.merge(rejection, 1, Integer::sum);
                    log.info("product_event=discovery_field_validation decision_id={} field={} outcome={}", decisionId, DiscoveryPageCollector.fieldKey(fact.name()), rejection);
                    continue;
                }
                if ("가격".equals(fact.name()) && !fact.value().matches("(?is).*(KRW|원|₩|USD|\\$|EUR|€|JPY|¥|GBP|£).*$")) {
                    rejected.merge("CURRENCY_MISSING", 1, Integer::sum); continue;
                }
                facts.add(block == null ? new ProductFact(fact.name(), fact.value(), page.url(),
                        fact.quote(), page.fetchedAt(), "UNREVIEWED") : SpecNormalizer.fact(block, page.url(), page.fetchedAt()));
            }
            for (var field : input.factNames()) if (product.facts() == null || product.facts().stream().filter(Objects::nonNull).noneMatch(f -> field.equals(f.name())))
                log.info("product_event=discovery_field_validation decision_id={} field={} outcome=AI_OMITTED", decisionId, DiscoveryPageCollector.fieldKey(field));
            var candidate = new CandidateSuggestion(decisionId, product.name().strip(), source.url(),
                    "수집한 조건별 근거를 확인하세요.", PROMPT_VERSION, Instant.now());
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
    private static boolean editable(Decision decision) {
        return decision.status() == DecisionTypes.DecisionStatus.DRAFT || decision.status() == DecisionTypes.DecisionStatus.DEFERRED;
    }
}
