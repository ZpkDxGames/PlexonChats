package com.antondev.chats.event;

import com.antondev.chats.ChatChannel;
import com.antondev.chats.PlexonChats;
import com.antondev.chats.event.bingo.BingoRenderer;
import com.antondev.chats.event.bingo.BingoSession;
import com.antondev.chats.text.ComponentTemplate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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

/** Owns the single Chat Events coordinator, active competition, Bingo session and exact-once completion paths. */
public final class ChatEventManager {
    public enum StartStatus { STARTED, DISABLED, ALREADY_ACTIVE, NOT_FOUND, EVENT_DISABLED, COOLDOWN, NOT_ENOUGH_PLAYERS, NO_ELIGIBLE_EVENTS, GENERATION_FAILED }

    private final PlexonChats plugin;
    private final ChatEventRewardService rewards;
    private final ChatEventStatisticsService statistics;
    private final ChatEventPresentation presentation = new ChatEventPresentation();
    private final ComponentTemplate templates = new ComponentTemplate(MiniMessage.miniMessage());
    private final Map<ChatEventEngine.Type, ChatEventEngine.Generator> generators = ChatEventEngine.generators();
    private final AtomicReference<ChatEventEngine.Competition> active = new AtomicReference<>();
    private final AtomicReference<BingoSession> activeBingo = new AtomicReference<>();
    private final Map<String, Long> cooldownUntilNanos = new LinkedHashMap<>();
    private volatile ChatEventConfig config;
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
        config = next;
        rewards.reload(next);
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
        statistics.close();
    }

    public void refreshIntegrations() { rewards.refreshIntegrations(); }
    private void stopCoordinator() { if (coordinatorTask != null) coordinatorTask.cancel(); coordinatorTask = null; }

    private void tick() {
        ChatEventConfig current = config;
        if (current == null || !current.enabled()) return;
        long now = System.nanoTime();

        BingoSession bingo = activeBingo.get();
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

    private void tickBingo(BingoSession session, long now) {
        if (session.phase() == BingoSession.Phase.JOINING && now >= session.joinDeadlineNanos()) {
            if (session.activate(now)) {
                announceBingoParticipants(session, List.of(Component.text("Bingo is live. Your card is ready.", NamedTextColor.GREEN)));
                for (BingoSession.Participant participant : session.participants()) {
                    Player player = Bukkit.getPlayer(participant.playerId());
                    if (player != null && player.isOnline()) showBingoCard(player, session, null);
                }
                return;
            }
            if (session.cancelInsufficient(now)) {
                finishBingoWithoutWinner(session, "CANCELLED", "Not enough players joined this Bingo round.", true);
                return;
            }
        }
        if (session.phase() != BingoSession.Phase.ACTIVE) return;
        if (session.timeout(now)) {
            finishBingoWithoutWinner(session, "TIMED_OUT", "Bingo ended without a winner.", true);
            return;
        }
        Integer drawn = session.draw(now);
        if (drawn == null) return;
        Map<String, Component> values = bingoValues(session);
        values.put("drawn_number", Component.text(BingoRenderer.label(drawn)));
        values.put("draw_count", Component.text(Integer.toString(session.drawCount())));
        values.put("open_card_button", BingoRenderer.openCardButton());
        List<Component> lines = bingoMessage(session.definition().bingo(), "draw", values,
                List.of("<gold>[BINGO]</gold> <gray>Draw</gray> <yellow>#{draw_count}</yellow><gray>:</gray> <white>{drawn_number}</white> <dark_gray>•</dark_gray> {open_card_button}"));
        announceBingoParticipants(session, lines);
        if (session.definition().bingo().redrawBoardEachDraw()) {
            for (BingoSession.Participant participant : session.participants()) {
                Player player = Bukkit.getPlayer(participant.playerId());
                if (player != null && player.isOnline()) showBingoCard(player, session, null);
            }
        }
    }

    public boolean acceptAnswer(Player player, ChatChannel channel, String rawAnswer) {
        if (activeBingo.get() != null) return false;
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
        lastEvent = definition.id() + "/TIMED_OUT";
        lastWinner = "-";
        active.compareAndSet(running, null);
        scheduleNextInterval();
        plugin.getLogger().info("Chat event timed out: run=" + running.round().runId() + " definition=" + definition.id());
    }

    public boolean stop() { return cancelActive("ADMIN_CANCEL", true); }

    private boolean cancelActive(String reason, boolean broadcast) {
        BingoSession bingo = activeBingo.get();
        if (bingo != null && bingo.cancel()) {
            if (broadcast) finishBingoWithoutWinner(bingo, "CANCELLED", "The current Bingo round was cancelled.", true);
            else clearBingo(bingo, "CANCELLED");
            plugin.getLogger().info("Bingo event cancelled: run=" + bingo.runId() + " reason=" + reason);
            return true;
        }
        ChatEventEngine.Competition running = active.get();
        if (running == null || !running.cancel()) return false;
        if (broadcast) announce(running.eligiblePlayers(), presentation.render(config, "cancelled", baseValues(running.round().definition(), running.round().runId())));
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
        if (definition.type() == ChatEventEngine.Type.BINGO) minimum = Math.max(minimum, definition.bingo().minParticipants());
        if (eligible.size() < minimum) return StartStatus.NOT_ENOUGH_PLAYERS;
        if (definition.type() == ChatEventEngine.Type.BINGO) return startBingo(definition, eligible, now);

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
        values.put("prompt", renderPrompt(round));
        values.put("answer", Component.text(round.canonicalAnswer()));
        announce(audience, presentation.render(current, "start", values));
        play(audience, "start");
        plugin.getLogger().info("Chat event started: run=" + round.runId() + " definition=" + definition.id() + " type=" + definition.type()
                + " participants=" + eligible.size());
        return StartStatus.STARTED;
    }

    private StartStatus startBingo(ChatEventConfig.Definition definition, List<Player> eligible, long now) {
        if (!definition.bingo().enabled()) return StartStatus.EVENT_DISABLED;
        Set<UUID> audience = eligible.stream().map(Player::getUniqueId).collect(java.util.stream.Collectors.toUnmodifiableSet());
        UUID runId = UUID.randomUUID();
        long joinDeadline = now + seconds(definition.bingo().joinSeconds());
        long timeoutDeadline = joinDeadline + seconds(definition.bingo().timeoutSeconds());
        BingoSession session = new BingoSession(runId, definition, audience, definition.bingo().minParticipants(), joinDeadline,
                timeoutDeadline, joinDeadline + seconds(definition.bingo().firstDrawDelaySeconds()), seconds(definition.bingo().drawIntervalSeconds()),
                definition.bingo().freeCenter(), definition.bingo().winPatterns(), ThreadLocalRandom.current());
        if (!activeBingo.compareAndSet(null, session)) return StartStatus.ALREADY_ACTIVE;
        markStarted(definition, now);

        Map<String, Component> values = bingoValues(session);
        values.put("join_seconds", Component.text(Integer.toString(definition.bingo().joinSeconds())));
        values.put("join_command", Component.text("/chat events bingo join " + runId));
        values.put("join_button", Component.text("[ JOIN BINGO ]", NamedTextColor.GREEN, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/chat events bingo join " + runId))
                .hoverEvent(HoverEvent.showText(Component.text("Join this Bingo round", NamedTextColor.GREEN))));
        List<Component> fallback = presentation.renderLines(List.of(
                "{separator}",
                "<gold><bold>BINGO</bold></gold>",
                "<gray>A new Bingo round is starting!</gray>",
                "",
                "{join_button}",
                "",
                "<gray>Join closes in:</gray> <yellow>{join_seconds}s</yellow>",
                "<gray>Reward:</gray> <gold>{reward}</gold>",
                "{separator}"), withSeparator(values));
        List<Component> configured = bingoMessage(definition.bingo(), "join-open", withSeparator(values), List.of());
        broadcast(configured.isEmpty() ? withBlankSpacing(fallback) : withBlankSpacing(configured));
        plugin.getLogger().info("Bingo event joining: run=" + runId + " definition=" + definition.id() + " eligible=" + eligible.size());
        return StartStatus.STARTED;
    }

    private void markStarted(ChatEventConfig.Definition definition, long now) {
        cooldownUntilNanos.put(definition.id(), now + seconds(definition.cooldownSeconds()));
        lastStartedId = definition.id();
    }

    public BingoSession.JoinResult bingoJoin(Player player, UUID runId) {
        BingoSession session = activeBingo.get();
        if (session == null || !session.runId().equals(runId)) return BingoSession.JoinResult.CLOSED;
        if (!player.hasPermission("plexonchats.events.bingo.play") || !eligibleNow(player, session.definition())) return BingoSession.JoinResult.NOT_ELIGIBLE;
        BingoSession.JoinResult result = session.join(player.getUniqueId(), player.getName(), System.nanoTime(), ThreadLocalRandom.current());
        switch (result) {
            case JOINED -> {
                send(player, bingoMessage(session.definition().bingo(), "joined", bingoValues(session),
                        List.of("<green>You joined this Bingo round.</green>", "<gray>Your card is shown below.</gray>")));
                showBingoCard(player, session, null);
            }
            case ALREADY_JOINED -> showBingoCard(player, session, null);
            case CLOSED -> player.sendMessage(Component.text("Bingo joining is closed for this round.", NamedTextColor.RED));
            case NOT_ELIGIBLE -> player.sendMessage(Component.text("You are not eligible for this Bingo round.", NamedTextColor.RED));
        }
        return result;
    }

    public BingoSession.MarkResult bingoMark(Player player, UUID runId, int cell) {
        BingoSession session = activeBingo.get();
        if (session == null || !session.runId().equals(runId)) {
            player.sendMessage(Component.text("That Bingo action is stale.", NamedTextColor.RED));
            return new BingoSession.MarkResult(BingoSession.MarkStatus.NOT_ACTIVE, null, null);
        }
        BingoSession.MarkResult result = session.mark(player.getUniqueId(), cell);
        switch (result.status()) {
            case MARKED -> {
                Map<String, Component> values = bingoValues(session);
                values.put("drawn_number", Component.text(BingoRenderer.label(result.number())));
                send(player, bingoMessage(session.definition().bingo(), "marked", values,
                        List.of("<gold>[BINGO]</gold> <green>Marked {drawn_number}.</green>")));
                showBingoCard(player, session, null);
            }
            case WON -> completeBingoWinner(session, player, result);
            case NOT_DRAWN -> player.sendMessage(Component.text("[BINGO] That number has not been drawn yet.", NamedTextColor.RED));
            case ALREADY_MARKED -> player.sendMessage(Component.text("[BINGO] That cell is already marked.", NamedTextColor.YELLOW));
            case NOT_PARTICIPANT -> player.sendMessage(Component.text("You are not participating in this Bingo round.", NamedTextColor.RED));
            case INVALID_CELL, NOT_ACTIVE -> player.sendMessage(Component.text("That Bingo action is invalid or stale.", NamedTextColor.RED));
        }
        return result;
    }

    private void completeBingoWinner(BingoSession session, Player player, BingoSession.MarkResult mark) {
        if (session.phase() != BingoSession.Phase.WON || !session.beginCompletion()) return;
        ChatEventConfig.Definition definition = session.definition();
        long elapsedNanos = Math.max(0, System.nanoTime() - (session.joinDeadlineNanos() - seconds(definition.bingo().joinSeconds())));
        long elapsedMs = elapsedNanos / 1_000_000L;
        ChatEventStatisticsService.PlayerStats stats = statistics.recordWin(session.runId(), player.getUniqueId(), player.getName(),
                definition.id(), ChatEventEngine.Type.BINGO, definition.rewardProfile(), elapsedMs);
        ChatEventRewardService.RewardResult reward = grantReward(player, definition);
        showBingoCard(player, session, mark.win());

        Map<String, Component> values = bingoValues(session);
        values.put("winner", Component.text(player.getName()));
        values.put("winner_uuid", Component.text(player.getUniqueId().toString()));
        values.put("pattern", Component.text(mark.win().pattern().displayName()));
        values.put("reward", Component.text(reward.summary()));
        values.put("reward_summary", Component.text(reward.summary()));
        values.put("winner_total_wins", Component.text(Long.toString(stats.totalWins())));
        values.put("winner_type_wins", Component.text(Long.toString(stats.typeWins(ChatEventEngine.Type.BINGO.name()))));
        List<Component> winnerCard = presentation.renderLines(List.of(
                "{separator}",
                "<gold><bold>BINGO WINNER</bold></gold>",
                "",
                "<white>{winner}</white> <gray>completed</gray> <aqua>{pattern}</aqua><gray>!</gray>",
                "<gray>Reward:</gray> <gold>{reward}</gold>",
                "",
                "<gray>Total Chat Event Wins:</gray> <yellow>{winner_total_wins}</yellow>",
                "<gray>Bingo Wins:</gray> <yellow>{winner_type_wins}</yellow>",
                "{separator}"), withSeparator(values));
        announceBingoParticipants(session, List.of(Component.text("Bingo complete — " + player.getName() + " won with " + mark.win().pattern().displayName() + ".", NamedTextColor.GOLD)));
        broadcast(withBlankSpacing(winnerCard));
        play(session.eligiblePlayers(), "win");
        lastEvent = definition.id() + "/WON";
        lastWinner = player.getName();
        if (reward.hasFailure()) recentFailure = "reward partial failure for " + definition.id();
        activeBingo.compareAndSet(session, null);
        scheduleNextInterval();
        plugin.getLogger().info("Bingo won: run=" + session.runId() + " winner=" + player.getUniqueId() + "/" + player.getName()
                + " pattern=" + mark.win().pattern() + " reward=" + definition.rewardProfile());
    }

    public boolean showBingoCard(Player player) {
        BingoSession session = activeBingo.get();
        if (session == null || session.participant(player.getUniqueId()) == null) return false;
        showBingoCard(player, session, session.winner() != null && session.winner().playerId().equals(player.getUniqueId()) ? session.winner().win() : null);
        return true;
    }

    private void showBingoCard(Player player, BingoSession session, com.antondev.chats.event.bingo.BingoBoard.Win win) {
        BingoSession.Participant participant = session.participant(player.getUniqueId());
        if (participant == null) return;
        send(player, BingoRenderer.render(session.runId(), participant, session.drawnNumbers(), session.drawCount(), session.lastDraw(), win));
    }

    public void onPlayerJoin(Player player) {
        BingoSession session = activeBingo.get();
        if (session != null && session.participant(player.getUniqueId()) != null) showBingoCard(player, session,
                session.winner() != null && session.winner().playerId().equals(player.getUniqueId()) ? session.winner().win() : null);
    }

    private void finishBingoWithoutWinner(BingoSession session, String state, String reason, boolean global) {
        announceBingoParticipants(session, List.of(Component.text(reason, state.equals("TIMED_OUT") ? NamedTextColor.YELLOW : NamedTextColor.GRAY)));
        if (global) {
            List<Component> lines = presentation.renderLines(List.of(
                    "{separator}",
                    "<yellow><bold>BINGO " + (state.equals("TIMED_OUT") ? "ENDED" : "CANCELLED") + "</bold></yellow>",
                    "<gray>{reason}</gray>",
                    "{separator}"), withSeparator(Map.of("reason", Component.text(reason))));
            broadcast(withBlankSpacing(lines));
        }
        clearBingo(session, state);
    }

    private void clearBingo(BingoSession session, String state) {
        lastEvent = session.definition().id() + "/" + state;
        lastWinner = "-";
        activeBingo.compareAndSet(session, null);
        scheduleNextInterval();
    }

    public void preview(String id, CommandSender sender) {
        ChatEventConfig current = config;
        if (current == null) { sender.sendMessage(Component.text("Chat Events are unavailable.")); return; }
        ChatEventConfig.Definition definition = current.definitions().get(id);
        if (definition == null) { sender.sendMessage(Component.text("Unknown Chat Event: " + id)); return; }
        if (definition.type() == ChatEventEngine.Type.BINGO) {
            sender.sendMessage(Component.text("Chat Event preview — " + definition.name() + " / BINGO"));
            sender.sendMessage(Component.text("Join: " + definition.bingo().joinSeconds() + "s • minimum participants: " + definition.bingo().minParticipants()));
            sender.sendMessage(Component.text("Draw: " + definition.bingo().firstDrawDelaySeconds() + "s initial / " + definition.bingo().drawIntervalSeconds() + "s interval"));
            sender.sendMessage(Component.text("Patterns: " + definition.bingo().winPatterns()));
            sender.sendMessage(Component.text("Reward: " + rewardDescription(definition.rewardProfile())));
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
        BingoSession bingo = activeBingo.get();
        if (bingo != null) return bingo.definition().id();
        ChatEventEngine.Competition value = active.get();
        return value == null ? "NONE" : value.round().definition().id();
    }
    public String activeType() { return activeBingo.get() != null ? "BINGO" : active.get() == null ? "-" : active.get().round().definition().type().name(); }
    public UUID activeRunId() {
        BingoSession bingo = activeBingo.get();
        if (bingo != null) return bingo.runId();
        ChatEventEngine.Competition value = active.get();
        return value == null ? null : value.round().runId();
    }
    public long remainingMillis() {
        BingoSession bingo = activeBingo.get();
        if (bingo != null) {
            long deadline = bingo.phase() == BingoSession.Phase.JOINING ? bingo.joinDeadlineNanos() : bingo.timeoutDeadlineNanos();
            return Math.max(0, (deadline - System.nanoTime()) / 1_000_000L);
        }
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
    public String bingoPhase() { BingoSession value = activeBingo.get(); return value == null ? "IDLE" : value.phase().name(); }
    public int bingoParticipantCount() { BingoSession value = activeBingo.get(); return value == null ? 0 : value.participantCount(); }
    public int bingoDrawCount() { BingoSession value = activeBingo.get(); return value == null ? 0 : value.drawCount(); }
    public String bingoLastDraw() { BingoSession value = activeBingo.get(); return value == null || value.lastDraw() <= 0 ? "-" : BingoRenderer.label(value.lastDraw()); }
    public int bingoRemainingCount() { BingoSession value = activeBingo.get(); return value == null ? 0 : value.remainingCount(); }
    public List<String> bingoParticipants() {
        BingoSession value = activeBingo.get();
        return value == null ? List.of() : value.participants().stream().map(BingoSession.Participant::playerName).toList();
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

    private Map<String, Component> bingoValues(BingoSession session) {
        Map<String, Component> values = baseValues(session.definition(), session.runId());
        values.put("participant_count", Component.text(Integer.toString(session.participantCount())));
        values.put("draw_count", Component.text(Integer.toString(session.drawCount())));
        values.put("drawn_number", Component.text(session.lastDraw() <= 0 ? "-" : BingoRenderer.label(session.lastDraw())));
        values.put("remaining_seconds", Component.text(Long.toString(Math.max(0, (session.timeoutDeadlineNanos() - System.nanoTime()) / 1_000_000_000L))));
        return values;
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

    private void announce(Set<UUID> audience, List<Component> lines) {
        for (UUID id : audience) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) send(player, lines);
        }
    }

    private void announceBingoParticipants(BingoSession session, List<Component> lines) {
        for (BingoSession.Participant participant : session.participants()) {
            Player player = Bukkit.getPlayer(participant.playerId());
            if (player != null && player.isOnline()) send(player, lines);
        }
    }

    private void broadcast(List<Component> lines) {
        for (Player player : Bukkit.getOnlinePlayers()) send(player, lines);
    }

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

    private static long seconds(long value) { return value <= 0 ? 0 : Math.multiplyExact(value, 1_000_000_000L); }
    private static String formatElapsed(long nanos) { return String.format(Locale.ROOT, "%.3fs", nanos / 1_000_000_000.0); }
}
