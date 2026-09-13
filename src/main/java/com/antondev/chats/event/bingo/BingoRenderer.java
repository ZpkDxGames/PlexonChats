package com.antondev.chats.event.bingo;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Adventure renderer for the one shared authoritative Bingo board. */
public final class BingoRenderer {
    private BingoRenderer() { }

    public static List<Component> render(BingoRun run, BingoBoard.Win winningPattern) {
        ArrayList<Component> lines = new ArrayList<>();
        lines.add(Component.text("BINGO • LIVE BOARD", NamedTextColor.GOLD, TextDecoration.BOLD));
        lines.add(Component.text("# |  B |  I |  N |  G |  O", NamedTextColor.AQUA, TextDecoration.BOLD));
        Set<Integer> drawn = run.drawnNumbers();
        Set<Integer> winning = winningPattern == null ? Set.of() : winningPattern.cells();
        for (int row = 0; row < 5; row++) {
            Component line = Component.text((row + 1) + " | ", NamedTextColor.DARK_GRAY);
            for (int column = 0; column < 5; column++) {
                int cell = row * 5 + column;
                line = line.append(cell(run.board(), drawn, cell, winning.contains(cell)));
                if (column < 4) line = line.append(Component.text(" | ", NamedTextColor.DARK_GRAY));
            }
            lines.add(line);
        }
        lines.add(Component.text("Last call: ", NamedTextColor.GRAY)
                .append(Component.text(run.lastDraw() <= 0 ? "-" : label(run.lastDraw()), NamedTextColor.WHITE))
                .append(Component.text(" • Draws: ", NamedTextColor.GRAY))
                .append(Component.text(run.drawCount() + "/75", NamedTextColor.YELLOW)));
        return List.copyOf(lines);
    }

    public static List<String> plainLines(BingoRun run) {
        ArrayList<String> lines = new ArrayList<>();
        lines.add("# |  B |  I |  N |  G |  O");
        Set<Integer> drawn = run.drawnNumbers();
        for (int row = 0; row < 5; row++) {
            StringBuilder line = new StringBuilder().append(row + 1).append(" | ");
            for (int column = 0; column < 5; column++) {
                int cell = row * 5 + column;
                int value = run.board().numberAt(cell);
                line.append(drawn.contains(value) ? "[" + two(value) + "]" : " " + two(value) + " ");
                if (column < 4) line.append(" | ");
            }
            lines.add(line.toString());
        }
        return List.copyOf(lines);
    }

    private static Component cell(BingoBoard board, Set<Integer> drawn, int cell, boolean winning) {
        int number = board.numberAt(cell);
        if (winning) return Component.text("[" + two(number) + "]", NamedTextColor.GOLD, TextDecoration.BOLD);
        if (drawn.contains(number)) return Component.text("[" + two(number) + "]", NamedTextColor.GREEN, TextDecoration.BOLD);
        return Component.text(" " + two(number) + " ", NamedTextColor.GRAY);
    }

    private static String two(int number) { return String.format(Locale.ROOT, "%02d", number); }

    public static String label(int number) {
        if (number < 1 || number > 75) return Integer.toString(number);
        char column = "BINGO".charAt((number - 1) / 15);
        return column + "-" + number;
    }
}
