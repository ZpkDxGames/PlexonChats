package com.antondev.chats.event.discord;

import com.antondev.chats.event.ChatEventEngine;
import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/** Immutable Discord presentation configuration for Chat Events. */
public record DiscordEventSettings(
        boolean enabled,
        TransportMode transport,
        String channelId,
        ParticipationMode participationMode,
        Webhook webhook,
        Embeds embeds,
        Updates updates,
        Map<ChatEventEngine.Type, Boolean> eventEnabled,
        Bingo bingo) {

    public DiscordEventSettings {
        channelId = channelId == null ? "" : channelId.strip();
        eventEnabled = Map.copyOf(eventEnabled);
    }

    public static DiscordEventSettings read(ConfigurationSection chatEvents) {
        ConfigurationSection root = chatEvents == null ? null : chatEvents.getConfigurationSection("discord");
        boolean enabled = bool(root, "enabled", false);
        TransportMode mode = enumValue(TransportMode.class, string(root, "transport", "AUTO"), TransportMode.AUTO);
        ParticipationMode participation = enumValue(ParticipationMode.class,
                string(root, "participation-mode", "DISPLAY_ONLY"), ParticipationMode.DISPLAY_ONLY);
        Webhook webhook = new Webhook(
                bool(root, "webhook.enabled", false),
                string(root, "webhook.url", ""),
                string(root, "webhook.username", "PlexonChats Events"));
        Embeds embeds = new Embeds(
                bool(root, "embeds.timestamp", true),
                bool(root, "embeds.show-reward", true),
                bool(root, "embeds.show-winner-avatar", false),
                bool(root, "embeds.show-event-type", true),
                bool(root, "embeds.show-footer", true));
        Updates updates = new Updates(
                bool(root, "updates.edit-existing-message", true),
                integer(root, "updates.minimum-edit-interval-ms", 1000));
        EnumMap<ChatEventEngine.Type, Boolean> eventEnabled = new EnumMap<>(ChatEventEngine.Type.class);
        for (ChatEventEngine.Type type : ChatEventEngine.Type.values()) {
            String key = type.name().toLowerCase(Locale.ROOT);
            eventEnabled.put(type, bool(root, "events." + key + ".enabled", true));
        }
        Bingo bingo = new Bingo(
                bool(root, "events.bingo.show-live-board", true),
                bool(root, "events.bingo.update-on-draw", true),
                bool(root, "events.bingo.show-last-call", true),
                bool(root, "events.bingo.show-draw-count", true),
                bool(root, "events.bingo.show-patterns", true),
                bool(root, "events.bingo.announce-winner", true));
        return new DiscordEventSettings(enabled, mode, string(root, "channel-id", ""), participation,
                webhook, embeds, updates, eventEnabled, bingo);
    }

    public boolean publishes(ChatEventEngine.Type type) {
        return enabled && eventEnabled.getOrDefault(type, true);
    }

    public enum TransportMode { AUTO, DISCORDSRV, WEBHOOK }
    public enum ParticipationMode { DISPLAY_ONLY, CROSS_PLATFORM }
    public record Webhook(boolean enabled, String url, String username) {
        public Webhook {
            url = url == null ? "" : url.strip();
            username = username == null || username.isBlank() ? "PlexonChats Events" : username.strip();
        }
    }
    public record Embeds(boolean timestamp, boolean showReward, boolean showWinnerAvatar,
                         boolean showEventType, boolean showFooter) { }
    public record Updates(boolean editExistingMessage, int minimumEditIntervalMs) {
        public Updates { minimumEditIntervalMs = Math.clamp(minimumEditIntervalMs, 250, 30_000); }
    }
    public record Bingo(boolean showLiveBoard, boolean updateOnDraw, boolean showLastCall,
                        boolean showDrawCount, boolean showPatterns, boolean announceWinner) { }

    private static boolean bool(ConfigurationSection section, String path, boolean fallback) {
        return section == null ? fallback : section.getBoolean(path, fallback);
    }
    private static int integer(ConfigurationSection section, String path, int fallback) {
        return section == null ? fallback : section.getInt(path, fallback);
    }
    private static String string(ConfigurationSection section, String path, String fallback) {
        return section == null ? fallback : section.getString(path, fallback);
    }
    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        try { return Enum.valueOf(type, value.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException | NullPointerException ignored) { return fallback; }
    }
}
