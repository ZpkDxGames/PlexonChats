package com.antondev.chats;

import com.antondev.chats.event.ChatEventEngine;
import com.antondev.chats.event.discord.ChatEventDiscordPublisher;
import com.antondev.chats.event.discord.DiscordEventEmbed;
import com.antondev.chats.event.discord.DiscordEventMessageRef;
import com.antondev.chats.event.discord.DiscordEventSettings;
import com.antondev.chats.event.discord.DiscordEventTransport;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DiscordEventPublisherTest extends PluginTestBase {
    @Test void oneEventRunCreatesOneMessageAndEditsItThroughTerminalState() throws Exception {
        FakeTransport transport = new FakeTransport();
        try (ChatEventDiscordPublisher publisher = new ChatEventDiscordPublisher(plugin, settings(true), transport)) {
            UUID run = UUID.randomUUID();
            publisher.start(run, embed("LIVE"));
            await(() -> "ACTIVE".equals(publisher.messageState(run)));
            assertEquals(1, transport.creates.get());

            publisher.update(run, embed("DRAW 1"));
            await(() -> transport.edits.size() >= 1);
            publisher.terminal(run, embed("WINNER"));
            await(() -> "TERMINAL".equals(publisher.messageState(run)));

            assertEquals(1, transport.creates.get(), "one event run must own one Discord message");
            assertEquals(List.of("DRAW 1", "WINNER"), transport.edits.stream().map(value -> value.embed.title()).toList());
            assertTrue(transport.edits.stream().allMatch(value -> value.ref.messageId().equals("1001")));
        }
    }

    @Test void delayedLiveEditCannotOverwriteTerminalStateAndLaterLiveUpdatesAreIgnored() throws Exception {
        BlockingTransport transport = new BlockingTransport();
        try (ChatEventDiscordPublisher publisher = new ChatEventDiscordPublisher(plugin, settings(true), transport)) {
            UUID run = UUID.randomUUID();
            publisher.start(run, embed("LIVE"));
            await(() -> transport.creates.get() == 1);

            publisher.update(run, embed("DRAW 25"));
            await(() -> transport.firstEditStarted != null);
            publisher.terminal(run, embed("WINNER"));
            publisher.update(run, embed("STALE DRAW"));
            transport.releaseFirstEdit.complete(null);

            await(() -> "TERMINAL".equals(publisher.messageState(run)));
            assertEquals("WINNER", transport.edits.getLast().embed.title());
            assertFalse(transport.edits.stream().anyMatch(value -> value.embed.title().equals("STALE DRAW")));
        }
    }

    @Test void disabledPublisherMakesNoTransportCalls() throws Exception {
        FakeTransport transport = new FakeTransport();
        try (ChatEventDiscordPublisher publisher = new ChatEventDiscordPublisher(plugin, settings(false), transport)) {
            UUID run = UUID.randomUUID();
            publisher.start(run, embed("LIVE"));
            publisher.update(run, embed("UPDATE"));
            publisher.terminal(run, embed("WINNER"));
            Thread.sleep(100);
            assertEquals(0, transport.creates.get());
            assertTrue(transport.edits.isEmpty());
        }
    }

    @Test void failedDiscordCreateIsIsolatedAndDoesNotEscapeCaller() throws Exception {
        FakeTransport transport = new FakeTransport();
        transport.failCreate = true;
        try (ChatEventDiscordPublisher publisher = new ChatEventDiscordPublisher(plugin, settings(true), transport)) {
            UUID run = UUID.randomUUID();
            assertDoesNotThrow(() -> publisher.start(run, embed("LIVE")));
            await(() -> transport.creates.get() >= 1);
            assertNotEquals("TERMINAL", publisher.messageState(run));
        }
    }

    private DiscordEventSettings settings(boolean enabled) {
        EnumMap<ChatEventEngine.Type, Boolean> events = new EnumMap<>(ChatEventEngine.Type.class);
        for (ChatEventEngine.Type type : ChatEventEngine.Type.values()) events.put(type, true);
        return new DiscordEventSettings(enabled, DiscordEventSettings.TransportMode.WEBHOOK, "",
                DiscordEventSettings.ParticipationMode.DISPLAY_ONLY,
                new DiscordEventSettings.Webhook(true, "https://discord.example.invalid/api/webhooks/1/secret", "Tests"),
                new DiscordEventSettings.Embeds(true, true, false, true, true),
                new DiscordEventSettings.Updates(true, 250), events,
                new DiscordEventSettings.Bingo(true, true, true, true, true, true));
    }

    private static DiscordEventEmbed embed(String title) {
        return new DiscordEventEmbed(title, "", DiscordEventEmbed.COLOR_LIVE, List.of(), "", false, "");
    }

    private static void await(Check check) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            if (check.done()) return;
            Thread.sleep(10);
        }
        fail("Timed out waiting for asynchronous publisher state");
    }

    @FunctionalInterface private interface Check { boolean done(); }
    private record Edit(DiscordEventMessageRef ref, DiscordEventEmbed embed) { }

    private static class FakeTransport implements DiscordEventTransport {
        final AtomicInteger creates = new AtomicInteger();
        final List<Edit> edits = java.util.Collections.synchronizedList(new ArrayList<>());
        volatile boolean failCreate;

        @Override public String name() { return "FAKE"; }
        @Override public String status() { return "READY"; }
        @Override public boolean channelConfigured() { return true; }
        @Override public CompletableFuture<DiscordEventMessageRef> create(DiscordEventEmbed embed) {
            int id = creates.incrementAndGet();
            if (failCreate) return CompletableFuture.failedFuture(new IllegalStateException("simulated Discord failure"));
            return CompletableFuture.completedFuture(new DiscordEventMessageRef("channel", Integer.toString(1000 + id)));
        }
        @Override public CompletableFuture<Void> edit(DiscordEventMessageRef message, DiscordEventEmbed embed) {
            edits.add(new Edit(message, embed));
            return CompletableFuture.completedFuture(null);
        }
        @Override public void close() { }
    }

    private static final class BlockingTransport extends FakeTransport {
        volatile CompletableFuture<Void> firstEditStarted;
        final CompletableFuture<Void> releaseFirstEdit = new CompletableFuture<>();

        @Override public CompletableFuture<Void> edit(DiscordEventMessageRef message, DiscordEventEmbed embed) {
            edits.add(new Edit(message, embed));
            if (firstEditStarted == null) {
                firstEditStarted = releaseFirstEdit;
                return releaseFirstEdit;
            }
            return CompletableFuture.completedFuture(null);
        }
    }
}
