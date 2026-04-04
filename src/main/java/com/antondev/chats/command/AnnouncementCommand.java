package com.antondev.chats.command;

import com.antondev.chats.PlexonChats;
import com.antondev.chats.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Handles /announce and /broadcast commands.
 */
public class AnnouncementCommand implements CommandExecutor, TabCompleter {

    private final PlexonChats plugin;

    public AnnouncementCommand(PlexonChats plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        ConfigManager config = plugin.getConfigManager();

        if (!sender.hasPermission("plexonchats.announce")) {
            sender.sendMessage(config.getNoPermission());
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(config.getPrefixed("<gray>Usage: <white>/announce <message>"));
            return true;
        }

        String rawAnnouncement = String.join(" ", args);
        String template = config.getAnnouncementFormat().replace("{message}", rawAnnouncement);

        for (Player online : Bukkit.getOnlinePlayers()) {
            String parsed = plugin.getPlaceholderApiService().apply(online, template);
            online.sendMessage(config.formatMessage(parsed));
        }
        Bukkit.getConsoleSender().sendMessage(config.formatMessage(template));

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        return Collections.emptyList();
    }
}
