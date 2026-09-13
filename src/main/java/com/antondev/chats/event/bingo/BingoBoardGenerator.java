package com.antondev.chats.event.bingo;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/** Generates traditional 75-ball cards with unique values inside each B/I/N/G/O column. */
public final class BingoBoardGenerator {
    private BingoBoardGenerator() { }

    public static BingoBoard generate(RandomGenerator random, boolean freeCenter) {
        if (random == null) throw new IllegalArgumentException("random");
        int[] values = new int[BingoBoard.CELL_COUNT];
        for (int column = 0; column < 5; column++) {
            int start = column * 15 + 1;
            List<Integer> pool = new ArrayList<>(15);
            for (int number = start; number < start + 15; number++) pool.add(number);
            for (int row = 0; row < 5; row++) {
                int target = row * 5 + column;
                if (freeCenter && target == 12) {
                    values[target] = BingoBoard.FREE;
                    continue;
                }
                values[target] = pool.remove(random.nextInt(pool.size()));
            }
        }
        return new BingoBoard(values);
    }
}
