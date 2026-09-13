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
    private static final char VERTICAL = '│';
    private static final char HORIZONTAL = '─';

    private BingoRenderer() { }

    public static List<Component> render(BingoRun run, BingoParticipant participant, BingoBoard.Win winningPattern) {
        if (run == null || participant == null) return List.of();
        ChatEventConfig.BingoRender settings = run.definition().bingo().render();
        Key font = Key.key(settings.font());
        Set<Integer> winning = winningPattern == null ? Set.of() : winningPattern.cells();
        ArrayList<Component> lines = new ArrayList<>();
        lines.add(Component.text("✦ BINGO — YOUR CARD", NamedTextColor.GOLD, TextDecoration.BOLD));
        if (settings.style() == ChatEventConfig.BingoRenderStyle.TABLE) {
            lines.addAll(renderTable(run, participant, winning, settings, font));
        } else {
            lines.addAll(renderCompact(run, participant, winning, settings, font));
        }
        if (settings.showLastCall() || settings.showDrawCount()) {
            Component footer = Component.text(" ".repeat(settings.leftPadding()), NamedTextColor.GRAY).font(font);
            if (settings.showLastCall()) {
                footer = footer.append(Component.text("Last: ", NamedTextColor.GRAY).font(font))
                        .append(Component.text(run.lastDraw() <= 0 ? "-" : label(run.lastDraw()), NamedTextColor.WHITE).font(font));
            }
            if (settings.showLastCall() && settings.showDrawCount()) footer = footer.append(Component.text("    ", NamedTextColor.GRAY).font(font));
            if (settings.showDrawCount()) footer = footer.append(Component.text("Draws: " + run.drawCount() + "/75", NamedTextColor.GRAY).font(font));
            lines.add(footer);
        }
        lines.add(Component.text(" ".repeat(settings.leftPadding()))
                .append(Component.text("[ CALL BINGO ]", NamedTextColor.AQUA, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/bingo claim " + run.runId()))
                        .hoverEvent(HoverEvent.showText(Component.text("Claim Bingo using your manual marks", NamedTextColor.GRAY)))));
        return List.copyOf(lines);
    }

    public static List<String> plainLines(BingoParticipant participant, ChatEventConfig.BingoRender settings) {
        if (participant == null) return List.of();
        if (settings.style() == ChatEventConfig.BingoRenderStyle.TABLE) return tablePlainLines(participant, settings);
        return compactPlainLines(participant, settings);
    }

    private static List<Component> renderTable(BingoRun run, BingoParticipant participant, Set<Integer> winning,
                                               ChatEventConfig.BingoRender settings, Key font) {
        ArrayList<Component> lines = new ArrayList<>();
        if (settings.showBorder()) lines.add(border(settings, '┌', '┬', '┐', font));
        lines.add(tableHeader(settings, font));
        if (settings.showBorder()) lines.add(border(settings, '├', '┼', '┤', font));
        for (int row = 0; row < 5; row++) lines.add(tableRow(run, participant, row, winning, settings, font));
        if (settings.showBorder()) lines.add(border(settings, '└', '┴', '┘', font));
        return List.copyOf(lines);
    }

    private static List<Component> renderCompact(BingoRun run, BingoParticipant participant, Set<Integer> winning,
                                                 ChatEventConfig.BingoRender settings, Key font) {
        ArrayList<Component> lines = new ArrayList<>(6);
        lines.add(compactHeader(settings, font));
        for (int row = 0; row < 5; row++) lines.add(compactRow(run, participant, row, winning, settings, font));
        return List.copyOf(lines);
    }

    private static Component border(ChatEventConfig.BingoRender settings, char left, char join, char right, Key font) {
        String segment = String.valueOf(HORIZONTAL).repeat(settings.cellWidth());
        StringBuilder text = new StringBuilder(" ".repeat(settings.leftPadding())).append(left);
        for (int column = 0; column < 5; column++) {
            text.append(segment);
            text.append(column == 4 ? right : join);
        }
        return gridText(text.toString(), NamedTextColor.DARK_GRAY, font);
    }

    private static Component tableHeader(ChatEventConfig.BingoRender settings, Key font) {
        Component line = Component.text(" ".repeat(settings.leftPadding()), NamedTextColor.DARK_GRAY).font(font);
        boolean outer = settings.showBorder();
        if (outer) line = line.append(separator(font));
        for (int column = 0; column < 5; column++) {
            line = line.append(gridText(center(String.valueOf(HEADERS.charAt(column)), settings.cellWidth()), NamedTextColor.AQUA, font));
            if (column < 4 || outer) line = line.append(separator(font));
        }
        return line;
    }

    private static Component tableRow(BingoRun run, BingoParticipant participant, int row, Set<Integer> winning,
                                      ChatEventConfig.BingoRender settings, Key font) {
        Component line = Component.text(" ".repeat(settings.leftPadding()), NamedTextColor.DARK_GRAY).font(font);
        boolean outer = settings.showBorder();
        if (outer) line = line.append(separator(font));
        for (int column = 0; column < 5; column++) {
            int cell = row * 5 + column;
            line = line.append(numberCell(run, participant, cell, winning, settings, font));
            if (column < 4 || outer) line = line.append(separator(font));
        }
        return line;
    }

    private static Component compactHeader(ChatEventConfig.BingoRender settings, Key font) {
        Component line = Component.text(" ".repeat(settings.leftPadding())).font(font);
        for (int column = 0; column < 5; column++) {
            line = line.append(gridText(center(String.valueOf(HEADERS.charAt(column)), settings.cellWidth()), NamedTextColor.AQUA, font));
            if (column < 4) line = line.append(gridText(" ".repeat(settings.columnGap()), NamedTextColor.DARK_GRAY, font));
        }
        return line;
    }

    private static Component compactRow(BingoRun run, BingoParticipant participant, int row, Set<Integer> winning,
                                        ChatEventConfig.BingoRender settings, Key font) {
        Component line = Component.text(" ".repeat(settings.leftPadding())).font(font);
        for (int column = 0; column < 5; column++) {
            int cell = row * 5 + column;
            line = line.append(numberCell(run, participant, cell, winning, settings, font));
            if (column < 4) line = line.append(gridText(" ".repeat(settings.columnGap()), NamedTextColor.DARK_GRAY, font));
        }
        return line;
    }

    private static Component numberCell(BingoRun run, BingoParticipant participant, int cell, Set<Integer> winning,
                                        ChatEventConfig.BingoRender settings, Key font) {
        int number = participant.board().numberAt(cell);
        boolean marked = participant.isMarked(cell);
        boolean winner = winning.contains(cell);
        NamedTextColor color = winner ? NamedTextColor.YELLOW : marked ? NamedTextColor.GREEN : NamedTextColor.GRAY;
        Component value = gridText(center(two(number), settings.cellWidth()), color, font);
        if (winner) value = value.decorate(TextDecoration.UNDERLINED);
        String label = label(number);
        return value.clickEvent(ClickEvent.runCommand("/bingo mark " + run.runId() + " " + number))
                .hoverEvent(HoverEvent.showText(Component.text(marked ? label + " is already marked" : "Click to mark " + label, NamedTextColor.GRAY)));
    }

    private static Component separator(Key font) {
        return gridText(String.valueOf(VERTICAL), NamedTextColor.DARK_GRAY, font);
    }

    private static Component gridText(String text, NamedTextColor color, Key font) {
        return Component.text(text, color)
                .font(font)
                .decoration(TextDecoration.BOLD, false)
                .decoration(TextDecoration.ITALIC, false);
    }

    private static List<String> tablePlainLines(BingoParticipant participant, ChatEventConfig.BingoRender settings) {
        ArrayList<String> lines = new ArrayList<>();
        if (settings.showBorder()) lines.add(borderPlain(settings, '┌', '┬', '┐'));
        lines.add(tableHeaderPlain(settings));
        if (settings.showBorder()) lines.add(borderPlain(settings, '├', '┼', '┤'));
        for (int row = 0; row < 5; row++) lines.add(tableNumberRowPlain(participant, row, settings));
        if (settings.showBorder()) lines.add(borderPlain(settings, '└', '┴', '┘'));
        return List.copyOf(lines);
    }

    private static List<String> compactPlainLines(BingoParticipant participant, ChatEventConfig.BingoRender settings) {
        ArrayList<String> lines = new ArrayList<>(6);
        lines.add(compactHeaderPlain(settings));
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

    private static String borderPlain(ChatEventConfig.BingoRender settings, char left, char join, char right) {
        String segment = String.valueOf(HORIZONTAL).repeat(settings.cellWidth());
        StringBuilder out = new StringBuilder(" ".repeat(settings.leftPadding())).append(left);
        for (int column = 0; column < 5; column++) out.append(segment).append(column == 4 ? right : join);
        return out.toString();
    }

    private static String tableHeaderPlain(ChatEventConfig.BingoRender settings) {
        StringBuilder out = new StringBuilder(" ".repeat(settings.leftPadding()));
        if (settings.showBorder()) out.append(VERTICAL);
        for (int column = 0; column < 5; column++) {
            out.append(center(String.valueOf(HEADERS.charAt(column)), settings.cellWidth()));
            if (column < 4 || settings.showBorder()) out.append(VERTICAL);
        }
        return out.toString();
    }

    private static String tableNumberRowPlain(BingoParticipant participant, int row, ChatEventConfig.BingoRender settings) {
        StringBuilder out = new StringBuilder(" ".repeat(settings.leftPadding()));
        if (settings.showBorder()) out.append(VERTICAL);
        for (int column = 0; column < 5; column++) {
            int cell = row * 5 + column;
            out.append(center(two(participant.board().numberAt(cell)), settings.cellWidth()));
            if (column < 4 || settings.showBorder()) out.append(VERTICAL);
        }
        return out.toString();
    }

    private static String compactHeaderPlain(ChatEventConfig.BingoRender settings) {
        StringBuilder out = new StringBuilder(" ".repeat(settings.leftPadding()));
        for (int column = 0; column < 5; column++) {
            out.append(center(String.valueOf(HEADERS.charAt(column)), settings.cellWidth()));
            if (column < 4) out.append(" ".repeat(settings.columnGap()));
        }
        return out.toString();
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
