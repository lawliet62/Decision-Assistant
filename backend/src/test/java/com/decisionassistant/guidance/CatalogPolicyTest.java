package com.decisionassistant.guidance;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.decisionassistant.guidance.DiscoveryContracts.*;

class CatalogPolicyTest {
    private final Instant checked = Instant.parse("2026-10-06T01:00:00Z");
    private final String url = "https://qcystore.co.kr/product/detail.html?product_no=633";
    private ProductFact fact(String name, String value, String quote) { return new ProductFact(name, value, "https://www.qcy.com/products/qcy-t13-anc", quote, checked, "UNREVIEWED"); }
    private OfficialOffer offer(Instant time) { return new OfficialOffer(url, "QCY", BigDecimal.valueOf(30000), "KRW", null, time, "판매가", CatalogProperties.VERSION); }
    private CatalogProduct product() {
        var p = new CatalogProduct("qcy-test");
        p.register(1, "QCY T13 ANC", url, "qcy:test", List.of(
                new CatalogProduct.Fact(fact("ANC 지원", "지원", "Active Noise Cancellation (ANC)"), CatalogProduct.ReviewCycle.HARDWARE),
                new CatalogProduct.Fact(fact("호환성", "iOS/Android", "Compatible system: iOS/Android"), CatalogProduct.ReviewCycle.SOFTWARE)), true);
        return p;
    }
    @Test void priceExpiresAtBoundaryAndNeverBecomesFreshThroughReading() {
        assertThat(CatalogDiscovery.fresh(offer(checked), checked.plusSeconds(86399))).isTrue();
        assertThat(CatalogDiscovery.fresh(offer(checked), checked.plusSeconds(86400))).isFalse();
        assertThat(CatalogDiscovery.fresh(offer(checked), checked.minusSeconds(1))).isFalse();
        assertThat(CatalogDiscovery.fresh(null, checked)).isFalse();
    }
    @Test void hardwareRemainsUsableWhileOverdueSoftwareIsWithheld() {
        var p = product();
        assertThat(p.usableFacts(checked.plus(Duration.ofDays(29)))).hasSize(2);
        assertThat(p.usableFacts(checked.plus(Duration.ofDays(30)))).extracting(ProductFact::name).containsExactly("ANC 지원");
        assertThat(p.usableFacts(checked.plus(Duration.ofDays(100)))).hasSize(1);
        assertThat(p.facts().getFirst().reviewDueAt()).isEqualTo(checked.plus(Duration.ofDays(90)));
    }
    @Test void verifiedReviewExtendsOnlyItsFactAndIsDiscardedWhenRegistrationChanges() {
        var p = product();
        var review = checked.plus(Duration.ofDays(29));
        p.reviewedFact(1, "호환성", review);
        assertThat(p.usableFacts(checked.plus(Duration.ofDays(31)))).extracting(ProductFact::name)
                .containsExactly("ANC 지원", "호환성");
        assertThat(p.usableFacts(checked.plus(Duration.ofDays(31))).get(1).fetchedAt()).isEqualTo(review);
        assertThatThrownBy(() -> p.reviewedFact(2, "호환성", review)).isInstanceOf(IllegalArgumentException.class);
        p.register(1, p.name(), url, "qcy:test", p.facts(), true);
        assertThat(p.usableFacts(checked.plus(Duration.ofDays(31)))).hasSize(2);
        p.register(2, p.name(), url, "qcy:test", p.facts(), true);
        assertThat(p.usableFacts(checked.plus(Duration.ofDays(31)))).hasSize(1);
    }
    @Test void catalogRevisionCannotSilentlyHideChangedRegistration() {
        var p = product();
        assertThatThrownBy(() -> p.register(1, "Changed", url, "qcy:test", p.facts(), true)).isInstanceOf(IllegalArgumentException.class);
        p.register(2, "Changed", url, "qcy:test", p.facts(), true);
        p.register(1, "Old", url, "qcy:test", List.of(), false);
        assertThat(p.name()).isEqualTo("Changed");
    }
    @Test void failedFetchDoesNotRenewPriceAndIdentityChangeInvalidatesIt() {
        var p = product();
        p.observed(new OfficialProduct("QCY T13 ANC", "qcy:test", offer(checked), ""));
        p.attempted(checked.plusSeconds(10)); p.rejected("COLLECTION_FAILED");
        assertThat(p.usableOffer().fetchedAt()).isEqualTo(checked);
        p.rejected("UNAVAILABLE"); assertThat(p.usableOffer()).isNull();
        p.observed(new OfficialProduct("QCY T13 ANC", "qcy:test", offer(checked), ""));
        p.register(2, "Other", url, "qcy:other", List.of(), true);
        assertThat(p.usableOffer()).isNull();
    }
    @Test void seedFactsAreExplicitAndDoNotPretendUnsupportedAliasesAreVerified() throws Exception {
        try (var in = getClass().getResourceAsStream("/catalog/earbuds.json")) {
            var entries = new tools.jackson.databind.ObjectMapper().readValue(in, CatalogImporter.Entry[].class);
            assertThat(entries).hasSize(9);
            assertThat(Arrays.stream(entries).filter(CatalogImporter.Entry::active)).hasSize(6);
            assertThat(entries[0].active()).isFalse();
            for (var entry : entries) CatalogImporter.validate(entry, Instant.parse("2030-01-01T00:00:00Z"));
            var template = new com.decisionassistant.template.IntakeTemplates().required(com.decisionassistant.decision.DecisionTypes.TemplateKey.EARBUDS);
            for (int index : List.of(0, 3)) {
                var facts = entries[index].facts().stream().map(CatalogProduct.Fact::evidence).toList();
                assertThat(MinimumRequirementMatcher.check(template.minimumRequirements(), Map.of("anc", "required"), facts).getFirst().status()).isEqualTo(RequirementStatus.MEETS);
                assertThat(CompatibilityMatcher.check(template.key(), "Android", facts, null, true).status()).isEqualTo(RequirementStatus.MEETS);
            }
            assertThat(entries[1].facts()).extracting(f -> f.evidence().value()).containsExactly("지원");
            assertThat(entries[4].facts()).isEmpty();
            assertThat(MinimumRequirementMatcher.check(template.minimumRequirements(), Map.of("anc", "required"),
                    entries[1].facts().stream().map(CatalogProduct.Fact::evidence).toList()).getFirst().status()).isEqualTo(RequirementStatus.MEETS);
            assertThat(entries[2].facts()).extracting(f -> f.evidence().value()).containsExactly("지원", "IPX5");
            assertThat(MinimumRequirementMatcher.check(template.minimumRequirements(), Map.of("anc", "required"),
                    entries[2].facts().stream().map(CatalogProduct.Fact::evidence).toList()).getFirst().status()).isEqualTo(RequirementStatus.MEETS);
        }
    }
}
