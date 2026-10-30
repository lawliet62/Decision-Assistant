package com.decisionassistant.guidance;

import com.decisionassistant.template.MinimumRequirements.MinimumRequirement;
import com.decisionassistant.template.MinimumRequirements.RequirementChoice;
import com.decisionassistant.template.MinimumRequirements.Rule;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;
import static com.decisionassistant.guidance.DiscoveryContracts.*;

/** 출처 검사를 통과한 사양을 비교한다. 추정·재검색·점수 평가를 수행하지 않는다. */
public final class MinimumRequirementMatcher {
    private MinimumRequirementMatcher() {}
    private static final String NUMBER = "((?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]+)?)";
    private static final Pattern CAPACITY = Pattern.compile(NUMBER + "\\s*(GB|TB)");
    private static final Pattern REFRESH = Pattern.compile(NUMBER + "\\s*(?i:Hz)");
    private static final Pattern PIXELS = Pattern.compile("([0-9]{3,5})\\s*[xX×]\\s*([0-9]{3,5})(?:\\s*(?i:px|pixels))?");
    private static final Pattern AMBIGUOUS = Pattern.compile(
            "(?i)(?:최대|확장|구성별|옵션별|또는|미확정|미확인|알\\s*수\\s*없|up\\s+to|expandable|optional|unknown|depending|\\bor\\b|(?:\\d|GB|TB|Hz)\\s*[/~–-]\\s*\\d)");
    private static final Pattern NEGATIVE = Pattern.compile("(?i)(?:미지원|지원\\s*(?:안|하지\\s*않)|없음|없다|불가|not\\s+supported|unsupported|without|非搭載|非対応|搭載していない)");
    private static final Pattern ANC_CONTEXT = Pattern.compile("(?i)(?:\\bANC\\b|액티브\\s*노이즈|능동형?\\s*소음|active noise cancel)");
    private static final Pattern PEN_CONTEXT = Pattern.compile("(?i)(?:전용\\s*펜|능동형\\s*펜|액티브\\s*펜|active (?:pen|stylus)|apple pencil|애플\\s*펜슬|s\\s*펜|s pen)");
    private static final Pattern CAPACITIVE_PEN = Pattern.compile("(?i)(?:정전식|capacitive)");

    public static List<MinimumCondition> conditions(List<MinimumRequirement> definitions, Map<String, String> selected) {
        return definitions.stream().filter(item -> selected.containsKey(item.id())).map(item -> {
            var choice = choice(item, selected.get(item.id()));
            return new MinimumCondition(item.id(), item.label(), choice.label());
        }).toList();
    }

    public static List<RequirementCheck> check(List<MinimumRequirement> definitions, Map<String, String> selected,
                                              List<ProductFact> facts) {
        return definitions.stream().filter(item -> selected.containsKey(item.id())).map(item -> {
            var choice = choice(item, selected.get(item.id()));
            var evidence = facts.stream().filter(fact -> item.factName().equals(fact.name())).toList();
            var values = evidence.stream().map(fact -> parse(item.rule(), fact)).toList();
            var status = RequirementStatus.UNKNOWN;
            if (!values.isEmpty() && values.stream().noneMatch(Objects::isNull) && values.stream().distinct().count() == 1) {
                var value = values.getFirst();
                var meets = switch (item.rule()) {
                    case CAPACITY, REFRESH_RATE -> value.number().compareTo(BigDecimal.valueOf(choice.minimum())) >= 0;
                    case RESOLUTION -> value.width() >= choice.width() && value.height() >= choice.height();
                    case SUPPORT -> value.supported();
                };
                status = meets ? RequirementStatus.MEETS : RequirementStatus.BELOW;
            }
            var reason = status != RequirementStatus.UNKNOWN ? null : evidence.isEmpty() ? UnknownReason.MISSING
                    : values.stream().filter(Objects::nonNull).distinct().count() > 1 ? UnknownReason.CONFLICT : UnknownReason.AMBIGUOUS;
            return new RequirementCheck(item.id(), item.label(), choice.label(), status, evidence, reason);
        }).toList();
    }

