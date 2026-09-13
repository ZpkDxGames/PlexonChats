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
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/** One joinable Bingo run. Draw history is global; cards and marks are participant-owned. */
public final class BingoRun {
    public enum Phase { LOBBY, STARTING, ACTIVE, WON, TIMED_OUT, CANCELLED }
    public enum Source { SCHEDULED, ADMIN }
    public enum JoinStatus { JOINED, ALREADY_JOINED, NOT_OPEN, NOT_ELIGIBLE }
    public enum LeaveStatus { LEFT, FORFEITED, NOT_JOINED, NOT_OPEN }
    public enum ActivationStatus { ACTIVATED, NOT_ENOUGH_PARTICIPANTS, NOT_LOBBY }
    public enum MarkStatus { MARKED, ALREADY_MARKED, NOT_CALLED, NOT_ON_CARD, NOT_JOINED, NOT_ACTIVE, STALE_RUN, FORFEITED }
    public enum ClaimStatus { WON, NO_PATTERN, NOT_ACTIVE, NOT_ELIGIBLE, NOT_JOINED, STALE_RUN, FORFEITED }

    public record JoinResult(JoinStatus status, BingoParticipant participant) { }
    public record MarkResult(MarkStatus status, BingoParticipant participant, int cell, int number) { }
    public record Winner(UUID playerId, String playerName, BingoBoard.Win win) { }
    public record ClaimResult(ClaimStatus status, BingoBoard.Win win) { }

    private final UUID runId;
    private final ChatEventConfig.Definition definition;
    private final Source source;
    private final long lobbyOpenedNanos;
    private final long lobbyDeadlineNanos;
    private final int minimumParticipants;
    private final long durationNanos;
    private final long firstDrawDelayNanos;
    private final long drawIntervalNanos;
    private final Set<BingoPattern> patterns;
    private final List<Integer> reminderThresholds;
    private final LinkedHashSet<Integer> emittedReminders = new LinkedHashSet<>();
    private final LinkedHashMap<UUID, BingoParticipant> participants = new LinkedHashMap<>();
    private final List<Integer> remaining = new ArrayList<>(75);
    private final List<Integer> drawHistory = new ArrayList<>(75);
    private final LinkedHashSet<Integer> drawn = new LinkedHashSet<>(75);
    private final AtomicReference<Phase> phase = new AtomicReference<>(Phase.LOBBY);
    private final AtomicBoolean completionClaimed = new AtomicBoolean(false);
    private final boolean allowLateJoin;
    private final boolean requireOnlineAtStart;
    private final boolean freeCenter;
    private final RandomGenerator random;
    private volatile long activatedNanos;
    private volatile long timeoutDeadlineNanos;
    private volatile long nextDrawNanos;
    private volatile int lastDraw;
    private volatile Winner winner;

    public BingoRun(UUID runId, ChatEventConfig.Definition definition, Source source,
                    long lobbyOpenedNanos, long lobbyDeadlineNanos, int minimumParticipants,
                    long durationNanos, long firstDrawDelayNanos, long drawIntervalNanos,
                    Set<BingoPattern> patterns, List<Integer> reminders,
                    boolean allowLateJoin, boolean requireOnlineAtStart, boolean freeCenter,
                    RandomGenerator random) {
        if (runId == null || definition == null || source == null || random == null) throw new IllegalArgumentException("Bingo run arguments must not be null");
        if (lobbyDeadlineNanos < lobbyOpenedNanos) throw new IllegalArgumentException("Bingo lobby deadline must not precede opening");
        if (minimumParticipants < 1) throw new IllegalArgumentException("Bingo minimum participants must be at least one");
        if (durationNanos <= 0 || drawIntervalNanos <= 0 || firstDrawDelayNanos < 0) throw new IllegalArgumentException("Invalid Bingo timing");
        if (patterns == null || patterns.isEmpty()) throw new IllegalArgumentException("Bingo requires at least one winning pattern");
        this.runId = runId;
        this.definition = definition;
        this.source = source;
        this.lobbyOpenedNanos = lobbyOpenedNanos;
        this.lobbyDeadlineNanos = lobbyDeadlineNanos;
        this.minimumParticipants = minimumParticipants;
        this.durationNanos = durationNanos;
        this.firstDrawDelayNanos = firstDrawDelayNanos;
        this.drawIntervalNanos = drawIntervalNanos;
        this.patterns = Set.copyOf(patterns);
        ArrayList<Integer> thresholds = new ArrayList<>(reminders == null ? List.of() : reminders);
        thresholds.removeIf(value -> value == null || value <= 0);
        thresholds.sort(Collections.reverseOrder());
        this.reminderThresholds = List.copyOf(new LinkedHashSet<>(thresholds));
        this.allowLateJoin = allowLateJoin;
        this.requireOnlineAtStart = requireOnlineAtStart;
        this.freeCenter = freeCenter;
        this.random = random;
        for (int number = 1; number <= 75; number++) remaining.add(number);
        Collections.shuffle(remaining, new java.util.Random(random.nextLong()));
    }

