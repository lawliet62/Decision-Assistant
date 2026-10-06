package com.decisionassistant.guidance;

import java.math.BigDecimal;
import java.util.*;
import static com.decisionassistant.guidance.DiscoveryContracts.*;

public final class DiscoveryRanking {
    private DiscoveryRanking() {}
    public enum Group { RECOMMENDED, UNKNOWN, BELOW, LEGACY }
    public static Group group(OfficialOffer offer, List<RequirementCheck> checks, BigDecimal budget) {
        return group(offer, checks, budget, null);
    }
    public static Group group(OfficialOffer offer, List<RequirementCheck> minimums, BigDecimal budget, RequirementCheck compatibility) {
        var checks = new ArrayList<>(minimums);
        if (compatibility != null) checks.add(compatibility);
        if (offer == null) return Group.LEGACY;
        if (checks.stream().anyMatch(item -> item.status() == RequirementStatus.BELOW)) return Group.BELOW;
        if (CatalogProperties.VERSION.equals(offer.policyVersion()) && !CatalogDiscovery.fresh(offer, java.time.Instant.now())) return Group.UNKNOWN;
        if (budget != null && offer.price().compareTo(budget) > 0 || checks.stream().anyMatch(item -> item.status() == RequirementStatus.BELOW)) return Group.BELOW;
        if (checks.stream().anyMatch(item -> item.status() == RequirementStatus.UNKNOWN)) return Group.UNKNOWN;
        return Group.RECOMMENDED;
    }
    public static Group catalogGroup(OfficialOffer offer, List<RequirementCheck> checks, BigDecimal budget, RequirementCheck compatibility) {
        if (checks.stream().anyMatch(item -> item.status() == RequirementStatus.BELOW)
                || compatibility != null && compatibility.status() == RequirementStatus.BELOW) return Group.BELOW;
        return offer == null ? Group.UNKNOWN : group(offer, checks, budget, compatibility);
    }
    public static Comparator<CandidateSuggestion> byPrice() {
        return Comparator.comparing((CandidateSuggestion item) -> item.officialOffer() == null ? null : item.officialOffer().price(), Comparator.nullsLast(BigDecimal::compareTo))
                .thenComparing(CandidateSuggestion::name).thenComparing(CandidateSuggestion::id);
    }
}
