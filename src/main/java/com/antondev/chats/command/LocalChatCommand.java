package com.antondev.chats.command;

import com.antondev.chats.ChatChannel;
import com.antondev.chats.PlexonChats;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.List;

public final class LocalChatCommand implements CommandExecutor, TabCompleter {
    private final PlexonChats plugin;
    public LocalChatCommand(PlexonChats plugin) { this.plugin = plugin; }
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(plugin.getConfigManager().getPlayerOnly()); return true; }
        if (args.length == 0) plugin.getChatManager().selectChannel(player, ChatChannel.LOCAL);
        else plugin.getChatManager().sendPublic(player, ChatChannel.LOCAL, String.join(" ", args));
        return true;
    }
    @Override public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) { return List.of(); }
}
