package com.decisionassistant.guidance;

import com.decisionassistant.ai.AiCallExecutor;
import com.decisionassistant.ai.AiContracts;
import com.decisionassistant.decision.Decision;
import com.decisionassistant.decision.DecisionRepository;
import com.decisionassistant.decision.DecisionTypes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.eq;

class EvidenceWorkflowWorkerTest {

    @Test
    @SuppressWarnings("unchecked")
    void importsPublicEvidenceAndProposesButDoesNotAutoAcceptAnAlternative() {
        var imports = mock(EvidenceImportRepository.class);
        var suggestions = mock(CandidateSuggestionRepository.class);
        var questions = mock(DecisionQuestionRepository.class);
        var decisions = mock(DecisionRepository.class);
        var web = mock(WebEvidenceProvider.class);
        var search = mock(WebSearchProvider.class);
        var structurer = (ObjectProvider<AiContracts.DecisionStructurer>) mock(ObjectProvider.class);
        var aiExecutor = mock(AiCallExecutor.class);
        var webProperties = new WebProperties(true, "https://api.example", "key", Duration.ofSeconds(5));
        var decision = new Decision(UUID.randomUUID(), "노트북 비교", DecisionTypes.TemplateKey.LAPTOP,
                "laptop-v2", Instant.now());
        decision.startGuidedIntake("가벼운 개발용 노트북", false, true, "test", Instant.now());
        var option = decision.addOption(null, "후보 · example.com", null, "https://example.com/a", null);
        var run = new EvidenceImport(decision.id(), option.id(), option.sourceUrl(), Instant.now());

        when(imports.findFirstByStatusOrderByCreatedAtAsc(EvidenceImport.Status.PENDING)).thenReturn(Optional.of(run));
        when(imports.countByDecisionIdAndStatusIn(any(), any())).thenReturn(0L);
        when(imports.findByDecisionIdOrderByCreatedAtAsc(decision.id())).thenReturn(List.of(run));
        when(decisions.lockById(decision.id())).thenReturn(Optional.of(decision));
        when(web.scrape(run.url())).thenReturn(new WebEvidenceProvider.Page("Example Laptop", "# Example Laptop\n16GB RAM"));
        when(search.search(any(), any(Integer.class))).thenReturn(List.of(
                new WebSearchProvider.SearchResult("Alternative", "https://example.org/b", "더 가벼운 대안")));
        when(suggestions.findByDecisionIdOrderByCreatedAtAsc(decision.id())).thenReturn(List.of());
        when(structurer.getIfAvailable()).thenReturn(null);

        new EvidenceWorkflowWorker(imports, suggestions, questions, decisions, web, webProperties, search,
                structurer, aiExecutor).processNext();

        assertThat(run.status()).isEqualTo(EvidenceImport.Status.SUCCEEDED);
        assertThat(decision.options().getFirst().name()).isEqualTo("Example Laptop");
        assertThat(decision.processingStatus()).isEqualTo(DecisionTypes.ProcessingStatus.NEEDS_INPUT);
        verify(suggestions).save(any(CandidateSuggestion.class));
        assertThat(decision.options()).hasSize(1);
    }

