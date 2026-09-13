package com.antondev.chats.event.bingo;

import java.util.LinkedHashSet;
import java.util.Set;

/** Pure Bingo evaluator. The supplied set contains manually marked cell indices, never global draw values. */
public final class BingoPatternEvaluator {
    private BingoPatternEvaluator() { }

    public static BingoBoard.Win firstWin(BingoBoard board, Set<Integer> markedCells, Set<BingoPattern> enabled) {
        if (enabled.contains(BingoPattern.ROW)) {
            for (int row = 0; row < 5; row++) {
                Set<Integer> cells = row(row);
                if (complete(markedCells, cells)) return new BingoBoard.Win(BingoPattern.ROW, cells);
            }
        }
        if (enabled.contains(BingoPattern.COLUMN)) {
            for (int column = 0; column < 5; column++) {
                Set<Integer> cells = column(column);
                if (complete(markedCells, cells)) return new BingoBoard.Win(BingoPattern.COLUMN, cells);
            }
        }
        if (enabled.contains(BingoPattern.DIAGONAL)) {
            Set<Integer> first = Set.of(0, 6, 12, 18, 24);
            if (complete(markedCells, first)) return new BingoBoard.Win(BingoPattern.DIAGONAL, first);
            Set<Integer> second = Set.of(4, 8, 12, 16, 20);
            if (complete(markedCells, second)) return new BingoBoard.Win(BingoPattern.DIAGONAL, second);
        }
        if (enabled.contains(BingoPattern.FULL_HOUSE)) {
            LinkedHashSet<Integer> all = new LinkedHashSet<>();
            for (int cell = 0; cell < BingoBoard.CELL_COUNT; cell++) all.add(cell);
            if (complete(markedCells, all)) return new BingoBoard.Win(BingoPattern.FULL_HOUSE, all);
        }
        return null;
    }

    public static boolean complete(Set<Integer> markedCells, Set<Integer> cells) {
        return markedCells.containsAll(cells);
    }

    private static Set<Integer> row(int row) {
        LinkedHashSet<Integer> cells = new LinkedHashSet<>();
        for (int column = 0; column < 5; column++) cells.add(row * 5 + column);
        return Set.copyOf(cells);
    }

    private static Set<Integer> column(int column) {
        LinkedHashSet<Integer> cells = new LinkedHashSet<>();
        for (int row = 0; row < 5; row++) cells.add(row * 5 + column);
        return Set.copyOf(cells);
    }
}
