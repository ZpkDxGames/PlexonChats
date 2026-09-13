package com.antondev.chats.event;

import com.antondev.chats.ChatChannel;
import com.antondev.chats.PlexonChats;
import com.antondev.chats.event.bingo.BingoPattern;
import com.antondev.chats.event.bingo.BingoRenderer;
import com.antondev.chats.event.bingo.BingoRun;
import com.antondev.chats.event.discord.BingoDiscordEmbedRenderer;
import com.antondev.chats.event.discord.ChatEventDiscordPublisher;
import com.antondev.chats.event.discord.DiscordEventEmbed;
import com.antondev.chats.event.discord.DiscordEventSettings;
import com.antondev.chats.event.discord.StandardEventDiscordEmbedRenderer;
import com.antondev.chats.text.ComponentTemplate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

/** Owns the single Chat Events coordinator, active competition, shared Bingo run and exact-once completion paths. */
public final class ChatEventManager {
    public enum StartStatus { STARTED, DISABLED, ALREADY_ACTIVE, NOT_FOUND, EVENT_DISABLED, COOLDOWN, NOT_ENOUGH_PLAYERS, NO_ELIGIBLE_EVENTS, GENERATION_FAILED }

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private final PlexonChats plugin;
    private final ChatEventRewardService rewards;
    private final ChatEventStatisticsService statistics;
    private final ChatEventPresentation presentation = new ChatEventPresentation();
    private final ComponentTemplate templates = new ComponentTemplate(MiniMessage.miniMessage());
    private final Map<ChatEventEngine.Type, ChatEventEngine.Generator> generators = ChatEventEngine.generators();
    private final AtomicReference<ChatEventEngine.Competition> active = new AtomicReference<>();
    private final AtomicReference<BingoRun> activeBingo = new AtomicReference<>();
    private final Map<String, Long> cooldownUntilNanos = new LinkedHashMap<>();
    private volatile ChatEventConfig config;
    private volatile DiscordEventSettings discordSettings;
    private volatile ChatEventDiscordPublisher discordPublisher;
    private BukkitTask coordinatorTask;
    private boolean paused;
    private long nextDeadlineNanos = Long.MAX_VALUE;
    private String lastStartedId;
    private String lastEvent = "NONE";
    private String lastWinner = "-";
    private String recentFailure = "NONE";

    public ChatEventManager(PlexonChats plugin) {
        this.plugin = plugin;
        this.rewards = new ChatEventRewardService(plugin);
        this.statistics = new ChatEventStatisticsService(plugin.getDataFolder().toPath().resolve("chat-events.db"), plugin.getLogger());
    }

