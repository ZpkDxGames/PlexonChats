package com.antondev.chats.event.bingo;

import com.antondev.chats.event.ChatEventConfig;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Participant-owned fixed-width Adventure Bingo renderer. Drawn and marked state are deliberately separate. */
public final class BingoRenderer {
    private static final String HEADERS = "BINGO";
    private BingoRenderer() { }

    public static List<Component> render(BingoRun run, BingoParticipant participant, BingoBoard.Win winningPattern) {
        if (run == null || participant == null) return List.of();
        ChatEventConfig.BingoRender settings = run.definition().bingo().render();
        Key font = Key.key(settings.font());
        ArrayList<Component> lines = new ArrayList<>();
        lines.add(Component.text("✦ BINGO • YOUR CARD", NamedTextColor.GOLD, TextDecoration.BOLD));
        lines.add(header(settings).font(font));
        Set<Integer> winning = winningPattern == null ? Set.of() : winningPattern.cells();
        for (int row = 0; row < 5; row++) lines.add(row(run, participant, row, winning, settings).font(font));
        Component footer = Component.text(" ".repeat(settings.leftPadding()) + "◈ Last ", NamedTextColor.GRAY)
                .append(Component.text(run.lastDraw() <= 0 ? "-" : label(run.lastDraw()), NamedTextColor.WHITE));
        if (settings.showDrawCount()) footer = footer.append(Component.text("   •   " + run.drawCount() + "/75", NamedTextColor.GRAY));
        if (settings.showLastCall() || settings.showDrawCount()) lines.add(footer.font(font));
        lines.add(Component.text(" ".repeat(settings.leftPadding()))
                .append(Component.text("[ CALL BINGO ]", NamedTextColor.AQUA, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/bingo claim " + run.runId()))
                        .hoverEvent(HoverEvent.showText(Component.text("Claim Bingo using your manual marks", NamedTextColor.GRAY)))));
        return List.copyOf(lines);
    }

    public static List<String> plainLines(BingoParticipant participant, ChatEventConfig.BingoRender settings) {
        if (participant == null) return List.of();
        ArrayList<String> lines = new ArrayList<>(6);
        lines.add(headerPlain(settings));
        for (int row = 0; row < 5; row++) {
            StringBuilder out = new StringBuilder(" ".repeat(settings.leftPadding()));
            for (int column = 0; column < 5; column++) {
                int cell = row * 5 + column;
                out.append(center(two(participant.board().numberAt(cell)), settings.cellWidth()));
                if (column < 4) out.append(" ".repeat(settings.columnGap()));
            }
            lines.add(out.toString());
        }
        return List.copyOf(lines);
    }

    private static Component header(ChatEventConfig.BingoRender settings) {
        Component line = Component.text(" ".repeat(settings.leftPadding()));
        for (int column = 0; column < 5; column++) {
            line = line.append(Component.text(center(String.valueOf(HEADERS.charAt(column)), settings.cellWidth()), NamedTextColor.AQUA, TextDecoration.BOLD));
            if (column < 4) line = line.append(Component.text(" ".repeat(settings.columnGap())));
        }
        return line;
    }

    private static String headerPlain(ChatEventConfig.BingoRender settings) {
        StringBuilder out = new StringBuilder(" ".repeat(settings.leftPadding()));
        for (int column = 0; column < 5; column++) {
            out.append(center(String.valueOf(HEADERS.charAt(column)), settings.cellWidth()));
            if (column < 4) out.append(" ".repeat(settings.columnGap()));
        }
        return out.toString();
    }

    private static Component row(BingoRun run, BingoParticipant participant, int row, Set<Integer> winning,
                                 ChatEventConfig.BingoRender settings) {
        Component line = Component.text(" ".repeat(settings.leftPadding()));
        for (int column = 0; column < 5; column++) {
            int cell = row * 5 + column;
            int number = participant.board().numberAt(cell);
            boolean marked = participant.isMarked(cell);
            Component value = Component.text(center(two(number), settings.cellWidth()), marked ? NamedTextColor.GREEN : NamedTextColor.GRAY);
            if (winning.contains(cell)) value = value.decorate(TextDecoration.BOLD, TextDecoration.UNDERLINED);
            String label = label(number);
            value = value.clickEvent(ClickEvent.runCommand("/bingo mark " + run.runId() + " " + number))
                    .hoverEvent(HoverEvent.showText(Component.text(marked ? label + " is already marked" : "Click to mark " + label, NamedTextColor.GRAY)));
            line = line.append(value);
            if (column < 4) line = line.append(Component.text(" ".repeat(settings.columnGap())));
        }
        return line;
    }

    public static Component viewCardButton() {
        return Component.text("[ VIEW CARD ]", NamedTextColor.AQUA, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/bingo"))
                .hoverEvent(HoverEvent.showText(Component.text("View your current Bingo card", NamedTextColor.GRAY)));
    }

    private static String center(String value, int width) {
        int safeWidth = Math.max(value.length(), width);
        int total = safeWidth - value.length();
        int left = total / 2;
        return " ".repeat(left) + value + " ".repeat(total - left);
    }

    private static String two(int number) { return String.format(Locale.ROOT, "%02d", number); }

    public static String label(int number) {
        if (number < 1 || number > 75) return Integer.toString(number);
        char column = HEADERS.charAt((number - 1) / 15);
        return column + "-" + number;
    }
}
