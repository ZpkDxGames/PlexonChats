package com.antondev.chats.config;

import com.antondev.chats.ChatChannel;
import com.antondev.chats.PlexonChats;
import com.antondev.chats.text.ComponentTemplate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Immutable-after-publication snapshot: invalid reloads never replace the live configuration. */
public final class ConfigManager {
    private final PlexonChats plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final ComponentTemplate templates = new ComponentTemplate(miniMessage);
    private final YamlConfiguration defaults = new YamlConfiguration();
    private volatile YamlConfiguration config;
    private long revision;

    public ConfigManager(PlexonChats plugin) {
        this.plugin = plugin;
        defaults.options().parseComments(true);
        try (var reader = new InputStreamReader(Objects.requireNonNull(plugin.getResource("config.yml")), StandardCharsets.UTF_8)) {
            defaults.load(reader);
        } catch (IOException | InvalidConfigurationException ex) {
            throw new IllegalStateException("Cannot read bundled configuration", ex);
        }
        if (!loadConfig()) throw new IllegalStateException("Invalid config.yml; repair it before enabling PlexonChats.");
    }

    public boolean loadConfig() {
        plugin.saveDefaultConfig();
        Path path = plugin.getDataFolder().toPath().resolve("config.yml");
        try {
            YamlConfiguration candidate = read(path);
            boolean upgraded = ConfigUpgrader.upgrade(candidate, defaults);
            validate(candidate);
            if (upgraded) {
                Path backup = path.resolveSibling("config-before-v" + ConfigUpgrader.VERSION + "-" + System.currentTimeMillis() + ".yml");
                Files.copy(path, backup);
                AtomicFiles.write(path, candidate.saveToString());
                plugin.getLogger().info("Added missing config options. Original saved as " + backup.getFileName());
            }
            config = candidate;
            revision++;
            return true;
        } catch (IOException | InvalidConfigurationException | IllegalArgumentException ex) {
            plugin.getLogger().severe("Configuration not applied: " + ex.getMessage());
            return false;
        }
    }

    public boolean saveSetting(String path, Object value) {
        Path file = plugin.getDataFolder().toPath().resolve("config.yml");
        try {
            YamlConfiguration candidate = read(file);
            candidate.set(path, value);
            validate(candidate);
            AtomicFiles.write(file, candidate.saveToString());
            return true;
        } catch (IOException | InvalidConfigurationException | IllegalArgumentException ex) {
            plugin.getLogger().warning("Setting not saved: " + ex.getMessage());
            return false;
        }
    }

