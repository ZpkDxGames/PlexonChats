package com.antondev.chats;

import com.antondev.chats.api.PlexonChatEvent;
import com.antondev.chats.gui.*;
import com.antondev.chats.player.PreferenceStore;
import com.antondev.chats.protocol.*;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class UniversalChatTest extends PluginTestBase {
    private final byte[] key=new byte[32];
    private void enable()throws Exception {
        Files.writeString(plugin.getDataFolder().toPath().resolve("network.key"),HexFormat.of().formatHex(key));
        config(yaml->{yaml.set("network.enabled",true);yaml.set("chat.cooldown-milliseconds",0);yaml.set("chat.duplicate-window-seconds",0);});
    }
    private void ready()throws Exception {
        // Bridge tick sends HELLO through a real MockBukkit player plugin-message connection.
        server.getScheduler().performOneTick();
        var field=plugin.getNetworkBridge().getClass().getDeclaredField("hello");field.setAccessible(true);
        UUID nonce=(UUID)field.get(plugin.getNetworkBridge());assertNotNull(nonce);
        var player=server.getOnlinePlayers().iterator().next();
        Envelope ack=new Envelope(Envelope.Type.READY,nonce,plugin.getNetworkBridge().session(),System.currentTimeMillis(),plugin.getConfigManager().string("network.instance-id","lobby"),player.getUniqueId(),player.getName(),"","","",null,false);
        plugin.getNetworkBridge().onPluginMessageReceived(Protocol.CHANNEL,player,Protocol.encode(ack,key));
        server.getScheduler().performOneTick();assertTrue(plugin.getNetworkBridge().ready());
    }
    private Envelope pending()throws Exception {
        var field=plugin.getNetworkBridge().getClass().getDeclaredField("pending");field.setAccessible(true);
        var map=(Map<?,?>)field.get(plugin.getNetworkBridge());assertEquals(1,map.size());
        var value=map.values().iterator().next();var accessor=value.getClass().getDeclaredMethod("message");accessor.setAccessible(true);return (Envelope)accessor.invoke(value);
    }
    private void accept(Envelope e){
        Envelope ack=new Envelope(Envelope.Type.ACCEPT,e.id(),e.session(),System.currentTimeMillis(),e.server(),e.author(),e.name(),"","","ACCEPTED",null,false);
        plugin.getNetworkBridge().onPluginMessageReceived(Protocol.CHANNEL,server.getPlayer(e.author()),Protocol.encode(ack,key));server.getScheduler().performOneTick();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"lobby","smp","towny"})
    void everyPaperOriginHasCorrectLabelAndOneAcceptedEcho(String origin)throws Exception {
        var sender=player("Sender");var listener=player("Listener");enable();
        config(yaml->yaml.set("network.instance-id",origin));ready();
        plugin.getChatManager().sendPublic(sender,ChatChannel.UNIVERSAL,"origin check");
        Envelope e=pending();assertEquals(origin,e.server());accept(e);
        assertTrue(next(listener).contains("["+plugin.getConfigManager().string("network.server-labels."+origin,origin)+"]"));assertNull(next(listener));
    }
    @Test void paperBurstQueueHasBackpressureAndNoPerMessageTasks()throws Exception {
        var sender=player("Sender");var listener=player("Listener");enable();
        config(yaml->yaml.set("network.queue-capacity",1));ready();
        int tasks=server.getScheduler().getPendingTasks().size();
        plugin.getChatManager().sendPublic(sender,ChatChannel.UNIVERSAL,"first");Envelope e=pending();
        plugin.getChatManager().sendPublic(sender,ChatChannel.UNIVERSAL,"second");
        assertEquals(1,plugin.getNetworkBridge().pending());assertTrue(next(sender).contains("unavailable"));assertNull(next(listener));
        assertEquals(tasks,server.getScheduler().getPendingTasks().size());accept(e);assertTrue(next(listener).contains("first"));assertNull(next(listener));
    }
    @Test void configuredBridgeWithoutPlayersHasNoCarrierAndNoHistory()throws Exception {
        enable();server.getScheduler().performOneTick();assertTrue(plugin.getNetworkBridge().status().startsWith("NO_CARRIER"));assertEquals(0,plugin.getNetworkBridge().pending());
    }
    @Test void bundledSchemaAndContentAlreadyMatchAllShippedStockMigrations()throws Exception {
        String text;try(var in=plugin.getResource("config.yml")){text=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
        var defaults=new YamlConfiguration();defaults.loadFromString(text);
        assertEquals(com.antondev.chats.config.ConfigUpgrader.VERSION,defaults.getInt("config-version"));
        var older=new YamlConfiguration();older.loadFromString(text);older.set("config-version",8);
        com.antondev.chats.config.ConfigUpgrader.upgrade(older,defaults);
        defaults.getValues(true).forEach((path,value)->{if(!(value instanceof org.bukkit.configuration.ConfigurationSection))assertEquals(value,older.get(path),path);});
    }
    @Test void authenticatedUnknownProtocolRevokesReadinessAndShowsDiagnostic()throws Exception {
        var listener=player("Listener");enable();ready();
        Envelope e=new Envelope(Envelope.Type.DELIVER,UUID.randomUUID(),plugin.getNetworkBridge().session(),System.currentTimeMillis(),"smp",UUID.randomUUID(),"Remote","Remote","","hello",List.of(listener.getUniqueId()),false);
        byte[] data=Protocol.encode(e,key);data[5]=2;
        var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec(key,"HmacSHA256"));
        byte[] signature=mac.doFinal(Arrays.copyOf(data,data.length-32));System.arraycopy(signature,0,data,data.length-32,32);
        plugin.getNetworkBridge().onPluginMessageReceived(Protocol.CHANNEL,listener,data);
        assertFalse(plugin.getNetworkBridge().ready());assertTrue(plugin.getNetworkBridge().status().contains("INCOMPATIBLE_PROTOCOL"));assertNull(next(listener));
    }
    @Test void literalPlayerSectionCodesDoNotGainFormattingOverTransport() {
        var original=net.kyori.adventure.text.Component.text("§cLiteral \ue000 text <red>literal</red>");
        var decoded=com.antondev.chats.network.NetworkText.decode(com.antondev.chats.network.NetworkText.encode(original));
        assertEquals(plain(original),plain(decoded));assertNull(decoded.color());
    }
    @Test void standaloneUniversalIsTruthfullyUnavailableWhileGlobalWorks(){var sender=player("Sender");var listener=player("Listener");plugin.getChatManager().sendPublic(sender,ChatChannel.UNIVERSAL,"network");assertTrue(next(sender).contains("unavailable"));assertNull(next(listener));plugin.getChatManager().clearAll();plugin.getChatManager().sendPublic(sender,ChatChannel.GLOBAL,"local server");assertTrue(next(listener).contains("local server"));}
    @Test void universalOriginWaitsForAcceptanceAndHonorsModerationOnce()throws Exception {
        var sender=player("Sender");var recipient=player("Recipient");enable();ready();
        int[] calls={0};server.getPluginManager().registerEvents(new Listener(){@EventHandler public void edit(PlexonChatEvent event){calls[0]++;event.setMessage(net.kyori.adventure.text.Component.text("moderated"));}},plugin);
        plugin.getChatManager().sendPublic(sender,ChatChannel.UNIVERSAL,"raw");Envelope e=pending();assertEquals("moderated",e.body());assertNull(next(recipient));accept(e);assertTrue(next(recipient).contains("[Lobby]"));assertNull(next(recipient));accept(e);assertNull(next(recipient));assertEquals(1,calls[0]);
    }
    @Test void cancelledUniversalNeverQueuesOrDisplays()throws Exception {var sender=player("Sender");player("Recipient");enable();ready();server.getPluginManager().registerEvents(new Listener(){@EventHandler public void cancel(PlexonChatEvent event){event.setCancelled(true);}},plugin);plugin.getChatManager().sendPublic(sender,ChatChannel.UNIVERSAL,"blocked");assertEquals(0,plugin.getNetworkBridge().pending());assertNull(next(sender));}
    @Test void remoteDisplayCannotReenterApiEventRewardOrDiscordAndIsDeduplicated()throws Exception {
        var listener=player("Listener");enable();ready();int[] calls={0};server.getPluginManager().registerEvents(new Listener(){@EventHandler public void observe(PlexonChatEvent event){calls[0]++;}},plugin);
        Envelope remote=new Envelope(Envelope.Type.DELIVER,UUID.randomUUID(),plugin.getNetworkBridge().session(),System.currentTimeMillis(),"towny",UUID.randomUUID(),"Remote","§bRemote","§6Owner ","<click:run_command:'/op x'>safe literal</click>",List.of(listener.getUniqueId()),false);
        byte[] data=Protocol.encode(remote,key);plugin.getNetworkBridge().onPluginMessageReceived(Protocol.CHANNEL,listener,data);plugin.getNetworkBridge().onPluginMessageReceived(Protocol.CHANNEL,listener,data);server.getScheduler().performOneTick();String text=next(listener);assertTrue(text.contains("[Towny]"));assertTrue(text.contains("<click:run_command"));assertNull(next(listener));assertEquals(0,calls[0]);assertFalse(plugin.getChatEvents().acceptAnswer(listener,ChatChannel.UNIVERSAL,"bingo"));
        String json=net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().serialize(plugin.getChatComponentFactory().buildNetworkMessage(remote));assertFalse(json.contains("clickEvent"));assertFalse(json.contains("hoverEvent"));
    }
    @Test void remotePermissionsAndForgedOrOldGenerationPacketsRejected()throws Exception {var listener=player("Listener");enable();ready();config(yaml->yaml.set("channels.universal.receive-permission","test.universal.read"));ready();Envelope e=new Envelope(Envelope.Type.DELIVER,UUID.randomUUID(),plugin.getNetworkBridge().session(),System.currentTimeMillis(),"smp",UUID.randomUUID(),"Remote","Remote","","private",List.of(listener.getUniqueId()),false);plugin.getNetworkBridge().onPluginMessageReceived(Protocol.CHANNEL,listener,Protocol.encode(e,key));server.getScheduler().performOneTick();assertNull(next(listener));listener.addAttachment(plugin,"test.universal.read",true);byte[] forged=Protocol.encode(e,key);forged[10]^=1;plugin.getNetworkBridge().onPluginMessageReceived(Protocol.CHANNEL,listener,forged);server.getScheduler().performOneTick();assertNull(next(listener));var old=e.withType(Envelope.Type.DELIVER,UUID.randomUUID());plugin.getNetworkBridge().onPluginMessageReceived(Protocol.CHANNEL,listener,Protocol.encode(old,key));server.getScheduler().performOneTick();assertNull(next(listener));}
    @Test void nativeViewerAndApiRestrictionsDoNotExpandAcrossNetwork()throws Exception {var sender=player("Sender");var excluded=player("Excluded");enable();ready();plugin.getChatManager().selectChannel(sender,ChatChannel.UNIVERSAL);drain(sender);plugin.getChatManager().route(sender,"hello",Set.of(sender.getUniqueId()));Envelope e=pending();assertTrue(e.restricted());assertEquals(List.of(sender.getUniqueId()),e.audience());accept(e);assertNull(next(excluded));}
    @Test void selectionCommandGuiPermissionsAndPreferenceRoundTrip()throws Exception {var sender=player("Sender");assertTrue(server.dispatchCommand(sender,"u"));assertEquals(ChatChannel.UNIVERSAL,plugin.getChatManager().getPlayerChannel(sender));assertTrue(server.dispatchCommand(sender,"chat channel global"));assertTrue(server.dispatchCommand(sender,"chat universal"));assertEquals(ChatChannel.UNIVERSAL,plugin.getChatManager().getPlayerChannel(sender));plugin.getPreferences().close();try(var reloaded=new PreferenceStore(plugin)){assertEquals(ChatChannel.UNIVERSAL,reloaded.get(sender.getUniqueId()).channel());}plugin.getChatGUI().open(sender);var layout=GuiLayout.read(plugin.getConfigManager().section("gui"),"Test",message->fail(message));assertTrue(layout.buttons().values().stream().anyMatch(button->button.action()==GuiAction.UNIVERSAL));sender.addAttachment(plugin,"plexonchats.universal",false);assertFalse(plugin.getChatManager().selectChannel(sender,ChatChannel.UNIVERSAL));}
    @Test void localExactBoundariesAndGlobalAllWorlds() {var sender=player("Sender");var near=player("Near");var edge=player("Edge");var far=player("Far");var other=player("Other");sender.teleport(new Location(sender.getWorld(),0,64,0));near.teleport(new Location(sender.getWorld(),99,64,0));edge.teleport(new Location(sender.getWorld(),100,64,0));far.teleport(new Location(sender.getWorld(),101,64,0));other.teleport(new Location(server.addSimpleWorld("other"),0,64,0));plugin.getChatManager().sendPublic(sender,ChatChannel.LOCAL,"radius");assertTrue(next(near).contains("radius"));assertTrue(next(edge).contains("radius"));assertNull(next(far));assertNull(next(other));plugin.getChatManager().clearAll();plugin.getChatManager().sendPublic(sender,ChatChannel.GLOBAL,"all worlds");assertTrue(next(other).contains("all worlds"));assertTrue(next(far).contains("all worlds"));}
    @Test void v10MigrationPreservesCustomDataAndPlacesUniversalWithoutCollision()throws Exception {var file=plugin.getDataFolder().toPath().resolve("config.yml");var yaml=YamlConfiguration.loadConfiguration(file.toFile());yaml.set("config-version",10);yaml.set("gui.items.universal",null);yaml.set("gui.items.custom.slot",11);yaml.set("gui.items.custom.action","NONE");yaml.set("gui.items.custom.material","DIAMOND");yaml.set("channels.global.format","CUSTOM {message}");yaml.set("default-channel","GLOBAL");yaml.save(file.toFile());byte[] before=Files.readAllBytes(file);assertTrue(plugin.reloadPlugin());var migrated=YamlConfiguration.loadConfiguration(file.toFile());assertEquals(11,migrated.getInt("config-version"));assertEquals(11,migrated.getInt("gui.items.custom.slot"));assertNotEquals(11,migrated.getInt("gui.items.universal.slot"));assertEquals("GLOBAL",migrated.getString("default-channel"));assertEquals("CUSTOM {message}",migrated.getString("channels.global.format"));try(var files=Files.list(file.getParent())){assertTrue(files.filter(path->path.getFileName().toString().startsWith("config-before-v11-")).anyMatch(path->{try{return Arrays.equals(before,Files.readAllBytes(path));}catch(Exception ex){return false;}}));}}
    @Test void invalidNetworkReloadKeepsKnownGoodGeneration()throws Exception {var old=plugin.getNetworkBridge();long revision=plugin.getConfigManager().revision();var file=plugin.getDataFolder().toPath().resolve("config.yml");var yaml=YamlConfiguration.loadConfiguration(file.toFile());yaml.set("network.queue-capacity",0);yaml.save(file.toFile());assertFalse(plugin.reloadPlugin());assertSame(old,plugin.getNetworkBridge());assertEquals(revision,plugin.getConfigManager().revision());}
}
