package com.antondev.chats.command;

import com.antondev.chats.ChatChannel;
import com.antondev.chats.PlexonChats;
import com.antondev.chats.config.ConfigManager;
import com.antondev.chats.placeholder.PlaceholderHandler;
import net.kyori.adventure.text.Component;
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
 * Handles the /g <message> shortcut command for quick global chat.
 */
public class GlobalChatCommand implements CommandExecutor, TabCompleter {

    private final PlexonChats plugin;

    public GlobalChatCommand(PlexonChats plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        ConfigManager config = plugin.getConfigManager();

        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getPlayerOnly());
            return true;
        }

        if (!player.hasPermission("plexonchats.global")) {
            player.sendMessage(config.getNoPermission());
            return true;
        }

        if (!config.isGlobalEnabled()) {
            player.sendMessage(config.getPrefixed("<red>Global chat is currently disabled."));
            return true;
        }

        if (args.length == 0) {
            // No message provided: toggle to global channel
            boolean changed = plugin.getChatManager().setPlayerChannel(player, ChatChannel.GLOBAL);
            if (changed) {
                player.sendMessage(config.getChannelSwitched(ChatChannel.GLOBAL));
            } else {
                player.sendMessage(config.getChannelAlready(ChatChannel.GLOBAL));
            }
            return true;
        }

        // Send the message directly to global chat
        String rawMessage = String.join(" ", args);
        PlaceholderHandler ph = plugin.getPlaceholderHandler();
        PlaceholderHandler.ProcessedMessage processed = ph.processMessage(player, rawMessage);
        Component finalMessage = plugin.getChatComponentFactory()
            .buildPublicMessage(player, ChatChannel.GLOBAL, processed.component());

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(finalMessage);
        }
        Bukkit.getConsoleSender().sendMessage(finalMessage);

        // Notify mentions
        if (!processed.mentionedPlayers().isEmpty()) {
            ph.notifyMentionedPlayers(processed.mentionedPlayers(), player);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String alias, @NotNull String[] args) {
        return Collections.emptyList();
    }
}
