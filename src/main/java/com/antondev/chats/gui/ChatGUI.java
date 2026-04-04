package com.antondev.chats.gui;

import com.antondev.chats.ChatChannel;
import com.antondev.chats.PlexonChats;
import com.antondev.chats.config.ConfigManager;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Interactive GUI for PlexonChats settings and channel selection.
 */
public class ChatGUI {

    private static final String DISCORD_TEXTURE_VALUE =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjU4NzU2ZmIzOGRjZTFiNWUzZmZkZWVlYmY4MTdhMDM3MmMxM2ZmZjdhZDdjMmU5OGFjZWQxYzU5MzIwZTlhNSJ9fX0=";
    private static final String GITHUB_TEXTURE_VALUE =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjJjYTkxODVkN2E5MGYwN2VhYjM1MTBjYzFhZGJlYmQwNzViY2MyOTU4YWY5MjQ4NTAyMTUwYThjYjQyYTQ2MSJ9fX0=";

    private final PlexonChats plugin;

    public ChatGUI(PlexonChats plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        openMain(player);
    }

    public void openMain(Player player) {
        ConfigManager config = plugin.getConfigManager();
        int rows = Math.max(5, config.getGuiRows());
        int size = rows * 9;

        Component title = config.getMiniMessage().deserialize(config.getGuiTitle());
        ChatGUIHolder holder = new ChatGUIHolder(ChatGUIHolder.Page.MAIN);
        Inventory gui = Bukkit.createInventory(holder, size, title);
        holder.setInventory(gui);

        ChatChannel currentChannel = plugin.getChatManager().getPlayerChannel(player);

        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, Component.empty(), null, false);
        for (int i = 0; i < size; i++) {
            int row = i / 9;
            int col = i % 9;
            if (row == 0 || row == rows - 1 || col == 0 || col == 8) {
                gui.setItem(i, border);
            }
        }

        boolean isLocal = currentChannel == ChatChannel.LOCAL;
        ItemStack localItem = createChannelItem(
                isLocal ? Material.LIME_CONCRETE : Material.WHITE_WOOL,
                "<gray><bold>Local Channel [L]",
                isLocal,
                List.of(
                        "<gray>Short-range world chat",
                        "<gray>Only players nearby can read",
                        "",
                        "<gray>Radius: <white>" + config.getLocalRadius() + " blocks",
                        "<gray>Tag style: <white>[L]",
                        "",
                        isLocal ? "<green>Currently active" : "<yellow>Click to activate"
                )
        );
        gui.setItem(10, localItem);

        boolean isGlobal = currentChannel == ChatChannel.GLOBAL;
        ItemStack globalItem = createChannelItem(
                isGlobal ? Material.LIME_CONCRETE : Material.GOLD_BLOCK,
                "<gold><bold>Global Channel [G]",
                isGlobal,
                List.of(
                        "<gray>Server-wide cross-world chat",
                        "<gray>Everyone online can read",
                        "",
                        "<gray>Shortcut: <white>" +
                                (config.getGlobalShortcutPrefix().isEmpty() ? "None" :
                                        config.getGlobalShortcutPrefix() + "<message>"),
                        "<gray>Tag style: <white>[G]",
                        "",
                        isGlobal ? "<green>Currently active" : "<yellow>Click to activate"
                )
        );
        gui.setItem(12, globalItem);

        ItemStack infoItem = createChannelItem(
                Material.BOOK,
                "<gradient:#00e6ff:#00ffac><bold>Advanced Features",
                false,
                List.of(
                        "<gray>Current channel: <white>" + currentChannel.getDisplayName(),
                        "",
                        "<gray>Features:",
                        "<gray>• <white>@player mentions",
                        "<gray>• <white>[item] and @hand display",
                        "<gray>• <white>/announce broadcasts",
                        "<gray>• <white>/msg and /reply tells",
                        "<gray>• <white>Hover sections in chat",
                        "",
                        "<gray>Use <white>/chat <gray>for more commands."
                )
        );
        gui.setItem(14, infoItem);

        ItemStack creatorPage = createCreatorButtonItem();
        gui.setItem(16, creatorPage);

        gui.setItem(30, createChannelItem(
                Material.PAPER,
                "<white><bold>Quick Tip",
                false,
                List.of(
                        "<gray>Click [G] or [L] in chat",
                        "<gray>to switch channels instantly.",
                        "",
                        "<gray>Click player/message text",
                        "<gray>to open private message flow."
                )
        ));

        gui.setItem(32, createChannelItem(
                Material.BELL,
                "<gold><bold>Announcements",
                false,
                List.of(
                        "<gray>Broadcast to all players:",
                        "<white>/announce <message>",
                        "",
                        "<gray>Permission:",
                        "<white>plexonchats.announce"
                )
        ));

        gui.setItem(34, createChannelItem(
                Material.BARRIER,
                "<red><bold>Close Menu",
                false,
                List.of("<gray>Close this interface")
        ));

