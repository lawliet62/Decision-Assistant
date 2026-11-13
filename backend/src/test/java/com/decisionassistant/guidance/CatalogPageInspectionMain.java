package com.decisionassistant.guidance;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import tools.jackson.databind.json.JsonMapper;

/** 오프라인 stdin 검증 전용. 네트워크·DB·Secret을 사용하거나 원문을 출력하지 않는다. */
public final class CatalogPageInspectionMain {
    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(System.out, true, StandardCharsets.UTF_8));
        if (args.length == 1 && "--sony-spec".equals(args[0])) { inspectSonySpecifications(); return; }
        if (args.length == 1 && java.util.Set.of("--apple5-ios", "--applepro-ios").contains(args[0])) {
            var bytes = System.in.readNBytes(2_000_001);
            if (bytes.length > 2_000_000) throw new IllegalArgumentException("HTML_TOO_LARGE");
            var html = new String(bytes, StandardCharsets.UTF_8);
            System.out.println("APPLE_IOS_VERIFIED=" + (args[0].equals("--apple5-ios")
                    ? AppleSpecificationVerifier.matchesAirpods5(html, "호환성") : AppleSpecificationVerifier.matches(html, "호환성")));
            return;
        }
        if (args.length == 1 && "--sennheiser-spec".equals(args[0])) {
            var bytes = System.in.readNBytes(2_000_001);
            if (bytes.length > 2_000_000) throw new IllegalArgumentException("HTML_TOO_LARGE");
            var html = new String(bytes, StandardCharsets.UTF_8);
            System.out.println("CATALOG_SPEC_INSPECTION=" + JsonMapper.builder().build().writeValueAsString(java.util.Map.of(
                    "ANC 지원", SennheiserSpecificationVerifier.matches(html, "ANC 지원"),
                    "방수", SennheiserSpecificationVerifier.matches(html, "방수"))));
            return;
        }
        if (args.length == 1 && "--anker-liberty5-spec".equals(args[0])) {
            var bytes = System.in.readNBytes(2_000_001);
            if (bytes.length > 2_000_000) throw new IllegalArgumentException("HTML_TOO_LARGE");
            var html = new String(bytes, StandardCharsets.UTF_8);
            System.out.println("ANKER_SPEC_INSPECTION=" + JsonMapper.builder().build().writeValueAsString(java.util.Map.of(
                    "anc", AnkerLibertySpecificationVerifier.matches(html), "multipoint", AnkerLibertySpecificationVerifier.matchesMultipoint(html))));
            return;
        }
        if (args.length != 1 || OfficialStorePolicy.store(args[0]).isEmpty())
            throw new IllegalArgumentException("REGISTERED_URL_REQUIRED");
        int maxBytes = SamsungBuds4Verifier.URL.equals(args[0]) ? SamsungBuds4Verifier.MAX_PRICE_HTML * 3 : 2_000_000;
        var bytes = System.in.readNBytes(maxBytes + 1);
        if (bytes.length > maxBytes) throw new IllegalArgumentException("HTML_TOO_LARGE");
        var page = new WebEvidenceProvider.Page("", "", new String(bytes, StandardCharsets.UTF_8), args[0]);
        var inspected = OfficialProductParser.inspect(args[0], page, Instant.now(), true);
        var output = new LinkedHashMap<String, Object>();
        output.put("outcome", inspected.product() == null ? inspected.rejection().name() : "VERIFIED");
        if (inspected.product() != null) {
            output.put("identity", inspected.product().identity());
            output.put("price", inspected.product().offer().price());
            output.put("currency", inspected.product().offer().currency());
        }
        System.out.println("CATALOG_INSPECTION_RESULT=" + JsonMapper.builder().build().writeValueAsString(output));
    }

    private static void inspectSonySpecifications() throws Exception {
        var json = JsonMapper.builder().build();
        var bytes = System.in.readNBytes(4_100_001);
        if (bytes.length > 4_100_000) throw new IllegalArgumentException("SPEC_BUNDLE_TOO_LARGE");
        var input = json.readTree(new String(bytes,StandardCharsets.UTF_8));
        var pages = input.path("pages");
        var result = new LinkedHashMap<String, Object>();
        try (var manifest = CatalogPageInspectionMain.class.getResourceAsStream("/catalog/earbuds.json")) {
            var entry = java.util.Arrays.stream(json.readValue(manifest, CatalogImporter.Entry[].class))
                    .filter(e -> e.id().equals("sony-wf-c710n-glass-blue")).findFirst().orElseThrow();
            var facts = new java.util.ArrayList<java.util.Map<String,Object>>();
            for (var fact : entry.facts()) {
                var doc = org.jsoup.Jsoup.parse(pages.path(fact.evidence().sourceUrl()).asText(""),fact.evidence().sourceUrl());
                result.put(fact.evidence().sourceUrl(),java.util.Map.of("matches",SonyC710nVerifier.specification(json.writeValueAsString(pages),fact.evidence()),
                        "title",doc.title(),"canonical",doc.select("link[rel=canonical]").eachAttr("href"),
                        "quotePresent",doc.text().contains(fact.evidence().quote())));
                facts.add(java.util.Map.of("name",fact.evidence().name(),"value",fact.evidence().value(),
                        "sourceUrl",fact.evidence().sourceUrl(),"quote",fact.evidence().quote()));
            }
            boolean verified=result.size()==2 && facts.size()==2 && result.values().stream().allMatch(v -> Boolean.TRUE.equals(((java.util.Map<?,?>)v).get("matches")));
            System.out.println("CATALOG_SPEC_INSPECTION=" + json.writeValueAsString(java.util.Map.of(
                    "verified",verified,"productId",entry.id(),"revision",entry.revision(),"purchaseUrl",entry.purchaseUrl(),
                    "identity",entry.expectedIdentity(),"facts",facts,"sources",result)));
        }
    }
}
