package com.antondev.chats.config;

import com.antondev.chats.ChatChannel;
import com.antondev.chats.PlexonChats;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Locale;

/**
 * Manages all configuration values with cached access for performance.
 */
public class ConfigManager {

    private final PlexonChats plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    // Cached values
    private ChatChannel defaultChannel;
    private boolean localEnabled;
    private int localRadius;
    private String localFormat;
    private String localShortcutPrefix;
    private String noRecipientsMessage;

    private boolean globalEnabled;
    private String globalFormat;
    private String globalShortcutPrefix;

    private boolean mentionsEnabled;
    private String mentionFormat;
    private String mentionActionbarMessage;
    private Sound mentionSound;
    private float mentionSoundVolume;
    private float mentionSoundPitch;

    private boolean itemDisplayEnabled;
    private List<String> itemTriggers;
    private String itemFormat;
    private String emptyHandMessage;

    private String guiTitle;
    private int guiRows;

    private String messagePrefix;
    private String channelSwitchedMsg;
    private String channelAlreadyMsg;
    private String noPermissionMsg;
    private String configReloadedMsg;
    private String playerOnlyMsg;
    private String unknownChannelMsg;
    private String announcementFormat;
    private String playerNotFoundMsg;
    private String noReplyTargetMsg;

    public ConfigManager(PlexonChats plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        // Default channel
        String defChannel = config.getString("default-channel", "LOCAL");
        defaultChannel = ChatChannel.fromName(defChannel);
        if (defaultChannel == null) defaultChannel = ChatChannel.LOCAL;

        // Local channel
        localEnabled = config.getBoolean("channels.local.enabled", true);
        localRadius = Math.max(0, config.getInt("channels.local.radius", 100));
        localFormat = config.getString("channels.local.format",
                "<gray>[<white>Local<gray>] <gray>{player} <dark_gray>» <gray>{message}");
        localShortcutPrefix = config.getString("channels.local.shortcut-prefix", "");
        noRecipientsMessage = config.getString("channels.local.no-recipients-message",
                "<gray><italic>No one is nearby to hear you...");

        // Global channel
        globalEnabled = config.getBoolean("channels.global.enabled", true);
        globalFormat = config.getString("channels.global.format",
                "<gold>[<yellow>Global<gold>] <white>{player} <dark_gray>» <white>{message}");
        globalShortcutPrefix = config.getString("channels.global.shortcut-prefix", "!");

        // Mentions
        mentionsEnabled = config.getBoolean("mentions.enabled", true);
        mentionFormat = config.getString("mentions.format", "<gradient:#00e6ff:#00ffac>@{player}</gradient>");
        mentionActionbarMessage = config.getString("mentions.actionbar-message",
            "<gray>[<gradient:#00e6ff:#00ffac>{player}</gradient><gray>] mentioned you in chat!");
        mentionSound = resolveSound(config.getString("mentions.sound", "ENTITY_EXPERIENCE_ORB_PICKUP"));
        if (mentionSound == null) {
            mentionSound = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
        }
        mentionSoundVolume = (float) config.getDouble("mentions.sound-volume", 1.0);
        mentionSoundPitch = (float) config.getDouble("mentions.sound-pitch", 1.2);

        // Item display
        itemDisplayEnabled = config.getBoolean("item-display.enabled", true);
        itemTriggers = config.getStringList("item-display.triggers");
        if (itemTriggers.isEmpty()) {
            itemTriggers = List.of("[item]", "@hand");
        }
        itemFormat = config.getString("item-display.format",
                "<aqua><bold>[<hover:show_item:'{item_data}'>{item_name}</hover>]</bold></aqua>");
        emptyHandMessage = config.getString("item-display.empty-hand-message",
                "<gray><italic>[Empty Hand]</italic></gray>");

        // GUI
        guiTitle = config.getString("gui.title",
                "<gradient:#FF6B6B:#4ECDC4><bold>PlexonChats</bold></gradient>");
        guiRows = Math.min(6, Math.max(3, config.getInt("gui.rows", 3)));

        // Messages
        messagePrefix = config.getString("messages.prefix",
            "<b><gradient:#00e6ff:#00ffac>Plexon Chats</gradient></b> <dark_gray>» ");
        channelSwitchedMsg = config.getString("messages.channel-switched",
                "<green>You switched to the <white>{channel}<green> channel.");
        channelAlreadyMsg = config.getString("messages.channel-already",
                "<yellow>You are already in the <white>{channel}<yellow> channel.");
        noPermissionMsg = config.getString("messages.no-permission",
                "<red>You don't have permission to do that.");
        configReloadedMsg = config.getString("messages.config-reloaded",
                "<green>Configuration reloaded successfully!");
        playerOnlyMsg = config.getString("messages.player-only",
                "<red>This command can only be used by players.");
        unknownChannelMsg = config.getString("messages.unknown-channel",
                "<red>Unknown channel: <white>{channel}");
        announcementFormat = config.getString("messages.announcement-format",
            "{message}");
        playerNotFoundMsg = config.getString("messages.player-not-found",
            "<red>Player not found: <white>{player}");
        noReplyTargetMsg = config.getString("messages.no-reply-target",
            "<red>You have no one to reply to.");
    }

