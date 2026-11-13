package com.decisionassistant.guidance;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.regex.Pattern;

/** 버즈4 화이트의 공개 즉시 할인만 확인한다. 카드·구독·추가 구성 가격은 수용하지 않는다. */
final class SamsungBuds4Verifier {
    static final int MAX_PRICE_HTML = 3_000_000;
    static final String ID = "samsung-galaxy-buds4-white";
    static final String SKU = "SM-R540NZWAKOO";
    static final String URL = "https://www.samsung.com/sec/buds/galaxy-buds4/buy/?modelCode=" + SKU;
    static final String CANONICAL = "https://www.samsung.com/sec/buds/galaxy-buds4-r540/" + SKU + "/";
    static final String GROUP = "https://www.samsung.com/sec/buds/galaxy-buds4/buy/";
    static final String TITLE = "갤럭시 버즈4 (화이트) | " + SKU + " | Samsung 대한민국";
    static final String NAME = "삼성 갤럭시 버즈4 화이트";
    static final String ANC_QUOTE = "액티브 노이즈 캔슬링(ANC) 알고리즘이 실시간으로 주변 소음을 차단하죠.";
    static final String BASIC_QUOTE = "갤럭시 버즈4를 블루투스 오디오로 연결하는 것은 OS에 관계없이 가능하지만";
    private static final Pattern PERIOD = Pattern.compile("기간\\s*:\\s*(20\\d{2}\\.\\d{2}\\.\\d{2})\\s*[~-]\\s*(20\\d{2}\\.\\d{2}\\.\\d{2})");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu.MM.dd").withResolverStyle(ResolverStyle.STRICT);
    private SamsungBuds4Verifier() {}

    static Optional<BigDecimal> price(String html, Instant at) {
        if (html == null || html.length() > MAX_PRICE_HTML) return rejected("HTML_SIZE");
        try {
            var doc = Jsoup.parse(html, URL);
            if (!model(doc)) return rejected("PAGE_IDENTITY");
            var groups = new ArrayList<tools.jackson.databind.JsonNode>();
            for (var script : doc.select("script[type=application/ld+json]")) {
                if (script.data().length() > 200_000) return rejected("JSON_SIZE");
                var node = JsonMapper.builder().build().readTree(script.data());
                if (node.path("@type").isString() && "ProductGroup".equals(node.path("@type").asText())) groups.add(node);
            }
            if (groups.size() != 1 || !GROUP.equals(groups.getFirst().path("@id").asText(""))) return rejected("PRODUCT_GROUP");
            var selected = new ArrayList<tools.jackson.databind.JsonNode>();
            for (var variant : groups.getFirst().path("hasVariant")) if (SKU.equals(variant.path("sku").asText(""))) selected.add(variant);
            if (selected.size() != 1 || !"갤럭시 버즈4|화이트".equals(selected.getFirst().path("name").asText(""))
                    || !"화이트".equals(selected.getFirst().path("color").asText(""))) return rejected("VARIANT_IDENTITY");
            var offer = selected.getFirst().path("offers");
            if (!offer.isObject() || !"Offer".equals(offer.path("@type").asText("")) || !URL.equals(offer.path("url").asText(""))
                    || !"KRW".equals(offer.path("priceCurrency").asText(""))
                    || !"https://schema.org/InStock".equals(offer.path("availability").asText(""))
                    || !"https://schema.org/NewCondition".equals(offer.path("itemCondition").asText(""))) return rejected("OFFER_IDENTITY_OR_STOCK");
            for (var field : List.of("priceSpecification", "eligibleCustomerType", "eligibleQuantity", "validForMemberTier", "businessFunction", "priceValidUntil"))
                if (offer.has(field)) return rejected("UNSUPPORTED_OFFER_CONDITION");
            var raw = offer.path("price").asText("");
            if (!raw.matches("[1-9][0-9]{0,7}")) return rejected("PRICE_FORMAT");
            var price = new BigDecimal(raw);
            if (!text(doc,"#bcBottomMdlCode",SKU) || !text(doc,"#goodsInfo .pd_txt",SKU)) return rejected("DISPLAYED_SKU");
            var models = doc.select("input[data-bc-opt-gb=30][checked]");
            var colors = doc.select("input[data-bc-opt-gb=10][checked]");
            if (models.size()!=1 || colors.size()!=1 || !"갤럭시 버즈4".equals(models.first().parent().select("label .choice_tit").text())
                    || !"화이트".equals(colors.first().parent().select("label .color_txt").text())) return rejected("SELECTED_MODEL_OR_COLOR");
            if (!text(doc,"#buy_count_status","1") || doc.select("input#buy_count").size()!=1
                    || !Set.of("","1").contains(doc.selectFirst("input#buy_count").val())) return rejected("QUANTITY");
            if (!text(doc,"#bcOrgPriceDt","기준가") || !text(doc,"#bcPriceNm","혜택가")
                    || !amount(doc,"#bcPrice",price) || !amount(doc,"#bcTotalPrice",price)) return rejected("PUBLIC_PRICE_OR_TOTAL");
            var bases=doc.select("#bcOrgPrice");
            if(bases.size()!=1 || !bases.first().text().matches("[0-9]{1,3}(?:,[0-9]{3})+")) return rejected("BASE_PRICE");
            var base=new BigDecimal(bases.first().text().replace(",",""));
            var discounts=doc.select("#defaultDiscountInfo");
            if(discounts.size()!=1 || !"즉시 할인".equals(discounts.first().select("h5.list_tit").text())) return rejected("INSTANT_DISCOUNT");
            var discountRaw=discounts.first().attr("data-discount-amount");
            if(!discountRaw.matches("[0-9]{1,8}") || !discountRaw.equals(discounts.first().select(".list_price em").text().replace(",","").replaceFirst("^-",""))) return rejected("DISCOUNT_AMOUNT");
            var discount=new BigDecimal(discountRaw);
            if(base.subtract(discount).compareTo(price)!=0 || discount.signum()<0) return rejected("DISCOUNT_CALCULATION");
            var cards=doc.select("input#chkPayBenefitCard");
            if(cards.isEmpty() || cards.size()>10 || cards.stream().anyMatch(c->!c.hasAttr("disabled") || c.hasAttr("checked")
                    || !"금액대 미달성".equals(c.parent().select("span.price").text()))) return rejected("CARD_BENEFIT_UNVERIFIED");
            var buy=doc.select("button#btnDirect[data-cta-type=normal]");
            if(buy.size()!=1 || !"구매하기".equals(buy.first().text()) || unavailable(buy.first())) return rejected("PURCHASE_UNAVAILABLE");
            if(discount.signum()>0 && !promotionCovers(doc.text(),at)) return rejected("PROMOTION_HORIZON");
            return Optional.of(price);
        } catch (RuntimeException e) { return rejected("PARSE_FAILED"); }
    }

