package com.decisionassistant.guidance;

import java.text.Normalizer;
import java.util.*;

/** 과거 공개 모델명은 검색의 보조 힌트, 전체 이력은 서버의 최종 제외 기준이다. */
record DiscoveryExclusions(Set<String> identities, Set<String> names, List<String> urls, List<String> publicNames) {
    static DiscoveryExclusions from(List<CandidateSuggestion> previous, List<String> optionNames) {
        var identities = new HashSet<String>(); var names = new HashSet<String>();
        var urls = new ArrayList<String>(); var publicNames = new ArrayList<String>();
        for (var candidate : previous) {
            if (candidate.productIdentity() != null) identities.add(candidate.productIdentity());
            names.add(normalizedName(candidate.name())); urls.add(candidate.url()); publicNames.add(candidate.name());
        }
        optionNames.forEach(name -> names.add(normalizedName(name)));
        return new DiscoveryExclusions(Set.copyOf(identities), Set.copyOf(names), List.copyOf(urls), List.copyOf(publicNames));
    }
    boolean contains(DiscoveryContracts.OfficialProduct product) {
        return identities.contains(product.identity()) || names.contains(normalizedName(product.name())) || containsUrl(product.offer().purchaseUrl());
    }
    boolean containsUrl(String url) { return urls.stream().anyMatch(previous -> OfficialProductParser.samePage(previous, url)); }
    String searchTerms() {
        // 오래된 모든 이름을 질의에 무한히 붙이지 않는다. 전체 누적 제외는 아래 서버 판정으로 보장한다.
        var start = Math.max(0, publicNames.size() - 5);
        return publicNames.subList(start, publicNames.size()).stream().map(name -> name.replaceAll("[^\\p{L}\\p{N}\\s]", " ").strip())
                .filter(name -> !name.isBlank() && name.length() <= 100).distinct().map(name -> " -\"" + name + "\"").collect(java.util.stream.Collectors.joining());
    }
    static String normalizedName(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).replaceAll("[^\\p{L}\\p{N}]", "").toLowerCase(Locale.ROOT);
    }
}
