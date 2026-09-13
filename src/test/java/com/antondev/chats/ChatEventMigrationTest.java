package com.antondev.chats;

import com.antondev.chats.config.ConfigUpgrader;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ChatEventMigrationTest extends PluginTestBase {
    @Test void legacyTypeEventNamedBingoRemainsTypeAndAdministratorCollectionIsNotRepopulated() throws Exception {
        YamlConfiguration current = new YamlConfiguration();
        current.loadFromString("""
                config-version: 6
                chat-events:
                  reward-profiles:
                    basic:
                      economy: { enabled: false, amount: 0 }
                      plexonkeys: { enabled: false, tier: BASIC, amount: 0 }
                      console-commands: []
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
        YamlConfiguration defaults = defaults();

        assertTrue(ConfigUpgrader.upgrade(current, defaults));
        assertEquals(8, current.getInt("config-version"));
        assertEquals("TYPE", current.getString("chat-events.events.bingo.type"));
        assertEquals("My Legacy Bingo Word", current.getString("chat-events.events.bingo.name"));
        assertEquals(9, current.getInt("chat-events.events.bingo.weight"));
        assertEquals(123, current.getInt("chat-events.events.bingo.cooldown-seconds"));
        assertEquals(java.util.List.of("customword"), current.getStringList("chat-events.events.bingo.values"));
        assertFalse(current.contains("chat-events.events.type-rush"));
        assertFalse(current.contains("chat-events.events.bingo-classic"));
    }

    @Test void v6BingoDefinitionIdentityAndOwnedValuesSurviveWhileParticipantMechanicsAreRemoved() throws Exception {
        YamlConfiguration current = new YamlConfiguration();
        current.loadFromString("""
                config-version: 6
                chat-events:
                  bingo:
                    enabled: true
                    join:
                      duration-seconds: 17
                      min-participants: 4
                    board:
                      variant: BINGO_75
                      free-center: true
                    draw:
                      first-delay-seconds: 7
                      interval-seconds: 9
                      redraw-board-each-draw: false
                    timeout-seconds: 333
                    win-patterns: [ROW, COLUMN, DIAGONAL, FULL_HOUSE]
                  reward-profiles:
                    custom:
                      economy: { enabled: false, amount: 0 }
                      plexonkeys: { enabled: false, tier: BASIC, amount: 0 }
                      console-commands: []
                  events:
                    my-bingo:
                      enabled: true
                      name: "Saturday Bingo"
                      type: BINGO
                      weight: 17
                      cooldown-seconds: 9876
                      reward-profile: custom
                      min-online: 6
                      bingo:
                        join-seconds: 12
                        min-participants: 3
                        board:
                          free-center: true
                        timeout-seconds: 444
                    custom-type:
                      enabled: true
                      name: "Keep Me"
                      type: TYPE
                      weight: 2
                      cooldown-seconds: 99
                      reward-profile: custom
                      values: ["mine"]
                      prompt: "Type {value}"
                """);

        assertTrue(ConfigUpgrader.upgrade(current, defaults()));
        assertEquals(8, current.getInt("config-version"));
        assertEquals("Saturday Bingo", current.getString("chat-events.events.my-bingo.name"));
        assertEquals("BINGO", current.getString("chat-events.events.my-bingo.type"));
        assertEquals(17, current.getInt("chat-events.events.my-bingo.weight"));
        assertEquals(9876, current.getInt("chat-events.events.my-bingo.cooldown-seconds"));
        assertEquals("custom", current.getString("chat-events.events.my-bingo.reward-profile"));
        assertEquals(6, current.getInt("chat-events.events.my-bingo.min-online"));
        assertEquals(444, current.getInt("chat-events.events.my-bingo.duration-seconds"));
        assertFalse(current.contains("chat-events.events.my-bingo.bingo"));
        assertEquals("TYPE", current.getString("chat-events.events.custom-type.type"));
        assertFalse(current.contains("chat-events.events.bingo-classic"));

        assertFalse(current.contains("chat-events.bingo.join"));
        assertFalse(current.contains("chat-events.bingo.board"));
        assertFalse(current.contains("chat-events.bingo.timeout-seconds"));
        assertFalse(current.contains("chat-events.bingo.win-patterns"));
        assertEquals(7, current.getInt("chat-events.bingo.draw.first-delay-seconds"), "administrator draw timing survives migration");
        assertEquals(9, current.getInt("chat-events.bingo.draw.interval-seconds"));
        assertTrue(current.getBoolean("chat-events.bingo.winning.horizontal"));
        assertTrue(current.getBoolean("chat-events.bingo.winning.vertical"));
        assertTrue(current.getBoolean("chat-events.bingo.winning.diagonal"));
        assertTrue(current.getBoolean("chat-events.bingo.winning.full-house"));
    }

    @Test void v7BingoWebhookMigratesToGeneralDiscordSchemaWithoutLeakingOrDeletingSecret() throws Exception {
        YamlConfiguration current = new YamlConfiguration();
        current.loadFromString("""
                config-version: 7
                chat-events:
                  bingo:
                    discord:
                      enabled: true
                      webhook-url: "https://discord.com/api/webhooks/123456/very-secret-token"
                      username: "Legacy Bingo"
                      send-start: true
                      send-draws: false
                      send-win: true
                  reward-profiles:
                    custom:
                      economy: { enabled: false, amount: 0 }
                      plexonkeys: { enabled: false, tier: BASIC, amount: 0 }
                      console-commands: []
                  events:
                    custom:
                      enabled: true
                      name: "Custom"
                      type: TYPE
                      weight: 3
                      cooldown-seconds: 99
                      reward-profile: custom
                      values: ["keep"]
                      prompt: "Type {value}"
                """);

        assertTrue(ConfigUpgrader.upgrade(current, defaults()));
        assertEquals(8, current.getInt("config-version"));
        assertTrue(current.getBoolean("chat-events.discord.enabled"));
        assertEquals("WEBHOOK", current.getString("chat-events.discord.transport"));
        assertEquals("DISPLAY_ONLY", current.getString("chat-events.discord.participation-mode"));
        assertTrue(current.getBoolean("chat-events.discord.webhook.enabled"));
        assertEquals("https://discord.com/api/webhooks/123456/very-secret-token", current.getString("chat-events.discord.webhook.url"));
        assertEquals("Legacy Bingo", current.getString("chat-events.discord.webhook.username"));
        assertTrue(current.getBoolean("chat-events.discord.events.bingo.enabled"));
        assertFalse(current.getBoolean("chat-events.discord.events.bingo.update-on-draw"));
        assertTrue(current.getBoolean("chat-events.discord.events.bingo.announce-winner"));
        assertFalse(current.contains("chat-events.bingo.discord"), "legacy duplicate secret location must be removed");
        assertEquals("TYPE", current.getString("chat-events.events.custom.type"));
        assertEquals(3, current.getInt("chat-events.events.custom.weight"));
        assertFalse(current.contains("chat-events.events.type-rush"), "administrator-owned event collection must not be repopulated");
    }

    private YamlConfiguration defaults() throws Exception {
        YamlConfiguration defaults = new YamlConfiguration();
        try (var reader = new InputStreamReader(plugin.getResource("config.yml"), StandardCharsets.UTF_8)) {
            defaults.load(reader);
        }
        return defaults;
    }
}
