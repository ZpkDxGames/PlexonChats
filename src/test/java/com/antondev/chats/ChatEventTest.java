package com.antondev.chats;

import com.antondev.chats.event.ChatEventConfig;
import com.antondev.chats.event.ChatEventEngine;
import com.antondev.chats.event.ChatEventManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

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
        assertEquals(7, plugin.getChatEvents().configuredCount());
        ChatEventConfig snapshot = runtimeConfig();
        assertEquals(Set.of(ChatEventEngine.Type.TYPE, ChatEventEngine.Type.UNSCRAMBLE, ChatEventEngine.Type.MATH,
                        ChatEventEngine.Type.TRIVIA, ChatEventEngine.Type.REVERSE, ChatEventEngine.Type.BINGO),
                snapshot.definitions().values().stream().map(ChatEventConfig.Definition::type).collect(java.util.stream.Collectors.toSet()));
        assertTrue(snapshot.definitions().containsKey("type-rush"));
        assertTrue(snapshot.definitions().containsKey("bingo-classic"));
    }

    @Test void matchingDefaultsAreDeterministicAndDataOnly() {
        ChatEventConfig.Matching defaults = ChatEventConfig.Matching.standard();
        assertEquals("bingo", ChatEventEngine.normalize("  BINGO  ", defaults));
        assertEquals("hello world", ChatEventEngine.normalize("Hello   World", defaults));
        assertEquals("<red>bingo</red>", ChatEventEngine.normalize("<red>BINGO</red>", defaults));
        var sensitive = new ChatEventConfig.Matching(true, true, true, java.text.Normalizer.Form.NFKC, false);
        assertNotEquals(ChatEventEngine.normalize("BINGO", sensitive), ChatEventEngine.normalize("bingo", sensitive));
    }

    @Test void ordinaryGeneratorsProducePlayableRoundsWhileBingoUsesDedicatedStateMachine() {
        ChatEventConfig config = runtimeConfig();
        java.util.Random random = new java.util.Random(42);
        for (ChatEventConfig.Definition definition : config.definitions().values()) {
            if (definition.type() == ChatEventEngine.Type.BINGO) {
                assertNull(ChatEventEngine.generators().get(ChatEventEngine.Type.BINGO));
                assertTrue(definition.bingo().enabled());
                continue;
            }
            ChatEventEngine.Generator generator = ChatEventEngine.generators().get(definition.type());
            assertNotNull(generator, definition.type().name());
            ChatEventEngine.Round round = generator.generate(definition, random);
            assertFalse(round.acceptedAnswers().isEmpty());
            assertTrue(round.acceptedAnswers().stream().noneMatch(String::isBlank));
        }
    }

    @Test void exactOnceWinnerTransitionSurvivesNearSimultaneousCorrectAnswers() throws Exception {
        ChatEventConfig.Definition definition = typeRush();
        ChatEventEngine.Round round = new ChatEventEngine.Round(UUID.randomUUID(), definition, java.util.Map.of("value", "bingo"), List.of("bingo"));
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        ChatEventEngine.Competition competition = new ChatEventEngine.Competition(round, System.nanoTime(), System.nanoTime() + 1_000_000_000L, Set.of(first, second));
        assertTrue(competition.activate());
        AtomicInteger winners = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(2), go = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            executor.submit(() -> attempt(competition, first, ready, go, winners));
            executor.submit(() -> attempt(competition, second, ready, go, winners));
            assertTrue(ready.await(2, TimeUnit.SECONDS));
            go.countDown(); executor.shutdown(); assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        }
        assertEquals(1, winners.get());
        assertEquals(ChatEventEngine.State.WON, competition.state());
        assertTrue(competition.beginReward()); assertFalse(competition.beginReward());
    }

    @Test void commandStylePublicSendCannotWinButNativeRouteCan() throws Exception {
        var player = player("Winner");
        player.addAttachment(plugin, "plexonchats.local", true);
        player.addAttachment(plugin, "plexonchats.global", true);
        config(yaml -> {
            yaml.set("chat.cooldown-milliseconds", 0);
            yaml.set("chat.duplicate-window-seconds", 0);
            yaml.set("chat-events.reward-profiles.basic.economy.enabled", false);
        });
        data(yaml -> {
            yaml.set("scheduler.enabled", false);
            yaml.set("minigames.type-rush.values", List.of("bingo"));
            yaml.set("minigames.type-rush.cooldown-seconds", 0);
        });
        assertEquals(ChatEventManager.StartStatus.STARTED, plugin.getChatEvents().start("type-rush"));
        plugin.getChatManager().sendPublic(player, ChatChannel.LOCAL, "bingo");
        assertTrue(plugin.getChatEvents().hasActiveEvent());
        plugin.getChatManager().route(player, "bingo", null);
        assertFalse(plugin.getChatEvents().hasActiveEvent());
        assertTrue(plugin.getChatEvents().lastEvent().endsWith("/WON"));
    }

    @Test void schedulerMayBeDisabledWhileManualEventsRemainPlayable() throws Exception {
        player("Participant");
        config(yaml -> yaml.set("chat-events.reward-profiles.basic.economy.enabled", false));
        data(yaml -> {
            yaml.set("scheduler.enabled", false);
            yaml.set("minigames.type-rush.values", List.of("bingo"));
            yaml.set("minigames.type-rush.cooldown-seconds", 0);
        });
        assertTrue(plugin.getChatEvents().enabled());
        assertFalse(plugin.getChatEvents().schedulerEnabled());
        assertTrue(plugin.getChatEvents().taskActive());
        assertEquals(ChatEventManager.StartStatus.STARTED, plugin.getChatEvents().start("type-rush"));
        assertTrue(plugin.getChatEvents().stop());
        assertTrue(plugin.getChatEvents().lastEvent().endsWith("/CANCELLED"));
    }

    @Test void v4DataLibrarySurvivesEmptyLegacyEventCollection() throws Exception {
        player("ParticipantOne"); player("ParticipantTwo");
        config(yaml -> {
            yaml.set("config-version", 9);
            yaml.set("chat-events.events", null);
            yaml.createSection("chat-events.events");
        });
        assertEquals(7, plugin.getChatEvents().configuredCount());
        assertEquals(ChatEventManager.StartStatus.STARTED, plugin.getChatEvents().start("unscramble"));
        assertTrue(plugin.getChatEvents().stop());
        assertEquals(ChatEventManager.StartStatus.STARTED, plugin.getChatEvents().startBingo());
        assertEquals("LOBBY", plugin.getChatEvents().bingoPhase());
        assertTrue(plugin.getChatEvents().stopBingo());
    }

    @Test void masterDisableStillLivesInConfigAndPreventsAllStarts() throws Exception {
        config(yaml -> yaml.set("chat-events.enabled", false));
        assertFalse(plugin.getChatEvents().enabled());
        assertFalse(plugin.getChatEvents().taskActive());
        assertEquals(ChatEventManager.StartStatus.DISABLED, plugin.getChatEvents().start("type-rush"));
        assertEquals(ChatEventManager.StartStatus.DISABLED, plugin.getChatEvents().startRandom(false));
    }

    @Test void malformedSingleDataMinigameIsQuarantinedWithoutCrashingPlugin() throws Exception {
        int before = plugin.getChatEvents().configuredCount();
        var file = plugin.getDataFolder().toPath().resolve("data.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        yaml.set("minigames.type-rush.type", "NOT_A_REAL_EVENT");
        yaml.save(file.toFile());
        assertTrue(plugin.reloadPlugin());
        assertEquals(before - 1, plugin.getChatEvents().configuredCount());
        assertNull(plugin.getChatEvents().definition("type-rush"));
        assertNotNull(plugin.getChatEvents().definition("unscramble"));
    }

    @Test void timeoutAndCancellationCannotCreateWinner() {
        ChatEventConfig.Definition definition = typeRush();
        UUID player = UUID.randomUUID();
        var round = new ChatEventEngine.Round(UUID.randomUUID(), definition, java.util.Map.of("value", "bingo"), List.of("bingo"));
        var timeout = new ChatEventEngine.Competition(round, 1, 2, Set.of(player));
        assertTrue(timeout.activate()); assertTrue(timeout.timeout()); assertFalse(timeout.tryWin(player, "Late", "bingo")); assertNull(timeout.winner());
        var cancelled = new ChatEventEngine.Competition(round, 1, 2, Set.of(player));
        assertTrue(cancelled.activate()); assertTrue(cancelled.cancel()); assertFalse(cancelled.tryWin(player, "Late", "bingo")); assertNull(cancelled.winner());
    }

    private ChatEventConfig runtimeConfig() { return ChatEventConfig.read(plugin.getConfigManager().section("chat-events"), plugin.getChatEventData().root()); }
    private ChatEventConfig.Definition typeRush() { ChatEventConfig.Definition definition = runtimeConfig().definitions().get("type-rush"); assertNotNull(definition); return definition; }
    private static void attempt(ChatEventEngine.Competition competition, UUID player, CountDownLatch ready, CountDownLatch go, AtomicInteger winners) {
        ready.countDown();
        try { go.await(); if (competition.tryWin(player, player.toString(), "bingo")) winners.incrementAndGet(); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
    }
}
