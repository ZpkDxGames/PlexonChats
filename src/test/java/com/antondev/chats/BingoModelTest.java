package com.antondev.chats;

import com.antondev.chats.event.ChatEventConfig;
import com.antondev.chats.event.ChatEventEngine;
import com.antondev.chats.event.bingo.BingoBoard;
import com.antondev.chats.event.bingo.BingoBoardGenerator;
import com.antondev.chats.event.bingo.BingoParticipant;
import com.antondev.chats.event.bingo.BingoPattern;
import com.antondev.chats.event.bingo.BingoPatternEvaluator;
import com.antondev.chats.event.bingo.BingoRenderer;
import com.antondev.chats.event.bingo.BingoRun;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
    @Test void generatedCardsUseTraditionalRangesTwentyFiveUniqueNumbersAndNoFreeTile() {
        for (int seed = 0; seed < 50; seed++) {
            BingoBoard board = BingoBoardGenerator.generate(new java.util.Random(seed));
            Set<Integer> seen = new HashSet<>();
            for (int cell = 0; cell < BingoBoard.CELL_COUNT; cell++) {
                int value = board.numberAt(cell), column = cell % 5;
                assertTrue(value >= column * 15 + 1 && value <= column * 15 + 15);
                assertTrue(seen.add(value));
            }
            assertEquals(25, seen.size());
            assertTrue(board.numberAt(12) >= 31 && board.numberAt(12) <= 45);
        }
    }

    @Test void lobbyRemindersEmitExactlyOnceAndLagCoalescesMissedThresholds() {
        BingoRun run = run(60, 2, 1);
        assertEquals(60, run.pollReminder(0));
        assertNull(run.pollReminder(1));
        assertEquals(30, run.pollReminder(seconds(31)));
        assertNull(run.pollReminder(seconds(31)));
        assertEquals(15, run.pollReminder(seconds(46)));
        assertEquals(5, run.pollReminder(seconds(56)));
        assertNull(run.pollReminder(seconds(59)));

        BingoRun lagged = run(60, 2, 2);
        assertEquals(60, lagged.pollReminder(0));
        assertEquals(15, lagged.pollReminder(seconds(46)), "missed 30s threshold must be coalesced, not burst");
        assertEquals(5, lagged.pollReminder(seconds(56)));
    }

    @Test void joinIsExplicitIdempotentAndCardsAreStablePerParticipant() {
        BingoRun run = run(60, 1, 3);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        var firstJoin = run.join(first, "First", 1, true);
        assertEquals(BingoRun.JoinStatus.JOINED, firstJoin.status());
        BingoBoard card = firstJoin.participant().board();
        assertSame(card, run.join(first, "First", 2, true).participant().board());
        assertEquals(BingoRun.JoinStatus.JOINED, run.join(second, "Second", 3, true).status());
        assertEquals(2, run.participantCount());
        assertNotSame(card, run.participant(second).board());
    }

    @Test void drawNeverMarksAndOnlyCalledCardValuesCanBeMarked() {
        BingoRun run = activeRun(4, 2);
        List<BingoParticipant> participants = new ArrayList<>(run.participants().values());
        BingoParticipant first = participants.get(0), second = participants.get(1);
        int target = first.board().numberAt(0);
        assertEquals(BingoRun.MarkStatus.NOT_CALLED, run.mark(first.playerId(), run.runId(), target).status());
        assertEquals(0, first.markedCount());

        int now = 0;
        while (!run.drawnNumbers().contains(target)) assertNotNull(run.draw(now++));
        assertEquals(0, first.markedCount(), "drawing a number must not mark any participant");
        assertEquals(0, second.markedCount(), "global draw state must not leak into another card");
        assertEquals(BingoRun.MarkStatus.MARKED, run.mark(first.playerId(), run.runId(), target).status());
        assertEquals(1, first.markedCount());
        assertEquals(0, second.markedCount());
        assertEquals(BingoRun.MarkStatus.ALREADY_MARKED, run.mark(first.playerId(), run.runId(), target).status());
        assertEquals(BingoRun.MarkStatus.STALE_RUN, run.mark(first.playerId(), UUID.randomUUID(), target).status());
    }

    @Test void markValidationRejectsNonParticipantAndNumbersOutsidePersonalCard() {
        BingoRun run = activeRun(40, 1);
        BingoParticipant participant = run.participants().values().iterator().next();
        drawAll(run);
        int onCard = participant.board().numberAt(0);
        assertEquals(BingoRun.MarkStatus.NOT_JOINED,
                run.mark(UUID.randomUUID(), run.runId(), onCard).status());
        int outside = java.util.stream.IntStream.rangeClosed(1, 75)
                .filter(value -> participant.board().indexOf(value) < 0)
                .findFirst().orElseThrow();
        assertTrue(run.drawnNumbers().contains(outside));
        assertEquals(BingoRun.MarkStatus.NOT_ON_CARD,
                run.mark(participant.playerId(), run.runId(), outside).status());
        assertEquals(0, participant.markedCount());
    }

    @Test void scheduledActivationStillRequiresConfiguredProductionMinimum() {
        BingoRun scheduled = new BingoRun(UUID.randomUUID(), definition(), BingoRun.Source.SCHEDULED,
                0, seconds(60), 2, seconds(600), 0, 1,
                Set.of(BingoPattern.ROW, BingoPattern.COLUMN, BingoPattern.DIAGONAL), List.of(60, 30, 15, 5),
                false, true, false, new java.util.Random(41));
        assertEquals(BingoRun.JoinStatus.JOINED,
                scheduled.join(UUID.randomUUID(), "OnlyScheduledPlayer", 1, true).status());
        assertEquals(BingoRun.ActivationStatus.NOT_ENOUGH_PARTICIPANTS,
                scheduled.activate(seconds(60), ignored -> true));
        assertEquals(BingoRun.Phase.CANCELLED, scheduled.phase());
    }

    @Test void globalDrawHistoryCannotWinButManualMarksCan() {
        BingoRun run = activeRun(5, 1);
        BingoParticipant participant = run.participants().values().iterator().next();
        drawAll(run);
        assertNull(BingoPatternEvaluator.firstWin(participant.board(), participant.markedCells(), run.patterns()));
        assertEquals(BingoRun.ClaimStatus.NO_PATTERN,
                run.claim(participant.playerId(), participant.nameSnapshot(), run.runId(), true).status());
        for (int cell = 0; cell < 5; cell++) {
            int number = participant.board().numberAt(cell);
            assertEquals(BingoRun.MarkStatus.MARKED, run.mark(participant.playerId(), run.runId(), number).status());
        }
        BingoRun.ClaimResult claim = run.claim(participant.playerId(), participant.nameSnapshot(), run.runId(), true);
        assertEquals(BingoRun.ClaimStatus.WON, claim.status());
        assertEquals(BingoPattern.ROW, claim.win().pattern());
        assertEquals(BingoRun.MarkStatus.NOT_ACTIVE,
                run.mark(participant.playerId(), run.runId(), participant.board().numberAt(5)).status());
    }

    @Test void horizontalVerticalDiagonalAndFullHouseEvaluateManualCellIndicesOnly() {
        BingoBoard board = fixedBoard();
        assertEquals(BingoPattern.ROW, BingoPatternEvaluator.firstWin(board, Set.of(0, 1, 2, 3, 4), Set.of(BingoPattern.ROW)).pattern());
        assertEquals(BingoPattern.COLUMN, BingoPatternEvaluator.firstWin(board, Set.of(0, 5, 10, 15, 20), Set.of(BingoPattern.COLUMN)).pattern());
        assertEquals(BingoPattern.DIAGONAL, BingoPatternEvaluator.firstWin(board, Set.of(0, 6, 12, 18, 24), Set.of(BingoPattern.DIAGONAL)).pattern());
        Set<Integer> all = new HashSet<>(); for (int cell = 0; cell < 25; cell++) all.add(cell);
        assertEquals(BingoPattern.FULL_HOUSE, BingoPatternEvaluator.firstWin(board, all, Set.of(BingoPattern.FULL_HOUSE)).pattern());
        assertNull(BingoPatternEvaluator.firstWin(board, Set.of(0, 1, 2, 3, 4), Set.of(BingoPattern.DIAGONAL)));
    }

    @Test void nearSimultaneousValidClaimsProduceExactlyOneWinnerRewardOwner() throws Exception {
        BingoRun run = activeRun(6, 2);
        drawAll(run);
        for (BingoParticipant participant : run.participants().values()) for (int cell = 0; cell < 5; cell++)
            assertEquals(BingoRun.MarkStatus.MARKED, run.mark(participant.playerId(), run.runId(), participant.board().numberAt(cell)).status());

        CountDownLatch ready = new CountDownLatch(2), fire = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger();
        List<Thread> threads = new ArrayList<>();
        for (BingoParticipant participant : run.participants().values()) {
            Thread thread = new Thread(() -> {
                ready.countDown();
                try { fire.await(); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); return; }
                if (run.claim(participant.playerId(), participant.nameSnapshot(), run.runId(), true).status() == BingoRun.ClaimStatus.WON) winners.incrementAndGet();
            });
            threads.add(thread); thread.start();
        }
        ready.await(); fire.countDown(); for (Thread thread : threads) thread.join();
        assertEquals(1, winners.get());
        assertEquals(BingoRun.Phase.WON, run.phase());
        assertNotNull(run.winner());
        assertTrue(run.beginCompletion()); assertFalse(run.beginCompletion());
    }

    @Test void fixedWidthRendererUsesUniformFontGreenMarksNoBracketsAndRunBoundClicks() {
        BingoRun run = activeRun(7, 1);
        BingoParticipant participant = run.participants().values().iterator().next();
        int target = participant.board().numberAt(0);
        drawAll(run);
        assertEquals(BingoRun.MarkStatus.MARKED, run.mark(participant.playerId(), run.runId(), target).status());

        ChatEventConfig.BingoRender settings = run.definition().bingo().render();
        List<String> plain = BingoRenderer.plainLines(participant, settings);
        assertEquals(6, plain.size());
        int width = plain.getFirst().length();
        assertTrue(plain.stream().allMatch(line -> line.length() == width));
        assertTrue(plain.stream().noneMatch(line -> line.contains("[") || line.contains("]")));
        assertTrue(plain.stream().allMatch(line -> line.startsWith(" ".repeat(settings.leftPadding()))));

        List<Component> rendered = BingoRenderer.render(run, participant, null);
        assertEquals(Key.key("minecraft:uniform"), rendered.get(1).style().font());
        Component markedRow = rendered.get(2);
        assertTrue(markedRow.children().stream().anyMatch(child -> NamedTextColor.GREEN.equals(child.style().color())));
        assertTrue(markedRow.children().stream().anyMatch(child -> child.style().clickEvent() != null
                && String.valueOf(child.style().clickEvent()).contains(run.runId().toString())));
    }

    private static BingoRun activeRun(int seed, int participants) {
        BingoRun run = run(0, 1, seed);
        for (int index = 0; index < participants; index++) assertEquals(BingoRun.JoinStatus.JOINED,
                run.join(UUID.randomUUID(), "Player" + index, index, true).status());
        assertEquals(BingoRun.ActivationStatus.ACTIVATED, run.activate(0, ignored -> true));
        return run;
    }

    private static BingoRun run(int lobbySeconds, int minimum, int seed) {
        return new BingoRun(UUID.randomUUID(), definition(), BingoRun.Source.ADMIN, 0, seconds(lobbySeconds), minimum,
                seconds(600), 0, 1, Set.of(BingoPattern.ROW, BingoPattern.COLUMN, BingoPattern.DIAGONAL, BingoPattern.FULL_HOUSE),
                List.of(60, 30, 15, 5), false, true, false, new java.util.Random(seed));
    }

    private static void drawAll(BingoRun run) {
        long now = 0;
        while (run.remainingCount() > 0) {
            Integer draw = run.draw(now++);
            assertNotNull(draw);
        }
        assertEquals(75, run.drawCount());
    }

    private static ChatEventConfig.Definition definition() {
        return new ChatEventConfig.Definition("bingo-classic", "Classic Bingo", ChatEventEngine.Type.BINGO, true, 4, 0,
                "epic", 600, true, 1, "", Set.of(), Set.of(), Set.of(ChatChannel.LOCAL, ChatChannel.GLOBAL),
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

    private static long seconds(long value) { return value * 1_000_000_000L; }
}
