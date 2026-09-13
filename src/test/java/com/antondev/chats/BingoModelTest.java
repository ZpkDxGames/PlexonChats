package com.antondev.chats;

import com.antondev.chats.event.bingo.BingoBoard;
import com.antondev.chats.event.bingo.BingoBoardGenerator;
import com.antondev.chats.event.bingo.BingoPattern;
import com.antondev.chats.event.bingo.BingoSession;
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
    @Test void generatedCardUsesTraditionalColumnRangesAndNoDuplicates() {
        BingoBoard board = BingoBoardGenerator.generate(new java.util.Random(42), true);
        Set<Integer> seen = new HashSet<>();
        for (int cell = 0; cell < BingoBoard.CELL_COUNT; cell++) {
            int value = board.numberAt(cell);
            if (cell == 12) {
                assertEquals(BingoBoard.FREE, value);
                assertTrue(board.isMarked(cell));
                continue;
            }
            int column = cell % 5;
            assertTrue(value >= column * 15 + 1 && value <= column * 15 + 15);
            assertTrue(seen.add(value), "duplicate board value " + value);
        }
    }

    @Test void generatedBoardIsStableAndTwoPlayersCanHaveDifferentCards() {
        BingoBoard first = BingoBoardGenerator.generate(new java.util.Random(12), true);
        int[] snapshot = first.numbers();
        first.mark(0, Set.of(first.numberAt(0)));
        assertArrayEquals(snapshot, first.numbers());
        BingoBoard second = BingoBoardGenerator.generate(new java.util.Random(13), true);
        assertFalse(java.util.Arrays.equals(first.numbers(), second.numbers()));
    }

    @Test void allSupportedPatternsAreDetectedAndAlmostPatternsDoNotWin() {
        for (BingoPattern pattern : BingoPattern.values()) {
            BingoBoard board = fixedBoard();
            List<Integer> cells = winningCells(pattern);
            Set<Integer> draws = new HashSet<>();
            for (int cell : cells) if (board.numberAt(cell) != BingoBoard.FREE) draws.add(board.numberAt(cell));
            for (int index = 0; index < cells.size() - 1; index++) board.mark(cells.get(index), draws);
            assertNull(board.firstWin(Set.of(pattern)), pattern + " must not win early");
            board.mark(cells.getLast(), draws);
            BingoBoard.Win win = board.firstWin(Set.of(pattern));
            assertNotNull(win, pattern.name());
            assertEquals(pattern, win.pattern());
            assertNull(board.firstWin(Set.of()), "disabled pattern must not win");
        }
    }

    @Test void sessionRejectsLateDuplicateAndUndrawnActions() {
        UUID eligible = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        BingoSession session = session(Set.of(eligible), 1, new java.util.Random(7));
        assertEquals(BingoSession.JoinResult.NOT_ELIGIBLE, session.join(outsider, "Out", 10, new java.util.Random(1)));
        assertEquals(BingoSession.JoinResult.JOINED, session.join(eligible, "Player", 10, new java.util.Random(2)));
        assertEquals(BingoSession.JoinResult.ALREADY_JOINED, session.join(eligible, "Player", 11, new java.util.Random(3)));
        assertEquals(BingoSession.JoinResult.CLOSED, session.join(UUID.randomUUID(), "Late", 101, new java.util.Random(4)));
        assertTrue(session.activate(100));
        BingoSession.Participant participant = session.participant(eligible);
        int cell = firstNonFree(participant.board());
        assertEquals(BingoSession.MarkStatus.NOT_DRAWN, session.mark(eligible, cell).status());
        assertEquals(BingoSession.MarkStatus.NOT_PARTICIPANT, session.mark(outsider, cell).status());
        assertEquals(BingoSession.MarkStatus.INVALID_CELL, session.mark(eligible, 99).status());
    }

    @Test void allSeventyFiveDrawsAreUniqueAndPoolExhausts() {
        UUID player = UUID.randomUUID();
        BingoSession session = session(Set.of(player), 1, new java.util.Random(9));
        assertEquals(BingoSession.JoinResult.JOINED, session.join(player, "Player", 1, new java.util.Random(10)));
        assertTrue(session.activate(100));
        Set<Integer> values = new HashSet<>();
        for (int index = 0; index < 75; index++) {
            Integer draw = session.draw(100 + index * 2L);
            assertNotNull(draw, "draw " + index);
            assertTrue(values.add(draw));
        }
        assertEquals(75, values.size());
        assertEquals(0, session.remainingCount());
        assertNull(session.draw(1000));
    }

    @Test void nearSimultaneousFinalMarksProduceOneWinnerAndOneCompletionClaim() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        BingoSession session = session(Set.of(first, second), 2, new java.util.Random(15));
        assertEquals(BingoSession.JoinResult.JOINED, session.join(first, "First", 1, new java.util.Random(16)));
        assertEquals(BingoSession.JoinResult.JOINED, session.join(second, "Second", 1, new java.util.Random(17)));
        assertTrue(session.activate(100));
        for (int index = 0; index < 75; index++) assertNotNull(session.draw(100 + index * 2L));

        List<Integer> firstRow = List.of(0, 1, 2, 3, 4);
        for (int index = 0; index < 4; index++) {
            assertEquals(BingoSession.MarkStatus.MARKED, session.mark(first, firstRow.get(index)).status());
            assertEquals(BingoSession.MarkStatus.MARKED, session.mark(second, firstRow.get(index)).status());
        }

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch fire = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger();
        List<Thread> threads = new ArrayList<>();
        for (UUID id : List.of(first, second)) {
            Thread thread = new Thread(() -> {
                ready.countDown();
                try { fire.await(); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); return; }
                if (session.mark(id, 4).status() == BingoSession.MarkStatus.WON) winners.incrementAndGet();
            });
            threads.add(thread);
            thread.start();
        }
        ready.await();
        fire.countDown();
        for (Thread thread : threads) thread.join();

        assertEquals(1, winners.get());
        assertEquals(BingoSession.Phase.WON, session.phase());
        assertNotNull(session.winner());
        assertTrue(session.beginCompletion());
        assertFalse(session.beginCompletion(), "reward/statistics/broadcast side effects must be exact-once");
    }

    private static BingoSession session(Set<UUID> eligible, int minimum, java.util.Random random) {
        return new BingoSession(UUID.randomUUID(), null, eligible, minimum, 100, 10_000,
                100, 1, true, Set.of(BingoPattern.ROW, BingoPattern.COLUMN, BingoPattern.DIAGONAL,
                BingoPattern.FOUR_CORNERS, BingoPattern.FULL_HOUSE), random);
    }

    private static int firstNonFree(BingoBoard board) {
        for (int cell = 0; cell < BingoBoard.CELL_COUNT; cell++) if (board.numberAt(cell) != BingoBoard.FREE) return cell;
        throw new AssertionError();
    }

    private static BingoBoard fixedBoard() {
        return new BingoBoard(new int[]{
                1, 16, 31, 46, 61,
                2, 17, 32, 47, 62,
                3, 18, 0, 48, 63,
                4, 19, 34, 49, 64,
                5, 20, 35, 50, 65
        });
    }

    private static List<Integer> winningCells(BingoPattern pattern) {
        return switch (pattern) {
            case ROW -> List.of(0, 1, 2, 3, 4);
            case COLUMN -> List.of(0, 5, 10, 15, 20);
            case DIAGONAL -> List.of(0, 6, 12, 18, 24);
            case FOUR_CORNERS -> List.of(0, 4, 20, 24);
            case FULL_HOUSE -> java.util.stream.IntStream.range(0, 25).boxed().toList();
        };
    }
}
