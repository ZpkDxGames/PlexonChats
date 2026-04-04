package com.antondev.chats.command;

import com.antondev.chats.ChatChannel;
import com.antondev.chats.PlexonChats;
import com.antondev.chats.config.ConfigManager;
import com.antondev.chats.gui.ChatGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Handles the /chat command with subcommands: channel, gui, reload.
 * Usage:
 *   /chat channel <local|global>
 *   /chat gui
 *   /chat reload
 */
public class ChatCommand implements CommandExecutor, TabCompleter {

    private final PlexonChats plugin;

    public ChatCommand(PlexonChats plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "channel", "ch", "c" -> handleChannel(sender, args);
            case "gui", "menu" -> handleGUI(sender);
            case "reload" -> handleReload(sender);
            default -> sendUsage(sender);
        }

        return true;
    }

    private void handleChannel(CommandSender sender, String[] args) {
        ConfigManager config = plugin.getConfigManager();

        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getPlayerOnly());
            return;
        }

        if (args.length < 2) {
            // Show current channel
            ChatChannel current = plugin.getChatManager().getPlayerChannel(player);
            player.sendMessage(config.getPrefixed(
                    "<gray>Current channel: <white>" + current.getDisplayName()));
            player.sendMessage(config.getPrefixed(
                    "<gray>Usage: <white>/chat channel <local|global>"));
            return;
        }

        String channelName = args[1];
        ChatChannel channel = ChatChannel.fromName(channelName);

        if (channel == null) {
            player.sendMessage(config.getUnknownChannel(channelName));
            return;
        }

        if (!player.hasPermission(channel.getPermission())) {
            player.sendMessage(config.getNoPermission());
            return;
        }

        boolean changed = plugin.getChatManager().setPlayerChannel(player, channel);
        if (changed) {
            player.sendMessage(config.getChannelSwitched(channel));
        } else {
            player.sendMessage(config.getChannelAlready(channel));
        }
    }

    private void handleGUI(CommandSender sender) {
        ConfigManager config = plugin.getConfigManager();

        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getPlayerOnly());
            return;
        }

        if (!player.hasPermission("plexonchats.gui")) {
            player.sendMessage(config.getNoPermission());
            return;
        }

        new ChatGUI(plugin).open(player);
    }

    private void handleReload(CommandSender sender) {
        ConfigManager config = plugin.getConfigManager();

        if (!sender.hasPermission("plexonchats.reload")) {
            sender.sendMessage(config.getNoPermission());
            return;
        }

        plugin.getConfigManager().loadConfig();
        sender.sendMessage(config.getConfigReloaded());
    }

    private void sendUsage(CommandSender sender) {
        ConfigManager config = plugin.getConfigManager();
        sender.sendMessage(config.formatMessage(
            "<gradient:#00e6ff:#00ffac><bold>PlexonChats</bold></gradient> <dark_gray>- <gray>Commands:"));
        sender.sendMessage(config.formatMessage(
                " <gray>• <white>/chat channel <local|global> <dark_gray>- <gray>Switch chat channel"));
        sender.sendMessage(config.formatMessage(
                " <gray>• <white>/chat gui <dark_gray>- <gray>Open chat settings GUI"));
        sender.sendMessage(config.formatMessage(
                " <gray>• <white>/chat reload <dark_gray>- <gray>Reload configuration"));
        sender.sendMessage(config.formatMessage(
                " <gray>• <white>/g <message> <dark_gray>- <gray>Quick global message"));
        sender.sendMessage(config.formatMessage(
            " <gray>• <white>/l <message> <dark_gray>- <gray>Quick local message"));
        sender.sendMessage(config.formatMessage(
            " <gray>• <white>/announce <message> <dark_gray>- <gray>Broadcast message"));
        sender.sendMessage(config.formatMessage(
            " <gray>• <white>/msg <player> <message> <dark_gray>- <gray>Private message"));
        sender.sendMessage(config.formatMessage(
            " <gray>• <white>/reply <message> <dark_gray>- <gray>Reply to last conversation"));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("channel", "gui", "reload"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("channel")) {
            for (ChatChannel channel : ChatChannel.values()) {
                if (sender.hasPermission(channel.getPermission())) {
                    completions.add(channel.name().toLowerCase(Locale.ROOT));
                }
            }
        }

        // Filter by what the player has typed so far
        String lastArg = args[args.length - 1].toLowerCase(Locale.ROOT);
        completions.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(lastArg));

        return completions;
    }
}
