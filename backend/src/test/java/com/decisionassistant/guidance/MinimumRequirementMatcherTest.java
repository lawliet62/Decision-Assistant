package com.decisionassistant.guidance;

import com.decisionassistant.decision.Decision;
import com.decisionassistant.decision.DecisionTypes;
import com.decisionassistant.template.DecisionTemplateFactory;
import com.decisionassistant.template.IntakeTemplates;
import com.decisionassistant.template.MinimumRequirements;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.Instant;
import java.util.*;
import java.math.BigDecimal;
import static com.decisionassistant.guidance.DiscoveryContracts.*;
import static com.decisionassistant.decision.DecisionTypes.TemplateKey.*;
import static org.assertj.core.api.Assertions.*;

class MinimumRequirementMatcherTest {
    @Test void maximumAncAttenuationDoesNotMakeExplicitSupportOptional() {
        for (var quote : List.of("ANC 지원, 최대 50dB 소음 감소", "Active Noise Cancellation, up to 50 dB")) {
            var fact = new ProductFact("ANC 지원", "지원", "https://example.com/spec", quote, Instant.now(), "UNREVIEWED");
            assertThat(check(EARBUDS, "anc", "required", fact).status()).isEqualTo(RequirementStatus.MEETS);
        }
        for (var quote : List.of("ANC 지원 여부 미확인, 최대 50dB", "ANC 옵션별 지원, 최대 50dB", "ANC 미지원, 최대 50dB")) {
            var fact = new ProductFact("ANC 지원", "지원", "https://example.com/spec", quote, Instant.now(), "UNREVIEWED");
            assertThat(check(EARBUDS, "anc", "required", fact).status()).isEqualTo(RequirementStatus.UNKNOWN);
        }
    }
    @Test void recognizesOnlyTheExplicitJapaneseAncSupportQuote() {
        var quote = "アクティブノイズキャンセリング搭載";
        var fact = new ProductFact("ANC 지원", "지원", "https://www.ankerjapan.com/products/d1202", quote, Instant.now(), "UNREVIEWED");
        assertThat(check(EARBUDS, "anc", "required", fact).status()).isEqualTo(RequirementStatus.MEETS);
        var negative = new ProductFact("ANC 지원", "지원", fact.sourceUrl(), "アクティブノイズキャンセリング非搭載", Instant.now(), "UNREVIEWED");
        assertThat(check(EARBUDS, "anc", "required", negative).status()).isEqualTo(RequirementStatus.UNKNOWN);
        var mixed = new ProductFact("ANC 지원", "지원", fact.sourceUrl(), quote + " 非対応", Instant.now(), "UNREVIEWED");
        assertThat(check(EARBUDS, "anc", "required", mixed).status()).isEqualTo(RequirementStatus.UNKNOWN);
        var negated = new ProductFact("ANC 지원", "지원", fact.sourceUrl(), quote + "しない", Instant.now(), "UNREVIEWED");
        assertThat(check(EARBUDS, "anc", "required", negated).status()).isEqualTo(RequirementStatus.UNKNOWN);
    }
    @Test void recognizesOnlyTheExactAdaptiveAncQuote() {
        var quote = AnkerP41iVerifier.QUOTE;
        var fact = new ProductFact("ANC 지원", "지원", AnkerP41iVerifier.SPEC_URL, quote, Instant.now(), "UNREVIEWED");
        assertThat(check(EARBUDS, "anc", "required", fact).status()).isEqualTo(RequirementStatus.MEETS);
        var negated = new ProductFact("ANC 지원", "지원", fact.sourceUrl(), quote + "しない", Instant.now(), "UNREVIEWED");
        assertThat(check(EARBUDS, "anc", "required", negated).status()).isEqualTo(RequirementStatus.UNKNOWN);
    }
    @Test void recognizesOnlyTheExactLibertyProAncQuote() {
        var quote = AnkerLibertyProSpecificationVerifier.ANC_QUOTE;
        var fact = new ProductFact("ANC 지원", "지원", AnkerLibertyProSpecificationVerifier.URL, quote, Instant.now(), "UNREVIEWED");
        assertThat(check(EARBUDS, "anc", "required", fact).status()).isEqualTo(RequirementStatus.MEETS);
        var negated = new ProductFact("ANC 지원", "지원", fact.sourceUrl(), quote + "しない", Instant.now(), "UNREVIEWED");
        assertThat(check(EARBUDS, "anc", "required", negated).status()).isEqualTo(RequirementStatus.UNKNOWN);
    }
    @ParameterizedTest
    @CsvSource({"적응형 ANC 탑재,MEETS", "액티브 노이즈 캔슬링 지원,MEETS", "ANC 미지원,BELOW", "통화 소음 억제,UNKNOWN", "ANC 지원 여부 미확인,UNKNOWN"})
    void recognizesExplicitFeaturePhrasesWithoutInventingSupport(String value, RequirementStatus expected) {
        var fact = new ProductFact("ANC 지원", value, "https://example.com/product", value, Instant.now(), "UNREVIEWED");
        assertThat(check(EARBUDS, "anc", "required", fact).status()).isEqualTo(expected);
    }
    private ProductFact fact(String name, String value) {
        return new ProductFact(name, value, "https://example.com/spec", "Model A " + name + " " + value,
                Instant.parse("2026-09-24T00:00:00Z"), "UNREVIEWED");
    }

