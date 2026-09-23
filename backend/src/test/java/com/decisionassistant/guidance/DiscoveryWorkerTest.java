package com.decisionassistant.guidance;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static com.decisionassistant.guidance.DiscoveryContracts.*;
import static org.assertj.core.api.Assertions.*;

class DiscoveryWorkerTest {
    private final SourcePage page = new SourcePage("https://example.com/review", "비교",
            "Buds A 가격 100,000 KRW. Buds B 무게 5 g.", Instant.now());
    private final DiscoveryInput input = new DiscoveryInput("무선 이어폰", List.of("출퇴근"), null, null,
            List.of("가격", "무게"), List.of(page), List.of());

    @Test void rejectsInventedProductsSourcesValuesAndSubjectiveRatings() {
        var real = new ProposedProduct("Buds A", page.url(), "Buds A 가격 100,000 KRW.", "조건과 비교하세요.", List.of(
                new ProposedFact("가격", "100,000 KRW", page.url(), "Buds A 가격 100,000 KRW."),
                new ProposedFact("무게", "3 g", page.url(), "Buds A 가격 100,000 KRW."),
                new ProposedFact("무게", "5 g", page.url(), "Buds B 무게 5 g."),
                new ProposedFact(null, "100,000 KRW", page.url(), "Buds A 가격 100,000 KRW."),
                new ProposedFact("음질", "5/5", page.url(), "Buds A 가격 100,000 KRW."),
                new ProposedFact("무게", "5 g", "https://example.com/invented", "Buds B 무게 5 g.")));
        var invented = new ProposedProduct("Buds C", page.url(), "Buds C", "", List.of());
        var external = new ProposedProduct("Buds A", "http://localhost/", "Buds A", "", List.of());
        var result = DiscoveryWorker.validateProducts(UUID.randomUUID(), new DiscoveryProposal(List.of(real, invented, external, real)), input);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().facts()).hasSize(1);
        assertThat(result.getFirst().facts().getFirst().value()).isEqualTo("100,000 KRW");
        assertThat(result.getFirst().facts().getFirst().verificationStatus()).isEqualTo("UNREVIEWED");
        assertThat(result.getFirst().optionId()).isNull();
    }

    @Test void oneComparisonPageMaySupportDistinctModelsWithoutCreatingOptions() {
        var result = DiscoveryWorker.validateProducts(UUID.randomUUID(), new DiscoveryProposal(List.of(
                new ProposedProduct("Buds A", page.url(), "Buds A", "", List.of()),
                new ProposedProduct("Buds B", page.url(), "Buds B", "", List.of()))), input);
        assertThat(result).extracting(CandidateSuggestion::name).containsExactly("Buds A", "Buds B");
        assertThat(result).allMatch(item -> item.status() == CandidateSuggestion.Status.PROPOSED && item.optionId() == null);
    }

    @Test void conflictingSourceFactsSurviveValidationForUnknownRequirementStatus() {
        var source = new SourcePage(page.url(), "사양", "Laptop A 메모리 8GB. Laptop A 메모리 32GB.", Instant.now());
        var input = new DiscoveryInput("노트북", List.of(), null, null, List.of("메모리"), List.of(source), List.of());
        var proposal = new DiscoveryProposal(List.of(new ProposedProduct("Laptop A", page.url(), "Laptop A", "구성 확인", List.of(
                new ProposedFact("메모리", "8GB", page.url(), "Laptop A 메모리 8GB."),
                new ProposedFact("메모리", "32GB", page.url(), "Laptop A 메모리 32GB.")))));
        var facts = DiscoveryWorker.validateProducts(UUID.randomUUID(), proposal, input).getFirst().facts();
        assertThat(facts).hasSize(2);
        assertThat(MinimumRequirementMatcher.check(com.decisionassistant.template.MinimumRequirements.forCategory(
                com.decisionassistant.decision.DecisionTypes.TemplateKey.LAPTOP), java.util.Map.of("memory", "16"), facts)
                .getFirst().status()).isEqualTo(RequirementStatus.UNKNOWN);
    }
}
