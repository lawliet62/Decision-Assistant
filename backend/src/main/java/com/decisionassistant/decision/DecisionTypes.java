package com.decisionassistant.decision;

public final class DecisionTypes {

    private DecisionTypes() {
    }

    public enum TemplateKey { LAPTOP, CUSTOM }

    public enum DecisionStatus { DRAFT, CHOSEN, DEFERRED, ABANDONED, ARCHIVED }

    public enum CriterionKind { NUMERIC_UTILITY, RATING_1_TO_5 }

    public enum PreferenceDirection { HIGHER_BETTER, LOWER_BETTER, NOT_APPLICABLE }

    public enum ValueCertainty { CONFIRMED, ESTIMATED }

    public enum ConstraintKind { NUMERIC_MIN, NUMERIC_MAX, BOOLEAN_REQUIRED, MANUAL }

    public enum ConstraintStatus { PASS, FAIL, UNKNOWN }

    public enum ResolutionKind { CHOSEN, DEFERRED, ABANDONED }

    public enum ExpectationStatus { MET, PARTIALLY_MET, NOT_MET, TOO_EARLY }
}
