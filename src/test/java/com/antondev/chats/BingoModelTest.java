package com.antondev.chats;

import com.antondev.chats.event.ChatEventConfig;
import com.antondev.chats.event.ChatEventEngine;
import com.antondev.chats.event.bingo.BingoAnsiRenderer;
import com.antondev.chats.event.bingo.BingoBoard;
import com.antondev.chats.event.bingo.BingoBoardGenerator;
import com.antondev.chats.event.bingo.BingoPattern;
import com.antondev.chats.event.bingo.BingoPatternEvaluator;
import com.antondev.chats.event.bingo.BingoRenderer;
import com.antondev.chats.event.bingo.BingoRun;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BingoModelTest {
    @Test void generatedBoardUsesTraditionalRangesTwentyFiveUniqueNumbersAndNoFreeTile() {
        for (int seed = 0; seed < 50; seed++) {
            BingoBoard board = BingoBoardGenerator.generate(new java.util.Random(seed));
            Set<Integer> seen = new HashSet<>();
            for (int cell = 0; cell < BingoBoard.CELL_COUNT; cell++) {
                int value = board.numberAt(cell);
                int column = cell % 5;
                assertTrue(value >= column * 15 + 1 && value <= column * 15 + 15);
                assertTrue(seen.add(value), "duplicate board value " + value);
                assertNotEquals(0, value);
            }
            assertEquals(25, seen.size());
            assertTrue(board.numberAt(12) >= 31 && board.numberAt(12) <= 45, "center must be a normal N number");
        }
    }

    @Test void renderedBoardIsSixColumnsBySixRowsIncludingHeader() {
        BingoRun run = run(new java.util.Random(42));
        assertTrue(run.activate());
        List<String> lines = BingoRenderer.plainLines(run);
        assertEquals(6, lines.size());
        assertEquals("# |  B |  I |  N |  G |  O", lines.getFirst());
        for (int row = 1; row <= 5; row++) assertTrue(lines.get(row).startsWith(row + " | "));
    }

    @Test void everyHorizontalVerticalAndDiagonalPatternUsesOnlyDrawHistory() {
        BingoBoard board = fixedBoard();
        for (int row = 0; row < 5; row++) {
            Set<Integer> draws = numbers(board, row * 5, row * 5 + 1, row * 5 + 2, row * 5 + 3, row * 5 + 4);
            assertEquals(BingoPattern.ROW, BingoPatternEvaluator.firstWin(board, draws, Set.of(BingoPattern.ROW)).pattern());
        }
        for (int column = 0; column < 5; column++) {
            Set<Integer> draws = numbers(board, column, column + 5, column + 10, column + 15, column + 20);
            assertEquals(BingoPattern.COLUMN, BingoPatternEvaluator.firstWin(board, draws, Set.of(BingoPattern.COLUMN)).pattern());
        }
        assertEquals(BingoPattern.DIAGONAL, BingoPatternEvaluator.firstWin(board, numbers(board, 0, 6, 12, 18, 24), Set.of(BingoPattern.DIAGONAL)).pattern());
        assertEquals(BingoPattern.DIAGONAL, BingoPatternEvaluator.firstWin(board, numbers(board, 4, 8, 12, 16, 20), Set.of(BingoPattern.DIAGONAL)).pattern());
    }

    @Test void almostCompleteDisabledAndFullHouseRulesAreStrict() {
        BingoBoard board = fixedBoard();
        Set<Integer> four = numbers(board, 0, 1, 2, 3);
        assertNull(BingoPatternEvaluator.firstWin(board, four, Set.of(BingoPattern.ROW)));
        Set<Integer> row = numbers(board, 0, 1, 2, 3, 4);
        assertNull(BingoPatternEvaluator.firstWin(board, row, Set.of(BingoPattern.DIAGONAL)));
        Set<Integer> all = new HashSet<>();
        for (int value : board.numbers()) all.add(value);
        assertEquals(BingoPattern.FULL_HOUSE, BingoPatternEvaluator.firstWin(board, all, Set.of(BingoPattern.FULL_HOUSE)).pattern());
        all.remove(board.numberAt(24));
        assertNull(BingoPatternEvaluator.firstWin(board, all, Set.of(BingoPattern.FULL_HOUSE)));
    }

    @Test void drawPoolContainsOneThroughSeventyFiveExactlyOnceAndMarksOnlyBoardMatches() {
        BingoRun run = run(new java.util.Random(9));
        assertTrue(run.activate());
        Set<Integer> values = new HashSet<>();
        for (int index = 0; index < 75; index++) {
            Integer draw = run.draw(index);
            assertNotNull(draw, "draw " + index);
            assertTrue(values.add(draw));
        }
        assertEquals(75, values.size());
        for (int number = 1; number <= 75; number++) assertTrue(values.contains(number));
        assertEquals(0, run.remainingCount());
        assertNull(run.draw(1000));
        assertEquals(25, run.board().markedCount(run.drawnNumbers()));
    }

    @Test void nearSimultaneousValidClaimsProduceExactlyOneWinnerAndOneCompletionOwner() throws Exception {
        BingoRun run = run(new java.util.Random(15));
        assertTrue(run.activate());
        for (int index = 0; index < 75; index++) assertNotNull(run.draw(index));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch fire = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger();
        List<Thread> threads = new ArrayList<>();
        for (String name : List.of("First", "Second")) {
            Thread thread = new Thread(() -> {
                ready.countDown();
                try { fire.await(); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); return; }
                if (run.claim(UUID.randomUUID(), name, true).status() == BingoRun.ClaimStatus.WON) winners.incrementAndGet();
            });
            threads.add(thread);
            thread.start();
        }
        ready.await(); fire.countDown();
        for (Thread thread : threads) thread.join();
        assertEquals(1, winners.get());
        assertEquals(BingoRun.Phase.WON, run.phase());
        assertNotNull(run.winner());
        assertTrue(run.beginCompletion());
        assertFalse(run.beginCompletion());
        assertNull(run.draw(1000), "terminal winner state must freeze draws");
    }

    @Test void invalidAndIneligibleClaimsDoNotTerminateRun() {
        BingoRun run = run(new java.util.Random(1));
        assertTrue(run.activate());
        assertEquals(BingoRun.ClaimStatus.NOT_ELIGIBLE, run.claim(UUID.randomUUID(), "Nope", false).status());
        assertEquals(BingoRun.ClaimStatus.NO_PATTERN, run.claim(UUID.randomUUID(), "Early", true).status());
        assertEquals(BingoRun.Phase.ACTIVE, run.phase());
    }

    @Test void ansiRendererUsesAnsiFenceAndGreenOnlyForCalledBoardCells() {
        BingoRun run = run(new java.util.Random(2));
        assertTrue(run.activate());
        String before = BingoAnsiRenderer.render(run);
        assertTrue(before.startsWith("```ansi\n"));
        assertFalse(before.contains("\u001B[1;32m"));
        while (run.board().indexOf(run.lastDraw()) < 0) assertNotNull(run.draw(run.drawCount()));
        String after = BingoAnsiRenderer.render(run);
        assertTrue(after.contains("\u001B[1;32m"));
        assertTrue(after.endsWith("```"));
    }

    private static BingoRun run(java.util.Random random) {
        return new BingoRun(UUID.randomUUID(), definition(), 0, 10_000, 0, 1,
                Set.of(BingoPattern.ROW, BingoPattern.COLUMN, BingoPattern.DIAGONAL, BingoPattern.FULL_HOUSE), random);
    }

    private static ChatEventConfig.Definition definition() {
        return new ChatEventConfig.Definition("bingo-classic", "Classic Bingo", ChatEventEngine.Type.BINGO, true, 5, 0,
                "epic", 420, true, 1, "", Set.of(), Set.of(), Set.of(ChatChannel.LOCAL, ChatChannel.GLOBAL),
                ChatEventConfig.Matching.standard(), "", List.of(), List.of(), List.of(), Set.of(), 1, 10, false,
                ChatEventConfig.BingoSettings.defaults());
    }

    private static BingoBoard fixedBoard() {
        return new BingoBoard(new int[]{
                1, 16, 31, 46, 61,
                2, 17, 32, 47, 62,
                3, 18, 33, 48, 63,
                4, 19, 34, 49, 64,
                5, 20, 35, 50, 65
        });
    }

    private static Set<Integer> numbers(BingoBoard board, int... cells) {
        Set<Integer> values = new HashSet<>();
        for (int cell : cells) values.add(board.numberAt(cell));
        return values;
    }
}
