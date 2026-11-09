package com.decisionassistant.decision;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
    public DecisionPage list(
            @org.springframework.web.bind.annotation.RequestParam(required = false) java.util.Set<DecisionTypes.DecisionStatus> statuses,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(1000000) int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(50) int size,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "UPDATED_AT") ListSort sort,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "DESC") ListDirection direction) {
        return service.list(statuses, page, size, sort, direction);
    }

    @GetMapping("/counts")
    public List<StatusCount> counts() { return service.counts(); }

    @org.springframework.web.bind.annotation.DeleteMapping("/{decisionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID decisionId,
                       @org.springframework.web.bind.annotation.RequestParam @jakarta.validation.constraints.Min(0) long expectedVersion) {
        service.delete(decisionId, expectedVersion);
    }

    @GetMapping("/{decisionId}")
    public DecisionResponse get(@PathVariable UUID decisionId) {
        return service.get(decisionId);
    }

    @PostMapping("/{decisionId}/discovery/retry")
    public DecisionResponse retryDiscovery(@PathVariable UUID decisionId, @Valid @RequestBody RetryDiscoveryRequest request) {
        return service.retryDiscovery(decisionId, request);
    }

    @PostMapping("/{decisionId}/discovery/refresh")
    public DecisionResponse refreshDiscovery(@PathVariable UUID decisionId, @Valid @RequestBody RetryDiscoveryRequest request) {
        return service.refreshDiscovery(decisionId, request);
    }

    @PostMapping("/{decisionId}/sources")
    @ResponseStatus(HttpStatus.CREATED)
    public DecisionResponse addSource(@PathVariable UUID decisionId, @Valid @RequestBody AddSourceRequest request) {
        return service.addSource(decisionId, request);
    }

    @PostMapping("/{decisionId}/evidence/{evidenceId}/retry")
    public DecisionResponse retryEvidence(@PathVariable UUID decisionId, @PathVariable UUID evidenceId) {
        return service.retryEvidence(decisionId, evidenceId);
    }

    @PostMapping("/{decisionId}/questions/{questionId}/answer")
    public DecisionResponse answerQuestion(@PathVariable UUID decisionId, @PathVariable UUID questionId,
                                           @Valid @RequestBody AnswerQuestionRequest request) {
        return service.answerQuestion(decisionId, questionId, request);
    }

    @PatchMapping("/{decisionId}/candidate-suggestions/{suggestionId}")
    public DecisionResponse updateSuggestion(@PathVariable UUID decisionId, @PathVariable UUID suggestionId,
                                             @Valid @RequestBody UpdateSuggestionRequest request) {
        return service.updateSuggestion(decisionId, suggestionId, request);
    }

    @PatchMapping("/{decisionId}/values/{valueId}/verification")
    public DecisionResponse confirmValue(@PathVariable UUID decisionId, @PathVariable UUID valueId,
                                         @Valid @RequestBody ConfirmValueRequest request) {
        return service.confirmValue(decisionId, valueId, request);
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

    @PutMapping("/{decisionId}/comparison")
    public DecisionResponse comparison(@PathVariable UUID decisionId, @Valid @RequestBody Comparison.UpdateRequest request) {
        return service.updateComparison(decisionId, request);
    }

    @PutMapping("/{decisionId}/comparison/confirmation")
    public DecisionResponse confirmation(@PathVariable UUID decisionId, @Valid @RequestBody Comparison.ConfirmRequest request) {
        return service.confirmFeature(decisionId, request);
    }

    @PostMapping("/{decisionId}/outcome-check-ins")
    @ResponseStatus(HttpStatus.CREATED)
    public OutcomeData addOutcome(@PathVariable UUID decisionId,
                                  @Valid @RequestBody CreateOutcomeRequest request) {
        return service.addOutcome(decisionId, request);
    }
}
