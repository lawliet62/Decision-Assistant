package com.decisionassistant.decision;

import com.decisionassistant.common.InvalidDecisionException;
import com.decisionassistant.guidance.*;
import com.decisionassistant.template.MinimumRequirements;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static com.decisionassistant.guidance.DiscoveryContracts.*;

/** 같은 후보의 비교 조건과 사용자가 직접 확인한 근거. 추천 실행의 조건과 분리한다. */
public final class Comparison {
    private Comparison() {}

    public record Settings(@DecimalMin("1") @DecimalMax("100000000") BigDecimal maxBudget,
                           boolean ancRequired, @Size(max = 40) String compatibility,
                           @NotBlank String focus, @NotNull @Size(max = 5) List<@NotNull UUID> deferredOptionIds) {}
    public record Confirmation(UUID optionId, String feature, boolean supported, String note,
                               String sourceUrl, Instant confirmedAt) {}
    public record State(Settings settings, List<Confirmation> confirmations) {}
    public record UpdateRequest(long expectedVersion, @NotNull @Valid Settings settings) {}
    public record ConfirmRequest(long expectedVersion, @NotNull UUID optionId, @NotBlank String feature,
                                 Boolean supported, @Size(max = 1000) String note, @Size(max = 2048) String sourceUrl) {}
    public record OptionComparison(UUID optionId, BigDecimal price, String priceOrigin,
                                   List<RequirementCheck> checks) {}
    public record Data(Settings settings, List<Confirmation> confirmations, List<OptionComparison> options) {}
    public record Snapshot(String schemaVersion, Instant capturedAt, DecisionDtos.ContextData context,
                           List<DecisionDtos.OptionData> options, List<DecisionDtos.CriterionData> criteria,
                           List<DecisionDtos.OptionValueData> values, List<DecisionDtos.ConstraintData> constraints,
                           List<DecisionDtos.ConstraintResultData> constraintResults,
                           List<DecisionDtos.ProductFactsData> productFacts, Data comparison, String title) {
        public static Snapshot capture(DecisionDtos.DecisionResponse response, Instant now) {
            return new Snapshot("comparison-v2", now, response.context(), response.options(), response.criteria(),
                    response.values(), response.constraints(), response.constraintResults(), response.productFacts(), response.comparison(), response.title());
        }
    }
    public static State initial(Decision decision, DiscoveryRequest request) {
        return new State(new Settings(request == null ? decision.context().budget() : request.maxBudget(),
                request != null && request.selectedRequirements().containsKey("anc"),
                request == null ? null : request.compatibility(), "가격", List.of()), List.of());
    }
    public static void validate(Decision decision, Settings settings) {
        if (decision.templateKey() != DecisionTypes.TemplateKey.EARBUDS)
            throw new InvalidDecisionException(List.of("조건 비교는 현재 무선 이어폰에서 제공합니다."));
        if (settings.compatibility() != null && !List.of("Android", "iOS", "멀티포인트").contains(settings.compatibility())
                || !List.of("가격", "ANC 지원", "호환성", "배터리", "무게").contains(settings.focus()))
            throw new InvalidDecisionException(List.of("지원하지 않는 비교 조건입니다."));
        var ids = decision.options().stream().map(DecisionOption::id).toList();
        if (!ids.containsAll(settings.deferredOptionIds()) || new HashSet<>(settings.deferredOptionIds()).size() != settings.deferredOptionIds().size())
            throw new InvalidDecisionException(List.of("보류할 후보는 현재 비교의 후보여야 합니다."));
    }
    public static Data calculate(State state, List<DecisionDtos.ProductFactsData> products, Instant now) {
        var settings = state.settings();
        return new Data(settings, state.confirmations(), products.stream().map(product -> {
            var checks = new ArrayList<RequirementCheck>();
            if (settings.ancRequired()) {
                var check = MinimumRequirementMatcher.check(MinimumRequirements.forCategory(DecisionTypes.TemplateKey.EARBUDS),
                        Map.of("anc", "required"), product.facts()).getFirst();
                checks.add(override(state, product.optionId(), "anc", check));
            }
            if (settings.compatibility() != null) {
                // 직접 추가한 후보도 정보가 없으면 UNKNOWN으로 표시한다.
                var check = CompatibilityMatcher.check(DecisionTypes.TemplateKey.EARBUDS, settings.compatibility(),
                        product.facts(), product.officialOffer(), true);
                checks.add(override(state, product.optionId(), settings.compatibility(), check));
            }
            var priceFact = product.facts().stream().filter(f -> "가격".equals(f.name()) && "CONFIRMED".equals(f.verificationStatus())).findFirst().orElse(null);
            BigDecimal price = null;
            if (priceFact != null && priceFact.value() != null) {
                try { price = new BigDecimal(priceFact.value().replaceAll("[,\\s]", "").replaceFirst("(?:KRW|원)$", "")); }
                catch (NumberFormatException ignored) { /* 근거를 수치로 읽을 수 없으면 미확인 */ }
            }
            var origin = price == null ? null : "사용자 확인";
            var offer = product.officialOffer();
            var disputedPrice = product.facts().stream().anyMatch(f -> "가격".equals(f.name()) && "DISPUTED".equals(f.verificationStatus()));
            if (priceFact == null && !disputedPrice && offer != null && "KRW".equals(offer.currency()) && CatalogDiscovery.fresh(offer, now)) {
                price = offer.price(); origin = "수집 가격";
            }
            if (price != null && price.signum() <= 0) { price = null; origin = null; }
            if (price == null) origin = null;
            if (settings.maxBudget() != null) {
                var evidence = priceFact == null ? List.<ProductFact>of() : List.of(priceFact);
                if (price != null && priceFact == null) evidence = List.of(new ProductFact("가격", price + " KRW", offer.purchaseUrl(), "수집 가격", offer.fetchedAt(), "UNREVIEWED"));
                checks.add(new RequirementCheck("budget", "최대 예산", settings.maxBudget().toPlainString() + "원",
                        price == null ? RequirementStatus.UNKNOWN : price.compareTo(settings.maxBudget()) <= 0 ? RequirementStatus.MEETS : RequirementStatus.BELOW,
                        evidence, price == null ? UnknownReason.MISSING : null));
            }
            return new OptionComparison(product.optionId(), price, origin, List.copyOf(checks));
        }).toList());
    }
    private static RequirementCheck override(State state, UUID optionId, String feature, RequirementCheck original) {
        var confirmed = state.confirmations().stream().filter(c -> c.optionId().equals(optionId) && c.feature().equals(feature)).findFirst().orElse(null);
        if (confirmed == null) return original;
        var fact = new ProductFact("anc".equals(feature) ? "ANC 지원" : "호환성", confirmed.supported() ? "지원" : "미지원",
                confirmed.sourceUrl(), "사용자 직접 확인: " + feature + " · " + Objects.toString(confirmed.note(), ""), confirmed.confirmedAt(), "CONFIRMED");
        return new RequirementCheck(original.id(), original.label(), original.requiredValue(),
                confirmed.supported() ? RequirementStatus.MEETS : RequirementStatus.BELOW, List.of(fact), null);
    }
}
