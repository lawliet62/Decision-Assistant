package com.decisionassistant.template;

import com.decisionassistant.decision.Decision;
import com.decisionassistant.decision.DecisionTypes;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

import static com.decisionassistant.decision.DecisionTypes.ConstraintKind.MANUAL;
import static com.decisionassistant.decision.DecisionTypes.ConstraintKind.NUMERIC_MAX;
import static com.decisionassistant.decision.DecisionTypes.ConstraintKind.NUMERIC_MIN;
import static com.decisionassistant.decision.DecisionTypes.CriterionKind.NUMERIC_UTILITY;
import static com.decisionassistant.decision.DecisionTypes.CriterionKind.RATING_1_TO_5;
import static com.decisionassistant.decision.DecisionTypes.PreferenceDirection.HIGHER_BETTER;
import static com.decisionassistant.decision.DecisionTypes.PreferenceDirection.LOWER_BETTER;
import static com.decisionassistant.decision.DecisionTypes.PreferenceDirection.NOT_APPLICABLE;

@Component
public class DecisionTemplateFactory {

    public String version(DecisionTypes.TemplateKey key) {
        return key == DecisionTypes.TemplateKey.LAPTOP ? "laptop-v1" : "custom-v1";
    }

    public void apply(Decision decision) {
        if (decision.templateKey() != DecisionTypes.TemplateKey.LAPTOP) {
            return;
        }
        var one = BigDecimal.ONE;
        var price = decision.addCriterion(null, "가격", NUMERIC_UTILITY, LOWER_BETTER, one, null, null, null, true);
        var weight = decision.addCriterion(null, "무게", NUMERIC_UTILITY, LOWER_BETTER, one, null, null, "kg", true);
        decision.addCriterion(null, "배터리", NUMERIC_UTILITY, HIGHER_BETTER, one, null, null, "hours", true);
        var memory = decision.addCriterion(null, "메모리", NUMERIC_UTILITY, HIGHER_BETTER, one, null, null, "GB", true);
        var storage = decision.addCriterion(null, "스토리지", NUMERIC_UTILITY, HIGHER_BETTER, one, null, null, "GB", true);
        decision.addCriterion(null, "성능 적합도", RATING_1_TO_5, NOT_APPLICABLE, one, null, null, null, true);
        decision.addCriterion(null, "디스플레이 적합도", RATING_1_TO_5, NOT_APPLICABLE, one, null, null, null, true);

        decision.addConstraint(null, "최대 예산", NUMERIC_MAX, price.id(), null, null, null, false);
        decision.addConstraint(null, "최소 메모리", NUMERIC_MIN, memory.id(), null, "GB", null, false);
        decision.addConstraint(null, "최소 스토리지", NUMERIC_MIN, storage.id(), null, "GB", null, false);
        decision.addConstraint(null, "최대 무게", NUMERIC_MAX, weight.id(), null, "kg", null, false);
        decision.addConstraint(null, "필수 OS·포트·호환성", MANUAL, null, null, null,
                "각 Option이 필수 요구사항을 충족하는지 확인합니다.", false);
    }
}
