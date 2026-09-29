package com.decisionassistant.guidance;

import com.decisionassistant.template.IntakeTemplates;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static com.decisionassistant.decision.DecisionTypes.TemplateKey.*;
import static com.decisionassistant.guidance.DiscoveryContracts.*;
import static org.assertj.core.api.Assertions.assertThat;

class DiscoveryQueryTest {
    private final IntakeTemplates templates = new IntakeTemplates();
    private final DiscoveryExclusions empty = DiscoveryExclusions.from(List.of(), List.of());

    @Test void multiUseRegressionSearchesAncWithoutNarrowingToUiWords() {
        var condition = conditions(List.of("출퇴근", "운동", "통화", "음악"), "Android", Map.of("anc", "required"));
        assertThat(DiscoveryQuery.build(templates.required(EARBUDS), condition, empty))
                .isEqualTo(OfficialStorePolicy.searchScope(EARBUDS) + " 무선 이어폰 ANC");
        assertThat(condition.selectedUses()).hasSize(4);
        assertThat(condition.maxBudget()).isEqualByComparingTo("200000");
        assertThat(condition.selectedRequirements()).containsEntry("anc", "required");
    }

    @Test void supportHintsAreConditionalAndNumericThresholdsStayOutOfSearch() {
        for (var template : templates.list()) {
            var requirements = new LinkedHashMap<String, String>();
            template.minimumRequirements().forEach(item -> requirements.put(item.id(), item.choices().getFirst().id()));
            var condition = conditions(template.uses(), template.compatibility().getFirst(), requirements);
            var expectedHint = template.key() == EARBUDS ? " ANC" : template.key() == TABLET ? " 전용 펜" : "";
            assertThat(DiscoveryQuery.build(template, condition, empty))
                    .isEqualTo(OfficialStorePolicy.searchScope(template.key()) + " " + template.label() + expectedHint);
            assertThat(DiscoveryQuery.build(template, conditions(template.uses(), null, Map.of()), empty))
                    .isEqualTo(OfficialStorePolicy.searchScope(template.key()) + " " + template.label());
        }
    }

    @Test void refreshKeepsPublicExclusionsButDoesNotSendEditedOptionNames() {
        var excluded = new DiscoveryExclusions(Set.of(), Set.of("개인 입력"), List.of(), List.of("Buds A"));
        assertThat(DiscoveryQuery.build(templates.required(EARBUDS), conditions(List.of("음악"), "iOS", Map.of()), excluded))
                .endsWith(" 무선 이어폰 -\"Buds A\"").doesNotContain("개인 입력");
    }

    private DiscoveryRequest conditions(List<String> uses, String compatibility, Map<String, String> requirements) {
        return new DiscoveryRequest("intake-v2", null, uses, new BigDecimal("200000"), compatibility,
                true, true, true, true, requirements);
    }
}
