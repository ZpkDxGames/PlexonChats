package com.antondev.chats;

import com.antondev.chats.event.ChatEventConfig;
import com.antondev.chats.event.ChatEventEngine;
import com.antondev.chats.event.ChatEventManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ChatEventTest extends PluginTestBase {
    @Test void defaultConfigurationLoadsAllRequiredTypesAndOneCoordinator() {
        assertTrue(plugin.getChatEvents().enabled());
        assertTrue(plugin.getChatEvents().schedulerEnabled());
        assertTrue(plugin.getChatEvents().taskActive());
        assertEquals(6, plugin.getChatEvents().configuredCount());
        ChatEventConfig snapshot = ChatEventConfig.read(plugin.getConfigManager().section("chat-events"));
        assertEquals(Set.of(ChatEventEngine.Type.TYPE, ChatEventEngine.Type.UNSCRAMBLE, ChatEventEngine.Type.MATH,
                        ChatEventEngine.Type.TRIVIA, ChatEventEngine.Type.REVERSE),
                snapshot.definitions().values().stream().map(ChatEventConfig.Definition::type).collect(java.util.stream.Collectors.toSet()));
    }

    @Test void matchingDefaultsAreDeterministicAndDataOnly() {
        ChatEventConfig.Matching defaults = ChatEventConfig.Matching.standard();
        assertEquals("bingo", ChatEventEngine.normalize("  BINGO  ", defaults));
        assertEquals("hello world", ChatEventEngine.normalize("Hello   World", defaults));
        assertEquals("<red>bingo</red>", ChatEventEngine.normalize("<red>BINGO</red>", defaults));
        var sensitive = new ChatEventConfig.Matching(true, true, true, java.text.Normalizer.Form.NFKC, false);
        assertNotEquals(ChatEventEngine.normalize("BINGO", sensitive), ChatEventEngine.normalize("bingo", sensitive));
    }

    @Test void allGeneratorsProduceBoundedPlayableRounds() {
        ChatEventConfig config = ChatEventConfig.read(plugin.getConfigManager().section("chat-events"));
        java.util.Random random = new java.util.Random(42);
        for (ChatEventConfig.Definition definition : config.definitions().values()) {
            ChatEventEngine.Round round = ChatEventEngine.generators().get(definition.type()).generate(definition, random);
            assertEquals(definition, round.definition());
            assertFalse(round.acceptedAnswers().isEmpty());
            assertTrue(round.acceptedAnswers().stream().noneMatch(String::isBlank));
            if (definition.type() == ChatEventEngine.Type.UNSCRAMBLE) {
                assertFalse(round.promptValues().get("scrambled").isBlank());
            }
            if (definition.type() == ChatEventEngine.Type.MATH) {
                assertNotNull(round.promptValues().get("expression"));
                assertDoesNotThrow(() -> Long.parseLong(round.canonicalAnswer()));
            }
        }
    }

    @Test void exactOnceWinnerTransitionSurvivesNearSimultaneousCorrectAnswers() throws Exception {
        ChatEventConfig config = ChatEventConfig.read(plugin.getConfigManager().section("chat-events"));
        ChatEventConfig.Definition definition = config.definitions().get("bingo");
        ChatEventEngine.Round round = new ChatEventEngine.Round(UUID.randomUUID(), definition, java.util.Map.of("value", "bingo"), List.of("bingo"));
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        ChatEventEngine.Competition competition = new ChatEventEngine.Competition(round, System.nanoTime(), System.nanoTime() + 1_000_000_000L, Set.of(first, second));
        assertTrue(competition.activate());
        AtomicInteger winners = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            executor.submit(() -> attempt(competition, first, ready, go, winners));
            executor.submit(() -> attempt(competition, second, ready, go, winners));
            assertTrue(ready.await(2, TimeUnit.SECONDS));
            go.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        }
        assertEquals(1, winners.get());
        assertEquals(ChatEventEngine.State.WON, competition.state());
        assertNotNull(competition.winner());
        assertTrue(competition.beginReward());
        assertFalse(competition.beginReward(), "Reward bundle may be attempted only once");
    }

    @Test void commandStylePublicSendCannotWinButNativeRouteCan() throws Exception {
        var player = player("Winner");
        player.addAttachment(plugin, "plexonchats.local", true);
        player.addAttachment(plugin, "plexonchats.global", true);
        config(yaml -> {
            yaml.set("chat.cooldown-milliseconds", 0);
            yaml.set("chat.duplicate-window-seconds", 0);
            yaml.set("chat-events.scheduler.enabled", false);
            yaml.set("chat-events.events.bingo.values", List.of("bingo"));
            yaml.set("chat-events.events.bingo.cooldown-seconds", 0);
            yaml.set("chat-events.reward-profiles.basic.economy.enabled", false);
        });
        assertEquals(ChatEventManager.StartStatus.STARTED, plugin.getChatEvents().start("bingo"));
        plugin.getChatManager().sendPublic(player, ChatChannel.LOCAL, "bingo");
        assertTrue(plugin.getChatEvents().hasActiveEvent(), "Command/synthetic sendPublic must not count as an event answer");
        plugin.getChatManager().route(player, "bingo", null);
        assertFalse(plugin.getChatEvents().hasActiveEvent(), "Accepted native public chat should complete the event");
        assertTrue(plugin.getChatEvents().lastEvent().endsWith("/WON"));
    }

    @Test void schedulerMayBeDisabledWhileManualEventsRemainPlayable() throws Exception {
        var player = player("Participant");
        config(yaml -> {
            yaml.set("chat-events.scheduler.enabled", false);
            yaml.set("chat-events.events.bingo.values", List.of("bingo"));
            yaml.set("chat-events.events.bingo.cooldown-seconds", 0);
            yaml.set("chat-events.reward-profiles.basic.economy.enabled", false);
        });
        assertTrue(plugin.getChatEvents().enabled());
        assertFalse(plugin.getChatEvents().schedulerEnabled());
        assertTrue(plugin.getChatEvents().taskActive(), "One coordinator remains available for manual event timeout lifecycle");
        assertEquals(ChatEventManager.StartStatus.STARTED, plugin.getChatEvents().start("bingo"));
        assertTrue(plugin.getChatEvents().stop());
        assertTrue(plugin.getChatEvents().lastEvent().endsWith("/CANCELLED"));
    }

    @Test void masterDisablePreventsManualAndAutomaticStarts() throws Exception {
        config(yaml -> yaml.set("chat-events.enabled", false));
        assertFalse(plugin.getChatEvents().enabled());
        assertFalse(plugin.getChatEvents().taskActive());
        assertEquals(ChatEventManager.StartStatus.DISABLED, plugin.getChatEvents().start("bingo"));
        assertEquals(ChatEventManager.StartStatus.DISABLED, plugin.getChatEvents().startRandom(false));
    }

    @Test void invalidEventCandidateIsRejectedAndPriorRuntimeSnapshotSurvives() throws Exception {
        long revision = plugin.getConfigManager().revision();
        int definitions = plugin.getChatEvents().configuredCount();
        var file = plugin.getDataFolder().toPath().resolve("config.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        yaml.set("chat-events.events.bingo.type", "NOT_A_REAL_EVENT");
        yaml.save(file.toFile());
        assertFalse(plugin.reloadPlugin());
        assertEquals(revision, plugin.getConfigManager().revision());
        assertEquals(definitions, plugin.getChatEvents().configuredCount());
        assertTrue(plugin.getChatEvents().enabled());
    }

    @Test void timeoutAndCancellationCannotCreateWinner() {
        ChatEventConfig config = ChatEventConfig.read(plugin.getConfigManager().section("chat-events"));
        ChatEventConfig.Definition definition = config.definitions().get("bingo");
        UUID player = UUID.randomUUID();
        var round = new ChatEventEngine.Round(UUID.randomUUID(), definition, java.util.Map.of("value", "bingo"), List.of("bingo"));
        var timeout = new ChatEventEngine.Competition(round, 1, 2, Set.of(player));
        assertTrue(timeout.activate());
        assertTrue(timeout.timeout());
        assertFalse(timeout.tryWin(player, "Late", "bingo"));
        assertNull(timeout.winner());
        var cancelled = new ChatEventEngine.Competition(round, 1, 2, Set.of(player));
        assertTrue(cancelled.activate());
        assertTrue(cancelled.cancel());
        assertFalse(cancelled.tryWin(player, "Late", "bingo"));
        assertNull(cancelled.winner());
    }

    private static void attempt(ChatEventEngine.Competition competition, UUID player, CountDownLatch ready, CountDownLatch go, AtomicInteger winners) {
        ready.countDown();
        try {
            go.await();
            if (competition.tryWin(player, player.toString(), "bingo")) winners.incrementAndGet();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
