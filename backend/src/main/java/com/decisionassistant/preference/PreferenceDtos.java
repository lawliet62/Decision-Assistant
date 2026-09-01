package com.decisionassistant.preference;

import com.decisionassistant.decision.DecisionTypes;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public final class PreferenceDtos {
    private PreferenceDtos() {}
    public record PreferenceResponse(UUID id, DecisionTypes.TemplateKey templateKey, String criterionName,
                                     String statement, int strength, String status, String confidence,
                                     UUID sourceDecisionId) {}
    public record UpdatePreferenceRequest(@NotBlank String action, String statement,
                                          @Min(1) @Max(3) Integer strength) {}
}
