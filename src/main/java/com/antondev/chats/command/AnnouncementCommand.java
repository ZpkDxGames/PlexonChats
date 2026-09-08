package com.antondev.chats.command;

import com.antondev.chats.PlexonChats;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.List;
import java.util.Map;

public final class AnnouncementCommand implements CommandExecutor, TabCompleter {
    private final PlexonChats plugin;
    public AnnouncementCommand(PlexonChats plugin) { this.plugin = plugin; }
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        var config = plugin.getConfigManager();
        if (!sender.hasPermission("plexonchats.announce")) { sender.sendMessage(config.getNoPermission()); return true; }
        if (args.length == 0) { sender.sendMessage(Component.text("Usage: /announce <message>")); return true; }
        String raw = String.join(" ", args);
        for (Player online : Bukkit.getOnlinePlayers()) online.sendMessage(format(raw, online));
        Component consoleMessage = format(raw, null);
        Bukkit.getConsoleSender().sendMessage(consoleMessage);
        plugin.getDiscordBridge().sendAnnouncement(consoleMessage);
        return true;
    }
    private Component format(String text, Player player) {
        Component message = plugin.getText().render(text, player);
        return plugin.getText().render(plugin.getConfigManager().getAnnouncementFormat(), player, Map.of("message", message));
    }
    @Override public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) { return List.of(); }
}
