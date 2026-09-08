package com.antondev.chats.player;

import com.antondev.chats.ChatChannel;
import com.antondev.chats.PlexonChats;
import com.antondev.chats.config.AtomicFiles;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Main-thread state with immutable snapshots written by one ordered background worker. */
public final class PreferenceStore implements AutoCloseable {
    private final PlexonChats plugin;
    private final Path path;
    private final Map<UUID, PlayerPreferences> values = new HashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean();
    private final AtomicBoolean writing = new AtomicBoolean();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "PlexonChats-preferences");
        thread.setDaemon(true);
        return thread;
    });
    private boolean writable = true;
    private BukkitTask task;

    public PreferenceStore(PlexonChats plugin) {
        this.plugin = plugin;
        path = plugin.getDataFolder().toPath().resolve("players.yml");
        if (Files.exists(path)) {
            YamlConfiguration yaml = new YamlConfiguration();
            try {
                yaml.load(path.toFile());
                for (String key : yaml.getKeys(false)) {
                    UUID id = UUID.fromString(key);
                    if (!yaml.isConfigurationSection(key)) throw new IllegalArgumentException("Invalid player section " + key);
                    values.put(id, new PlayerPreferences(ChatChannel.fromName(yaml.getString(key + ".channel", "")),
                            yaml.getBoolean(key + ".mentions", true), yaml.getBoolean(key + ".tips", true),
                            yaml.getBoolean(key + ".private-messages", true)));
                }
            } catch (IOException | InvalidConfigurationException | IllegalArgumentException ex) {
                writable = false;
                values.clear();
                plugin.getLogger().severe("players.yml could not be loaded and will NOT be overwritten: " + ex.getMessage());
            }
        }
        reloadTimer();
    }

    public PlayerPreferences get(UUID id) { return values.getOrDefault(id, PlayerPreferences.defaults()); }
    public void set(UUID id, PlayerPreferences value) { values.put(id, value); dirty.set(true); }

    public void reloadTimer() {
        if (task != null) task.cancel();
        long interval = plugin.getConfigManager().number("preferences.save-interval-seconds", 60, 5, 3600) * 20;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::flush, interval, interval);
    }

    public void flush() {
        if (!writable || !dirty.getAndSet(false)) return;
        Map<UUID, PlayerPreferences> snapshot = Map.copyOf(values);
        writer.execute(() -> {
            writing.set(true);
            try {
                YamlConfiguration yaml = new YamlConfiguration();
                snapshot.forEach((id, preference) -> {
                    String key = id.toString();
                    if (preference.channel() != null) yaml.set(key + ".channel", preference.channel().name());
                    yaml.set(key + ".mentions", preference.mentions());
                    yaml.set(key + ".tips", preference.tips());
                    yaml.set(key + ".private-messages", preference.privateMessages());
                });
                AtomicFiles.write(path, yaml.saveToString());
            } catch (IOException ex) {
                dirty.set(true);
                plugin.getLogger().warning("Player preferences could not be saved; will retry: " + ex.getMessage());
            } finally {
                writing.set(false);
            }
        });
    }

    public boolean writable() { return writable; }
    public boolean dirty() { return dirty.get(); }
    public String writerState() { return writing.get() ? "RUNNING" : writer.isShutdown() ? "STOPPED" : "IDLE"; }
    public boolean saveTaskActive() { return task != null && !task.isCancelled(); }

    @Override public void close() {
        if (task != null) task.cancel();
        flush();
        writer.shutdown();
        try {
            if (!writer.awaitTermination(5, TimeUnit.SECONDS)) plugin.getLogger().warning("Preference save is still finishing after shutdown.");
        } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
    }
}
