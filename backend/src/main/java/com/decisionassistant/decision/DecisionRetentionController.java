package com.decisionassistant.decision;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/decisions")
public class DecisionRetentionController {
    private final DecisionRetentionService service;
    private final java.time.Instant restoredTo;
    public DecisionRetentionController(DecisionRetentionService service,
            @org.springframework.beans.factory.annotation.Value("${decision-assistant.recovery.restored-to:}") String restoredTo) {
        this.service = service;
        this.restoredTo = restoredTo.isBlank() ? null : java.time.Instant.parse(restoredTo);
    }
    public record RecoveryNotice(java.time.Instant restoredTo) {}
    @GetMapping("/recovery-notice")
    public RecoveryNotice recoveryNotice() {
        return new RecoveryNotice(restoredTo);
    }
    @GetMapping("/management")
    public DecisionRetentionService.RetentionPage list(@RequestParam DecisionRetentionService.RetentionView view,
            @RequestParam(defaultValue = "0") @jakarta.validation.constraints.Min(0)
            @jakarta.validation.constraints.Max(1000000) int page) { return service.list(view, page); }
    @PatchMapping("/{decisionId}/retention")
    public DecisionRetentionService.RetentionItem change(@PathVariable UUID decisionId,
            @Valid @RequestBody DecisionRetentionService.RetentionChange request) { return service.change(decisionId, request); }
}
