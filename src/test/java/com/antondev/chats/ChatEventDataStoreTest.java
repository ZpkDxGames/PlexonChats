package com.antondev.chats;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatEventDataStoreTest extends PluginTestBase {
    @Test void freshRuntimeHasSchemaOneDataFile() {
        assertEquals(1, plugin.getChatEventData().schemaVersion());
        assertTrue(Files.isRegularFile(plugin.getDataFolder().toPath().resolve("data.yml")));
        assertEquals(7, plugin.getChatEvents().configuredCount());
    }

    @Test void firstV4MigrationPreservesAdministratorGameplayValuesAndNeverCopiesSecrets() throws Exception {
        var data = plugin.getDataFolder().toPath().resolve("data.yml");
        Files.delete(data);
        var config = plugin.getDataFolder().toPath().resolve("config.yml").toFile();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(config);
        yaml.set("chat-events.scheduler.min-interval-seconds", 777);
        yaml.set("chat-events.events.type-rush.enabled", false);
        yaml.set("chat-events.events.type-rush.weight", 91);
        yaml.set("chat-events.events.type-rush.cooldown-seconds", 1234);
        yaml.set("chat-events.events.type-rush.duration-seconds", 44);
        yaml.set("chat-events.events.type-rush.reward-profile", "rare");
        yaml.set("chat-events.events.type-rush.values", List.of("custom-one", "custom-two"));
        yaml.set("chat-events.events.math-normal.min-operand", 17);
        yaml.set("chat-events.events.math-normal.max-operand", 83);
        yaml.set("chat-events.discord.webhook.url", "https://discord.example.invalid/api/webhooks/SECRET-DO-NOT-MIGRATE");
        yaml.save(config);

        assertTrue(plugin.reloadPlugin());
        YamlConfiguration migrated = YamlConfiguration.loadConfiguration(data.toFile());
        assertEquals(1, migrated.getInt("schema-version"));
        assertEquals(777, migrated.getLong("scheduler.min-interval-seconds"));
        assertFalse(migrated.getBoolean("minigames.type-rush.enabled"));
        assertEquals(91, migrated.getInt("minigames.type-rush.weight"));
        assertEquals(1234, migrated.getLong("minigames.type-rush.cooldown-seconds"));
        assertEquals(44, migrated.getInt("minigames.type-rush.duration-seconds"));
        assertEquals("rare", migrated.getString("minigames.type-rush.reward-profile"));
        assertEquals(List.of("custom-one", "custom-two"), migrated.getStringList("minigames.type-rush.values"));
        assertEquals(17, migrated.getInt("minigames.math-normal.min-operand"));
        assertEquals(83, migrated.getInt("minigames.math-normal.max-operand"));
        assertFalse(Files.readString(data).contains("SECRET-DO-NOT-MIGRATE"));
        assertTrue(Files.list(plugin.getDataFolder().toPath()).anyMatch(path -> path.getFileName().toString().startsWith("config-before-v4-")));
    }

    @Test void existingDataIsNotOverwrittenByLegacyConfigOnReload() throws Exception {
        var dataFile = plugin.getDataFolder().toPath().resolve("data.yml").toFile();
        YamlConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        data.set("minigames.type-rush.values", List.of("data-authority"));
        data.set("minigames.type-rush.weight", 77);
        data.save(dataFile);

        var configFile = plugin.getDataFolder().toPath().resolve("config.yml").toFile();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        config.set("chat-events.events.type-rush.values", List.of("legacy-must-not-win"));
        config.set("chat-events.events.type-rush.weight", 1);
        config.save(configFile);

        assertTrue(plugin.reloadPlugin());
        assertEquals(List.of("data-authority"), plugin.getChatEventData().root().getStringList("minigames.type-rush.values"));
        assertEquals(77, plugin.getChatEventData().root().getInt("minigames.type-rush.weight"));
        assertEquals(77, plugin.getChatEvents().definition("type-rush").weight());
    }
}
