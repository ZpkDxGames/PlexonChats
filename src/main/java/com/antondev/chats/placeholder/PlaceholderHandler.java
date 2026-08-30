package com.antondev.chats.placeholder;

import com.antondev.chats.PlexonChats;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Untrusted messages use a color-only parser; item/mention components are inserted afterwards. */
public final class PlaceholderHandler {
    private static final Pattern MENTION = Pattern.compile("(?<![\\w@])@(?<mentionplayer>[a-zA-Z0-9_]{3,16})(?!\\w)");
    public static final MiniMessage COLORS = MiniMessage.builder().tags(TagResolver.resolver(
            StandardTags.color(), StandardTags.decorations(), StandardTags.gradient(),
            StandardTags.rainbow(), StandardTags.reset())).build();
    private final PlexonChats plugin;
    private Pattern itemTriggers;
    private Pattern combinedTokens;

    public PlaceholderHandler(PlexonChats plugin) { this.plugin = plugin; reload(); }
    public void reload() {
        var triggers = plugin.getConfigManager().getItemTriggers();
        itemTriggers = triggers.isEmpty() ? null : Pattern.compile(triggers.stream().map(Pattern::quote)
                .collect(java.util.stream.Collectors.joining("|")), Pattern.CASE_INSENSITIVE);
        combinedTokens = itemTriggers == null ? MENTION : Pattern.compile(
                "(?<itemtoken>" + itemTriggers.pattern() + ")|" + MENTION.pattern(), Pattern.CASE_INSENSITIVE);
    }

    public ProcessedMessage processMessage(Player sender, String message) {
        var config = plugin.getConfigManager();
        Component component;
        if (!sender.hasPermission("plexonchats.formatting")) component = Component.text(message);
        else {
            MiniMessage parser = config.bool("formatting.allow-advanced-player-tags", false)
                    && sender.hasPermission("plexonchats.formatting.advanced") ? config.getMiniMessage() : COLORS;
            try { component = parser.deserialize(message); }
            catch (IllegalArgumentException ex) { component = Component.text(message); }
        }

        Map<java.util.UUID, Player> mentions = new LinkedHashMap<>();
        boolean itemsEnabled = config.isItemDisplayEnabled() && sender.hasPermission("plexonchats.item") && itemTriggers != null;
        boolean mentionsEnabled = config.isMentionsEnabled() && sender.hasPermission("plexonchats.mention");
        if (itemsEnabled || mentionsEnabled) {
            Pattern tokens = itemsEnabled ? (mentionsEnabled ? combinedTokens : itemTriggers) : MENTION;
            Component[] cachedItem = new Component[1];
            // One pass keeps item names/hover text and configured mention labels out of token parsing.
            // Item triggers win over mentions when a username happens to be "hand".
            component = component.replaceText(TextReplacementConfig.builder().match(tokens).replacement((match, builder) -> {
                if (itemsEnabled && (!mentionsEnabled || match.group("itemtoken") != null)) {
                    if (cachedItem[0] == null) cachedItem[0] = buildItemComponent(sender);
                    return cachedItem[0];
                }
                Player player = Bukkit.getPlayerExact(match.group("mentionplayer"));
                if (player == null || !player.isOnline() || !sender.canSee(player)) return builder;
                mentions.put(player.getUniqueId(), player);
                return plugin.getText().render(config.getMentionFormat(), sender, Map.of("player", Component.text(player.getName())));
            }).build());
        }
        return new ProcessedMessage(component, List.copyOf(mentions.values()));
    }

    private Component buildItemComponent(Player player) {
        var config = plugin.getConfigManager();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir() || item.getAmount() == 0) return config.formatMessage(config.getEmptyHandMessage());
        Component itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                ? item.getItemMeta().displayName() : Component.translatable(item.getType().translationKey());
        Component result = plugin.getText().render(config.getItemFormat(), player, Map.of(
                "item_name", itemName, "amount", Component.text(item.getAmount()), "material", Component.text(item.getType().name())));
        if (config.bool("item-display.hover-item", true)) result = result.hoverEvent(item.asHoverEvent());
        if (config.bool("item-display.click-preview", true)) {
            String token = plugin.getItemPreviewManager().store(item, player.getName());
            result = result.clickEvent(ClickEvent.runCommand("/chatitem " + token));
        }
        return result;
    }

    public void notifyMentionedPlayers(List<Player> players, Player sender) {
        var config = plugin.getConfigManager();
        if (!config.isMentionsEnabled()) return;
        for (Player player : players) {
            if (!player.isOnline() || player.equals(sender) || !plugin.getPreferences().get(player.getUniqueId()).mentions()) continue;
            var sound = config.getMentionSound();
            if (sound != null) player.playSound(player.getLocation(), sound, config.getMentionSoundVolume(), config.getMentionSoundPitch());
            player.sendActionBar(config.getMentionActionbar(sender.getName()));
        }
    }

    public record ProcessedMessage(Component component, List<Player> mentionedPlayers) {}
}
