package com.decisionassistant.guidance;

import com.decisionassistant.decision.DecisionTypes.TemplateKey;
import java.net.URI;
import java.util.*;

/** 등록 근거와 확장 절차는 docs/official-stores.md. URL 이름이나 검색 순위로 공식 여부를 추론하지 않는다. */
public final class OfficialStorePolicy {
    private OfficialStorePolicy() {}
    public static final String VERSION = "official-kr-v2";
    public static boolean verifiesCompatibility(DiscoveryContracts.OfficialOffer offer) {
        return offer != null && (VERSION.equals(offer.policyVersion()) || CatalogProperties.VERSION.equals(offer.policyVersion()));
    }
    public record Description(String version, String scope, String priceBasis) {}
    public static Description description() { return new Description(VERSION, "KOREAN_OFFICIAL_STORES", "OBSERVED_KRW_PRICE"); }
    public record Store(String id, String name, String host, String productPath, Set<String> imageHosts) {}
    private static final List<Store> STORES = List.of(
            new Store("samsung-kr", "삼성닷컴", "www.samsung.com", "/sec/(buds|smartphones|tablets|galaxybook|monitors)/[^/]+/[^/]+/?", Set.of("images.samsung.com")),
            new Store("apple-kr", "Apple 공식 스토어", "www.apple.com", "/kr/shop/(buy-[^/]+|product)/.+", Set.of("store.storeimages.cdn-apple.com", "www.apple.com")),
            new Store("lg-kr", "LG전자", "www.lge.co.kr", "/(?:product/)?(?:monitors|notebook)/[^/]+/?", Set.of("www.lge.co.kr")),
            new Store("sony-kr", "소니 공식 스토어", "store.sony.co.kr", "/product-view/109871591/?", Set.of("store.sony.co.kr")),
            new Store("bose-kr", "BOSE 공식 온라인 스토어", "www.bose.co.kr", "/357/?", Set.of()),
            new Store("anker-kr", "앤커코리아 공식 스토어", "ankerkorea.co.kr", "/product/(?:detail\\.html|[^/]+/(?:40|43|45|91|154)/?)", Set.of()),
            new Store("marshall-kr", "Marshall 공식 스토어", "www.marshall.com", "/kr/ko/product/(?:motif-ii-anc|minor-iv)/?", Set.of()),
            new Store("sennheiser-kr", "젠하이저 공식 스토어", "kr.sennheiser-hearing.com", "/product/(?:detail\\.html|모멘텀-트루-와이어리스-5/731/?)", Set.of()),
            new Store("audio-technica-kr", "오디오테크니카 공식 스토어", "audio-technica.co.kr", "/product/ath-sq1tw2nc/955/?", Set.of()),
            new Store("qcy-kr", "QCY 공식몰", "qcystore.co.kr", "/product/(?:detail.html|[^/]+/[0-9]+(?:/.*)?)", Set.of("qcystore.co.kr")),
            new Store("qcy-naver", "QCY 공식 스마트스토어", "smartstore.naver.com", "/ylintl/products/[0-9]+/?", Set.of("shop-phinf.pstatic.net")));

