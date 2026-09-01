package com.decisionassistant.preference;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/preferences")
public class PreferenceController {
    private final PreferenceService service;
    public PreferenceController(PreferenceService service) { this.service = service; }
    @GetMapping public List<PreferenceDtos.PreferenceResponse> list() { return service.list(); }
    @PatchMapping("/{preferenceId}") public PreferenceDtos.PreferenceResponse update(
            @PathVariable UUID preferenceId, @Valid @RequestBody PreferenceDtos.UpdatePreferenceRequest request) {
        return service.update(preferenceId, request);
    }
    @DeleteMapping("/{preferenceId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID preferenceId) { service.delete(preferenceId); }
}