        player.openInventory(gui);
    }

    public void openCreator(Player player) {
        ConfigManager config = plugin.getConfigManager();
        int size = 45;

        Component title = config.getMiniMessage().deserialize("<gradient:#00e6ff:#00ffac><bold>PlexonChats Creator</bold></gradient>");
        ChatGUIHolder holder = new ChatGUIHolder(ChatGUIHolder.Page.CREATOR);
        Inventory gui = Bukkit.createInventory(holder, size, title);
        holder.setInventory(gui);

        ItemStack border = createItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, Component.empty(), null, false);
        for (int i = 0; i < size; i++) {
            int row = i / 9;
            int col = i % 9;
            if (row == 0 || row == 4 || col == 0 || col == 8) {
                gui.setItem(i, border);
            }
        }

        gui.setItem(13, createCreatorHeadItem());

        gui.setItem(20, createLinkHeadItem(
                DISCORD_TEXTURE_VALUE,
                "<aqua><bold>Discord Contact",
                List.of(
                        "<gray>Reach the creator on Discord",
                        "<white>discord.com/users/348426610095161355",
                        "",
                        "<yellow>Click to receive a clickable link"
                )
        ));

        gui.setItem(22, createChannelItem(
                Material.BOOK,
                "<gold><bold>Other Projects",
                false,
                List.of(
                        "<gray>Spigot author resources:",
                        "<white>spigotmc.org/resources/authors/tonim.2341103",
                        "",
                        "<yellow>Click to receive a clickable link"
                )
        ));

        gui.setItem(24, createLinkHeadItem(
                GITHUB_TEXTURE_VALUE,
                "<white><bold>Creator GitHub",
                List.of(
                        "<gray>GitHub profile:",
                        "<white>github.com/ZpkDxGames",
                        "",
                        "<yellow>Click to receive a clickable link"
                )
        ));

        gui.setItem(40, createChannelItem(
                Material.ARROW,
                "<green><bold>Back",
                false,
                List.of("<gray>Return to main menu")
        ));

        gui.setItem(44, createChannelItem(
                Material.BARRIER,
                "<red><bold>Close",
                false,
                List.of("<gray>Close this menu")
        ));

        player.openInventory(gui);
    }

    private ItemStack createItem(Material material, Component name, List<Component> lore, boolean glint) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        if (lore != null) {
            meta.lore(lore);
        }
        if (glint) {
            meta.setEnchantmentGlintOverride(true);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createChannelItem(Material material, String name, boolean selected,
                                        List<String> loreLines) {
        ConfigManager config = plugin.getConfigManager();
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(config.getMiniMessage().deserialize(name)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(config.getMiniMessage().deserialize(line)
                    .decoration(TextDecoration.ITALIC, false));
        }
                if (!lore.isEmpty()) {
                        meta.lore(lore);
                }

        if (selected) {
            meta.setEnchantmentGlintOverride(true);
        }

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCreatorHeadItem() {
        ConfigManager config = plugin.getConfigManager();
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();

        OfflinePlayer creator = Bukkit.getOfflinePlayer("ZpkDxGames");
        meta.setOwningPlayer(creator);
        meta.displayName(config.formatMessage("<gradient:#00e6ff:#00ffac><bold>ZpkDxGames</bold></gradient>")
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = List.of(
                config.formatMessage("<gray>Plugin creator and maintainer"),
                config.formatMessage("<gray>MC name: <white>ZpkDxGames"),
                config.formatMessage("<gray>Use the items below for links.")
        );
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

        private ItemStack createCreatorButtonItem() {
                ConfigManager config = plugin.getConfigManager();
                ItemStack item = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) item.getItemMeta();

                OfflinePlayer creator = Bukkit.getOfflinePlayer("ZpkDxGames");
                meta.setOwningPlayer(creator);
                meta.displayName(config.formatMessage("<aqua><bold>Creator Page</bold></aqua>")
                                .decoration(TextDecoration.ITALIC, false));
                meta.lore(List.of(
                                config.formatMessage("<gray>Open the creator profile panel"),
                                config.formatMessage("<gray>with links and details for:"),
                                config.formatMessage("<white>ZpkDxGames"),
                                Component.empty(),
                                config.formatMessage("<yellow>Click to open")
                ));
                item.setItemMeta(meta);
                return item;
        }

        private ItemStack createLinkHeadItem(String textureValue, String name, List<String> loreLines) {
                ConfigManager config = plugin.getConfigManager();
                ItemStack item = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) item.getItemMeta();

                try {
                        PlayerProfile profile = Bukkit.getServer().createProfile(UUID.randomUUID());
                        profile.setProperty(new ProfileProperty("textures", textureValue));
                        meta.setPlayerProfile(profile);
                } catch (Exception ex) {
                        // Fallback: keep default head if texture URL fails.
                }

                meta.displayName(config.getMiniMessage().deserialize(name).decoration(TextDecoration.ITALIC, false));
                List<Component> lore = new ArrayList<>();
                for (String line : loreLines) {
                        lore.add(config.getMiniMessage().deserialize(line).decoration(TextDecoration.ITALIC, false));
                }
                meta.lore(lore);
                item.setItemMeta(meta);
                return item;
        }
}
