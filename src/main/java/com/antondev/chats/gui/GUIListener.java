package com.antondev.chats.gui;

import com.antondev.chats.ChatChannel;
import com.antondev.chats.PlexonChats;
import com.antondev.chats.config.ConfigManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Handles click events within the PlexonChats GUI.
 */
public class GUIListener implements Listener {

    private final PlexonChats plugin;

    public GUIListener(PlexonChats plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof ItemPreviewHolder) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;
            if (!(event.getWhoClicked() instanceof Player player)) return;
            if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;

            if (event.getRawSlot() == 22) {
                player.closeInventory();
            }
            return;
        }

        if (!(event.getInventory().getHolder() instanceof ChatGUIHolder holder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getCurrentItem() == null) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;

        ConfigManager config = plugin.getConfigManager();
        int slot = event.getRawSlot();
        ChatGUI gui = new ChatGUI(plugin);

        if (holder.getPage() == ChatGUIHolder.Page.MAIN) {
            switch (slot) {
                case 10 -> {
                    if (!player.hasPermission("plexonchats.local")) {
                        player.sendMessage(config.getNoPermission());
                        return;
                    }
                    boolean changed = plugin.getChatManager().setPlayerChannel(player, ChatChannel.LOCAL);
                    player.sendMessage(changed
                            ? config.getChannelSwitched(ChatChannel.LOCAL)
                            : config.getChannelAlready(ChatChannel.LOCAL));
                    gui.openMain(player);
                }
                case 12 -> {
                    if (!player.hasPermission("plexonchats.global")) {
                        player.sendMessage(config.getNoPermission());
                        return;
                    }
                    boolean changed = plugin.getChatManager().setPlayerChannel(player, ChatChannel.GLOBAL);
                    player.sendMessage(changed
                            ? config.getChannelSwitched(ChatChannel.GLOBAL)
                            : config.getChannelAlready(ChatChannel.GLOBAL));
                    gui.openMain(player);
                }
                case 16 -> gui.openCreator(player);
                case 34 -> player.closeInventory();
            }
            return;
        }

        switch (slot) {
            case 20 -> sendLink(player, "Discord", "https://discord.com/users/348426610095161355");
            case 22 -> sendLink(player, "Spigot Projects", "https://www.spigotmc.org/resources/authors/tonim.2341103/");
            case 24 -> sendLink(player, "GitHub", "https://github.com/ZpkDxGames");
            case 40 -> gui.openMain(player);
            case 44 -> player.closeInventory();
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ChatGUIHolder
                || event.getInventory().getHolder() instanceof ItemPreviewHolder) {
            event.setCancelled(true);
        }
    }

    private void sendLink(Player player, String label, String url) {
        ConfigManager config = plugin.getConfigManager();
        player.sendMessage(config.getPrefixed("<gray>" + label + ": <white>" + url));
        player.sendMessage(config.formatMessage("<click:open_url:'" + url + "'><aqua><underlined>Click here to open "
                + label + "</underlined></aqua></click>"));
    }
}
