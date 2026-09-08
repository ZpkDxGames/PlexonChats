package com.antondev.chats.player;

import com.antondev.chats.ChatChannel;

public record PlayerPreferences(ChatChannel channel, boolean mentions, boolean tips, boolean privateMessages) {
    public static PlayerPreferences defaults() { return new PlayerPreferences(null, true, true, true); }
    public PlayerPreferences withChannel(ChatChannel value) { return new PlayerPreferences(value, mentions, tips, privateMessages); }
    public PlayerPreferences toggleMentions() { return new PlayerPreferences(channel, !mentions, tips, privateMessages); }
    public PlayerPreferences toggleTips() { return new PlayerPreferences(channel, mentions, !tips, privateMessages); }
    public PlayerPreferences togglePrivateMessages() { return new PlayerPreferences(channel, mentions, tips, !privateMessages); }
}
