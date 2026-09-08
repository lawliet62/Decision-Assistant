package com.decisionassistant.ai;

import com.decisionassistant.decision.DecisionTypes;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StructureInputTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void readsHistoricalSnapshotsWithoutCriteria() {
        var historical = mapper.readValue("""
                {"title":"Comparison","intendedUse":"Travel","notes":null,"options":[]}
                """, AiContracts.StructureInput.class);

        assertThat(historical.title()).isEqualTo("Comparison");
        assertThat(historical.criteria()).isEmpty();
    }

    @Test
    void preservesCriteriaNamesTypesAndUnitsInNewSnapshots() {
        var input = new AiContracts.StructureInput("Comparison", "Travel", null, List.of(),
                List.of(new AiContracts.CriterionInput("가격", DecisionTypes.CriterionKind.NUMERIC_UTILITY, "KRW")));

        var restored = mapper.readValue(mapper.writeValueAsString(input), AiContracts.StructureInput.class);

        assertThat(restored).isEqualTo(input);
    }
}
