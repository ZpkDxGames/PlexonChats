package com.antondev.chats;

import com.antondev.chats.automessage.AutoMessageManager;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class AutoMessageTest extends PluginTestBase {
    private void simpleGroup() throws Exception {
        config(yaml -> {
            yaml.set("auto-messages.groups", null);
            yaml.set("auto-messages.groups.test.enabled", true);
            yaml.set("auto-messages.groups.test.interval-seconds", 10);
            yaml.set("auto-messages.groups.test.initial-delay-seconds", 1);
            yaml.set("auto-messages.groups.test.order", "SEQUENTIAL");
            yaml.set("auto-messages.groups.test.respect-tip-preference", true);
            yaml.set("auto-messages.groups.test.messages", List.of(
                    Map.of("id", "one", "delivery", "CHAT", "lines", List.of("First {player_name}")),
                    Map.of("id", "two", "delivery", "CHAT", "lines", List.of("Second {player_name}"))));
        });
        plugin.getAutoMessages().close();
    }
    @Test void scheduledMessagesArePersonalizedAndPreviewDoesNotAdvance() throws Exception {
        simpleGroup();
        var player = player("Viewer");
        AtomicLong clock = new AtomicLong();
        try (var manager = new AutoMessageManager(plugin, clock::get)) {
            manager.reload();
            manager.preview("test", player);
            assertEquals("First Viewer", next(player));
            clock.set(1_000_000_000L);
            manager.tick();
            assertEquals("First Viewer", next(player));
            clock.set(11_000_000_000L);
            manager.tick();
            assertEquals("Second Viewer", next(player));
            assertNull(next(player));
        }
    }
    @Test void playerOptOutSkipsDeliveryAndDoesNotConsumeRotation() throws Exception {
        simpleGroup();
        var player = player("Viewer");
        plugin.getPreferences().set(player.getUniqueId(), plugin.getPreferences().get(player.getUniqueId()).toggleTips());
        AtomicLong clock = new AtomicLong();
        try (var manager = new AutoMessageManager(plugin, clock::get)) {
            manager.reload();
            clock.set(1_000_000_000L);
            manager.tick();
            assertNull(next(player));
            plugin.getPreferences().set(player.getUniqueId(), plugin.getPreferences().get(player.getUniqueId()).toggleTips());
            clock.set(11_000_000_000L);
            manager.tick();
            assertEquals("First Viewer", next(player));
        }
    }
    @Test void manualSendReschedulesAndLagDoesNotCreateCatchupFloods() throws Exception {
        simpleGroup();
        var player = player("Viewer");
        AtomicLong clock = new AtomicLong();
        try (var manager = new AutoMessageManager(plugin, clock::get)) {
            manager.reload();
            assertEquals(1, manager.sendNow("test"));
            assertEquals("First Viewer", next(player));
            clock.set(1_000_000_000L);
            manager.tick();
            assertNull(next(player));
            clock.set(1_000_000_000_000L);
            manager.tick();
            assertEquals("Second Viewer", next(player));
            assertNull(next(player));
            manager.tick();
            assertNull(next(player));
        }
    }
    @Test void pausedMessagesWaitForResumeInterval() throws Exception {
        simpleGroup();
        var player = player("Viewer");
        AtomicLong clock = new AtomicLong();
        try (var manager = new AutoMessageManager(plugin, clock::get)) {
            manager.reload();
            manager.pause();
            clock.set(10_000_000_000L);
            manager.tick();
            assertNull(next(player));
            manager.resume();
            manager.tick();
            assertNull(next(player));
            clock.set(20_000_000_000L);
            manager.tick();
            assertEquals("First Viewer", next(player));
        }
    }
    @Test void worldAndPermissionFiltersAreRespected() throws Exception {
        simpleGroup();
        var player = player("Viewer");
        config(yaml -> {
            yaml.set("auto-messages.groups.test.permission", "tips.staff");
            yaml.set("auto-messages.groups.test.worlds", List.of("other"));
        });
        var manager = plugin.getAutoMessages();
        assertEquals(0, manager.sendNow("test"));
        player.addAttachment(plugin, "tips.staff", true);
        assertEquals(0, manager.sendNow("test"));
        player.teleport(new org.bukkit.Location(server.addSimpleWorld("other"), 0, 64, 0));
        assertEquals(1, manager.sendNow("test"));
        assertEquals("First Viewer", next(player));
    }
    @Test void minimumOnlineAndDisabledSchedulerPreventDelivery() throws Exception {
        simpleGroup();
        player("Viewer");
        config(yaml -> yaml.set("auto-messages.groups.test.min-online", 2));
        assertEquals(0, plugin.getAutoMessages().sendNow("test"));
        player("Another");
        assertEquals(2, plugin.getAutoMessages().sendNow("test"));
        config(yaml -> yaml.set("auto-messages.enabled", false));
        assertEquals(0, plugin.getAutoMessages().sendNow("test"));
    }
    @Test void actionBarDeliveryUsesItsOwnChannel() throws Exception {
        simpleGroup();
        var player = player("Viewer");
        config(yaml -> yaml.set("auto-messages.groups.test.messages",
                List.of(Map.of("id", "bar", "delivery", "ACTION_BAR", "lines", List.of("Action {player_name}")))));
        assertEquals(1, plugin.getAutoMessages().sendNow("test"));
        assertEquals("Action Viewer", plain(player.nextActionBar()));
        assertNull(next(player));
    }
    @Test void repeatedReloadKeepsOnlyOneSchedulerTask() throws Exception {
        simpleGroup();
        player("Viewer");
        for (int i = 0; i < 5; i++) assertTrue(plugin.reloadPlugin());
        long count = server.getScheduler().getPendingTasks().stream().filter(task -> task.getOwner() == plugin).count();
        // One preference timer, one auto-message timer, one Chat Events coordinator and one preview-cache cleanup timer.
        assertEquals(4, count);
        assertTrue(plugin.getChatEvents().taskActive());
    }

    @Test void titleDeliveryUsesTitleAndSubtitle() throws Exception {
        simpleGroup();
        // MockBukkit's legacy nextTitle queue does not record Adventure titles.
        var received = new java.util.concurrent.atomic.AtomicReference<net.kyori.adventure.title.Title>();
        var player = new org.mockbukkit.mockbukkit.entity.PlayerMock(server, "Viewer") {
            @Override public void showTitle(net.kyori.adventure.title.Title title) { received.set(title); }
        };
        server.addPlayer(player);
        config(yaml -> yaml.set("auto-messages.groups.test.messages", List.of(Map.of(
                "id", "title", "delivery", "TITLE", "lines", List.of("Event", "Welcome {player_name}")))));
        assertEquals(1, plugin.getAutoMessages().sendNow("test"));
        assertNotNull(received.get());
        assertEquals("Event", plain(received.get().title()));
        assertEquals("Welcome Viewer", plain(received.get().subtitle()));
        assertEquals(java.time.Duration.ofSeconds(3), received.get().times().stay());
        assertNull(next(player));
    }
}
