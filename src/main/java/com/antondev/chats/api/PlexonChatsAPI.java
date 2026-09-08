package com.antondev.chats.api;

import com.antondev.chats.ChatChannel;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * Stable service API for PlexonChats consumers.
 *
 * <p>Look up through Bukkit's ServicesManager. Mutating methods must be called on the primary
 * server thread. Read methods do not make an asynchronous-safety guarantee unless the caller
 * already owns the relevant Bukkit/thread boundary.</p>
 */
public interface PlexonChatsAPI {
    ChatChannel channel(Player player);

    boolean canReceive(Player player, ChatChannel channel);

    PlayerChatPreferencesView preferences(UUID playerId);

    String discordStatus();

    String autoMessageStatus();

    Set<String> autoMessageGroups();

    boolean selectChannel(Player player, ChatChannel channel);

    void sendPublic(Player sender, ChatChannel channel, String rawMessage);

    boolean sendPrivate(Player sender, Player recipient, String rawMessage);
}
