package com.antondev.chats;

import com.antondev.chats.event.ChatEventManager;
import com.antondev.chats.event.bingo.BingoRun;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BingoAudienceTest extends PluginTestBase {
    @Test void adminStartCreatesLobbyAndNeverAutoEnrollsObservers() throws Exception {
        var first = player("First");
        var second = player("Second");
        var observer = player("Observer");
        configureBingo();

        assertEquals(ChatEventManager.StartStatus.STARTED, plugin.getChatEvents().startBingo());
        assertEquals("BINGO", plugin.getChatEvents().activeType());
        assertEquals("LOBBY", plugin.getChatEvents().bingoPhase());
        assertEquals("ADMIN", plugin.getChatEvents().bingoSource());
        assertEquals(0, plugin.getChatEvents().bingoParticipantCount());
        assertFalse(plugin.getChatEvents().showBingoBoard(observer));

        assertEquals(BingoRun.JoinStatus.JOINED, plugin.getChatEvents().bingoJoin(first).status());
        assertEquals(BingoRun.JoinStatus.ALREADY_JOINED, plugin.getChatEvents().bingoJoin(first).status());
        assertEquals(BingoRun.JoinStatus.JOINED, plugin.getChatEvents().bingoJoin(second).status());
        assertEquals(2, plugin.getChatEvents().bingoParticipantCount());
        assertTrue(plugin.getChatEvents().showBingoBoard(first));
        assertTrue(plugin.getChatEvents().showBingoBoard(second));
        assertFalse(plugin.getChatEvents().showBingoBoard(observer), "non-participant must never see another player's card");
        assertEquals(6, plugin.getChatEvents().bingoBoardPreview().size());
    }

    @Test void adminFastStartWorksWithExactlyOneExplicitParticipant() throws Exception {
        var only = player("OnlyPlayer");
        configureBingo();
        assertEquals(ChatEventManager.StartStatus.STARTED, plugin.getChatEvents().startBingo());
        assertEquals(BingoRun.JoinStatus.JOINED, plugin.getChatEvents().bingoJoin(only).status());
        assertEquals(ChatEventManager.StartStatus.STARTED, plugin.getChatEvents().startBingoNow());
        assertEquals("ACTIVE", plugin.getChatEvents().bingoPhase());
        assertEquals(1, plugin.getChatEvents().bingoParticipantCount());
        assertEquals(1, plugin.getChatEvents().bingoOnlineParticipantCount());
    }

    @Test void fastStartWithZeroParticipantsDoesNotAutoEnrollAnyone() throws Exception {
        player("Observer");
        configureBingo();
        assertEquals(ChatEventManager.StartStatus.STARTED, plugin.getChatEvents().startBingo());
        assertEquals(ChatEventManager.StartStatus.NOT_ENOUGH_PLAYERS, plugin.getChatEvents().startBingoNow());
        assertEquals("LOBBY", plugin.getChatEvents().bingoPhase());
        assertEquals(0, plugin.getChatEvents().bingoParticipantCount());
    }

    @Test void leavingLobbyRemovesCardWhileLeavingActiveForfeits() throws Exception {
        var player = player("Leaver");
        configureBingo();
        assertEquals(ChatEventManager.StartStatus.STARTED, plugin.getChatEvents().startBingo());
        assertEquals(BingoRun.JoinStatus.JOINED, plugin.getChatEvents().bingoJoin(player).status());
        assertEquals(BingoRun.LeaveStatus.LEFT, plugin.getChatEvents().bingoLeave(player));
        assertEquals(0, plugin.getChatEvents().bingoParticipantCount());
        assertEquals(BingoRun.JoinStatus.JOINED, plugin.getChatEvents().bingoJoin(player).status());
        assertEquals(ChatEventManager.StartStatus.STARTED, plugin.getChatEvents().startBingoNow());
        assertEquals(BingoRun.LeaveStatus.FORFEITED, plugin.getChatEvents().bingoLeave(player));
        assertFalse(plugin.getChatEvents().showBingoBoard(player));
    }

    private void configureBingo() throws Exception {
        data(yaml -> {
            yaml.set("scheduler.enabled", false);
            yaml.set("minigames.bingo-classic.cooldown-seconds", 0);
            yaml.set("minigames.bingo-classic.min-online", 1);
            yaml.set("minigames.bingo-classic.duration-seconds", 30);
            yaml.set("minigames.bingo-classic.lobby.admin-minimum-participants", 1);
            yaml.set("minigames.bingo-classic.draws.first-call-delay-seconds", 0);
            yaml.set("minigames.bingo-classic.draws.interval-seconds", 1);
        });
        config(yaml -> {
            yaml.set("chat-events.reward-profiles.epic.economy.enabled", false);
            yaml.set("chat-events.reward-profiles.epic.plexonkeys.enabled", false);
        });
    }
}
