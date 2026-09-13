package com.antondev.chats.config;

import com.antondev.chats.event.ChatEventValidation;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Adds new options without overwriting administrator-owned values or restoring deleted custom entries. */
public final class ConfigUpgrader {
    public static final int VERSION = 7;
    private static final Set<String> USER_COLLECTIONS = Set.of(
            "gui.items", "gui.admin.items", "gui.events.items", "gui.creator.items", "auto-messages.groups",
            "chat-events.events");

    private ConfigUpgrader() {}

    public static boolean upgrade(YamlConfiguration current, YamlConfiguration defaults) {
        int previousVersion = current.getInt("config-version", 1);
        boolean upgraded = previousVersion < VERSION;
        if (upgraded) {
            if (previousVersion < 7) migrateBingoV7(current);
            merge(current, defaults, "");
            // Preserve administrator-owned GUI collections. Add only cross-page entry points that are known-safe.
            copySectionIfMissing(current, defaults, "gui.admin.items.chat-events");
            current.set("config-version", VERSION);
        }
        ConfigurationSection chatEvents = current.getConfigurationSection("chat-events");
        if (chatEvents != null) ChatEventValidation.validate(chatEvents);
        return upgraded;
    }

    /** v7 replaces participant-specific Bingo with one shared, automatically marked, claim-based board. */
    private static void migrateBingoV7(YamlConfiguration current) {
        String root = "chat-events.bingo";
        if (current.isConfigurationSection(root)) {
            Set<String> legacyPatterns = new HashSet<>();
            for (String value : current.getStringList(root + ".win-patterns")) legacyPatterns.add(value.toUpperCase(Locale.ROOT));
            if (!legacyPatterns.isEmpty()) {
                current.set(root + ".winning.horizontal", legacyPatterns.contains("ROW") || legacyPatterns.contains("HORIZONTAL"));
                current.set(root + ".winning.vertical", legacyPatterns.contains("COLUMN") || legacyPatterns.contains("VERTICAL"));
                current.set(root + ".winning.diagonal", legacyPatterns.contains("DIAGONAL"));
                current.set(root + ".winning.full-house", legacyPatterns.contains("FULL_HOUSE"));
            }
            for (String obsolete : Set.of(
                    "join", "join-seconds", "min-participants", "board", "free-center", "draw-interval-seconds",
                    "draw.redraw-board-each-draw", "timeout-seconds", "win-patterns")) {
                current.set(root + "." + obsolete, null);
            }
            for (String obsoleteMessage : Set.of("join-open", "joined", "marked", "invalid-mark", "not-participant")) {
                current.set(root + ".messages." + obsoleteMessage, null);
            }
        }

        ConfigurationSection events = current.getConfigurationSection("chat-events.events");
        if (events == null) return;
        for (String id : events.getKeys(false)) {
            ConfigurationSection event = events.getConfigurationSection(id);
            if (event == null || !event.getString("type", "TYPE").equalsIgnoreCase("BINGO")) continue;
            ConfigurationSection legacyBingo = event.getConfigurationSection("bingo");
            if (!event.contains("duration-seconds") && legacyBingo != null && legacyBingo.contains("timeout-seconds")) {
                event.set("duration-seconds", legacyBingo.getInt("timeout-seconds", 300));
            }
            // Event identity, weight, cooldown, reward, minimum-online and other administrator values remain untouched.
            event.set("bingo", null);
        }
    }

    private static void merge(YamlConfiguration current, ConfigurationSection defaults, String parent) {
        for (String key : defaults.getKeys(false)) {
            String path = parent.isEmpty() ? key : parent + "." + key;
            ConfigurationSection child = defaults.getConfigurationSection(key);
            if (child != null) {
                if (USER_COLLECTIONS.contains(path)) {
                    if (!current.contains(path)) current.createSection(path);
                    continue;
                }
                if (current.contains(path) && !current.isConfigurationSection(path)) continue;
                if (!current.contains(path)) current.createSection(path);
                merge(current, child, path);
            } else if (!current.contains(path)) {
                current.set(path, defaults.get(key));
                current.setComments(path, defaults.getComments(key));
            }
        }
    }

    private static void copySectionIfMissing(YamlConfiguration current, ConfigurationSection defaults, String path) {
        if (current.contains(path)) return;
        ConfigurationSection source = defaults.getConfigurationSection(path);
        if (source == null) return;
        current.createSection(path);
        copyValues(current, source, path);
    }

    private static void copyValues(YamlConfiguration current, ConfigurationSection source, String targetPath) {
        for (String key : source.getKeys(false)) {
            String path = targetPath + "." + key;
            ConfigurationSection child = source.getConfigurationSection(key);
            if (child != null) {
                current.createSection(path);
                copyValues(current, child, path);
            } else {
                current.set(path, source.get(key));
                current.setComments(path, source.getComments(key));
            }
        }
    }
}
