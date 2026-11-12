package com.decisionassistant.decision;

import com.decisionassistant.guidance.DiscoveryContracts.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class ComparisonTest {
    private final Instant now = Instant.parse("2026-11-05T00:00:00Z");
    private final UUID option = UUID.randomUUID();
    private Comparison.State state(List<Comparison.Confirmation> confirmations) {
        return new Comparison.State(new Comparison.Settings(new BigDecimal("100000"), true, "iOS", "가격", List.of()), confirmations);
    }
    private DecisionDtos.ProductFactsData product(List<ProductFact> facts, Instant priceTime) {
        return new DecisionDtos.ProductFactsData(option, facts, now, null, List.of(),
                new OfficialOffer("https://example.com/product", "시험 판매처", new BigDecimal("90000"), "KRW", null,
                        priceTime, "OBSERVED_KRW_PRICE", "catalog-v1"), null, null);
    }
    private ProductFact fact(String name, String value, String quote, String verification) {
        return new ProductFact(name, value, "https://example.com/manual", quote, now, verification);
    }
    @Test
    void missingConflictingAndAppOnlyFactsRemainUnknown() {
        var facts = List.of(fact("ANC 지원", "지원", "ANC 지원", "UNREVIEWED"),
                fact("ANC 지원", "미지원", "ANC 미지원", "UNREVIEWED"),
                fact("호환성", "iOS", "iOS 앱 지원", "UNREVIEWED"));
        var result = Comparison.calculate(state(List.of()), List.of(product(facts, now.minusSeconds(72 * 3600))), now).options().getFirst();
        assertThat(result.price()).isNull();
        assertThat(result.checks()).allMatch(check -> check.status() == RequirementStatus.UNKNOWN);
        assertThat(result.checks().getFirst().unknownReason()).isEqualTo(UnknownReason.CONFLICT);
    }
    @Test
    void directConfirmationOverridesOnlyItsFeatureAndPriceUsesConfirmedInput() {
        var facts = List.of(fact("ANC 지원", "미지원", "ANC 미지원", "UNREVIEWED"),
                fact("가격", "120000 KRW", "사용자 확인 가격", "CONFIRMED"));
        var state = state(List.of(new Comparison.Confirmation(option, "anc", true, "설명서", null, now),
                new Comparison.Confirmation(option, "Android", true, "직접 연결", null, now)));
        var result = Comparison.calculate(state, List.of(product(facts, now)), now).options().getFirst();
        assertThat(result.price()).isEqualByComparingTo("120000");
        assertThat(result.checks()).extracting(RequirementCheck::status)
                .containsExactly(RequirementStatus.MEETS, RequirementStatus.UNKNOWN, RequirementStatus.BELOW);
        assertThat(result.checks().getFirst().facts().getFirst().quote()).startsWith("사용자 직접 확인:");
        assertThat(facts.getFirst().value()).isEqualTo("미지원");
        var refreshed = Comparison.calculate(state, List.of(product(List.of(), now)), now);
        assertThat(refreshed.options().getFirst().checks().getFirst().status()).isEqualTo(RequirementStatus.MEETS);
    }
    @Test
    void aConditionChangeReusesSameEvidenceWithoutFillingUnknowns() {
        var product = product(List.of(fact("ANC 지원", "미지원", "ANC 미지원", "UNREVIEWED")), now);
        var original = Comparison.calculate(state(List.of()), List.of(product), now);
        var relaxed = new Comparison.State(new Comparison.Settings(null, false, null, "배터리", List.of(option)), List.of());
        assertThat(Comparison.calculate(relaxed, List.of(product), now).options().getFirst().checks()).isEmpty();
        assertThat(original.options().getFirst().checks().getFirst().status()).isEqualTo(RequirementStatus.BELOW);
        assertThat(Comparison.calculate(state(List.of()), List.of(product), now)).isEqualTo(original);
    }

    @Test
    void disputedOrUnreadableConfirmedPriceDoesNotFallBackToCollectedPrice() {
        for (var fact : List.of(fact("가격", "90000 KRW", "가격 이견", "DISPUTED"),
                fact("가격", null, "수치 없음", "CONFIRMED"))) {
            var result = Comparison.calculate(state(List.of()), List.of(product(List.of(fact), now)), now).options().getFirst();
            assertThat(result.price()).isNull();
            assertThat(result.checks().getLast().status()).isEqualTo(RequirementStatus.UNKNOWN);
        }
    }
}