    public static Optional<Store> store(String url) {
        try {
            var uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                    || uri.getRawPath().toLowerCase(Locale.ROOT).matches(".*%(?:2f|5c|2e|25).*" )
                    || !uri.normalize().getPath().equals(uri.getPath())) return Optional.empty();
            return STORES.stream().filter(store -> store.host().equalsIgnoreCase(uri.getHost()) && uri.getPath().matches(store.productPath()))
                    .filter(store -> !store.id().equals("sony-kr") || uri.getRawQuery() == null)
                    .filter(store -> !store.id().equals("audio-technica-kr") || uri.getRawQuery() == null && uri.getFragment() == null)
                    .filter(store -> !store.id().equals("bose-kr") || "idx=307".equals(uri.getRawQuery()))
                    .filter(store -> !store.id().equals("anker-kr") || (uri.getPath().equals("/product/detail.html")
                            ? Set.of("product_no=40", "product_no=43", "product_no=45", "product_no=91", "product_no=154").contains(uri.getRawQuery())
                            : uri.getRawQuery() == null || uri.getPath().matches(".*/43/?") && uri.getRawQuery().matches("item_code=P00000BR000[B-E]")
                            || uri.getPath().matches(".*/45/?") && uri.getRawQuery().matches("item_code=P00000BT000[B-E]")
                            || uri.getPath().matches(".*/40/?") && uri.getRawQuery().matches("item_code=P00000BO000[B-C]")
                            || uri.getPath().matches(".*/154/?") && uri.getRawQuery().matches("item_code=P00000FY000[A-D]")))
                    .filter(store -> !store.id().equals("marshall-kr") || (uri.getPath().matches("/kr/ko/product/motif-ii-anc/?")
                            ? "pid=1006450".equals(uri.getRawQuery()) : "pid=1006653".equals(uri.getRawQuery())))
                    .filter(store -> !store.id().equals("sennheiser-kr") || (uri.getPath().equals("/product/detail.html")
                            ? "product_no=731".equals(uri.getRawQuery())
                            : uri.getRawQuery() == null || uri.getRawQuery().matches("item_code=P0000BCD000[B-F]")))
                    .filter(store -> !uri.getPath().equals("/product/detail.html") || Objects.toString(uri.getQuery(), "").matches("(?:.*&)?product_no=[0-9]+(?:&.*)?"))
                    .findFirst();
        } catch (RuntimeException ignored) { return Optional.empty(); }
    }
    public static String image(Store store, String pageUrl, String value) {
        try {
            if (value == null || value.isBlank()) return null;
            var uri = URI.create(pageUrl).resolve(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                    || uri.toASCIIString().length() > 2048) return null;
            return store.imageHosts().contains(uri.getHost()) ? uri.toASCIIString() : null;
        } catch (RuntimeException ignored) { return null; }
    }
    public static String searchScope(TemplateKey key) {
        return switch (key) {
            case EARBUDS -> "(site:qcystore.co.kr/product/ OR site:www.samsung.com/sec/buds/ OR site:www.apple.com/kr/shop/buy-airpods/)";
            case LAPTOP -> "(site:www.samsung.com/sec/galaxybook/ OR site:www.lge.co.kr/notebook/ OR site:www.lge.co.kr/product/notebook/ OR site:www.apple.com/kr/shop/buy-mac/)";
            case PHONE -> "(site:www.samsung.com/sec/smartphones/ OR site:www.apple.com/kr/shop/buy-iphone/)";
            case TABLET -> "(site:www.samsung.com/sec/tablets/ OR site:www.apple.com/kr/shop/buy-ipad/)";
            case MONITOR -> "(site:www.samsung.com/sec/monitors/ OR site:www.lge.co.kr/monitors/ OR site:www.lge.co.kr/product/monitors/)";
            case CUSTOM -> throw new IllegalArgumentException("추천 템플릿이 없습니다.");
        };
    }
    public enum SourceKind { VIDEO, BLOG, OFFICIAL_STORE, WEB }
    public static SourceKind sourceKind(String url, boolean verifiedOffer) {
        if (verifiedOffer) return SourceKind.OFFICIAL_STORE;
        try {
            var host = Objects.toString(URI.create(url).getHost(), "").toLowerCase(Locale.ROOT);
            if (Set.of("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be", "vimeo.com", "www.vimeo.com").contains(host)) return SourceKind.VIDEO;
            if (host.equals("blog.naver.com") || host.equals("m.blog.naver.com") || host.endsWith(".tistory.com") || host.equals("medium.com")) return SourceKind.BLOG;
        } catch (RuntimeException ignored) { }
        return SourceKind.WEB;
    }
}
