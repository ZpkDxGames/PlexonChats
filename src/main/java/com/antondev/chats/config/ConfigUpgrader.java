package com.antondev.chats.config;

import com.antondev.chats.event.ChatEventValidation;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Adds new options without overwriting administrator-owned values or restoring deleted custom entries. */
public final class ConfigUpgrader {
    public static final int VERSION = 9;
    private static final Set<String> USER_COLLECTIONS = Set.of(
            "gui.items", "gui.admin.items", "gui.events.items", "gui.creator.items", "auto-messages.groups",
            "chat-events.events");

    private ConfigUpgrader() {}

    public static boolean upgrade(YamlConfiguration current, YamlConfiguration defaults) {
        int previousVersion = current.getInt("config-version", 1);
        boolean upgraded = previousVersion < VERSION;
        if (upgraded) {
            if (previousVersion < 7) migrateBingoV7(current);
            if (previousVersion < 8) migrateDiscordV8(current);
            merge(current, defaults, "");
            if (previousVersion < 9) migratePresentationV9(current);
            // Preserve administrator-owned GUI collections. Add only cross-page entry points that are known-safe.
            copySectionIfMissing(current, defaults, "gui.admin.items.chat-events");
            current.set("config-version", VERSION);
        }
        ConfigurationSection chatEvents = current.getConfigurationSection("chat-events");
        if (chatEvents != null) ChatEventValidation.validate(chatEvents);
        return upgraded;
    }

    /** v7 replaces participant-specific Bingo with one shared, automatically marked, claim-based board. */
    private static void migrateBingoV7(YamlConfiguration current) {
        String root = "chat-events.bingo";
        if (current.isConfigurationSection(root)) {
            Set<String> legacyPatterns = new HashSet<>();
            for (String value : current.getStringList(root + ".win-patterns")) legacyPatterns.add(value.toUpperCase(Locale.ROOT));
            if (!legacyPatterns.isEmpty()) {
                current.set(root + ".winning.horizontal", legacyPatterns.contains("ROW") || legacyPatterns.contains("HORIZONTAL"));
                current.set(root + ".winning.vertical", legacyPatterns.contains("COLUMN") || legacyPatterns.contains("VERTICAL"));
                current.set(root + ".winning.diagonal", legacyPatterns.contains("DIAGONAL"));
                current.set(root + ".winning.full-house", legacyPatterns.contains("FULL_HOUSE"));
            }
            for (String obsolete : Set.of(
                    "join", "join-seconds", "min-participants", "board", "free-center", "draw-interval-seconds",
                    "draw.redraw-board-each-draw", "timeout-seconds", "win-patterns")) {
                current.set(root + "." + obsolete, null);
            }
            for (String obsoleteMessage : Set.of("join-open", "joined", "marked", "invalid-mark", "not-participant")) {
                current.set(root + ".messages." + obsoleteMessage, null);
            }
        }

        ConfigurationSection events = current.getConfigurationSection("chat-events.events");
        if (events == null) return;
        for (String id : events.getKeys(false)) {
            ConfigurationSection event = events.getConfigurationSection(id);
            if (event == null || !event.getString("type", "TYPE").equalsIgnoreCase("BINGO")) continue;
            ConfigurationSection legacyBingo = event.getConfigurationSection("bingo");
            if (!event.contains("duration-seconds") && legacyBingo != null && legacyBingo.contains("timeout-seconds")) {
                event.set("duration-seconds", legacyBingo.getInt("timeout-seconds", 300));
            }
            // Event identity, weight, cooldown, reward, minimum-online and other administrator values remain untouched.
            event.set("bingo", null);
        }
    }

    /** v8 generalizes the v3.5 Bingo-only webhook into one Chat Events Discord presentation layer. */
    private static void migrateDiscordV8(YamlConfiguration current) {
        String legacy = "chat-events.bingo.discord";
        if (!current.isConfigurationSection(legacy)) return;

        boolean legacyEnabled = current.getBoolean(legacy + ".enabled", false);
        String legacyUrl = current.getString(legacy + ".webhook-url", "");
        String legacyUsername = current.getString(legacy + ".username", "PlexonChats Bingo");
        boolean sendDraws = current.getBoolean(legacy + ".send-draws", true);
        boolean sendWin = current.getBoolean(legacy + ".send-win", true);

        if (!current.contains("chat-events.discord.enabled")) current.set("chat-events.discord.enabled", legacyEnabled);
        if (!current.contains("chat-events.discord.transport")) {
            current.set("chat-events.discord.transport", legacyEnabled && !legacyUrl.isBlank() ? "WEBHOOK" : "AUTO");
        }
        if (!current.contains("chat-events.discord.participation-mode")) current.set("chat-events.discord.participation-mode", "DISPLAY_ONLY");
        if (!current.contains("chat-events.discord.webhook.enabled")) current.set("chat-events.discord.webhook.enabled", legacyEnabled && !legacyUrl.isBlank());
        // Preserve an administrator-owned secret exactly while moving it to the generalized schema.
        if (!current.contains("chat-events.discord.webhook.url") && !legacyUrl.isBlank()) current.set("chat-events.discord.webhook.url", legacyUrl);
        if (!current.contains("chat-events.discord.webhook.username")) current.set("chat-events.discord.webhook.username", legacyUsername);
        if (!current.contains("chat-events.discord.events.bingo.enabled")) current.set("chat-events.discord.events.bingo.enabled", legacyEnabled);
        if (!current.contains("chat-events.discord.events.bingo.update-on-draw")) current.set("chat-events.discord.events.bingo.update-on-draw", sendDraws);
        if (!current.contains("chat-events.discord.events.bingo.announce-winner")) current.set("chat-events.discord.events.bingo.announce-winner", sendWin);

        // The secret now has one authoritative location. Do not leave a second stale webhook copy behind.
        current.set(legacy, null);
    }

