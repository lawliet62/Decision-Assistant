package com.decisionassistant.common;

import java.util.List;

public class InvalidDecisionException extends RuntimeException {

    private final List<String> violations;

    public InvalidDecisionException(List<String> violations) {
        super("Decision is not ready");
        this.violations = List.copyOf(violations);
    }

    public List<String> violations() {
        return violations;
    }
}
