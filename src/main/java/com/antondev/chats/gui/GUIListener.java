package com.antondev.chats.gui;

import com.antondev.chats.PlexonChats;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Cancel transfers for the entire view, including shift-clicks, hotbar swaps and drags. */
public final class GUIListener implements Listener {
    private final PlexonChats plugin;
    public GUIListener(PlexonChats plugin) { this.plugin = plugin; }

    @EventHandler public void onClick(InventoryClickEvent event) {
        var top = event.getView().getTopInventory();
        var rawHolder = top.getHolder();
        if (!(rawHolder instanceof ChatGUIHolder) && !(rawHolder instanceof ItemPreviewHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= top.getSize()) return;
        if (event.isShiftClick() || (!event.isLeftClick() && !event.isRightClick())) return;

        if (rawHolder instanceof ItemPreviewHolder) {
            if (event.getRawSlot() == 22) Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.getOpenInventory().getTopInventory() == top) player.closeInventory();
            });
            return;
        }
        ChatGUIHolder holder = (ChatGUIHolder) rawHolder;
        if (!holder.getViewer().equals(player.getUniqueId())) return;
        GuiButton button = holder.buttonAt(event.getRawSlot());
        if (button == null) return;
        // Bukkit inventory opens/closes are deferred out of the inventory transaction.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != top) return;
            if (holder.getRevision() != plugin.getConfigManager().revision()) {
                plugin.getChatGUI().openPage(player, holder.getPage());
                return;
            }
            plugin.getChatGUI().click(player, holder, button);
        });
    }

    @EventHandler public void onDrag(InventoryDragEvent event) {
        var holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof ChatGUIHolder || holder instanceof ItemPreviewHolder) event.setCancelled(true);
    }
}
