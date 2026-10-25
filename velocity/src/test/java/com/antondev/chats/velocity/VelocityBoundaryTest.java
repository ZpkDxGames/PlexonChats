package com.antondev.chats.velocity;

import com.antondev.chats.protocol.Protocol;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import java.nio.file.Path;
import static org.mockito.Mockito.*;

class VelocityBoundaryTest {
    @TempDir Path directory;
    @Test void clientMessageIsClaimedEvenWhenBridgeDisabled(){
        var plugin=new PlexonChatsVelocity(mock(ProxyServer.class),mock(Logger.class),directory);
        var event=mock(PluginMessageEvent.class);
        when(event.getIdentifier()).thenReturn(MinecraftChannelIdentifier.from(Protocol.CHANNEL));
        when(event.getSource()).thenReturn(mock(Player.class));
        plugin.message(event);
        verify(event).setResult(PluginMessageEvent.ForwardResult.handled());
        verify(event,never()).getData();
    }
    @Test void unrelatedChannelsRemainOwnedByTheirPlugins(){
        var plugin=new PlexonChatsVelocity(mock(ProxyServer.class),mock(Logger.class),directory);
        var event=mock(PluginMessageEvent.class);
        when(event.getIdentifier()).thenReturn(MinecraftChannelIdentifier.from("other:plugin"));
        plugin.message(event);verify(event,never()).setResult(any());
    }
}
