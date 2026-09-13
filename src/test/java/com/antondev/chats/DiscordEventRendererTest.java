package com.antondev.chats;

import com.antondev.chats.event.ChatEventConfig;
import com.antondev.chats.event.ChatEventEngine;
import com.antondev.chats.event.bingo.BingoParticipant;
import com.antondev.chats.event.bingo.BingoPattern;
import com.antondev.chats.event.bingo.BingoRun;
import com.antondev.chats.event.discord.BingoDiscordEmbedRenderer;
import com.antondev.chats.event.discord.DiscordEventEmbed;
import com.antondev.chats.event.discord.DiscordEventSettings;
import com.antondev.chats.event.discord.StandardEventDiscordEmbedRenderer;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DiscordEventRendererTest extends PluginTestBase {
    @Test void allImplementedStandardEventTypesStillRenderLifecycleEmbeds() {
        ChatEventConfig config = runtimeConfig();
        DiscordEventSettings settings = settings();
        for (ChatEventConfig.Definition definition : config.definitions().values()) {
            if (definition.type() == ChatEventEngine.Type.BINGO) continue;
            DiscordEventEmbed live = StandardEventDiscordEmbedRenderer.live(definition, definition.name(), "Prompt value", "$1,000", settings);
            DiscordEventEmbed win = StandardEventDiscordEmbedRenderer.winner(definition, definition.name(), "Winner", "answer", "$1,000", "1.250s", "", settings);
            DiscordEventEmbed timeout = StandardEventDiscordEmbedRenderer.timeout(definition, definition.name(), "answer", true, settings);
            DiscordEventEmbed cancel = StandardEventDiscordEmbedRenderer.cancelled(definition, definition.name(), settings);
            assertTrue(live.title().startsWith("CHAT EVENT"));
            assertTrue(live.fields().stream().anyMatch(field -> field.value().contains("Prompt value")));
            assertEquals("CHAT EVENT • COMPLETED", win.title());
            assertEquals("CHAT EVENT • EXPIRED", timeout.title());
            assertEquals("CHAT EVENT • CANCELLED", cancel.title());
        }
    }

    @Test void bingoDiscordIsDisplayOnlyAndNeverExposesParticipantCards() {
        ChatEventConfig.Definition definition = runtimeConfig().definitions().get("bingo-classic");
        BingoRun run = run(definition);
        UUID player = UUID.randomUUID();
        assertEquals(BingoRun.JoinStatus.JOINED, run.join(player, "Player", 0, true).status());
        DiscordEventEmbed lobby = BingoDiscordEmbedRenderer.lobby(run, 60, "$5,000 + key", settings());
        assertEquals("BINGO — JOINING", lobby.title());
        assertEquals("1", field(lobby, "Participants"));
        assertNoCardField(lobby);

        assertEquals(BingoRun.ActivationStatus.ACTIVATED, run.activate(0, ignored -> true));
        assertNotNull(run.draw(0));
        DiscordEventEmbed live = BingoDiscordEmbedRenderer.live(run, "$5,000 + key", settings());
        assertEquals("BINGO — LIVE", live.title());
        assertEquals("1", field(live, "Players"));
        assertEquals("1 / 75", field(live, "Draws"));
        assertNoCardField(live);
        assertTrue(field(live, "Authority").contains("cards and marks stay private"));
    }

    @Test void winnerEmbedCarriesWinnerPatternAndDrawCountButNoCard() {
        ChatEventConfig.Definition definition = runtimeConfig().definitions().get("bingo-classic");
        BingoRun run = run(definition);
        UUID playerId = UUID.randomUUID();
        assertEquals(BingoRun.JoinStatus.JOINED, run.join(playerId, "Winner", 0, true).status());
        assertEquals(BingoRun.ActivationStatus.ACTIVATED, run.activate(0, ignored -> true));
        long now = 0;
        while (run.remainingCount() > 0) assertNotNull(run.draw(now++));
        BingoParticipant participant = run.participant(playerId);
        for (int cell = 0; cell < 5; cell++) assertEquals(BingoRun.MarkStatus.MARKED,
                run.mark(playerId, run.runId(), participant.board().numberAt(cell)).status());
        BingoRun.ClaimResult claim = run.claim(playerId, "Winner", run.runId(), true);
        assertEquals(BingoRun.ClaimStatus.WON, claim.status());
        assertEquals(BingoPattern.ROW, claim.win().pattern());

        DiscordEventEmbed winner = BingoDiscordEmbedRenderer.winner(run, claim.win(), "Winner", "$5,000 + key", settings());
        assertEquals("BINGO — WINNER", winner.title());
        assertEquals("Winner", field(winner, "Winner"));
        assertEquals("75 / 75", field(winner, "Draws"));
        assertNoCardField(winner);
    }

    private ChatEventConfig runtimeConfig() {
        return ChatEventConfig.read(plugin.getConfigManager().section("chat-events"), plugin.getChatEventData().root());
    }

    private static BingoRun run(ChatEventConfig.Definition definition) {
        ChatEventConfig.BingoSettings settings = definition.bingo();
        return new BingoRun(UUID.randomUUID(), definition, BingoRun.Source.ADMIN, 0, 0, 1,
                600_000_000_000L, 0, 1, settings.winPatterns(), settings.lobby().remindersSeconds(),
                false, true, false, new java.util.Random(42));
    }

    private static void assertNoCardField(DiscordEventEmbed embed) {
        assertTrue(embed.fields().stream().noneMatch(field -> field.name().equalsIgnoreCase("Board") || field.name().equalsIgnoreCase("Final Board")));
    }
    private static String field(DiscordEventEmbed embed, String name) { return embed.fields().stream().filter(field -> field.name().equals(name)).findFirst().orElseThrow().value(); }

    private DiscordEventSettings settings() {
        EnumMap<ChatEventEngine.Type, Boolean> events = new EnumMap<>(ChatEventEngine.Type.class);
        for (ChatEventEngine.Type type : ChatEventEngine.Type.values()) events.put(type, true);
        return new DiscordEventSettings(true, DiscordEventSettings.TransportMode.AUTO, "", DiscordEventSettings.ParticipationMode.DISPLAY_ONLY,
                new DiscordEventSettings.Webhook(false, "", "PlexonChats Events"), new DiscordEventSettings.Embeds(true, true, false, true, true),
                new DiscordEventSettings.Updates(true, 1000), events, new DiscordEventSettings.Bingo(false, true, true, true, true, true));
    }
}
