package com.decisionassistant.ai;

import com.decisionassistant.guidance.DiscoveryContracts.DiscoveryInput;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import tools.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class DiscoveryAdapterTest {
    @Test void providerFailureAndInvalidStructuredOutputEachMakeExactlyOneRequest() throws Exception {
        for (var status : List.of(429, 200)) {
            var calls = new AtomicInteger();
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                calls.incrementAndGet();
                exchange.getRequestBody().readAllBytes();
                var payload = (status == 429 ? "{\"error\":{\"message\":\"limit\",\"type\":\"rate_limit_error\"}}" : """
                        {"id":"test","object":"chat.completion","created":0,"model":"test",
                         "choices":[{"index":0,"message":{"role":"assistant","content":"invalid JSON"},"finish_reason":"stop"}],
                         "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                        """).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, payload.length); exchange.getResponseBody().write(payload); exchange.close();
            });
            server.start();
            try {
                var model = OpenAiChatModel.builder().options(OpenAiChatOptions.builder().apiKey("fixture")
                        .baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).model("test").maxRetries(0).build()).build();
                var adapter = new OpenAiDecisionAdapter(ChatClient.builder(model), new ObjectMapper(),
                        new AiProperties(true, "openai", "test", Duration.ofSeconds(3)), model, "fixture");
                assertThatThrownBy(() -> adapter.discover(new DiscoveryInput("노트북", null, null, null, List.of("가격"), List.of())))
                        .isInstanceOf(RuntimeException.class);
                assertThat(calls).hasValue(1);
            } finally { server.stop(0); }
        }
    }
}
