package com.antondev.chats;

import com.antondev.chats.config.ConfigManager;
import com.antondev.chats.config.ConfigUpgrader;
import com.antondev.chats.diagnostics.ChatDiagnostics;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

class Phase2ContractTest extends PluginTestBase {
    @Test void releaseMetadataAndConfigGenerationMatchCandidate() {
        assertEquals("3.4.0", plugin.getPluginMeta().getVersion());
        assertEquals(6, ConfigUpgrader.VERSION);
        assertTrue(plugin.getConfigManager().revision() >= 1);
        assertTrue(plugin.getDiagnostics().lastReload().startsWith("SUCCESS"));
    }

    @Test void diagnosticsCountSuccessfulPublicDelivery() {
        var sender = player("Sender");
        var recipient = player("Recipient");
        long messages = plugin.getDiagnostics().publicMessagesDeliveredCount();
        long deliveries = plugin.getDiagnostics().recipientDeliveriesCount();
        plugin.getChatManager().sendPublic(sender, ChatChannel.GLOBAL, "metrics");
        assertEquals(messages + 1, plugin.getDiagnostics().publicMessagesDeliveredCount());
        assertEquals(deliveries + 2, plugin.getDiagnostics().recipientDeliveriesCount());
        assertTrue(next(recipient).contains("metrics"));
        assertEquals("PAPER_CHAT_EVENT -> PLEXONCHATS -> PLEXON_CHAT_EVENT -> SINGLE_DELIVERY", ChatDiagnostics.OWNERSHIP);
    }

    @Test void invalidSchedulerConfigIsRejectedWithoutReplacingRuntime() throws Exception {
        long revision = plugin.getConfigManager().revision();
        var file = plugin.getDataFolder().toPath().resolve("config.yml");
        var yaml = YamlConfiguration.loadConfiguration(file.toFile());
        yaml.set("auto-messages.groups.tips.interval-seconds", -1);
        yaml.save(file.toFile());
        assertFalse(plugin.reloadPlugin());
        assertEquals(revision, plugin.getConfigManager().revision());
        assertTrue(plugin.getDiagnostics().lastReload().startsWith("FAILED"));
    }

    @Test void invalidGuiSlotAndUnknownDeliveryAreStrictlyRejected() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString("config-version: 6\nchannels:\n  local:\n    format: '{message}'\n  global:\n    format: '{message}'\ngui:\n  rows: 1\n  items:\n    bad:\n      slot: 12\n      material: PAPER\nauto-messages:\n  groups:\n    test:\n      interval-seconds: 30\n      initial-delay-seconds: 1\n      messages:\n        - delivery: UNKNOWN\n          lines: ['hello']\n");
        assertThrows(IllegalArgumentException.class, () -> ConfigManager.validate(yaml));
        yaml.set("gui.items.bad.slot", 1);
        assertThrows(IllegalArgumentException.class, () -> ConfigManager.validate(yaml));
    }

    @Test void repeatedReloadKeepsSingleAutoMessageTask() throws Exception {
        assertTrue(plugin.getAutoMessages().taskActive());
        int before = server.getScheduler().getPendingTasks().size();
        assertTrue(plugin.reloadPlugin());
        assertTrue(plugin.reloadPlugin());
        assertTrue(plugin.reloadPlugin());
        assertTrue(plugin.getAutoMessages().taskActive());
        assertTrue(plugin.getChatEvents().taskActive());
        assertEquals(before, server.getScheduler().getPendingTasks().size());
    }

    @Test void failedYamlReloadLeavesKnownGoodFormat() throws Exception {
        String format = plugin.getConfigManager().getGlobalFormat();
        long revision = plugin.getConfigManager().revision();
        Files.writeString(plugin.getDataFolder().toPath().resolve("config.yml"), "channels: [invalid\n");
        assertFalse(plugin.reloadPlugin());
        assertEquals(format, plugin.getConfigManager().getGlobalFormat());
        assertEquals(revision, plugin.getConfigManager().revision());
    }
}
