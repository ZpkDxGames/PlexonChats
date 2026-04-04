package com.antondev.chats.chat;

import com.antondev.chats.ChatChannel;
import com.antondev.chats.PlexonChats;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player channel assignments and message routing.
 */
public class ChatManager {

    private final PlexonChats plugin;
    private final Map<UUID, ChatChannel> playerChannels = new ConcurrentHashMap<>();

    public ChatManager(PlexonChats plugin) {
        this.plugin = plugin;
    }

    /**
     * Gets the current channel for a player. Defaults to the configured default channel.
     */
    public ChatChannel getPlayerChannel(Player player) {
        return playerChannels.getOrDefault(player.getUniqueId(), plugin.getConfigManager().getDefaultChannel());
    }

    /**
     * Sets the channel for a player.
     * @return true if channel was changed, false if already in that channel
     */
    public boolean setPlayerChannel(Player player, ChatChannel channel) {
        ChatChannel current = getPlayerChannel(player);
        if (current == channel) {
            return false;
        }
        playerChannels.put(player.getUniqueId(), channel);
        return true;
    }

    /**
     * Removes a player's channel data (on disconnect).
     */
    public void removePlayer(Player player) {
        playerChannels.remove(player.getUniqueId());
    }

    /**
     * Clears all player channel data.
     */
    public void clearAll() {
        playerChannels.clear();
    }
}