    static boolean supports(DiscoveryContracts.ProductFact f) {
        return URL.equals(f.sourceUrl()) && ("ANC 지원".equals(f.name()) && "지원".equals(f.value()) && ANC_QUOTE.equals(f.quote())
                || "호환성".equals(f.name()) && "Android/iOS".equals(f.value()) && BASIC_QUOTE.equals(f.quote()));
    }
    static boolean basicPlatform(DiscoveryContracts.ProductFact f,String requested) {
        return supports(f) && "호환성".equals(f.name()) && Set.of("Android","iOS").contains(requested);
    }
    static boolean specification(String html,DiscoveryContracts.ProductFact fact) {
        if(!supports(fact) || html==null || html.length()>2_000_000) return false;
        var doc=Jsoup.parse(html,URL);
        if(!model(doc)) return false;
        return doc.select("p").stream().anyMatch(p->p.text().contains(fact.quote())
                && (!"ANC 지원".equals(fact.name()) || p.text().contains("갤럭시 버즈4는 뛰어난 사운드 시스템")));
    }
    static boolean promotionCovers(String text,Instant at) {
        var matcher=PERIOD.matcher(text);var periods=new HashSet<String>();
        while(matcher.find()) periods.add(matcher.group(1)+"|"+matcher.group(2));
        if(periods.size()!=1) return false;
        try {
            var dates=periods.iterator().next().split("\\|");var zone=ZoneId.of("Asia/Seoul");
            var start=LocalDate.parse(dates[0],DATE).atStartOfDay(zone).toInstant();
            var end=LocalDate.parse(dates[1],DATE).plusDays(1).atStartOfDay(zone).toInstant();
            return !at.isBefore(start) && !at.plus(Duration.ofHours(72)).isAfter(end);
        } catch(RuntimeException e) {return false;}
    }
    private static boolean model(Document d) {
        var c=d.select("link[rel=canonical]");
        return TITLE.equals(d.title()) && c.size()==1 && CANONICAL.equals(c.first().absUrl("href"));
    }
    private static boolean text(Document d,String selector,String expected) {
        var e=d.select(selector);return e.size()==1 && expected.equals(e.first().text());
    }
    private static boolean amount(Document d,String selector,BigDecimal expected) {
        var e=d.select(selector);
        return e.size()==1 && e.first().text().matches("[0-9]{1,3}(?:,[0-9]{3})+")
                && expected.compareTo(new BigDecimal(e.first().text().replace(",","")))==0
                && expected.toPlainString().equals(e.first().attr("data-price"));
    }
    private static boolean unavailable(Element e) {
        for(var p=e;p!=null;p=p.parent()) if(p.hasAttr("disabled") || p.hasAttr("hidden") || "true".equals(p.attr("aria-disabled"))
                || p.attr("style").matches("(?is).*(display\\s*:\\s*none|visibility\\s*:\\s*hidden).*")) return true;
        return false;
    }
    private static Optional<BigDecimal> rejected(String stage) {
        org.slf4j.LoggerFactory.getLogger(SamsungBuds4Verifier.class).warn("samsung_buds4_price_check stage={}",stage);
        return Optional.empty();
    }
}