    private static YamlConfiguration read(Path path) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().parseComments(true);
        yaml.load(path.toFile());
        return yaml;
    }

    public static void validate(YamlConfiguration yaml) {
        if (yaml.getInt("config-version", 1) > ConfigUpgrader.VERSION) {
            throw new IllegalArgumentException("This configuration was made by a newer PlexonChats version.");
        }
        for (String path : List.of("channels", "channels.local", "channels.global", "chat-components", "gui",
                "gui.items", "gui.admin", "gui.admin.items", "gui.creator", "gui.creator.items",
                "auto-messages", "auto-messages.groups", "integrations", "integrations.discordsrv", "messages",
                "connection-messages", "connection-messages.join", "connection-messages.quit")) {
            if (yaml.contains(path) && !yaml.isConfigurationSection(path)) {
                throw new IllegalArgumentException(path + " must be a YAML section (use {} for an empty collection).");
            }
        }
        for (String channel : List.of("local", "global")) {
            String format = yaml.getString("channels." + channel + ".format", "{player}: {message}");
            if (!format.contains("{message}")) throw new IllegalArgumentException("channels." + channel + ".format must include {message}.");
        }
        for (String kind : List.of("join", "quit")) {
            String mode = yaml.getString("connection-messages." + kind + ".mode", "DEFAULT").toUpperCase(Locale.ROOT);
            if (!List.of("DEFAULT", "CUSTOM", "HIDDEN").contains(mode)) {
                throw new IllegalArgumentException("connection-messages." + kind + ".mode must be DEFAULT, CUSTOM or HIDDEN.");
            }
        }
    }

    public String string(String path, String fallback) { return config.getString(path, defaults.getString(path, fallback)); }
    public boolean bool(String path, boolean fallback) { return config.getBoolean(path, defaults.getBoolean(path, fallback)); }
    public int integer(String path, int fallback, int min, int max) {
        return Math.clamp(config.getInt(path, defaults.getInt(path, fallback)), min, max);
    }
    public long number(String path, long fallback, long min, long max) {
        return Math.clamp(config.getLong(path, defaults.getLong(path, fallback)), min, max);
    }
    public double decimal(String path, double fallback, double min, double max) {
        double value = config.getDouble(path, defaults.getDouble(path, fallback));
        return Double.isFinite(value) ? Math.clamp(value, min, max) : fallback;
    }
    public List<String> lines(String path) {
        if (config.isString(path)) return List.of(config.getString(path, ""));
        return config.contains(path) ? List.copyOf(config.getStringList(path)) : List.copyOf(defaults.getStringList(path));
    }
    public ConfigurationSection section(String path) { return config.getConfigurationSection(path); }
    public long revision() { return revision; }
    public MiniMessage getMiniMessage() { return miniMessage; }

    public Component formatMessage(String text) {
        try { return miniMessage.deserialize(text); }
        catch (IllegalArgumentException ex) { return Component.text(text); }
    }
    public Component getPrefixed(String text) { return formatMessage(string("messages.prefix", "") + text); }
    public Component message(String key) { return message(key, Map.of()); }
    public Component message(String key, Map<String, String> values) {
        Map<String, Component> components = new LinkedHashMap<>();
        values.forEach((k, v) -> components.put(k, Component.text(v)));
        String source = string("messages.prefix", "") + string("messages." + key, key);
        try { return templates.render(source, components); }
        catch (IllegalArgumentException ex) { return Component.text(source); }
    }
    public Component getChannelSwitched(ChatChannel channel) { return message("channel-switched", Map.of("channel", channel.getDisplayName())); }
    public Component getChannelAlready(ChatChannel channel) { return message("channel-already", Map.of("channel", channel.getDisplayName())); }
    public Component getNoPermission() { return message("no-permission"); }
    public Component getConfigReloaded() { return message("config-reloaded"); }
    public Component getPlayerOnly() { return message("player-only"); }
    public Component getUnknownChannel(String value) { return message("unknown-channel", Map.of("channel", value)); }
    public Component getPlayerNotFound(String value) { return message("player-not-found", Map.of("player", value)); }
    public Component getNoReplyTarget() { return message("no-reply-target"); }
    public Component getNoRecipients() { return formatMessage(string("channels.local.no-recipients-message", "")); }
    public Component getMentionActionbar(String value) {
        return templates.render(string("mentions.actionbar-message", "{player} mentioned you"), Map.of("player", Component.text(value)));
    }

    public ChatChannel getDefaultChannel() {
        ChatChannel channel = ChatChannel.fromName(string("default-channel", "LOCAL"));
        if (channel == null) channel = ChatChannel.LOCAL;
        return isChannelEnabled(channel) ? channel : (isGlobalEnabled() ? ChatChannel.GLOBAL : ChatChannel.LOCAL);
    }
    public boolean isChannelEnabled(ChatChannel channel) { return channel == ChatChannel.GLOBAL ? isGlobalEnabled() : isLocalEnabled(); }
    public boolean isLocalEnabled() { return bool("channels.local.enabled", true); }
    public int getLocalRadius() { return integer("channels.local.radius", 100, 0, 100_000); }
    public String getLocalFormat() { return string("channels.local.format", "{player}: {message}"); }
    public String getLocalShortcutPrefix() { return string("channels.local.shortcut-prefix", ""); }
    public boolean isGlobalEnabled() { return bool("channels.global.enabled", true); }
    public String getGlobalFormat() { return string("channels.global.format", "{player}: {message}"); }
    public String getGlobalShortcutPrefix() { return string("channels.global.shortcut-prefix", "!"); }
    public boolean isMentionsEnabled() { return bool("mentions.enabled", true); }
    public String getMentionFormat() { return string("mentions.format", "<aqua>@{player}</aqua>"); }
    public Sound getMentionSound() { return resolveSound(string("mentions.sound", "block.note_block.pling")); }
    public float getMentionSoundVolume() { return (float) decimal("mentions.sound-volume", 1, 0, 10); }
    public float getMentionSoundPitch() { return (float) decimal("mentions.sound-pitch", 1.5, 0, 2); }
    public boolean isItemDisplayEnabled() { return bool("item-display.enabled", true); }
    public List<String> getItemTriggers() { return lines("item-display.triggers").stream().filter(s -> !s.isBlank()).limit(16).toList(); }
    public String getItemFormat() { return string("item-display.format", "<yellow>{item_name}</yellow> <gray>x{amount}"); }
    public String getEmptyHandMessage() { return string("item-display.empty-hand-message", "<gray>[Empty Hand]"); }
    public String getGuiTitle() { return string("gui.title", "PlexonChats"); }
    public int getGuiRows() { return integer("gui.rows", 3, 1, 6); }
    public String getAnnouncementFormat() { return string("messages.announcement-format", "{message}"); }

    public Sound resolveSound(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("NONE")) return null;
        try {
            if (!value.contains(":") && value.equals(value.toUpperCase(Locale.ROOT))) {
                try { return (Sound) Sound.class.getField(value).get(null); }
                catch (ReflectiveOperationException ignored) { }
            }
            NamespacedKey key = NamespacedKey.fromString(value.toLowerCase(Locale.ROOT));
            return key == null ? null : Registry.SOUNDS.get(key);
        } catch (IllegalArgumentException ex) { return null; }
    }
}