    /**
     * v9 refreshes only known stock v8 Chat Event presentation text. Exact administrator-owned templates remain untouched.
     * The new cards remove repeated labels, use compact state symbols, and add hover help without changing gameplay state.
     */
    private static void migratePresentationV9(YamlConfiguration current) {
        replaceListIfExact(current, "chat-events.presentation.start", List.of(
                "{separator}",
                "<gradient:#55ffff:#1597ff><bold>CHAT EVENT</bold></gradient> <dark_gray>•</dark_gray> <aqua>{event_name}</aqua>",
                "<gray>{description}</gray>",
                "",
                "<white>{prompt}</white>",
                "",
                "<gray>Reward:</gray> <gold>{reward}</gold>",
                "<gray>Time:</gray> <yellow>{duration_seconds}s</yellow>",
                "{separator}"), List.of(
                "{separator}",
                "<gradient:#55ffff:#1597ff><bold>✦ {event_type}</bold></gradient> <hover:show_text:'<gray>First correct answer wins.</gray><newline><dark_gray>Answer in LOCAL or GLOBAL public chat.</dark_gray>'><aqua>ⓘ</aqua></hover>",
                "",
                "<white>{prompt}</white>",
                "",
                "<hover:show_text:'<gray>Event reward</gray>'><gold>◆ {reward}</gold></hover> <dark_gray>•</dark_gray> <hover:show_text:'<gray>Time limit</gray>'><yellow>⏱ {duration_seconds}s</yellow></hover>",
                "{separator}"));

        replaceListIfExact(current, "chat-events.presentation.winner", List.of(
                "{separator}",
                "<green><bold>CHAT EVENT COMPLETE</bold></green>",
                "<white>{winner}</white> <gray>won in</gray> <aqua>{elapsed}</aqua>",
                "<gray>Reward:</gray> <gold>{reward}</gold>",
                "<gray>Total wins:</gray> <yellow>{winner_total_wins}</yellow>",
                "<gray>{event_type} wins:</gray> <yellow>{winner_type_wins}</yellow>",
                "{separator}"), List.of(
                "{separator}",
                "<green><bold>✔ {event_type} COMPLETE</bold></green>",
                "",
                "<hover:show_text:'<gray>Chat Event wins</gray><newline><gray>Total:</gray> <yellow>{winner_total_wins}</yellow><newline><gray>{event_type}:</gray> <yellow>{winner_type_wins}</yellow>'><gold>♛</gold> <white>{winner}</white></hover> <gray>answered correctly in</gray> <aqua>{elapsed}</aqua>",
                "<gray>Answer:</gray> <white>{answer}</white>",
                "<hover:show_text:'<gray>Reward granted to the winner.</gray>'><gold>◆ {reward}</gold></hover>",
                "{separator}"));

        replaceListIfExact(current, "chat-events.presentation.timeout", List.of(
                "{separator}",
                "<yellow><bold>CHAT EVENT ENDED</bold></yellow>",
                "<gray>No winner this round.</gray>",
                "<gray>Answer:</gray> <white>{answer}</white>",
                "{separator}"), List.of(
                "{separator}",
                "<yellow><bold>⌛ {event_type} EXPIRED</bold></yellow>",
                "",
                "<gray>No correct answer</gray>",
                "<gray>Answer:</gray> <white>{answer}</white>",
                "{separator}"));

        replaceListIfExact(current, "chat-events.presentation.cancelled", List.of(
                "{separator}",
                "<gray><bold>CHAT EVENT CANCELLED</bold></gray>",
                "<gray>No reward or win statistic was issued.</gray>",
                "{separator}"), List.of(
                "{separator}",
                "<red><bold>✕ {event_type} CANCELLED</bold></red>",
                "",
                "<gray>Event cancelled by staff.</gray>",
                "{separator}"));

        replaceListIfExact(current, "chat-events.bingo.messages.start", List.of(
                "{separator}",
                "<gold><bold>BINGO</bold></gold> <gray>Watch the shared board and claim the first completed pattern.</gray>",
                "<gray>Use <white>/bingo claim</white> or type <white>bingo</white> in public chat.</gray>",
                "<gray>First call in:</gray> <yellow>5s</yellow>",
                "{separator}"), List.of(
                "{separator}",
                "<gold><bold>✦ BINGO</bold></gold> <hover:show_text:'<gray>Complete one enabled pattern and claim it first.</gray><newline><gray>Use /bingo claim or type bingo in public chat.</gray>'><aqua>ⓘ</aqua></hover>",
                "<gray>Shared board <dark_gray>•</dark_gray> automatic marking <dark_gray>•</dark_gray> no FREE center</gray>",
                "<hover:show_text:'<gray>Event reward</gray>'><gold>◆ {reward}</gold></hover> <dark_gray>•</dark_gray> <hover:show_text:'<gray>Time limit</gray>'><yellow>⏱ {duration_seconds}s</yellow></hover> <dark_gray>•</dark_gray> <aqua>First call {next_draw}</aqua>",
                "{separator}"));
        replaceListIfExact(current, "chat-events.bingo.messages.draw", List.of(
                "<gold>[BINGO]</gold> <gray>Call</gray> <yellow>#{draw_count}</yellow><gray>:</gray> <white>{drawn_number}</white>"), List.of(
                "<gold>◈ BINGO</gold> <dark_gray>•</dark_gray> <gray>Call</gray> <yellow>#{draw_count}</yellow> <dark_gray>›</dark_gray> <white>{drawn_number}</white>"));
        replaceListIfExact(current, "chat-events.bingo.messages.invalid-claim", List.of(
                "<yellow>[BINGO] No enabled winning pattern is complete yet.</yellow>"), List.of(
                "<yellow>✦ Not yet.</yellow> <gray>No winning pattern is complete.</gray> <hover:show_text:'<gray>Keep watching the shared board; marks are automatic.</gray>'><aqua>ⓘ</aqua></hover>"));

        replaceStringIfExact(current, "chat-events.events.type-rush.prompt",
                "<gray>Type <yellow>\"{value}\"</yellow> before anyone else!</gray>",
                "<gray>Type:</gray> <yellow>\"{value}\"</yellow>");
        replaceStringIfExact(current, "chat-events.events.unscramble.prompt",
                "<gray>Unscramble <aqua>{scrambled}</aqua>!</gray>",
                "<gray>Rearrange:</gray> <aqua>{scrambled}</aqua>");
        replaceStringIfExact(current, "chat-events.events.math-normal.prompt",
                "<gray>What's <aqua>{expression}</aqua>?</gray>",
                "<gray>Solve:</gray> <aqua>{expression}</aqua>");
        replaceStringIfExact(current, "chat-events.events.math-hard.prompt",
                "<gray>Solve <red>{expression}</red>!</gray>",
                "<gray>Solve:</gray> <red>{expression}</red>");
        replaceStringIfExact(current, "chat-events.events.reverse.prompt",
                "<gray>Reverse <aqua>{value}</aqua>!</gray>",
                "<gray>Text:</gray> <aqua>{value}</aqua>");
    }

