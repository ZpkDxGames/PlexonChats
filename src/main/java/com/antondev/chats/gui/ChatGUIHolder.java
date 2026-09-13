package com.antondev.chats.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;

/** Immutable GUI routing token. Revision/context values make stale inventories harmless. */
public final class ChatGUIHolder implements InventoryHolder {
    public enum Page { MAIN, ADMIN, EVENTS, EVENT_LIST, EVENT_DETAILS, EVENT_REWARDS, EVENT_BINGO, EVENT_STATS, CREATOR }

    private final Page page;
    private final UUID viewer;
    private final long revision;
    private final Map<Integer, GuiButton> buttons;
    private final String context;
    private final int pageIndex;
    private final UUID activeRunId;
    private Inventory inventory;

    public ChatGUIHolder(Page page, UUID viewer, long revision, Map<Integer, GuiButton> buttons) {
        this(page, viewer, revision, buttons, "", 0, null);
    }

    public ChatGUIHolder(Page page, UUID viewer, long revision, Map<Integer, GuiButton> buttons,
                         String context, int pageIndex, UUID activeRunId) {
        this.page = page;
        this.viewer = viewer;
        this.revision = revision;
        this.buttons = Map.copyOf(buttons);
        this.context = context == null ? "" : context;
        this.pageIndex = Math.max(0, pageIndex);
        this.activeRunId = activeRunId;
    }

    public void setInventory(Inventory inventory) { this.inventory = inventory; }
    @Override public @NotNull Inventory getInventory() {
        if (inventory == null) throw new IllegalStateException("Inventory has not been initialized");
        return inventory;
    }
    public Page getPage() { return page; }
    public UUID getViewer() { return viewer; }
    public long getRevision() { return revision; }
    public String getContext() { return context; }
    public int getPageIndex() { return pageIndex; }
    public UUID getActiveRunId() { return activeRunId; }
    public GuiButton buttonAt(int slot) { return buttons.get(slot); }
}
