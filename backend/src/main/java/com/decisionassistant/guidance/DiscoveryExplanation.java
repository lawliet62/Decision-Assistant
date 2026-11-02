package com.decisionassistant.guidance;

import java.util.*;
import static com.decisionassistant.guidance.DiscoveryContracts.*;

/** 검증된 사양·판정만 문장에 넣는다. 판매처의 홍보 문구와 AI 자유 설명은 사용하지 않는다. */
final class DiscoveryExplanation {
    private DiscoveryExplanation() {}
    static String explain(DiscoveryInput input, OfficialOffer offer, List<ProductFact> facts,
                          List<RequirementCheck> minimums, RequirementCheck compatibility) {
        var parts = new ArrayList<String>(); var checks = new ArrayList<>(minimums);
        if (compatibility != null) checks.add(compatibility);
        for (var check : checks) if (check.status() != RequirementStatus.MEETS) {
            var actual = check.facts().stream().map(ProductFact::value).distinct().collect(java.util.stream.Collectors.joining(" / "));
            var subject = check.requiredValue().equals("지원 필수") ? check.label() : check.label() + "(" + check.requiredValue() + ")";
            parts.add(check.status() == RequirementStatus.BELOW
                    ? "요청 조건 " + subject + "에 미달합니다" + (actual.isBlank() ? "." : " (수집값 " + actual + ").")
                    : subject + " 여부를 " + switch (check.unknownReason()) {
                        case CONFLICT -> "근거가 상충하여 확인하지 못했습니다."; case AMBIGUOUS -> "근거의 표현이 모호하여 확인하지 못했습니다."; default -> "수집 근거로 확인하지 못했습니다.";
                    });
        }
        if (offer == null || CatalogProperties.VERSION.equals(offer.policyVersion()) && !CatalogDiscovery.fresh(offer, java.time.Instant.now())) {
            parts.add("72시간 안에 확인한 판매가가 없어 예산 충족 여부를 판정하지 않았습니다.");
        } else {
            var price = offer.price().stripTrailingZeros().toPlainString();
            parts.add("확인 시점의 공식 판매가는 " + price + "원입니다." + (input.maxBudget() == null ? "" : offer.price().compareTo(input.maxBudget()) > 0 ? " 선택한 예산을 초과합니다." : " 선택한 예산 이내입니다."));
        }
        var verified = checks.stream().filter(c -> c.status() == RequirementStatus.MEETS)
                .map(c -> c.requiredValue().equals("지원 필수") ? c.label() : c.label() + " " + c.requiredValue()).toList();
        if (!verified.isEmpty()) parts.add(String.join(", ", verified) + "의 지원·충족 근거가 있습니다.");
        var uses = input.uses() == null ? List.<String>of() : input.uses();
        boolean anc = minimums.stream().anyMatch(c -> c.id().equals("anc") && c.status() == RequirementStatus.MEETS);
        boolean pen = minimums.stream().anyMatch(c -> c.id().equals("pen") && c.status() == RequirementStatus.MEETS);
        if (anc && uses.contains("출퇴근")) parts.add("ANC는 출퇴근 중 주변 소음 감소에 관련된 기능이며, 실제 차음 성능은 확인하지 않았습니다.");
        else if (pen && (uses.contains("필기") || uses.contains("그림"))) parts.add("전용 펜 지원은 필기·그림 입력에 관련되며 펜 포함 여부와 사용감은 별도 확인이 필요합니다.");
        else if (minimums.stream().anyMatch(c -> c.id().equals("refreshRate") && c.status() == RequirementStatus.MEETS) && uses.contains("게임"))
            parts.add("주사율은 화면 갱신 빈도에 관련된 사양이며 게임의 실제 프레임 성능을 보장하지 않습니다.");
        else if (!uses.isEmpty()) parts.add(String.join("·", uses) + " 용도에서의 실제 성능·사용감은 확인하지 못했습니다.");
        if (uses.contains("통화") || uses.contains("운동")) parts.add("통화 품질·운동 중 착용 안정성은 별도 확인이 필요합니다.");
        if (facts.stream().anyMatch(f -> Set.of("전용 앱", "기기별 추가 기능").contains(f.name())))
            parts.add("전용 앱·추가 기능의 지원 범위와 제약은 아래 사양 근거를 확인하세요.");
        // text column에 저장하므로 제약·미확인을 임의 길이에서 잘라 누락시키지 않는다.
        return String.join(" ", parts);
    }
}
