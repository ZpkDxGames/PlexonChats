package com.antondev.chats;

import net.kyori.adventure.text.Component;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConnectionMessageTest extends PluginTestBase {
    @Test void defaultModesLeaveExistingMessagesUntouched() {
        var player = player("Viewer");
        var join = new PlayerJoinEvent(player, Component.text("Existing join"));
        var quit = new PlayerQuitEvent(player, Component.text("Existing quit"), PlayerQuitEvent.QuitReason.DISCONNECTED);
        server.getPluginManager().callEvent(join);
        server.getPluginManager().callEvent(quit);
        assertEquals("Existing join", plain(join.joinMessage()));
        assertEquals("Existing quit", plain(quit.quitMessage()));
    }

    @Test void customFormatsResolvePlayerAndPostEventOnlineCount() throws Exception {
        var player = player("Viewer");
        player("Other");
        config(yaml -> {
            yaml.set("connection-messages.join.mode", "CUSTOM");
            yaml.set("connection-messages.join.format", "Welcome {player_name}; online={online}");
            yaml.set("connection-messages.quit.mode", "CUSTOM");
            yaml.set("connection-messages.quit.format", "Bye {player_name}; online={online}");
        });
        var join = new PlayerJoinEvent(player, Component.text("Native"));
        var quit = new PlayerQuitEvent(player, Component.text("Native"), PlayerQuitEvent.QuitReason.DISCONNECTED);
        server.getPluginManager().callEvent(join);
        server.getPluginManager().callEvent(quit);
        assertEquals("Welcome Viewer; online=2", plain(join.joinMessage()));
        assertEquals("Bye Viewer; online=1", plain(quit.quitMessage()));
    }

    @Test void firstJoinFormatOnlyAppliesToNewPlayers() throws Exception {
        var player = spy(player("Viewer"));
        config(yaml -> {
            yaml.set("connection-messages.join.mode", "CUSTOM");
            yaml.set("connection-messages.join.format", "Back {player_name}");
            yaml.set("connection-messages.join.first-join-format", "First visit {player_name}");
        });
        doReturn(false).when(player).hasPlayedBefore();
        var first = new PlayerJoinEvent(player, Component.text("Native"));
        server.getPluginManager().callEvent(first);
        assertEquals("First visit Viewer", plain(first.joinMessage()));
        doReturn(true).when(player).hasPlayedBefore();
        var returning = new PlayerJoinEvent(player, Component.text("Native"));
        server.getPluginManager().callEvent(returning);
        assertEquals("Back Viewer", plain(returning.joinMessage()));
    }

    @Test void hiddenModeSuppressesBothEvents() throws Exception {
        var player = player("Viewer");
        config(yaml -> {
            yaml.set("connection-messages.join.mode", "HIDDEN");
            yaml.set("connection-messages.quit.mode", "HIDDEN");
        });
        var join = new PlayerJoinEvent(player, Component.text("Native"));
        var quit = new PlayerQuitEvent(player, Component.text("Native"), PlayerQuitEvent.QuitReason.DISCONNECTED);
        server.getPluginManager().callEvent(join);
        server.getPluginManager().callEvent(quit);
        assertNull(join.joinMessage());
        assertNull(quit.quitMessage());
    }

    @Test void customModeDoesNotRevealPreviouslySuppressedMessages() throws Exception {
        var player = player("Viewer");
        config(yaml -> {
            yaml.set("connection-messages.join.mode", "CUSTOM");
            yaml.set("connection-messages.quit.mode", "CUSTOM");
        });
        var join = new PlayerJoinEvent(player, (Component) null);
        var quit = new PlayerQuitEvent(player, (Component) null, PlayerQuitEvent.QuitReason.DISCONNECTED);
        server.getPluginManager().callEvent(join);
        server.getPluginManager().callEvent(quit);
        assertNull(join.joinMessage());
        assertNull(quit.quitMessage());
    }

    @Test void silentPermissionIsExplicitAndNotGrantedToOperators() throws Exception {
        var player = player("Viewer");
        player.setOp(true);
        assertFalse(player.hasPermission("plexonchats.connection.silent"));
        config(yaml -> yaml.set("connection-messages.join.mode", "CUSTOM"));
        player.addAttachment(plugin, "plexonchats.connection.silent", true);
        var join = new PlayerJoinEvent(player, Component.text("Native"));
        server.getPluginManager().callEvent(join);
        assertNull(join.joinMessage());
    }

    @Test void invalidConnectionModeKeepsTheLiveConfiguration() throws Exception {
        var path = plugin.getDataFolder().toPath().resolve("config.yml");
        var yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(path.toFile());
        yaml.set("connection-messages.join.mode", "INVALID");
        yaml.save(path.toFile());
        long revision = plugin.getConfigManager().revision();
        assertFalse(plugin.reloadPlugin());
        assertEquals(revision, plugin.getConfigManager().revision());
    }

    @Test void formattingChangesTheEventWithoutManuallyBroadcasting() throws Exception {
        var player = player("Viewer");
        var recipient = player("Other");
        config(yaml -> {
            yaml.set("connection-messages.join.mode", "CUSTOM");
            yaml.set("connection-messages.join.format", "Welcome {player_name}");
        });
        drain(player);
        drain(recipient);
        var join = new PlayerJoinEvent(player, Component.text("Native"));
        server.getPluginManager().callEvent(join);
        assertEquals("Welcome Viewer", plain(join.joinMessage()));
        assertNull(next(player));
        assertNull(next(recipient));
    }
}
