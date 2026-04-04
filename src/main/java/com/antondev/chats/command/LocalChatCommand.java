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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Handles /l local chat shortcut command.
 */
public class LocalChatCommand implements CommandExecutor, TabCompleter {

    private final PlexonChats plugin;

    public LocalChatCommand(PlexonChats plugin) {
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

        if (!player.hasPermission("plexonchats.local")) {
            player.sendMessage(config.getNoPermission());
            return true;
        }

        if (!config.isLocalEnabled()) {
            player.sendMessage(config.getPrefixed("<red>Local chat is currently disabled."));
            return true;
        }

        if (args.length == 0) {
            boolean changed = plugin.getChatManager().setPlayerChannel(player, ChatChannel.LOCAL);
            if (changed) {
                player.sendMessage(config.getChannelSwitched(ChatChannel.LOCAL));
            } else {
                player.sendMessage(config.getChannelAlready(ChatChannel.LOCAL));
            }
            return true;
        }

        String rawMessage = String.join(" ", args);
        PlaceholderHandler.ProcessedMessage processed = plugin.getPlaceholderHandler().processMessage(player, rawMessage);

        Component finalMessage = plugin.getChatComponentFactory()
                .buildPublicMessage(player, ChatChannel.LOCAL, processed.component());

        int radius = config.getLocalRadius();
        long radiusSquared = (long) radius * radius;
        Set<Player> recipients = new HashSet<>();
        var playerLocation = player.getLocation();

        for (Player worldPlayer : player.getWorld().getPlayers()) {
            if (worldPlayer.equals(player)) {
                recipients.add(worldPlayer);
                continue;
            }
            if (worldPlayer.getLocation().distanceSquared(playerLocation) <= radiusSquared) {
                recipients.add(worldPlayer);
            }
        }

        if (recipients.size() <= 1) {
            player.sendMessage(finalMessage);
            player.sendMessage(config.getNoRecipients());
        } else {
            for (Player recipient : recipients) {
                recipient.sendMessage(finalMessage);
            }
        }

        Bukkit.getConsoleSender().sendMessage(finalMessage);

        if (!processed.mentionedPlayers().isEmpty()) {
            List<Player> mentionedRecipients = processed.mentionedPlayers().stream()
                    .filter(recipients::contains)
                    .toList();
            plugin.getPlaceholderHandler().notifyMentionedPlayers(mentionedRecipients, player);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String alias, @NotNull String[] args) {
        return Collections.emptyList();
    }
}