    @ParameterizedTest
    @CsvSource({"16 GB,MEETS", "15.9 GB,BELOW", "32GB,MEETS", "16/32GB,UNKNOWN", "16 Gb,UNKNOWN", "16,UNKNOWN", "0GB,UNKNOWN", "-16GB,UNKNOWN"})
    void memoryUsesOnlyUnambiguousCapacity(String actual, RequirementStatus expected) {
        assertThat(check(LAPTOP, "memory", "16", fact("메모리", actual)).status()).isEqualTo(expected);
    }

    @Test void storageConvertsTerabytesAndComparesEquivalentValues() {
        var checks = MinimumRequirementMatcher.check(MinimumRequirements.forCategory(LAPTOP), Map.of("storage", "1000"),
                List.of(fact("저장공간", "1 TB"), fact("저장공간", "1,000 GB")));
        assertThat(checks.getFirst().status()).isEqualTo(RequirementStatus.MEETS);
        assertThat(check(LAPTOP, "storage", "1000", fact("저장공간", "999 GB")).status()).isEqualTo(RequirementStatus.BELOW);
    }

    @ParameterizedTest
    @CsvSource({"144Hz,MEETS", "143.9 Hz,BELOW", "60~165 Hz,UNKNOWN", "최대 165Hz,UNKNOWN", "165,UNKNOWN"})
    void refreshRateUsesSingleValue(String actual, RequirementStatus expected) {
        assertThat(check(MONITOR, "refreshRate", "144", fact("주사율", actual)).status()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"QHD,MEETS", "2560×1440,MEETS", "3440 x 1440,MEETS", "3840x1080,BELOW", "FHD,BELOW", "4K UHD,MEETS", "2K,UNKNOWN", "4K,UNKNOWN", "1920x1080/3840x2160,UNKNOWN"})
    void resolutionComparesBothDimensions(String actual, RequirementStatus expected) {
        assertThat(check(MONITOR, "resolution", "qhd", fact("해상도", actual)).status()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"ANC 지원,MEETS", "지원,MEETS", "ANC 미지원,BELOW", "없음,BELOW", "좋은 노이즈 캔슬링,UNKNOWN", "지원 여부 미확인,UNKNOWN"})
    void ancRequiresExplicitSupport(String actual, RequirementStatus expected) {
        assertThat(check(EARBUDS, "anc", "required", fact("ANC 지원", actual)).status()).isEqualTo(expected);
    }

    @Test void missingConflictingDisputedAndAmbiguousFactsStayUnknown() {
        assertThat(MinimumRequirementMatcher.check(MinimumRequirements.forCategory(LAPTOP), Map.of("memory", "16"), List.of())
                .getFirst().status()).isEqualTo(RequirementStatus.UNKNOWN);
        var conflicting = List.of(fact("메모리", "8GB"), fact("메모리", "32GB"));
        assertThat(MinimumRequirementMatcher.check(MinimumRequirements.forCategory(LAPTOP), Map.of("memory", "16"), conflicting)
                .getFirst().status()).isEqualTo(RequirementStatus.UNKNOWN);
        for (var quote : List.of("Model A 최대 32GB까지 확장 가능", "Model A 16GB 또는 32GB", "Model A 16GB/32GB")) {
            var value = new ProductFact("메모리", "32GB", "https://example.com/spec", quote, Instant.now(), "UNREVIEWED");
            assertThat(check(LAPTOP, "memory", "16", value).status()).isEqualTo(RequirementStatus.UNKNOWN);
        }
        var disputed = new ProductFact("메모리", "32GB", null, null, null, "DISPUTED");
        assertThat(check(LAPTOP, "memory", "16", disputed).status()).isEqualTo(RequirementStatus.UNKNOWN);
        var falsePositive = new ProductFact("ANC 지원", "지원", null, "Model A ANC 미지원", null, "UNREVIEWED");
        assertThat(check(EARBUDS, "anc", "required", falsePositive).status()).isEqualTo(RequirementStatus.UNKNOWN);
    }

