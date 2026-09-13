package com.antondev.chats.gui;

import com.antondev.chats.PlexonChats;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Cancel transfers for the entire view while routing only explicit safe button interactions. */
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

        if (rawHolder instanceof ItemPreviewHolder) {
            if (event.getRawSlot() == 22 && event.isLeftClick()) Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.getOpenInventory().getTopInventory() == top) player.closeInventory();
            });
            return;
        }

        ChatGUIHolder holder = (ChatGUIHolder) rawHolder;
        if (!holder.getViewer().equals(player.getUniqueId())) return;
        GuiButton button = holder.buttonAt(event.getRawSlot());
        if (button == null) return;

        boolean eventDefinition = button.action() == GuiAction.EVENT_DEFINITION;
        if (event.isShiftClick() && !(eventDefinition && event.getClick() == ClickType.SHIFT_LEFT)) return;
        if (!event.isLeftClick() && !event.isRightClick()) return;

        ClickType click = event.getClick();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != top) return;
            if (holder.getRevision() != plugin.getConfigManager().revision()) {
                plugin.getChatGUI().reopen(player, holder);
                return;
            }
            plugin.getChatGUI().click(player, holder, button, click);
        });
    }

    @EventHandler public void onDrag(InventoryDragEvent event) {
        var holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof ChatGUIHolder || holder instanceof ItemPreviewHolder) event.setCancelled(true);
    }
}
