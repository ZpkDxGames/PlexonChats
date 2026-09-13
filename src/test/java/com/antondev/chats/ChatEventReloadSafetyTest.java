package com.antondev.chats;

import com.antondev.chats.event.ChatEventManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatEventReloadSafetyTest extends PluginTestBase {
    @Test void rejectedConfigReloadDoesNotCancelActiveEvent() throws Exception {
        player("Participant");
        config(yaml -> {
            yaml.set("chat-events.scheduler.enabled", false);
            yaml.set("chat-events.events.type-rush.values", List.of("keep-running"));
            yaml.set("chat-events.events.type-rush.cooldown-seconds", 0);
            yaml.set("chat-events.reward-profiles.basic.economy.enabled", false);
        });
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
}