    @Test
    void structuresEarlierEvidenceWhenTheLastSourceExhaustsRetries() throws Exception {
        var fixture = new Fixture();
        fixture.run.running(Instant.now());
        fixture.run.failed("first attempt", "firecrawl", Instant.now());
        var imported = fixture.decision.addOption(null, "Imported product", null, "https://example.com/ok", "Price: 100");
        var success = new EvidenceImport(fixture.decision.id(), imported.id(), imported.sourceUrl(), Instant.now());
        success.succeeded(imported.name(), "hash", imported.sourceText(), "firecrawl", Instant.now());
        when(fixture.imports.findByDecisionIdOrderByCreatedAtAsc(fixture.decision.id()))
                .thenReturn(List.of(success, fixture.run));
        var price = fixture.decision.addCriterion(null, "가격", DecisionTypes.CriterionKind.NUMERIC_UTILITY,
                DecisionTypes.PreferenceDirection.LOWER_BETTER, BigDecimal.ONE,
                BigDecimal.valueOf(1000), BigDecimal.ZERO, "KRW", true);
        fixture.decision.addCriterion(null, "비활성 기준", DecisionTypes.CriterionKind.RATING_1_TO_5,
                DecisionTypes.PreferenceDirection.NOT_APPLICABLE, BigDecimal.ONE, null, null, null, false);
        when(fixture.structurer.structure(any())).thenAnswer(invocation -> {
            AiContracts.StructureInput input = invocation.getArgument(0);
            assertThat(input.criteria()).containsExactly(new AiContracts.CriterionInput(
                    price.name(), price.kind(), price.unit()));
            assertThat(input.options()).anySatisfy(option -> {
                assertThat(option.id()).isEqualTo(imported.id());
                assertThat(option.sourceText()).isEqualTo("Price: 100");
            });
            var value = new AiContracts.ValueSuggestion(imported.id().toString(), price.name(),
                    BigDecimal.valueOf(100), null, "ESTIMATED", "Price: 100");
            return new AiContracts.AiResult<>(new AiContracts.StructureProposal(
                    List.of(), List.of(value), List.of(), List.of(), List.of()), 1, 1);
        });
        doThrow(new IllegalStateException("upstream credential detail")).when(fixture.web).scrape(fixture.run.url());

        fixture.worker().processNext();

        assertThat(fixture.run.status()).isEqualTo(EvidenceImport.Status.FAILED);
        assertThat(fixture.run.error()).doesNotContain("credential");
        assertThat(success.status()).isEqualTo(EvidenceImport.Status.SUCCEEDED);
        assertThat(fixture.decision.processingStatus()).isEqualTo(DecisionTypes.ProcessingStatus.NEEDS_INPUT);
        assertThat(fixture.decision.values()).singleElement().satisfies(value -> {
            assertThat(value.numericValue()).isEqualByComparingTo("100");
            assertThat(value.sourceUrl()).isEqualTo(imported.sourceUrl());
            assertThat(value.verificationStatus()).isEqualTo(DecisionTypes.VerificationStatus.UNREVIEWED);
        });
    }

    @Test
    void keepsImportingWhileAnotherSourceIsStillPending() throws Exception {
        var fixture = new Fixture();
        fixture.run.running(Instant.now());
        fixture.run.failed("first attempt", "firecrawl", Instant.now());
        when(fixture.imports.countByDecisionIdAndStatusIn(eq(fixture.decision.id()), any())).thenReturn(1L);
        doThrow(new IllegalStateException()).when(fixture.web).scrape(fixture.run.url());

        fixture.worker().processNext();

        assertThat(fixture.run.status()).isEqualTo(EvidenceImport.Status.FAILED);
        assertThat(fixture.decision.processingStatus()).isEqualTo(DecisionTypes.ProcessingStatus.IMPORTING);
        verifyNoInteractions(fixture.structurer, fixture.search);
    }

    @Test
    void retriesEmptyPagesWithoutCallingAiAndEventuallyStops() throws Exception {
        var fixture = new Fixture();
        when(fixture.web.scrape(fixture.run.url())).thenReturn(new WebEvidenceProvider.Page("Empty", " "));
        when(fixture.imports.countByDecisionIdAndStatusIn(eq(fixture.decision.id()), any()))
                .thenAnswer(ignored -> fixture.run.status() == EvidenceImport.Status.PENDING ? 1L : 0L);
        var worker = fixture.worker();

        worker.processNext();
        assertThat(fixture.run.status()).isEqualTo(EvidenceImport.Status.PENDING);
        assertThat(fixture.decision.processingStatus()).isEqualTo(DecisionTypes.ProcessingStatus.IMPORTING);
        worker.processNext();

        assertThat(fixture.run.status()).isEqualTo(EvidenceImport.Status.FAILED);
        assertThat(fixture.run.attemptCount()).isEqualTo(2);
        assertThat(fixture.decision.processingStatus()).isEqualTo(DecisionTypes.ProcessingStatus.NEEDS_INPUT);
        verifyNoInteractions(fixture.structurer, fixture.search);
    }

    @Test
    void aiFailureDoesNotChangeSuccessfulImportIntoFailure() throws Exception {
        var fixture = new Fixture();
        when(fixture.web.scrape(fixture.run.url())).thenReturn(new WebEvidenceProvider.Page("Product", "Specs"));
        when(fixture.structurer.structure(any())).thenThrow(new IllegalStateException("AI failed"));

        fixture.worker().processNext();

        assertThat(fixture.run.status()).isEqualTo(EvidenceImport.Status.SUCCEEDED);
        assertThat(fixture.decision.options().getFirst().sourceText()).isEqualTo("Specs");
        assertThat(fixture.decision.processingStatus()).isEqualTo(DecisionTypes.ProcessingStatus.NEEDS_INPUT);
    }

