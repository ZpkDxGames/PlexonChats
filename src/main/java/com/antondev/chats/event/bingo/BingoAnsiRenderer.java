package com.antondev.chats.event.bingo;

import java.util.Locale;
import java.util.Set;

/** Discord monospaced ANSI renderer. Minecraft formatting is never reused here. */
public final class BingoAnsiRenderer {
    private static final String GREEN = "\u001B[1;32m";
    private static final String RESET = "\u001B[0m";

    private BingoAnsiRenderer() { }

    public static String render(BingoRun run) {
        StringBuilder out = new StringBuilder("```ansi\n");
        out.append("# |  B |  I |  N |  G |  O\n");
        Set<Integer> drawn = run.drawnNumbers();
        for (int row = 0; row < 5; row++) {
            out.append(row + 1).append(" | ");
            for (int column = 0; column < 5; column++) {
                int value = run.board().numberAt(row * 5 + column);
                if (drawn.contains(value)) out.append(GREEN).append('[').append(two(value)).append(']').append(RESET);
                else out.append(' ').append(two(value)).append(' ');
                if (column < 4) out.append(" | ");
            }
            out.append('\n');
        }
        return out.append("```").toString();
    }

    private static String two(int value) { return String.format(Locale.ROOT, "%02d", value); }
}
