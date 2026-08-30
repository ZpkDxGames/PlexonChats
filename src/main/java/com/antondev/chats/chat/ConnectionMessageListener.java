package com.antondev.chats.chat;

import com.antondev.chats.PlexonChats;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Locale;
import java.util.Map;

/** Changes the native event component once; never manually rebroadcasts or bridges it. */
public final class ConnectionMessageListener implements Listener {
    private final PlexonChats plugin;

    public ConnectionMessageListener(PlexonChats plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        event.joinMessage(format(event.getPlayer(), "join", event.joinMessage()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        event.quitMessage(format(event.getPlayer(), "quit", event.quitMessage()));
    }

    private Component format(Player player, String kind, Component original) {
        var config = plugin.getConfigManager();
        String path = "connection-messages." + kind;
        String mode = config.string(path + ".mode", "DEFAULT").toUpperCase(Locale.ROOT);
        if (mode.equals("DEFAULT")) return original;
        if (mode.equals("HIDDEN")) return null;
        if (original == null && config.bool("connection-messages.respect-hidden", true)) return null;
        String silent = config.string("connection-messages.silent-permission", "");
        if (!silent.isBlank() && player.hasPermission(silent)) return null;

        boolean joining = kind.equals("join");
        String template = config.string(path + ".format", "{player_name}");
        if (joining && !player.hasPlayedBefore()) {
            String firstJoin = config.string(path + ".first-join-format", "");
            if (!firstJoin.isBlank()) template = firstJoin;
        }
        var onlinePlayers = Bukkit.getOnlinePlayers();
        boolean alreadyCounted = onlinePlayers.stream().anyMatch(online -> online.getUniqueId().equals(player.getUniqueId()));
        int count = onlinePlayers.size() + (joining && !alreadyCounted ? 1 : !joining && alreadyCounted ? -1 : 0);
        return plugin.getText().render(template, player, Map.of(
                "player", plugin.getChatComponentFactory().buildPlayerComponent(player),
                "online", Component.text(Math.max(0, count))));
    }
}
