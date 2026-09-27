package com.decisionassistant.guidance;

import org.jsoup.Jsoup;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import static com.decisionassistant.guidance.DiscoveryContracts.*;

/** 수집 응답만 파싱한다. 링크·이미지를 추가로 요청하지 않는다. */
final class OfficialProductParser {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private OfficialProductParser() {}

    static OfficialProduct parse(String requestedUrl, WebEvidenceProvider.Page page, Instant fetchedAt) {
        var store = OfficialStorePolicy.store(requestedUrl).orElse(null);
        if (store == null || page.html() == null || page.html().length() > 2_000_000) return null;
        // provider가 최종 URL을 확인하지 못하거나 다른 상품으로 이동했다면 공식 상품으로 승격하지 않는다.
        if (!samePage(requestedUrl, page.resolvedUrl())) return null;
        var doc = Jsoup.parse(page.html(), requestedUrl);
        var products = new ArrayList<JsonNode>();
        for (var script : doc.select("script[type=application/ld+json]")) {
            if (script.data().length() > 200_000) continue;
            try { collect(JSON.readTree(script.data()), products, 0); } catch (RuntimeException ignored) { }
        }
        var matching = products.stream().filter(product -> samePage(requestedUrl, product.path("url").asText(""))
                || samePage(requestedUrl, product.path("@id").asText(""))).toList();
        // 여러 모델·관련 상품이 있으면 현재 페이지에 명시적으로 연결된 단일 상품만 사용한다.
        var candidates = matching.isEmpty() ? products : matching;
        if (candidates.size() != 1) return null;
        var product = candidates.getFirst();
        var name = product.path("name").asText("").strip();
        if (name.isBlank() || name.length() > 200) return null;
        var variants = type(product, "ProductGroup") ? iterable(product.path("hasVariant")) : List.of(product);
        if (type(product, "ProductGroup")) {
            var varies = product.path("variesBy");
            var dimensions = varies.isArray() ? iterable(varies) : List.of(varies);
            if (dimensions.isEmpty() || dimensions.stream().anyMatch(item -> !Set.of("color", "https://schema.org/color", "http://schema.org/color").contains(item.asText("")))) return null;
        }
        var rows = new ArrayList<JsonNode>();
        for (var variant : variants) {
            var offers = variant.path("offers");
            rows.addAll(offers.isArray() ? iterable(offers) : List.of(offers));
        }
        if (rows.isEmpty()) return null;
        var prices = new TreeSet<BigDecimal>();
        for (var row : rows) {
            if (!type(row, "Offer") || !"KRW".equals(row.path("priceCurrency").asText())
                    || row.has("priceSpecification") || row.has("eligibleCustomerType") || row.has("eligibleQuantity")
                    || row.has("validForMemberTier") || row.has("businessFunction")) return null;
            var availability = row.path("availability").asText("");
            if (!availability.isBlank() && !availability.endsWith("/InStock") && !availability.endsWith("/OnlineOnly")) return null;
            var condition = row.path("itemCondition").asText("");
            if (!condition.isBlank() && !condition.endsWith("/NewCondition")) return null;
            if (row.has("url") && !samePage(requestedUrl, row.path("url").asText())) return null;
            try {
                if (row.has("priceValidUntil") && LocalDate.parse(row.path("priceValidUntil").asText()).isBefore(LocalDate.now(java.time.ZoneOffset.UTC))) return null;
                var raw = row.path("price").asText("");
                if (!raw.matches("[0-9]+(?:\\.0+)?")) return null;
                var price = new BigDecimal(raw);
                if (price.signum() <= 0 || price.compareTo(new BigDecimal("100000000")) > 0) return null;
                prices.add(price);
            } catch (RuntimeException ignored) { return null; }
        }
        if (prices.size() != 1) return null;
        var price = prices.first();
        var images = product.has("image") ? product.path("image") : variants.getFirst().path("image");
        var first = images.isArray() ? images.path(0) : images;
        var image = OfficialStorePolicy.image(store, requestedUrl, first.isObject() ? first.path("url").asText("") : first.asText(""));
        if (first.isMissingNode() || first.isNull() || first.asText("").isBlank() && !first.isObject()) image = null;
        var sku = product.path("sku").asText(product.path("productGroupID").asText("")).strip();
        if (sku.length() > 300) return null;
        var identity = store.id() + ":" + (sku.isBlank() ? name.replaceAll("[\\s\\p{Punct}]", "").toLowerCase(Locale.ROOT) : sku.toLowerCase(Locale.ROOT));
        var description = Jsoup.parse(product.path("description").asText("")).text();
        if (description.length() > 1500) description = description.substring(0, 1500);
        return new OfficialProduct(name, identity, new OfficialOffer(requestedUrl, store.name(), price, "KRW", image,
                fetchedAt, "상품 구조화 데이터의 판매가", OfficialStorePolicy.VERSION), description);
    }

    private static List<JsonNode> iterable(JsonNode node) { var items = new ArrayList<JsonNode>(); node.forEach(items::add); return items; }
    private static boolean type(JsonNode node, String type) {
        var value = node.path("@type");
        return value.asText("").equals(type) || value.isArray() && iterable(value).stream().anyMatch(item -> type.equals(item.asText()));
    }
    private static void collect(JsonNode node, List<JsonNode> products, int depth) {
        if (depth > 8) return;
        if (node.isArray()) node.forEach(item -> collect(item, products, depth + 1));
        else if (node.isObject()) {
            if (type(node, "Product") || type(node, "ProductGroup")) products.add(node);
            else { if (node.has("@graph")) collect(node.path("@graph"), products, depth + 1); if (node.has("mainEntity")) collect(node.path("mainEntity"), products, depth + 1); }
        }
    }
    static boolean samePage(String left, String right) {
        try {
            var a = URI.create(left); var b = URI.create(right);
            return OfficialStorePolicy.store(left).isPresent() && OfficialStorePolicy.store(right).isPresent()
                    && a.getHost().equalsIgnoreCase(b.getHost()) && productPath(a).equals(productPath(b));
        } catch (RuntimeException ignored) { return false; }
    }
    private static String productPath(URI uri) {
        if ("qcystore.co.kr".equalsIgnoreCase(uri.getHost())) {
            if (uri.getPath().equals("/product/detail.html")) return Arrays.stream(Objects.toString(uri.getQuery(), "").split("&"))
                    .filter(item -> item.startsWith("product_no=")).map(item -> item.substring(11)).findFirst().orElse("");
            var parts = uri.getPath().split("/");
            if (parts.length > 3) return parts[3];
        }
        return uri.getPath().replaceFirst("/$", "");
    }
}
