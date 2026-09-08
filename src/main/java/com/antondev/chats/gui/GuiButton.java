package com.antondev.chats.gui;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public record GuiButton(String id, int slot, GuiAction action, Material material, Material activeMaterial,
                        Material disabledMaterial, String name, List<String> lore, String activeName, List<String> activeLore,
                        String value, String permission, boolean hideWithoutPermission, boolean glow) {

    public static GuiButton read(String id, ConfigurationSection section, int size, Consumer<String> warning) {
        if (!section.getBoolean("enabled", true)) return null;
        int slot = section.getInt("slot", -1);
        if (slot < 0 || slot >= size) { warning.accept("GUI item " + id + " has an out-of-range slot: " + slot); return null; }
        GuiAction action;
        try { action = GuiAction.valueOf(section.getString("action", "NONE").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { warning.accept("Unknown GUI action for " + id); return null; }
        Material material = material(section.getString("material", "PAPER"));
        if (material == null) { warning.accept("Invalid GUI material for " + id); return null; }
        Material active = material(section.getString("active-material", material.name()));
        Material disabled = material(section.getString("disabled-material", material.name()));
        return new GuiButton(id, slot, action, material, active == null ? material : active, disabled == null ? material : disabled,
                section.getString("name", id), List.copyOf(section.getStringList("lore")), section.getString("active-name", ""),
                List.copyOf(section.getStringList("active-lore")), section.getString("value", ""),
                section.getString("permission", ""), section.getBoolean("hide-without-permission", false),
                section.getBoolean("glow-when-active", false));
    }

    public static Material material(String name) {
        Material material = Material.matchMaterial(name);
        return material == null || material.isAir() || !material.isItem() ? null : material;
    }
}
