package com.decisionassistant.guidance;

import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;
import static com.decisionassistant.guidance.DiscoveryContracts.*;

/** 구매 모델과 공식 문서의 연결 범위. 해외 판매가나 모델 별칭을 구매 조건으로 사용하지 않는다. */
final class OfficialSpecificationPolicy {
    private OfficialSpecificationPolicy() {}
    private static final Pattern QCY_MODEL = Pattern.compile("(?i)(?<![a-z0-9_])((?:HT|T|H)\\d+[a-z0-9]*(?:[ -]+(?:ANC|PRO|PLUS|ULTRA|LITE|MAX|NEO)(?:[ -]*[0-9]+)?|[ -]+[0-9]+)*(?:\\+)?)(?![a-z0-9_+])");

    static ProductModel purchaseModel(String url, String name) {
        var store = OfficialStorePolicy.store(url).orElse(null);
        if (store == null) return null;
        var brand = switch (store.id()) { case "qcy-kr", "qcy-naver" -> "qcy"; default -> null; };
        if (brand == null) return null;
        var codes = models(brand, name);
        // 제품군·마케팅 이름만으로 다른 구성의 사양을 연결하지 않는다.
        return codes.size() == 1 ? new ProductModel(brand, codes.getFirst(), name) : null;
    }

    static List<String> models(String brand, String text) {
        var matcher = pattern(brand).matcher(text);
        var codes = new LinkedHashSet<String>();
        while (matcher.find()) codes.add(normalize(matcher.group()));
        return List.copyOf(codes);
    }
    private static Pattern pattern(String brand) {
        if (!brand.equals("qcy")) throw new IllegalArgumentException("알 수 없는 제조사");
        return QCY_MODEL;
    }
    static String normalize(String code) {
        return code.toUpperCase(Locale.ROOT).replaceAll("(?<=\\d)[\\s-]+(?=\\d)", "_").replaceAll("[\\s-]", "");
    }
    static boolean matches(ProductModel model, String heading) {
        return heading.length() <= 300 && models(model.brand(), heading).equals(List.of(model.code()));
    }
    static String query(ProductModel model) {
        var scope = switch (model.brand()) {
            case "qcy" -> "(site:www.qcy.com/products/ OR site:support.qcy.com/support-center- OR site:www.qcy.com/pages/support-center- OR site:www.qcykorea.com/default/product/)";
            default -> throw new IllegalArgumentException("알 수 없는 제조사");
        };
        // 공개 모델만 전송한다. 메모·사용자 이름·예산·identity는 포함하지 않는다.
        var token = pattern(model.brand()).matcher(model.quote());
        var queryModel = token.find() ? token.group() : model.code();
        return scope + " \"" + queryModel + "\"";
    }
    static boolean allows(String url, ProductModel model) { return key(url, model.brand()) != null; }
    static boolean sameDocument(String left, String right, ProductModel model) {
        var key = key(left, model.brand());
        return key != null && key.equals(key(right, model.brand()));
    }
    private static String key(String url, String brand) {
        try {
            var uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || uri.getHost() == null
                    || uri.getPort() != -1 && uri.getPort() != 443 || uri.getRawPath().contains("%")
                    || !uri.normalize().getPath().equals(uri.getPath())) return null;
            var host = uri.getHost().toLowerCase(Locale.ROOT);
            var path = uri.getPath().replaceFirst("/$", "");
            if (brand.equals("qcy")) {
                if (host.equals("www.qcy.com") && path.matches("/products/[a-z0-9-]+")) return host + path;
                if (host.equals("support.qcy.com") && path.matches("/support-center-[a-z0-9-]+")) return "qcy-support" + path;
                if (host.equals("www.qcy.com") && path.matches("/pages/support-center-[a-z0-9-]+")) return "qcy-support" + path.substring(6);
                if (host.equals("www.qcykorea.com") && path.equals("/default/product/product01.php")) {
                    var params = new HashMap<String, String>();
                    for (var pair : Objects.toString(uri.getRawQuery(), "").split("&")) {
                        var parts = pair.split("=", 2);
                        if (parts.length == 2 && params.putIfAbsent(parts[0], parts[1]) != null) return null;
                    }
                    if ("read_form".equals(params.get("com_board_basic")) && "10".equals(params.get("com_board_id"))
                            && Objects.toString(params.get("com_board_idx"), "").matches("[0-9]+")) return host + path + ":" + params.get("com_board_idx");
                }
            }
            return null;
        } catch (RuntimeException ignored) { return null; }
    }
}