    public synchronized JoinResult join(UUID playerId, String playerName, long nowNanos, boolean eligible) {
        if (!eligible) return new JoinResult(JoinStatus.NOT_ELIGIBLE, null);
        Phase current = phase.get();
        if (current != Phase.LOBBY && !(allowLateJoin && current == Phase.ACTIVE)) return new JoinResult(JoinStatus.NOT_OPEN, participants.get(playerId));
        BingoParticipant existing = participants.get(playerId);
        if (existing != null) return new JoinResult(JoinStatus.ALREADY_JOINED, existing);
        BingoParticipant participant = new BingoParticipant(playerId, playerName, runId, BingoBoardGenerator.generate(random), nowNanos, freeCenter);
        participants.put(playerId, participant);
        return new JoinResult(JoinStatus.JOINED, participant);
    }

    public synchronized LeaveStatus leave(UUID playerId) {
        BingoParticipant participant = participants.get(playerId);
        if (participant == null) return LeaveStatus.NOT_JOINED;
        Phase current = phase.get();
        if (current == Phase.LOBBY) {
            participants.remove(playerId);
            return LeaveStatus.LEFT;
        }
        if (current == Phase.ACTIVE) {
            participant.forfeit();
            return LeaveStatus.FORFEITED;
        }
        return LeaveStatus.NOT_OPEN;
    }

    /** Returns one due reminder, coalescing missed thresholds after lag instead of emitting a burst. */
    public synchronized Integer pollReminder(long nowNanos) {
        if (phase.get() != Phase.LOBBY || nowNanos >= lobbyDeadlineNanos) return null;
        long remainingNanos = Math.max(0, lobbyDeadlineNanos - nowNanos);
        int remainingSeconds = (int) Math.ceil(remainingNanos / 1_000_000_000.0);
        Integer chosen = null;
        for (int threshold : reminderThresholds) {
            if (remainingSeconds <= threshold && !emittedReminders.contains(threshold)) chosen = threshold;
        }
        if (chosen == null) return null;
        for (int threshold : reminderThresholds) if (threshold >= chosen) emittedReminders.add(threshold);
        return chosen;
    }

    public synchronized ActivationStatus activate(long nowNanos, Predicate<UUID> online) {
        if (phase.get() != Phase.LOBBY) return ActivationStatus.NOT_LOBBY;
        if (requireOnlineAtStart) participants.entrySet().removeIf(entry -> !online.test(entry.getKey()));
        if (participants.size() < minimumParticipants) {
            phase.set(Phase.CANCELLED);
            return ActivationStatus.NOT_ENOUGH_PARTICIPANTS;
        }
        if (!phase.compareAndSet(Phase.LOBBY, Phase.STARTING)) return ActivationStatus.NOT_LOBBY;
        activatedNanos = nowNanos;
        timeoutDeadlineNanos = nowNanos + durationNanos;
        nextDrawNanos = nowNanos + firstDrawDelayNanos;
        phase.set(Phase.ACTIVE);
        return ActivationStatus.ACTIVATED;
    }

    public synchronized Integer draw(long nowNanos) {
        if (phase.get() != Phase.ACTIVE || nowNanos < nextDrawNanos || remaining.isEmpty()) return null;
        int value = remaining.removeFirst();
        drawHistory.add(value);
        drawn.add(value);
        lastDraw = value;
        nextDrawNanos = nowNanos + drawIntervalNanos;
        return value;
    }

