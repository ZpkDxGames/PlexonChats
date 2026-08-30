package com.antondev.chats.text;

import com.antondev.chats.PlexonChats;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.net.URI;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Lazy built-ins and per-subject PlaceholderAPI expansion for trusted configuration templates. */
public final class TextService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&').hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    private final PlexonChats plugin;
    private final ComponentTemplate templates;
    private final Set<String> warned = new HashSet<>();

    public TextService(PlexonChats plugin) {
        this.plugin = plugin;
        templates = new ComponentTemplate(plugin.getConfigManager().getMiniMessage());
    }

    public Component render(String source, Player subject) { return render(source, subject, Map.of()); }

    public Component render(String source, Player subject, Map<String, Component> extra) {
        try {
            return templates.render(source, key -> extra.containsKey(key) ? extra.get(key) : resolve(subject, key));
        } catch (IllegalArgumentException ex) {
            if (warned.add(source) && warned.size() <= 50) plugin.getLogger().warning("Invalid text template: " + ex.getMessage());
            return Component.text(source);
        }
    }

    private Component resolve(Player player, String key) {
        if (key.startsWith("%")) return legacy(plugin.getPlaceholderApiService().apply(player, key));
        return switch (key) {
            case "player", "player_name" -> Component.text(player == null ? "Console" : player.getName());
            case "display_name" -> player == null ? Component.text("Console") : withoutInteractions(player.displayName());
            case "world" -> Component.text(player == null ? "N/A" : player.getWorld().getName());
            case "uuid" -> Component.text(player == null ? "N/A" : player.getUniqueId().toString());
            case "rank" -> Component.text(player == null ? "N/A" : plugin.getPlayerInfoService().getRank(player));
            case "rank_prefix" -> player == null ? Component.empty() : plugin.getPlayerInfoService().buildRankPrefix(player);
            case "balance" -> Component.text(player == null ? "N/A" : plugin.getPlayerInfoService().getBalance(player));
            case "playtime" -> Component.text(player == null ? "N/A" : plugin.getPlayerInfoService().formatPlaytime(player));
            case "ping" -> Component.text(player == null ? 0 : player.getPing());
            case "online" -> Component.text(Bukkit.getOnlinePlayers().size());
            case "max_players" -> Component.text(Bukkit.getMaxPlayers());
            case "server_name" -> Component.text(plugin.getConfigManager().string("server-name", "Plexon"));
            case "version" -> Component.text(plugin.getPluginMeta().getVersion());
            case "radius" -> Component.text(plugin.getConfigManager().getLocalRadius());
            case "channel" -> player == null ? Component.empty() : Component.text(plugin.getChatManager().getPlayerChannel(player).getDisplayName());
            case "global_shortcut" -> Component.text(plugin.getConfigManager().getGlobalShortcutPrefix());
            case "discord_status" -> Component.text(plugin.getDiscordBridge().status());
            case "auto_groups" -> Component.text(plugin.getAutoMessages().groupNames().size());
            case "auto_status" -> Component.text(plugin.getAutoMessages().status());
            default -> null;
        };
    }

    public String plain(String source, Player subject) {
        return PlainTextComponentSerializer.plainText().serialize(render(source, subject));
    }

    public Component interactive(Component component, String path, Player subject, Map<String, Component> extra) {
        var config = plugin.getConfigManager();
        if (config.bool(path + ".hover.enabled", false)) {
            component = component.hoverEvent(HoverEvent.showText(render(String.join("\n", config.lines(path + ".hover.lines")), subject, extra)));
        }
        String action = config.string(path + ".click.action", "NONE");
        String value = PlainTextComponentSerializer.plainText().serialize(render(config.string(path + ".click.value", ""), subject, extra));
        ClickEvent click = clickEvent(action, value);
        return click == null ? component : component.clickEvent(click);
    }

    public static ClickEvent clickEvent(String action, String value) {
        try {
            return switch (action.toUpperCase(Locale.ROOT)) {
                case "SUGGEST_COMMAND" -> ClickEvent.suggestCommand(value);
                case "RUN_COMMAND" -> value.startsWith("/") ? ClickEvent.runCommand(value) : null;
                case "COPY_TO_CLIPBOARD" -> ClickEvent.copyToClipboard(value);
                case "OPEN_URL" -> isWebUrl(value) ? ClickEvent.openUrl(value) : null;
                default -> null;
            };
        } catch (IllegalArgumentException ex) { return null; }
    }

    public static boolean isWebUrl(String value) {
        try {
            URI uri = URI.create(value);
            return uri.getHost() != null && ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException ex) { return false; }
    }

    public static Component legacy(String value) { return LEGACY.deserialize(value.replace('§', '&')); }
    public static Component withoutInteractions(Component value) {
        return value.hoverEvent(null).clickEvent(null).insertion(null)
                .children(value.children().stream().map(TextService::withoutInteractions).toList());
    }
    public void resetWarnings() { warned.clear(); }
}
