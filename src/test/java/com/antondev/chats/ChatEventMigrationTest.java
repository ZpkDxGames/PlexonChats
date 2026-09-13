package com.antondev.chats;

import com.antondev.chats.config.ConfigUpgrader;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ChatEventMigrationTest extends PluginTestBase {
    @Test void v5AdministratorOwnedLegacyBingoIdIsPreservedAsTypeEvent() throws Exception {
        YamlConfiguration current = new YamlConfiguration();
        current.loadFromString("""
                config-version: 5
                chat-events:
                  events:
                    bingo:
                      enabled: true
                      name: "My Legacy Bingo Word"
                      type: TYPE
                      weight: 9
                      cooldown-seconds: 123
                      reward-profile: basic
                      values: ["customword"]
                      prompt: "<gray>Type <yellow>{value}</yellow>.</gray>"
                """);
        YamlConfiguration defaults = new YamlConfiguration();
        try (var reader = new InputStreamReader(plugin.getResource("config.yml"), StandardCharsets.UTF_8)) {
            defaults.load(reader);
        }

        assertTrue(ConfigUpgrader.upgrade(current, defaults));
        assertEquals(6, current.getInt("config-version"));
        assertEquals("TYPE", current.getString("chat-events.events.bingo.type"));
        assertEquals("My Legacy Bingo Word", current.getString("chat-events.events.bingo.name"));
        assertEquals(123, current.getInt("chat-events.events.bingo.cooldown-seconds"));
        assertEquals(java.util.List.of("customword"), current.getStringList("chat-events.events.bingo.values"));
        assertFalse(current.contains("chat-events.events.type-rush"), "administrator-owned event collection must not be repopulated");
        assertFalse(current.contains("chat-events.events.bingo-classic"), "real Bingo fresh default must not replace legacy IDs during migration");
        assertNotNull(current.getConfigurationSection("chat-events.presentation"));
        assertNotNull(current.getConfigurationSection("chat-events.bingo"));
    }
}
