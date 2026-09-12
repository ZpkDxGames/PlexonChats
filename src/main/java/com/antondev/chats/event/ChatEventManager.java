package com.antondev.chats.event;

import com.antondev.chats.ChatChannel;
import com.antondev.chats.PlexonChats;
import com.antondev.chats.text.ComponentTemplate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

/** Owns the single Chat Events coordinator, active competition and exact-once completion path. */
public final class ChatEventManager {
    public enum StartStatus { STARTED, DISABLED, ALREADY_ACTIVE, NOT_FOUND, EVENT_DISABLED, COOLDOWN, NOT_ENOUGH_PLAYERS, NO_ELIGIBLE_EVENTS, GENERATION_FAILED }

    private final PlexonChats plugin;
    private final ChatEventRewardService rewards;
    private final ComponentTemplate templates = new ComponentTemplate(MiniMessage.miniMessage());
    private final Map<ChatEventEngine.Type, ChatEventEngine.Generator> generators = ChatEventEngine.generators();
    private final AtomicReference<ChatEventEngine.Competition> active = new AtomicReference<>();
    private final Map<String, Long> cooldownUntilNanos = new LinkedHashMap<>();
    private volatile ChatEventConfig config;
    private BukkitTask coordinatorTask;
    private boolean paused;
    private long nextDeadlineNanos = Long.MAX_VALUE;
    private String lastStartedId;
    private String lastEvent = "NONE";
    private String lastWinner = "-";
    private String recentFailure = "NONE";

    public ChatEventManager(PlexonChats plugin) { this.plugin = plugin; this.rewards = new ChatEventRewardService(plugin); }

