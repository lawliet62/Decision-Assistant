package com.decisionassistant.decision;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static com.decisionassistant.decision.DecisionDtos.*;

@RestController
@RequestMapping("/api/v1/decisions")
public class DecisionController {

    private final DecisionService service;

    public DecisionController(DecisionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a Decision from a versioned template")
    public DecisionResponse create(@Valid @RequestBody CreateDecisionRequest request) {
        return service.create(request);
    }

    @GetMapping
    public List<DecisionSummary> list() {
        return service.list();
    }

    @GetMapping("/{decisionId}")
    public DecisionResponse get(@PathVariable UUID decisionId) {
        return service.get(decisionId);
    }

    @PutMapping("/{decisionId}/draft")
    public DecisionResponse update(@PathVariable UUID decisionId,
                                   @Valid @RequestBody UpdateDraftRequest request) {
        return service.update(decisionId, request);
    }

    @PostMapping("/{decisionId}/resolutions")
    @ResponseStatus(HttpStatus.CREATED)
    public ResolutionData resolve(@PathVariable UUID decisionId,
                                  @Valid @RequestBody CreateResolutionRequest request) {
        return service.resolve(decisionId, request);
    }

    @PostMapping("/{decisionId}/outcome-check-ins")
    @ResponseStatus(HttpStatus.CREATED)
    public OutcomeData addOutcome(@PathVariable UUID decisionId,
                                  @Valid @RequestBody CreateOutcomeRequest request) {
        return service.addOutcome(decisionId, request);
    }
}
