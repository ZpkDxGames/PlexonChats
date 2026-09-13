package com.antondev.chats;

import com.antondev.chats.storage.ChatEventDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChatEventDatabaseTest {
    @TempDir Path temp;

    @Test void winnerAggregatesPersistAndDuplicateRunCannotDoubleCount() throws Exception {
        Path file = temp.resolve("chat-events.db");
        ChatEventDatabase database = new ChatEventDatabase(file);
        database.initialize();
        UUID player = UUID.randomUUID();
        UUID firstRun = UUID.randomUUID();
        UUID secondRun = UUID.randomUUID();

        assertTrue(database.recordWin(new ChatEventDatabase.WinRecord(firstRun, player, "Tonim", "trivia", "TRIVIA", "rare", 1000, 4210)));
        assertFalse(database.recordWin(new ChatEventDatabase.WinRecord(firstRun, player, "Tonim", "trivia", "TRIVIA", "rare", 1001, 1)));
        assertTrue(database.recordWin(new ChatEventDatabase.WinRecord(secondRun, player, "Tonim", "bingo-classic", "BINGO", "epic", 2000, 8000)));

        var stored = database.loadPlayers().getFirst();
        assertEquals(player, stored.playerId());
        assertEquals(2, stored.totalWins());
        assertEquals(1, stored.typeWins().get("TRIVIA"));
        assertEquals(1, stored.typeWins().get("BINGO"));
        assertEquals(1, stored.definitionWins().get("TRIVIA"));
        assertEquals(1, stored.definitionWins().get("BINGO-CLASSIC"));
        assertEquals(2, database.historyCount());
        assertEquals(secondRun, database.loadRecent(10).getFirst().runId());

        ChatEventDatabase reopened = new ChatEventDatabase(file);
        reopened.initialize();
        assertEquals(2, reopened.historyCount());
        assertEquals(2, reopened.loadPlayers().getFirst().totalWins());
    }

    @Test void playerRenameUpdatesLastKnownNameWithoutChangingUuidIdentity() throws Exception {
        ChatEventDatabase database = new ChatEventDatabase(temp.resolve("rename.db"));
        database.initialize();
        UUID player = UUID.randomUUID();
        assertTrue(database.recordWin(new ChatEventDatabase.WinRecord(UUID.randomUUID(), player, "OldName", "type-rush", "TYPE", "basic", 10, 20)));
        assertTrue(database.recordWin(new ChatEventDatabase.WinRecord(UUID.randomUUID(), player, "NewName", "math-normal", "MATH", "rare", 30, 40)));
        var stored = database.loadPlayers().getFirst();
        assertEquals(player, stored.playerId());
        assertEquals("NewName", stored.name());
        assertEquals(2, stored.totalWins());
        assertEquals(10L, stored.firstWinAt());
        assertEquals(30L, stored.lastWinAt());
    }
}
