package com.antondev.chats.chat;

import com.antondev.chats.PlexonChats;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ChatListener implements Listener {
    private final PlexonChats plugin;
    public ChatListener(PlexonChats plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncChatEvent event) {
        // Preserve lower-priority moderation edits AND audience restrictions.
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        UUID senderId = event.getPlayer().getUniqueId();
        Set<UUID> viewers = event.viewers().stream().filter(Player.class::isInstance).map(Player.class::cast)
                .map(Player::getUniqueId).collect(Collectors.toUnmodifiableSet());
        event.setCancelled(true);
        Runnable route = () -> {
            Player sender = Bukkit.getPlayer(senderId);
            if (sender != null && sender.isOnline()) plugin.getChatManager().route(sender, message, viewers);
        };
        if (event.isAsynchronous()) Bukkit.getScheduler().runTask(plugin, route);
        else route.run();
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        plugin.getChatManager().removePlayer(event.getPlayer());
        plugin.getPrivateMessageManager().removePlayer(event.getPlayer());
    }
}
