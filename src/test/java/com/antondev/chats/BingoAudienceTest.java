package com.antondev.chats;

import com.antondev.chats.event.ChatEventManager;
import com.antondev.chats.event.bingo.BingoRun;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BingoAudienceTest extends PluginTestBase {
    @Test void sharedBoardStartsImmediatelyForAllEligiblePlayersWithoutJoinPhase() throws Exception {
        var first = player("First");
        var second = player("Second");
        var observer = player("Observer");
        configureBingo();

        assertEquals(ChatEventManager.StartStatus.STARTED, plugin.getChatEvents().start("bingo-classic"));
        assertEquals("BINGO", plugin.getChatEvents().activeType());
        assertEquals("ACTIVE", plugin.getChatEvents().bingoPhase());
        assertEquals(3, plugin.getChatEvents().bingoParticipantCount(), "diagnostic audience is eligibility-based, never opt-in participants");
        assertEquals(6, plugin.getChatEvents().bingoBoardPreview().size());
        assertEquals("# |  B |  I |  N |  G |  O", plugin.getChatEvents().bingoBoardPreview().getFirst());

        assertNotNull(first.nextComponentMessage(), "eligible player receives start/board traffic");
        assertNotNull(second.nextComponentMessage(), "eligible player receives start/board traffic");
        assertNotNull(observer.nextComponentMessage(), "eligible observer sees the same shared board without joining");
        drain(first); drain(second); drain(observer);

        assertTrue(plugin.getChatEvents().showBingoBoard(observer));
        assertNotNull(observer.nextComponentMessage(), "/bingo surface is available to every eligible player");
        drain(observer);

        tickSecond();
        assertEquals(1, plugin.getChatEvents().bingoDrawCount());
        String firstDraw = next(first);
        String secondDraw = next(second);
        String observerDraw = next(observer);
        assertNotNull(firstDraw);
        assertNotNull(secondDraw);
        assertNotNull(observerDraw, "draw traffic is shared, not participant-only");
    }

    @Test void invalidCommandAndPublicChatClaimsDoNotEndTheRun() {
        var player = player("Claimant");
        configureBingo();
        assertEquals(ChatEventManager.StartStatus.STARTED, plugin.getChatEvents().start("bingo-classic"));
        drain(player);

        assertEquals(BingoRun.ClaimStatus.NO_PATTERN, plugin.getChatEvents().bingoClaim(player).status());
        assertEquals("ACTIVE", plugin.getChatEvents().bingoPhase());
        assertFalse(plugin.getChatEvents().acceptAnswer(player, ChatChannel.GLOBAL, "bingo"));
        assertEquals("ACTIVE", plugin.getChatEvents().bingoPhase());
        assertFalse(plugin.getChatEvents().acceptAnswer(player, ChatChannel.GLOBAL, "not bingo"));
        assertEquals("ACTIVE", plugin.getChatEvents().bingoPhase());
    }

    private void configureBingo() {
        config(yaml -> {
            yaml.set("chat-events.scheduler.enabled", false);
            yaml.set("chat-events.events.bingo-classic.cooldown-seconds", 0);
            yaml.set("chat-events.events.bingo-classic.min-online", 1);
            yaml.set("chat-events.events.bingo-classic.duration-seconds", 30);
            yaml.set("chat-events.bingo.draw.first-delay-seconds", 0);
            yaml.set("chat-events.bingo.draw.interval-seconds", 1);
            yaml.set("chat-events.reward-profiles.epic.economy.enabled", false);
            yaml.set("chat-events.reward-profiles.epic.plexonkeys.enabled", false);
        });
    }

    private void tickSecond() {
        for (int tick = 0; tick < 20; tick++) server.getScheduler().performOneTick();
    }
}
