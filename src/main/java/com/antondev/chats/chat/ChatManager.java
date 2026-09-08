package com.antondev.chats.chat;

import com.antondev.chats.ChatChannel;
import com.antondev.chats.PlexonChats;
import com.antondev.chats.api.PlexonChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One permission-checked route for normal chat and both shortcut commands. Main thread only. */
public final class ChatManager {
    private final PlexonChats plugin;
    private final MessageLimiter limiter = new MessageLimiter(System::nanoTime);
    public ChatManager(PlexonChats plugin) { this.plugin = plugin; }

    public ChatChannel getPlayerChannel(Player player) {
        ChatChannel selected = plugin.getPreferences().get(player.getUniqueId()).channel();
        if (selected == null) selected = plugin.getConfigManager().getDefaultChannel();
        if (available(player, selected)) return selected;
        for (ChatChannel channel : ChatChannel.values()) if (available(player, channel)) return channel;
        return selected; // Sending will reject it if no channel is available.
    }

    private boolean available(Player player, ChatChannel channel) {
        return plugin.getConfigManager().isChannelEnabled(channel) && player.hasPermission(channel.getPermission());
    }

    public boolean setPlayerChannel(Player player, ChatChannel channel) {
        if (!available(player, channel) || getPlayerChannel(player) == channel) return false;
        plugin.getPreferences().set(player.getUniqueId(), plugin.getPreferences().get(player.getUniqueId()).withChannel(channel));
        return true;
    }

    public boolean selectChannel(Player player, ChatChannel channel) {
        if (!checkChannel(player, channel)) return false;
        boolean changed = setPlayerChannel(player, channel);
        player.sendMessage(changed ? plugin.getConfigManager().getChannelSwitched(channel) : plugin.getConfigManager().getChannelAlready(channel));
        return true;
    }

    private boolean checkChannel(Player player, ChatChannel channel) {
        var config = plugin.getConfigManager();
        if (!player.hasPermission(channel.getPermission())) {
            player.sendMessage(config.getNoPermission());
            return false;
        }
        if (!config.isChannelEnabled(channel)) {
            player.sendMessage(config.message("channel-disabled", Map.of("channel", channel.getDisplayName())));
            return false;
        }
        return true;
    }

    public void route(Player sender, String raw, Set<UUID> allowedViewers) {
        String global = plugin.getConfigManager().getGlobalShortcutPrefix();
        String local = plugin.getConfigManager().getLocalShortcutPrefix();
        ChatChannel channel = getPlayerChannel(sender);
        // Prefer the longest configured prefix when prefixes overlap.
        if (!global.isEmpty() && raw.startsWith(global) && (local.isEmpty() || !raw.startsWith(local) || global.length() >= local.length())) {
            channel = ChatChannel.GLOBAL;
            raw = raw.substring(global.length());
        } else if (!local.isEmpty() && raw.startsWith(local)) {
            channel = ChatChannel.LOCAL;
            raw = raw.substring(local.length());
        }
        sendPublic(sender, channel, raw, allowedViewers);
    }

    public boolean validateMessage(Player sender, String raw) {
        var config = plugin.getConfigManager();
        if (raw.isBlank()) { sender.sendMessage(config.message("empty-message")); return false; }
        int max = config.integer("chat.max-message-length", 512, 1, 10_000);
        if (raw.length() > max) { sender.sendMessage(config.message("message-too-long", Map.of("limit", String.valueOf(max)))); return false; }
        if (raw.indexOf('\n') >= 0 || raw.indexOf('\r') >= 0) { sender.sendMessage(config.message("message-newline")); return false; }
        if (sender.hasPermission("plexonchats.bypass.cooldown")) return true;
        MessageLimiter.Result result = limiter.check(sender.getUniqueId(), raw,
                config.number("chat.cooldown-milliseconds", 1000, 0, 60_000),
                config.number("chat.duplicate-window-seconds", 5, 0, 3600) * 1000);
        if (result.duplicate()) { sender.sendMessage(config.message("duplicate-message")); return false; }
        if (result.remainingMillis() > 0) {
            sender.sendMessage(config.message("cooldown", Map.of("seconds", String.format(java.util.Locale.ROOT, "%.1f", result.remainingMillis() / 1000.0))));
            return false;
        }
        return true;
    }

    public void sendPublic(Player sender, ChatChannel channel, String raw) { sendPublic(sender, channel, raw, null); }

    public void sendPublic(Player sender, ChatChannel channel, String raw, Set<UUID> allowedViewers) {
        if (!checkChannel(sender, channel) || !validateMessage(sender, raw)) return;
        Set<Player> recipients = new LinkedHashSet<>();
        var location = sender.getLocation();
        double radius = plugin.getConfigManager().getLocalRadius();
        var candidates = channel == ChatChannel.GLOBAL ? Bukkit.getOnlinePlayers() : sender.getWorld().getPlayers();
        for (Player recipient : candidates) {
            if (allowedViewers != null && !allowedViewers.contains(recipient.getUniqueId())) continue;
            if (!canReceive(recipient, channel)) continue;
            if (channel == ChatChannel.LOCAL && recipient.getLocation().distanceSquared(location) > radius * radius) continue;
            recipients.add(recipient);
        }

        var processed = plugin.getPlaceholderHandler().processMessage(sender, raw);
        PlexonChatEvent event = new PlexonChatEvent(sender, channel, raw, processed.component(), recipients);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;
        Component formatted = plugin.getChatComponentFactory().buildPublicMessage(sender, channel, event.getMessage());
        event.getRecipients().stream().filter(Player::isOnline).forEach(player -> player.sendMessage(formatted));
        if (channel == ChatChannel.LOCAL && event.getRecipients().contains(sender)
                && event.getRecipients().stream().noneMatch(player -> !player.equals(sender))) {
            sender.sendMessage(plugin.getConfigManager().getNoRecipients());
        }
        if (plugin.getConfigManager().bool("chat.log-to-console", true)) Bukkit.getConsoleSender().sendMessage(formatted);
        List<Player> mentions = processed.mentionedPlayers().stream().filter(event.getRecipients()::contains).toList();
        plugin.getPlaceholderHandler().notifyMentionedPlayers(mentions, sender);
        // This is the only player-chat -> Discord entry point. PM/local traffic never enters it.
        if (channel == ChatChannel.GLOBAL && event.isDiscordAllowed()) plugin.getDiscordBridge().sendChat(event);
    }

    public boolean canReceive(Player player, ChatChannel channel) {
        String permission = plugin.getConfigManager().string("channels." + channel.name().toLowerCase(java.util.Locale.ROOT) + ".receive-permission", "");
        return permission.isBlank() || player.hasPermission(permission);
    }

    public void removePlayer(Player player) { limiter.remove(player.getUniqueId()); }
    public void clearAll() { limiter.clear(); }
}
