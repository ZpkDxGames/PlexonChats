package com.antondev.chats.automessage;

import com.antondev.chats.PlexonChats;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.LongSupplier;

/** One timer for all groups, no catch-up bursts and no duplicated tasks after reload. */
public final class AutoMessageManager implements AutoCloseable {
    private static final long SECOND = 1_000_000_000L;
    private static final class RuntimeGroup {
        final MessageGroup definition;
        final MessageRotation<MessageGroup.Entry> rotation;
        long nextDue;
        RuntimeGroup(MessageGroup definition, long now) {
            this.definition = definition;
            rotation = new MessageRotation<>(definition.entries(), definition.shuffle(), new Random());
            nextDue = now + definition.initialDelaySeconds() * SECOND;
        }
    }
    private final PlexonChats plugin;
    private final LongSupplier clock;
    private final Map<String, RuntimeGroup> groups = new LinkedHashMap<>();
    private BukkitTask task;
    private boolean paused;

    public AutoMessageManager(PlexonChats plugin) { this(plugin, System::nanoTime); }
    public AutoMessageManager(PlexonChats plugin, LongSupplier clock) { this.plugin = plugin; this.clock = clock; }

    public void reload() {
        close();
        groups.clear();
        paused = false;
        var section = plugin.getConfigManager().section("auto-messages.groups");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                var entry = section.getConfigurationSection(id);
                if (entry == null || !entry.getBoolean("enabled", true)) continue;
                var definition = MessageGroup.read(id, entry, plugin.getLogger()::warning);
                if (!definition.entries().isEmpty()) groups.put(id, new RuntimeGroup(definition, clock.getAsLong()));
                if (groups.size() == 100) { plugin.getLogger().warning("Only the first 100 message groups are loaded."); break; }
            }
        }
        if (enabled() && !groups.isEmpty()) task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20, 20);
    }

    public boolean enabled() { return plugin.getConfigManager().bool("auto-messages.enabled", true); }
    public boolean paused() { return paused; }
    public String status() { return !enabled() ? "DISABLED" : paused ? "PAUSED" : "RUNNING"; }
    public Set<String> groupNames() { return java.util.Collections.unmodifiableSet(groups.keySet()); }
    public boolean taskActive() { return task != null && !task.isCancelled(); }
    public void pause() { paused = true; }
    public void resume() {
        paused = false;
        long now = clock.getAsLong();
        groups.values().forEach(group -> group.nextDue = now + group.definition.intervalSeconds() * SECOND);
    }

    public void tick() {
        if (!enabled() || paused) return;
        long now = clock.getAsLong();
        for (RuntimeGroup group : groups.values()) {
            if (now < group.nextDue) continue;
            broadcast(group);
            // A late tick never tries to replay all missed intervals.
            group.nextDue = now + group.definition.intervalSeconds() * SECOND;
        }
    }

    public int sendNow(String id) {
        RuntimeGroup group = groups.get(id);
        if (group == null || !enabled()) return 0;
        int sent = broadcast(group);
        group.nextDue = clock.getAsLong() + group.definition.intervalSeconds() * SECOND;
        return sent;
    }

    public boolean preview(String id, CommandSender viewer) {
        RuntimeGroup group = groups.get(id);
        if (group == null) return false;
        MessageGroup.Entry entry = group.rotation.peek();
        if (entry == null) return false;
        // Preview is chat-only, private, silent, and does not advance the rotation/timer.
        viewer.sendMessage(render(entry.lines(), viewer instanceof Player player ? player : null));
        return true;
    }

    private int broadcast(RuntimeGroup group) {
        if (Bukkit.getOnlinePlayers().size() < group.definition.minOnline()) return 0;
        List<? extends Player> recipients = Bukkit.getOnlinePlayers().stream().filter(p -> group.definition.accepts(p, plugin)).toList();
        if (recipients.isEmpty()) return 0;
        MessageGroup.Entry entry = group.rotation.next();
        if (entry == null) return 0;
        var sound = plugin.getConfigManager().resolveSound(group.definition.sound());
        for (Player player : recipients) {
            switch (entry.delivery()) {
                case CHAT -> player.sendMessage(render(entry.lines(), player));
                case ACTION_BAR -> player.sendActionBar(plugin.getText().render(String.join(" ", entry.lines()), player));
                case TITLE -> {
                    Component title = plugin.getText().render(entry.lines().getFirst(), player);
                    Component subtitle = entry.lines().size() > 1 ? plugin.getText().render(entry.lines().get(1), player) : Component.empty();
                    player.showTitle(Title.title(title, subtitle, Title.Times.times(
                            seconds(entry.fadeIn()), seconds(entry.stay()), seconds(entry.fadeOut()))));
                }
            }
            if (sound != null) player.playSound(player.getLocation(), sound, group.definition.volume(), group.definition.pitch());
        }
        if (group.definition.forwardToDiscord() && group.definition.unrestricted() && entry.delivery() == MessageGroup.Delivery.CHAT) {
            plugin.getDiscordBridge().sendAnnouncement(render(entry.lines(), null));
        }
        return recipients.size();
    }

    private static Duration seconds(double value) { return Duration.ofMillis((long) (value * 1000)); }
    private Component render(List<String> lines, Player player) {
        return Component.join(JoinConfiguration.newlines(), lines.stream().map(line -> plugin.getText().render(line, player)).toList());
    }
    @Override public void close() { if (task != null) { task.cancel(); task = null; } }
}
