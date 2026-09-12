package com.antondev.chats.event.bingo;

import java.util.LinkedHashSet;
import java.util.Set;

/** Supported server-authoritative 5x5 Bingo win patterns. */
public enum BingoPattern {
    ROW,
    COLUMN,
    DIAGONAL,
    FOUR_CORNERS,
    FULL_HOUSE;

    public Set<Integer> matchingCells(BingoBoard board) {
        return switch (this) {
            case ROW -> firstCompleteRow(board);
            case COLUMN -> firstCompleteColumn(board);
            case DIAGONAL -> firstCompleteDiagonal(board);
            case FOUR_CORNERS -> complete(board, Set.of(0, 4, 20, 24));
            case FULL_HOUSE -> {
                LinkedHashSet<Integer> all = new LinkedHashSet<>();
                for (int index = 0; index < BingoBoard.CELL_COUNT; index++) all.add(index);
                yield complete(board, all);
            }
        };
    }

    private static Set<Integer> firstCompleteRow(BingoBoard board) {
        for (int row = 0; row < 5; row++) {
            LinkedHashSet<Integer> cells = new LinkedHashSet<>();
            for (int column = 0; column < 5; column++) cells.add(row * 5 + column);
            Set<Integer> complete = complete(board, cells);
            if (!complete.isEmpty()) return complete;
        }
        return Set.of();
    }

    private static Set<Integer> firstCompleteColumn(BingoBoard board) {
        for (int column = 0; column < 5; column++) {
            LinkedHashSet<Integer> cells = new LinkedHashSet<>();
            for (int row = 0; row < 5; row++) cells.add(row * 5 + column);
            Set<Integer> complete = complete(board, cells);
            if (!complete.isEmpty()) return complete;
        }
        return Set.of();
    }

    private static Set<Integer> firstCompleteDiagonal(BingoBoard board) {
        Set<Integer> first = complete(board, Set.of(0, 6, 12, 18, 24));
        return first.isEmpty() ? complete(board, Set.of(4, 8, 12, 16, 20)) : first;
    }

    private static Set<Integer> complete(BingoBoard board, Set<Integer> cells) {
        for (int cell : cells) if (!board.isMarked(cell)) return Set.of();
        return Set.copyOf(cells);
    }
}
