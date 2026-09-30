package com.decisionassistant.guidance;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.util.*;

/** 동일 상품임을 확인한 구조만 사용한다. 페이지 전체를 상품 사양으로 승격하지 않는다. */
final class ProductPageRegions {
    private ProductPageRegions() {}
    static List<Element> products(Document doc, String name, String url, String sku) {
        var matches = doc.select("[itemscope][itemtype$=/Product]").stream()
                .filter(root -> root.select("[itemprop=name]").stream()
                        .anyMatch(item -> text(item.text()).equals(text(name)) && item.closest("[itemscope][itemtype$=/Product]") == root))
                .filter(root -> root.select("[itemprop=sku]").stream().filter(item -> item.closest("[itemscope][itemtype$=/Product]") == root)
                        .allMatch(item -> !sku.isBlank() && sku.equals(item.hasAttr("content") ? item.attr("content") : item.text().strip())))
                .filter(root -> root.select("[itemprop=url]").stream().filter(item -> item.closest("[itemscope][itemtype$=/Product]") == root)
                        .allMatch(item -> OfficialProductParser.samePage(url, item.hasAttr("href") ? item.absUrl("href") : item.attr("content"))))
                .toList();
        return matches.size() == 1 ? matches : List.of();
    }
    static boolean current(Document doc, String url, String name) {
        var canonical = doc.selectFirst("link[rel=canonical]");
        if (canonical != null && !OfficialProductParser.samePage(url, canonical.absUrl("href"))) return false;
        return text(doc.title()).contains(text(name)) || doc.select("h1, meta[property=og:title]").stream()
                .anyMatch(item -> text(item.tagName().equals("meta") ? item.attr("content") : item.text()).contains(text(name)));
    }
    static boolean owned(Element element, Element root) {
        for (var cursor = element; cursor != null; cursor = cursor.parent()) {
            var marker = (cursor.id() + " " + cursor.className() + " " + cursor.attr("itemprop")).toLowerCase(Locale.ROOT);
            if (marker.matches(".*(?:review|recommend|related|accessor).*")) return false;
            if (cursor == root) return true;
            if (cursor.hasAttr("itemscope") && cursor.attr("itemtype").endsWith("/Product")) return false;
        }
        return false;
    }
    private static String text(String value) { return value.replaceAll("\\s+", " ").strip(); }
}
