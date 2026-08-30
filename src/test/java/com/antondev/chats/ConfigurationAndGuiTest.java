package com.antondev.chats;

import com.antondev.chats.gui.*;
import com.antondev.chats.player.PreferenceStore;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationAndGuiTest extends PluginTestBase {
    @Test void malformedReloadKeepsLiveConfigurationAndRevision() throws Exception {
        long revision = plugin.getConfigManager().revision();
        String format = plugin.getConfigManager().getGlobalFormat();
        Files.writeString(plugin.getDataFolder().toPath().resolve("config.yml"), "channels: [broken YAML\n");
        assertFalse(plugin.reloadPlugin());
        assertEquals(revision, plugin.getConfigManager().revision());
        assertEquals(format, plugin.getConfigManager().getGlobalFormat());
        assertEquals(2, plugin.getAutoMessages().groupNames().size());
    }
    @Test void missingBodyIsRejectedWithoutReplacingRuntime() throws Exception {
        var file = plugin.getDataFolder().toPath().resolve("config.yml");
        var yaml = YamlConfiguration.loadConfiguration(file.toFile());
        yaml.set("channels.global.format", "<red>Missing body");
        yaml.save(file.toFile());
        assertFalse(plugin.reloadPlugin());
        assertTrue(plugin.getConfigManager().getGlobalFormat().contains("{message}"));
    }
    @Test void legacyConfigurationIsBackedUpAndCustomValuesSurvive() throws Exception {
        var file = plugin.getDataFolder().toPath().resolve("config.yml");
        String legacy = "default-channel: GLOBAL\nchannels:\n  global:\n    format: '<gold>LEGACY {player}: {message}'\n    shortcut-prefix: '!!'\ngui:\n  rows: 2\n";
        Files.writeString(file, legacy);
        assertTrue(plugin.reloadPlugin());
        assertEquals("!!", plugin.getConfigManager().getGlobalShortcutPrefix());
        assertEquals("<gold>LEGACY {player}: {message}", plugin.getConfigManager().getGlobalFormat());
        try (var files = Files.list(file.getParent())) {
            var backups = files.filter(path -> path.getFileName().toString().startsWith("config-before-v2-")).toList();
            assertEquals(1, backups.size());
            assertEquals(legacy, Files.readString(backups.getFirst()));
        }
    }
    @Test void rowsAndCustomButtonPositionsAreHonored() throws Exception {
        var player = player("Viewer");
        config(yaml -> {
            yaml.set("gui.rows", 1);
            yaml.set("gui.items", null);
            yaml.createSection("gui.items");
            yaml.set("gui.items.custom.slot", 4);
            yaml.set("gui.items.custom.material", "DIAMOND");
            yaml.set("gui.items.custom.action", "MESSAGE");
            yaml.set("gui.items.custom.name", "<aqua>Hello {player_name}");
            yaml.set("gui.items.custom.value", "<green>Custom click worked");
        });
        plugin.getChatGUI().open(player);
        assertEquals(9, player.getOpenInventory().getTopInventory().getSize());
        assertEquals(Material.DIAMOND, player.getOpenInventory().getTopInventory().getItem(4).getType());
        click(player, 4, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getScheduler().performOneTick();
        assertEquals("Custom click worked", next(player));
    }
    @Test void invalidGuiEntriesAreSkippedWithWarnings() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString("rows: 1\nitems:\n  first:\n    slot: 4\n    material: PAPER\n  duplicate:\n    slot: 4\n    material: STONE\n  outside:\n    slot: 10\n  invalid:\n    slot: 5\n    material: NOT_A_REAL_ITEM\n");
        List<String> warnings = new ArrayList<>();
        GuiLayout layout = GuiLayout.read(yaml, "Test", warnings::add);
        assertEquals(1, layout.buttons().size());
        assertEquals(3, warnings.size());
    }
    @Test void defaultAdminLayoutHasAllSixButtons() {
        var layout = GuiLayout.read(plugin.getConfigManager().section("gui.admin"), "Admin", message -> fail(message));
        assertEquals(6, layout.buttons().size());
    }
    @Test void administrationPageAndActionsRequirePermissions() {
        var player = player("Visitor");
        plugin.getChatGUI().openPage(player, ChatGUIHolder.Page.ADMIN);
        assertTrue(player.getOpenInventory().getTopInventory() == null
                || !(player.getOpenInventory().getTopInventory().getHolder() instanceof ChatGUIHolder));
        assertTrue(next(player).contains("permission"));
        plugin.getChatGUI().openMain(player);
        var holder = (ChatGUIHolder) player.getOpenInventory().getTopInventory().getHolder();
        assertNull(holder.buttonAt(22));
        long revision = plugin.getConfigManager().revision();
        var button = new GuiButton("bad-reload", 1, GuiAction.RELOAD, Material.PAPER, Material.PAPER, Material.PAPER,
                "Reload", List.of(), "", List.of(), "", "", false, false);
        plugin.getChatGUI().click(player, holder, button);
        assertEquals(revision, plugin.getConfigManager().revision());
        assertTrue(next(player).contains("permission"));
    }
    @Test void ordinaryClickChangesPreferenceOnNextTick() {
        var player = player("Viewer");
        plugin.getChatGUI().open(player);
        var event = click(player, 14, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertTrue(event.isCancelled());
        assertTrue(plugin.getPreferences().get(player.getUniqueId()).mentions());
        server.getScheduler().performOneTick();
        assertFalse(plugin.getPreferences().get(player.getUniqueId()).mentions());
    }
    @Test void shiftAndHotbarClicksCannotTriggerButtonsOrTakeItems() {
        var player = player("Viewer");
        plugin.getChatGUI().open(player);
        assertTrue(click(player, 14, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY).isCancelled());
        assertTrue(click(player, 14, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP).isCancelled());
        server.getScheduler().performOneTick();
        assertTrue(plugin.getPreferences().get(player.getUniqueId()).mentions());
    }
    @Test void bottomInventoryAndDragTransfersAreCancelled() {
        var player = player("Viewer");
        plugin.getChatGUI().open(player);
        assertTrue(click(player, 30, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY).isCancelled());
        var event = new InventoryDragEvent(player.getOpenInventory(), new ItemStack(Material.STONE),
                new ItemStack(Material.STONE, 2), false, Map.of(10, new ItemStack(Material.STONE)));
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
    }
    @Test void playerPreferencesSurviveAStoreReload() {
        var player = player("Viewer");
        var value = plugin.getPreferences().get(player.getUniqueId()).withChannel(ChatChannel.GLOBAL).toggleMentions().toggleTips();
        plugin.getPreferences().set(player.getUniqueId(), value);
        plugin.getPreferences().close();
        try (var loaded = new PreferenceStore(plugin)) {
            assertEquals(value, loaded.get(player.getUniqueId()));
        }
    }
    @Test void invalidPreferenceFileIsNotOverwritten() throws Exception {
        var file = plugin.getDataFolder().toPath().resolve("players.yml");
        String corrupt = "broken: [yaml\n";
        Files.writeString(file, corrupt);
        try (var store = new PreferenceStore(plugin)) {
            store.set(java.util.UUID.randomUUID(), com.antondev.chats.player.PlayerPreferences.defaults().toggleTips());
            store.flush();
        }
        assertEquals(corrupt, Files.readString(file));
    }
    @Test void previewCacheIsBoundedAndTokensAreNotPredictableShortIds() throws Exception {
        config(yaml -> yaml.set("item-display.preview.max-entries", 2));
        var cache = plugin.getItemPreviewManager();
        String first = cache.store(new ItemStack(Material.DIAMOND), "Viewer");
        cache.store(new ItemStack(Material.EMERALD), "Viewer");
        String last = cache.store(new ItemStack(Material.IRON_INGOT), "Viewer");
        assertEquals(2, cache.size());
        assertNull(cache.get(first));
        assertNotNull(cache.get(last));
        assertEquals(36, last.length());
    }

    private InventoryClickEvent click(org.mockbukkit.mockbukkit.entity.PlayerMock player, int slot, ClickType click, InventoryAction action) {
        InventoryClickEvent event = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER, slot, click, action);
        server.getPluginManager().callEvent(event);
        return event;
    }
}
