package com.antondev.chats;

import com.antondev.chats.api.PlexonChatEvent;
import com.antondev.chats.integration.DiscordBridge;
import com.antondev.chats.integration.DiscordSrvBridge;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.events.DiscordGuildMessagePostProcessEvent;
import github.scarsz.discordsrv.api.events.GameChatMessagePreProcessEvent;
import github.scarsz.discordsrv.dependencies.jda.api.JDA;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import github.scarsz.discordsrv.dependencies.jda.api.entities.User;
import github.scarsz.configuralize.DynamicConfig;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Only mock Discord/JDA endpoints: these tests never log in, send real messages, or need a bot token. */
@SuppressWarnings("deprecation")
class DiscordBridgeTest extends PluginTestBase {
    private MockedStatic<DiscordSRV> globals;
    private DiscordSRV discord;
    private DiscordSrvBridge bridge;
    private TextChannel channel;

    @BeforeEach void setUpBridge() {
        globals = mockStatic(DiscordSRV.class);
        discord = mock(DiscordSRV.class);
        globals.when(DiscordSRV::getPlugin).thenReturn(discord);
        globals.when(DiscordSRV::config).thenReturn(mock(DynamicConfig.class));
        when(discord.isEnabled()).thenReturn(true);
        JDA jda = mock(JDA.class);
        when(discord.getJda()).thenReturn(jda);
        when(jda.getStatus()).thenReturn(JDA.Status.CONNECTED);
        channel = mock(TextChannel.class);
        when(channel.getName()).thenReturn("global-chat");
        when(discord.getDestinationTextChannelForGameChannelName("global")).thenReturn(channel);
        when(discord.getDestinationGameChannelNameForTextChannel(channel)).thenReturn("global");
        DiscordSRV.isReady = true;
        bridge = new DiscordSrvBridge(plugin);
    }

    @AfterEach void tearDownBridge() {
        try {
            if (bridge != null) bridge.close();
            if (globals != null) DiscordSRV.isReady = false;
        } finally {
            if (globals != null) globals.close();
        }
    }

    @Test void statusDistinguishesMissingMappingAndConnection() {
        assertEquals("ACTIVE", bridge.status());
        when(discord.getDestinationTextChannelForGameChannelName("global")).thenReturn(null);
        assertEquals("CHANNEL_NOT_MAPPED", bridge.status());
        DiscordSRV.isReady = false;
        assertEquals("WAITING_FOR_DISCORD", bridge.status());
    }

    @Test void bothNativeChatRoutesAreCancelledToPreventDuplicateOrLocalLeaks() {
        for (var nativeEvent : java.util.List.of(mock(AsyncChatEvent.class), mock(AsyncPlayerChatEvent.class))) {
            var event = mock(GameChatMessagePreProcessEvent.class);
            when(event.getTriggeringBukkitEvent()).thenReturn(nativeEvent);
            bridge.preventNativeDuplicate(event);
            verify(event).setCancelled(true);
        }
    }

