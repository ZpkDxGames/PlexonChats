package com.antondev.chats.event.bingo;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** One player's immutable card and mutable manual-mark state for a single Bingo run. */
public final class BingoParticipant {
    private final UUID playerId;
    private final String nameSnapshot;
    private final UUID runId;
    private final BingoBoard board;
    private final long joinedNanos;
    private final LinkedHashSet<Integer> markedCells = new LinkedHashSet<>();
    private boolean forfeited;

    public BingoParticipant(UUID playerId, String nameSnapshot, UUID runId, BingoBoard board, long joinedNanos, boolean freeCenter) {
        if (playerId == null || runId == null || board == null) throw new IllegalArgumentException("Bingo participant arguments must not be null");
        this.playerId = playerId;
        this.nameSnapshot = nameSnapshot == null || nameSnapshot.isBlank() ? playerId.toString() : nameSnapshot;
        this.runId = runId;
        this.board = board;
        this.joinedNanos = joinedNanos;
        if (freeCenter) markedCells.add(12);
    }

    public synchronized boolean markCell(int cell) {
        if (cell < 0 || cell >= BingoBoard.CELL_COUNT) return false;
        return markedCells.add(cell);
    }

    public synchronized boolean isMarked(int cell) { return markedCells.contains(cell); }
    public synchronized Set<Integer> markedCells() { return Set.copyOf(markedCells); }
    public synchronized int markedCount() { return markedCells.size(); }
    public synchronized boolean forfeited() { return forfeited; }
    public synchronized void forfeit() { forfeited = true; }

    public UUID playerId() { return playerId; }
    public String nameSnapshot() { return nameSnapshot; }
    public UUID runId() { return runId; }
    public BingoBoard board() { return board; }
    public long joinedNanos() { return joinedNanos; }
}
