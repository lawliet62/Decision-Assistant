import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.lang.reflect.*;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;

/** 공개 페이지를 한 번 읽어 변경 전후 파서에 동일하게 전달한다. HTML은 저장하지 않는다. */
public class SourceComparison {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("oldClasses newClasses outputJson");
        var libs = new ArrayList<URL>();
        try (var files = Files.list(Path.of("BOOT-INF/lib"))) {
            for (var jar : files.filter(p -> p.toString().endsWith(".jar")).toList()) libs.add(jar.toUri().toURL());
        }
        var oldUrls = new ArrayList<>(libs); oldUrls.addFirst(Path.of(args[0]).toUri().toURL());
        var newUrls = new ArrayList<>(libs); newUrls.addFirst(Path.of(args[1]).toUri().toURL());
        try (var before = new URLClassLoader(oldUrls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader());
             var after = new URLClassLoader(newUrls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
            var http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(15)).build();
            var results = new ArrayList<Map<String, Object>>();
            for (var url : List.of("https://qcystore.co.kr/product/detail.html?product_no=990",
                    "https://qcystore.co.kr/product/detail.html?product_no=979",
                    "https://qcystore.co.kr/product/detail.html?product_no=1012",
                    "https://qcystore.co.kr/product/detail.html?product_no=633",
                    "https://www.lge.co.kr/notebook/15u40n-ga76k",
                    "https://www.lge.co.kr/product/notebook/15u50u-growk")) {
                var row = new LinkedHashMap<String, Object>(); row.put("url", url);
                try {
                    var response = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).GET().build(), HttpResponse.BodyHandlers.ofString());
                    var now = Instant.now(); row.put("checkedAt", now.toString()); row.put("httpStatus", response.statusCode());
                    row.put("resolvedUrl", response.uri().toString());
                    row.put("before", inspect(before, url, response.uri().toString(), response.body(), now));
                    row.put("after", inspect(after, url, response.uri().toString(), response.body(), now));
                } catch (Exception failure) { row.put("errorType", failure.getClass().getSimpleName()); }
                results.add(row);
                System.out.println("source_checked=" + url);
            }
            Files.writeString(Path.of(args[2]), JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(results) + "\n");
        }
    }
    private static Map<String,Object> inspect(ClassLoader loader, String url, String resolved, String html, Instant now) throws Exception {
        var pageType = loader.loadClass("com.decisionassistant.guidance.WebEvidenceProvider$Page");
        var constructor = pageType.getDeclaredConstructor(String.class, String.class, String.class, String.class); constructor.setAccessible(true);
        var page = constructor.newInstance("", "공개 출처 진단", html, resolved);
        var method = loader.loadClass("com.decisionassistant.guidance.OfficialProductParser").getDeclaredMethod("inspect", String.class, pageType, Instant.class); method.setAccessible(true);
        var result = method.invoke(null, url, page, now);
        var row = new LinkedHashMap<String,Object>();
        var rejection = call(result, "rejection"); row.put("outcome", rejection == null ? "ACCEPTED" : rejection.toString());
        var product = call(result, "product");
        if (product != null) {
            row.put("name", call(product, "name")); row.put("identity", call(product, "identity")); row.put("priceQuote", call(product, "priceQuote"));
            var fields = new ArrayList<String>();
            for (var block : (List<?>) call(result, "blocks")) fields.add(call(block, "factName").toString());
            row.put("evidenceFields", fields);
        }
        return row;
    }
    private static Object call(Object object, String name) throws Exception {
        var method = object.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(object);
    }
}
