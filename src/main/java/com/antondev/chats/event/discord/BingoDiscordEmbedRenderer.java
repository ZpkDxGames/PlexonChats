package com.antondev.chats.event.discord;

import com.antondev.chats.event.bingo.BingoBoard;
import com.antondev.chats.event.bingo.BingoRenderer;
import com.antondev.chats.event.bingo.BingoRun;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Display-only Bingo embeds. Participant-owned Minecraft cards are never mirrored to shared Discord. */
public final class BingoDiscordEmbedRenderer {
    private BingoDiscordEmbedRenderer() { }

    public static DiscordEventEmbed lobby(BingoRun run, int seconds, String reward, DiscordEventSettings settings) {
        List<DiscordEventEmbed.Field> fields = new ArrayList<>();
        fields.add(DiscordEventEmbed.field("Starts in", Math.max(0, seconds) + "s", true));
        fields.add(DiscordEventEmbed.field("Participants", Integer.toString(run.participantCount()), true));
        fields.add(DiscordEventEmbed.field("Source", run.source().name(), true));
        if (settings.embeds().showReward() && reward != null && !reward.isBlank()) fields.add(DiscordEventEmbed.field("Reward", reward, false));
        fields.add(DiscordEventEmbed.field("How to join", "Use `/bingo join` in Minecraft.", false));
        return embed(settings, "BINGO — JOINING", "JOINING", DiscordEventEmbed.COLOR_LIVE, fields);
    }

    public static DiscordEventEmbed live(BingoRun run, String reward, DiscordEventSettings settings) {
        List<DiscordEventEmbed.Field> fields = new ArrayList<>();
        fields.add(DiscordEventEmbed.field("Players", Integer.toString(run.participantCount()), true));
        if (settings.bingo().showLastCall()) fields.add(DiscordEventEmbed.field("Last call", lastCall(run), true));
        if (settings.bingo().showDrawCount()) fields.add(DiscordEventEmbed.field("Draws", run.drawCount() + " / 75", true));
        if (settings.bingo().showPatterns()) fields.add(DiscordEventEmbed.field("Winning patterns", patterns(run), false));
        if (settings.embeds().showReward() && reward != null && !reward.isBlank()) fields.add(DiscordEventEmbed.field("Reward", reward, false));
        fields.add(DiscordEventEmbed.field("Authority", "Minecraft only • cards and marks stay private to participants", false));
        return embed(settings, "BINGO — LIVE", "LIVE", DiscordEventEmbed.COLOR_LIVE, fields);
    }

    public static DiscordEventEmbed winner(BingoRun run, BingoBoard.Win win, String winner, String reward,
                                           DiscordEventSettings settings) {
        List<DiscordEventEmbed.Field> fields = new ArrayList<>();
        fields.add(DiscordEventEmbed.field("Winner", winner, true));
        fields.add(DiscordEventEmbed.field("Pattern", win.pattern().displayName(), true));
        if (settings.bingo().showDrawCount()) fields.add(DiscordEventEmbed.field("Draws", run.drawCount() + " / 75", true));
        if (settings.bingo().showLastCall()) fields.add(DiscordEventEmbed.field("Winning call", lastCall(run), true));
        if (settings.embeds().showReward() && reward != null && !reward.isBlank()) fields.add(DiscordEventEmbed.field("Reward", reward, false));
        return embed(settings, "BINGO — WINNER", "WINNER", DiscordEventEmbed.COLOR_SUCCESS, fields);
    }

    public static DiscordEventEmbed terminal(BingoRun run, Terminal terminal, DiscordEventSettings settings) {
        List<DiscordEventEmbed.Field> fields = new ArrayList<>();
        fields.add(DiscordEventEmbed.field("Players", Integer.toString(run.participantCount()), true));
        if (settings.bingo().showLastCall()) fields.add(DiscordEventEmbed.field("Last call", lastCall(run), true));
        if (settings.bingo().showDrawCount()) fields.add(DiscordEventEmbed.field("Draws", run.drawCount() + " / 75", true));
        String title;
        String result;
        int color;
        switch (terminal) {
            case TIMED_OUT -> { title = "BINGO — TIMED OUT"; result = "No valid manual-mark claim was received before timeout."; color = DiscordEventEmbed.COLOR_TIMEOUT; }
            case CANCELLED -> { title = "BINGO — CANCELLED"; result = "The lobby or round ended without reward or statistic."; color = DiscordEventEmbed.COLOR_CANCELLED; }
            case EXHAUSTED -> { title = "BINGO — ENDED"; result = "All 75 calls were exhausted without a valid claim."; color = DiscordEventEmbed.COLOR_TIMEOUT; }
            default -> throw new IllegalStateException("Unhandled terminal state " + terminal);
        }
        fields.add(DiscordEventEmbed.field("Result", result, false));
        return embed(settings, title, terminal.name().replace('_', ' '), color, fields);
    }

    private static String patterns(BingoRun run) {
        return run.patterns().stream().map(value -> value.displayName()).sorted().collect(Collectors.joining(" • "));
    }
    private static String lastCall(BingoRun run) { return run.lastDraw() <= 0 ? "Not called yet" : BingoRenderer.label(run.lastDraw()); }
    private static DiscordEventEmbed embed(DiscordEventSettings settings, String title, String status, int color, List<DiscordEventEmbed.Field> fields) {
        String footer = settings.embeds().showFooter() ? "PlexonCraft • Chat Events • " + status : "";
        return new DiscordEventEmbed(title, "Discord is display-only; Minecraft remains authoritative.", color, fields,
                footer, settings.embeds().timestamp(), "");
    }

    public enum Terminal { TIMED_OUT, CANCELLED, EXHAUSTED }
}
