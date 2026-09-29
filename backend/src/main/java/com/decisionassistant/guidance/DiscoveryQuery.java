package com.decisionassistant.guidance;

import com.decisionassistant.template.IntakeTemplates.IntakeTemplate;
import static com.decisionassistant.guidance.DiscoveryContracts.*;

/** 후보 발견용 검색어. 전체 조건은 AI 입력과 서버 판정에 따로 보존한다. */
final class DiscoveryQuery {
    private DiscoveryQuery() {}

    static String build(IntakeTemplate template, DiscoveryRequest condition, DiscoveryExclusions excluded) {
        var hint = switch (template.key()) {
            case EARBUDS -> "required".equals(condition.selectedRequirements().get("anc")) ? " ANC" : "";
            case TABLET -> "required".equals(condition.selectedRequirements().get("pen")) ? " 전용 펜" : "";
            default -> "";
        };
        return OfficialStorePolicy.searchScope(template.key()) + " " + template.label() + hint + excluded.searchTerms();
    }
}
