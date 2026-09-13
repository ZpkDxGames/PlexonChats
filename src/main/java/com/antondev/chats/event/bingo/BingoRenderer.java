package com.antondev.chats.event.bingo;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Compact participant-only Adventure renderer for one server-owned Bingo board. */
public final class BingoRenderer {
    private static final String SEPARATOR = "━━━━━━━━━━━━━━━━━━━━━━━━━━━━";
    private BingoRenderer() { }

    public static List<Component> render(UUID runId, BingoSession.Participant participant, Set<Integer> drawn,
                                         int drawCount, int lastDraw, BingoBoard.Win winningPattern) {
        ArrayList<Component> lines = new ArrayList<>();
        lines.add(Component.empty());
        lines.add(Component.text(SEPARATOR, NamedTextColor.DARK_GRAY));
        lines.add(Component.text("           YOUR BINGO CARD", NamedTextColor.GOLD, TextDecoration.BOLD));
        lines.add(Component.empty());
        lines.add(Component.text(" B      I      N      G      O", NamedTextColor.AQUA, TextDecoration.BOLD));
        Set<Integer> winningCells = winningPattern == null ? Set.of() : winningPattern.cells();
        BingoBoard board = participant.board();
        for (int row = 0; row < 5; row++) {
            Component line = Component.empty();
            for (int column = 0; column < 5; column++) {
                int cell = row * 5 + column;
                if (column > 0) line = line.append(Component.space());
                line = line.append(cell(runId, board, drawn, cell, winningCells.contains(cell)));
            }
            lines.add(line);
        }
        lines.add(Component.empty());
        lines.add(Component.text("Last draw: ", NamedTextColor.GRAY)
                .append(Component.text(lastDraw <= 0 ? "-" : label(lastDraw), NamedTextColor.WHITE)));
        lines.add(Component.text("Draws: ", NamedTextColor.GRAY)
                .append(Component.text(drawCount + " / 75", NamedTextColor.YELLOW)));
        lines.add(Component.text(SEPARATOR, NamedTextColor.DARK_GRAY));
        lines.add(Component.empty());
        return List.copyOf(lines);
    }

    public static Component openCardButton() {
        return Component.text("[OPEN CARD]", NamedTextColor.AQUA, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/chat events bingo card"))
                .hoverEvent(HoverEvent.showText(Component.text("Show your current Bingo card", NamedTextColor.GREEN)));
    }

    private static Component cell(UUID runId, BingoBoard board, Set<Integer> drawn, int index, boolean winning) {
        int number = board.numberAt(index);
        if (number == BingoBoard.FREE) {
            return Component.text("[★ ]", winning ? NamedTextColor.GOLD : NamedTextColor.AQUA)
                    .hoverEvent(HoverEvent.showText(Component.text("FREE center cell", NamedTextColor.AQUA)));
        }
        boolean marked = board.isMarked(index);
        boolean markable = drawn.contains(number);
        NamedTextColor color = winning ? NamedTextColor.GOLD : marked ? NamedTextColor.GREEN : markable ? NamedTextColor.YELLOW : NamedTextColor.GRAY;
        String text = String.format(Locale.ROOT, "[%02d]", number);
        String hover = winning ? "Winning cell" : marked ? "Marked" : markable ? "Click to mark " + label(number) : "Not drawn yet";
        Component component = Component.text(text, color).hoverEvent(HoverEvent.showText(Component.text(hover, color)));
        if (!marked && markable) {
            component = component.clickEvent(ClickEvent.runCommand("/chat events bingo mark " + runId + " " + index));
        }
        return component;
    }

    public static String label(int number) {
        if (number < 1 || number > 75) return Integer.toString(number);
        char column = "BINGO".charAt((number - 1) / 15);
        return column + "-" + number;
    }
}
