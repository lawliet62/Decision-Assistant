package com.decisionassistant.guidance;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** 호출자는 승인된 고정 URL만 전달한다. 본문 완료까지 시간과 크기를 제한한다. */
final class SpecificationPageFetcher {
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final int MAX_BYTES = 2_000_000;
    private SpecificationPageFetcher() {}

    static String fetch(URI url) throws Exception { return fetch(url, TIMEOUT, MAX_BYTES); }

    static String fetch(URI url, Duration timeout, int maxBytes) throws Exception {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
        var request = HttpRequest.newBuilder(url).timeout(timeout).GET().build();
        var pending = client.sendAsync(request, info -> {
            if (info.statusCode() != 200) throw new IllegalStateException("SOURCE_UNAVAILABLE");
            return new LimitedBody(maxBytes);
        });
        try {
            return new String(pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS).body(), StandardCharsets.UTF_8);
        } catch (TimeoutException e) {
            pending.cancel(true);
            throw e;
        } catch (InterruptedException e) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final int limit;
        private Flow.Subscription subscription;
        LimitedBody(int limit) { this.limit = limit; }
        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (var buffer : buffers) {
                if (buffer.remaining() > limit - bytes.size()) {
                    subscription.cancel(); result.completeExceptionally(new IllegalStateException("SOURCE_TOO_LARGE")); return;
                }
                var chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { result.completeExceptionally(error); }
        @Override public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
