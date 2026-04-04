package com.antondev.chats.command;

import com.antondev.chats.PlexonChats;
import com.antondev.chats.config.ConfigManager;
import com.antondev.chats.placeholder.PlaceholderHandler;
import com.antondev.chats.player.PlayerInfoService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Handles /msg, /tell, /w private messaging.
 */
public class MessageCommand implements CommandExecutor, TabCompleter {

    private final PlexonChats plugin;

    public MessageCommand(PlexonChats plugin) {
        this.plugin = plugin;
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

        if (args.length < 2) {
            senderPlayer.sendMessage(config.getPrefixed("<gray>Usage: <white>/msg <player> <message>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            senderPlayer.sendMessage(config.getPlayerNotFound(args[0]));
            return true;
        }

        if (target.equals(senderPlayer)) {
            senderPlayer.sendMessage(config.getPrefixed("<red>You cannot message yourself."));
            return true;
        }

        String rawMessage = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        sendPrivateMessage(senderPlayer, target, rawMessage);
        return true;
    }

    public void sendPrivateMessage(Player sender, Player target, String rawMessage) {
        ConfigManager config = plugin.getConfigManager();
        PlayerInfoService infoService = plugin.getPlayerInfoService();

        PlaceholderHandler.ProcessedMessage processed = plugin.getPlaceholderHandler().processMessage(sender, rawMessage);

        Component senderName = Component.text(sender.getName())
                .hoverEvent(HoverEvent.showText(infoService.buildPlayerHover(sender)))
                .clickEvent(ClickEvent.suggestCommand("/msg " + sender.getName() + " "));

        Component targetName = Component.text(target.getName())
                .hoverEvent(HoverEvent.showText(infoService.buildPlayerHover(target)))
                .clickEvent(ClickEvent.suggestCommand("/msg " + target.getName() + " "));

        Component messageBody = processed.component()
                .hoverEvent(HoverEvent.showText(config.formatMessage("<gray>Click to reply")))
                .clickEvent(ClickEvent.suggestCommand("/msg " + sender.getName() + " "));

        Component sent = config.formatMessage("<gray>[To] ")
                .append(targetName)
                .append(config.formatMessage(" <dark_gray>» </dark_gray>"))
                .append(messageBody);

        Component received = config.formatMessage("<gray>[From] ")
                .append(senderName)
                .append(config.formatMessage(" <dark_gray>» </dark_gray>"))
                .append(messageBody.clickEvent(ClickEvent.suggestCommand("/msg " + target.getName() + " ")));

        sender.sendMessage(sent);
        target.sendMessage(received);

        plugin.getPrivateMessageManager().linkConversation(sender, target);

        if (!processed.mentionedPlayers().isEmpty()) {
            plugin.getPlaceholderHandler().notifyMentionedPlayers(processed.mentionedPlayers(), sender);
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String query = args[0].toLowerCase(Locale.ROOT);
            List<String> matches = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(query)) {
                    matches.add(online.getName());
                }
            }
            return matches;
        }
        return List.of();
    }
}
