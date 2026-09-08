package com.antondev.chats.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import java.util.Map;
import java.util.UUID;

public final class ChatGUIHolder implements InventoryHolder {
    public enum Page { MAIN, ADMIN, CREATOR }
    private final Page page;
    private final UUID viewer;
    private final long revision;
    private final Map<Integer, GuiButton> buttons;
    private Inventory inventory;
    public ChatGUIHolder(Page page, UUID viewer, long revision, Map<Integer, GuiButton> buttons) {
        this.page = page; this.viewer = viewer; this.revision = revision; this.buttons = Map.copyOf(buttons);
    }
    public void setInventory(Inventory inventory) { this.inventory = inventory; }
    @Override public @NotNull Inventory getInventory() {
        if (inventory == null) throw new IllegalStateException("Inventory has not been initialized");
        return inventory;
    }
    public Page getPage() { return page; }
    public UUID getViewer() { return viewer; }
    public long getRevision() { return revision; }
    public GuiButton buttonAt(int slot) { return buttons.get(slot); }
}
