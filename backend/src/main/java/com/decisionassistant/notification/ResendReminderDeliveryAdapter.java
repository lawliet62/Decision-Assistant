package com.decisionassistant.notification;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

@Component
public class ResendReminderDeliveryAdapter implements ReminderDeliveryPort {
    private final ReminderProperties properties;
    private final RestClient client;
    public ResendReminderDeliveryAdapter(ReminderProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.client = builder.baseUrl(properties.resendBaseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.resendApiKey()).build();
    }
    @Override public String send(UUID reminderId, String email, String decisionTitle, String link) {
        if (!properties.configured()) throw new IllegalStateException("이메일 reminder가 비활성화되어 있습니다.");
        var html = "<p>선택한 제품이 실제로 잘 맞았는지 짧게 돌아볼 시간입니다.</p>"
                + "<p><a href=\"" + link + "\">Decision Assistant에서 회고하기</a></p>";
        JsonNode response = client.post().uri("/emails").contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "outcome-reminder/" + reminderId)
                .body(Map.of("from", properties.fromAddress(), "to", email,
                        "subject", "선택은 실제로 잘 맞았나요?", "html", html))
                .retrieve().body(JsonNode.class);
        return response == null ? null : response.path("id").asText(null);
    }
}
