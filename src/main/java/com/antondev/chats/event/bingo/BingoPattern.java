package com.antondev.chats.event.bingo;

/** Winning families supported by the shared-board Bingo model. */
public enum BingoPattern {
    ROW,
    COLUMN,
    DIAGONAL,
    FULL_HOUSE;

    public String displayName() {
        return switch (this) {
            case ROW -> "Horizontal";
            case COLUMN -> "Vertical";
            case DIAGONAL -> "Diagonal";
            case FULL_HOUSE -> "Full House";
        };
    }
}
