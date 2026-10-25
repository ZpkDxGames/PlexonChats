package com.antondev.chats.velocity;

import com.antondev.chats.protocol.Protocol;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.scheduler.ScheduledTask;
import org.slf4j.Logger;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** No player chat ownership on proxy, no listeners/ports beyond the registered plugin channel. */
public final class PlexonChatsVelocity {
    private static final MinecraftChannelIdentifier CHANNEL=MinecraftChannelIdentifier.from(Protocol.CHANNEL);
    private final ProxyServer proxy;private final Logger logger;private final Path directory;
    private Relay relay;private ScheduledTask worker;
    @Inject public PlexonChatsVelocity(ProxyServer proxy,Logger logger,@DataDirectory Path directory){this.proxy=proxy;this.logger=logger;this.directory=directory;}
    @Subscribe public void initialize(ProxyInitializeEvent event) {
        // Always claim the channel, even if config is invalid, to block forged client forwarding.
        proxy.getChannelRegistrar().register(CHANNEL);
        try {
            Files.createDirectories(directory);Path file=directory.resolve("bridge.properties");
            if(!Files.exists(file))try(var in=getClass().getResourceAsStream("/bridge.properties")){Files.copy(Objects.requireNonNull(in),file);}
            Properties p=new Properties();try(var in=Files.newInputStream(file)){p.load(in);}
            if(!Boolean.parseBoolean(p.getProperty("enabled","false"))){logger.info("PlexonChats network DISABLED; configure bridge.properties and dedicated backend key files.");return;}
            Map<String,byte[]> keys=new HashMap<>();
            for(String raw:p.getProperty("servers","lobby,smp,towny").split(",")) {
                String id=raw.strip();if(!Protocol.validServer(id)||proxy.getServer(id).isEmpty()||keys.containsKey(id))throw new IllegalArgumentException("Invalid or unregistered backend");
                keys.put(id,Protocol.readKey(directory.resolve(p.getProperty("key."+id,"keys/"+id+".key"))));
            }
            int capacity=Integer.parseInt(p.getProperty("queue-capacity","256"));
            relay=new Relay(keys,new Relay.Transport(){
                public boolean current(String backend,UUID carrier,UUID author,String name){
                    if(!carrier.equals(author))return false;
                    return proxy.getPlayer(author).filter(player->player.getUsername().equals(name))
                            .flatMap(player->player.getCurrentServer()).filter(connection->connection.getServerInfo().getName().equals(backend)).isPresent();
                }
                public Set<UUID> audience(String backend){return proxy.getServer(backend).map(server->server.getPlayersConnected().stream().map(com.velocitypowered.api.proxy.Player::getUniqueId).collect(java.util.stream.Collectors.toSet())).orElse(Set.of());}
                public boolean send(String backend,byte[] data){return proxy.getServer(backend).map(server->server.sendPluginMessage(CHANNEL,data)).orElse(false);}
            },capacity);
            worker=proxy.getScheduler().buildTask(this,()->relay.drain(System.currentTimeMillis())).repeat(50,TimeUnit.MILLISECONDS).schedule();
            proxy.getCommandManager().register(proxy.getCommandManager().metaBuilder("plexonchatsbridge").plugin(this).build(),new com.velocitypowered.api.command.SimpleCommand(){
                public void execute(Invocation invocation){invocation.source().sendMessage(net.kyori.adventure.text.Component.text(relay.status()));}
                public boolean hasPermission(Invocation invocation){return invocation.source().hasPermission("plexonchats.bridge.manage");}
            });
            logger.info("PlexonChats Velocity bridge active, protocol {}, {} backends",Protocol.VERSION,keys.size());
        }catch(IOException|IllegalArgumentException ex){logger.error("PlexonChats bridge unavailable: {}. Local Paper chat remains independent.",ex.getClass().getSimpleName());}
    }
    @Subscribe public void message(PluginMessageEvent event) {
        if(!CHANNEL.equals(event.getIdentifier()))return;
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if(relay==null || !(event.getSource() instanceof ServerConnection source))return;
        // A lingering connection during a switch must not author traffic on its old backend.
        if(source.getPlayer().getCurrentServer().filter(current->current==source).isEmpty())return;
        relay.receive(source.getServerInfo().getName(),source.getPlayer().getUniqueId(),event.getData(),System.currentTimeMillis());
    }
    @Subscribe public void shutdown(ProxyShutdownEvent event){if(worker!=null)worker.cancel();if(relay!=null)relay.close();proxy.getChannelRegistrar().unregister(CHANNEL);proxy.getCommandManager().unregister("plexonchatsbridge");}
}
