package com.antondev.chats.event;

import com.antondev.chats.ChatChannel;
import com.antondev.chats.PlexonChats;
import com.antondev.chats.event.bingo.BingoBoard;
import com.antondev.chats.event.bingo.BingoParticipant;
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

/** Owns the single Chat Events coordinator and the joinable participant-owned Bingo state machine. */
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
    private final Deque<String> recentStartedIds = new ArrayDeque<>();
    private volatile ChatEventConfig config;
    private volatile DiscordEventSettings discordSettings;
    private volatile ChatEventDiscordPublisher discordPublisher;
    private BukkitTask coordinatorTask;
    private boolean paused;
    private long nextDeadlineNanos = Long.MAX_VALUE;
    private String lastEvent = "NONE";
    private String lastWinner = "-";
    private String recentFailure = "NONE";

    public ChatEventManager(PlexonChats plugin) {
        this.plugin = plugin;
        this.rewards = new ChatEventRewardService(plugin);
        this.statistics = new ChatEventStatisticsService(plugin.getDataFolder().toPath().resolve("chat-events.db"), plugin.getLogger());
    }

    public void reload() {
        ChatEventConfig next = ChatEventConfig.read(plugin.getConfigManager().section("chat-events"), plugin.getChatEventData().root());
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
        recentStartedIds.clear();
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
    private void closeDiscordPublisher() { ChatEventDiscordPublisher publisher = discordPublisher; discordPublisher = null; if (publisher != null) publisher.close(); }

    private void tick() {
        ChatEventConfig current = config;
        if (current == null || !current.enabled()) return;
        long now = System.nanoTime();
        BingoRun bingo = activeBingo.get();
        if (bingo != null) { tickBingo(bingo, now); return; }
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
        if (run.phase() == BingoRun.Phase.LOBBY) {
            Integer reminder = run.pollReminder(now);
            if (reminder != null) announceBingoReminder(run, reminder);
            if (now >= run.lobbyDeadlineNanos()) activateBingo(run, now);
            return;
        }
        if (run.phase() != BingoRun.Phase.ACTIVE) return;
        if (run.timeout(now)) { finishBingoWithoutWinner(run, "TIMED_OUT", "Bingo ended without a winner.", true, true); return; }
        if (run.exhaust(now)) { finishBingoWithoutWinner(run, "EXHAUSTED", "All 75 numbers were called without an accepted claim.", true, true); return; }
        Integer drawn = run.draw(now);
        if (drawn == null) return;
        announceBingoDraw(run, drawn);
        if (run.definition().bingo().render().onEveryDraw()) {
            for (UUID id : run.participantIds()) {
                Player player = Bukkit.getPlayer(id);
                if (player != null && player.isOnline()) showBingoBoard(player);
            }
        }
        publishBingoLive(run);
    }

    private void activateBingo(BingoRun run, long now) {
        BingoRun.ActivationStatus status = run.activate(now, this::online);
        if (status == BingoRun.ActivationStatus.NOT_ENOUGH_PARTICIPANTS) {
            finishBingoWithoutWinner(run, "CANCELLED", "not enough players joined", true, false);
            return;
        }
        if (status != BingoRun.ActivationStatus.ACTIVATED) return;
        markStarted(run.definition(), now);
        Set<UUID> audience = run.participantIds();
        play(audience, "start");
        broadcast(List.of(Component.text("✦ BINGO • LIVE", NamedTextColor.GOLD)
                .append(Component.text(" • " + run.participantCount() + " player(s)", NamedTextColor.GRAY))));
        if (run.definition().bingo().render().onStart()) {
            for (UUID id : audience) {
                Player player = Bukkit.getPlayer(id);
                if (player != null && player.isOnline()) showBingoBoard(player);
            }
        }
        publishBingoLive(run);
        plugin.getLogger().info("Bingo activated: run=" + run.runId() + " source=" + run.source() + " participants=" + run.participantCount());
    }

    /** Called only from the accepted native Minecraft public-chat route. */
    public boolean acceptAnswer(Player player, ChatChannel channel, String rawAnswer) {
        BingoRun bingo = activeBingo.get();
        if (bingo != null) {
            if (!rawAnswer.trim().equalsIgnoreCase("bingo") || !bingo.definition().acceptedChannels().contains(channel)) return false;
            BingoRun.ClaimResult result = claimModel(bingo, player, bingo.runId());
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
        plugin.getLogger().info("Chat event won: run=" + running.round().runId() + " winner=" + player.getUniqueId() + "/" + player.getName());
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
        values.put("answer", definition.revealAnswerOnTimeout() ? Component.text(running.round().canonicalAnswer()) : Component.text("not revealed", NamedTextColor.GRAY));
        announce(running.eligiblePlayers(), presentation.render(config, "timeout", values));
        play(running.eligiblePlayers(), "timeout");
        publishStandardTimeout(running);
        lastEvent = definition.id() + "/TIMED_OUT";
        lastWinner = "-";
        active.compareAndSet(running, null);
        scheduleNextInterval();
    }

    public boolean stop() { return cancelActive("ADMIN_CANCEL", true); }
    public boolean stopBingo() {
        BingoRun run = activeBingo.get();
        if (run == null || !run.cancel()) return false;
        finishBingoWithoutWinner(run, "CANCELLED", "The current Bingo round was cancelled.", true, true);
        return true;
    }

    private boolean cancelActive(String reason, boolean broadcast) {
        BingoRun bingo = activeBingo.get();
        if (bingo != null && bingo.cancel()) {
            if (broadcast) finishBingoWithoutWinner(bingo, "CANCELLED", "The current Bingo round was cancelled.", true, true);
            else clearBingo(bingo, "CANCELLED", true);
            plugin.getLogger().info("Bingo event cancelled: run=" + bingo.runId() + " reason=" + reason);
            return true;
        }
        ChatEventEngine.Competition running = active.get();
        if (running == null || !running.cancel()) return false;
        if (broadcast) { announce(running.eligiblePlayers(), presentation.render(config, "cancelled", baseValues(running.round().definition(), running.round().runId()))); publishStandardCancelled(running); }
        lastEvent = running.round().definition().id() + "/CANCELLED";
        lastWinner = "-";
        active.compareAndSet(running, null);
        scheduleNextInterval();
        return true;
    }

    public StartStatus start(String id) {
        ChatEventConfig current = config;
        if (current == null || !current.enabled()) return StartStatus.DISABLED;
        ChatEventConfig.Definition definition = current.definitions().get(id);
        if (definition == null) return StartStatus.NOT_FOUND;
        return startDefinition(definition, false);
    }

    /** Admin Bingo start: bypasses scheduler deadline/cooldown/min-online but still requires explicit join. */
    public StartStatus startBingo() {
        ChatEventConfig.Definition definition = bingoDefinition();
        if (definition == null) return config == null || !config.enabled() ? StartStatus.DISABLED : StartStatus.NOT_FOUND;
        return openBingo(definition, BingoRun.Source.ADMIN, definition.bingo().lobby().adminLobbySeconds(), true);
    }

    /** Fast admin path. Existing lobby activates immediately when at least one participant joined. */
    public StartStatus startBingoNow() {
        BingoRun existing = activeBingo.get();
        if (existing != null) {
            if (existing.phase() != BingoRun.Phase.LOBBY) return StartStatus.ALREADY_ACTIVE;
            if (existing.participantCount() < 1) return StartStatus.NOT_ENOUGH_PLAYERS;
            activateBingo(existing, System.nanoTime());
            return existing.phase() == BingoRun.Phase.ACTIVE ? StartStatus.STARTED : StartStatus.NOT_ENOUGH_PLAYERS;
        }
        ChatEventConfig.Definition definition = bingoDefinition();
        if (definition == null) return config == null || !config.enabled() ? StartStatus.DISABLED : StartStatus.NOT_FOUND;
        return openBingo(definition, BingoRun.Source.ADMIN, definition.bingo().lobby().adminFastLobbySeconds(), true);
    }

    private ChatEventConfig.Definition bingoDefinition() {
        ChatEventConfig current = config;
        if (current == null || !current.enabled()) return null;
        ChatEventConfig.Definition preferred = current.definitions().get("bingo-classic");
        if (preferred != null && preferred.type() == ChatEventEngine.Type.BINGO && preferred.enabled()) return preferred;
        return current.definitions().values().stream().filter(value -> value.type() == ChatEventEngine.Type.BINGO && value.enabled()).findFirst().orElse(null);
    }

    public StartStatus startRandom(boolean scheduled) {
        ChatEventConfig current = config;
        if (current == null || !current.enabled()) return StartStatus.DISABLED;
        if (hasActiveEvent()) return StartStatus.ALREADY_ACTIVE;
        List<ChatEventConfig.Definition> eligible = selectableDefinitions(System.nanoTime());
        if (eligible.isEmpty()) return StartStatus.NO_ELIGIBLE_EVENTS;
        ChatEventConfig.Definition chosen = "UNIFORM".equals(current.scheduler().randomizerMode())
                ? eligible.get(ThreadLocalRandom.current().nextInt(eligible.size())) : weighted(eligible);
        return startDefinition(chosen, scheduled);
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
        if (definition.type() == ChatEventEngine.Type.BINGO) {
            int lobbySeconds = scheduled ? definition.bingo().lobby().durationSeconds() : definition.bingo().lobby().adminLobbySeconds();
            return openBingo(definition, scheduled ? BingoRun.Source.SCHEDULED : BingoRun.Source.ADMIN, lobbySeconds, false);
        }
        ChatEventEngine.Generator generator = generators.get(definition.type());
        if (generator == null) return StartStatus.GENERATION_FAILED;
        final ChatEventEngine.Round round;
        try { round = generator.generate(definition, ThreadLocalRandom.current()); }
        catch (RuntimeException ex) { recentFailure = "generator " + definition.id() + ": " + ex.getClass().getSimpleName(); plugin.getDiagnostics().recordIntegrationFailure("chat-events-generator", ex); return StartStatus.GENERATION_FAILED; }
        Set<UUID> audience = eligible.stream().map(Player::getUniqueId).collect(java.util.stream.Collectors.toUnmodifiableSet());
        ChatEventEngine.Competition competition = new ChatEventEngine.Competition(round, now, now + seconds(definition.durationSeconds()), audience);
        if (!active.compareAndSet(null, competition)) return StartStatus.ALREADY_ACTIVE;
        if (!competition.activate()) { active.compareAndSet(competition, null); return StartStatus.GENERATION_FAILED; }
        markStarted(definition, now);
        Map<String, Component> values = baseValues(definition, round.runId());
        Component prompt = renderPrompt(round);
        values.put("prompt", prompt); values.put("answer", Component.text(round.canonicalAnswer()));
        announce(audience, presentation.render(current, "start", values));
        play(audience, "start"); publishStandardStart(competition, PLAIN.serialize(prompt));
        return StartStatus.STARTED;
    }

    private synchronized StartStatus openBingo(ChatEventConfig.Definition definition, BingoRun.Source source, int lobbySeconds, boolean bypassEligibility) {
        ChatEventConfig current = config;
        if (current == null || !current.enabled()) return StartStatus.DISABLED;
        if (hasActiveEvent()) return StartStatus.ALREADY_ACTIVE;
        if (!definition.enabled() || !definition.bingo().enabled()) return StartStatus.EVENT_DISABLED;
        long now = System.nanoTime();
        if (!bypassEligibility) {
            if (cooldownUntilNanos.getOrDefault(definition.id(), 0L) > now) return StartStatus.COOLDOWN;
            int onlineMinimum = source == BingoRun.Source.SCHEDULED ? Math.max(definition.minOnline(), current.scheduler().minOnline()) : definition.minOnline();
            if (eligiblePlayers(definition).size() < onlineMinimum) return StartStatus.NOT_ENOUGH_PLAYERS;
        }
        ChatEventConfig.BingoSettings settings = definition.bingo();
        int minimum = source == BingoRun.Source.SCHEDULED ? settings.lobby().minimumParticipants() : settings.lobby().adminMinimumParticipants();
        try {
            BingoRun run = new BingoRun(UUID.randomUUID(), definition, source, now, now + seconds(Math.max(1, lobbySeconds)), minimum,
                    seconds(definition.durationSeconds()), seconds(settings.firstDrawDelaySeconds()), seconds(settings.drawIntervalSeconds()),
                    settings.winPatterns(), settings.lobby().remindersSeconds(), settings.lobby().allowLateJoin(),
                    settings.lobby().requireOnlineAtStart(), settings.freeCenter(), ThreadLocalRandom.current());
            if (!activeBingo.compareAndSet(null, run)) return StartStatus.ALREADY_ACTIVE;
            run.pollReminder(now); // reserve the opening threshold so the first scheduler tick cannot duplicate it
            announceBingoLobby(run, lobbySeconds);
            publishBingoLobby(run, lobbySeconds);
            plugin.getLogger().info("Bingo lobby opened: run=" + run.runId() + " source=" + source + " seconds=" + lobbySeconds);
            return StartStatus.STARTED;
        } catch (RuntimeException failure) {
            recentFailure = "bingo generator " + definition.id() + ": " + failure.getClass().getSimpleName();
            plugin.getDiagnostics().recordIntegrationFailure("chat-events-bingo", failure);
            return StartStatus.GENERATION_FAILED;
        }
    }

    private void markStarted(ChatEventConfig.Definition definition, long now) {
        cooldownUntilNanos.put(definition.id(), now + seconds(definition.cooldownSeconds()));
        recentStartedIds.remove(definition.id()); recentStartedIds.addFirst(definition.id());
        int limit = Math.max(1, config == null ? 1 : config.scheduler().historySize());
        while (recentStartedIds.size() > limit) recentStartedIds.removeLast();
    }

    public BingoRun.JoinResult bingoJoin(Player player) {
        BingoRun run = activeBingo.get();
        if (run == null) return new BingoRun.JoinResult(BingoRun.JoinStatus.NOT_OPEN, null);
        boolean eligible = player.hasPermission("plexonchats.events.bingo.play") && eligibleNow(player, run.definition());
        BingoRun.JoinResult result = run.join(player.getUniqueId(), player.getName(), System.nanoTime(), eligible);
        switch (result.status()) {
            case JOINED -> {
                player.sendActionBar(Component.text("✔ Joined Bingo • your card is locked for this run", NamedTextColor.GREEN));
                if (run.definition().bingo().render().onJoin()) showBingoBoard(player);
                publishBingoPhaseUpdate(run);
            }
            case ALREADY_JOINED -> player.sendActionBar(Component.text("You're already in this Bingo round.", NamedTextColor.YELLOW));
            case NOT_OPEN -> player.sendActionBar(Component.text("This Bingo round is not open for joining.", NamedTextColor.YELLOW));
            case NOT_ELIGIBLE -> player.sendActionBar(Component.text("You are not eligible to join this Bingo round.", NamedTextColor.RED));
        }
        return result;
    }

    public BingoRun.LeaveStatus bingoLeave(Player player) {
        BingoRun run = activeBingo.get();
        if (run == null) return BingoRun.LeaveStatus.NOT_OPEN;
        BingoRun.LeaveStatus result = run.leave(player.getUniqueId());
        switch (result) {
            case LEFT -> { player.sendActionBar(Component.text("Left the Bingo lobby.", NamedTextColor.YELLOW)); publishBingoPhaseUpdate(run); }
            case FORFEITED -> player.sendActionBar(Component.text("You forfeited this Bingo round.", NamedTextColor.YELLOW));
            case NOT_JOINED -> player.sendActionBar(Component.text("You didn't join this Bingo round.", NamedTextColor.YELLOW));
            case NOT_OPEN -> player.sendActionBar(Component.text("This Bingo round cannot be left now.", NamedTextColor.YELLOW));
        }
        return result;
    }

    public BingoRun.MarkResult bingoMark(Player player, UUID expectedRunId, int number) {
        BingoRun run = activeBingo.get();
        if (run == null) { player.sendActionBar(Component.text("There is no active Bingo round.", NamedTextColor.YELLOW)); return new BingoRun.MarkResult(BingoRun.MarkStatus.NOT_ACTIVE, null, -1, number); }
        BingoRun.MarkResult result = run.mark(player.getUniqueId(), expectedRunId, number);
        String label = BingoRenderer.label(number);
        switch (result.status()) {
            case MARKED -> { player.sendActionBar(Component.text("✔ " + label + " marked", NamedTextColor.GREEN)); if (run.definition().bingo().render().afterSuccessfulMark()) showBingoBoard(player); }
            case ALREADY_MARKED -> player.sendActionBar(Component.text(label + " is already marked.", NamedTextColor.YELLOW));
            case NOT_CALLED -> player.sendActionBar(Component.text("✕ " + label + " has not been called yet.", NamedTextColor.RED));
            case NOT_ON_CARD -> player.sendActionBar(Component.text("✕ " + label + " is not on your card.", NamedTextColor.RED));
            case NOT_JOINED -> player.sendActionBar(Component.text("You didn't join this Bingo round.", NamedTextColor.YELLOW));
            case NOT_ACTIVE -> player.sendActionBar(Component.text("This Bingo round is not active.", NamedTextColor.YELLOW));
            case STALE_RUN -> player.sendActionBar(Component.text("That Bingo card belongs to an older round.", NamedTextColor.RED));
            case FORFEITED -> player.sendActionBar(Component.text("You already forfeited this Bingo round.", NamedTextColor.RED));
        }
        return result;
    }

    public BingoRun.ClaimResult bingoClaim(Player player) {
        BingoRun run = activeBingo.get();
        UUID runId = run == null ? new UUID(0, 0) : run.runId();
        return bingoClaim(player, runId);
    }

    public BingoRun.ClaimResult bingoClaim(Player player, UUID expectedRunId) {
        BingoRun run = activeBingo.get();
        if (run == null) { player.sendActionBar(Component.text("There is no active Bingo round.", NamedTextColor.YELLOW)); return new BingoRun.ClaimResult(BingoRun.ClaimStatus.NOT_ACTIVE, null); }
        BingoRun.ClaimResult result = claimModel(run, player, expectedRunId);
        handleBingoClaim(run, player, result);
        return result;
    }

    private BingoRun.ClaimResult claimModel(BingoRun run, Player player, UUID expectedRunId) {
        boolean eligible = player.hasPermission("plexonchats.events.bingo.play") && eligibleNow(player, run.definition());
        return run.claim(player.getUniqueId(), player.getName(), expectedRunId, eligible);
    }

    private void handleBingoClaim(BingoRun run, Player player, BingoRun.ClaimResult result) {
        switch (result.status()) {
            case WON -> completeBingoWinner(run, player, result.win());
            case NO_PATTERN -> player.sendActionBar(Component.text("✕ No completed Bingo pattern yet.", NamedTextColor.RED));
            case NOT_ELIGIBLE -> player.sendActionBar(Component.text("You are not eligible to claim this Bingo round.", NamedTextColor.RED));
            case NOT_JOINED -> player.sendActionBar(Component.text("You didn't join this Bingo round.", NamedTextColor.YELLOW));
            case STALE_RUN -> player.sendActionBar(Component.text("That Bingo claim belongs to an older round.", NamedTextColor.RED));
            case FORFEITED -> player.sendActionBar(Component.text("You forfeited this Bingo round.", NamedTextColor.RED));
            case NOT_ACTIVE -> player.sendActionBar(Component.text("There is no active claimable Bingo round.", NamedTextColor.YELLOW));
        }
    }

    private void completeBingoWinner(BingoRun run, Player player, BingoBoard.Win win) {
        if (run.phase() != BingoRun.Phase.WON || !run.beginCompletion()) return;
        ChatEventConfig.Definition definition = run.definition();
        long elapsedNanos = Math.max(0, System.nanoTime() - run.startedNanos());
        long elapsedMs = elapsedNanos / 1_000_000L;
        ChatEventStatisticsService.PlayerStats stats = statistics.recordWin(run.runId(), player.getUniqueId(), player.getName(),
                definition.id(), ChatEventEngine.Type.BINGO, definition.rewardProfile(), elapsedMs);
        ChatEventRewardService.RewardResult reward = grantReward(player, definition);
        BingoParticipant participant = run.participant(player.getUniqueId());
        if (participant != null) send(player, withBlankSpacing(BingoRenderer.render(run, participant, win)));
        Map<String, Component> values = bingoValues(run);
        values.put("winner", Component.text(player.getName())); values.put("pattern", Component.text(win.pattern().displayName()));
        values.put("reward", Component.text(reward.summary())); values.put("reward_summary", Component.text(reward.summary()));
        values.put("winner_total_wins", Component.text(Long.toString(stats.totalWins()))); values.put("winner_type_wins", Component.text(Long.toString(stats.typeWins(ChatEventEngine.Type.BINGO.name()))));
        List<Component> winnerCard = presentation.renderLines(List.of(
                "{separator}", "<green><bold>✔ BINGO COMPLETE</bold></green>", "", "  <gold>♛</gold> <white>{winner}</white> <gray>•</gray> <aqua>{pattern}</aqua>",
                "  <gray>◈</gray> <white>{draw_count}/75</white> <gray>numbers called</gray>", "  <gray>◆</gray> <gold>{reward}</gold>", "{separator}"), withSeparator(values));
        broadcast(withBlankSpacing(winnerCard));
        play(run.participantIds(), "win"); publishBingoWinner(run, win, player.getName(), reward.summary());
        lastEvent = definition.id() + "/WON"; lastWinner = player.getName();
        if (reward.hasFailure()) recentFailure = "reward partial failure for " + definition.id();
        activeBingo.compareAndSet(run, null); scheduleNextInterval();
    }

    public boolean showBingoBoard(Player player) {
        BingoRun run = activeBingo.get();
        if (run == null || (run.phase() != BingoRun.Phase.LOBBY && run.phase() != BingoRun.Phase.ACTIVE)) return false;
        BingoParticipant participant = run.participant(player.getUniqueId());
        if (participant == null || participant.forfeited()) return false;
        List<Component> lines = new ArrayList<>(BingoRenderer.render(run, participant, null));
        lines.add(Component.text("Patterns: ", NamedTextColor.GRAY).append(Component.text(patternsText(run.patterns()), NamedTextColor.WHITE)));
        if (run.phase() == BingoRun.Phase.ACTIVE) lines.add(Component.text("Next call: ", NamedTextColor.GRAY).append(Component.text(formatSeconds(nextDrawMillis(run)), NamedTextColor.YELLOW)));
        send(player, withBlankSpacing(lines)); return true;
    }

    public void onPlayerJoin(Player player) {
        BingoRun run = activeBingo.get();
        if (run != null && run.participant(player.getUniqueId()) != null && "KEEP".equalsIgnoreCase(run.definition().bingo().lobby().disconnectPolicy())) showBingoBoard(player);
    }

    private void finishBingoWithoutWinner(BingoRun run, String state, String reason, boolean global, boolean normalSchedule) {
        if (global) {
            Map<String, Component> values = withSeparator(Map.of("reason", Component.text(reason)));
            List<Component> lines = presentation.renderLines(List.of("{separator}", "<red><bold>✕ BINGO " + (state.equals("CANCELLED") ? "CANCELLED" : "ENDED") + "</bold></red>", "", "  <gray>{reason}</gray>", "  <gray>No reward issued</gray>", "{separator}"), values);
            broadcast(withBlankSpacing(lines)); publishBingoTerminal(run, state);
        }
        clearBingo(run, state, normalSchedule);
    }

    private void clearBingo(BingoRun run, String state, boolean normalSchedule) {
        lastEvent = run.definition().id() + "/" + state; lastWinner = "-"; activeBingo.compareAndSet(run, null);
        if (normalSchedule) scheduleNextInterval(); else scheduleShortRetry();
    }

    private void scheduleShortRetry() {
        ChatEventConfig current = config;
        if (current == null || !current.enabled() || !current.scheduler().enabled()) { nextDeadlineNanos = Long.MAX_VALUE; return; }
        long defer = Math.max(10, Math.min(60, current.scheduler().minIntervalSeconds()));
        nextDeadlineNanos = System.nanoTime() + seconds(defer);
    }

    public void preview(String id, CommandSender sender) {
        ChatEventConfig current = config;
        if (current == null) { sender.sendMessage(Component.text("Chat Events are unavailable.")); return; }
        ChatEventConfig.Definition definition = current.definitions().get(id);
        if (definition == null) { sender.sendMessage(Component.text("Unknown Chat Event: " + id)); return; }
        if (definition.type() == ChatEventEngine.Type.BINGO) {
            sender.sendMessage(Component.text("Chat Event preview — " + definition.name() + " / BINGO"));
            sender.sendMessage(Component.text("Joinable lobby • personal cards • manual marks • no automatic marking"));
            sender.sendMessage(Component.text("Lobby: " + definition.bingo().lobby().durationSeconds() + "s • reminders " + definition.bingo().lobby().remindersSeconds()));
            sender.sendMessage(Component.text("Draw: " + definition.bingo().firstDrawDelaySeconds() + "s initial / " + definition.bingo().drawIntervalSeconds() + "s interval"));
            sender.sendMessage(Component.text("Patterns: " + patternsText(definition.bingo().winPatterns())));
            sender.sendMessage(Component.text("Reward: " + rewardDescription(definition.rewardProfile()))); return;
        }
        try {
            ChatEventEngine.Round round = generators.get(definition.type()).generate(definition, ThreadLocalRandom.current());
            sender.sendMessage(Component.text("Chat Event preview — " + definition.id() + " / " + definition.type())); sender.sendMessage(renderPrompt(round));
            sender.sendMessage(Component.text("Answer(s): " + String.join(", ", round.acceptedAnswers()))); sender.sendMessage(Component.text("Reward: " + rewardDescription(definition.rewardProfile())));
            sender.sendMessage(Component.text("Duration: " + definition.durationSeconds() + "s • cooldown: " + definition.cooldownSeconds() + "s"));
        } catch (RuntimeException ex) { sender.sendMessage(Component.text("Preview failed: " + ex.getMessage())); }
    }

    public void pause() { paused = true; }
    public void resume() { paused = false; ChatEventConfig current = config; if (current != null) nextDeadlineNanos = System.nanoTime() + seconds(current.scheduler().initialDelaySeconds()); }
    public boolean hasActiveEvent() { return active.get() != null || activeBingo.get() != null; }
    public boolean enabled() { ChatEventConfig current = config; return current != null && current.enabled(); }
    public boolean schedulerEnabled() { ChatEventConfig current = config; return current != null && current.scheduler().enabled(); }
    public boolean paused() { return paused; }
    public boolean taskActive() { return coordinatorTask != null && !coordinatorTask.isCancelled(); }
    public String activeId() { BingoRun bingo = activeBingo.get(); if (bingo != null) return bingo.definition().id(); ChatEventEngine.Competition value = active.get(); return value == null ? "NONE" : value.round().definition().id(); }
    public String activeType() { return activeBingo.get() != null ? "BINGO" : active.get() == null ? "-" : active.get().round().definition().type().name(); }
    public UUID activeRunId() { BingoRun bingo = activeBingo.get(); if (bingo != null) return bingo.runId(); ChatEventEngine.Competition value = active.get(); return value == null ? null : value.round().runId(); }
    public long remainingMillis() {
        BingoRun bingo = activeBingo.get();
        if (bingo != null) { long deadline = bingo.phase() == BingoRun.Phase.LOBBY ? bingo.lobbyDeadlineNanos() : bingo.timeoutDeadlineNanos(); return deadline <= 0 ? -1 : Math.max(0, (deadline - System.nanoTime()) / 1_000_000L); }
        ChatEventEngine.Competition value = active.get(); return value == null ? -1 : Math.max(0, (value.deadlineNanos() - System.nanoTime()) / 1_000_000L);
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
    public String bingoSource() { BingoRun value = activeBingo.get(); return value == null ? "-" : value.source().name(); }
    public long bingoLobbyRemainingMillis() { BingoRun value = activeBingo.get(); return value == null || value.phase() != BingoRun.Phase.LOBBY ? -1 : Math.max(0, (value.lobbyDeadlineNanos() - System.nanoTime()) / 1_000_000L); }
    public int bingoDrawCount() { BingoRun value = activeBingo.get(); return value == null ? 0 : value.drawCount(); }
    public String bingoLastDraw() { BingoRun value = activeBingo.get(); return value == null || value.lastDraw() <= 0 ? "-" : BingoRenderer.label(value.lastDraw()); }
    public int bingoRemainingCount() { BingoRun value = activeBingo.get(); return value == null ? 0 : value.remainingCount(); }
    public long bingoNextDrawMillis() { BingoRun value = activeBingo.get(); return value == null || value.phase() != BingoRun.Phase.ACTIVE ? -1 : nextDrawMillis(value); }
    public String bingoPatterns() { BingoRun value = activeBingo.get(); return value == null ? "-" : patternsText(value.patterns()); }
    public boolean bingoDiscordEnabled() { return discordSettings != null && discordSettings.publishes(ChatEventEngine.Type.BINGO); }
    public String bingoRewardProfile() { BingoRun value = activeBingo.get(); return value == null ? "-" : value.definition().rewardProfile(); }
    public int bingoParticipantCount() { BingoRun value = activeBingo.get(); return value == null ? 0 : value.participantCount(); }
    public int bingoOnlineParticipantCount() { BingoRun value = activeBingo.get(); return value == null ? 0 : value.onlineParticipantCount(this::online); }
    public List<String> bingoParticipants() { BingoRun value = activeBingo.get(); return value == null ? List.of() : value.participants().values().stream().map(BingoParticipant::nameSnapshot).toList(); }
    public List<String> bingoBoardPreview() { BingoRun value = activeBingo.get(); if (value == null || value.participants().isEmpty()) return List.of(); BingoParticipant first = value.participants().values().iterator().next(); return BingoRenderer.plainLines(first, value.definition().bingo().render()); }

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
                DiscordEventEmbed.COLOR_LIVE, List.of(DiscordEventEmbed.field("Transport", publisher.transportName(), true), DiscordEventEmbed.field("Participation", settings.participationMode().name(), true)),
                settings.embeds().showFooter() ? "PlexonCraft • Chat Events • TEST" : "", settings.embeds().timestamp(), "");
        return publisher.test(embed);
    }

    public List<String> listStatus() {
        ChatEventConfig current = config; if (current == null) return List.of(); long now = System.nanoTime(); List<String> lines = new ArrayList<>();
        for (ChatEventConfig.Definition definition : current.definitions().values()) {
            long cooldown = Math.max(0, (cooldownUntilNanos.getOrDefault(definition.id(), 0L) - now) / 1_000_000_000L);
            lines.add(definition.id() + " — " + definition.name() + " — " + definition.type() + " — " + (definition.enabled() ? "ENABLED" : "DISABLED") + " — reward=" + definition.rewardProfile() + " — weight=" + definition.weight() + " — cooldown=" + cooldown + "s");
        }
        return List.copyOf(lines);
    }

    public String status() { if (!enabled()) return "DISABLED"; String suffix = hasActiveEvent() ? " / active=" + activeId() : ""; if (!schedulerEnabled()) return "MANUAL ONLY" + suffix; if (paused) return "PAUSED" + suffix; return "RUNNING" + suffix; }

    private List<ChatEventConfig.Definition> selectableDefinitions(long now) {
        ChatEventConfig current = config; if (current == null || !current.enabled()) return List.of();
        List<ChatEventConfig.Definition> values = current.definitions().values().stream().filter(ChatEventConfig.Definition::enabled)
                .filter(definition -> definition.type() != ChatEventEngine.Type.BINGO || definition.bingo().enabled())
                .filter(definition -> definition.weight() > 0).filter(definition -> cooldownUntilNanos.getOrDefault(definition.id(), 0L) <= now)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (current.scheduler().avoidImmediateRepeat() && values.size() > 1 && !recentStartedIds.isEmpty()) values.removeIf(definition -> recentStartedIds.contains(definition.id()));
        if (values.isEmpty() && current.scheduler().avoidImmediateRepeat()) {
            values = current.definitions().values().stream().filter(ChatEventConfig.Definition::enabled).filter(definition -> definition.weight() > 0)
                    .filter(definition -> cooldownUntilNanos.getOrDefault(definition.id(), 0L) <= now).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        }
        return values;
    }

    private ChatEventConfig.Definition weighted(List<ChatEventConfig.Definition> definitions) {
        long total = 0; for (ChatEventConfig.Definition definition : definitions) total = Math.addExact(total, definition.weight());
        long target = ThreadLocalRandom.current().nextLong(total); for (ChatEventConfig.Definition definition : definitions) { target -= definition.weight(); if (target < 0) return definition; } return definitions.getLast();
    }

    private List<Player> eligiblePlayers(ChatEventConfig.Definition definition) { List<Player> result = new ArrayList<>(); for (Player player : Bukkit.getOnlinePlayers()) if (eligibleNow(player, definition)) result.add(player); return List.copyOf(result); }
    private Set<UUID> eligibleAudience(ChatEventConfig.Definition definition) { return eligiblePlayers(definition).stream().map(Player::getUniqueId).collect(java.util.stream.Collectors.toUnmodifiableSet()); }
    private boolean eligibleNow(Player player, ChatEventConfig.Definition definition) { if (!player.isOnline()) return false; if (!definition.permission().isBlank() && !player.hasPermission(definition.permission())) return false; String world = player.getWorld().getName().toLowerCase(Locale.ROOT); if (definition.excludedWorlds().contains(world)) return false; return definition.worlds().isEmpty() || definition.worlds().contains(world); }
    private boolean online(UUID id) { Player player = Bukkit.getPlayer(id); return player != null && player.isOnline(); }

    private Component renderPrompt(ChatEventEngine.Round round) { Map<String, Component> context = new LinkedHashMap<>(); round.promptValues().forEach((key, value) -> context.put(key, Component.text(value))); return templates.render(round.definition().prompt(), context); }
    private Map<String, Component> baseValues(ChatEventConfig.Definition definition, UUID runId) {
        ChatEventConfig current = config; Map<String, Component> values = new LinkedHashMap<>();
        values.put("event_id", Component.text(definition.id())); values.put("event_type", Component.text(current == null ? definition.type().name() : current.presentation().typeName(definition.type())));
        values.put("event_name", Component.text(definition.name())); values.put("description", Component.text(definition.name())); values.put("reward_profile", Component.text(definition.rewardProfile()));
        values.put("reward", Component.text(rewardDescription(definition.rewardProfile()))); values.put("reward_summary", Component.text(rewardDescription(definition.rewardProfile())));
        values.put("duration_seconds", Component.text(Integer.toString(definition.durationSeconds()))); values.put("remaining_seconds", Component.text(Long.toString(Math.max(0, remainingMillis() / 1000L))));
        if (runId != null) values.put("run_id", Component.text(runId.toString())); return values;
    }
    private Map<String, Component> bingoValues(BingoRun run) {
        Map<String, Component> values = baseValues(run.definition(), run.runId());
        values.put("draw_count", Component.text(Integer.toString(run.drawCount()))); values.put("drawn_number", Component.text(run.lastDraw() <= 0 ? "-" : BingoRenderer.label(run.lastDraw())));
        values.put("remaining_pool", Component.text(Integer.toString(run.remainingCount()))); values.put("patterns", Component.text(patternsText(run.patterns())));
        values.put("next_draw", Component.text(run.phase() == BingoRun.Phase.ACTIVE ? formatSeconds(nextDrawMillis(run)) : "-"));
        values.put("participant_count", Component.text(Integer.toString(run.participantCount()))); values.put("participant_online", Component.text(Integer.toString(run.onlineParticipantCount(this::online))));
        values.put("source", Component.text(run.source().name())); long lobbyMs = Math.max(0, (run.lobbyDeadlineNanos() - System.nanoTime()) / 1_000_000L);
        values.put("lobby_seconds", Component.text(Long.toString((long) Math.ceil(lobbyMs / 1000.0)))); values.put("lobby_time", Component.text(formatClock(lobbyMs)));
        return values;
    }

    private void announceBingoLobby(BingoRun run, int seconds) {
        Map<String, Component> values = bingoValues(run); values.put("lobby_seconds", Component.text(Integer.toString(seconds))); values.put("lobby_time", Component.text(formatClock(seconds * 1000L)));
        List<Component> lines = bingoMessage(run.definition().bingo(), "lobby", withSeparator(values), ChatEventConfig.BingoSettings.defaults().message("lobby"));
        announce(eligibleAudience(run.definition()), withBlankSpacing(lines));
    }
    private void announceBingoReminder(BingoRun run, int seconds) {
        Map<String, Component> values = bingoValues(run); values.put("lobby_seconds", Component.text(Integer.toString(seconds))); values.put("lobby_time", Component.text(formatClock(seconds * 1000L)));
        announce(eligibleAudience(run.definition()), bingoMessage(run.definition().bingo(), "reminder", values, ChatEventConfig.BingoSettings.defaults().message("reminder")));
        publishBingoPhaseUpdate(run);
    }
    private void announceBingoDraw(BingoRun run, int drawn) {
        Map<String, Component> values = bingoValues(run); values.put("drawn_number", Component.text(BingoRenderer.label(drawn)));
        List<Component> base = bingoMessage(run.definition().bingo(), "draw", values, ChatEventConfig.BingoSettings.defaults().message("draw"));
        for (Player player : eligiblePlayers(run.definition())) {
            boolean participant = run.participant(player.getUniqueId()) != null;
            for (int index = 0; index < base.size(); index++) {
                Component line = base.get(index);
                if (participant && index == base.size() - 1) line = line.append(Component.space()).append(BingoRenderer.viewCardButton());
                player.sendMessage(line);
            }
        }
    }

    private void publishStandardStart(ChatEventEngine.Competition competition, String prompt) { ChatEventDiscordPublisher publisher = discordPublisher; DiscordEventSettings settings = discordSettings; ChatEventConfig.Definition definition = competition.round().definition(); if (publisher == null || settings == null || !settings.publishes(definition.type())) return; publisher.start(competition.round().runId(), StandardEventDiscordEmbedRenderer.live(definition, typeName(definition.type()), prompt, rewardDescription(definition.rewardProfile()), settings)); }
    private void publishStandardWinner(ChatEventEngine.Competition competition, String winner, String reward, String elapsed) { ChatEventDiscordPublisher publisher = discordPublisher; DiscordEventSettings settings = discordSettings; ChatEventConfig.Definition definition = competition.round().definition(); if (publisher == null || settings == null || !settings.publishes(definition.type())) return; publisher.terminal(competition.round().runId(), StandardEventDiscordEmbedRenderer.winner(definition, typeName(definition.type()), winner, competition.round().canonicalAnswer(), reward, elapsed, "", settings)); }
    private void publishStandardTimeout(ChatEventEngine.Competition competition) { ChatEventDiscordPublisher publisher = discordPublisher; DiscordEventSettings settings = discordSettings; ChatEventConfig.Definition definition = competition.round().definition(); if (publisher == null || settings == null || !settings.publishes(definition.type())) return; publisher.terminal(competition.round().runId(), StandardEventDiscordEmbedRenderer.timeout(definition, typeName(definition.type()), competition.round().canonicalAnswer(), definition.revealAnswerOnTimeout(), settings)); }
    private void publishStandardCancelled(ChatEventEngine.Competition competition) { ChatEventDiscordPublisher publisher = discordPublisher; DiscordEventSettings settings = discordSettings; ChatEventConfig.Definition definition = competition.round().definition(); if (publisher == null || settings == null || !settings.publishes(definition.type())) return; publisher.terminal(competition.round().runId(), StandardEventDiscordEmbedRenderer.cancelled(definition, typeName(definition.type()), settings)); }

    private void publishBingoLobby(BingoRun run, int seconds) { ChatEventDiscordPublisher publisher = discordPublisher; DiscordEventSettings settings = discordSettings; if (publisher == null || settings == null || !settings.publishes(ChatEventEngine.Type.BINGO)) return; publisher.start(run.runId(), BingoDiscordEmbedRenderer.lobby(run, seconds, rewardDescription(run.definition().rewardProfile()), settings)); }
    private void publishBingoLive(BingoRun run) { ChatEventDiscordPublisher publisher = discordPublisher; DiscordEventSettings settings = discordSettings; if (publisher == null || settings == null || !settings.publishes(ChatEventEngine.Type.BINGO)) return; publisher.update(run.runId(), BingoDiscordEmbedRenderer.live(run, rewardDescription(run.definition().rewardProfile()), settings)); }
    private void publishBingoPhaseUpdate(BingoRun run) { if (run.phase() == BingoRun.Phase.LOBBY) { ChatEventDiscordPublisher publisher = discordPublisher; DiscordEventSettings settings = discordSettings; if (publisher != null && settings != null && settings.publishes(ChatEventEngine.Type.BINGO)) publisher.update(run.runId(), BingoDiscordEmbedRenderer.lobby(run, (int) Math.ceil(Math.max(0, run.lobbyDeadlineNanos() - System.nanoTime()) / 1_000_000_000.0), rewardDescription(run.definition().rewardProfile()), settings)); } else publishBingoLive(run); }
    private void publishBingoWinner(BingoRun run, BingoBoard.Win win, String winner, String reward) { ChatEventDiscordPublisher publisher = discordPublisher; DiscordEventSettings settings = discordSettings; if (publisher == null || settings == null || !settings.publishes(ChatEventEngine.Type.BINGO) || !settings.bingo().announceWinner()) return; publisher.terminal(run.runId(), BingoDiscordEmbedRenderer.winner(run, win, winner, reward, settings)); }
    private void publishBingoTerminal(BingoRun run, String state) { ChatEventDiscordPublisher publisher = discordPublisher; DiscordEventSettings settings = discordSettings; if (publisher == null || settings == null || !settings.publishes(ChatEventEngine.Type.BINGO)) return; BingoDiscordEmbedRenderer.Terminal terminal = switch (state) { case "CANCELLED" -> BingoDiscordEmbedRenderer.Terminal.CANCELLED; case "EXHAUSTED" -> BingoDiscordEmbedRenderer.Terminal.EXHAUSTED; default -> BingoDiscordEmbedRenderer.Terminal.TIMED_OUT; }; publisher.terminal(run.runId(), BingoDiscordEmbedRenderer.terminal(run, terminal, settings)); }

    private String typeName(ChatEventEngine.Type type) { ChatEventConfig current = config; return current == null ? type.name() : current.presentation().typeName(type); }
    private Map<String, Component> withSeparator(Map<String, Component> values) { Map<String, Component> result = new LinkedHashMap<>(values); ChatEventConfig current = config; result.put("separator", current == null ? Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY) : templates.render(current.presentation().separator(), Map.of())); return result; }
    private List<Component> bingoMessage(ChatEventConfig.BingoSettings settings, String key, Map<String, Component> values, List<String> fallback) { List<String> lines = settings.message(key); if (lines.isEmpty()) lines = fallback; return lines.isEmpty() ? List.of() : presentation.renderLines(lines, values); }
    private List<Component> withBlankSpacing(List<Component> lines) { ChatEventConfig current = config; if (current == null) return lines; ArrayList<Component> result = new ArrayList<>(); for (int i = 0; i < current.presentation().blankLinesBefore(); i++) result.add(Component.empty()); result.addAll(lines); for (int i = 0; i < current.presentation().blankLinesAfter(); i++) result.add(Component.empty()); return List.copyOf(result); }
    private void announce(Set<UUID> audience, List<Component> lines) { for (UUID id : audience) { Player player = Bukkit.getPlayer(id); if (player != null && player.isOnline()) send(player, lines); } }
    private void broadcast(List<Component> lines) { for (Player player : Bukkit.getOnlinePlayers()) send(player, lines); }
    private static void send(CommandSender sender, List<Component> lines) { for (Component line : lines) sender.sendMessage(line); }
    private void play(Set<UUID> audience, String key) { ChatEventConfig current = config; if (current == null) return; ChatEventConfig.SoundSpec spec = current.sounds().get(key); if (spec == null || spec.sound().equalsIgnoreCase("NONE")) return; Sound sound = plugin.getConfigManager().resolveSound(spec.sound()); if (sound == null) return; for (UUID id : audience) { Player player = Bukkit.getPlayer(id); if (player != null && player.isOnline()) player.playSound(player.getLocation(), sound, spec.volume(), spec.pitch()); } }
    private void scheduleNextInterval() { ChatEventConfig current = config; if (current == null || !current.enabled() || !current.scheduler().enabled()) { nextDeadlineNanos = Long.MAX_VALUE; return; } long min = current.scheduler().minIntervalSeconds(), max = current.scheduler().maxIntervalSeconds(); long interval = min == max ? min : ThreadLocalRandom.current().nextLong(min, max + 1); nextDeadlineNanos = System.nanoTime() + seconds(interval); }
    private String rewardDescription(String profileId) { ChatEventConfig current = config; if (current == null) return profileId; ChatEventConfig.RewardProfile profile = current.rewards().get(profileId); if (profile == null) return profileId + " (missing)"; List<String> parts = new ArrayList<>(); if (profile.economyEnabled() && profile.economyAmount() > 0) parts.add("$" + String.format(Locale.ROOT, "%.2f", profile.economyAmount())); if (profile.keysEnabled() && profile.keyAmount() > 0) parts.add(profile.keyTier() + " key x" + profile.keyAmount()); if (!profile.consoleCommands().isEmpty()) parts.add(profile.consoleCommands().size() + " command reward(s)"); return parts.isEmpty() ? "none" : String.join(" + ", parts); }
    private static String patternsText(Set<BingoPattern> patterns) { return patterns.stream().map(BingoPattern::displayName).sorted().collect(java.util.stream.Collectors.joining(", ")); }
    private static long nextDrawMillis(BingoRun run) { return Math.max(0, (run.nextDrawNanos() - System.nanoTime()) / 1_000_000L); }
    private static String formatSeconds(long millis) { return String.format(Locale.ROOT, "%.1fs", millis / 1000.0); }
    private static String formatClock(long millis) { long seconds = Math.max(0, (long) Math.ceil(millis / 1000.0)); return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60); }
    private static long seconds(long value) { return value <= 0 ? 0 : Math.multiplyExact(value, 1_000_000_000L); }
    private static String formatElapsed(long nanos) { return String.format(Locale.ROOT, "%.3fs", nanos / 1_000_000_000.0); }
}
