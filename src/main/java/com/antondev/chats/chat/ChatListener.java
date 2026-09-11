package com.antondev.chats.chat;

import com.antondev.chats.PlexonChats;
import io.papermc.paper.event.player.ChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Single native chat observation point.
 *
 * Paper's synchronous ChatEvent is intentionally used so the mature Bukkit/world/permission routing
 * remains main-thread safe without allocating a scheduler task for every player message.
 */
@SuppressWarnings("deprecation")
public final class ChatListener implements Listener {
    private final PlexonChats plugin;
    public ChatListener(PlexonChats plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerChat(ChatEvent event) {
        plugin.getDiagnostics().nativeObserved();
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        Set<UUID> viewers = event.viewers().stream().filter(Player.class::isInstance).map(Player.class::cast)
                .map(Player::getUniqueId).collect(Collectors.toUnmodifiableSet());
        event.setCancelled(true);
        plugin.getChatManager().route(event.getPlayer(), message, viewers);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        plugin.getChatManager().removePlayer(event.getPlayer());
        plugin.getPrivateMessageManager().removePlayer(event.getPlayer());
    }
}
