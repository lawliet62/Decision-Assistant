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
        return switch (key) {
            case LAPTOP -> "laptop-v2";
            case PHONE -> "phone-v1";
            case TABLET -> "tablet-v1";
            case MONITOR -> "monitor-v1";
            case EARBUDS -> "earbuds-v1";
            case CUSTOM -> "custom-v1";
        };
    }

    public void apply(Decision decision) {
        if (decision.templateKey() == DecisionTypes.TemplateKey.CUSTOM) {
            return;
        }
        if (decision.templateKey() == DecisionTypes.TemplateKey.LAPTOP) {
            applyLaptop(decision);
        } else if (decision.templateKey() == DecisionTypes.TemplateKey.EARBUDS) {
            applyEarbuds(decision);
        } else {
            applyPersonalDevice(decision);
        }
    }

    private void applyLaptop(Decision decision) {
        var one = BigDecimal.ONE;
        var price = decision.addCriterion(null, "가격", NUMERIC_UTILITY, LOWER_BETTER, one,
                BigDecimal.valueOf(4_000_000), BigDecimal.valueOf(700_000), "KRW", true);
        var weight = decision.addCriterion(null, "무게", NUMERIC_UTILITY, LOWER_BETTER, one,
                BigDecimal.valueOf(3), BigDecimal.valueOf(0.8), "kg", true);
        decision.addCriterion(null, "배터리", NUMERIC_UTILITY, HIGHER_BETTER, one,
                BigDecimal.valueOf(4), BigDecimal.valueOf(20), "hours", true);
        var memory = decision.addCriterion(null, "메모리", NUMERIC_UTILITY, HIGHER_BETTER, one,
                BigDecimal.valueOf(8), BigDecimal.valueOf(64), "GB", true);
        var storage = decision.addCriterion(null, "스토리지", NUMERIC_UTILITY, HIGHER_BETTER, one,
                BigDecimal.valueOf(256), BigDecimal.valueOf(2048), "GB", true);
        decision.addCriterion(null, "성능 적합도", RATING_1_TO_5, NOT_APPLICABLE, one, null, null, null, true);
        decision.addCriterion(null, "디스플레이 적합도", RATING_1_TO_5, NOT_APPLICABLE, one, null, null, null, true);

        decision.addConstraint(null, "최대 예산", NUMERIC_MAX, price.id(), null, null, null, false);
        decision.addConstraint(null, "최소 메모리", NUMERIC_MIN, memory.id(), null, "GB", null, false);
        decision.addConstraint(null, "최소 스토리지", NUMERIC_MIN, storage.id(), null, "GB", null, false);
        decision.addConstraint(null, "최대 무게", NUMERIC_MAX, weight.id(), null, "kg", null, false);
        decision.addConstraint(null, "필수 OS·포트·호환성", MANUAL, null, null, null,
                "각 Option이 필수 요구사항을 충족하는지 확인합니다.", false);
    }

    private void applyEarbuds(Decision decision) {
        var price = decision.addCriterion(null, "가격", NUMERIC_UTILITY, LOWER_BETTER, BigDecimal.ONE,
                BigDecimal.valueOf(500_000), BigDecimal.valueOf(50_000), "KRW", true);
        for (var name : java.util.List.of("음질", "착용감", "노이즈 캔슬링", "배터리 적합도")) {
            decision.addCriterion(null, name, RATING_1_TO_5, NOT_APPLICABLE, BigDecimal.ONE,
                    null, null, null, true);
        }
        decision.addConstraint(null, "최대 예산", NUMERIC_MAX, price.id(), null, "KRW", null, false);
        decision.addConstraint(null, "필수 호환성", MANUAL, null, null, null,
                "사용할 휴대폰·앱·기능과의 호환성을 직접 확인합니다.", false);
    }

    private void applyPersonalDevice(Decision decision) {
        var one = BigDecimal.ONE;
        var price = decision.addCriterion(null, "가격", NUMERIC_UTILITY, LOWER_BETTER, one,
                BigDecimal.valueOf(3_000_000), BigDecimal.valueOf(300_000), "KRW", true);
        decision.addCriterion(null, "성능 적합도", RATING_1_TO_5, NOT_APPLICABLE, one, null, null, null, true);
        decision.addCriterion(null, "휴대성", RATING_1_TO_5, NOT_APPLICABLE, one, null, null, null, true);
        decision.addCriterion(null, "배터리 적합도", RATING_1_TO_5, NOT_APPLICABLE, one, null, null, null, true);
        decision.addCriterion(null, "디스플레이 적합도", RATING_1_TO_5, NOT_APPLICABLE, one, null, null, null, true);
        decision.addCriterion(null, "생태계·호환성", RATING_1_TO_5, NOT_APPLICABLE, one, null, null, null, true);
        decision.addConstraint(null, "최대 예산", NUMERIC_MAX, price.id(), null, "KRW", null, false);
        decision.addConstraint(null, "필수 호환성", MANUAL, null, null, null,
                "기존 기기·앱·포트와 반드시 호환되어야 하는지 확인합니다.", false);
    }
}
