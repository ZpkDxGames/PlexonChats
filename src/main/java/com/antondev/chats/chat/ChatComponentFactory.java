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
        String format = channel == ChatChannel.GLOBAL ? config.getGlobalFormat() : config.getLocalFormat();
        return text.render(format, sender, values);
    }

    public Component buildPlayerComponent(Player player) {
        Component name = plugin.getText().render(plugin.getConfigManager()
                .string("chat-components.player.name-format", "{display_name}"), player);
        return plugin.getText().interactive(name, "chat-components.player", player, Map.of());
    }
}
