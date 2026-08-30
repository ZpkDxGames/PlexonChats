package com.antondev.chats.gui;

public enum GuiAction {
    LOCAL, GLOBAL, TOGGLE_MENTIONS, TOGGLE_TIPS, TOGGLE_PRIVATE_MESSAGES,
    OPEN_ADMIN, OPEN_CREATOR, OPEN_MAIN, RELOAD, TOGGLE_AUTO_MESSAGES,
    PREVIEW_FORMAT, PLAYER_COMMAND, MESSAGE, LINK, CLOSE, NONE;

    public String requiredPermission() {
        return switch (this) {
            case LOCAL -> "plexonchats.local";
            case GLOBAL -> "plexonchats.global";
            case OPEN_ADMIN, PREVIEW_FORMAT -> "plexonchats.manage";
            case RELOAD -> "plexonchats.reload";
            case TOGGLE_AUTO_MESSAGES -> "plexonchats.automessages";
            default -> "";
        };
    }
}
