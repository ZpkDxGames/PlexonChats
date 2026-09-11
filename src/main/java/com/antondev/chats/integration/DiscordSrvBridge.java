package com.antondev.chats.integration;

import com.antondev.chats.ChatChannel;
import com.antondev.chats.PlexonChats;
import com.antondev.chats.api.PlexonChatEvent;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.ListenerPriority;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.DiscordGuildMessagePostProcessEvent;
import github.scarsz.discordsrv.api.events.GameChatMessagePreProcessEvent;
import github.scarsz.discordsrv.util.DiscordUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.ChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/** DiscordSRV 1.30.5 API. This class is loaded only if DiscordSRV is installed and enabled. */
public final class DiscordSrvBridge implements DiscordBridge {
    private static final int OUTBOUND_QUEUE_CAPACITY = 256;
    private static final int MAX_OUTBOUND_PER_TICK = 64;

    private final PlexonChats plugin;
    private final String channel;
    private final boolean outgoing;
    private final boolean incoming;
    private final boolean suppressMentions;
    private final boolean announcements;
    private final String incomingFormat;
    private final String receivePermission;
    private final AtomicBoolean warned = new AtomicBoolean();
    private final ArrayBlockingQueue<Runnable> outbound = new ArrayBlockingQueue<>(OUTBOUND_QUEUE_CAPACITY);
    private final BukkitTask outboundWorker;
    private volatile boolean closed;

