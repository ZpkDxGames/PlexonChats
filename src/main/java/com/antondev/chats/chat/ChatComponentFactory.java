package com.antondev.chats.chat;

import com.antondev.chats.ChatChannel;
import com.antondev.chats.PlexonChats;
import com.antondev.chats.player.PlayerInfoService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

/**
 * Builds interactive chat components used in public channels.
 */
public class ChatComponentFactory {

    private final PlexonChats plugin;

    public ChatComponentFactory(PlexonChats plugin) {
        this.plugin = plugin;
    }

    public Component buildPublicMessage(Player sender, ChatChannel channel, Component messageComponent) {
        MiniMessage mm = plugin.getConfigManager().getMiniMessage();

        Component channelBadge = buildChannelBadge(channel);
        Component playerComponent = buildPlayerComponent(sender);
        Component separator = mm.deserialize(" <dark_gray>» </dark_gray>")
                .hoverEvent(HoverEvent.showText(mm.deserialize(
                        "<gray>Quick message <white>" + sender.getName())))
                .clickEvent(ClickEvent.suggestCommand("/msg " + sender.getName() + " "));

        return channelBadge.append(Component.space())
                .append(playerComponent)
                .append(separator)
                .append(messageComponent);
    }

    private Component buildChannelBadge(ChatChannel channel) {
        MiniMessage mm = plugin.getConfigManager().getMiniMessage();

        if (channel == ChatChannel.GLOBAL) {
            return mm.deserialize("<gradient:#6ea8ff:#88beff>[G]</gradient>")
                    .hoverEvent(HoverEvent.showText(mm.deserialize(
                            "<gray>Global channel\n<blue>Click to switch to Global")))
                    .clickEvent(ClickEvent.runCommand("/chat channel global"));
        }

        return mm.deserialize("<gradient:#f1d374:#f7e08f>[L]</gradient>")
                .hoverEvent(HoverEvent.showText(mm.deserialize(
                        "<gray>Local channel\n<yellow>Click to switch to Local")))
                .clickEvent(ClickEvent.runCommand("/chat channel local"));
    }

    private Component buildPlayerComponent(Player sender) {
        PlayerInfoService infoService = plugin.getPlayerInfoService();
        Component playerName = Component.text(sender.getName())
                .hoverEvent(HoverEvent.showText(infoService.buildPlayerHover(sender)))
                .clickEvent(ClickEvent.suggestCommand("/msg " + sender.getName() + " "));
        return infoService.buildRankPrefix(sender).append(playerName);
    }
}
