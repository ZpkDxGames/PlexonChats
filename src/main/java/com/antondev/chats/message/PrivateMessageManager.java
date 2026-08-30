package com.antondev.chats.message;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores last private-message conversations for /reply.
 */
public class PrivateMessageManager {

    private final Map<UUID, UUID> lastMessagedPlayer = new ConcurrentHashMap<>();

    public void linkConversation(Player one, Player two) {
        lastMessagedPlayer.put(one.getUniqueId(), two.getUniqueId());
        lastMessagedPlayer.put(two.getUniqueId(), one.getUniqueId());
    }

    public UUID getLastMessaged(Player player) {
        return lastMessagedPlayer.get(player.getUniqueId());
    }

    public void removePlayer(Player player) {
        UUID playerId = player.getUniqueId();
        lastMessagedPlayer.remove(playerId);
        lastMessagedPlayer.entrySet().removeIf(entry -> entry.getValue().equals(playerId));
    }

    public void clear() {
        lastMessagedPlayer.clear();
    }
}
