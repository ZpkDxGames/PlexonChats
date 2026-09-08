package com.antondev.chats.api;

import com.antondev.chats.ChatChannel;
import com.antondev.chats.PlexonChats;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class PlexonChatsApiImpl implements PlexonChatsAPI {
    private final PlexonChats plugin;

    public PlexonChatsApiImpl(PlexonChats plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public ChatChannel channel(Player player) {
        requirePrimaryThread("channel");
        return plugin.getChatManager().getPlayerChannel(Objects.requireNonNull(player, "player"));
    }

    @Override
    public boolean canReceive(Player player, ChatChannel channel) {
        requirePrimaryThread("canReceive");
        return plugin.getChatManager().canReceive(
                Objects.requireNonNull(player, "player"), Objects.requireNonNull(channel, "channel"));
    }

    @Override
    public PlayerChatPreferencesView preferences(UUID playerId) {
        requirePrimaryThread("preferences");
        var preference = plugin.getPreferences().get(Objects.requireNonNull(playerId, "playerId"));
        return new PlayerChatPreferencesView(
                preference.channel(), preference.mentions(), preference.tips(), preference.privateMessages());
    }

    @Override public String discordStatus() { return plugin.getDiscordBridge().status(); }
    @Override public String autoMessageStatus() { return plugin.getAutoMessages().status(); }
    @Override public Set<String> autoMessageGroups() { return Set.copyOf(plugin.getAutoMessages().groupNames()); }

    @Override
    public boolean selectChannel(Player player, ChatChannel channel) {
        requirePrimaryThread("selectChannel");
        return plugin.getChatManager().selectChannel(
                Objects.requireNonNull(player, "player"), Objects.requireNonNull(channel, "channel"));
    }

    @Override
    public void sendPublic(Player sender, ChatChannel channel, String rawMessage) {
        requirePrimaryThread("sendPublic");
        plugin.getChatManager().sendPublic(
                Objects.requireNonNull(sender, "sender"),
                Objects.requireNonNull(channel, "channel"),
                Objects.requireNonNull(rawMessage, "rawMessage"));
    }

    @Override
    public boolean sendPrivate(Player sender, Player recipient, String rawMessage) {
        requirePrimaryThread("sendPrivate");
        return plugin.getMessageCommand().trySendPrivateMessage(
                Objects.requireNonNull(sender, "sender"),
                Objects.requireNonNull(recipient, "recipient"),
                Objects.requireNonNull(rawMessage, "rawMessage"));
    }

    private static void requirePrimaryThread(String operation) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("PlexonChatsAPI." + operation + " must be called on the primary server thread");
        }
    }
}
