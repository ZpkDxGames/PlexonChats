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
import java.util.UUID;

/**
 * Handles /reply and /r private-message reply.
 */
public class ReplyCommand implements CommandExecutor, TabCompleter {

    private final PlexonChats plugin;
    private final MessageCommand messageCommand;

    public ReplyCommand(PlexonChats plugin, MessageCommand messageCommand) {
        this.plugin = plugin;
        this.messageCommand = messageCommand;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        ConfigManager config = plugin.getConfigManager();

        if (!(sender instanceof Player senderPlayer)) {
            sender.sendMessage(config.getPlayerOnly());
            return true;
        }

        if (!senderPlayer.hasPermission("plexonchats.tell")) {
            senderPlayer.sendMessage(config.getNoPermission());
            return true;
        }

        if (args.length == 0) {
            senderPlayer.sendMessage(config.getPrefixed("<gray>Usage: <white>/reply <message>"));
            return true;
        }

        UUID targetId = plugin.getPrivateMessageManager().getLastMessaged(senderPlayer);
        if (targetId == null) {
            senderPlayer.sendMessage(config.getNoReplyTarget());
            return true;
        }

        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) {
            senderPlayer.sendMessage(config.message("reply-target-offline"));
            return true;
        }

        String rawMessage = String.join(" ", args);
        messageCommand.sendPrivateMessage(senderPlayer, target, rawMessage);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        return Collections.emptyList();
    }
}
