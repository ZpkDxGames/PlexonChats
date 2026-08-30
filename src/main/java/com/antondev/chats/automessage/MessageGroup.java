package com.antondev.chats.automessage;

import com.antondev.chats.PlexonChats;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public record MessageGroup(String id, long intervalSeconds, long initialDelaySeconds, boolean shuffle,
                           int minOnline, boolean respectPreference, String permission, Set<String> worlds,
                           Set<String> excludedWorlds, String sound, float volume, float pitch,
                           boolean forwardToDiscord, List<Entry> entries) {
    public enum Delivery { CHAT, ACTION_BAR, TITLE }
    public record Entry(String id, Delivery delivery, List<String> lines, double fadeIn, double stay, double fadeOut) {}

    public static MessageGroup read(String id, ConfigurationSection section, Consumer<String> warning) {
        List<Entry> entries = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Map<?, ?> value : section.getMapList("messages")) {
            if (Boolean.FALSE.equals(value.get("enabled"))) continue;
            String entryId = String.valueOf(value.getOrDefault("id", null));
            if (entryId.equals("null") || entryId.isBlank()) entryId = "message-" + (entries.size() + 1);
            if (!ids.add(entryId)) { warning.accept("Duplicate message ID " + id + "/" + entryId); continue; }
            String deliveryName = value.containsKey("delivery") ? String.valueOf(value.get("delivery")) : "CHAT";
            Delivery delivery;
            try { delivery = Delivery.valueOf(deliveryName.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ex) { warning.accept("Unknown delivery in " + id + "/" + entryId); continue; }
            Object rawLines = value.get("lines");
            List<String> lines = rawLines instanceof List<?> list ? list.stream().map(String::valueOf).toList()
                    : rawLines instanceof String line ? List.of(line) : List.of();
            if (lines.isEmpty() || lines.stream().allMatch(String::isBlank)) { warning.accept("Empty message " + id + "/" + entryId + " skipped"); continue; }
            entries.add(new Entry(entryId, delivery, lines,
                    duration(value.get("fade-in-seconds"), .5), duration(value.get("stay-seconds"), 3), duration(value.get("fade-out-seconds"), .5)));
        }
        String order = section.getString("order", "SEQUENTIAL").toUpperCase(Locale.ROOT);
        if (!Set.of("SEQUENTIAL", "SHUFFLE").contains(order)) warning.accept("Unknown order in " + id + "; using SEQUENTIAL");
        return new MessageGroup(id, Math.clamp(section.getLong("interval-seconds", 300), 10, 604800),
                Math.clamp(section.getLong("initial-delay-seconds", 60), 1, 604800), order.equals("SHUFFLE"),
                Math.max(0, section.getInt("min-online", 1)), section.getBoolean("respect-tip-preference", true),
                section.getString("permission", ""), Set.copyOf(section.getStringList("worlds")),
                Set.copyOf(section.getStringList("excluded-worlds")), section.getString("sound", "NONE"),
                (float) duration(section.get("sound-volume"), .6), (float) Math.min(2, duration(section.get("sound-pitch"), 1.2)),
                section.getBoolean("forward-to-discord", false), List.copyOf(entries));
    }

    private static double duration(Object value, double fallback) {
        double number = value instanceof Number n ? n.doubleValue() : fallback;
        return Double.isFinite(number) ? Math.clamp(number, 0, 60) : fallback;
    }

    public boolean accepts(Player player, PlexonChats plugin) {
        return (permission.isBlank() || player.hasPermission(permission))
                && (worlds.isEmpty() || worlds.contains(player.getWorld().getName()))
                && !excludedWorlds.contains(player.getWorld().getName())
                && (!respectPreference || plugin.getPreferences().get(player.getUniqueId()).tips());
    }

    public boolean unrestricted() {
        return permission.isBlank() && worlds.isEmpty() && excludedWorlds.isEmpty() && !respectPreference;
    }
}
