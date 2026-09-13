package com.antondev.chats.event.bingo;

import com.antondev.chats.event.ChatEventConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/** Optional Bingo-only Discord webhook transport. Network I/O never runs on the Paper main thread. */
public final class BingoDiscordWebhook implements AutoCloseable {
    private final Logger logger;
    private final ThreadPoolExecutor executor;
    private final HttpClient client;

    public BingoDiscordWebhook(Logger logger) {
        this.logger = logger;
        this.executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(32), runnable -> {
                    Thread thread = new Thread(runnable, "PlexonChats-Bingo-Webhook");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.DiscardPolicy());
        this.client = HttpClient.newBuilder()
                .executor(executor)
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public void send(ChatEventConfig.BingoDiscord settings, String heading, BingoRun run) {
        if (settings == null || !settings.enabled() || settings.webhookUrl().isBlank()) return;
        final URI endpoint;
        try { endpoint = URI.create(settings.webhookUrl()); }
        catch (IllegalArgumentException invalid) {
            logger.warning("Bingo Discord webhook configuration is invalid; delivery skipped.");
            return;
        }
        String content = "**" + heading + "**\n" + BingoAnsiRenderer.render(run);
        String body = "{\"username\":\"" + json(settings.username()) + "\",\"content\":\"" + json(content) + "\"}";
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            client.sendAsync(request, HttpResponse.BodyHandlers.discarding()).whenComplete((response, failure) -> {
                if (failure != null) logger.warning("Bingo Discord webhook delivery failed: " + failure.getClass().getSimpleName());
                else if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    logger.warning("Bingo Discord webhook delivery returned HTTP " + response.statusCode() + '.');
                }
            });
        } catch (RuntimeException failure) {
            logger.warning("Bingo Discord webhook delivery could not be queued: " + failure.getClass().getSimpleName());
        }
    }

    private static String json(String value) {
        StringBuilder out = new StringBuilder(value.length() + 32);
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }

    @Override public void close() { executor.shutdownNow(); }
}
