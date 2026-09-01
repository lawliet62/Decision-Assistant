package com.decisionassistant.guidance;

import com.decisionassistant.ai.AiCallExecutor;
import com.decisionassistant.ai.AiContracts;
import com.decisionassistant.decision.Decision;
import com.decisionassistant.decision.DecisionRepository;
import com.decisionassistant.decision.DecisionTypes;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
public class EvidenceWorkflowWorker {
    private static final Logger log = LoggerFactory.getLogger(EvidenceWorkflowWorker.class);
    private final EvidenceImportRepository imports;
    private final CandidateSuggestionRepository suggestions;
    private final DecisionQuestionRepository questions;
    private final DecisionRepository decisions;
    private final WebEvidenceProvider web;
    private final WebProperties webProperties;
    private final AiContracts.DecisionStructurer structurer;
    private final AiCallExecutor aiExecutor;
    private final Clock clock = Clock.systemUTC();

    public EvidenceWorkflowWorker(EvidenceImportRepository imports, CandidateSuggestionRepository suggestions,
                                  DecisionQuestionRepository questions, DecisionRepository decisions,
                                  WebEvidenceProvider web, WebProperties webProperties,
                                  ObjectProvider<AiContracts.DecisionStructurer> structurer,
                                  AiCallExecutor aiExecutor) {
        this.imports = imports; this.suggestions = suggestions; this.questions = questions;
        this.decisions = decisions; this.web = web; this.webProperties = webProperties;
        this.structurer = structurer.getIfAvailable(); this.aiExecutor = aiExecutor;
    }

    @Scheduled(fixedDelayString = "${decision-assistant.web.poll-interval:2000}")
    @Transactional
    public void processNext() {
        if (!webProperties.configured()) return;
        var run = imports.findFirstByStatusOrderByCreatedAtAsc(EvidenceImport.Status.PENDING).orElse(null);
        if (run == null) return;
        var now = Instant.now(clock);
        run.running(now);
        imports.flush();
        try {
            var page = web.scrape(run.url());
            var excerpt = excerpt(page.markdown(), 6000);
            run.succeeded(page.title(), hash(page.markdown()), excerpt, web.name(), Instant.now(clock));
            log.info("product_event=evidence_import_succeeded decision_id={} import_id={}", run.decisionId(), run.id());
            var decision = decisions.findById(run.decisionId()).orElseThrow();
            decision.applyImportedOption(run.optionId(), page.title(), excerpt, Instant.now(clock));
            if (imports.countByDecisionIdAndStatusIn(decision.id(),
                    List.of(EvidenceImport.Status.PENDING, EvidenceImport.Status.RUNNING)) == 0) {
                finish(decision);
            }
            decisions.save(decision);
        } catch (RuntimeException exception) {
            run.failed(safeMessage(exception), web.name(), Instant.now(clock));
            log.warn("product_event=evidence_import_failed decision_id={} import_id={} reason={}",
                    run.decisionId(), run.id(), exception.getClass().getSimpleName());
            decisions.findById(run.decisionId()).ifPresent(decision -> {
                decision.processingStatus(run.status() == EvidenceImport.Status.PENDING
                        ? DecisionTypes.ProcessingStatus.IMPORTING : DecisionTypes.ProcessingStatus.NEEDS_INPUT,
                        Instant.now(clock));
                decisions.save(decision);
            });
        }
    }

    private void finish(Decision decision) {
        if (decision.aiConsent() && structurer != null) {
            try {
                var input = new AiContracts.StructureInput(decision.title(), decision.context().intendedUse(),
                        decision.intentText(), decision.options().stream().map(option -> new AiContracts.OptionInput(
                        option.id(), option.name(), option.description(), option.sourceText())).toList());
                applyProposal(decision, aiExecutor.call(() -> structurer.structure(input)).value());
            } catch (RuntimeException ignored) {
                // Imported evidence remains useful when the optional AI proposal fails.
            }
        }
        discoverCandidates(decision);
        decision.processingStatus(DecisionTypes.ProcessingStatus.NEEDS_INPUT, Instant.now(clock));
    }

    private void applyProposal(Decision decision, AiContracts.StructureProposal proposal) {
        if (proposal == null) return;
        for (var value : proposal.values() == null ? List.<AiContracts.ValueSuggestion>of() : proposal.values()) {
            try {
                var optionId = UUID.fromString(value.optionId());
                var criterion = decision.criteria().stream()
                        .filter(item -> item.name().equalsIgnoreCase(value.criterionName())).findFirst().orElse(null);
                if (criterion == null || decision.options().stream().noneMatch(option -> option.id().equals(optionId))) continue;
                var certainty = "CONFIRMED".equalsIgnoreCase(value.certainty())
                        ? DecisionTypes.ValueCertainty.CONFIRMED : DecisionTypes.ValueCertainty.ESTIMATED;
                var sourceUrl = decision.options().stream().filter(option -> option.id().equals(optionId))
                        .map(option -> option.sourceUrl()).findFirst().orElse(null);
                decision.upsertGeneratedValue(optionId, criterion.id(), value.numericValue(), value.rating(), certainty,
                        sourceUrl, value.evidenceNote(), DecisionTypes.ValueOrigin.WEB_EXTRACTED);
            } catch (IllegalArgumentException ignored) {
                // Invalid proposal references are ignored instead of mutating unrelated draft data.
            }
        }
        var open = questions.findTop3ByDecisionIdAndStatusOrderByPriorityAscCreatedAtAsc(
                decision.id(), DecisionQuestion.Status.OPEN).size();
        if (open < 3 && proposal.missingInformation() != null) {
            proposal.missingInformation().stream().limit(3 - open).forEach(item -> questions.save(
                    new DecisionQuestion(decision.id(), DecisionQuestion.Kind.TEXT, "ai.missing", item,
                            "이 정보가 후보 간 차이를 설명하는 데 필요합니다.", List.of(), 20, Instant.now(clock))));
        }
    }

    private void discoverCandidates(Decision decision) {
        if (!suggestions.findByDecisionIdOrderByCreatedAtAsc(decision.id()).isEmpty()) return;
        var existingUrls = decision.options().stream().map(option -> option.sourceUrl()).filter(java.util.Objects::nonNull).toList();
        var query = categoryName(decision.templateKey()) + " 비교 추천 대안 "
                + decision.options().stream().map(option -> option.name()).limit(2).reduce("", (a, b) -> a + " " + b);
        try {
            web.search(query.trim(), 5).stream()
                    .filter(item -> item.url() != null && !item.url().isBlank() && !existingUrls.contains(item.url()))
                    .filter(item -> !suggestions.existsByDecisionIdAndUrl(decision.id(), item.url()))
                    .limit(3)
                    .forEach(item -> suggestions.save(new CandidateSuggestion(decision.id(), item.title(), item.url(),
                            excerpt(item.description(), 500), web.name(), Instant.now(clock))));
        } catch (RuntimeException ignored) {
            // Candidate discovery is optional and must not invalidate imported evidence.
        }
    }

    private String categoryName(DecisionTypes.TemplateKey key) {
        return switch (key) { case LAPTOP -> "노트북"; case PHONE -> "스마트폰"; case TABLET -> "태블릿";
            case MONITOR -> "모니터"; case CUSTOM -> "전자기기"; };
    }
    private String excerpt(String text, int max) { if (text == null) return ""; return text.length() <= max ? text : text.substring(0, max); }
    private String safeMessage(RuntimeException exception) { var message = exception.getMessage(); return message == null ? "외부 수집에 실패했습니다." : excerpt(message, 1000); }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
