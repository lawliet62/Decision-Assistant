package com.decisionassistant.ai;

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

import static com.decisionassistant.ai.AiDtos.*;

@RestController
@RequestMapping("/api/v1")
public class AiController {

    private final StructureService service;

    public AiController(StructureService service) {
        this.service = service;
    }

    @GetMapping("/ai/capabilities")
    public AiCapabilitiesResponse capabilities() {
        return service.capabilities();
    }

    @PostMapping("/decisions/{decisionId}/structure-proposals")
    @ResponseStatus(HttpStatus.CREATED)
    public StructureRunResponse create(@PathVariable UUID decisionId,
                                       @Valid @RequestBody CreateStructureProposalRequest request) {
        return service.create(decisionId, request);
    }
}
