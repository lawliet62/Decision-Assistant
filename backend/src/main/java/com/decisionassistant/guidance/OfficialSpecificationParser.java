package com.decisionassistant.guidance;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import static com.decisionassistant.guidance.DiscoveryContracts.*;

/** 승인된 공식 문서의 모델 표기와 사양 영역을 함께 대조한다. 원문·이미지는 저장하지 않는다. */
final class OfficialSpecificationParser {
    private OfficialSpecificationParser() {}
    record Result(List<ProductFact> facts, String outcome) {
        static Result rejected(String reason) { return new Result(List.of(), reason); }
    }
    static Result inspect(String url, WebEvidenceProvider.Page page, OfficialProduct product, List<String> factNames, Instant fetchedAt) {
        var model = product.model();
        if (model == null || !OfficialSpecificationPolicy.allows(url, model)) return Result.rejected("UNREGISTERED_SOURCE");
        if (page == null || page.html() == null || page.html().length() > 2_000_000) return Result.rejected("HTML_UNAVAILABLE");
        if (!OfficialSpecificationPolicy.sameDocument(url, page.resolvedUrl(), model)) return Result.rejected("RESOLVED_URL_MISMATCH");
        var doc = Jsoup.parse(page.html(), url);
        for (var canonical : doc.select("link[rel=canonical]"))
            if (!OfficialSpecificationPolicy.sameDocument(url, canonical.absUrl("href"), model)) return Result.rejected("CANONICAL_URL_MISMATCH");
        doc.select("nav, header, footer, aside, script, style, noscript, [hidden], [aria-hidden=true]").remove();
        for (var element : new ArrayList<>(doc.select("[class], [id]"))) {
            var marker = (element.className() + " " + element.id()).toLowerCase(Locale.ROOT);
            if (marker.matches(".*(?:review|recommend|related|accessor|navigation|mega-menu).*")) element.remove();
        }
        var roots = doc.select("main, article, [itemscope][itemtype$=/Product]").stream()
                .filter(root -> root.select("h1, h2, [itemprop=model]").stream().anyMatch(h -> OfficialSpecificationPolicy.matches(model, h.text())))
                .filter(root -> root.select("main, article, [itemscope][itemtype$=/Product]").stream().noneMatch(child -> child != root
                        && child.select("h1, h2, [itemprop=model]").stream().anyMatch(h -> OfficialSpecificationPolicy.matches(model, h.text())))).toList();
        if (roots.size() != 1) return Result.rejected("MODEL_UNCONFIRMED");
        var root = roots.getFirst();
        String modelQuote;
        var specificationSections = root.select(".specification-section");
        if (!specificationSections.isEmpty()) {
            // QCY의 상품 본문에는 다른 모델 카드도 있다. Product Name으로 다시 연결한 사양 영역만 읽는다.
            if (specificationSections.size() != 1) return Result.rejected("MODEL_AMBIGUOUS");
            var specification = specificationSections.getFirst();
            var names = specification.select(".spec-details p").stream().map(Element::text)
                    .filter(text -> text.startsWith("Product Name:")).toList();
            if (names.size() != 1 || !OfficialSpecificationPolicy.matches(model, names.getFirst())) return Result.rejected("MODEL_UNCONFIRMED");
            modelQuote = names.getFirst();
            root = specification;
        } else modelQuote = root.select("h1, h2, [itemprop=model]").stream()
                .filter(h -> OfficialSpecificationPolicy.matches(model, h.text())).findFirst().orElseThrow().text();
        var headings = root.select("h1, h2, h3, h4, h5, h6, [itemprop=model]").stream().filter(h -> !OfficialSpecificationPolicy.models(model.brand(), h.text()).isEmpty()).toList();
        if (headings.stream().anyMatch(h -> !OfficialSpecificationPolicy.matches(model, h.text()))) return Result.rejected("MODEL_AMBIGUOUS");
        var link = new OfficialEvidenceLink(model.code(), model.quote(), modelQuote, hash(root.text()));
        var blocks = new LinkedHashMap<String, EvidenceBlock>();
        for (var row : root.select("tr")) {
            if (!ProductPageRegions.owned(row, root)) continue;
            var cells = row.children().stream().filter(e -> Set.of("th", "td").contains(e.tagName())).toList();
            if (cells.size() == 2) add(blocks, product, row, root, cells.getFirst().text(), cells.getLast().text());
        }
        for (var label : root.select("dt")) {
            var value = label.nextElementSibling();
            if (value != null && value.tagName().equals("dd") && ProductPageRegions.owned(label, root))
                add(blocks, product, label, root, label.text(), value.text());
        }
        for (var line : root.select("p, li")) {
            if (!ProductPageRegions.owned(line, root) || line.select("p, li").stream().anyMatch(child -> child != line)) continue;
            var copy = line.clone();
            copy.select("br").after("\n");
            for (var text : copy.wholeText().split("\\R")) {
                text = text.strip();
                var colon = text.indexOf(':');
                if (colon > 0 && colon <= 120) add(blocks, product, line, root, text.substring(0, colon), text.substring(colon + 1));
                // 확인한 QCY 사양 블록의 기능 목록. ENC·바람 소음 억제는 ANC로 바꾸지 않는다.
                if (model.brand().equals("qcy") && line.closest(".specification-section .spec-details") != null
                        && text.matches("(?i)active noise cancell?ation(?: \\(ANC\\))?"))
                    blocks.putIfAbsent(text, new EvidenceBlock("s" + blocks.size(), product.identity(), "ANC 지원", "ANC", "supported", text));
            }
        }
        // 명확한 기능 제목만 지원 근거로 삼는다. 제품명에 ANC가 포함됐다는 이유로 채택하지 않는다.
        for (var heading : root.select("h2, h3")) {
            var text = heading.text();
            if (ProductPageRegions.owned(heading, root) && text.matches("(?i)(?:[0-9]+\\s*dB\\s+)?(?:(?:hybrid|adaptive)\\s+)?active noise cancell?ation"))
                blocks.putIfAbsent(text, new EvidenceBlock("s" + blocks.size(), product.identity(), "ANC 지원", "ANC", "supported", text));
        }
        var facts = new ArrayList<ProductFact>();
        int remaining = 6000;
        for (var block : blocks.values()) {
            if (!factNames.contains(block.factName())) continue;
            int size = block.quote().length() + block.value().length() + link.purchaseModelQuote().length() + link.documentModelQuote().length();
            if (size > remaining) continue;
            var fact = SpecNormalizer.fact(block, page.resolvedUrl(), fetchedAt);
            facts.add(new ProductFact(fact.name(), fact.value(), fact.sourceUrl(), fact.quote(), fact.fetchedAt(), fact.verificationStatus(), fact.normalizationVersion(), link));
            remaining -= size;
        }
        return new Result(List.copyOf(facts), facts.isEmpty() ? "NO_SPECIFICATIONS" : "ACCEPTED");
    }
    private static void add(Map<String, EvidenceBlock> blocks, OfficialProduct product, Element element, Element root, String label, String value) {
        label = label.strip(); value = value.strip();
        var name = ProductEvidence.factName(label);
        if (name == null || label.length() > 120 || value.isBlank() || value.length() > 300 || blocks.size() >= 40) return;
        if (name.equals("호환성") && appContext(element, root)) return;
        // 나열된 다른 모델·구성은 현재 모델의 값으로 승격하지 않는다.
        var models = OfficialSpecificationPolicy.models(product.model().brand(), value);
        if (!models.isEmpty() && !models.equals(List.of(product.model().code()))) return;
        var quote = label + ": " + value;
        blocks.putIfAbsent(quote, new EvidenceBlock("s" + blocks.size(), product.identity(), name, label, value, quote));
    }
    private static boolean appContext(Element element, Element root) {
        for (var cursor = element; cursor != null; cursor = cursor.parent()) {
            if ((cursor.id() + " " + cursor.className()).matches("(?is).*(?:\\bapp\\b|앱).*")) return true;
            if (cursor == root) break;
            for (var previous = cursor.previousElementSibling(); previous != null; previous = previous.previousElementSibling()) {
                if (!previous.tagName().matches("h[1-6]")) continue;
                if (previous.text().matches("(?is).*(?:\\bapp\\b|앱).*")) return true;
                break;
            }
        }
        return false;
    }
    private static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