    @Test
    void finishingImportDoesNotEditAnAlreadyChosenDecision() throws Exception {
        var fixture = new Fixture();
        fixture.decision.resolve(DecisionTypes.DecisionStatus.CHOSEN, Instant.now());
        when(fixture.web.scrape(fixture.run.url())).thenReturn(new WebEvidenceProvider.Page("New name", "New specs"));
        fixture.worker().processNext();
        assertThat(fixture.run.status()).isEqualTo(EvidenceImport.Status.SUCCEEDED);
        assertThat(fixture.decision.options().getFirst().sourceText()).isNull();
        assertThat(fixture.decision.status()).isEqualTo(DecisionTypes.DecisionStatus.CHOSEN);
        verifyNoInteractions(fixture.structurer, fixture.search);
    }

    @Test
    void earbudsNeverAutoFillSubjectiveRatings() throws Exception {
        var fixture = new Fixture(DecisionTypes.TemplateKey.EARBUDS);
        new com.decisionassistant.template.DecisionTemplateFactory().apply(fixture.decision);
        when(fixture.web.scrape(fixture.run.url())).thenReturn(new WebEvidenceProvider.Page("Buds", "Price 100000; ANC supported"));
        var optionId = fixture.run.optionId().toString();
        when(fixture.structurer.structure(any())).thenReturn(new AiContracts.AiResult<>(new AiContracts.StructureProposal(
                List.of(), List.of(
                new AiContracts.ValueSuggestion(optionId, "가격", BigDecimal.valueOf(100000), null, "CONFIRMED", "Price"),
                new AiContracts.ValueSuggestion(optionId, "음질", null, 5, "CONFIRMED", "Invented rating")),
                List.of(), List.of(), List.of()), 1, 1));
        fixture.worker().processNext();
        assertThat(fixture.decision.values()).singleElement().satisfies(value -> {
            assertThat(value.numericValue()).isEqualByComparingTo("100000");
            assertThat(value.verificationStatus()).isEqualTo(DecisionTypes.VerificationStatus.UNREVIEWED);
        });
    }

    private static final class Fixture {
        final EvidenceImportRepository imports = mock(EvidenceImportRepository.class);
        final CandidateSuggestionRepository suggestions = mock(CandidateSuggestionRepository.class);
        final DecisionQuestionRepository questions = mock(DecisionQuestionRepository.class);
        final DecisionRepository decisions = mock(DecisionRepository.class);
        final WebEvidenceProvider web = mock(WebEvidenceProvider.class);
        final WebSearchProvider search = mock(WebSearchProvider.class);
        final AiContracts.DecisionStructurer structurer = mock(AiContracts.DecisionStructurer.class);
        final AiCallExecutor executor = mock(AiCallExecutor.class);
        final Decision decision;
        final EvidenceImport run;

        Fixture() throws Exception {
            this(DecisionTypes.TemplateKey.CUSTOM);
        }

        Fixture(DecisionTypes.TemplateKey template) throws Exception {
            decision = new Decision(UUID.randomUUID(), "Comparison", template,
                    new com.decisionassistant.template.DecisionTemplateFactory().version(template), Instant.now());
            decision.startGuidedIntake("Comparison", true, true, "guided-v1", Instant.now());
            var option = decision.addOption(null, "후보 · example.com", null, "https://example.com/pending", null);
            run = new EvidenceImport(decision.id(), option.id(), option.sourceUrl(), Instant.now());
            when(imports.findFirstByStatusOrderByCreatedAtAsc(EvidenceImport.Status.PENDING)).thenReturn(Optional.of(run));
            when(imports.findByDecisionIdOrderByCreatedAtAsc(decision.id())).thenReturn(List.of(run));
            when(decisions.lockById(decision.id())).thenReturn(Optional.of(decision));
            when(executor.call(any())).thenAnswer(invocation -> ((Callable<?>) invocation.getArgument(0)).call());
        }

        @SuppressWarnings("unchecked")
        EvidenceWorkflowWorker worker() {
            var provider = (ObjectProvider<AiContracts.DecisionStructurer>) mock(ObjectProvider.class);
            when(provider.getIfAvailable()).thenReturn(structurer);
            return new EvidenceWorkflowWorker(imports, suggestions, questions, decisions, web,
                    new WebProperties(true, "https://api.example", "key", Duration.ofSeconds(5)),
                    search, provider, executor);
        }
    }
}
