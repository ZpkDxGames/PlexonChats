package com.antondev.chats.gui;

import org.bukkit.configuration.ConfigurationSection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public record GuiLayout(String title, int size, Map<Integer, GuiButton> buttons) {
    public static GuiLayout read(ConfigurationSection section, String defaultTitle, Consumer<String> warning) {
        if (section == null) return new GuiLayout(defaultTitle, 27, Map.of());
        int size = Math.clamp(section.getInt("rows", 3), 1, 6) * 9;
        Map<Integer, GuiButton> buttons = new LinkedHashMap<>();
        ConfigurationSection items = section.getConfigurationSection("items");
        if (items != null) {
            for (String id : items.getKeys(false)) {
                ConfigurationSection value = items.getConfigurationSection(id);
                if (value == null) { warning.accept("GUI item " + id + " must be a section"); continue; }
                GuiButton button = GuiButton.read(id, value, size, warning);
                if (button == null) continue;
                if (buttons.putIfAbsent(button.slot(), button) != null) warning.accept("Duplicate GUI slot " + button.slot() + "; skipped " + id);
            }
        }
        return new GuiLayout(section.getString("title", defaultTitle), size, Collections.unmodifiableMap(buttons));
    }
}
