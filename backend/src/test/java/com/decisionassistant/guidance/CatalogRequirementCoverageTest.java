package com.decisionassistant.guidance;

import com.decisionassistant.template.MinimumRequirements;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import static com.decisionassistant.decision.DecisionTypes.TemplateKey.EARBUDS;
import static com.decisionassistant.guidance.DiscoveryContracts.*;
import static org.assertj.core.api.Assertions.*;

class CatalogRequirementCoverageTest {
    @Test void registeredEvidenceReachesRequirementChecksWithoutAiOrPrice() throws Exception {
        try (var stream = new ClassPathResource("catalog/earbuds.json").getInputStream()) {
            var entries = new ObjectMapper().readValue(stream, CatalogImporter.Entry[].class);
            int active = 0, anc = 0, compatibility = 0, multipoint = 0;
            for (var entry : entries) {
                if (!entry.active()) continue;
                active++;
                var product = new CatalogProduct(entry.id());
                product.register(entry.revision(), entry.name(), entry.purchaseUrl(), entry.expectedIdentity(), entry.facts(), true);
                // 등록 시각 직후로 고정하여 만료와 문구 해석을 분리한다.
                var at = entry.facts().stream().map(f -> f.evidence().fetchedAt()).max(Instant::compareTo).orElseThrow();
                var facts = product.usableFacts(at);
                var check = MinimumRequirementMatcher.check(MinimumRequirements.forCategory(EARBUDS), Map.of("anc", "required"), facts).getFirst();
                if (facts.stream().anyMatch(f -> f.name().equals("ANC 지원"))) {
                    assertThat(check.status()).as(entry.id()).isEqualTo(RequirementStatus.MEETS);
                    anc++;
                } else assertThat(check.unknownReason()).as(entry.id()).isEqualTo(UnknownReason.MISSING);
                for (var platform : List.of("Android", "iOS")) {
                    var result = CompatibilityMatcher.check(EARBUDS, platform, facts, null, true);
                    if (facts.stream().anyMatch(f -> f.name().equals("호환성") && f.value().contains(platform))) {
                        assertThat(result.status()).as(entry.id() + " " + platform).isEqualTo(RequirementStatus.MEETS);
                        compatibility++;
                    } else assertThat(result.status()).as(entry.id()).isEqualTo(RequirementStatus.UNKNOWN);
                }
                if (CompatibilityMatcher.check(EARBUDS, "멀티포인트", facts, null, true).status() == RequirementStatus.MEETS) multipoint++;
            }
            assertThat(active).isEqualTo(23);
            assertThat(anc).isEqualTo(16);
            assertThat(compatibility).isEqualTo(2);
            assertThat(multipoint).isEqualTo(1);
        }
    }
}