    public DiscordSrvBridge(PlexonChats plugin) {
        this.plugin = plugin;
        var config = plugin.getConfigManager();
        channel = config.string("integrations.discordsrv.game-channel", "global");
        outgoing = config.bool("integrations.discordsrv.minecraft-to-discord", true);
        incoming = config.bool("integrations.discordsrv.discord-to-minecraft", true);
        suppressMentions = config.bool("integrations.discordsrv.suppress-mentions", true);
        announcements = config.bool("integrations.discordsrv.forward-announcements", false);
        incomingFormat = config.string("integrations.discordsrv.incoming-format", "{message}");
        receivePermission = config.string("integrations.discordsrv.receive-permission", "");
        DiscordSRV.api.subscribe(this);
        outboundWorker = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::drainOutbound, 1L, 1L);
        plugin.getLogger().info("DiscordSRV adapter registered for game channel '" + channel + "'. Only global chat is forwarded.");
    }

    @Override public String status() {
        if (closed) return "DISABLED";
        try {
            DiscordSRV discord = DiscordSRV.getPlugin();
            if (discord == null || !discord.isEnabled()) return "NOT_INSTALLED";
            if (!DiscordSRV.isReady || discord.getJda() == null
                    || discord.getJda().getStatus() != github.scarsz.discordsrv.dependencies.jda.api.JDA.Status.CONNECTED) return "WAITING_FOR_DISCORD";
            return discord.getDestinationTextChannelForGameChannelName(channel) == null ? "CHANNEL_NOT_MAPPED" : "ACTIVE";
        } catch (RuntimeException | LinkageError ex) {
            warn(ex);
            return "INCOMPATIBLE";
        }
    }

    @SuppressWarnings("deprecation")
    @Subscribe(priority = ListenerPriority.HIGHEST)
    public void preventNativeDuplicate(GameChatMessagePreProcessEvent event) {
        if (closed) return;
        Object triggering = event.getTriggeringBukkitEvent();
        if (triggering instanceof ChatEvent || triggering instanceof AsyncChatEvent || triggering instanceof AsyncPlayerChatEvent) event.setCancelled(true);
    }

    @Override public void sendChat(PlexonChatEvent event) {
        if (closed || !outgoing || event.isCancelled() || event.getChannel() != ChatChannel.GLOBAL || !event.isDiscordAllowed()) return;
        if (!status().equals("ACTIVE")) return;
        DiscordSRV discord = DiscordSRV.getPlugin();
        String text = safeMentions(PlainTextComponentSerializer.plainText().serialize(event.getMessage()));
        enqueue(() -> {
            if (closed) return;
            try { discord.processChatMessage(event.getPlayer(), text, channel, false, event); }
            catch (RuntimeException | LinkageError ex) { warn(ex); }
        });
    }

    @Subscribe(priority = ListenerPriority.HIGHEST)
    public void receive(DiscordGuildMessagePostProcessEvent event) {
        if (closed || event.isCancelled()) return;
        try {
            String destination = DiscordSRV.getPlugin().getDestinationGameChannelNameForTextChannel(event.getChannel());
            if (destination == null || !destination.equalsIgnoreCase(channel)) return;
            event.setCancelled(true);
            if (!incoming) return;
            String json = github.scarsz.discordsrv.dependencies.kyori.adventure.text.serializer.gson.GsonComponentSerializer
                    .gson().serialize(event.getMinecraftMessage());
            Component message = GsonComponentSerializer.gson().deserialize(json);
            String name = event.getMember() == null ? event.getAuthor().getName() : event.getMember().getEffectiveName();
            Map<String, Component> values = Map.of(
                    "message", message,
                    "discord_name", Component.text(name),
                    "discord_user", Component.text(event.getAuthor().getName()),
                    "discord_channel", Component.text(event.getChannel().getName()));
            // Discord callbacks are not guaranteed to be on the primary thread. One sync handoff is required
            // for Bukkit player/permission delivery; unlike outgoing player chat, this is not one task per MC message.
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (closed || !plugin.getConfigManager().isGlobalEnabled()) return;
                Component rendered = plugin.getText().render(incomingFormat, null, values);
                Bukkit.getOnlinePlayers().stream().filter(p -> plugin.getChatManager().canReceive(p, ChatChannel.GLOBAL))
                        .filter(p -> receivePermission.isBlank() || p.hasPermission(receivePermission)).forEach(p -> p.sendMessage(rendered));
                if (plugin.getConfigManager().bool("chat.log-to-console", true)) Bukkit.getConsoleSender().sendMessage(rendered);
            });
        } catch (RuntimeException | LinkageError ex) { warn(ex); }
    }

    @Override public void sendAnnouncement(Component message) {
        if (closed || !announcements || !outgoing || !status().equals("ACTIVE")) return;
        try {
            if (!DiscordSRV.config().getBoolean("DiscordChatChannelMinecraftToDiscord")) return;
            var destination = DiscordSRV.getPlugin().getDestinationTextChannelForGameChannelName(channel);
            String text = safeMentions(PlainTextComponentSerializer.plainText().serialize(message));
            if (text.isBlank()) return;
            enqueue(() -> {
                if (closed) return;
                try { DiscordUtil.sendMessage(destination, text); }
                catch (RuntimeException | LinkageError ex) { warn(ex); }
            });
        } catch (RuntimeException | LinkageError ex) { warn(ex); }
    }

    private void enqueue(Runnable work) {
        if (closed) return;
        if (!outbound.offer(work)) {
            warn(new IllegalStateException("outbound queue full (capacity=" + OUTBOUND_QUEUE_CAPACITY + ")"));
        }
    }

    private void drainOutbound() {
        if (closed) return;
        for (int i = 0; i < MAX_OUTBOUND_PER_TICK; i++) {
            Runnable work = outbound.poll();
            if (work == null) return;
            try { work.run(); }
            catch (RuntimeException | LinkageError ex) { warn(ex); }
        }
    }

    int pendingOutbound() { return outbound.size(); }
    boolean outboundWorkerActive() { return !outboundWorker.isCancelled(); }
    private String safeMentions(String value) { return suppressMentions ? value.replace("@", "@\u200B") : value; }
    private void warn(Throwable error) {
        plugin.getDiagnostics().recordIntegrationFailure("DiscordSRV", error);
        if (warned.compareAndSet(false, true)) plugin.getLogger().warning("DiscordSRV forwarding failed. In-game chat is unaffected. "
                + error.getClass().getSimpleName() + "; check DiscordSRV connection/configuration and use /chat reload.");
    }
    @Override public void close() {
        if (closed) return;
        closed = true;
        outboundWorker.cancel();
        outbound.clear();
        try { DiscordSRV.api.unsubscribe(this); }
        catch (RuntimeException | LinkageError ex) { warn(ex); }
    }
}
