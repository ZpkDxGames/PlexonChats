package com.antondev.chats.event.discord;

/** Stable identity of one Discord message owned by a Chat Event run. */
public record DiscordEventMessageRef(String channelId, String messageId) {
    public DiscordEventMessageRef {
        channelId = channelId == null ? "" : channelId;
        if (messageId == null || messageId.isBlank()) throw new IllegalArgumentException("Discord message id must not be blank");
    }
}
