package com.antondev.chats;

import com.antondev.chats.event.ChatEventConfig;
import com.antondev.chats.event.ChatEventEngine;
import com.antondev.chats.event.bingo.BingoRenderer;
import com.antondev.chats.event.bingo.BingoRun;
import com.antondev.chats.event.discord.BingoDiscordEmbedRenderer;
import com.antondev.chats.event.discord.DiscordEventEmbed;
import com.antondev.chats.event.discord.DiscordEventSettings;
import com.antondev.chats.event.discord.StandardEventDiscordEmbedRenderer;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class DiscordEventRendererTest extends PluginTestBase {
    @Test void allImplementedStandardEventTypesRenderStartWinnerTimeoutAndCancel() {
        ChatEventConfig config = ChatEventConfig.read(plugin.getConfigManager().section("chat-events"));
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
            assertTrue(win.fields().stream().anyMatch(field -> field.name().equals("Winner") && field.value().equals("Winner")));
            assertEquals("CHAT EVENT • EXPIRED", timeout.title());
            assertEquals("CHAT EVENT • CANCELLED", cancel.title());
        }
        assertTrue(config.definitions().values().stream().map(ChatEventConfig.Definition::type).toList().containsAll(
                List.of(ChatEventEngine.Type.TYPE, ChatEventEngine.Type.UNSCRAMBLE, ChatEventEngine.Type.MATH,
                        ChatEventEngine.Type.TRIVIA, ChatEventEngine.Type.REVERSE, ChatEventEngine.Type.BINGO)));
    }

    @Test void bingoRendererUsesExactAuthoritativeBoardAndNeverShowsFreeCenter() {
        ChatEventConfig config = ChatEventConfig.read(plugin.getConfigManager().section("chat-events"));
        ChatEventConfig.Definition definition = config.definitions().get("bingo-classic");
        assertNotNull(definition);
        BingoRun run = new BingoRun(UUID.randomUUID(), definition, 1, 1_000_000, 1, 1,
                definition.bingo().winPatterns(), RandomGenerator.getDefault());
        assertTrue(run.activate());

        Set<Integer> boardValues = java.util.stream.IntStream.range(0, 25)
                .map(run.board()::numberAt).boxed().collect(Collectors.toSet());
        Integer drawnBoardValue = null;
        int calls = 0;
        for (long now = 1; now <= 75 && drawnBoardValue == null; now++) {
            Integer drawn = run.draw(now);
            assertNotNull(drawn);
            calls++;
            if (boardValues.contains(drawn)) drawnBoardValue = drawn;
        }
        assertNotNull(drawnBoardValue, "a complete 1-75 draw pool must eventually call a value on the 25-cell board");

        DiscordEventEmbed live = BingoDiscordEmbedRenderer.live(run, "$5,000 + 1 EPIC key", settings());
        String board = field(live, "Board");
        assertFalse(board.toUpperCase(Locale.ROOT).contains("FREE"));
        for (int cell = 0; cell < 25; cell++) {
            String value = String.format(Locale.ROOT, "%02d", run.board().numberAt(cell));
            assertTrue(board.contains(value), "Discord board must contain authoritative cell " + cell + "=" + value);
        }
        assertTrue(board.contains("[" + String.format(Locale.ROOT, "%02d", drawnBoardValue) + "]"),
                "drawn board state must come from the same BingoRun");
        assertEquals(calls + " / 75", field(live, "Draws"));
        assertEquals(BingoRenderer.label(run.lastDraw()), field(live, "Last Call"));
    }

    @Test void bingoWinnerEmbedHighlightsWinningCellsFromAuthoritativeClaim() {
        ChatEventConfig config = ChatEventConfig.read(plugin.getConfigManager().section("chat-events"));
        ChatEventConfig.Definition definition = config.definitions().get("bingo-classic");
        BingoRun run = new BingoRun(UUID.randomUUID(), definition, 1, 10_000, 1, 1,
                definition.bingo().winPatterns(), RandomGenerator.getDefault());
        assertTrue(run.activate());
        for (int i = 1; i <= 75; i++) assertNotNull(run.draw(i));
        BingoRun.ClaimResult claim = run.claim(UUID.randomUUID(), "Winner", true);
        assertEquals(BingoRun.ClaimStatus.WON, claim.status());
        assertNotNull(claim.win());

        DiscordEventEmbed winner = BingoDiscordEmbedRenderer.winner(run, claim.win(), "Winner", "$5,000 + 1 EPIC key", settings());
        assertEquals("BINGO • WINNER", winner.title());
        assertEquals("Winner", field(winner, "Winner"));
        assertEquals("75 / 75", field(winner, "Draws"));
        assertTrue(field(winner, "Final Board").contains("{"), "winning cells should be visually distinguished");
    }

    private static String field(DiscordEventEmbed embed, String name) {
        return embed.fields().stream().filter(field -> field.name().equals(name)).findFirst().orElseThrow().value();
    }

    private DiscordEventSettings settings() {
        EnumMap<ChatEventEngine.Type, Boolean> events = new EnumMap<>(ChatEventEngine.Type.class);
        for (ChatEventEngine.Type type : ChatEventEngine.Type.values()) events.put(type, true);
        return new DiscordEventSettings(true, DiscordEventSettings.TransportMode.AUTO, "",
                DiscordEventSettings.ParticipationMode.DISPLAY_ONLY,
                new DiscordEventSettings.Webhook(false, "", "PlexonChats Events"),
                new DiscordEventSettings.Embeds(true, true, false, true, true),
                new DiscordEventSettings.Updates(true, 1000), events,
                new DiscordEventSettings.Bingo(true, true, true, true, true, true));
    }
}
