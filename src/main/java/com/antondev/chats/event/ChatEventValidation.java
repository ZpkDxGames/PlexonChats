package com.antondev.chats.event;

import com.antondev.chats.ChatChannel;
import com.antondev.chats.event.bingo.BingoPattern;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Strict validator used before a Chat Events configuration generation is published. */
public final class ChatEventValidation {
    private static final Set<String> KEY_TIERS = Set.of("BASIC", "RARE", "EPIC", "LEGENDARY");
    private ChatEventValidation() { }

    public static void validate(ConfigurationSection root) {
        if (root == null) throw invalid("chat-events", "must be a YAML section");
        for (String key : List.of("scheduler", "defaults", "presentation", "bingo", "reward-profiles", "events", "sounds", "messages")) section(root, key);

        long initial = root.getLong("scheduler.initial-delay-seconds", 180);
        long min = root.getLong("scheduler.min-interval-seconds", 600);
        long max = root.getLong("scheduler.max-interval-seconds", 1200);
        if (initial < 0 || initial > 604800) throw invalid("chat-events.scheduler.initial-delay-seconds", "must be between 0 and 604800");
        if (min < 1 || min > 604800) throw invalid("chat-events.scheduler.min-interval-seconds", "must be between 1 and 604800");
        if (max < 1 || max > 604800) throw invalid("chat-events.scheduler.max-interval-seconds", "must be between 1 and 604800");
        if (min > max) throw invalid("chat-events.scheduler.min-interval-seconds", "must be <= max-interval-seconds");
        int schedulerMinOnline = root.getInt("scheduler.min-online", 1);
        if (schedulerMinOnline < 0 || schedulerMinOnline > 10000) throw invalid("chat-events.scheduler.min-online", "must be between 0 and 10000");

        ConfigurationSection defaults = root.getConfigurationSection("defaults");
        int duration = defaults == null ? 30 : defaults.getInt("duration-seconds", 30);
        if (duration < 1 || duration > 3600) throw invalid("chat-events.defaults.duration-seconds", "must be between 1 and 3600");
        int defaultsMinOnline = defaults == null ? 1 : defaults.getInt("min-online", 1);
        if (defaultsMinOnline < 0 || defaultsMinOnline > 10000) throw invalid("chat-events.defaults.min-online", "must be between 0 and 10000");
        validateChannels(defaults, "accepted-channels", "chat-events.defaults.accepted-channels");
        validateMatching(defaults == null ? null : defaults.getConfigurationSection("matching"), "chat-events.defaults.matching");

        validatePresentation(root.getConfigurationSection("presentation"));
        validateBingo(root.getConfigurationSection("bingo"), "chat-events.bingo", true);
        Set<String> rewardIds = validateRewards(root.getConfigurationSection("reward-profiles"));
        validateEvents(root.getConfigurationSection("events"), rewardIds, duration, defaultsMinOnline);
        for (String key : List.of("start", "win", "timeout")) validateSound(root, "sounds." + key + ".sound");
        ConfigurationSection messages = root.getConfigurationSection("messages");
        if (messages != null) for (String key : messages.getKeys(false)) if (messages.isString(key)) validateMiniMessage("chat-events.messages." + key, messages.getString(key, ""));
    }

