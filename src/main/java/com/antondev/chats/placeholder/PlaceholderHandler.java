package com.antondev.chats.placeholder;

import com.antondev.chats.PlexonChats;
import com.antondev.chats.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles @player mentions and [item]/@hand placeholders in chat messages.
 */
public class PlaceholderHandler {

    private static final Pattern MENTION_PATTERN = Pattern.compile("@(\\w{3,16})");
    private static final String ITEM_PLACEHOLDER_TOKEN = "__PLEXONCHATS_ITEM_PLACEHOLDER__";

    private final PlexonChats plugin;

    public PlaceholderHandler(PlexonChats plugin) {
        this.plugin = plugin;
    }

    /**
     * Processes all placeholders in a message string, returning the final Component.
     * Also handles side effects like playing mention sounds.
     *
     * @param sender  the player who sent the message
     * @param message the raw message string
     * @return the processed Component with all placeholders resolved
     */
    public ProcessedMessage processMessage(Player sender, String message) {
        ConfigManager config = plugin.getConfigManager();
        MiniMessage mm = config.getMiniMessage();
        List<Player> mentionedPlayers = new ArrayList<>();
        Set<UUID> mentionIds = new HashSet<>();

        // Step 1: Handle item placeholders BEFORE MiniMessage parsing
        String processed = message;
        if (config.isItemDisplayEnabled()) {
            for (String trigger : config.getItemTriggers()) {
                if (processed.toLowerCase(Locale.ROOT).contains(trigger.toLowerCase(Locale.ROOT))) {
                    // We replace the trigger with a unique token that we'll handle after MiniMessage
                    processed = processed.replaceAll("(?i)" + Pattern.quote(trigger), ITEM_PLACEHOLDER_TOKEN);
                }
            }
        }

        // Step 2: Handle @mentions - replace with formatted version
        if (config.isMentionsEnabled()) {
            Matcher matcher = MENTION_PATTERN.matcher(processed);
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                String mentionedName = matcher.group(1);
                Player mentioned = Bukkit.getPlayerExact(mentionedName);
                if (mentioned != null && mentioned.isOnline()) {
                    if (mentionIds.add(mentioned.getUniqueId())) {
                        mentionedPlayers.add(mentioned);
                    }
                    String formatted = config.getMentionFormat().replace("{player}", mentioned.getName());
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(formatted));
                } else {
                    // Keep the @mention as-is but gray it out
                    matcher.appendReplacement(sb, Matcher.quoteReplacement("<gray>@" + mentionedName + "</gray>"));
                }
            }
            matcher.appendTail(sb);
            processed = sb.toString();
        }

        // Step 3: Parse with MiniMessage (handles all color codes, gradients, etc.)
        Component component = mm.deserialize(processed);

        // Step 4: Replace item placeholder tokens with actual item hover components
        if (config.isItemDisplayEnabled() && processed.contains(ITEM_PLACEHOLDER_TOKEN)) {
            Component itemComponent = buildItemComponent(sender);
            component = component.replaceText(
                    TextReplacementConfig.builder()
                    .matchLiteral(ITEM_PLACEHOLDER_TOKEN)
                            .replacement(itemComponent)
                            .build()
            );
        }

        return new ProcessedMessage(component, mentionedPlayers);
    }

    /**
     * Builds the item hover component for the player's main hand item.
     */
    private Component buildItemComponent(Player player) {
        ConfigManager config = plugin.getConfigManager();
        MiniMessage mm = config.getMiniMessage();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType() == Material.AIR || item.getAmount() == 0) {
            return mm.deserialize(config.getEmptyHandMessage());
        }

        String token = plugin.getItemPreviewManager().store(item, player.getName());
        String itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
            ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(item.getItemMeta().displayName())
            : formatMaterialName(item.getType().name());

        String text = "<gradient:#ffd66b:#ffe58f>@hand</gradient><gray>: <yellow>" + itemName
            + (item.getAmount() > 1 ? " <gray>x" + item.getAmount() : "");

        return mm.deserialize(text)
            .hoverEvent(HoverEvent.showText(mm.deserialize("<gray>Click to preview this item in a GUI")))
            .clickEvent(ClickEvent.runCommand("/chatitem " + token));
    }

    /**
     * Notifies mentioned players with a sound.
     */
    public void notifyMentionedPlayers(List<Player> mentioned, Player sender) {
        ConfigManager config = plugin.getConfigManager();
        if (!config.isMentionsEnabled() || mentioned.isEmpty()) return;

        Sound sound = config.getMentionSound();
        float volume = config.getMentionSoundVolume();
        float pitch = config.getMentionSoundPitch();

        for (Player player : mentioned) {
            if (player.isOnline() && !player.equals(sender)) {
                player.playSound(player.getLocation(), sound, volume, pitch);
                player.sendActionBar(config.getMentionActionbar(sender.getName()));
            }
        }
    }

    private String formatMaterialName(String materialName) {
        String[] words = materialName.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    /**
     * Holds a processed message and the list of mentioned players.
     */
    public record ProcessedMessage(Component component, List<Player> mentionedPlayers) {
    }
}
