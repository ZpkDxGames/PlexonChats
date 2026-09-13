package com.antondev.chats.event.bingo;

import com.antondev.chats.event.ChatEventConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.random.RandomGenerator;

/** One server-authoritative shared Bingo run. No participant card or client mark state exists. */
public final class BingoRun {
    public enum Phase { STARTING, ACTIVE, WON, TIMED_OUT, CANCELLED }
    public enum ClaimStatus { WON, NO_PATTERN, NOT_ACTIVE, NOT_ELIGIBLE }

    public record Winner(UUID playerId, String playerName, BingoBoard.Win win) { }
    public record ClaimResult(ClaimStatus status, BingoBoard.Win win) { }

    private final UUID runId;
    private final ChatEventConfig.Definition definition;
    private final BingoBoard board;
    private final Set<BingoPattern> patterns;
    private final List<Integer> remaining = new ArrayList<>(75);
    private final List<Integer> drawHistory = new ArrayList<>(75);
    private final LinkedHashSet<Integer> drawn = new LinkedHashSet<>(75);
    private final long startedNanos;
    private final long timeoutDeadlineNanos;
    private final long drawIntervalNanos;
    private final AtomicReference<Phase> phase = new AtomicReference<>(Phase.STARTING);
    private final AtomicBoolean completionClaimed = new AtomicBoolean(false);
    private volatile long nextDrawNanos;
    private volatile int lastDraw;
    private volatile Winner winner;

    public BingoRun(UUID runId, ChatEventConfig.Definition definition, long startedNanos,
                    long timeoutDeadlineNanos, long firstDrawNanos, long drawIntervalNanos,
                    Set<BingoPattern> patterns, RandomGenerator random) {
        if (runId == null || definition == null || random == null) throw new IllegalArgumentException("Bingo run arguments must not be null");
        if (timeoutDeadlineNanos <= startedNanos) throw new IllegalArgumentException("Bingo timeout must follow start");
        if (drawIntervalNanos <= 0) throw new IllegalArgumentException("Bingo draw interval must be positive");
        if (patterns == null || patterns.isEmpty()) throw new IllegalArgumentException("Bingo requires at least one winning pattern");
        this.runId = runId;
        this.definition = definition;
        this.startedNanos = startedNanos;
        this.timeoutDeadlineNanos = timeoutDeadlineNanos;
        this.nextDrawNanos = firstDrawNanos;
        this.drawIntervalNanos = drawIntervalNanos;
        this.patterns = Set.copyOf(patterns);
        this.board = BingoBoardGenerator.generate(random);
        for (int number = 1; number <= 75; number++) remaining.add(number);
        Collections.shuffle(remaining, new java.util.Random(random.nextLong()));
    }

    public boolean activate() { return phase.compareAndSet(Phase.STARTING, Phase.ACTIVE); }

    public synchronized Integer draw(long nowNanos) {
        if (phase.get() != Phase.ACTIVE || nowNanos < nextDrawNanos || remaining.isEmpty()) return null;
        int value = remaining.removeFirst();
        drawHistory.add(value);
        drawn.add(value);
        lastDraw = value;
        nextDrawNanos = nowNanos + drawIntervalNanos;
        return value;
    }

    /** Atomic winner boundary: evaluation is authoritative and only one ACTIVE -> WON transition can succeed. */
    public synchronized ClaimResult claim(UUID playerId, String playerName, boolean eligible) {
        if (!eligible) return new ClaimResult(ClaimStatus.NOT_ELIGIBLE, null);
        if (phase.get() != Phase.ACTIVE) return new ClaimResult(ClaimStatus.NOT_ACTIVE, null);
        BingoBoard.Win win = BingoPatternEvaluator.firstWin(board, drawn, patterns);
        if (win == null) return new ClaimResult(ClaimStatus.NO_PATTERN, null);
        if (!phase.compareAndSet(Phase.ACTIVE, Phase.WON)) return new ClaimResult(ClaimStatus.NOT_ACTIVE, null);
        winner = new Winner(playerId, playerName, win);
        return new ClaimResult(ClaimStatus.WON, win);
    }

    public boolean timeout(long nowNanos) {
        return nowNanos >= timeoutDeadlineNanos && phase.compareAndSet(Phase.ACTIVE, Phase.TIMED_OUT);
    }

    /** Gives players one normal draw interval after the final call before exhaustion closes the run. */
    public boolean exhaust(long nowNanos) {
        return remainingCount() == 0 && nowNanos >= nextDrawNanos && phase.compareAndSet(Phase.ACTIVE, Phase.TIMED_OUT);
    }

    public boolean cancel() {
        Phase current = phase.get();
        return (current == Phase.STARTING || current == Phase.ACTIVE) && phase.compareAndSet(current, Phase.CANCELLED);
    }

    /** Shared exact-once gate for reward, persistence and winner publication side effects. */
    public boolean beginCompletion() { return completionClaimed.compareAndSet(false, true); }

    public UUID runId() { return runId; }
    public ChatEventConfig.Definition definition() { return definition; }
    public BingoBoard board() { return board; }
    public Phase phase() { return phase.get(); }
    public long startedNanos() { return startedNanos; }
    public long timeoutDeadlineNanos() { return timeoutDeadlineNanos; }
    public long nextDrawNanos() { return nextDrawNanos; }
    public int lastDraw() { return lastDraw; }
    public Winner winner() { return winner; }
    public Set<BingoPattern> patterns() { return patterns; }

    public synchronized List<Integer> drawHistory() { return List.copyOf(drawHistory); }
    public synchronized Set<Integer> drawnNumbers() { return Set.copyOf(drawn); }
    public synchronized int drawCount() { return drawHistory.size(); }
    public synchronized int remainingCount() { return remaining.size(); }
}
