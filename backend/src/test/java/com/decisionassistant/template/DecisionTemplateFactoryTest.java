package com.decisionassistant.template;

import com.decisionassistant.decision.Decision;
import com.decisionassistant.decision.DecisionTypes;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class DecisionTemplateFactoryTest {
    @Test void earbudsSeedUsesApprovedDefaultsWithoutChangingEvaluator() {
        var factory = new DecisionTemplateFactory();
        var decision = new Decision(UUID.randomUUID(), "Buds", DecisionTypes.TemplateKey.EARBUDS,
                factory.version(DecisionTypes.TemplateKey.EARBUDS), Instant.now());
        factory.apply(decision);
        assertThat(decision.templateVersion()).isEqualTo("earbuds-v1");
        assertThat(decision.criteria()).extracting(item -> item.name())
                .containsExactly("가격", "음질", "착용감", "노이즈 캔슬링", "배터리 적합도");
        assertThat(decision.criteria()).allSatisfy(item -> assertThat(item.weight()).isEqualByComparingTo(BigDecimal.ONE));
        assertThat(decision.criteria().subList(1, 5)).allSatisfy(item -> {
            assertThat(item.kind()).isEqualTo(DecisionTypes.CriterionKind.RATING_1_TO_5);
            assertThat(item.worstAnchor()).isNull();
        });
        assertThat(decision.constraints()).hasSize(2).allSatisfy(item -> assertThat(item.active()).isFalse());
    }
}
