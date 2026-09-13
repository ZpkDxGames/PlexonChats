package com.antondev.chats.event.discord;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class DiscordEventTransportFactoryTest {
    @Test void autoPrefersReadyDiscordSrvWithoutConstructingWebhook() {
        FakeTransport discord = new FakeTransport("DiscordSRV", "READY", true);
        AtomicBoolean fallbackRequested = new AtomicBoolean();
        DiscordEventTransport selected = DiscordEventTransportFactory.selectAuto(discord, true, () -> {
            fallbackRequested.set(true);
            return new FakeTransport("WEBHOOK", "READY", true);
        });

        assertSame(discord, selected);
        assertFalse(fallbackRequested.get());
        assertFalse(discord.closed);
    }

    @Test void autoKeepsDiscordSrvWhileConnectionIsStarting() {
        FakeTransport discord = new FakeTransport("DiscordSRV", "WAITING_FOR_DISCORD", false);
        DiscordEventTransport selected = DiscordEventTransportFactory.selectAuto(discord, true,
                () -> new FakeTransport("WEBHOOK", "READY", true));

        assertSame(discord, selected);
        assertFalse(discord.closed);
    }

    @Test void autoFallsBackToWebhookWhenDiscordSrvIsUnavailable() {
        FakeTransport discord = new FakeTransport("NONE", "NOT_INSTALLED", false);
        FakeTransport webhook = new FakeTransport("WEBHOOK", "READY", true);
        DiscordEventTransport selected = DiscordEventTransportFactory.selectAuto(discord, true, () -> webhook);

        assertSame(webhook, selected);
        assertTrue(discord.closed, "discarded DiscordSRV candidate must be closed");
    }

    @Test void autoReturnsCleanUnavailableTransportWhenNeitherTransportExists() {
        FakeTransport discord = new FakeTransport("NONE", "NOT_INSTALLED", false);
        DiscordEventTransport selected = assertDoesNotThrow(() ->
                DiscordEventTransportFactory.selectAuto(discord, false,
                        () -> { throw new AssertionError("webhook fallback must not be constructed"); }));

        assertEquals("NONE", selected.name());
        assertFalse(selected.channelConfigured());
        assertTrue(selected.status().contains("webhook is not configured"));
        assertTrue(discord.closed);
    }

    @Test void invalidWebhookFallbackIsContainedAsUnavailableTransport() {
        FakeTransport discord = new FakeTransport("NONE", "INCOMPATIBLE", false);
        DiscordEventTransport selected = assertDoesNotThrow(() ->
                DiscordEventTransportFactory.selectAuto(discord, true,
                        () -> { throw new IllegalArgumentException("bad webhook"); }));

        assertEquals("NONE", selected.name());
        assertTrue(selected.status().contains("invalid"));
    }

    private static final class FakeTransport implements DiscordEventTransport {
        private final String name;
        private final String status;
        private final boolean configured;
        private boolean closed;

        private FakeTransport(String name, String status, boolean configured) {
            this.name = name;
            this.status = status;
            this.configured = configured;
        }

        @Override public String name() { return name; }
        @Override public String status() { return status; }
        @Override public boolean channelConfigured() { return configured; }
        @Override public CompletableFuture<DiscordEventMessageRef> create(DiscordEventEmbed embed) {
            return CompletableFuture.completedFuture(new DiscordEventMessageRef("channel", "message"));
        }
        @Override public CompletableFuture<Void> edit(DiscordEventMessageRef message, DiscordEventEmbed embed) {
            return CompletableFuture.completedFuture(null);
        }
        @Override public void close() { closed = true; }
    }
}
