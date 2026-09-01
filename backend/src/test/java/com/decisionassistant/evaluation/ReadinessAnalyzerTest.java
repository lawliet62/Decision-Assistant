package com.decisionassistant.evaluation;

import com.decisionassistant.decision.Decision;
import com.decisionassistant.decision.DecisionTypes;
import com.decisionassistant.template.DecisionTemplateFactory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReadinessAnalyzerTest {
    private final ReadinessAnalyzer analyzer = new ReadinessAnalyzer();

    @Test
    void reportsProvisionalRangesUntilEveryActiveCellIsKnown() {
        var decision = new Decision(UUID.randomUUID(), "휴대폰 비교", DecisionTypes.TemplateKey.PHONE,
                "phone-v1", Instant.now());
        new DecisionTemplateFactory().apply(decision);
        var first = decision.addOption(null, "A", null, null, null);
        decision.addOption(null, "B", null, null, null);
        var price = decision.criteria().stream().filter(item -> item.name().equals("가격")).findFirst().orElseThrow();
        decision.addValue(null, first.id(), price.id(), BigDecimal.valueOf(1_000_000), null,
                DecisionTypes.ValueCertainty.CONFIRMED, null, null);

        var result = analyzer.analyze(decision);

        assertThat(result.readiness()).isEqualTo("PROVISIONAL");
        assertThat(result.missingValues()).isPositive();
        assertThat(result.ranges()).hasSize(2);
        assertThat(result.ranges()).allSatisfy(range -> assertThat(range.upperScore())
                .isGreaterThanOrEqualTo(range.lowerScore()));
    }
}
