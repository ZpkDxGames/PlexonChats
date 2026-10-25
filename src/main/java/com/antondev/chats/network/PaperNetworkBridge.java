package com.antondev.chats.network;

import com.antondev.chats.ChatChannel;
import com.antondev.chats.PlexonChats;
import com.antondev.chats.protocol.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/** Main-thread batching state; inbound decode is pure/immutable and queue bounded even for async callbacks. */
public final class PaperNetworkBridge implements PluginMessageListener,AutoCloseable {
    private record Pending(UUID player,Envelope message,Runnable accepted,long deadline) {}
    private final PlexonChats plugin;
    private final String instance;
    private final Set<String> allowlist;
    private final UUID session=UUID.randomUUID();
    private final RecentIds recent=new RecentIds(8192,60_000);
    private final ArrayBlockingQueue<Envelope> inbound;
    private final ArrayBlockingQueue<UUID> outbound;
    private final Map<UUID,Pending> pending=new LinkedHashMap<>();
    private final AtomicLong rejected=new AtomicLong(),duplicates=new AtomicLong(),drops=new AtomicLong();
    private byte[] key;
    private volatile boolean closed;
    private boolean registered;
    private BukkitTask task;
    private UUID hello;
    private long readyUntil,nextHello;
    private String state="DISABLED";
    public PaperNetworkBridge(PlexonChats plugin) {
        this.plugin=plugin;var config=plugin.getConfigManager();
        instance=config.string("network.instance-id","lobby");allowlist=Set.copyOf(config.lines("network.allowed-server-ids"));
        int capacity=config.integer("network.queue-capacity",256,1,1024);
        inbound=new ArrayBlockingQueue<>(capacity);outbound=new ArrayBlockingQueue<>(capacity);
        if(!config.bool("network.enabled",false))return;
        try {key=Protocol.readKey(plugin.getDataFolder().toPath().resolve(config.string("network.authentication-key-file","network.key")));}
        catch(IOException ex){state="KEY_UNAVAILABLE";plugin.getLogger().warning("Universal chat unavailable: configure the dedicated network key file. Local/Global are independent.");return;}
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin,Protocol.CHANNEL,this);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin,Protocol.CHANNEL);registered=true;
        state="WAITING_FOR_PROXY";task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,1,1);
    }
    public boolean ready(){return !closed&&key!=null&&System.currentTimeMillis()<readyUntil;}
    public boolean submit(Player author,Envelope message,Runnable accepted) {
        if(!ready()||pending.size()>=inbound.remainingCapacity()+inbound.size()||outbound.remainingCapacity()==0){unavailable(author);drops.incrementAndGet();return false;}
        try {Protocol.encode(message,key);}catch(IllegalArgumentException ex){author.sendMessage(plugin.getConfigManager().message("network-payload-invalid"));return false;}
        pending.put(message.id(),new Pending(author.getUniqueId(),message,accepted,System.currentTimeMillis()+4000));
        if(!outbound.offer(message.id())){pending.remove(message.id());unavailable(author);return false;}
        return true;
    }
    public Envelope envelope(Player player,String display,String rank,String body,List<UUID> originViewers,boolean restricted) {
        return new Envelope(Envelope.Type.SUBMIT,UUID.randomUUID(),session,System.currentTimeMillis(),instance,
                player.getUniqueId(),player.getName(),display,rank,body,originViewers,restricted);
    }
    @Override public void onPluginMessageReceived(String channel,Player carrier,byte[] data) {
        if(closed||key==null||!channel.equals(Protocol.CHANNEL))return;
        try {
            Envelope e=Protocol.decode(data,key,System.currentTimeMillis());
            if(!e.session().equals(session)||!allowlist.contains(e.server())||
                    !Set.of(Envelope.Type.READY,Envelope.Type.DELIVER,Envelope.Type.ACCEPT,Envelope.Type.REJECT).contains(e.type()))throw new IllegalArgumentException();
            if(!inbound.offer(e))drops.incrementAndGet();
        }catch(IllegalArgumentException ex){rejected.incrementAndGet();}
    }
    private void tick() {
        if(closed)return;long now=System.currentTimeMillis();recent.cleanup(now);
        if(now>=nextHello) {
            Player carrier=Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
            if(carrier==null){state="NO_CARRIER";readyUntil=0;}
            else {hello=UUID.randomUUID();send(carrier,new Envelope(Envelope.Type.HELLO,hello,session,now,instance,carrier.getUniqueId(),carrier.getName(),"","","",null,false));}
            nextHello=now+3000;
        }
        for(int i=0;i<64;i++){Envelope e=inbound.poll();if(e==null)break;receive(e,now);}
        for(int i=0;i<64;i++){
            UUID id=outbound.poll();if(id==null)break;Pending work=pending.get(id);if(work==null)continue;
            Player player=Bukkit.getPlayer(work.player());
            if(player==null||!player.isOnline()||!ready()||now>work.deadline()){pending.remove(id);if(player!=null)unavailable(player);drops.incrementAndGet();continue;}
            if(!send(player,work.message())){pending.remove(id);unavailable(player);}
        }
        var iterator=pending.entrySet().iterator();
        while(iterator.hasNext()){Pending work=iterator.next().getValue();if(now>work.deadline()){iterator.remove();Player player=Bukkit.getPlayer(work.player());if(player!=null)unavailable(player);drops.incrementAndGet();}}
    }
    private void receive(Envelope e,long now) {
        if(now-e.createdAt()>Protocol.MAX_AGE_MS){drops.incrementAndGet();return;}
        if(e.type()==Envelope.Type.READY){if(instance.equals(e.server())&&e.id().equals(hello)){readyUntil=now+8000;state="READY";hello=null;}else rejected.incrementAndGet();return;}
        if(e.type()==Envelope.Type.ACCEPT||e.type()==Envelope.Type.REJECT){
            if(!instance.equals(e.server())){rejected.incrementAndGet();return;}
            Pending work=pending.remove(e.id());if(work==null)return;
            if(!work.player().equals(e.author())||now>work.deadline()){drops.incrementAndGet();return;}
            Player author=Bukkit.getPlayer(work.player());
            if(e.type()==Envelope.Type.REJECT){if(author!=null)unavailable(author);return;}
            if(recent.add(e.id(),now))work.accepted().run();else duplicates.incrementAndGet();
            if(e.body().equals("PARTIAL")&&author!=null)author.sendMessage(plugin.getConfigManager().message("network-partial"));
            return;
        }
        if(instance.equals(e.server())||e.audience()==null){rejected.incrementAndGet();return;}
        if(!recent.add(e.id(),now)){duplicates.incrementAndGet();return;}
        if(!plugin.getConfigManager().isChannelEnabled(ChatChannel.UNIVERSAL))return;
        var formatted=plugin.getChatComponentFactory().buildNetworkMessage(e);
        Set<UUID> audience=Set.copyOf(e.audience());
        for(Player recipient:Bukkit.getOnlinePlayers())if(audience.contains(recipient.getUniqueId())&&plugin.getChatManager().canReceive(recipient,ChatChannel.UNIVERSAL))recipient.sendMessage(formatted);
        // No public API/native events, logging, Discord, mentions, answer/reward processing or transport here.
    }
    private boolean send(Player carrier,Envelope message) {
        try {carrier.sendPluginMessage(plugin,Protocol.CHANNEL,Protocol.encode(message,key));return true;}
        catch(RuntimeException ex){drops.incrementAndGet();readyUntil=0;state="TRANSPORT_FAILED";return false;}
    }
    public void unavailable(Player player){player.sendMessage(plugin.getConfigManager().message("network-unavailable"));}
    public String status(){return (ready()?"READY":state.equals("READY")?"UNAVAILABLE":state)+" protocol=1 pending="+pending.size()+" inbound="+inbound.size()+" outbound="+outbound.size()+" drops="+drops.get()+" duplicates="+duplicates.get()+" rejected="+rejected.get();}
    public int pending(){return pending.size();}
    public UUID session(){return session;}
    @Override public void close(){if(closed)return;closed=true;if(task!=null)task.cancel();if(registered){plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin,Protocol.CHANNEL,this);plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin,Protocol.CHANNEL);}for(Pending work:pending.values()){Player author=Bukkit.getPlayer(work.player());if(author!=null)unavailable(author);}pending.clear();inbound.clear();outbound.clear();recent.clear();readyUntil=0;state="CLOSED";}
}