    @Test void penCompatibilityDoesNotMeanIncludedOrGenericCapacitiveStylus() {
        assertThat(check(TABLET, "pen", "required", fact("펜 지원", "전용 펜 지원")).status()).isEqualTo(RequirementStatus.MEETS);
        for (var value : List.of("펜 별매", "기본 포함", "정전식 펜 지원", "능동형 펜 미확인")) {
            assertThat(check(TABLET, "pen", "required", fact("펜 지원", value)).status()).isEqualTo(RequirementStatus.UNKNOWN);
        }
        var capacitive = new ProductFact("펜 지원", "지원", null, "Tablet A 정전식 펜 지원", null, "UNREVIEWED");
        assertThat(check(TABLET, "pen", "required", capacitive).status()).isEqualTo(RequirementStatus.UNKNOWN);
        var unrelated = new ProductFact("ANC 지원", "지원", null, "Buds A 통화 소음 억제 지원", null, "UNREVIEWED");
        assertThat(check(EARBUDS, "anc", "required", unrelated).status()).isEqualTo(RequirementStatus.UNKNOWN);
    }

    @Test void eachSelectedRequirementRetainsItsOwnStatusAndSources() {
        var checks = MinimumRequirementMatcher.check(MinimumRequirements.forCategory(TABLET),
                Map.of("storage", "256", "refreshRate", "120", "pen", "required"),
                List.of(fact("저장공간", "128GB"), fact("펜 지원", "전용 펜 지원")));
        assertThat(checks).extracting(RequirementCheck::status)
                .containsExactly(RequirementStatus.BELOW, RequirementStatus.UNKNOWN, RequirementStatus.MEETS);
        assertThat(checks.getFirst().facts().getFirst().sourceUrl()).isEqualTo("https://example.com/spec");
        assertThat(MinimumRequirementMatcher.check(MinimumRequirements.forCategory(LAPTOP), Map.of(), List.of())).isEmpty();
    }

    @Test void confirmedValueOverridesExtractedValueForBothDisplayAndMatchingWithoutMutatingDraft() {
        var decision = new Decision(UUID.randomUUID(), "노트북", LAPTOP, "laptop-v2", Instant.now());
        new DecisionTemplateFactory().apply(decision);
        var option = decision.addOption(null, "Laptop A", null, null, null);
        var storage = decision.criteria().stream().filter(item -> item.name().equals("스토리지")).findFirst().orElseThrow();
        var value = decision.addValue(null, option.id(), storage.id(), BigDecimal.valueOf(1024), null,
                DecisionTypes.ValueCertainty.CONFIRMED, null, "확장 후 직접 확인");
        var extracted = List.of(fact("저장공간", "256GB"));
        var resolved = ProductFactResolver.resolve(decision, option.id(), extracted, null);
        assertThat(resolved).hasSize(1);
        assertThat(resolved.getFirst().value()).isEqualTo("1024 GB");
        assertThat(check(LAPTOP, "storage", "1000", resolved.getFirst()).status()).isEqualTo(RequirementStatus.MEETS);
        assertThat(decision.values()).hasSize(1);
        assertThat(decision.constraints()).noneMatch(item -> item.active());
        value.markGenerated(DecisionTypes.ValueOrigin.AI_INFERRED, DecisionTypes.VerificationStatus.UNREVIEWED);
        assertThat(ProductFactResolver.resolve(decision, option.id(), extracted, null)).isEqualTo(extracted);
        value.markGenerated(DecisionTypes.ValueOrigin.USER, DecisionTypes.VerificationStatus.DISPUTED);
        var disputed = ProductFactResolver.resolve(decision, option.id(), extracted, null);
        assertThat(disputed.getFirst().verificationStatus()).isEqualTo("DISPUTED");
        assertThat(check(LAPTOP, "storage", "1000", disputed.getFirst()).status()).isEqualTo(RequirementStatus.UNKNOWN);
    }

    @Test void oldTemplateRetainsOriginalFactsAndHasNoMinimumRequirements() {
        var templates = new IntakeTemplates();
        for (var key : List.of(LAPTOP, PHONE, TABLET, MONITOR, EARBUDS)) {
            assertThat(templates.required(key).minimumRequirements()).isNotEmpty();
            assertThat(templates.required(key, "intake-v1").minimumRequirements()).isEmpty();
        }
        assertThat(templates.required(PHONE, "intake-v1").facts()).doesNotContain("주사율");
        assertThat(templates.required(TABLET).facts()).contains("주사율");
    }

    private RequirementCheck check(DecisionTypes.TemplateKey key, String id, String choice, ProductFact fact) {
        return MinimumRequirementMatcher.check(MinimumRequirements.forCategory(key), Map.of(id, choice), List.of(fact)).getFirst();
    }
}
