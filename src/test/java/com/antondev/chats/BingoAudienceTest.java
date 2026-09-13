package com.antondev.chats;

import com.antondev.chats.event.ChatEventManager;
import com.antondev.chats.event.bingo.BingoSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BingoAudienceTest extends PluginTestBase {
    @Test void nonParticipantReceivesJoinInviteButNoBoardOrDrawTraffic() throws Exception {
        var first = player("First");
        var second = player("Second");
        var observer = player("Observer");
        config(yaml -> {
            yaml.set("chat-events.scheduler.enabled", false);
            yaml.set("chat-events.events.bingo-classic.cooldown-seconds", 0);
            yaml.set("chat-events.events.bingo-classic.bingo.join-seconds", 1);
            yaml.set("chat-events.events.bingo-classic.bingo.draw.first-delay-seconds", 1);
            yaml.set("chat-events.events.bingo-classic.bingo.draw.interval-seconds", 1);
            yaml.set("chat-events.events.bingo-classic.bingo.timeout-seconds", 10);
            yaml.set("chat-events.reward-profiles.epic.economy.enabled", false);
            yaml.set("chat-events.reward-profiles.epic.plexonkeys.enabled", false);
        });

        assertEquals(ChatEventManager.StartStatus.STARTED, plugin.getChatEvents().start("bingo-classic"));
        assertEquals("BINGO", plugin.getChatEvents().activeType());
        assertNotNull(first.nextComponentMessage(), "participant candidate should receive the one global join invitation");
        assertNotNull(second.nextComponentMessage(), "participant candidate should receive the one global join invitation");
        assertNotNull(observer.nextComponentMessage(), "non-participant may receive the one global join invitation");
        drain(first);
        drain(second);
        drain(observer);

        var runId = plugin.getChatEvents().activeRunId();
        assertNotNull(runId);
        assertEquals(BingoSession.JoinResult.JOINED, plugin.getChatEvents().bingoJoin(first, runId));
        assertEquals(BingoSession.JoinResult.JOINED, plugin.getChatEvents().bingoJoin(second, runId));
        drain(first);
        drain(second);
        assertNull(observer.nextComponentMessage(), "joining/board traffic must not reach an idle player");

        Thread.sleep(1_100L);
        tickSecond();
        assertEquals("ACTIVE", plugin.getChatEvents().bingoPhase());
        assertNotNull(first.nextComponentMessage(), "joined participant receives activation/card traffic");
        assertNotNull(second.nextComponentMessage(), "joined participant receives activation/card traffic");
        assertNull(observer.nextComponentMessage(), "non-participant must not receive activation/card traffic");
        drain(first);
        drain(second);

        Thread.sleep(1_100L);
        tickSecond();
        String firstDraw = next(first);
        String secondDraw = next(second);
        assertNotNull(firstDraw);
        assertNotNull(secondDraw);
        assertTrue(firstDraw.contains("Draw"), firstDraw);
        assertTrue(secondDraw.contains("Draw"), secondDraw);
        assertNull(observer.nextComponentMessage(), "non-participant must not receive repeated Bingo draw traffic");
    }

    private void tickSecond() {
        for (int tick = 0; tick < 20; tick++) server.getScheduler().performOneTick();
    }
}
