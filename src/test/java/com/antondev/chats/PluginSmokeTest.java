package com.antondev.chats;

import com.antondev.chats.api.PlexonChatsAPI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import static org.junit.jupiter.api.Assertions.*;

class PluginSmokeTest {
    private ServerMock server;
    private PlexonChats plugin;
    @BeforeEach void start() { server = MockBukkit.mock(); plugin = MockBukkit.load(PlexonChats.class); }
    @AfterEach void stop() { MockBukkit.unmock(); }

    @Test void loadsWithNoOptionalPlugins() {
        assertTrue(plugin.isEnabled());
        assertEquals("DISABLED", plugin.getDiscordBridge().status());
        assertEquals(2, plugin.getAutoMessages().groupNames().size());
        assertEquals("3.1.0", plugin.getPluginMeta().getVersion());
        assertEquals("STANDALONE", plugin.getCoreBridge().mode());
        var registration = server.getServicesManager().getRegistration(PlexonChatsAPI.class);
        assertNotNull(registration);
        assertSame(plugin.getApi(), registration.getProvider());
    }

    @Test void configuredMenuCanOpen() {
        var player = server.addPlayer("Tonim");
        plugin.getChatGUI().open(player);
        assertEquals(27, player.getOpenInventory().getTopInventory().getSize());
    }
}
