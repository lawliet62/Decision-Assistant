package com.decisionassistant.job;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * 같은 backend image를 한 번 실행하고 종료하는 job으로 기동하는 switch.
 * 값이 없거나 비어 있으면 기존 web service로 동작한다. env {@code DECISION_ASSISTANT_JOB} 또는
 * program argument {@code --decision-assistant.job=...}로 지정한다.
 */
public final class JobMode {
    public static final String PROPERTY = "decision-assistant.job";
    public static final String PURGE_EXPIRED_DECISIONS = "purge-expired-decisions";
    static final Set<String> SUPPORTED = Set.of(PURGE_EXPIRED_DECISIONS);

    private JobMode() {}

    public static boolean active(Environment environment) {
        return StringUtils.hasText(environment.getProperty(PROPERTY));
    }

    /** job mode에서는 등록하지 않는 상시 실행 bean(예약 작업, 기동 시 import·refresh)에 붙인다. */
    public static final class Inactive implements Condition {
        @Override public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return !active(context.getEnvironment());
        }
    }
}
