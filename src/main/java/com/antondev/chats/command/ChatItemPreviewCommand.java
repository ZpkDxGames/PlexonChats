package com.antondev.chats.command;

import com.antondev.chats.PlexonChats;
import com.antondev.chats.config.ConfigManager;
import com.antondev.chats.gui.ItemPreviewHolder;
import com.antondev.chats.item.ItemPreviewManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Opens a simple GUI that displays a chat-shared item preview.
 */
public class ChatItemPreviewCommand implements CommandExecutor, TabCompleter {

    private final PlexonChats plugin;

    public ChatItemPreviewCommand(PlexonChats plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        ConfigManager config = plugin.getConfigManager();

        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getPlayerOnly());
            return true;
        }

        if (!player.hasPermission("plexonchats.item")) {
            player.sendMessage(config.getNoPermission());
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(config.getPrefixed("<red>Invalid item preview token."));
            return true;
        }

        ItemPreviewManager.StoredItem stored = plugin.getItemPreviewManager().get(args[0]);
        if (stored == null) {
            player.sendMessage(config.getPrefixed("<red>This item preview has expired."));
            return true;
        }

        openPreview(player, stored);
        return true;
    }

    private void openPreview(Player viewer, ItemPreviewManager.StoredItem stored) {
        ConfigManager config = plugin.getConfigManager();
        ItemPreviewHolder holder = new ItemPreviewHolder();
        Inventory inv = Bukkit.createInventory(holder, 27,
                config.formatMessage(config.string("item-display.preview.title", "Hand Item Preview")));
        holder.setInventory(inv);

        ItemStack item = stored.item().clone();
        inv.setItem(13, item);

        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(config.formatMessage("<yellow><bold>Item Details</bold></yellow>"));
        List<Component> lore = new ArrayList<>();
        lore.add(config.formatMessage("<gray>Shared by: <white>" + stored.ownerName()));
        lore.add(config.formatMessage("<gray>Material: <white>" + item.getType().name()));
        lore.add(config.formatMessage("<gray>Amount: <white>" + item.getAmount()));
        lore.add(Component.empty());

        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta != null && itemMeta.hasDisplayName()) {
            lore.add(config.formatMessage("<gray>Name: ").append(itemMeta.displayName()));
        }

        if (itemMeta != null && itemMeta.hasLore() && itemMeta.lore() != null) {
            lore.add(config.formatMessage("<gray>Lore lines: <white>" + itemMeta.lore().size()));
        } else {
            lore.add(config.formatMessage("<gray>Lore lines: <white>0"));
        }

        lore.add(config.formatMessage("<gray>Shared at: <white>"
                + new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date(stored.createdAtMillis()))));
        infoMeta.lore(lore);
        info.setItemMeta(infoMeta);
        inv.setItem(11, info);

        ItemStack enchants = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta enchMeta = enchants.getItemMeta();
        enchMeta.displayName(config.formatMessage("<aqua><bold>Enchantments</bold></aqua>"));
        List<Component> enchantLore = new ArrayList<>();
        Map<Enchantment, Integer> enchantMap = item.getEnchantments();
        if (enchantMap.isEmpty()) {
            enchantLore.add(config.formatMessage("<gray>No enchantments"));
        } else {
            enchantMap.forEach((enchantment, level) ->
                    enchantLore.add(config.formatMessage("<gray>• <white>" + enchantment.getKey().getKey()
                            + " <gray>Lv<white>" + level)));
        }
        enchMeta.lore(enchantLore);
        enchants.setItemMeta(enchMeta);
        inv.setItem(15, enchants);

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.displayName(config.formatMessage("<red><bold>Close</bold></red>"));
        close.setItemMeta(closeMeta);
        inv.setItem(22, close);

        viewer.openInventory(inv);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        return Collections.emptyList();
    }
}
