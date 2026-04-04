package com.antondev.chats.chat;

import com.antondev.chats.ChatChannel;
import com.antondev.chats.PlexonChats;
import com.antondev.chats.config.ConfigManager;
import com.antondev.chats.placeholder.PlaceholderHandler;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Listens to chat events and routes messages to the appropriate channels.
 */
public class ChatListener implements Listener {

    private final PlexonChats plugin;

    public ChatListener(PlexonChats plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerChat(AsyncChatEvent event) {
        event.setCancelled(true);

        UUID senderId = event.getPlayer().getUniqueId();
        String rawMessage = PlainTextComponentSerializer.plainText().serialize(event.originalMessage());

        if (event.isAsynchronous()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player sender = Bukkit.getPlayer(senderId);
                if (sender == null || !sender.isOnline()) {
                    return;
                }
                routeMessage(sender, rawMessage);
            });
            return;
        }

        Player sender = Bukkit.getPlayer(senderId);
        if (sender == null || !sender.isOnline()) {
            return;
        }
        routeMessage(sender, rawMessage);
    }

    private void routeMessage(Player sender, String rawMessage) {
        ConfigManager config = plugin.getConfigManager();
        ChatManager chatManager = plugin.getChatManager();

        // Check for global shortcut prefix (e.g., "!hello" -> send "hello" globally)
        String globalPrefix = config.getGlobalShortcutPrefix();
        if (!globalPrefix.isEmpty() && rawMessage.startsWith(globalPrefix) && rawMessage.length() > globalPrefix.length()) {
            if (sender.hasPermission("plexonchats.global")) {
                String strippedMessage = rawMessage.substring(globalPrefix.length());
                if (strippedMessage.isBlank()) {
                    return;
                }
                sendGlobalMessage(sender, strippedMessage);
                return;
            }
        }

        // Check for local shortcut prefix
        String localPrefix = config.getLocalShortcutPrefix();
        if (!localPrefix.isEmpty() && rawMessage.startsWith(localPrefix) && rawMessage.length() > localPrefix.length()) {
            if (sender.hasPermission("plexonchats.local")) {
                String strippedMessage = rawMessage.substring(localPrefix.length());
                if (strippedMessage.isBlank()) {
                    return;
                }
                sendLocalMessage(sender, strippedMessage);
                return;
            }
        }

        // Route based on player's current channel
        ChatChannel channel = chatManager.getPlayerChannel(sender);
        switch (channel) {
            case GLOBAL -> sendGlobalMessage(sender, rawMessage);
            case LOCAL -> sendLocalMessage(sender, rawMessage);
        }
    }

    private void sendGlobalMessage(Player sender, String rawMessage) {
        ConfigManager config = plugin.getConfigManager();
        if (!config.isGlobalEnabled()) {
            sender.sendMessage(config.getPrefixed("<red>Global chat is currently disabled."));
            return;
        }

        PlaceholderHandler ph = plugin.getPlaceholderHandler();
        PlaceholderHandler.ProcessedMessage processed = ph.processMessage(sender, rawMessage);
        Component finalMessage = plugin.getChatComponentFactory()
            .buildPublicMessage(sender, ChatChannel.GLOBAL, processed.component());

        // Send to all online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(finalMessage);
        }

        // Log to console
        Bukkit.getConsoleSender().sendMessage(finalMessage);

        // Notify mentioned players (run on main thread for sound)
        ph.notifyMentionedPlayers(processed.mentionedPlayers(), sender);
    }

    private void sendLocalMessage(Player sender, String rawMessage) {
        ConfigManager config = plugin.getConfigManager();
        if (!config.isLocalEnabled()) {
            sender.sendMessage(config.getPrefixed("<red>Local chat is currently disabled."));
            return;
        }

        PlaceholderHandler ph = plugin.getPlaceholderHandler();
        PlaceholderHandler.ProcessedMessage processed = ph.processMessage(sender, rawMessage);
        Component finalMessage = plugin.getChatComponentFactory()
            .buildPublicMessage(sender, ChatChannel.LOCAL, processed.component());

        // Find players within radius in the same world
        int radius = config.getLocalRadius();
        long radiusSquared = (long) radius * radius;
        Set<Player> recipients = new HashSet<>();
        var senderLocation = sender.getLocation();

        for (Player player : sender.getWorld().getPlayers()) {
            if (player.equals(sender)) {
                recipients.add(player);
                continue;
            }
            if (player.getLocation().distanceSquared(senderLocation) <= radiusSquared) {
                recipients.add(player);
            }
        }

        if (recipients.size() <= 1) {
            // Only sender is in range
            sender.sendMessage(finalMessage);
            sender.sendMessage(config.getNoRecipients());
        } else {
            for (Player player : recipients) {
                player.sendMessage(finalMessage);
            }
        }

        // Log to console
        Bukkit.getConsoleSender().sendMessage(finalMessage);

        // Notify mentioned players
        if (!processed.mentionedPlayers().isEmpty()) {
            List<Player> mentionedRecipients = processed.mentionedPlayers().stream()
                    .filter(recipients::contains)
                    .toList();
            ph.notifyMentionedPlayers(mentionedRecipients, sender);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getChatManager().removePlayer(event.getPlayer());
        plugin.getPrivateMessageManager().removePlayer(event.getPlayer());
    }
}
