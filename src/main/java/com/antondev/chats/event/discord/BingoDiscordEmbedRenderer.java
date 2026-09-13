package com.antondev.chats.event.discord;

import com.antondev.chats.event.bingo.BingoBoard;
import com.antondev.chats.event.bingo.BingoRenderer;
import com.antondev.chats.event.bingo.BingoRun;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Renders the same authoritative BingoRun board used by Minecraft; never generates Discord-only game state. */
public final class BingoDiscordEmbedRenderer {
    private BingoDiscordEmbedRenderer() { }

    public static DiscordEventEmbed live(BingoRun run, String reward, DiscordEventSettings settings) {
        List<DiscordEventEmbed.Field> fields = common(run, null, reward, settings);
        fields.add(DiscordEventEmbed.field("How to win", "Complete a valid pattern and type `bingo` in Minecraft.", false));
        return embed(settings, "BINGO • LIVE BOARD", "LIVE", DiscordEventEmbed.COLOR_LIVE, fields);
    }

    public static DiscordEventEmbed winner(BingoRun run, BingoBoard.Win win, String winner, String reward,
                                           DiscordEventSettings settings) {
        List<DiscordEventEmbed.Field> fields = new ArrayList<>();
        if (settings.bingo().showLiveBoard()) fields.add(DiscordEventEmbed.field("Final Board", board(run, win), false));
        fields.add(DiscordEventEmbed.field("Winner", winner, true));
        fields.add(DiscordEventEmbed.field("Winning Pattern", win.pattern().displayName(), true));
        if (settings.bingo().showDrawCount()) fields.add(DiscordEventEmbed.field("Draws", run.drawCount() + " / 75", true));
        if (settings.bingo().showLastCall()) fields.add(DiscordEventEmbed.field("Winning Call", lastCall(run), true));
        if (settings.embeds().showReward() && reward != null && !reward.isBlank()) fields.add(DiscordEventEmbed.field("Reward", reward, false));
        return embed(settings, "BINGO • WINNER", "WINNER", DiscordEventEmbed.COLOR_SUCCESS, fields);
    }

    public static DiscordEventEmbed terminal(BingoRun run, Terminal terminal, DiscordEventSettings settings) {
        List<DiscordEventEmbed.Field> fields = common(run, null, "", settings);
        String title;
        String result;
        int color;
        switch (terminal) {
            case TIMED_OUT -> { title = "BINGO • TIMED OUT"; result = "No valid claim was received before the event ended."; color = DiscordEventEmbed.COLOR_TIMEOUT; }
            case CANCELLED -> { title = "BINGO • CANCELLED"; result = "The event was stopped by an administrator."; color = DiscordEventEmbed.COLOR_CANCELLED; }
            case EXHAUSTED -> { title = "BINGO • ENDED"; result = "All available numbers were exhausted without a valid claim."; color = DiscordEventEmbed.COLOR_TIMEOUT; }
            default -> throw new IllegalStateException("Unhandled Bingo terminal state " + terminal);
        }
        fields.add(DiscordEventEmbed.field("Result", result, false));
        return embed(settings, title, terminal.name().replace('_', ' '), color, fields);
    }

    private static List<DiscordEventEmbed.Field> common(BingoRun run, BingoBoard.Win win, String reward,
                                                        DiscordEventSettings settings) {
        List<DiscordEventEmbed.Field> fields = new ArrayList<>();
        if (settings.bingo().showLiveBoard()) fields.add(DiscordEventEmbed.field("Board", board(run, win), false));
        if (settings.bingo().showLastCall()) fields.add(DiscordEventEmbed.field("Last Call", lastCall(run), true));
        if (settings.bingo().showDrawCount()) fields.add(DiscordEventEmbed.field("Draws", run.drawCount() + " / 75", true));
        if (settings.bingo().showPatterns()) {
            String patterns = run.patterns().stream().map(value -> value.displayName()).sorted().collect(Collectors.joining(" • "));
            fields.add(DiscordEventEmbed.field("Winning Patterns", patterns, false));
        }
        if (settings.embeds().showReward() && reward != null && !reward.isBlank()) fields.add(DiscordEventEmbed.field("Reward", reward, false));
        return fields;
    }

    static String board(BingoRun run, BingoBoard.Win win) {
        StringBuilder out = new StringBuilder("```text\n    B     I     N     G     O\n");
        Set<Integer> drawn = run.drawnNumbers();
        Set<Integer> winning = win == null ? Set.of() : win.cells();
        for (int row = 0; row < 5; row++) {
            for (int column = 0; column < 5; column++) {
                int cell = row * 5 + column;
                int number = run.board().numberAt(cell);
                String value = String.format(Locale.ROOT, "%02d", number);
                if (winning.contains(cell)) out.append('{').append(value).append('}');
                else if (drawn.contains(number)) out.append('[').append(value).append(']');
                else out.append(' ').append(value).append(' ');
                if (column < 4) out.append("  ");
            }
            out.append('\n');
        }
        out.append("```\n`[ ]` called • `{ }` winning line");
        return out.toString();
    }

    private static String lastCall(BingoRun run) {
        return run.lastDraw() <= 0 ? "Not called yet" : BingoRenderer.label(run.lastDraw());
    }

    private static DiscordEventEmbed embed(DiscordEventSettings settings, String title, String status, int color,
                                           List<DiscordEventEmbed.Field> fields) {
        String footer = settings.embeds().showFooter() ? "PlexonCraft • Chat Events • " + status : "";
        return new DiscordEventEmbed(title, "Minecraft is authoritative for this event.", color, fields,
                footer, settings.embeds().timestamp(), "");
    }

    public enum Terminal { TIMED_OUT, CANCELLED, EXHAUSTED }
}