    // ---- Component builders ----

    public Component formatMessage(String miniMessageStr) {
        return miniMessage.deserialize(miniMessageStr);
    }

    public Component getPrefixed(String message) {
        return miniMessage.deserialize(messagePrefix + message);
    }

    public Component getChannelSwitched(ChatChannel channel) {
        return getPrefixed(channelSwitchedMsg.replace("{channel}", channel.getDisplayName()));
    }

    public Component getChannelAlready(ChatChannel channel) {
        return getPrefixed(channelAlreadyMsg.replace("{channel}", channel.getDisplayName()));
    }

    public Component getNoPermission() {
        return getPrefixed(noPermissionMsg);
    }

    public Component getConfigReloaded() {
        return getPrefixed(configReloadedMsg);
    }

    public Component getPlayerOnly() {
        return getPrefixed(playerOnlyMsg);
    }

    public Component getUnknownChannel(String channel) {
        return getPrefixed(unknownChannelMsg.replace("{channel}", channel));
    }

    public Component getPlayerNotFound(String playerName) {
        return getPrefixed(playerNotFoundMsg.replace("{player}", playerName));
    }

    public Component getNoReplyTarget() {
        return getPrefixed(noReplyTargetMsg);
    }

    public Component getNoRecipients() {
        return miniMessage.deserialize(noRecipientsMessage);
    }

    public Component getMentionActionbar(String senderName) {
        return miniMessage.deserialize(mentionActionbarMessage.replace("{player}", senderName));
    }

    // ---- Getters ----

    public MiniMessage getMiniMessage() { return miniMessage; }
    public ChatChannel getDefaultChannel() { return defaultChannel; }

    public boolean isLocalEnabled() { return localEnabled; }
    public int getLocalRadius() { return localRadius; }
    public String getLocalFormat() { return localFormat; }
    public String getLocalShortcutPrefix() { return localShortcutPrefix; }

    public boolean isGlobalEnabled() { return globalEnabled; }
    public String getGlobalFormat() { return globalFormat; }
    public String getGlobalShortcutPrefix() { return globalShortcutPrefix; }

    public boolean isMentionsEnabled() { return mentionsEnabled; }
    public String getMentionFormat() { return mentionFormat; }
    public Sound getMentionSound() { return mentionSound; }
    public float getMentionSoundVolume() { return mentionSoundVolume; }
    public float getMentionSoundPitch() { return mentionSoundPitch; }

    public boolean isItemDisplayEnabled() { return itemDisplayEnabled; }
    public List<String> getItemTriggers() { return itemTriggers; }
    public String getItemFormat() { return itemFormat; }
    public String getEmptyHandMessage() { return emptyHandMessage; }

    public String getGuiTitle() { return guiTitle; }
    public int getGuiRows() { return guiRows; }
    public String getAnnouncementFormat() { return announcementFormat; }

    private Sound resolveSound(String soundValue) {
        NamespacedKey key = NamespacedKey.fromString(soundValue);
        if (key == null) {
            key = NamespacedKey.minecraft(soundValue.toLowerCase(Locale.ROOT));
        }
        if (key == null) {
            key = NamespacedKey.fromString(soundValue.toLowerCase(Locale.ROOT));
        }
        return key == null ? null : Registry.SOUNDS.get(key);
    }
}