    @Test void managedGlobalMessageIsNotCancelledByNativeGuard() {
        var player = player("Sender");
        var managed = new PlexonChatEvent(player, ChatChannel.GLOBAL, "hello", Component.text("hello"), Set.of(player));
        var event = mock(GameChatMessagePreProcessEvent.class);
        when(event.getTriggeringBukkitEvent()).thenReturn(managed);
        bridge.preventNativeDuplicate(event);
        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test void localAndCancelledEventsNeverForward() {
        var sender = player("Sender");
        var local = new PlexonChatEvent(sender, ChatChannel.LOCAL, "local", Component.text("local"), Set.of(sender));
        bridge.sendChat(local);
        var global = new PlexonChatEvent(sender, ChatChannel.GLOBAL, "cancelled", Component.text("cancelled"), Set.of(sender));
        global.setCancelled(true);
        bridge.sendChat(global);
        global.setCancelled(false);
        global.setDiscordAllowed(false);
        bridge.sendChat(global);
        server.getScheduler().waitAsyncTasksFinished();
        verify(discord, never()).processChatMessage(any(), anyString(), anyString(), anyBoolean(), any());
    }

    @Test void globalForwardUsesProcessedBodyAndSuppressesMassMentions() {
        var sender = player("Sender");
        var event = new PlexonChatEvent(sender, ChatChannel.GLOBAL, "raw", Component.text("filtered @everyone"), Set.of(sender));
        bridge.sendChat(event);
        server.getScheduler().waitAsyncTasksFinished();
        verify(discord, times(1)).processChatMessage(sender, "filtered @\u200Beveryone", "global", false, event);
    }

    @Test void incomingMessageIsDeliveredOnceWithoutEcho() {
        var player = player("Recipient");
        var event = incomingEvent();
        bridge.receive(event);
        verify(event).setCancelled(true);
        assertNull(next(player));
        server.getScheduler().performOneTick();
        assertEquals("Discord hello", next(player));
        assertNull(next(player));
        verify(discord, never()).processChatMessage(any(), anyString(), anyString(), anyBoolean(), any());
    }

    @Test void alreadyCancelledAndUnmappedIncomingMessagesAreLeftAlone() {
        var player = player("Recipient");
        var cancelled = incomingEvent();
        when(cancelled.isCancelled()).thenReturn(true);
        bridge.receive(cancelled);
        verify(cancelled, never()).setCancelled(anyBoolean());
        var unmapped = incomingEvent();
        when(discord.getDestinationGameChannelNameForTextChannel(channel)).thenReturn("staff");
        bridge.receive(unmapped);
        verify(unmapped, never()).setCancelled(anyBoolean());
        server.getScheduler().performOneTick();
        assertNull(next(player));
    }

    @Test void disablingIncomingStillSuppressesNativeDeliveryForMappedChannel() throws Exception {
        bridge.close();
        config(yaml -> yaml.set("integrations.discordsrv.discord-to-minecraft", false));
        bridge = new DiscordSrvBridge(plugin);
        var player = player("Recipient");
        var event = incomingEvent();
        bridge.receive(event);
        verify(event).setCancelled(true);
        server.getScheduler().performOneTick();
        assertNull(next(player));
    }

    @Test void incomingPermissionFilterIsAppliedOnMainThread() throws Exception {
        bridge.close();
        config(yaml -> yaml.set("integrations.discordsrv.receive-permission", "discord.read"));
        bridge = new DiscordSrvBridge(plugin);
        var permitted = player("Allowed");
        var denied = player("Denied");
        permitted.addAttachment(plugin, "discord.read", true);
        bridge.receive(incomingEvent());
        server.getScheduler().performOneTick();
        assertEquals("Discord hello", next(permitted));
        assertNull(next(denied));
    }

    @Test void shutdownDropsQueuedInboundMessage() {
        var player = player("Recipient");
        bridge.receive(incomingEvent());
        bridge.close();
        server.getScheduler().performOneTick();
        assertNull(next(player));
    }

    @Test void incompatibleLookupDoesNotEscapeIntoChatDelivery() {
        when(discord.getDestinationGameChannelNameForTextChannel(channel)).thenThrow(new NoSuchMethodError("API changed"));
        assertDoesNotThrow(() -> bridge.receive(incomingEvent()));
        when(discord.getDestinationTextChannelForGameChannelName("global")).thenThrow(new NoSuchMethodError("API changed"));
        assertEquals("INCOMPATIBLE", bridge.status());
    }

    private DiscordGuildMessagePostProcessEvent incomingEvent() {
        var event = mock(DiscordGuildMessagePostProcessEvent.class);
        when(event.getChannel()).thenReturn(channel);
        User author = mock(User.class);
        when(author.getName()).thenReturn("DiscordUser");
        when(event.getAuthor()).thenReturn(author);
        when(event.getMinecraftMessage()).thenReturn(
                github.scarsz.discordsrv.dependencies.kyori.adventure.text.Component.text("Discord hello"));
        return event;
    }
}
