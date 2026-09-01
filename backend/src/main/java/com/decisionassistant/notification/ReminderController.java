package com.decisionassistant.notification;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ReminderController {
    private final ReminderService service;
    public ReminderController(ReminderService service) { this.service = service; }
    @GetMapping("/notification-preferences")
    public ReminderService.NotificationPreferenceResponse preference() { return service.preference(); }
    @PatchMapping("/notification-preferences")
    public ReminderService.NotificationPreferenceResponse updatePreference(
            @Valid @RequestBody ReminderService.UpdateNotificationPreferenceRequest request) {
        return service.updatePreference(request);
    }
    @PatchMapping("/decisions/{decisionId}/reminders/{reminderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateReminder(@PathVariable UUID decisionId, @PathVariable UUID reminderId,
                               @Valid @RequestBody ReminderService.UpdateReminderRequest request) {
        service.updateReminder(decisionId, reminderId, request);
    }
}
