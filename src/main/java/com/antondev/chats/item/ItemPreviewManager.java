package com.antondev.chats.item;

import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores temporary chat item previews referenced by tokens.
 */
public class ItemPreviewManager {

    private static final long TTL_MILLIS = 30L * 60L * 1000L;

    private final Map<String, StoredItem> previews = new ConcurrentHashMap<>();

    public String store(ItemStack item, String ownerName) {
        cleanupExpired();

        String token;
        do {
            token = UUID.randomUUID().toString().substring(0, 8);
        } while (previews.containsKey(token));

        previews.put(token, new StoredItem(item.clone(), ownerName, System.currentTimeMillis()));
        return token;
    }

    public StoredItem get(String token) {
        cleanupExpired();
        return previews.get(token);
    }

    public void clear() {
        previews.clear();
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        previews.entrySet().removeIf(entry -> now - entry.getValue().createdAtMillis() > TTL_MILLIS);
    }

    public record StoredItem(ItemStack item, String ownerName, long createdAtMillis) {
    }
}