    private static void validatePresentation(ConfigurationSection presentation) {
        if (presentation == null) return;
        int before = presentation.getInt("blank-lines-before", 1);
        int after = presentation.getInt("blank-lines-after", 1);
        if (before < 0 || before > 3) throw invalid("chat-events.presentation.blank-lines-before", "must be between 0 and 3");
        if (after < 0 || after > 3) throw invalid("chat-events.presentation.blank-lines-after", "must be between 0 and 3");
        validateMiniMessage("chat-events.presentation.separator", presentation.getString("separator", ""));
        for (String key : List.of("start", "winner", "timeout", "cancelled")) validateLines(presentation, key, "chat-events.presentation." + key, true);
        ConfigurationSection typeNames = presentation.getConfigurationSection("type-names");
        if (typeNames != null) {
            for (String key : typeNames.getKeys(false)) {
                try { ChatEventEngine.Type.valueOf(key.toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException ex) { throw invalid("chat-events.presentation.type-names." + key, "uses an unknown event type"); }
                if (typeNames.getString(key, "").isBlank()) throw invalid("chat-events.presentation.type-names." + key, "must not be blank");
            }
        }
    }

    private static void validateBingo(ConfigurationSection bingo, String path, boolean requireMessages) {
        if (bingo == null) return;
        int join = bingo.contains("join-seconds") ? bingo.getInt("join-seconds") : bingo.getInt("join.duration-seconds", 15);
        int participants = bingo.contains("min-participants") ? bingo.getInt("min-participants") : bingo.getInt("join.min-participants", 2);
        int first = bingo.contains("draw-interval-seconds") ? 5 : bingo.getInt("draw.first-delay-seconds", 5);
        int interval = bingo.contains("draw-interval-seconds") ? bingo.getInt("draw-interval-seconds") : bingo.getInt("draw.interval-seconds", 5);
        int timeout = bingo.getInt("timeout-seconds", 300);
        range(join, 1, 120, path + ".join.duration-seconds");
        range(participants, 1, 1000, path + ".join.min-participants");
        range(first, 0, 300, path + ".draw.first-delay-seconds");
        range(interval, 1, 300, path + ".draw.interval-seconds");
        range(timeout, 10, 7200, path + ".timeout-seconds");
        if (timeout <= join) throw invalid(path + ".timeout-seconds", "must be greater than the join duration");
        String variant = bingo.getString("board.variant", "BINGO_75");
        if (!variant.equalsIgnoreCase("BINGO_75")) throw invalid(path + ".board.variant", "only BINGO_75 is supported in 3.4.0");
        List<String> patterns = bingo.getStringList("win-patterns");
        if (patterns.isEmpty() && path.equals("chat-events.bingo")) throw invalid(path + ".win-patterns", "must contain at least one pattern");
        for (String value : patterns) {
            try { BingoPattern.valueOf(value.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ex) { throw invalid(path + ".win-patterns", "contains unsupported pattern " + value); }
        }
        ConfigurationSection messages = bingo.getConfigurationSection("messages");
        if (requireMessages && messages != null) {
            for (String key : messages.getKeys(false)) validateLines(messages, key, path + ".messages." + key, false);
        }
    }

    private static Set<String> validateRewards(ConfigurationSection rewards) {
        if (rewards == null || rewards.getKeys(false).isEmpty()) throw invalid("chat-events.reward-profiles", "must define at least one profile");
        Set<String> ids = new HashSet<>();
        for (String id : rewards.getKeys(false)) {
            if (id == null || id.isBlank() || !ids.add(id)) throw invalid("chat-events.reward-profiles", "contains a blank/duplicate profile id");
            ConfigurationSection profile = rewards.getConfigurationSection(id);
            if (profile == null) throw invalid("chat-events.reward-profiles." + id, "must be a YAML section");
            double amount = profile.getDouble("economy.amount", 0);
            if (!Double.isFinite(amount) || amount < 0) throw invalid("chat-events.reward-profiles." + id + ".economy.amount", "must be a finite non-negative value");
            long keyAmount = profile.getLong("plexonkeys.amount", 0);
            if (keyAmount < 0) throw invalid("chat-events.reward-profiles." + id + ".plexonkeys.amount", "must be non-negative");
            String tier = profile.getString("plexonkeys.tier", "").toUpperCase(Locale.ROOT);
            if (profile.getBoolean("plexonkeys.enabled", false) && keyAmount > 0 && !KEY_TIERS.contains(tier)) {
                throw invalid("chat-events.reward-profiles." + id + ".plexonkeys.tier", "must be BASIC, RARE, EPIC or LEGENDARY");
            }
            for (String command : profile.getStringList("console-commands")) {
                if (command == null || command.isBlank() || command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0) {
                    throw invalid("chat-events.reward-profiles." + id + ".console-commands", "contains an empty or multi-line command");
                }
            }
        }
        return Set.copyOf(ids);
    }

    private static void validateEvents(ConfigurationSection events, Set<String> rewardIds, int defaultDuration, int defaultMinOnline) {
        if (events == null) throw invalid("chat-events.events", "must be a YAML section");
        Set<String> ids = new HashSet<>();
        for (String id : events.getKeys(false)) {
            if (id == null || id.isBlank() || !ids.add(id)) throw invalid("chat-events.events", "contains a blank/duplicate event id");
            String path = "chat-events.events." + id;
            ConfigurationSection event = events.getConfigurationSection(id);
            if (event == null) throw invalid(path, "must be a YAML section");
            ChatEventEngine.Type type;
            try { type = ChatEventEngine.Type.valueOf(event.getString("type", "").toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ex) { throw invalid(path + ".type", "is unknown"); }
            String name = event.getString("name", id);
            if (name == null || name.isBlank()) throw invalid(path + ".name", "must not be blank");
            int weight = event.getInt("weight", 1);
            if (weight < 0 || weight > 1_000_000) throw invalid(path + ".weight", "must be between 0 and 1000000; zero is manual-only");
            long cooldown = event.getLong("cooldown-seconds", 0);
            if (cooldown < 0 || cooldown > 31_536_000L) throw invalid(path + ".cooldown-seconds", "must be between 0 and 31536000");
            String reward = event.getString("reward-profile", "");
            if (!rewardIds.contains(reward)) throw invalid(path + ".reward-profile", "references unknown profile " + reward);
            int duration = event.getInt("duration-seconds", defaultDuration);
            if (duration < 1 || duration > 3600) throw invalid(path + ".duration-seconds", "must be between 1 and 3600");
            int minOnline = event.getInt("min-online", defaultMinOnline);
            if (minOnline < 0 || minOnline > 10000) throw invalid(path + ".min-online", "must be between 0 and 10000");
            validateChannels(event, "accepted-channels", path + ".accepted-channels");
            validateMatching(event.getConfigurationSection("matching"), path + ".matching");

            if (type != ChatEventEngine.Type.BINGO) {
                String prompt = event.getString("prompt", "");
                if (prompt.isBlank()) throw invalid(path + ".prompt", "must not be blank");
                validateMiniMessage(path + ".prompt", prompt);
            }

            switch (type) {
                case TYPE, REVERSE -> requireStrings(event.getStringList("values"), path + ".values");
                case UNSCRAMBLE -> requireStrings(event.getStringList("words"), path + ".words");
                case TRIVIA -> {
                    List<Map<?, ?>> entries = event.getMapList("entries");
                    if (entries.isEmpty()) throw invalid(path + ".entries", "must contain at least one trivia entry");
                    for (int index = 0; index < entries.size(); index++) {
                        Map<?, ?> entry = entries.get(index);
                        Object question = entry.get("question");
                        if (question == null || String.valueOf(question).isBlank()) throw invalid(path + ".entries[" + index + "].question", "must not be blank");
                        Object answers = entry.get("accepted-answers");
                        if (!(answers instanceof List<?> list) || list.isEmpty() || list.stream().map(String::valueOf).allMatch(String::isBlank)) {
                            throw invalid(path + ".entries[" + index + "].accepted-answers", "must contain at least one non-empty answer");
                        }
                    }
                }
                case MATH -> validateMath(event, path);
                case BINGO -> validateBingo(event.getConfigurationSection("bingo"), path + ".bingo", false);
            }
        }
    }

    private static void validateMath(ConfigurationSection event, String path) {
        List<String> operations = event.getStringList("operations");
        if (operations.isEmpty()) throw invalid(path + ".operations", "must not be empty");
        boolean divide = false;
        for (String operation : operations) {
            try {
                ChatEventEngine.MathOperation parsed = ChatEventEngine.MathOperation.valueOf(operation.toUpperCase(Locale.ROOT));
                divide |= parsed == ChatEventEngine.MathOperation.DIVIDE;
            } catch (IllegalArgumentException ex) { throw invalid(path + ".operations", "contains unsupported operation " + operation); }
        }
        int min = event.getInt("min-operand", 1);
        int max = event.getInt("max-operand", 10);
        if (min > max) throw invalid(path + ".max-operand", "must be >= min-operand");
        if (min < -1_000_000 || max > 1_000_000) throw invalid(path + ".min-operand", "operand bounds must stay within -1000000..1000000");
        if (divide && min == 0 && max == 0) throw invalid(path + ".operations", "DIVIDE requires at least one non-zero operand");
    }

    private static void validateLines(ConfigurationSection section, String key, String path, boolean requireNonEmpty) {
        if (section == null || !section.contains(key)) return;
        List<String> values = section.isString(key) ? List.of(section.getString(key, "")) : section.getStringList(key);
        if (requireNonEmpty && values.isEmpty()) throw invalid(path, "must contain at least one line");
        for (String line : values) validateMiniMessage(path, line == null ? "" : line);
    }

    private static void validateChannels(ConfigurationSection section, String key, String path) {
        if (section == null || !section.contains(key)) return;
        List<String> values = section.getStringList(key);
        if (values.isEmpty()) throw invalid(path, "must contain LOCAL and/or GLOBAL when present");
        for (String value : values) if (ChatChannel.fromName(value) == null) throw invalid(path, "contains unknown channel " + value);
    }

    private static void validateMatching(ConfigurationSection section, String path) {
        if (section == null) return;
        String form = section.getString("unicode-normalization", "NFKC").toUpperCase(Locale.ROOT);
        try { Normalizer.Form.valueOf(form); }
        catch (IllegalArgumentException ex) { throw invalid(path + ".unicode-normalization", "must be NFC, NFD, NFKC or NFKD"); }
    }

    private static void requireStrings(List<String> values, String path) {
        if (values.isEmpty() || values.stream().allMatch(value -> value == null || value.isBlank())) throw invalid(path, "must contain at least one non-empty value");
    }

    private static void section(ConfigurationSection root, String path) {
        if (root.contains(path) && !root.isConfigurationSection(path)) throw invalid("chat-events." + path, "must be a YAML section");
    }

    private static void validateMiniMessage(String path, String value) {
        try { MiniMessage.miniMessage().deserialize(value); }
        catch (IllegalArgumentException ex) { throw invalid(path, "contains invalid MiniMessage: " + ex.getMessage()); }
    }

    private static void validateSound(ConfigurationSection root, String path) {
        if (!root.contains(path)) return;
        String value = root.getString(path, "NONE");
        if (value == null || value.isBlank() || value.equalsIgnoreCase("NONE")) return;
        try {
            Sound resolved = null;
            if (!value.contains(":") && value.equals(value.toUpperCase(Locale.ROOT))) {
                try { resolved = (Sound) Sound.class.getField(value).get(null); }
                catch (ReflectiveOperationException ignored) { }
            }
            NamespacedKey key = NamespacedKey.fromString(value.toLowerCase(Locale.ROOT));
            if (key == null || (Bukkit.getServer() != null && resolved == null && Registry.SOUNDS.get(key) == null)) throw new IllegalArgumentException();
        } catch (RuntimeException ex) { throw invalid("chat-events." + path, "is invalid: " + value); }
    }

    private static void range(long value, long min, long max, String path) {
        if (value < min || value > max) throw invalid(path, "must be between " + min + " and " + max);
    }

    private static IllegalArgumentException invalid(String path, String message) { return new IllegalArgumentException(path + " " + message); }
}
