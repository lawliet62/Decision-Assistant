package com.decisionassistant.decision;

import java.util.List;
import com.decisionassistant.common.InvalidDecisionException;

public final class DecisionReasons {
    private DecisionReasons() {}
    public record Reason(String id, String label, DecisionTypes.ResolutionKind kind) {}
    public static List<Reason> templates() {
        return List.of(
            new Reason("budget", "예산에 적합함", DecisionTypes.ResolutionKind.CHOSEN),
            new Reason("features", "필요한 기능을 충족함", DecisionTypes.ResolutionKind.CHOSEN),
            new Reason("compatibility", "사용하던 기기와 잘 맞음", DecisionTypes.ResolutionKind.CHOSEN),
            new Reason("performance", "가격보다 성능을 우선함", DecisionTypes.ResolutionKind.CHOSEN),
            new Reason("information", "추가 정보가 필요함", DecisionTypes.ResolutionKind.DEFERRED),
            new Reason("wait_price", "가격 하락을 기다림", DecisionTypes.ResolutionKind.DEFERRED),
            new Reason("later", "지금은 필요하지 않음", DecisionTypes.ResolutionKind.DEFERRED),
            new Reason("compare_more", "후보를 더 비교하고 싶음", DecisionTypes.ResolutionKind.DEFERRED),
            new Reason("unnecessary", "필요성이 줄어듦", DecisionTypes.ResolutionKind.ABANDONED),
            new Reason("expensive", "예산 부담이 큼", DecisionTypes.ResolutionKind.ABANDONED),
            new Reason("no_match", "조건에 맞는 제품이 없음", DecisionTypes.ResolutionKind.ABANDONED),
            new Reason("keep_current", "기존 제품을 계속 사용하기로 함", DecisionTypes.ResolutionKind.ABANDONED));
    }
    public static List<String> validate(DecisionTypes.ResolutionKind kind, List<String> ids, String primary) {
        var selected = ids == null ? List.<String>of() : ids;
        if (selected.size() > 8 || selected.stream().distinct().count() != selected.size()
                || selected.stream().anyMatch(id -> templates().stream().noneMatch(r -> r.kind() == kind && r.id().equals(id)))
                || primary != null && !selected.contains(primary))
            throw new InvalidDecisionException(List.of("결과에 맞는 이유와 선택한 대표 이유를 지정하세요."));
        return List.copyOf(selected);
    }
}
