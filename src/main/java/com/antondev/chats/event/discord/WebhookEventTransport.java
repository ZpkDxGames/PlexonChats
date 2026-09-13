package com.antondev.chats.event.discord;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Discord webhook transport with wait=true message IDs, PATCH editing and one bounded retry. */
public final class WebhookEventTransport implements DiscordEventTransport {
    private static final Pattern MESSAGE_ID = Pattern.compile("\\\"id\\\"\\s*:\\s*\\\"([0-9]+)\\\"");
    private final DiscordEventSettings settings;
    private final URI createEndpoint;
    private final String baseEndpoint;
    private final ThreadPoolExecutor executor;
    private final HttpClient client;
    private volatile boolean closed;

    public WebhookEventTransport(DiscordEventSettings settings) {
        this.settings = settings;
        String configured = settings.webhook().url();
        String rawBase = configured == null ? "" : configured.strip();
        int query = rawBase.indexOf('?');
        this.baseEndpoint = query >= 0 ? rawBase.substring(0, query) : rawBase;
        this.createEndpoint = URI.create(appendWait(rawBase));
        this.executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(16), runnable -> {
                    Thread thread = new Thread(runnable, "PlexonChats-Discord-Webhook");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        this.client = HttpClient.newBuilder().executor(executor).connectTimeout(Duration.ofSeconds(4)).build();
    }

    @Override public String name() { return "WEBHOOK"; }
    @Override public String status() {
        if (closed) return "CLOSED";
        if (!settings.webhook().enabled() || settings.webhook().url().isBlank()) return "NOT_CONFIGURED";
        return "READY";
    }
    @Override public boolean channelConfigured() { return !closed && settings.webhook().enabled() && !settings.webhook().url().isBlank(); }

    @Override public CompletableFuture<DiscordEventMessageRef> create(DiscordEventEmbed embed) {
        if (!channelConfigured()) return CompletableFuture.failedFuture(new IllegalStateException("Discord webhook is not configured"));
        HttpRequest request = HttpRequest.newBuilder(createEndpoint)
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload(embed, true)))
                .build();
        return execute(request, 0).thenCompose(response -> {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return CompletableFuture.failedFuture(new IllegalStateException("Discord webhook returned HTTP " + response.statusCode()));
            }
            Matcher matcher = MESSAGE_ID.matcher(response.body() == null ? "" : response.body());
            if (!matcher.find()) return CompletableFuture.failedFuture(new IllegalStateException("Discord webhook response did not include a message id"));
            return CompletableFuture.completedFuture(new DiscordEventMessageRef("webhook", matcher.group(1)));
        });
    }

    @Override public CompletableFuture<Void> edit(DiscordEventMessageRef message, DiscordEventEmbed embed) {
        if (!channelConfigured()) return CompletableFuture.failedFuture(new IllegalStateException("Discord webhook is not configured"));
        URI endpoint;
        try { endpoint = URI.create(baseEndpoint + "/messages/" + message.messageId()); }
        catch (IllegalArgumentException invalid) { return CompletableFuture.failedFuture(new IllegalStateException("Discord webhook configuration is invalid")); }
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(payload(embed, false)))
                .build();
        return execute(request, 0).thenCompose(response -> {
            if (response.statusCode() >= 200 && response.statusCode() < 300) return CompletableFuture.completedFuture(null);
            return CompletableFuture.failedFuture(new IllegalStateException("Discord webhook returned HTTP " + response.statusCode()));
        });
    }

    private CompletableFuture<HttpResponse<String>> execute(HttpRequest request, int attempt) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Discord webhook transport is closed"));
        CompletableFuture<HttpResponse<String>> result = new CompletableFuture<>();
        try {
            client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, failure) -> {
                if (failure != null) {
                    if (attempt == 0) retry(request, attempt + 1, 500, result);
                    else result.completeExceptionally(new IllegalStateException("Discord webhook request failed: " + failure.getClass().getSimpleName()));
                    return;
                }
                boolean retryable = response.statusCode() == 429 || response.statusCode() >= 500;
                if (retryable && attempt == 0) {
                    retry(request, attempt + 1, retryDelayMillis(response), result);
                    return;
                }
                result.complete(response);
            });
        } catch (RuntimeException failure) {
            result.completeExceptionally(new IllegalStateException("Discord webhook request could not be queued: " + failure.getClass().getSimpleName()));
        }
        return result;
    }

    private void retry(HttpRequest request, int attempt, long delayMillis, CompletableFuture<HttpResponse<String>> target) {
        try {
            executor.execute(() -> {
                try { Thread.sleep(Math.clamp(delayMillis, 250L, 5_000L)); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); target.completeExceptionally(interrupted); return; }
                execute(request, attempt).whenComplete((response, failure) -> {
                    if (failure != null) target.completeExceptionally(failure);
                    else target.complete(response);
                });
            });
        } catch (RuntimeException rejected) {
            target.completeExceptionally(new IllegalStateException("Discord webhook retry queue is full"));
        }
    }

    private static long retryDelayMillis(HttpResponse<?> response) {
        String value = response.headers().firstValue("Retry-After").orElse("0.5");
        try {
            double seconds = Double.parseDouble(value);
            return (long) (seconds * 1000.0);
        } catch (NumberFormatException ignored) { return 500; }
    }

    private String payload(DiscordEventEmbed embed, boolean includeUsername) {
        StringBuilder json = new StringBuilder(1024);
        json.append('{');
        if (includeUsername) json.append("\"username\":\"").append(json(settings.webhook().username())).append("\",");
        json.append("\"allowed_mentions\":{\"parse\":[]},\"embeds\":[{");
        json.append("\"title\":\"").append(json(embed.title())).append("\",");
        json.append("\"description\":\"").append(json(embed.description())).append("\",");
        json.append("\"color\":").append(embed.color());
        if (!embed.fields().isEmpty()) {
            json.append(",\"fields\":[");
            for (int i = 0; i < embed.fields().size(); i++) {
                if (i > 0) json.append(',');
                DiscordEventEmbed.Field field = embed.fields().get(i);
                json.append("{\"name\":\"").append(json(field.name())).append("\",\"value\":\"")
                        .append(json(field.value())).append("\",\"inline\":").append(field.inline()).append('}');
            }
            json.append(']');
        }
        if (!embed.footer().isBlank()) json.append(",\"footer\":{\"text\":\"").append(json(embed.footer())).append("\"}");
        if (embed.timestamp()) json.append(",\"timestamp\":\"").append(java.time.Instant.now()).append("\"");
        if (!embed.thumbnailUrl().isBlank()) json.append(",\"thumbnail\":{\"url\":\"").append(json(embed.thumbnailUrl())).append("\"}");
        json.append("}]}");
        return json.toString();
    }

    private static String appendWait(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) return "https://discord.invalid/";
        if (endpoint.contains("wait=true")) return endpoint;
        return endpoint + (endpoint.contains("?") ? "&" : "?") + "wait=true";
    }

    private static String json(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> { if (c < 0x20) out.append(String.format("\\u%04x", (int) c)); else out.append(c); }
            }
        }
        return out.toString();
    }

    @Override public void close() {
        closed = true;
        executor.shutdownNow();
    }
}
