package com.decisionassistant.decision;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import static com.decisionassistant.decision.MemoryInsightContracts.*;

@RestController
@RequestMapping("/api/v1/decisions/{decisionId}/memories/{resolutionId}/insight")
public class MemoryInsightController {
    private final MemoryInsightService service;
    public MemoryInsightController(MemoryInsightService service) { this.service = service; }
    @GetMapping public InsightView get(@PathVariable UUID decisionId, @PathVariable UUID resolutionId) {
        return service.view(decisionId, resolutionId);
    }
    @PostMapping public InsightResponse generate(@PathVariable UUID decisionId, @PathVariable UUID resolutionId,
                                               @Valid @RequestBody GenerateRequest request) {
        return service.generate(decisionId, resolutionId, request.expectedInputHash());
    }
}
