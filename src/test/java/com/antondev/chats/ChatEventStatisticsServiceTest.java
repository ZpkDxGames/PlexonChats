package com.antondev.chats;

import com.antondev.chats.event.ChatEventEngine;
import com.antondev.chats.event.ChatEventStatisticsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class ChatEventStatisticsServiceTest {
    @TempDir Path temp;

    @Test void duplicateRunIsIdempotentInImmediateCacheBeforePersistence() throws Exception {
        try (var service = new ChatEventStatisticsService(temp.resolve("chat-events.db"), Logger.getLogger("test"))) {
            long deadline = System.nanoTime() + 2_000_000_000L;
            while (service.state() == ChatEventStatisticsService.State.STARTING && System.nanoTime() < deadline) Thread.sleep(10L);
            assertEquals(ChatEventStatisticsService.State.READY, service.state());

            UUID run = UUID.randomUUID();
            UUID player = UUID.randomUUID();
            var first = service.recordWin(run, player, "Tonim", "type-rush", ChatEventEngine.Type.TYPE, "basic", 100);
            var duplicate = service.recordWin(run, player, "Tonim", "type-rush", ChatEventEngine.Type.TYPE, "basic", 100);

            assertEquals(1, first.totalWins());
            assertEquals(1, duplicate.totalWins());
            assertEquals(1, service.getTotalWins(player));
            assertEquals(1, service.getWinsByType(player, ChatEventEngine.Type.TYPE));
            assertEquals(1, service.getWinsByDefinition(player, "type-rush"));
            assertEquals(1, service.totalRecordedWins());
        }
    }
}