    public void reload() {
        ChatEventConfig next = ChatEventConfig.read(plugin.getConfigManager().section("chat-events"));
        cancelActive("CONFIG_RELOAD", true);
        stopCoordinator();
        closeDiscordPublisher();
        config = next;
        rewards.reload(next);
        discordSettings = DiscordEventSettings.read(plugin.getConfigManager().section("chat-events"));
        discordPublisher = new ChatEventDiscordPublisher(plugin, discordSettings);
        if (discordSettings.enabled() && !discordPublisher.transportStatus().equals("READY")
                && !discordPublisher.transportStatus().equals("WAITING_FOR_DISCORD")) {
            plugin.getLogger().warning("Discord Chat Events are enabled but transport is unavailable: " + discordPublisher.transportStatus());
        }
        cooldownUntilNanos.clear();
        lastStartedId = null;
        paused = false;
        long now = System.nanoTime();
        nextDeadlineNanos = now + seconds(next.scheduler().initialDelaySeconds());
        if (next.enabled()) coordinatorTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void close() {
        cancelActive("PLUGIN_DISABLE", false);
        stopCoordinator();
        config = null;
        closeDiscordPublisher();
        statistics.close();
    }

    public void refreshIntegrations() { rewards.refreshIntegrations(); }
    private void stopCoordinator() { if (coordinatorTask != null) coordinatorTask.cancel(); coordinatorTask = null; }
    private void closeDiscordPublisher() {
        ChatEventDiscordPublisher publisher = discordPublisher;
        discordPublisher = null;
        if (publisher != null) publisher.close();
    }

    private void tick() {
        ChatEventConfig current = config;
        if (current == null || !current.enabled()) return;
        long now = System.nanoTime();

        BingoRun bingo = activeBingo.get();
        if (bingo != null) {
            tickBingo(bingo, now);
            return;
        }

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

    private void tickBingo(BingoRun run, long now) {
        if (run.phase() != BingoRun.Phase.ACTIVE) return;
        if (run.timeout(now)) {
            finishBingoWithoutWinner(run, "TIMED_OUT", "Bingo ended without a winner.", true);
            return;
        }
        if (run.exhaust(now)) {
            finishBingoWithoutWinner(run, "EXHAUSTED", "All 75 numbers were called without an accepted claim.", true);
            return;
        }
        Integer drawn = run.draw(now);
        if (drawn == null) return;

        Map<String, Component> values = bingoValues(run);
        values.put("drawn_number", Component.text(BingoRenderer.label(drawn)));
        List<Component> call = bingoMessage(run.definition().bingo(), "draw", values,
                List.of("<gold>[BINGO]</gold> <gray>Call</gray> <yellow>#{draw_count}</yellow><gray>:</gray> <white>{drawn_number}</white>"));
        announceBingoAudience(run, withBlankSpacing(join(call, BingoRenderer.render(run, null))));
        publishBingoUpdate(run);
    }

    /** Called only from the accepted native Minecraft public-chat route after PlexonChatEvent cancellation. */
    public boolean acceptAnswer(Player player, ChatChannel channel, String rawAnswer) {
        BingoRun bingo = activeBingo.get();
        if (bingo != null) {
            if (!rawAnswer.trim().equalsIgnoreCase("bingo")) return false;
            if (!bingo.definition().acceptedChannels().contains(channel)) return false;
            BingoRun.ClaimResult result = claimModel(bingo, player);
            if (Bukkit.isPrimaryThread()) handleBingoClaim(bingo, player, result);
            else Bukkit.getScheduler().runTask(plugin, () -> handleBingoClaim(bingo, player, result));
            return result.status() == BingoRun.ClaimStatus.WON;
        }

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
        ChatEventConfig.Definition definition = running.round().definition();
        long elapsed = Math.max(0, System.nanoTime() - running.startedNanos());
        long elapsedMs = elapsed / 1_000_000L;
        ChatEventStatisticsService.PlayerStats stats = statistics.recordWin(running.round().runId(), player.getUniqueId(), player.getName(),
                definition.id(), definition.type(), definition.rewardProfile(), elapsedMs);
        ChatEventRewardService.RewardResult reward = grantReward(player, definition);

        Map<String, Component> values = baseValues(definition, running.round().runId());
        values.put("answer", Component.text(running.round().canonicalAnswer()));
        values.put("winner", Component.text(player.getName()));
        values.put("winner_uuid", Component.text(player.getUniqueId().toString()));
        values.put("elapsed", Component.text(formatElapsed(elapsed)));
        values.put("elapsed_ms", Component.text(Long.toString(elapsedMs)));
        values.put("reward", Component.text(reward.summary()));
        values.put("reward_summary", Component.text(reward.summary()));
        values.put("winner_total_wins", Component.text(Long.toString(stats.totalWins())));
        values.put("winner_type_wins", Component.text(Long.toString(stats.typeWins(definition.type().name()))));
        announce(running.eligiblePlayers(), presentation.render(config, "winner", values));
        play(running.eligiblePlayers(), "win");
        publishStandardWinner(running, player.getName(), reward.summary(), formatElapsed(elapsed));
        lastEvent = definition.id() + "/WON";
        lastWinner = player.getName();
        if (reward.hasFailure()) recentFailure = "reward partial failure for " + definition.id();
        active.compareAndSet(running, null);
        scheduleNextInterval();
        plugin.getLogger().info("Chat event won: run=" + running.round().runId() + " winner=" + player.getUniqueId() + "/" + player.getName()
                + " elapsed=" + formatElapsed(elapsed) + " reward=" + definition.rewardProfile());
    }

    private ChatEventRewardService.RewardResult grantReward(Player player, ChatEventConfig.Definition definition) {
        try { return rewards.grant(player, definition); }
        catch (RuntimeException | LinkageError ex) {
            plugin.getDiagnostics().recordIntegrationFailure("chat-events-reward", ex);
            return new ChatEventRewardService.RewardResult(List.of(new ChatEventRewardService.ComponentResult(
                    "BUNDLE", ChatEventRewardService.Status.FAILED, ex.getClass().getSimpleName(), "")));
        }
    }

    private void timeout(ChatEventEngine.Competition running) {
        if (!running.timeout()) return;
        ChatEventConfig.Definition definition = running.round().definition();
        Map<String, Component> values = baseValues(definition, running.round().runId());
        values.put("answer", definition.revealAnswerOnTimeout()
                ? Component.text(running.round().canonicalAnswer()) : Component.text("not revealed", NamedTextColor.GRAY));
        announce(running.eligiblePlayers(), presentation.render(config, "timeout", values));
        play(running.eligiblePlayers(), "timeout");
        publishStandardTimeout(running);
        lastEvent = definition.id() + "/TIMED_OUT";
        lastWinner = "-";
        active.compareAndSet(running, null);
        scheduleNextInterval();
        plugin.getLogger().info("Chat event timed out: run=" + running.round().runId() + " definition=" + definition.id());
    }

    public boolean stop() { return cancelActive("ADMIN_CANCEL", true); }

    public boolean stopBingo() {
        BingoRun run = activeBingo.get();
        if (run == null || !run.cancel()) return false;
        finishBingoWithoutWinner(run, "CANCELLED", "The current Bingo round was cancelled.", true);
        plugin.getLogger().info("Bingo event cancelled: run=" + run.runId() + " reason=ADMIN_CANCEL");
        return true;
    }

    private boolean cancelActive(String reason, boolean broadcast) {
        BingoRun bingo = activeBingo.get();
        if (bingo != null && bingo.cancel()) {
            if (broadcast) finishBingoWithoutWinner(bingo, "CANCELLED", "The current Bingo round was cancelled.", true);
            else clearBingo(bingo, "CANCELLED");
            plugin.getLogger().info("Bingo event cancelled: run=" + bingo.runId() + " reason=" + reason);
            return true;
        }
        ChatEventEngine.Competition running = active.get();
        if (running == null || !running.cancel()) return false;
        if (broadcast) {
            announce(running.eligiblePlayers(), presentation.render(config, "cancelled", baseValues(running.round().definition(), running.round().runId())));
            publishStandardCancelled(running);
        }
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

    public StartStatus startBingo() {
        ChatEventConfig current = config;
        if (current == null || !current.enabled()) return StartStatus.DISABLED;
        ChatEventConfig.Definition preferred = current.definitions().get("bingo-classic");
        if (preferred != null && preferred.type() == ChatEventEngine.Type.BINGO) return startDefinition(preferred, false);
        for (ChatEventConfig.Definition definition : current.definitions().values()) {
            if (definition.type() == ChatEventEngine.Type.BINGO && definition.enabled()) return startDefinition(definition, false);
        }
        return StartStatus.NOT_FOUND;
    }

    public StartStatus startRandom(boolean scheduled) {
        ChatEventConfig current = config;
        if (current == null || !current.enabled()) return StartStatus.DISABLED;
        if (hasActiveEvent()) return StartStatus.ALREADY_ACTIVE;
        List<ChatEventConfig.Definition> eligible = selectableDefinitions(System.nanoTime());
        if (eligible.isEmpty()) return StartStatus.NO_ELIGIBLE_EVENTS;
        return startDefinition(weighted(eligible), scheduled);
    }

    private synchronized StartStatus startDefinition(ChatEventConfig.Definition definition, boolean scheduled) {
        ChatEventConfig current = config;
        if (current == null || !current.enabled()) return StartStatus.DISABLED;
        if (hasActiveEvent()) return StartStatus.ALREADY_ACTIVE;
        if (!definition.enabled()) return StartStatus.EVENT_DISABLED;
        long now = System.nanoTime();
        if (cooldownUntilNanos.getOrDefault(definition.id(), 0L) > now) return StartStatus.COOLDOWN;
        List<Player> eligible = eligiblePlayers(definition);
        int minimum = Math.max(definition.minOnline(), scheduled ? current.scheduler().minOnline() : 0);
        if (eligible.size() < minimum) return StartStatus.NOT_ENOUGH_PLAYERS;
        if (definition.type() == ChatEventEngine.Type.BINGO) return startBingo(definition, now);

        ChatEventEngine.Generator generator = generators.get(definition.type());
        if (generator == null) return StartStatus.GENERATION_FAILED;
        final ChatEventEngine.Round round;
        try { round = generator.generate(definition, ThreadLocalRandom.current()); }
        catch (RuntimeException ex) {
            recentFailure = "generator " + definition.id() + ": " + ex.getClass().getSimpleName();
            plugin.getDiagnostics().recordIntegrationFailure("chat-events-generator", ex);
            return StartStatus.GENERATION_FAILED;
        }
        Set<UUID> audience = eligible.stream().map(Player::getUniqueId).collect(java.util.stream.Collectors.toUnmodifiableSet());
        ChatEventEngine.Competition competition = new ChatEventEngine.Competition(round, now, now + seconds(definition.durationSeconds()), audience);
        if (!active.compareAndSet(null, competition)) return StartStatus.ALREADY_ACTIVE;
        if (!competition.activate()) { active.compareAndSet(competition, null); return StartStatus.GENERATION_FAILED; }
        markStarted(definition, now);
        Map<String, Component> values = baseValues(definition, round.runId());
        Component prompt = renderPrompt(round);
        values.put("prompt", prompt);
        values.put("answer", Component.text(round.canonicalAnswer()));
        announce(audience, presentation.render(current, "start", values));
        play(audience, "start");
        publishStandardStart(competition, PLAIN.serialize(prompt));
        plugin.getLogger().info("Chat event started: run=" + round.runId() + " definition=" + definition.id() + " type=" + definition.type()
                + " participants=" + eligible.size());
        return StartStatus.STARTED;
    }

    private StartStatus startBingo(ChatEventConfig.Definition definition, long now) {
        ChatEventConfig.BingoSettings settings = definition.bingo();
        if (!settings.enabled()) return StartStatus.EVENT_DISABLED;
        UUID runId = UUID.randomUUID();
        BingoRun run;
        try {
            run = new BingoRun(runId, definition, now, now + seconds(definition.durationSeconds()),
                    now + seconds(settings.firstDrawDelaySeconds()), seconds(settings.drawIntervalSeconds()),
                    settings.winPatterns(), ThreadLocalRandom.current());
        } catch (RuntimeException failure) {
            recentFailure = "bingo generator " + definition.id() + ": " + failure.getClass().getSimpleName();
            plugin.getDiagnostics().recordIntegrationFailure("chat-events-bingo", failure);
            return StartStatus.GENERATION_FAILED;
        }
        if (!activeBingo.compareAndSet(null, run)) return StartStatus.ALREADY_ACTIVE;
        if (!run.activate()) { activeBingo.compareAndSet(run, null); return StartStatus.GENERATION_FAILED; }
        markStarted(definition, now);

        Map<String, Component> values = bingoValues(run);
        List<Component> intro = bingoMessage(settings, "start", withSeparator(values), List.of(
                "{separator}",
                "<gold><bold>BINGO</bold></gold> <gray>Watch the shared board and claim the first completed pattern.</gray>",
                "<gray>Use <white>/bingo claim</white> or type <white>bingo</white> in public chat.</gray>",
                "<gray>First call in:</gray> <yellow>" + settings.firstDrawDelaySeconds() + "s</yellow>",
                "{separator}"));
        announceBingoAudience(run, withBlankSpacing(join(intro, BingoRenderer.render(run, null))));
        play(eligibleAudience(definition), "start");
        publishBingoStart(run);
        plugin.getLogger().info("Bingo event started: run=" + runId + " definition=" + definition.id()
                + " eligible=" + eligiblePlayers(definition).size());
        return StartStatus.STARTED;
    }

    private void markStarted(ChatEventConfig.Definition definition, long now) {
        cooldownUntilNanos.put(definition.id(), now + seconds(definition.cooldownSeconds()));
        lastStartedId = definition.id();
    }

    public BingoRun.ClaimResult bingoClaim(Player player) {
        BingoRun run = activeBingo.get();
        if (run == null) return new BingoRun.ClaimResult(BingoRun.ClaimStatus.NOT_ACTIVE, null);
        BingoRun.ClaimResult result = claimModel(run, player);
        handleBingoClaim(run, player, result);
        return result;
    }

    private BingoRun.ClaimResult claimModel(BingoRun run, Player player) {
        boolean eligible = player.hasPermission("plexonchats.events.bingo.play") && eligibleNow(player, run.definition());
        return run.claim(player.getUniqueId(), player.getName(), eligible);
    }

    private void handleBingoClaim(BingoRun run, Player player, BingoRun.ClaimResult result) {
        switch (result.status()) {
            case WON -> completeBingoWinner(run, player, result.win());
            case NO_PATTERN -> send(player, bingoMessage(run.definition().bingo(), "invalid-claim", bingoValues(run),
                    List.of("<yellow>[BINGO] No enabled winning pattern is complete yet.</yellow>")));
            case NOT_ELIGIBLE -> player.sendMessage(Component.text("You are not eligible to claim this Bingo round.", NamedTextColor.RED));
            case NOT_ACTIVE -> player.sendMessage(Component.text("There is no active claimable Bingo round.", NamedTextColor.YELLOW));
        }
    }

    private void completeBingoWinner(BingoRun run, Player player, com.antondev.chats.event.bingo.BingoBoard.Win win) {
        if (run.phase() != BingoRun.Phase.WON || !run.beginCompletion()) return;
        ChatEventConfig.Definition definition = run.definition();
        long elapsedNanos = Math.max(0, System.nanoTime() - run.startedNanos());
        long elapsedMs = elapsedNanos / 1_000_000L;
        ChatEventStatisticsService.PlayerStats stats = statistics.recordWin(run.runId(), player.getUniqueId(), player.getName(),
                definition.id(), ChatEventEngine.Type.BINGO, definition.rewardProfile(), elapsedMs);
        ChatEventRewardService.RewardResult reward = grantReward(player, definition);

        broadcast(withBlankSpacing(BingoRenderer.render(run, win)));
        Map<String, Component> values = bingoValues(run);
        values.put("winner", Component.text(player.getName()));
        values.put("winner_uuid", Component.text(player.getUniqueId().toString()));
        values.put("pattern", Component.text(win.pattern().displayName()));
        values.put("reward", Component.text(reward.summary()));
        values.put("reward_summary", Component.text(reward.summary()));
        values.put("winner_total_wins", Component.text(Long.toString(stats.totalWins())));
        values.put("winner_type_wins", Component.text(Long.toString(stats.typeWins(ChatEventEngine.Type.BINGO.name()))));
        List<Component> winnerCard = presentation.renderLines(List.of(
                "{separator}",
                "<gold><bold>BINGO WINNER</bold></gold>",
                "<white>{winner}</white> <gray>claimed</gray> <aqua>{pattern}</aqua><gray>!</gray>",
                "<gray>Reward:</gray> <gold>{reward}</gold>",
                "<gray>Total Chat Event Wins:</gray> <yellow>{winner_total_wins}</yellow>",
                "<gray>Bingo Wins:</gray> <yellow>{winner_type_wins}</yellow>",
                "{separator}"), withSeparator(values));
        broadcast(withBlankSpacing(winnerCard));
        play(eligibleAudience(definition), "win");
        publishBingoWinner(run, win, player.getName(), reward.summary());
        lastEvent = definition.id() + "/WON";
        lastWinner = player.getName();
        if (reward.hasFailure()) recentFailure = "reward partial failure for " + definition.id();
        activeBingo.compareAndSet(run, null);
        scheduleNextInterval();
        plugin.getLogger().info("Bingo won: run=" + run.runId() + " winner=" + player.getUniqueId() + "/" + player.getName()
                + " pattern=" + win.pattern() + " reward=" + definition.rewardProfile());
    }

    public boolean showBingoBoard(Player player) {
        BingoRun run = activeBingo.get();
        if (run == null || run.phase() != BingoRun.Phase.ACTIVE) return false;
        if (!player.hasPermission("plexonchats.events.bingo.play") || !eligibleNow(player, run.definition())) return false;
        List<Component> lines = new ArrayList<>(BingoRenderer.render(run, null));
        lines.add(Component.text("Patterns: ", NamedTextColor.GRAY).append(Component.text(patternsText(run.patterns()), NamedTextColor.WHITE)));
        lines.add(Component.text("Next call: ", NamedTextColor.GRAY).append(Component.text(formatSeconds(nextDrawMillis(run)), NamedTextColor.YELLOW)));
        lines.add(Component.text("Claim with /bingo claim or type bingo in public chat.", NamedTextColor.AQUA));
        send(player, withBlankSpacing(lines));
        return true;
    }

    public void onPlayerJoin(Player player) {
        BingoRun run = activeBingo.get();
        if (run != null && run.phase() == BingoRun.Phase.ACTIVE && player.hasPermission("plexonchats.events.bingo.play") && eligibleNow(player, run.definition())) {
            showBingoBoard(player);
        }
    }

    private void finishBingoWithoutWinner(BingoRun run, String state, String reason, boolean global) {
        if (global) {
            broadcast(withBlankSpacing(BingoRenderer.render(run, null)));
            List<Component> lines = presentation.renderLines(List.of(
                    "{separator}",
                    "<yellow><bold>BINGO " + (state.equals("CANCELLED") ? "CANCELLED" : "ENDED") + "</bold></yellow>",
                    "<gray>{reason}</gray>",
                    "<gray>No reward or win statistic was issued.</gray>",
                    "{separator}"), withSeparator(Map.of("reason", Component.text(reason))));
            broadcast(withBlankSpacing(lines));
            publishBingoTerminal(run, state);
        }
        clearBingo(run, state);
    }

    private void clearBingo(BingoRun run, String state) {
        lastEvent = run.definition().id() + "/" + state;
        lastWinner = "-";
        activeBingo.compareAndSet(run, null);
        scheduleNextInterval();
    }

    public void preview(String id, CommandSender sender) {
        ChatEventConfig current = config;
        if (current == null) { sender.sendMessage(Component.text("Chat Events are unavailable.")); return; }
        ChatEventConfig.Definition definition = current.definitions().get(id);
        if (definition == null) { sender.sendMessage(Component.text("Unknown Chat Event: " + id)); return; }
        if (definition.type() == ChatEventEngine.Type.BINGO) {
            sender.sendMessage(Component.text("Chat Event preview — " + definition.name() + " / BINGO"));
            sender.sendMessage(Component.text("Shared board • automatic marking • no FREE center"));
            sender.sendMessage(Component.text("Draw: " + definition.bingo().firstDrawDelaySeconds() + "s initial / " + definition.bingo().drawIntervalSeconds() + "s interval"));
            sender.sendMessage(Component.text("Patterns: " + patternsText(definition.bingo().winPatterns())));
            sender.sendMessage(Component.text("Duration: " + definition.durationSeconds() + "s"));
            sender.sendMessage(Component.text("Reward: " + rewardDescription(definition.rewardProfile())));
            sender.sendMessage(Component.text("Discord event sync: " + (discordEventsEnabled() ? discordTransport() + "/" + discordTransportStatus() : "DISABLED")));
            return;
        }
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

    public boolean hasActiveEvent() { return active.get() != null || activeBingo.get() != null; }
    public boolean enabled() { ChatEventConfig current = config; return current != null && current.enabled(); }
    public boolean schedulerEnabled() { ChatEventConfig current = config; return current != null && current.scheduler().enabled(); }
    public boolean paused() { return paused; }
    public boolean taskActive() { return coordinatorTask != null && !coordinatorTask.isCancelled(); }
    public String activeId() {
        BingoRun bingo = activeBingo.get();
        if (bingo != null) return bingo.definition().id();
        ChatEventEngine.Competition value = active.get();
        return value == null ? "NONE" : value.round().definition().id();
    }
    public String activeType() { return activeBingo.get() != null ? "BINGO" : active.get() == null ? "-" : active.get().round().definition().type().name(); }
    public UUID activeRunId() {
        BingoRun bingo = activeBingo.get();
        if (bingo != null) return bingo.runId();
        ChatEventEngine.Competition value = active.get();
        return value == null ? null : value.round().runId();
    }
    public long remainingMillis() {
        BingoRun bingo = activeBingo.get();
        if (bingo != null) return Math.max(0, (bingo.timeoutDeadlineNanos() - System.nanoTime()) / 1_000_000L);
        ChatEventEngine.Competition value = active.get();
        return value == null ? -1 : Math.max(0, (value.deadlineNanos() - System.nanoTime()) / 1_000_000L);
    }
    public String lastEvent() { return lastEvent; }
    public String lastWinner() { return lastWinner; }
    public String recentFailure() { return recentFailure; }
    public String economyState() { return rewards.economyState(); }
    public String keysState() { return rewards.keysState(); }
    public Set<String> eventIds() { ChatEventConfig current = config; return current == null ? Set.of() : current.definitions().keySet(); }
    public Map<String, ChatEventConfig.Definition> definitions() { ChatEventConfig current = config; return current == null ? Map.of() : current.definitions(); }
    public ChatEventConfig.Definition definition(String id) { ChatEventConfig current = config; return current == null ? null : current.definitions().get(id); }
    public int configuredCount() { ChatEventConfig current = config; return current == null ? 0 : current.definitions().size(); }
    public int eligibleScheduledCount() { return selectableDefinitions(System.nanoTime()).size(); }
    public long cooldownRemainingSeconds(String id) { return Math.max(0, (cooldownUntilNanos.getOrDefault(id, 0L) - System.nanoTime()) / 1_000_000_000L); }
    public ChatEventStatisticsService statistics() { return statistics; }
    public String bingoPhase() { BingoRun value = activeBingo.get(); return value == null ? "IDLE" : value.phase().name(); }
    public int bingoDrawCount() { BingoRun value = activeBingo.get(); return value == null ? 0 : value.drawCount(); }
    public String bingoLastDraw() { BingoRun value = activeBingo.get(); return value == null || value.lastDraw() <= 0 ? "-" : BingoRenderer.label(value.lastDraw()); }
    public int bingoRemainingCount() { BingoRun value = activeBingo.get(); return value == null ? 0 : value.remainingCount(); }
    public long bingoNextDrawMillis() { BingoRun value = activeBingo.get(); return value == null ? -1 : nextDrawMillis(value); }
    public String bingoPatterns() { BingoRun value = activeBingo.get(); return value == null ? "-" : patternsText(value.patterns()); }
    public boolean bingoDiscordEnabled() { return discordSettings != null && discordSettings.publishes(ChatEventEngine.Type.BINGO); }
    public String bingoRewardProfile() { BingoRun value = activeBingo.get(); return value == null ? "-" : value.definition().rewardProfile(); }
    /** Compatibility diagnostic: this is current eligible audience size, not an opt-in participant count. */
    public int bingoParticipantCount() { BingoRun value = activeBingo.get(); return value == null ? 0 : eligiblePlayers(value.definition()).size(); }
    public List<String> bingoParticipants() {
        BingoRun value = activeBingo.get();
        return value == null ? List.of() : eligiblePlayers(value.definition()).stream().map(Player::getName).toList();
    }

    public List<String> bingoBoardPreview() {
        BingoRun value = activeBingo.get();
        return value == null ? List.of() : BingoRenderer.plainLines(value);
    }

    public boolean discordEventsEnabled() { return discordSettings != null && discordSettings.enabled(); }
    public String discordTransport() { ChatEventDiscordPublisher value = discordPublisher; return value == null ? "NONE" : value.transportName(); }
    public String discordTransportStatus() { ChatEventDiscordPublisher value = discordPublisher; return value == null ? "DISABLED" : value.transportStatus(); }
    public boolean discordChannelConfigured() { ChatEventDiscordPublisher value = discordPublisher; return value != null && value.channelConfigured(); }
    public String discordParticipation() { ChatEventDiscordPublisher value = discordPublisher; return value == null ? "DISPLAY_ONLY" : value.participationMode(); }
    public String discordMessageState() { ChatEventDiscordPublisher value = discordPublisher; return value == null ? "NONE" : value.messageState(activeRunId()); }
    public boolean discordPendingUpdate() { ChatEventDiscordPublisher value = discordPublisher; return value != null && value.pending(activeRunId()); }

    public CompletableFuture<?> sendDiscordTest() {
        ChatEventDiscordPublisher publisher = discordPublisher;
        if (publisher == null) return CompletableFuture.failedFuture(new IllegalStateException("Discord event sync is unavailable"));
        DiscordEventSettings settings = publisher.settings();
        DiscordEventEmbed embed = new DiscordEventEmbed("PLEXONCHATS • TEST", "Discord Chat Events synchronization test. No event, reward or statistic was created.",
                DiscordEventEmbed.COLOR_LIVE, List.of(DiscordEventEmbed.field("Transport", publisher.transportName(), true),
                DiscordEventEmbed.field("Participation", settings.participationMode().name(), true)),
                settings.embeds().showFooter() ? "PlexonCraft • Chat Events • TEST" : "", settings.embeds().timestamp(), "");
        return publisher.test(embed);
    }

    public List<String> listStatus() {
        ChatEventConfig current = config;
        if (current == null) return List.of();
        long now = System.nanoTime();
        List<String> lines = new ArrayList<>();
        for (ChatEventConfig.Definition definition : current.definitions().values()) {
            long cooldown = Math.max(0, (cooldownUntilNanos.getOrDefault(definition.id(), 0L) - now) / 1_000_000_000L);
            lines.add(definition.id() + " — " + definition.name() + " — " + definition.type() + " — " + (definition.enabled() ? "ENABLED" : "DISABLED")
                    + " — reward=" + definition.rewardProfile() + " — weight=" + definition.weight() + " — cooldown=" + cooldown + "s");
        }
        return List.copyOf(lines);
    }

    public String status() {
        if (!enabled()) return "DISABLED";
        String activeSuffix = hasActiveEvent() ? " / active=" + activeId() : "";
        if (!schedulerEnabled()) return "MANUAL ONLY" + activeSuffix;
        if (paused) return "PAUSED" + activeSuffix;
        return "RUNNING" + activeSuffix;
    }

    private List<ChatEventConfig.Definition> selectableDefinitions(long now) {
        ChatEventConfig current = config;
        if (current == null || !current.enabled()) return List.of();
        List<ChatEventConfig.Definition> values = current.definitions().values().stream()
                .filter(ChatEventConfig.Definition::enabled)
                .filter(definition -> definition.type() != ChatEventEngine.Type.BINGO || definition.bingo().enabled())
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

    private Set<UUID> eligibleAudience(ChatEventConfig.Definition definition) {
        return eligiblePlayers(definition).stream().map(Player::getUniqueId).collect(java.util.stream.Collectors.toUnmodifiableSet());
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

    private Map<String, Component> baseValues(ChatEventConfig.Definition definition, UUID runId) {
        ChatEventConfig current = config;
        Map<String, Component> values = new LinkedHashMap<>();
        values.put("event_id", Component.text(definition.id()));
        values.put("event_type", Component.text(current == null ? definition.type().name() : current.presentation().typeName(definition.type())));
        values.put("event_name", Component.text(definition.name()));
        values.put("description", Component.text(definition.name()));
        values.put("reward_profile", Component.text(definition.rewardProfile()));
        values.put("reward", Component.text(rewardDescription(definition.rewardProfile())));
        values.put("reward_summary", Component.text(rewardDescription(definition.rewardProfile())));
        values.put("duration_seconds", Component.text(Integer.toString(definition.durationSeconds())));
        values.put("remaining_seconds", Component.text(Long.toString(Math.max(0, remainingMillis() / 1000L))));
        if (runId != null) values.put("run_id", Component.text(runId.toString()));
        return values;
    }

    private Map<String, Component> bingoValues(BingoRun run) {
        Map<String, Component> values = baseValues(run.definition(), run.runId());
        values.put("draw_count", Component.text(Integer.toString(run.drawCount())));
        values.put("drawn_number", Component.text(run.lastDraw() <= 0 ? "-" : BingoRenderer.label(run.lastDraw())));
        values.put("remaining_pool", Component.text(Integer.toString(run.remainingCount())));
        values.put("patterns", Component.text(patternsText(run.patterns())));
        values.put("next_draw", Component.text(formatSeconds(nextDrawMillis(run))));
        return values;
    }

    private void publishStandardStart(ChatEventEngine.Competition competition, String prompt) {
        ChatEventDiscordPublisher publisher = discordPublisher;
        DiscordEventSettings settings = discordSettings;
        ChatEventConfig.Definition definition = competition.round().definition();
        if (publisher == null || settings == null || !settings.publishes(definition.type())) return;
        publisher.start(competition.round().runId(), StandardEventDiscordEmbedRenderer.live(definition,
                typeName(definition.type()), prompt, rewardDescription(definition.rewardProfile()), settings));
    }

    private void publishStandardWinner(ChatEventEngine.Competition competition, String winner, String reward, String elapsed) {
        ChatEventDiscordPublisher publisher = discordPublisher;
        DiscordEventSettings settings = discordSettings;
        ChatEventConfig.Definition definition = competition.round().definition();
        if (publisher == null || settings == null || !settings.publishes(definition.type())) return;
        publisher.terminal(competition.round().runId(), StandardEventDiscordEmbedRenderer.winner(definition,
                typeName(definition.type()), winner, competition.round().canonicalAnswer(), reward, elapsed, "", settings));
    }

    private void publishStandardTimeout(ChatEventEngine.Competition competition) {
        ChatEventDiscordPublisher publisher = discordPublisher;
        DiscordEventSettings settings = discordSettings;
        ChatEventConfig.Definition definition = competition.round().definition();
        if (publisher == null || settings == null || !settings.publishes(definition.type())) return;
        publisher.terminal(competition.round().runId(), StandardEventDiscordEmbedRenderer.timeout(definition,
                typeName(definition.type()), competition.round().canonicalAnswer(), definition.revealAnswerOnTimeout(), settings));
    }

    private void publishStandardCancelled(ChatEventEngine.Competition competition) {
        ChatEventDiscordPublisher publisher = discordPublisher;
        DiscordEventSettings settings = discordSettings;
        ChatEventConfig.Definition definition = competition.round().definition();
        if (publisher == null || settings == null || !settings.publishes(definition.type())) return;
        publisher.terminal(competition.round().runId(), StandardEventDiscordEmbedRenderer.cancelled(definition,
                typeName(definition.type()), settings));
    }

    private void publishBingoStart(BingoRun run) {
        ChatEventDiscordPublisher publisher = discordPublisher;
        DiscordEventSettings settings = discordSettings;
        if (publisher == null || settings == null || !settings.publishes(ChatEventEngine.Type.BINGO)) return;
        publisher.start(run.runId(), BingoDiscordEmbedRenderer.live(run, rewardDescription(run.definition().rewardProfile()), settings));
    }

    private void publishBingoUpdate(BingoRun run) {
        ChatEventDiscordPublisher publisher = discordPublisher;
        DiscordEventSettings settings = discordSettings;
        if (publisher == null || settings == null || !settings.publishes(ChatEventEngine.Type.BINGO) || !settings.bingo().updateOnDraw()) return;
        publisher.update(run.runId(), BingoDiscordEmbedRenderer.live(run, rewardDescription(run.definition().rewardProfile()), settings));
    }

    private void publishBingoWinner(BingoRun run, com.antondev.chats.event.bingo.BingoBoard.Win win, String winner, String reward) {
        ChatEventDiscordPublisher publisher = discordPublisher;
        DiscordEventSettings settings = discordSettings;
        if (publisher == null || settings == null || !settings.publishes(ChatEventEngine.Type.BINGO) || !settings.bingo().announceWinner()) return;
        publisher.terminal(run.runId(), BingoDiscordEmbedRenderer.winner(run, win, winner, reward, settings));
    }

    private void publishBingoTerminal(BingoRun run, String state) {
        ChatEventDiscordPublisher publisher = discordPublisher;
        DiscordEventSettings settings = discordSettings;
        if (publisher == null || settings == null || !settings.publishes(ChatEventEngine.Type.BINGO)) return;
        BingoDiscordEmbedRenderer.Terminal terminal = switch (state) {
            case "CANCELLED" -> BingoDiscordEmbedRenderer.Terminal.CANCELLED;
            case "EXHAUSTED" -> BingoDiscordEmbedRenderer.Terminal.EXHAUSTED;
            default -> BingoDiscordEmbedRenderer.Terminal.TIMED_OUT;
        };
        publisher.terminal(run.runId(), BingoDiscordEmbedRenderer.terminal(run, terminal, settings));
    }

    private String typeName(ChatEventEngine.Type type) {
        ChatEventConfig current = config;
        return current == null ? type.name() : current.presentation().typeName(type);
    }

    private Map<String, Component> withSeparator(Map<String, Component> values) {
        Map<String, Component> result = new LinkedHashMap<>(values);
        ChatEventConfig current = config;
        result.put("separator", current == null ? Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY)
                : templates.render(current.presentation().separator(), Map.of()));
        return result;
    }

    private List<Component> bingoMessage(ChatEventConfig.BingoSettings settings, String key, Map<String, Component> values, List<String> fallback) {
        List<String> lines = settings.message(key);
        if (lines.isEmpty()) lines = fallback;
        return lines.isEmpty() ? List.of() : presentation.renderLines(lines, values);
    }

    private List<Component> withBlankSpacing(List<Component> lines) {
        ChatEventConfig current = config;
        if (current == null) return lines;
        ArrayList<Component> result = new ArrayList<>();
        for (int i = 0; i < current.presentation().blankLinesBefore(); i++) result.add(Component.empty());
        result.addAll(lines);
        for (int i = 0; i < current.presentation().blankLinesAfter(); i++) result.add(Component.empty());
        return List.copyOf(result);
    }

    private static List<Component> join(List<Component> first, List<Component> second) {
        ArrayList<Component> result = new ArrayList<>(first.size() + second.size());
        result.addAll(first);
        result.addAll(second);
        return List.copyOf(result);
    }

    private void announce(Set<UUID> audience, List<Component> lines) {
        for (UUID id : audience) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) send(player, lines);
        }
    }

    private void announceBingoAudience(BingoRun run, List<Component> lines) { announce(eligibleAudience(run.definition()), lines); }

    private void broadcast(List<Component> lines) { for (Player player : Bukkit.getOnlinePlayers()) send(player, lines); }

    private static void send(CommandSender sender, List<Component> lines) { for (Component line : lines) sender.sendMessage(line); }

    private void play(Set<UUID> audience, String key) {
        ChatEventConfig current = config;
        if (current == null) return;
        ChatEventConfig.SoundSpec spec = current.sounds().get(key);
        if (spec == null || spec.sound().equalsIgnoreCase("NONE")) return;
        Sound sound = plugin.getConfigManager().resolveSound(spec.sound());
        if (sound == null) return;
        for (UUID id : audience) {
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
        if (profile.economyEnabled() && profile.economyAmount() > 0) parts.add("$" + String.format(Locale.ROOT, "%.2f", profile.economyAmount()));
        if (profile.keysEnabled() && profile.keyAmount() > 0) parts.add(profile.keyTier() + " key x" + profile.keyAmount());
        if (!profile.consoleCommands().isEmpty()) parts.add(profile.consoleCommands().size() + " command reward(s)");
        return parts.isEmpty() ? "none" : String.join(" + ", parts);
    }

    private static String patternsText(Set<BingoPattern> patterns) {
        return patterns.stream().map(BingoPattern::displayName).sorted().collect(java.util.stream.Collectors.joining(", "));
    }
    private static long nextDrawMillis(BingoRun run) { return Math.max(0, (run.nextDrawNanos() - System.nanoTime()) / 1_000_000L); }
    private static String formatSeconds(long millis) { return String.format(Locale.ROOT, "%.1fs", millis / 1000.0); }
    private static long seconds(long value) { return value <= 0 ? 0 : Math.multiplyExact(value, 1_000_000_000L); }
    private static String formatElapsed(long nanos) { return String.format(Locale.ROOT, "%.3fs", nanos / 1_000_000_000.0); }
}
