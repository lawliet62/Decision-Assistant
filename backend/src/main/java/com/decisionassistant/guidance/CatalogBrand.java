package com.decisionassistant.guidance;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** 관리용 대표 브랜드. 알려진 표식은 누락 검사에만 사용하며 모기업 관계를 추론하지 않는다. */
final class CatalogBrand {
    private static final Pattern[] KNOWN = {
            Pattern.compile("(^|[^a-z0-9])(qcy|큐씨와이)([^a-z0-9]|$)"),
            Pattern.compile("(^|[^a-z0-9])(anker|soundcore|앤커|사운드코어)([^a-z0-9]|$)"),
            Pattern.compile("(^|[^a-z0-9])(apple|airpods|애플)([^a-z0-9]|$)"),
            Pattern.compile("(^|[^a-z0-9])(audio-technica|오디오테크니카)([^a-z0-9]|$)"),
            Pattern.compile("(^|[^a-z0-9])(bose|보스)([^a-z0-9]|$)"),
            Pattern.compile("(^|[^a-z0-9])(marshall|마샬)([^a-z0-9]|$)"),
            Pattern.compile("(^|[^a-z0-9])(sennheiser|젠하이저)([^a-z0-9]|$)"),
            Pattern.compile("(^|[^a-z0-9])(sony|소니)([^a-z0-9]|$)"),
            Pattern.compile("(^|[^a-z0-9])(samsung|삼성)([^a-z0-9]|$)"),
            Pattern.compile("(^|[^a-z0-9])(lg|엘지)([^a-z0-9]|$)")
    };
    private CatalogBrand() {}
    static String validate(String brand, String id, String name, String identity) {
        if (brand == null) throw new IllegalArgumentException("brandName을 명시하세요. 노브랜드는 빈 문자열로 등록합니다.");
        var value = brand.strip();
        if (value.length() > 100) throw new IllegalArgumentException("브랜드명은 최대 100자입니다.");
        var text = (Objects.toString(id, "") + " " + Objects.toString(name, "") + " "
                + Objects.toString(identity, "")).toLowerCase(Locale.ROOT);
        if (value.isEmpty()) for (var known : KNOWN) {
            if (known.matcher(text).find()) throw new IllegalArgumentException("브랜드가 확인되는 제품의 brandName은 비워 둘 수 없습니다.");
        }
        return value;
    }
}
