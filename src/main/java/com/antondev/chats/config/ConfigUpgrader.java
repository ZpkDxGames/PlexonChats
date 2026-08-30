package com.antondev.chats.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.Set;

/** Adds new options without overwriting existing values or restoring deleted custom entries. */
public final class ConfigUpgrader {
    public static final int VERSION = 2;
    private static final Set<String> USER_COLLECTIONS = Set.of(
            "gui.items", "gui.admin.items", "gui.creator.items", "auto-messages.groups");
    private ConfigUpgrader() {}

    public static boolean upgrade(YamlConfiguration current, YamlConfiguration defaults) {
        if (current.getInt("config-version", 1) >= VERSION) return false;
        merge(current, defaults, "");
        current.set("config-version", VERSION);
        return true;
    }

    private static void merge(YamlConfiguration current, ConfigurationSection defaults, String parent) {
        for (String key : defaults.getKeys(false)) {
            String path = parent.isEmpty() ? key : parent + "." + key;
            ConfigurationSection child = defaults.getConfigurationSection(key);
            if (child != null) {
                if (current.contains(path) && USER_COLLECTIONS.contains(path)) continue;
                if (current.contains(path) && !current.isConfigurationSection(path)) continue;
                if (!current.contains(path)) current.createSection(path);
                merge(current, child, path);
            } else if (!current.contains(path)) {
                current.set(path, defaults.get(key));
                current.setComments(path, defaults.getComments(key));
            }
        }
    }
}
