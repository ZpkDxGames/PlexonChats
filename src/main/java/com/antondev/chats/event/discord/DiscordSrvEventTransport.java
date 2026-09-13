package com.antondev.chats.event.discord;

import com.antondev.chats.PlexonChats;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.EmbedBuilder;
import github.scarsz.discordsrv.dependencies.jda.api.JDA;
import github.scarsz.discordsrv.dependencies.jda.api.entities.MessageEmbed;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/** DiscordSRV 1.30.5/JDA transport. Every Discord operation is queued asynchronously by JDA. */
public final class DiscordSrvEventTransport implements DiscordEventTransport {
    private final PlexonChats plugin;
    private final DiscordEventSettings settings;
    private volatile boolean closed;

    public DiscordSrvEventTransport(PlexonChats plugin, DiscordEventSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    @Override public String name() { return "DiscordSRV"; }

    @Override public String status() {
        if (closed) return "CLOSED";
        try {
            DiscordSRV discord = DiscordSRV.getPlugin();
            if (discord == null || !discord.isEnabled()) return "NOT_INSTALLED";
            JDA jda = discord.getJda();
            if (!DiscordSRV.isReady || jda == null || jda.getStatus() != JDA.Status.CONNECTED) return "WAITING_FOR_DISCORD";
            return resolveChannel() == null ? "CHANNEL_NOT_CONFIGURED" : "READY";
        } catch (RuntimeException | LinkageError failure) {
            return "INCOMPATIBLE";
        }
    }

    @Override public boolean channelConfigured() {
        if (closed) return false;
        try { return resolveChannel() != null; }
        catch (RuntimeException | LinkageError ignored) { return false; }
    }

    @Override public CompletableFuture<DiscordEventMessageRef> create(DiscordEventEmbed embed) {
        CompletableFuture<DiscordEventMessageRef> future = new CompletableFuture<>();
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("DiscordSRV event transport is closed"));
        try {
            TextChannel channel = resolveChannel();
            if (channel == null) return CompletableFuture.failedFuture(new IllegalStateException("Discord event channel is not configured"));
            channel.sendMessageEmbeds(toJda(embed)).queue(
                    message -> future.complete(new DiscordEventMessageRef(channel.getId(), message.getId())),
                    future::completeExceptionally);
        } catch (RuntimeException | LinkageError failure) {
            future.completeExceptionally(failure);
        }
        return future;
    }

    @Override public CompletableFuture<Void> edit(DiscordEventMessageRef ref, DiscordEventEmbed embed) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("DiscordSRV event transport is closed"));
        try {
            TextChannel channel = resolveChannel(ref.channelId());
            if (channel == null) return CompletableFuture.failedFuture(new IllegalStateException("Discord event channel is unavailable"));
            channel.retrieveMessageById(ref.messageId()).queue(
                    message -> message.editMessageEmbeds(toJda(embed)).queue(
                            ignored -> future.complete(null), future::completeExceptionally),
                    future::completeExceptionally);
        } catch (RuntimeException | LinkageError failure) {
            future.completeExceptionally(failure);
        }
        return future;
    }

    private TextChannel resolveChannel() {
        return resolveChannel(settings.channelId());
    }

    private TextChannel resolveChannel(String explicitChannelId) {
        DiscordSRV discord = DiscordSRV.getPlugin();
        if (discord == null || discord.getJda() == null) return null;
        if (explicitChannelId != null && !explicitChannelId.isBlank()) {
            try { return discord.getJda().getTextChannelById(explicitChannelId); }
            catch (IllegalArgumentException ignored) { return null; }
        }
        String gameChannel = plugin.getConfigManager().string("integrations.discordsrv.game-channel", "global");
        return discord.getDestinationTextChannelForGameChannelName(gameChannel);
    }

    private static MessageEmbed toJda(DiscordEventEmbed embed) {
        EmbedBuilder builder = new EmbedBuilder();
        if (!embed.title().isBlank()) builder.setTitle(embed.title());
        if (!embed.description().isBlank()) builder.setDescription(embed.description());
        builder.setColor(embed.color());
        for (DiscordEventEmbed.Field field : embed.fields()) builder.addField(field.name(), field.value(), field.inline());
        if (!embed.footer().isBlank()) builder.setFooter(embed.footer());
        if (embed.timestamp()) builder.setTimestamp(Instant.now());
        if (!embed.thumbnailUrl().isBlank()) builder.setThumbnail(embed.thumbnailUrl());
        return builder.build();
    }

    @Override public void close() { closed = true; }
}