    public void reload() {
        cancelActive("CONFIG_RELOAD", true);
        stopCoordinator();
        ChatEventConfig next = ChatEventConfig.read(plugin.getConfigManager().section("chat-events"));
        config = next;
        rewards.reload(next);
        cooldownUntilNanos.clear();
        lastStartedId = null;
        paused = false;
        long now = System.nanoTime();
        nextDeadlineNanos = now + seconds(next.scheduler().initialDelaySeconds());
        if (next.enabled()) coordinatorTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void close() { cancelActive("PLUGIN_DISABLE", false); stopCoordinator(); config = null; }
    public void refreshIntegrations() { rewards.refreshIntegrations(); }
    private void stopCoordinator() { if (coordinatorTask != null) coordinatorTask.cancel(); coordinatorTask = null; }

    private void tick() {
        ChatEventConfig current = config;
        if (current == null || !current.enabled()) return;
        long now = System.nanoTime();
        ChatEventEngine.Competition running = active.get();
        if (running != null) {
            if (running.state() == ChatEventEngine.State.ACTIVE && now >= running.deadlineNanos()) timeout(running);
            return;
        }
        if (!current.scheduler().enabled() || paused || now < nextDeadlineNanos) return;
        if (startRandom(true) != StartStatus.STARTED) {
            long defer = Math.max(10, Math.min(60, current.scheduler().minIntervalSeconds()));
            nextDeadlineNanos = now + seconds(defer);
        }
    }

    public boolean acceptAnswer(Player player, ChatChannel channel, String rawAnswer) {
        ChatEventEngine.Competition running = active.get();
        if (running == null || running.state() != ChatEventEngine.State.ACTIVE) return false;
        ChatEventConfig.Definition definition = running.round().definition();
        if (!definition.acceptedChannels().contains(channel) || !eligibleNow(player, definition)) return false;
        if (!running.tryWin(player.getUniqueId(), player.getName(), rawAnswer)) return false;
        if (Bukkit.isPrimaryThread()) completeWinner(running, player);
        else Bukkit.getScheduler().runTask(plugin, () -> completeWinner(running, player));
        return true;
    }

    private void completeWinner(ChatEventEngine.Competition running, Player player) {
        if (running.state() != ChatEventEngine.State.WON || !running.beginReward()) return;
        ChatEventRewardService.RewardResult reward;
        try { reward = rewards.grant(player, running.round().definition()); }
        catch (RuntimeException | LinkageError ex) {
            plugin.getDiagnostics().recordIntegrationFailure("chat-events-reward", ex);
            reward = new ChatEventRewardService.RewardResult(List.of(new ChatEventRewardService.ComponentResult(
                    "BUNDLE", ChatEventRewardService.Status.FAILED, ex.getClass().getSimpleName(), "")));
        }
        long elapsed = Math.max(0, System.nanoTime() - running.startedNanos());
        Map<String, Component> values = baseValues(running);
        values.put("winner", Component.text(player.getName()));
        values.put("winner_uuid", Component.text(player.getUniqueId().toString()));
        values.put("elapsed", Component.text(formatElapsed(elapsed)));
        values.put("elapsed_ms", Component.text(Long.toString(elapsed / 1_000_000L)));
        values.put("reward_summary", Component.text(reward.summary()));
        announce(running, message("winner", values));
        play(running, "win");
        lastEvent = running.round().definition().id() + "/WON";
        lastWinner = player.getName();
        if (reward.hasFailure()) recentFailure = "reward partial failure for " + running.round().definition().id();
        active.compareAndSet(running, null);
        scheduleNextInterval();
        plugin.getLogger().info("Chat event won: run=" + running.round().runId() + " winner=" + player.getUniqueId() + "/" + player.getName()
                + " elapsed=" + formatElapsed(elapsed) + " reward=" + running.round().definition().rewardProfile());
    }

    private void timeout(ChatEventEngine.Competition running) {
        if (!running.timeout()) return;
        ChatEventConfig.Definition definition = running.round().definition();
        if (definition.revealAnswerOnTimeout()) {
            Map<String, Component> values = baseValues(running);
            values.put("answer", Component.text(running.round().canonicalAnswer()));
            announce(running, message("timed-out", values));
        }
        play(running, "timeout");
        lastEvent = definition.id() + "/TIMED_OUT";
        lastWinner = "-";
        active.compareAndSet(running, null);
        scheduleNextInterval();
        plugin.getLogger().info("Chat event timed out: run=" + running.round().runId() + " definition=" + definition.id());
    }

    public boolean stop() { return cancelActive("ADMIN_CANCEL", true); }
    private boolean cancelActive(String reason, boolean broadcast) {
        ChatEventEngine.Competition running = active.get();
        if (running == null || !running.cancel()) return false;
        if (broadcast) announce(running, message("cancelled", baseValues(running)));
        lastEvent = running.round().definition().id() + "/CANCELLED";
        lastWinner = "-";
        active.compareAndSet(running, null);
        scheduleNextInterval();
        plugin.getLogger().info("Chat event cancelled: run=" + running.round().runId() + " reason=" + reason);
        return true;
    }

    public StartStatus start(String id) {
        ChatEventConfig current = config;
        if (current == null || !current.enabled()) return StartStatus.DISABLED;
        ChatEventConfig.Definition definition = current.definitions().get(id);
        if (definition == null) return StartStatus.NOT_FOUND;
        return startDefinition(definition, false);
    }

    public StartStatus startRandom(boolean scheduled) {
        ChatEventConfig current = config;
        if (current == null || !current.enabled()) return StartStatus.DISABLED;
        if (active.get() != null) return StartStatus.ALREADY_ACTIVE;
        List<ChatEventConfig.Definition> eligible = selectableDefinitions(System.nanoTime());
        if (eligible.isEmpty()) return StartStatus.NO_ELIGIBLE_EVENTS;
        return startDefinition(weighted(eligible), scheduled);
    }

    private StartStatus startDefinition(ChatEventConfig.Definition definition, boolean scheduled) {
        ChatEventConfig current = config;
        if (current == null || !current.enabled()) return StartStatus.DISABLED;
        if (active.get() != null) return StartStatus.ALREADY_ACTIVE;
        if (!definition.enabled()) return StartStatus.EVENT_DISABLED;
        long now = System.nanoTime();
        if (cooldownUntilNanos.getOrDefault(definition.id(), 0L) > now) return StartStatus.COOLDOWN;
        List<Player> participants = eligiblePlayers(definition);
        int minimum = Math.max(definition.minOnline(), scheduled ? current.scheduler().minOnline() : 0);
        if (participants.size() < minimum) return StartStatus.NOT_ENOUGH_PLAYERS;
        ChatEventEngine.Generator generator = generators.get(definition.type());
        if (generator == null) return StartStatus.GENERATION_FAILED;
        final ChatEventEngine.Round round;
        try { round = generator.generate(definition, ThreadLocalRandom.current()); }
        catch (RuntimeException ex) {
            recentFailure = "generator " + definition.id() + ": " + ex.getClass().getSimpleName();
            plugin.getDiagnostics().recordIntegrationFailure("chat-events-generator", ex);
            return StartStatus.GENERATION_FAILED;
        }
        Set<UUID> audience = participants.stream().map(Player::getUniqueId).collect(java.util.stream.Collectors.toUnmodifiableSet());
        ChatEventEngine.Competition competition = new ChatEventEngine.Competition(round, now, now + seconds(definition.durationSeconds()), audience);
        if (!active.compareAndSet(null, competition)) return StartStatus.ALREADY_ACTIVE;
        if (!competition.activate()) { active.compareAndSet(competition, null); return StartStatus.GENERATION_FAILED; }
        cooldownUntilNanos.put(definition.id(), now + seconds(definition.cooldownSeconds()));
        lastStartedId = definition.id();
        Component prompt = renderPrompt(round);
        Map<String, Component> values = baseValues(competition);
        values.put("prompt", prompt);
        announce(competition, startedMessage(values));
        play(competition, "start");
        plugin.getLogger().info("Chat event started: run=" + round.runId() + " definition=" + definition.id() + " type=" + definition.type()
                + " participants=" + participants.size());
        return StartStatus.STARTED;
    }

    public void preview(String id, CommandSender sender) {
        ChatEventConfig current = config;
        if (current == null) { sender.sendMessage(Component.text("Chat Events are unavailable.")); return; }
        ChatEventConfig.Definition definition = current.definitions().get(id);
        if (definition == null) { sender.sendMessage(Component.text("Unknown Chat Event: " + id)); return; }
        try {
            ChatEventEngine.Round round = generators.get(definition.type()).generate(definition, ThreadLocalRandom.current());
            sender.sendMessage(Component.text("Chat Event preview — " + definition.id() + " / " + definition.type()));
            sender.sendMessage(renderPrompt(round));
            sender.sendMessage(Component.text("Answer(s): " + String.join(", ", round.acceptedAnswers())));
            sender.sendMessage(Component.text("Reward: " + rewardDescription(definition.rewardProfile())));
            sender.sendMessage(Component.text("Duration: " + definition.durationSeconds() + "s • cooldown: " + definition.cooldownSeconds() + "s"));
        } catch (RuntimeException ex) { sender.sendMessage(Component.text("Preview failed: " + ex.getMessage())); }
    }

    public void pause() { paused = true; }
    public void resume() {
        paused = false;
        ChatEventConfig current = config;
        if (current != null) nextDeadlineNanos = System.nanoTime() + seconds(current.scheduler().initialDelaySeconds());
    }

    public boolean hasActiveEvent() { return active.get() != null; }
    public boolean enabled() { ChatEventConfig current = config; return current != null && current.enabled(); }
    public boolean schedulerEnabled() { ChatEventConfig current = config; return current != null && current.scheduler().enabled(); }
    public boolean paused() { return paused; }
    public boolean taskActive() { return coordinatorTask != null && !coordinatorTask.isCancelled(); }
    public String activeId() { ChatEventEngine.Competition value = active.get(); return value == null ? "NONE" : value.round().definition().id(); }
    public String activeType() { ChatEventEngine.Competition value = active.get(); return value == null ? "-" : value.round().definition().type().name(); }
    public long remainingMillis() {
        ChatEventEngine.Competition value = active.get();
        return value == null ? -1 : Math.max(0, (value.deadlineNanos() - System.nanoTime()) / 1_000_000L);
    }
    public String lastEvent() { return lastEvent; }
    public String lastWinner() { return lastWinner; }
    public String recentFailure() { return recentFailure; }
    public String economyState() { return rewards.economyState(); }
    public String keysState() { return rewards.keysState(); }
    public Set<String> eventIds() { ChatEventConfig current = config; return current == null ? Set.of() : current.definitions().keySet(); }
    public int configuredCount() { ChatEventConfig current = config; return current == null ? 0 : current.definitions().size(); }
    public int eligibleScheduledCount() { return selectableDefinitions(System.nanoTime()).size(); }

    public List<String> listStatus() {
        ChatEventConfig current = config;
        if (current == null) return List.of();
        long now = System.nanoTime();
        List<String> lines = new ArrayList<>();
        for (ChatEventConfig.Definition definition : current.definitions().values()) {
            long cooldown = Math.max(0, (cooldownUntilNanos.getOrDefault(definition.id(), 0L) - now) / 1_000_000_000L);
            lines.add(definition.id() + " — " + definition.type() + " — " + (definition.enabled() ? "ENABLED" : "DISABLED")
                    + " — reward=" + definition.rewardProfile() + " — weight=" + definition.weight() + " — cooldown=" + cooldown + "s");
        }
        return List.copyOf(lines);
    }

    public String status() {
        if (!enabled()) return "DISABLED";
        if (!schedulerEnabled()) return "MANUAL ONLY" + (active.get() == null ? "" : " / active=" + activeId());
        if (paused) return "PAUSED" + (active.get() == null ? "" : " / active=" + activeId());
        return "RUNNING" + (active.get() == null ? "" : " / active=" + activeId());
    }

    private List<ChatEventConfig.Definition> selectableDefinitions(long now) {
        ChatEventConfig current = config;
        if (current == null || !current.enabled()) return List.of();
        List<ChatEventConfig.Definition> values = current.definitions().values().stream()
                .filter(ChatEventConfig.Definition::enabled)
                .filter(definition -> definition.weight() > 0)
                .filter(definition -> cooldownUntilNanos.getOrDefault(definition.id(), 0L) <= now)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (current.scheduler().avoidImmediateRepeat() && values.size() > 1 && lastStartedId != null) values.removeIf(definition -> definition.id().equals(lastStartedId));
        return values;
    }

    private ChatEventConfig.Definition weighted(List<ChatEventConfig.Definition> definitions) {
        long total = 0;
        for (ChatEventConfig.Definition definition : definitions) total = Math.addExact(total, definition.weight());
        long target = ThreadLocalRandom.current().nextLong(total);
        for (ChatEventConfig.Definition definition : definitions) { target -= definition.weight(); if (target < 0) return definition; }
        return definitions.getLast();
    }

    private List<Player> eligiblePlayers(ChatEventConfig.Definition definition) {
        List<Player> result = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) if (eligibleNow(player, definition)) result.add(player);
        return List.copyOf(result);
    }

