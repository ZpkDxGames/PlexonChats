package com.antondev.chats.event.discord;

import com.antondev.chats.event.ChatEventConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shared embed presentation for TYPE/UNSCRAMBLE/MATH/TRIVIA/REVERSE events. */
public final class StandardEventDiscordEmbedRenderer {
    private StandardEventDiscordEmbedRenderer() { }

    public static DiscordEventEmbed live(ChatEventConfig.Definition definition, String typeName, String prompt,
                                         String reward, DiscordEventSettings settings) {
        List<DiscordEventEmbed.Field> fields = new ArrayList<>();
        fields.add(DiscordEventEmbed.field(challengeLabel(definition), safe(prompt, "Chat challenge"), false));
        if (settings.embeds().showReward() && reward != null && !reward.isBlank()) {
            fields.add(DiscordEventEmbed.field("Reward", reward, true));
        }
        fields.add(DiscordEventEmbed.field("How to win", "First correct Minecraft answer wins.", false));
        return embed(settings, "CHAT EVENT • " + displayType(definition, typeName), "LIVE", DiscordEventEmbed.COLOR_LIVE,
                fields, "", "");
    }

    public static DiscordEventEmbed winner(ChatEventConfig.Definition definition, String typeName, String winner,
                                           String answer, String reward, String elapsed, String avatarUrl,
                                           DiscordEventSettings settings) {
        List<DiscordEventEmbed.Field> fields = new ArrayList<>();
        fields.add(DiscordEventEmbed.field("Winner", safe(winner, "Unknown"), true));
        if (answer != null && !answer.isBlank()) fields.add(DiscordEventEmbed.field("Answer", answer, true));
        if (elapsed != null && !elapsed.isBlank()) fields.add(DiscordEventEmbed.field("Completion time", elapsed, true));
        if (settings.embeds().showReward() && reward != null && !reward.isBlank()) {
            fields.add(DiscordEventEmbed.field("Reward", reward, false));
        }
        return embed(settings, "CHAT EVENT • COMPLETED", displayType(definition, typeName), DiscordEventEmbed.COLOR_SUCCESS,
                fields, "", settings.embeds().showWinnerAvatar() ? safe(avatarUrl, "") : "");
    }

    public static DiscordEventEmbed timeout(ChatEventConfig.Definition definition, String typeName, String answer,
                                            boolean revealAnswer, DiscordEventSettings settings) {
        List<DiscordEventEmbed.Field> fields = new ArrayList<>();
        if (revealAnswer && answer != null && !answer.isBlank()) fields.add(DiscordEventEmbed.field("Correct Answer", answer, false));
        fields.add(DiscordEventEmbed.field("Result", "No player answered in time.", false));
        return embed(settings, "CHAT EVENT • EXPIRED", displayType(definition, typeName), DiscordEventEmbed.COLOR_TIMEOUT,
                fields, "", "");
    }

    public static DiscordEventEmbed cancelled(ChatEventConfig.Definition definition, String typeName,
                                              DiscordEventSettings settings) {
        return embed(settings, "CHAT EVENT • CANCELLED", displayType(definition, typeName), DiscordEventEmbed.COLOR_CANCELLED,
                List.of(DiscordEventEmbed.field("Result", "The event was stopped by an administrator.", false)), "", "");
    }

    private static DiscordEventEmbed embed(DiscordEventSettings settings, String title, String status, int color,
                                           List<DiscordEventEmbed.Field> fields, String description, String thumbnail) {
        String footer = settings.embeds().showFooter() ? "PlexonCraft • Chat Events • " + status : "";
        return new DiscordEventEmbed(title, description, color, fields, footer, settings.embeds().timestamp(), thumbnail);
    }

    private static String displayType(ChatEventConfig.Definition definition, String typeName) {
        if (typeName != null && !typeName.isBlank()) return typeName.toUpperCase(Locale.ROOT);
        return definition.type().name();
    }

    private static String challengeLabel(ChatEventConfig.Definition definition) {
        return switch (definition.type()) {
            case MATH -> "Solve";
            case UNSCRAMBLE -> "Unscramble";
            case TRIVIA -> "Question";
            case REVERSE -> "Reverse";
            case TYPE -> "Type";
            case BINGO -> "Challenge";
        };
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
