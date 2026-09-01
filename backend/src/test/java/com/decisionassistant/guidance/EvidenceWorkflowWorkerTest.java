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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvidenceWorkflowWorkerTest {

    @Test
    @SuppressWarnings("unchecked")
    void importsPublicEvidenceAndProposesButDoesNotAutoAcceptAnAlternative() {
        var imports = mock(EvidenceImportRepository.class);
        var suggestions = mock(CandidateSuggestionRepository.class);
        var questions = mock(DecisionQuestionRepository.class);
        var decisions = mock(DecisionRepository.class);
        var web = mock(WebEvidenceProvider.class);
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
        when(decisions.findById(decision.id())).thenReturn(Optional.of(decision));
        when(web.scrape(run.url())).thenReturn(new WebEvidenceProvider.Page("Example Laptop", "# Example Laptop\n16GB RAM"));
        when(web.search(any(), any(Integer.class))).thenReturn(List.of(
                new WebEvidenceProvider.SearchResult("Alternative", "https://example.org/b", "더 가벼운 대안")));
        when(suggestions.findByDecisionIdOrderByCreatedAtAsc(decision.id())).thenReturn(List.of());
        when(structurer.getIfAvailable()).thenReturn(null);

        new EvidenceWorkflowWorker(imports, suggestions, questions, decisions, web, webProperties,
                structurer, aiExecutor).processNext();

        assertThat(run.status()).isEqualTo(EvidenceImport.Status.SUCCEEDED);
        assertThat(decision.options().getFirst().name()).isEqualTo("Example Laptop");
        assertThat(decision.processingStatus()).isEqualTo(DecisionTypes.ProcessingStatus.NEEDS_INPUT);
        verify(suggestions).save(any(CandidateSuggestion.class));
        assertThat(decision.options()).hasSize(1);
    }
}