    public synchronized MarkResult mark(UUID playerId, UUID expectedRunId, int number) {
        if (!runId.equals(expectedRunId)) return new MarkResult(MarkStatus.STALE_RUN, null, -1, number);
        if (phase.get() != Phase.ACTIVE) return new MarkResult(MarkStatus.NOT_ACTIVE, participants.get(playerId), -1, number);
        BingoParticipant participant = participants.get(playerId);
        if (participant == null) return new MarkResult(MarkStatus.NOT_JOINED, null, -1, number);
        if (participant.forfeited()) return new MarkResult(MarkStatus.FORFEITED, participant, -1, number);
        int cell = participant.board().indexOf(number);
        if (cell < 0) return new MarkResult(MarkStatus.NOT_ON_CARD, participant, -1, number);
        if (!drawn.contains(number)) return new MarkResult(MarkStatus.NOT_CALLED, participant, cell, number);
        if (!participant.markCell(cell)) return new MarkResult(MarkStatus.ALREADY_MARKED, participant, cell, number);
        return new MarkResult(MarkStatus.MARKED, participant, cell, number);
    }

    /** Atomic winner boundary. Only this participant's manual marked cell indices are evaluated. */
    public synchronized ClaimResult claim(UUID playerId, String playerName, UUID expectedRunId, boolean eligible) {
        if (!runId.equals(expectedRunId)) return new ClaimResult(ClaimStatus.STALE_RUN, null);
        if (!eligible) return new ClaimResult(ClaimStatus.NOT_ELIGIBLE, null);
        if (phase.get() != Phase.ACTIVE) return new ClaimResult(ClaimStatus.NOT_ACTIVE, null);
        BingoParticipant participant = participants.get(playerId);
        if (participant == null) return new ClaimResult(ClaimStatus.NOT_JOINED, null);
        if (participant.forfeited()) return new ClaimResult(ClaimStatus.FORFEITED, null);
        BingoBoard.Win win = BingoPatternEvaluator.firstWin(participant.board(), participant.markedCells(), patterns);
        if (win == null) return new ClaimResult(ClaimStatus.NO_PATTERN, null);
        if (!phase.compareAndSet(Phase.ACTIVE, Phase.WON)) return new ClaimResult(ClaimStatus.NOT_ACTIVE, null);
        winner = new Winner(playerId, playerName == null || playerName.isBlank() ? participant.nameSnapshot() : playerName, win);
        return new ClaimResult(ClaimStatus.WON, win);
    }

    public boolean timeout(long nowNanos) {
        return phase.get() == Phase.ACTIVE && nowNanos >= timeoutDeadlineNanos && phase.compareAndSet(Phase.ACTIVE, Phase.TIMED_OUT);
    }

    public boolean exhaust(long nowNanos) {
        return phase.get() == Phase.ACTIVE && remainingCount() == 0 && nowNanos >= nextDrawNanos && phase.compareAndSet(Phase.ACTIVE, Phase.TIMED_OUT);
    }

    public boolean cancel() {
        Phase current = phase.get();
        return (current == Phase.LOBBY || current == Phase.STARTING || current == Phase.ACTIVE) && phase.compareAndSet(current, Phase.CANCELLED);
    }

    public boolean beginCompletion() { return completionClaimed.compareAndSet(false, true); }

    public synchronized BingoParticipant participant(UUID playerId) { return participants.get(playerId); }
    public synchronized Map<UUID, BingoParticipant> participants() { return Map.copyOf(participants); }
    public synchronized Set<UUID> participantIds() { return Set.copyOf(participants.keySet()); }
    public synchronized int participantCount() { return participants.size(); }
    public synchronized int onlineParticipantCount(Predicate<UUID> online) {
        int count = 0;
        for (UUID id : participants.keySet()) if (online.test(id)) count++;
        return count;
    }

    public UUID runId() { return runId; }
    public ChatEventConfig.Definition definition() { return definition; }
    public Source source() { return source; }
    public Phase phase() { return phase.get(); }
    public long lobbyOpenedNanos() { return lobbyOpenedNanos; }
    public long lobbyDeadlineNanos() { return lobbyDeadlineNanos; }
    public int minimumParticipants() { return minimumParticipants; }
    public long startedNanos() { return activatedNanos; }
    public long timeoutDeadlineNanos() { return timeoutDeadlineNanos; }
    public long nextDrawNanos() { return nextDrawNanos; }
    public int lastDraw() { return lastDraw; }
    public Winner winner() { return winner; }
    public Set<BingoPattern> patterns() { return patterns; }
    public List<Integer> reminders() { return reminderThresholds; }

    public synchronized List<Integer> drawHistory() { return List.copyOf(drawHistory); }
    public synchronized Set<Integer> drawnNumbers() { return Set.copyOf(drawn); }
    public synchronized int drawCount() { return drawHistory.size(); }
    public synchronized int remainingCount() { return remaining.size(); }
}