    private boolean eligibleNow(Player player, ChatEventConfig.Definition definition) {
        if (!player.isOnline()) return false;
        if (!definition.permission().isBlank() && !player.hasPermission(definition.permission())) return false;
        String world = player.getWorld().getName().toLowerCase(Locale.ROOT);
        if (definition.excludedWorlds().contains(world)) return false;
        return definition.worlds().isEmpty() || definition.worlds().contains(world);
    }

    private Component renderPrompt(ChatEventEngine.Round round) {
        Map<String, Component> context = new LinkedHashMap<>();
        round.promptValues().forEach((key, value) -> context.put(key, Component.text(value)));
        return templates.render(round.definition().prompt(), context);
    }
    private Component startedMessage(Map<String, Component> values) {
        ChatEventConfig current = config;
        return current == null ? Component.empty() : templates.render(current.messages().getOrDefault("started", "{prompt}"), values);
    }
    private Component message(String key, Map<String, Component> values) {
        ChatEventConfig current = config;
        if (current == null) return Component.empty();
        return templates.render(current.messages().getOrDefault("prefix", "") + current.messages().getOrDefault(key, key), values);
    }
    private Map<String, Component> baseValues(ChatEventEngine.Competition running) {
        Map<String, Component> values = new LinkedHashMap<>();
        ChatEventConfig.Definition definition = running.round().definition();
        values.put("event_id", Component.text(definition.id()));
        values.put("event_type", Component.text(definition.type().name()));
        values.put("reward_profile", Component.text(definition.rewardProfile()));
        values.put("answer", Component.text(running.round().canonicalAnswer()));
        long remaining = Math.max(0, (running.deadlineNanos() - System.nanoTime()) / 1_000_000L);
        values.put("remaining", Component.text(String.format(Locale.ROOT, "%.1fs", remaining / 1000.0)));
        running.round().promptValues().forEach((key, value) -> values.put(key, Component.text(value)));
        return values;
    }
    private void announce(ChatEventEngine.Competition running, Component component) {
        for (UUID id : running.eligiblePlayers()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) player.sendMessage(component);
        }
    }
    private void play(ChatEventEngine.Competition running, String key) {
        ChatEventConfig current = config;
        if (current == null) return;
        ChatEventConfig.SoundSpec spec = current.sounds().get(key);
        if (spec == null || spec.sound().equalsIgnoreCase("NONE")) return;
        Sound sound = plugin.getConfigManager().resolveSound(spec.sound());
        if (sound == null) return;
        for (UUID id : running.eligiblePlayers()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) player.playSound(player.getLocation(), sound, spec.volume(), spec.pitch());
        }
    }

    private void scheduleNextInterval() {
        ChatEventConfig current = config;
        if (current == null || !current.enabled() || !current.scheduler().enabled()) { nextDeadlineNanos = Long.MAX_VALUE; return; }
        long min = current.scheduler().minIntervalSeconds();
        long max = current.scheduler().maxIntervalSeconds();
        long interval = min == max ? min : ThreadLocalRandom.current().nextLong(min, max + 1);
        nextDeadlineNanos = System.nanoTime() + seconds(interval);
    }

    private String rewardDescription(String profileId) {
        ChatEventConfig current = config;
        if (current == null) return profileId;
        ChatEventConfig.RewardProfile profile = current.rewards().get(profileId);
        if (profile == null) return profileId + " (missing)";
        List<String> parts = new ArrayList<>();
        if (profile.economyEnabled() && profile.economyAmount() > 0) parts.add("$" + profile.economyAmount());
        if (profile.keysEnabled() && profile.keyAmount() > 0) parts.add(profile.keyTier() + " key x" + profile.keyAmount());
        if (!profile.consoleCommands().isEmpty()) parts.add(profile.consoleCommands().size() + " command reward(s)");
        return parts.isEmpty() ? "none" : String.join(" + ", parts);
    }

    private static long seconds(long value) { return value <= 0 ? 0 : Math.multiplyExact(value, 1_000_000_000L); }
    private static String formatElapsed(long nanos) { return String.format(Locale.ROOT, "%.3fs", nanos / 1_000_000_000.0); }
}