    private static void replaceListIfExact(YamlConfiguration current, String path, List<String> expected, List<String> replacement) {
        if (!current.contains(path)) return;
        List<String> actual = current.isString(path) ? List.of(current.getString(path, "")) : current.getStringList(path);
        if (actual.equals(expected)) current.set(path, replacement);
    }

    private static void replaceStringIfExact(YamlConfiguration current, String path, String expected, String replacement) {
        if (expected.equals(current.getString(path))) current.set(path, replacement);
    }

    private static void merge(YamlConfiguration current, ConfigurationSection defaults, String parent) {
        for (String key : defaults.getKeys(false)) {
            String path = parent.isEmpty() ? key : parent + "." + key;
            ConfigurationSection child = defaults.getConfigurationSection(key);
            if (child != null) {
                if (USER_COLLECTIONS.contains(path)) {
                    if (!current.contains(path)) current.createSection(path);
                    continue;
                }
                if (current.contains(path) && !current.isConfigurationSection(path)) continue;
                if (!current.contains(path)) current.createSection(path);
                merge(current, child, path);
            } else if (!current.contains(path)) {
                current.set(path, defaults.get(key));
                current.setComments(path, defaults.getComments(key));
            }
        }
    }

    private static void copySectionIfMissing(YamlConfiguration current, ConfigurationSection defaults, String path) {
        if (current.contains(path)) return;
        ConfigurationSection source = defaults.getConfigurationSection(path);
        if (source == null) return;
        current.createSection(path);
        copyValues(current, source, path);
    }

    private static void copyValues(YamlConfiguration current, ConfigurationSection source, String targetPath) {
        for (String key : source.getKeys(false)) {
            String path = targetPath + "." + key;
            ConfigurationSection child = source.getConfigurationSection(key);
            if (child != null) {
                current.createSection(path);
                copyValues(current, child, path);
            } else {
                current.set(path, source.get(key));
                current.setComments(path, source.getComments(key));
            }
        }
    }
}
