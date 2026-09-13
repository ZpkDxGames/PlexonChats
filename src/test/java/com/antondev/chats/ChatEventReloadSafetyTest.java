package com.antondev.chats;

import com.antondev.chats.event.ChatEventManager;
import com.antondev.chats.event.bingo.BingoRun;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatEventReloadSafetyTest extends PluginTestBase {
    @Test void rejectedConfigReloadDoesNotCancelActiveEvent() throws Exception {
        player("Participant");
        data(yaml -> {
            yaml.set("scheduler.enabled", false);
            yaml.set("minigames.type-rush.values", List.of("keep-running"));
            yaml.set("minigames.type-rush.cooldown-seconds", 0);
        });
        config(yaml -> yaml.set("chat-events.reward-profiles.basic.economy.enabled", false));
        assertEquals(ChatEventManager.StartStatus.STARTED, plugin.getChatEvents().start("type-rush"));
        var runId = plugin.getChatEvents().activeRunId();
        assertNotNull(runId);

        var file = plugin.getDataFolder().toPath().resolve("config.yml").toFile();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.set("chat-events.presentation.blank-lines-before", 9);
        yaml.save(file);

        assertFalse(plugin.reloadPlugin());
        assertTrue(plugin.getChatEvents().hasActiveEvent());
        assertEquals(runId, plugin.getChatEvents().activeRunId(), "rejected candidate must not replace/cancel current run");
    }

    @Test void successfulReloadCancelsPendingBingoLobbyWithoutRewardOrWinner() throws Exception {
        var player = player("LobbyPlayer");
        configureBingo();
        long winsBefore = plugin.getChatEvents().statistics().get(player.getUniqueId(), player.getName()).totalWins();
        assertEquals(ChatEventManager.StartStatus.STARTED, plugin.getChatEvents().startBingo());
        assertEquals(BingoRun.JoinStatus.JOINED, plugin.getChatEvents().bingoJoin(player).status());
        assertEquals("LOBBY", plugin.getChatEvents().bingoPhase());
        assertTrue(plugin.reloadPlugin());
        assertFalse(plugin.getChatEvents().hasActiveEvent());
        assertEquals("IDLE", plugin.getChatEvents().bingoPhase());
        assertEquals("-", plugin.getChatEvents().lastWinner());
        assertEquals(winsBefore, plugin.getChatEvents().statistics().get(player.getUniqueId(), player.getName()).totalWins());
    }

    @Test void successfulReloadCancelsActiveBingoWithoutRewardOrWinner() throws Exception {
        var player = player("ActivePlayer");
        configureBingo();
        long winsBefore = plugin.getChatEvents().statistics().get(player.getUniqueId(), player.getName()).totalWins();
        assertEquals(ChatEventManager.StartStatus.STARTED, plugin.getChatEvents().startBingo());
        assertEquals(BingoRun.JoinStatus.JOINED, plugin.getChatEvents().bingoJoin(player).status());
        assertEquals(ChatEventManager.StartStatus.STARTED, plugin.getChatEvents().startBingoNow());
        assertEquals("ACTIVE", plugin.getChatEvents().bingoPhase());
        assertTrue(plugin.reloadPlugin());
        assertFalse(plugin.getChatEvents().hasActiveEvent());
        assertEquals("IDLE", plugin.getChatEvents().bingoPhase());
        assertEquals("-", plugin.getChatEvents().lastWinner());
        assertEquals(winsBefore, plugin.getChatEvents().statistics().get(player.getUniqueId(), player.getName()).totalWins());
    }

    private void configureBingo() throws Exception {
        data(yaml -> {
            yaml.set("scheduler.enabled", false);
            yaml.set("minigames.bingo-classic.cooldown-seconds", 0);
            yaml.set("minigames.bingo-classic.min-online", 1);
            yaml.set("minigames.bingo-classic.lobby.admin-minimum-participants", 1);
        });
        config(yaml -> {
            yaml.set("chat-events.reward-profiles.epic.economy.enabled", false);
            yaml.set("chat-events.reward-profiles.epic.plexonkeys.enabled", false);
        });
    }
}