    private static RequirementChoice choice(MinimumRequirement definition, String id) {
        return definition.choices().stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
    }

    private record Value(BigDecimal number, int width, int height, boolean supported) {}

    private static Value parse(Rule rule, ProductFact fact) {
        if (fact.value() == null || "DISPUTED".equals(fact.verificationStatus())) return null;
        var raw = fact.value().strip();
        // 사람이 확인한 수치는 자유 형식의 메모 대신 입력한 수치 자체를 사용한다.
        if (!"CONFIRMED".equals(fact.verificationStatus()) && !SpecNormalizer.VERSION.equals(fact.normalizationVersion())
                && AMBIGUOUS.matcher(Objects.toString(fact.quote(), "")).find()) return null;
        if (rule == Rule.SUPPORT) return support(raw, fact);
        if (rule == Rule.RESOLUTION) return resolution(raw);
        var matcher = (rule == Rule.CAPACITY ? CAPACITY : REFRESH).matcher(raw);
        if (!matcher.matches()) return null;
        var number = new BigDecimal(matcher.group(1).replace(",", ""));
        if (number.signum() <= 0) return null;
        if (rule == Rule.CAPACITY && "TB".equals(matcher.group(2))) number = number.multiply(BigDecimal.valueOf(1000));
        return new Value(number.stripTrailingZeros(), 0, 0, false);
    }

    private static Value support(String raw, ProductFact fact) {
        var value = raw.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ")
                .replaceFirst("^(?:anc|노이즈 캔슬링|전용 펜|액티브 펜|능동형 펜|펜|active noise cancellation|active stylus)\\s*[:：]?\\s*", "");
        Boolean supported = switch (value) {
            case "지원", "지원함", "있음", "supported", "yes", "true" -> true;
            case "미지원", "지원 안 함", "지원하지 않음", "없음", "not supported", "unsupported", "no", "false" -> false;
            default -> null;
        };
        if (supported == null && "ANC 지원".equals(fact.name()) && ANC_CONTEXT.matcher(raw).find()) {
            if (raw.matches("(?i)(?:(?:적응형|하이브리드|adaptive|hybrid)\\s+)?(?:ANC|액티브\\s*노이즈\\s*캔슬링|active noise cancell?ation)\\s*(?:지원|탑재|제공|기능|supported|enabled)")) supported = true;
            else if (raw.matches("(?i)(?:ANC|액티브\\s*노이즈\\s*캔슬링|active noise cancell?ation)\\s*(?:미지원|없음|not supported|unsupported)")) supported = false;
        }
        if (supported == null) return null;
        var quote = Objects.toString(fact.quote(), "");
        if (supported && NEGATIVE.matcher(quote).find()) return null;
        if ("ANC 지원".equals(fact.name()) && !ANC_CONTEXT.matcher(quote).find()
                && !"アクティブノイズキャンセリング搭載".equals(quote.strip())
                && !AnkerP41iVerifier.QUOTE.equals(quote.strip())) return null;
        if ("펜 지원".equals(fact.name()) && (!PEN_CONTEXT.matcher(quote).find() || CAPACITIVE_PEN.matcher(quote).find())) return null;
        return new Value(null, 0, 0, supported);
    }

    private static Value resolution(String raw) {
        var alias = raw.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
        var named = switch (alias) {
            case "FHD", "FULL HD" -> new Value(null, 1920, 1080, false);
            case "QHD", "WQHD" -> new Value(null, 2560, 1440, false);
            case "4K UHD", "UHD" -> new Value(null, 3840, 2160, false);
            default -> null;
        };
        if (named != null) return named;
        var pixels = PIXELS.matcher(raw);
        if (!pixels.matches()) return null;
        return new Value(null, Integer.parseInt(pixels.group(1)), Integer.parseInt(pixels.group(2)), false);
    }
}
