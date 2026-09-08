package com.antondev.chats.command;

import com.antondev.chats.ChatChannel;
import com.antondev.chats.PlexonChats;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.List;

public final class GlobalChatCommand implements CommandExecutor, TabCompleter {
    private final PlexonChats plugin;
    public GlobalChatCommand(PlexonChats plugin) { this.plugin = plugin; }
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(plugin.getConfigManager().getPlayerOnly()); return true; }
        if (args.length == 0) plugin.getChatManager().selectChannel(player, ChatChannel.GLOBAL);
        else plugin.getChatManager().sendPublic(player, ChatChannel.GLOBAL, String.join(" ", args));
        return true;
    }
    @Override public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) { return List.of(); }
}
