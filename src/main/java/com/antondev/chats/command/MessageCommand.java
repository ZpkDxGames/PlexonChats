package com.antondev.chats.command;

import com.antondev.chats.PlexonChats;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MessageCommand implements CommandExecutor, TabCompleter {
    private final PlexonChats plugin;
    public MessageCommand(PlexonChats plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        var config = plugin.getConfigManager();
        if (!(sender instanceof Player player)) { sender.sendMessage(config.getPlayerOnly()); return true; }
        if (args.length < 2) { player.sendMessage(Component.text("Usage: /msg <player> <message>")); return true; }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline() || !player.canSee(target)) { player.sendMessage(config.getPlayerNotFound(args[0])); return true; }
        sendPrivateMessage(player, target, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
        return true;
    }

    /** Existing command-facing entry point retained for source compatibility. */
    public void sendPrivateMessage(Player sender, Player target, String raw) {
        trySendPrivateMessage(sender, target, raw);
    }

    /** Controlled PM route used by the public service API. Main thread only. */
    public boolean trySendPrivateMessage(Player sender, Player target, String raw) {
        var config = plugin.getConfigManager();
        if (!sender.hasPermission("plexonchats.tell")) { sender.sendMessage(config.getNoPermission()); return false; }
        if (!config.bool("private-messages.enabled", true)) { sender.sendMessage(config.message("private-disabled")); return false; }
        if (sender.equals(target)) { sender.sendMessage(config.message("private-self")); return false; }
        if (!target.isOnline() || !sender.canSee(target)) { sender.sendMessage(config.message("reply-target-offline")); return false; }
        if (!plugin.getPreferences().get(target.getUniqueId()).privateMessages() && !sender.hasPermission("plexonchats.bypass.private")) {
            sender.sendMessage(config.message("private-blocked")); return false;
        }
        if (!plugin.getChatManager().validateMessage(sender, raw)) return false;
        var processed = plugin.getPlaceholderHandler().processMessage(sender, raw);
        Map<String, Component> values = Map.of(
                "sender", plugin.getChatComponentFactory().buildPlayerComponent(sender),
                "target", plugin.getChatComponentFactory().buildPlayerComponent(target),
                "message", processed.component());
        sender.sendMessage(plugin.getText().render(config.string("private-messages.sent-format", "[To] {target}: {message}"), sender, values));
        target.sendMessage(plugin.getText().render(config.string("private-messages.received-format", "[From] {sender}: {message}"), sender, values));
        plugin.getPrivateMessageManager().linkConversation(sender, target);
        // An uninvolved third player must never receive a notification about a private conversation.
        plugin.getPlaceholderHandler().notifyMentionedPlayers(processed.mentionedPlayers().stream().filter(target::equals).toList(), sender);
        return true;
    }

    @Override public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length != 1 || !sender.hasPermission("plexonchats.tell")) return List.of();
        return Bukkit.getOnlinePlayers().stream().filter(p -> !(sender instanceof Player player) || player.canSee(p))
                .map(Player::getName).filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
    }
}
