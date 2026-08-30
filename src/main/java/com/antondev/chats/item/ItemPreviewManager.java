package com.antondev.chats.item;

import com.antondev.chats.PlexonChats;
import org.bukkit.inventory.ItemStack;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Bounded, insertion-ordered cache. Only expired oldest entries need to be visited. Main thread only. */
public final class ItemPreviewManager {
    private final PlexonChats plugin;
    private final LinkedHashMap<String, StoredItem> previews = new LinkedHashMap<>();
    public ItemPreviewManager(PlexonChats plugin) { this.plugin = plugin; }

    public String store(ItemStack item, String ownerName) {
        cleanupExpired();
        int max = plugin.getConfigManager().integer("item-display.preview.max-entries", 1000, 1, 10_000);
        while (previews.size() >= max) previews.pollFirstEntry();
        String token = UUID.randomUUID().toString();
        previews.put(token, new StoredItem(item.clone(), ownerName, System.currentTimeMillis()));
        return token;
    }

    public StoredItem get(String token) { cleanupExpired(); return previews.get(token); }
    public void clear() { previews.clear(); }
    public void cleanupExpired() {
        long cutoff = System.currentTimeMillis() - plugin.getConfigManager().number("item-display.preview.expire-seconds", 1800, 10, 86_400) * 1000;
        while (!previews.isEmpty() && previews.firstEntry().getValue().createdAtMillis() <= cutoff) previews.pollFirstEntry();
    }
    public int size() { return previews.size(); }
    public record StoredItem(ItemStack item, String ownerName, long createdAtMillis) {}
}
