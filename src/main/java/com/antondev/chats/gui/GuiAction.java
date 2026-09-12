package com.antondev.chats.gui;

public enum GuiAction {
    LOCAL, GLOBAL, TOGGLE_MENTIONS, TOGGLE_TIPS, TOGGLE_PRIVATE_MESSAGES,
    OPEN_ADMIN, OPEN_CREATOR, OPEN_MAIN, OPEN_CHAT_EVENTS, RELOAD, TOGGLE_AUTO_MESSAGES,
    TOGGLE_CHAT_EVENTS, PAUSE_CHAT_EVENTS, RESUME_CHAT_EVENTS, START_RANDOM_CHAT_EVENT, STOP_CHAT_EVENT,
    PREVIEW_FORMAT, PLAYER_COMMAND, MESSAGE, LINK, CLOSE, NONE;

    public String requiredPermission() {
        return switch (this) {
            case LOCAL -> "plexonchats.local";
            case GLOBAL -> "plexonchats.global";
            case OPEN_ADMIN, PREVIEW_FORMAT -> "plexonchats.manage";
            case OPEN_CHAT_EVENTS, TOGGLE_CHAT_EVENTS, PAUSE_CHAT_EVENTS, RESUME_CHAT_EVENTS,
                    START_RANDOM_CHAT_EVENT, STOP_CHAT_EVENT -> "plexonchats.events.manage";
            case RELOAD -> "plexonchats.reload";
            case TOGGLE_AUTO_MESSAGES -> "plexonchats.automessages";
            default -> "";
        };
    }
}
