package com.antondev.chats.chat;

import com.antondev.chats.ChatChannel;
import com.antondev.chats.PlexonChats;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Configured layouts with independently configurable interactive components. */
public final class ChatComponentFactory {
    private final PlexonChats plugin;
    public ChatComponentFactory(PlexonChats plugin) { this.plugin = plugin; }

    public Component buildPublicMessage(Player sender, ChatChannel channel, Component message) {
        String id = channel.name().toLowerCase(Locale.ROOT);
        Map<String, Component> values = new HashMap<>();
        values.put("channel", Component.text(channel.getDisplayName()));
        values.put("channel_id", Component.text(id));
        values.put("player", buildPlayerComponent(sender));
        values.put("message", message);
        var text = plugin.getText();
        var config = plugin.getConfigManager();
        Component badge = text.render(config.string("channels." + id + ".badge", "[" + id + "]"), sender, values);
        values.put("channel_badge", text.interactive(badge, "chat-components.channel", sender, values));
        Component separator = text.render(config.string("chat-components.separator.format", " » "), sender, values);
        values.put("separator", text.interactive(separator, "chat-components.separator", sender, values));
        values.put("server", Component.text(config.string("network.server-labels." + config.string("network.instance-id", "lobby"), "Lobby")));
        String format = config.string("channels." + id + ".format", "{player}: {message}");
        return text.render(format, sender, values);
    }

    /** Remote fields are legacy text/color only. No remote click, hover, MiniMessage or local PM token. */
    public Component buildNetworkMessage(com.antondev.chats.protocol.Envelope packet) {
        var config = plugin.getConfigManager();
        Map<String, Component> values = new HashMap<>();
        values.put("player", com.antondev.chats.network.NetworkText.decode(packet.display()));
        values.put("player_name", Component.text(packet.name()));
        values.put("rank_prefix", com.antondev.chats.network.NetworkText.decode(packet.rank()));
        values.put("message", com.antondev.chats.network.NetworkText.decode(packet.body()));
        values.put("server", Component.text(config.string("network.server-labels." + packet.server(), packet.server())));
        values.put("server_name", values.get("server"));
        values.put("channel", Component.text("Universal")); values.put("channel_id", Component.text("universal"));
        values.put("channel_badge", plugin.getText().render(config.string("channels.universal.badge", "[U]"), null, values));
        values.put("separator", plugin.getText().render(config.string("chat-components.separator.format", " » "), null, values));
        return plugin.getText().render(config.string("channels.universal.format", "{channel_badge} [{server}] {rank_prefix}{player}: {message}"), null, values);
    }

    public Component buildPlayerComponent(Player player) {
        Component name = plugin.getText().render(plugin.getConfigManager()
                .string("chat-components.player.name-format", "{display_name}"), player);
        return plugin.getText().interactive(name, "chat-components.player", player, Map.of());
    }
}
