package com.antondev.chats.event.bingo;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/** Mutable mark state over one immutable 5x5 75-ball Bingo card. */
public final class BingoBoard {
    public static final int CELL_COUNT = 25;
    public static final int FREE = 0;

    public enum MarkResult { MARKED, ALREADY_MARKED, NOT_DRAWN, INVALID_CELL }
    public record Win(BingoPattern pattern, Set<Integer> cells) {
        public Win { cells = Set.copyOf(cells); }
    }

    private final int[] numbers;
    private final boolean[] marked;

    public BingoBoard(int[] numbers) {
        if (numbers == null || numbers.length != CELL_COUNT) throw new IllegalArgumentException("Bingo board must contain 25 cells");
        this.numbers = numbers.clone();
        validate();
        this.marked = new boolean[CELL_COUNT];
        for (int index = 0; index < CELL_COUNT; index++) if (this.numbers[index] == FREE) marked[index] = true;
    }

    private void validate() {
        LinkedHashSet<Integer> seen = new LinkedHashSet<>();
        for (int index = 0; index < CELL_COUNT; index++) {
            int number = numbers[index];
            if (number == FREE) {
                if (index != 12) throw new IllegalArgumentException("Only the center cell may be FREE");
                continue;
            }
            int column = index % 5;
            int min = column * 15 + 1;
            int max = min + 14;
            if (number < min || number > max) throw new IllegalArgumentException("Bingo value outside column range at cell " + index);
            if (!seen.add(number)) throw new IllegalArgumentException("Duplicate Bingo value " + number);
        }
    }

    public synchronized MarkResult mark(int cell, Set<Integer> drawnNumbers) {
        if (cell < 0 || cell >= CELL_COUNT) return MarkResult.INVALID_CELL;
        if (marked[cell]) return MarkResult.ALREADY_MARKED;
        int number = numbers[cell];
        if (number != FREE && !drawnNumbers.contains(number)) return MarkResult.NOT_DRAWN;
        marked[cell] = true;
        return MarkResult.MARKED;
    }

    public synchronized boolean isMarked(int cell) {
        return cell >= 0 && cell < CELL_COUNT && marked[cell];
    }

    public int numberAt(int cell) {
        if (cell < 0 || cell >= CELL_COUNT) throw new IndexOutOfBoundsException(cell);
        return numbers[cell];
    }

    public int[] numbers() { return numbers.clone(); }
    public synchronized boolean[] marks() { return marked.clone(); }

    public synchronized int markedCount() {
        int count = 0;
        for (boolean value : marked) if (value) count++;
        return count;
    }

    public synchronized Win firstWin(Set<BingoPattern> enabled) {
        for (BingoPattern pattern : BingoPattern.values()) {
            if (!enabled.contains(pattern)) continue;
            Set<Integer> cells = pattern.matchingCells(this);
            if (!cells.isEmpty()) return new Win(pattern, cells);
        }
        return null;
    }

    @Override public synchronized String toString() {
        return "BingoBoard{" + Arrays.toString(numbers) + ", marked=" + Arrays.toString(marked) + '}';
    }
}
