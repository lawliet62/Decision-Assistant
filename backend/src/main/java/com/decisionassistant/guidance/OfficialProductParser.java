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

    enum Rejection {
        UNREGISTERED_STORE, HTML_MISSING, HTML_TOO_LARGE, RESOLVED_URL_MISSING, RESOLVED_URL_MISMATCH, CANONICAL_URL_MISMATCH,
        PRODUCT_MISSING, PRODUCT_AMBIGUOUS, PRODUCT_NAME_INVALID, VARIANT_UNSUPPORTED,
        OFFER_MISSING, OFFER_TYPE_UNSUPPORTED, CURRENCY_UNSUPPORTED, CONDITIONAL_PRICE,
        UNAVAILABLE, NOT_NEW, OFFER_URL_MISMATCH, PRICE_EXPIRED, PRICE_INVALID,
        PRICE_AMBIGUOUS, IDENTITY_INVALID, PRICE_CONTEXT_MISSING
    }
    record Result(OfficialProduct product, Rejection rejection, List<EvidenceBlock> blocks) {
        Result(OfficialProduct product, Rejection rejection) { this(product, rejection, List.of()); }
        private static Result rejected(Rejection reason) { return new Result(null, reason); }
    }

    static OfficialProduct parse(String requestedUrl, WebEvidenceProvider.Page page, Instant fetchedAt) {
        return inspect(requestedUrl, page, fetchedAt).product();
    }

    static Result inspect(String requestedUrl, WebEvidenceProvider.Page page, Instant fetchedAt) {
        return inspect(requestedUrl, page, fetchedAt, false);
    }

    static Result inspect(String requestedUrl, WebEvidenceProvider.Page page, Instant fetchedAt, boolean catalog) {
        var store = OfficialStorePolicy.store(requestedUrl).orElse(null);
        if (store == null) return Result.rejected(Rejection.UNREGISTERED_STORE);
        if (page.html() == null) return Result.rejected(Rejection.HTML_MISSING);
        if (page.html().length() > 2_000_000) return Result.rejected(Rejection.HTML_TOO_LARGE);
        // provider가 최종 URL을 확인하지 못하거나 다른 상품으로 이동했다면 공식 상품으로 승격하지 않는다.
        if (page.resolvedUrl() == null || page.resolvedUrl().isBlank()) return Result.rejected(Rejection.RESOLVED_URL_MISSING);
        if (!samePage(requestedUrl, page.resolvedUrl())) return Result.rejected(Rejection.RESOLVED_URL_MISMATCH);
        var doc = Jsoup.parse(page.html(), requestedUrl);
        var canonical = doc.selectFirst("link[rel=canonical]");
        if (canonical != null && !samePage(requestedUrl, canonical.absUrl("href"))) return Result.rejected(Rejection.CANONICAL_URL_MISMATCH);
        if (store.id().equals("sony-kr")) return catalog ? inspectSony(doc, requestedUrl, store, fetchedAt) : Result.rejected(Rejection.UNREGISTERED_STORE);
        if (store.id().equals("bose-kr")) {
            if (!catalog || !BoseProductVerifier.URL.equals(requestedUrl)) return Result.rejected(Rejection.UNREGISTERED_STORE);
            var verified = BoseProductVerifier.price(page.html()).orElse(null);
            if (verified == null) return Result.rejected(Rejection.PRICE_CONTEXT_MISSING);
            return new Result(new OfficialProduct(BoseProductVerifier.NAME, "bose-kr:307", new OfficialOffer(requestedUrl, store.name(), verified,
                    "KRW", null, fetchedAt, "공식 상품의 단일 색상·구매 영역을 대조한 판매가", OfficialStorePolicy.VERSION), "", "판매가 " + verified + "원"), null);
        }
        if (store.id().equals("sennheiser-kr")) {
            if (!catalog || !"https://kr.sennheiser-hearing.com/product/detail.html?product_no=731".equals(requestedUrl)) return Result.rejected(Rejection.UNREGISTERED_STORE);
            var verified = SennheiserProductVerifier.price(page.html()).orElse(null);
            if (verified == null) return Result.rejected(Rejection.PRICE_CONTEXT_MISSING);
            return new Result(new OfficialProduct("모멘텀 트루 와이어리스 5", "sennheiser-kr:cafe24_bnispace_1_731",
                    new OfficialOffer(requestedUrl, store.name(), verified, "KRW", null, fetchedAt,
                            "공식 상품 색상별 가격과 구매 영역을 대조한 판매가", OfficialStorePolicy.VERSION), "", "판매가 " + verified + "원"), null);
        }
        var products = new ArrayList<JsonNode>();
        for (var script : doc.select("script[type=application/ld+json]")) {
            if (script.data().length() > 200_000) continue;
            try { collect(JSON.readTree(script.data()), products, 0); } catch (RuntimeException ignored) { }
        }
        var matching = products.stream().filter(product -> samePage(requestedUrl, product.path("url").asText(""))
                || samePage(requestedUrl, product.path("@id").asText(""))).toList();
        // 여러 모델·관련 상품이 있으면 현재 페이지에 명시적으로 연결된 단일 상품만 사용한다.
        var candidates = matching.isEmpty() ? products : matching;
        if (candidates.size() != 1) return Result.rejected(candidates.isEmpty() ? Rejection.PRODUCT_MISSING : Rejection.PRODUCT_AMBIGUOUS);
        var product = candidates.getFirst();
        var name = product.path("name").asText("").strip();
        if (name.isBlank() || name.length() > 200) return Result.rejected(Rejection.PRODUCT_NAME_INVALID);
        var variants = type(product, "ProductGroup") ? iterable(product.path("hasVariant")) : List.of(product);
        if (type(product, "ProductGroup")) {
            var varies = product.path("variesBy");
            var dimensions = varies.isArray() ? iterable(varies) : List.of(varies);
            if ((dimensions.isEmpty() || dimensions.stream().anyMatch(item -> !Set.of("color", "https://schema.org/color", "http://schema.org/color").contains(item.asText(""))))
                    && !qcyColorSelection(store, product, variants, dimensions, requestedUrl)) return Result.rejected(Rejection.VARIANT_UNSUPPORTED);
        }
        var rows = new ArrayList<JsonNode>();
        for (var variant : variants) {
            var offers = variant.path("offers");
            rows.addAll(offers.isArray() ? iterable(offers) : List.of(offers));
        }
        if (rows.isEmpty()) return Result.rejected(Rejection.OFFER_MISSING);
        var prices = new TreeSet<BigDecimal>();
        for (var row : rows) {
            if (row.isMissingNode() || row.isNull()) return Result.rejected(Rejection.OFFER_MISSING);
            if (!type(row, "Offer")) return Result.rejected(Rejection.OFFER_TYPE_UNSUPPORTED);
            if (!"KRW".equals(row.path("priceCurrency").asText())) return Result.rejected(Rejection.CURRENCY_UNSUPPORTED);
            if (row.has("priceSpecification") || row.has("eligibleCustomerType") || row.has("eligibleQuantity")
                    || row.has("validForMemberTier") || row.has("businessFunction")) return Result.rejected(Rejection.CONDITIONAL_PRICE);
            var availability = row.path("availability").asText("");
            if (!availability.isBlank() && !availability.endsWith("/InStock") && !availability.endsWith("/OnlineOnly")) return Result.rejected(Rejection.UNAVAILABLE);
            var condition = row.path("itemCondition").asText("");
            if (!condition.isBlank() && !condition.endsWith("/NewCondition")) return Result.rejected(Rejection.NOT_NEW);
            if (row.has("url") && !samePage(requestedUrl, row.path("url").asText())) return Result.rejected(Rejection.OFFER_URL_MISMATCH);
            try {
                if (row.has("priceValidUntil") && LocalDate.parse(row.path("priceValidUntil").asText()).isBefore(LocalDate.now(java.time.ZoneOffset.UTC))) return Result.rejected(Rejection.PRICE_EXPIRED);
                var raw = row.path("price").asText("");
                if (!raw.matches("[0-9]+(?:\\.0+)?")) return Result.rejected(Rejection.PRICE_INVALID);
                var price = new BigDecimal(raw);
                if (price.signum() <= 0 || price.compareTo(new BigDecimal("100000000")) > 0) return Result.rejected(Rejection.PRICE_INVALID);
                prices.add(price);
            } catch (RuntimeException ignored) { return Result.rejected(Rejection.PRICE_INVALID); }
        }
        if (prices.size() != 1) return Result.rejected(Rejection.PRICE_AMBIGUOUS);
        var price = prices.first();
        var sku = product.path("sku").asText(product.path("productGroupID").asText("")).strip();
        if (store.id().equals("marshall-kr") && (!(samePage("https://www.marshall.com/kr/ko/product/motif-ii-anc?pid=1006450", requestedUrl)
                && "1006450".equals(sku) && "MOTIF II A.N.C.".equals(name)
                || samePage("https://www.marshall.com/kr/ko/product/minor-iv?pid=1006653", requestedUrl)
                && "1006653".equals(sku) && "MINOR IV".equals(name)) || rows.size() != 1
                || !rows.getFirst().path("availability").asText("").equals("https://schema.org/InStock")))
            return Result.rejected(Rejection.IDENTITY_INVALID);
        if (store.id().equals("apple-kr")) {
            var offerSkus = rows.stream().map(row -> row.path("sku").asText("").strip()).distinct().toList();
            if (offerSkus.size() != 1 || offerSkus.getFirst().isBlank()
                    || !sku.isBlank() && !sku.equalsIgnoreCase(offerSkus.getFirst())) return Result.rejected(Rejection.IDENTITY_INVALID);
            sku = offerSkus.getFirst();
        }
        if (sku.length() > 300) return Result.rejected(Rejection.IDENTITY_INVALID);
        var visible = VisiblePrice.inspect(doc, requestedUrl, name, sku, price);
        if (visible.rejection() != null) return Result.rejected(visible.rejection());
        var images = product.has("image") ? product.path("image") : variants.getFirst().path("image");
        var first = images.isArray() ? images.path(0) : images;
        var image = OfficialStorePolicy.image(store, requestedUrl, first.isObject() ? first.path("url").asText("") : first.asText(""));
        if (first.isMissingNode() || first.isNull() || first.asText("").isBlank() && !first.isObject()) image = null;
        var identity = store.id() + ":" + (sku.isBlank() ? name.replaceAll("[\\s\\p{Punct}]", "").toLowerCase(Locale.ROOT) : sku.toLowerCase(Locale.ROOT));
        // QCY는 같은 모델의 색상별 진입 페이지에도 서로 다른 productGroupID를 부여한다.
        // 색상 그룹으로 검증한 경우에만 말미 색상을 제거하여 비교 후보 중복을 막는다.
        if (store.id().equals("qcy-kr") && type(product, "ProductGroup"))
            identity = DiscoveryExclusions.qcyColorIdentity(name);
        var description = Jsoup.parse(product.path("description").asText("")).text();
        if (description.length() > 1500) description = description.substring(0, 1500);
        return new Result(new OfficialProduct(name, identity, new OfficialOffer(requestedUrl, store.name(), price, "KRW", image,
                fetchedAt, "상품 구조화 데이터·구매 영역을 대조한 판매가", OfficialStorePolicy.VERSION), description, visible.quote()), null,
                ProductEvidence.extract(doc, product, requestedUrl, name, identity));
    }

    private static List<JsonNode> iterable(JsonNode node) { var items = new ArrayList<JsonNode>(); node.forEach(items::add); return items; }
    private static Result inspectSony(org.jsoup.nodes.Document doc, String url, OfficialStorePolicy.Store store, Instant at) {
        var regions = doc.select(".product_view_about");
        if (regions.isEmpty()) return Result.rejected(Rejection.PRODUCT_MISSING);
        if (regions.size() != 1) return Result.rejected(Rejection.PRODUCT_AMBIGUOUS);
        var root = regions.first();
        var names = root.select(".product_tit");
        if (names.size() != 1 || !names.first().text().equals("플로트 런 Float Run")
                || !ProductPageRegions.current(doc, url, names.first().text())) return Result.rejected(Rejection.IDENTITY_INVALID);
        if (!root.select(".color_select, .prd_select_box select, .prd_select_box input, .prd_select_box button, .prd_select_box a, .selected_opt li").isEmpty())
            return Result.rejected(Rejection.VARIANT_UNSUPPORTED);
        var buy = root.select(".result_btn_box a.direct[href=/order/sheet]");
        if (buy.size() != 1 || buy.first().hasAttr("disabled") || "true".equalsIgnoreCase(buy.first().attr("aria-disabled"))
                || buy.first().className().matches("(?i).*(disabled|soldout).*")
                || root.text().matches("(?is).*(품절|판매종료|구매불가|sold\\s*out).*"))
            return Result.rejected(Rejection.UNAVAILABLE);
        var amounts = root.select(".product_price .sale .price");
        if (amounts.size() != 1) return Result.rejected(Rejection.PRICE_CONTEXT_MISSING);
        var sale = amounts.first().parent(); var raw = amounts.first().text();
        if (sale == null || sale.text().matches("(?is).*(회원|쿠폰|카드|구독|월\\s*납).*")) return Result.rejected(Rejection.CONDITIONAL_PRICE);
        if (!sale.text().replaceAll("\\s+", "").equals(raw + "원")) return Result.rejected(Rejection.PRICE_CONTEXT_MISSING);
        if (!raw.matches("[0-9]{1,3}(?:,[0-9]{3})+")) return Result.rejected(Rejection.PRICE_INVALID);
        var price = new BigDecimal(raw.replace(",", ""));
        if (price.signum() <= 0 || price.compareTo(new BigDecimal("100000000")) > 0) return Result.rejected(Rejection.PRICE_INVALID);
        var name = names.first().text();
        return new Result(new OfficialProduct(name, "sony-kr:109871591", new OfficialOffer(url, store.name(), price,
                "KRW", null, at, "공식 상품 구매 영역의 판매가", OfficialStorePolicy.VERSION), "", "판매가 " + raw + "원"), null);
    }
    private static boolean qcyColorSelection(OfficialStorePolicy.Store store, JsonNode product, List<JsonNode> variants, List<JsonNode> dimensions, String requestedUrl) {
        if (!store.id().equals("qcy-kr") || dimensions.size() != 1 || variants.isEmpty()) return false;
        var dimension = dimensions.getFirst().asText("");
        var ht20 = dimension.equals("옵션") && samePage(requestedUrl, "https://qcystore.co.kr/product/detail.html?product_no=1028")
                && product.path("productGroupID").asText("").equals("cafe24_ylintlshop_1_1028")
                && product.path("name").asText("").equals("QCY HT20 멜로버즈 블루투스 무선 이어폰 노이즈캔슬링 AI마이크 C타입 커널형 게이밍");
        if (!dimension.equals("선택") && !ht20) return false;
        var colors = ht20 ? Set.of("블랙", "화이트", "퍼플") : Set.of("블랙", "화이트");
        if (ht20) {
            var seen = new HashSet<String>();
            for (var variant : variants) {
                var properties = variant.path("additionalProperty");
                if (!properties.isArray() || properties.size() != 1
                        || !seen.add(properties.get(0).path("value").asText(""))
                        || !variant.path("offers").path("availability").asText("").equals("https://schema.org/InStock")) return false;
            }
            if (!seen.equals(colors)) return false;
        }
        var name = product.path("name").asText("").strip();
        return variants.stream().allMatch(variant -> {
            var properties = variant.path("additionalProperty");
            if (!type(variant, "Product") || !properties.isArray() || properties.size() != 1) return false;
            var property = properties.path(0);
            var color = property.path("value").asText("");
            return type(property, "PropertyValue") && property.path("name").asText("").equals(dimension)
                    && colors.contains(color)
                    && variant.path("name").asText("").strip().equals(name + " " + color);
        });
    }
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
        if ("www.lge.co.kr".equalsIgnoreCase(uri.getHost()))
            return uri.getPath().replaceFirst("^/product/", "/").replaceFirst("/$", "");
        if ("qcystore.co.kr".equalsIgnoreCase(uri.getHost())) {
            if (uri.getPath().equals("/product/detail.html")) return Arrays.stream(Objects.toString(uri.getQuery(), "").split("&"))
                    .filter(item -> item.startsWith("product_no=")).map(item -> item.substring(11)).findFirst().orElse("");
            var parts = uri.getPath().split("/");
            if (parts.length > 3) return parts[3];
        }
        if ("kr.sennheiser-hearing.com".equalsIgnoreCase(uri.getHost())) return "731";
        return uri.getPath().replaceFirst("/$", "");
    }
}
