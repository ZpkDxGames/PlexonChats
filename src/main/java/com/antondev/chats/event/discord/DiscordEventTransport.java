package com.antondev.chats.event.discord;

import java.util.concurrent.CompletableFuture;

/** Asynchronous create/edit boundary used by the Chat Event Discord publisher. */
public interface DiscordEventTransport extends AutoCloseable {
    String name();
    String status();
    boolean channelConfigured();
    CompletableFuture<DiscordEventMessageRef> create(DiscordEventEmbed embed);
    CompletableFuture<Void> edit(DiscordEventMessageRef message, DiscordEventEmbed embed);
    @Override void close();

    static DiscordEventTransport unavailable(String reason) {
        return new DiscordEventTransport() {
            @Override public String name() { return "NONE"; }
            @Override public String status() { return reason; }
            @Override public boolean channelConfigured() { return false; }
            @Override public CompletableFuture<DiscordEventMessageRef> create(DiscordEventEmbed embed) {
                return CompletableFuture.failedFuture(new IllegalStateException(reason));
            }
            @Override public CompletableFuture<Void> edit(DiscordEventMessageRef message, DiscordEventEmbed embed) {
                return CompletableFuture.failedFuture(new IllegalStateException(reason));
            }
            @Override public void close() { }
        };
    }
}
