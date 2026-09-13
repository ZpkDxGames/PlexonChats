package com.antondev.chats.event.bingo;

import com.antondev.chats.event.ChatEventConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.random.RandomGenerator;

/** One globally-active Bingo run. Client clicks are requests; all authority stays in this object. */
public final class BingoSession {
    public enum Phase { JOINING, ACTIVE, WON, TIMED_OUT, CANCELLED }
    public enum JoinResult { JOINED, ALREADY_JOINED, CLOSED, NOT_ELIGIBLE }
    public enum MarkStatus { MARKED, WON, NOT_ACTIVE, NOT_PARTICIPANT, INVALID_CELL, NOT_DRAWN, ALREADY_MARKED }

    public record Participant(UUID playerId, String playerName, BingoBoard board) { }
    public record MarkResult(MarkStatus status, Integer number, BingoBoard.Win win) { }
    public record Winner(UUID playerId, String playerName, BingoBoard.Win win) { }

    private final UUID runId;
    private final ChatEventConfig.Definition definition;
    private final Set<UUID> eligiblePlayers;
    private final int minimumParticipants;
    private final long joinDeadlineNanos;
    private final long timeoutDeadlineNanos;
    private final long drawIntervalNanos;
    private final boolean freeCenter;
    private final Set<BingoPattern> patterns;
    private final Map<UUID, Participant> participants = new LinkedHashMap<>();
    private final List<Integer> remaining = new ArrayList<>(75);
    private final LinkedHashSet<Integer> drawn = new LinkedHashSet<>();
    private final AtomicReference<Phase> phase = new AtomicReference<>(Phase.JOINING);
    private final AtomicBoolean completionClaimed = new AtomicBoolean(false);
    private volatile Winner winner;
    private volatile long nextDrawNanos;
    private volatile int lastDraw;

    public BingoSession(UUID runId, ChatEventConfig.Definition definition, Set<UUID> eligiblePlayers,
                        int minimumParticipants, long joinDeadlineNanos, long timeoutDeadlineNanos,
                        long firstDrawNanos, long drawIntervalNanos, boolean freeCenter,
                        Set<BingoPattern> patterns, RandomGenerator random) {
        this.runId = runId;
        this.definition = definition;
        this.eligiblePlayers = Set.copyOf(eligiblePlayers);
        this.minimumParticipants = minimumParticipants;
        this.joinDeadlineNanos = joinDeadlineNanos;
        this.timeoutDeadlineNanos = timeoutDeadlineNanos;
        this.nextDrawNanos = firstDrawNanos;
        this.drawIntervalNanos = drawIntervalNanos;
        this.freeCenter = freeCenter;
        this.patterns = Set.copyOf(patterns);
        for (int number = 1; number <= 75; number++) remaining.add(number);
        Collections.shuffle(remaining, new java.util.Random(random.nextLong()));
    }

    public synchronized JoinResult join(UUID playerId, String playerName, long nowNanos, RandomGenerator random) {
        Participant current = participants.get(playerId);
        if (current != null) return JoinResult.ALREADY_JOINED;
        if (phase.get() != Phase.JOINING || nowNanos > joinDeadlineNanos) return JoinResult.CLOSED;
        if (!eligiblePlayers.contains(playerId)) return JoinResult.NOT_ELIGIBLE;
        participants.put(playerId, new Participant(playerId, playerName, BingoBoardGenerator.generate(random, freeCenter)));
        return JoinResult.JOINED;
    }

    public synchronized boolean activate(long nowNanos) {
        if (phase.get() != Phase.JOINING || nowNanos < joinDeadlineNanos || participants.size() < minimumParticipants) return false;
        return phase.compareAndSet(Phase.JOINING, Phase.ACTIVE);
    }

    public synchronized boolean cancelInsufficient(long nowNanos) {
        return phase.get() == Phase.JOINING && nowNanos >= joinDeadlineNanos && participants.size() < minimumParticipants
                && phase.compareAndSet(Phase.JOINING, Phase.CANCELLED);
    }

    public synchronized Integer draw(long nowNanos) {
        if (phase.get() != Phase.ACTIVE || nowNanos < nextDrawNanos || remaining.isEmpty()) return null;
        int value = remaining.removeFirst();
        drawn.add(value);
        lastDraw = value;
        nextDrawNanos = nowNanos + drawIntervalNanos;
        return value;
    }

    public synchronized MarkResult mark(UUID playerId, int cell) {
        if (phase.get() != Phase.ACTIVE) return new MarkResult(MarkStatus.NOT_ACTIVE, null, null);
        Participant participant = participants.get(playerId);
        if (participant == null) return new MarkResult(MarkStatus.NOT_PARTICIPANT, null, null);
        if (cell < 0 || cell >= BingoBoard.CELL_COUNT) return new MarkResult(MarkStatus.INVALID_CELL, null, null);
        int number = participant.board().numberAt(cell);
        BingoBoard.MarkResult result = participant.board().mark(cell, drawn);
        if (result == BingoBoard.MarkResult.NOT_DRAWN) return new MarkResult(MarkStatus.NOT_DRAWN, number, null);
        if (result == BingoBoard.MarkResult.ALREADY_MARKED) return new MarkResult(MarkStatus.ALREADY_MARKED, number, null);
        if (result == BingoBoard.MarkResult.INVALID_CELL) return new MarkResult(MarkStatus.INVALID_CELL, number, null);
        BingoBoard.Win win = participant.board().firstWin(patterns);
        if (win == null) return new MarkResult(MarkStatus.MARKED, number, null);
        if (!phase.compareAndSet(Phase.ACTIVE, Phase.WON)) return new MarkResult(MarkStatus.NOT_ACTIVE, number, null);
        winner = new Winner(playerId, participant.playerName(), win);
        return new MarkResult(MarkStatus.WON, number, win);
    }

    public boolean timeout(long nowNanos) {
        return nowNanos >= timeoutDeadlineNanos && phase.compareAndSet(Phase.ACTIVE, Phase.TIMED_OUT);
    }

    public boolean cancel() {
        Phase current = phase.get();
        return (current == Phase.JOINING || current == Phase.ACTIVE) && phase.compareAndSet(current, Phase.CANCELLED);
    }

    /** Shared exact-once completion boundary for reward/statistics/broadcast side effects. */
    public boolean beginCompletion() { return completionClaimed.compareAndSet(false, true); }

    public UUID runId() { return runId; }
    public ChatEventConfig.Definition definition() { return definition; }
    public Phase phase() { return phase.get(); }
    public long joinDeadlineNanos() { return joinDeadlineNanos; }
    public long timeoutDeadlineNanos() { return timeoutDeadlineNanos; }
    public long nextDrawNanos() { return nextDrawNanos; }
    public int lastDraw() { return lastDraw; }
    public Winner winner() { return winner; }
    public Set<BingoPattern> patterns() { return patterns; }
    public Set<UUID> eligiblePlayers() { return eligiblePlayers; }

    public synchronized int participantCount() { return participants.size(); }
    public synchronized Participant participant(UUID playerId) { return participants.get(playerId); }
    public synchronized List<Participant> participants() { return List.copyOf(participants.values()); }
    public synchronized Set<Integer> drawnNumbers() { return Set.copyOf(drawn); }
    public synchronized int drawCount() { return drawn.size(); }
    public synchronized int remainingCount() { return remaining.size(); }
}
