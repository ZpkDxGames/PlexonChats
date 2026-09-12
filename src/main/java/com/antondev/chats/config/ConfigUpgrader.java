package com.antondev.chats.config;

import com.antondev.chats.event.ChatEventValidation;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.Set;

/** Adds new options without overwriting existing values or restoring deleted custom entries. */
public final class ConfigUpgrader {
    public static final int VERSION = 6;
    private static final Set<String> USER_COLLECTIONS = Set.of(
            "gui.items", "gui.admin.items", "gui.events.items", "gui.creator.items", "auto-messages.groups",
            "chat-events.events");
    private ConfigUpgrader() {}

    public static boolean upgrade(YamlConfiguration current, YamlConfiguration defaults) {
        boolean upgraded = current.getInt("config-version", 1) < VERSION;
        if (upgraded) {
            merge(current, defaults, "");
            // Preserve administrator-owned GUI collections. Add only cross-page entry points that are known-safe.
            copySectionIfMissing(current, defaults, "gui.admin.items.chat-events");
            current.set("config-version", VERSION);
        }
        // Production defaults always contain chat-events. Minimal merge-only fixtures intentionally may not.
        ConfigurationSection chatEvents = current.getConfigurationSection("chat-events");
        if (chatEvents != null) ChatEventValidation.validate(chatEvents);
        return upgraded;
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
