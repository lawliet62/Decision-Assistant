package com.decisionassistant.evaluation;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static com.decisionassistant.evaluation.EvaluationDtos.*;

@RestController
@RequestMapping("/api/v1/decisions/{decisionId}/evaluations")
public class EvaluationController {

    private final EvaluationService service;

    public EvaluationController(EvaluationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EvaluationResponse create(@PathVariable UUID decisionId,
                                     @Valid @RequestBody CreateEvaluationRequest request) {
        return service.create(decisionId, request);
    }

    @GetMapping("/{evaluationId}")
    public EvaluationResponse get(@PathVariable UUID decisionId, @PathVariable UUID evaluationId) {
        return service.get(decisionId, evaluationId);
    }

    @PostMapping("/{evaluationId}/llm-retries")
    public EvaluationResponse retry(@PathVariable UUID decisionId, @PathVariable UUID evaluationId,
                                    @Valid @RequestBody RetryLlmRequest request) {
        return service.retry(decisionId, evaluationId, request);
    }
}
