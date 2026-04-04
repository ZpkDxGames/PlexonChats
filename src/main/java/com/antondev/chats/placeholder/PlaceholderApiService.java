package com.antondev.chats.placeholder;

import com.antondev.chats.PlexonChats;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * Optional PlaceholderAPI bridge loaded via reflection.
 */
public class PlaceholderApiService {

    private final PlexonChats plugin;

    private boolean checked;
    private Method setPlaceholdersMethod;

    public PlaceholderApiService(PlexonChats plugin) {
        this.plugin = plugin;
    }

    public String apply(Player player, String text) {
        hookIfNeeded();
        if (setPlaceholdersMethod == null || player == null) {
            return text;
        }

        try {
            Object result = setPlaceholdersMethod.invoke(null, player, text);
            return result == null ? text : String.valueOf(result);
        } catch (ReflectiveOperationException ex) {
            return text;
        }
    }

    private void hookIfNeeded() {
        if (checked) {
            return;
        }
        checked = true;

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            plugin.getLogger().info("PlaceholderAPI not detected. Placeholder support is disabled.");
            return;
        }

        try {
            Class<?> clazz = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            setPlaceholdersMethod = clazz.getMethod("setPlaceholders", Player.class, String.class);
            plugin.getLogger().info("PlaceholderAPI detected. Placeholder support enabled.");
        } catch (ClassNotFoundException | NoSuchMethodException ex) {
            setPlaceholdersMethod = null;
            plugin.getLogger().warning("PlaceholderAPI was found but compatible API methods were not detected.");
        }
    }
}
