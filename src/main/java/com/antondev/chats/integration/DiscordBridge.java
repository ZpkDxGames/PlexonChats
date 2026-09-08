package com.antondev.chats.integration;

import com.antondev.chats.PlexonChats;
import com.antondev.chats.api.PlexonChatEvent;
import net.kyori.adventure.text.Component;

/** No DiscordSRV classes in this interface: the plugin can load without that optional dependency. */
public interface DiscordBridge extends AutoCloseable {
    String status();
    void sendChat(PlexonChatEvent event);
    void sendAnnouncement(Component message);
    @Override void close();

    static DiscordBridge create(PlexonChats plugin) {
        if (!plugin.getConfigManager().bool("integrations.discordsrv.enabled", false)) return inactive("DISABLED");
        var discord = plugin.getServer().getPluginManager().getPlugin("DiscordSRV");
        if (discord == null || !discord.isEnabled()) return inactive("NOT_INSTALLED");
        try {
            return new DiscordSrvBridge(plugin);
        } catch (LinkageError | RuntimeException ex) {
            plugin.getLogger().warning("DiscordSRV integration unavailable; in-game chat remains active: " + ex.getClass().getSimpleName());
            return inactive("INCOMPATIBLE");
        }
    }

    static DiscordBridge inactive(String reason) {
        return new DiscordBridge() {
            @Override public String status() { return reason; }
            @Override public void sendChat(PlexonChatEvent event) {}
            @Override public void sendAnnouncement(Component message) {}
            @Override public void close() {}
        };
    }
}
