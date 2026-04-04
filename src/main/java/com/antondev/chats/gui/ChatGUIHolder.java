package com.antondev.chats.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Custom InventoryHolder to identify PlexonChats GUIs.
 */
public class ChatGUIHolder implements InventoryHolder {

    public enum Page {
        MAIN,
        CREATOR
    }

    private final Page page;
    private Inventory inventory;

    public ChatGUIHolder(Page page) {
        this.page = page;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("Chat GUI inventory has not been initialized yet.");
        }
        return inventory;
    }

    public Page getPage() {
        return page;
    }
}
