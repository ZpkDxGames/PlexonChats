package com.antondev.chats.event.bingo;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/** Immutable 5x5 75-ball Bingo card. Mark state is participant-owned and represented by cell indices. */
public final class BingoBoard {
    public static final int CELL_COUNT = 25;

    public record Win(BingoPattern pattern, Set<Integer> cells) {
        public Win { cells = Set.copyOf(cells); }
    }

    private final int[] numbers;

    public BingoBoard(int[] numbers) {
        if (numbers == null || numbers.length != CELL_COUNT) {
            throw new IllegalArgumentException("Bingo board must contain exactly 25 numbered cells");
        }
        this.numbers = numbers.clone();
        validate();
    }

    private void validate() {
        LinkedHashSet<Integer> seen = new LinkedHashSet<>();
        for (int index = 0; index < CELL_COUNT; index++) {
            int value = numbers[index];
            int column = index % 5;
            int min = column * 15 + 1;
            int max = min + 14;
            if (value < min || value > max) {
                throw new IllegalArgumentException("Bingo value outside B/I/N/G/O range at cell " + index + ": " + value);
            }
            if (!seen.add(value)) throw new IllegalArgumentException("Duplicate Bingo value " + value);
        }
    }

    public int numberAt(int cell) {
        if (cell < 0 || cell >= CELL_COUNT) throw new IndexOutOfBoundsException(cell);
        return numbers[cell];
    }

    public int[] numbers() { return numbers.clone(); }

    public int indexOf(int number) {
        for (int index = 0; index < numbers.length; index++) if (numbers[index] == number) return index;
        return -1;
    }

    public boolean isMarked(int cell, Set<Integer> markedCells) {
        return cell >= 0 && cell < CELL_COUNT && markedCells.contains(cell);
    }

    public boolean[] marks(Set<Integer> markedCells) {
        boolean[] result = new boolean[CELL_COUNT];
        for (int index = 0; index < CELL_COUNT; index++) result[index] = markedCells.contains(index);
        return result;
    }

    public int markedCount(Set<Integer> markedCells) {
        int count = 0;
        for (int index = 0; index < CELL_COUNT; index++) if (markedCells.contains(index)) count++;
        return count;
    }

    @Override public String toString() { return "BingoBoard" + Arrays.toString(numbers); }
}
