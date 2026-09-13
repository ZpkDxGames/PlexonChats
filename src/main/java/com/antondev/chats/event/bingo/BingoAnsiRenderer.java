package com.antondev.chats.event.bingo;

/** Legacy diagnostic renderer retained for compatibility. It never exposes participant-owned cards. */
public final class BingoAnsiRenderer {
    private BingoAnsiRenderer() { }

    public static String render(BingoRun run) {
        String last = run.lastDraw() <= 0 ? "-" : BingoRenderer.label(run.lastDraw());
        return "```ansi\nBINGO " + run.phase().name() + "\nPlayers: " + run.participantCount()
                + "\nLast call: " + last + "\nDraws: " + run.drawCount() + "/75\n```";
    }
}
