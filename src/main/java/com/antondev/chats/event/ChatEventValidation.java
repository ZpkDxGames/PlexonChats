package com.antondev.chats.event;

import com.antondev.chats.ChatChannel;
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
    private static final MiniMessage LEGACY_MINI_MESSAGE = MiniMessage.miniMessage();
    private static final MiniMessage STRICT_MINI_MESSAGE = MiniMessage.builder().strict(true).build();

    private ChatEventValidation() { }

    public static void validate(ConfigurationSection root) {
        if (root == null) throw invalid("chat-events", "must be a YAML section");
        for (String key : List.of("scheduler", "defaults", "presentation", "bingo", "reward-profiles", "events", "sounds", "messages")) section(root, key);
        long initial = root.getLong("scheduler.initial-delay-seconds", 180);
        long min = root.getLong("scheduler.min-interval-seconds", 600);
        long max = root.getLong("scheduler.max-interval-seconds", 1200);
        range(initial, 0, 604800, "chat-events.scheduler.initial-delay-seconds");
        range(min, 1, 604800, "chat-events.scheduler.min-interval-seconds");
        range(max, 1, 604800, "chat-events.scheduler.max-interval-seconds");
        if (min > max) throw invalid("chat-events.scheduler.min-interval-seconds", "must be <= max-interval-seconds");
        range(root.getInt("scheduler.min-online", 1), 0, 10000, "chat-events.scheduler.min-online");

        ConfigurationSection defaults = root.getConfigurationSection("defaults");
        int duration = defaults == null ? 30 : defaults.getInt("duration-seconds", 30);
        range(duration, 1, 3600, "chat-events.defaults.duration-seconds");
        int defaultsMinOnline = defaults == null ? 1 : defaults.getInt("min-online", 1);
        range(defaultsMinOnline, 0, 10000, "chat-events.defaults.min-online");
        validateChannels(defaults, "accepted-channels", "chat-events.defaults.accepted-channels");
        validateMatching(defaults == null ? null : defaults.getConfigurationSection("matching"), "chat-events.defaults.matching");
        validatePresentation(root.getConfigurationSection("presentation"));
        validateBingo(root.getConfigurationSection("bingo"));
        Set<String> rewardIds = validateRewards(root.getConfigurationSection("reward-profiles"));
        validateEvents(root.getConfigurationSection("events"), rewardIds, duration, defaultsMinOnline);
        for (String key : List.of("start", "win", "timeout")) validateSound(root, "sounds." + key + ".sound");
        ConfigurationSection messages = root.getConfigurationSection("messages");
        if (messages != null) for (String key : messages.getKeys(false)) if (messages.isString(key)) validateLegacyMiniMessage("chat-events.messages." + key, messages.getString(key, ""));
    }

    private static void validatePresentation(ConfigurationSection presentation) {
        if (presentation == null) return;
        range(presentation.getInt("blank-lines-before", 1), 0, 3, "chat-events.presentation.blank-lines-before");
        range(presentation.getInt("blank-lines-after", 1), 0, 3, "chat-events.presentation.blank-lines-after");
        validateStrictMiniMessage("chat-events.presentation.separator", presentation.getString("separator", ""));
        for (String key : List.of("start", "winner", "timeout", "cancelled")) validateLines(presentation, key, "chat-events.presentation." + key, true, true);
        ConfigurationSection typeNames = presentation.getConfigurationSection("type-names");
        if (typeNames != null) for (String key : typeNames.getKeys(false)) {
            try { ChatEventEngine.Type.valueOf(key.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ex) { throw invalid("chat-events.presentation.type-names." + key, "uses an unknown event type"); }
            if (typeNames.getString(key, "").isBlank()) throw invalid("chat-events.presentation.type-names." + key, "must not be blank");
        }
    }

    private static void validateBingo(ConfigurationSection bingo) {
        if (bingo == null) return;
        range(bingo.getInt("draw.first-delay-seconds", 5), 0, 300, "chat-events.bingo.draw.first-delay-seconds");
        range(bingo.getInt("draw.interval-seconds", 5), 1, 300, "chat-events.bingo.draw.interval-seconds");
        boolean any = bingo.getBoolean("winning.horizontal", true) || bingo.getBoolean("winning.vertical", true)
                || bingo.getBoolean("winning.diagonal", true) || bingo.getBoolean("winning.full-house", false);
        if (!any) throw invalid("chat-events.bingo.winning", "must enable at least one winning pattern");
        ConfigurationSection discord = bingo.getConfigurationSection("discord");
        if (discord != null && discord.getBoolean("enabled", false) && discord.getString("webhook-url", "").isBlank()) {
            throw invalid("chat-events.bingo.discord.webhook-url", "must be configured when Discord sync is enabled");
        }
        ConfigurationSection messages = bingo.getConfigurationSection("messages");
        if (messages != null) for (String key : messages.getKeys(false)) validateLines(messages, key, "chat-events.bingo.messages." + key, false, true);
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
            if (profile.getBoolean("plexonkeys.enabled", false) && keyAmount > 0 && !KEY_TIERS.contains(tier)) throw invalid("chat-events.reward-profiles." + id + ".plexonkeys.tier", "must be BASIC, RARE, EPIC or LEGENDARY");
            for (String command : profile.getStringList("console-commands")) if (command == null || command.isBlank() || command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0) throw invalid("chat-events.reward-profiles." + id + ".console-commands", "contains an empty or multi-line command");
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
            if (event.getString("name", id).isBlank()) throw invalid(path + ".name", "must not be blank");
            range(event.getInt("weight", 1), 0, 1_000_000, path + ".weight");
            range(event.getLong("cooldown-seconds", 0), 0, 31_536_000L, path + ".cooldown-seconds");
            String reward = event.getString("reward-profile", "");
            if (!rewardIds.contains(reward)) throw invalid(path + ".reward-profile", "references unknown profile " + reward);
            range(event.getInt("duration-seconds", defaultDuration), 1, 3600, path + ".duration-seconds");
            range(event.getInt("min-online", defaultMinOnline), 0, 10000, path + ".min-online");
            validateChannels(event, "accepted-channels", path + ".accepted-channels");
            validateMatching(event.getConfigurationSection("matching"), path + ".matching");
            if (type != ChatEventEngine.Type.BINGO) {
                String prompt = event.getString("prompt", "");
                if (prompt.isBlank()) throw invalid(path + ".prompt", "must not be blank");
                validateLegacyMiniMessage(path + ".prompt", prompt);
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
                        if (!(answers instanceof List<?> list) || list.isEmpty() || list.stream().map(String::valueOf).allMatch(String::isBlank)) throw invalid(path + ".entries[" + index + "].accepted-answers", "must contain at least one non-empty answer");
                    }
                }
                case MATH -> validateMath(event, path);
                case BINGO -> { }
            }
        }
    }

    private static void validateMath(ConfigurationSection event, String path) {
        List<String> operations = event.getStringList("operations");
        if (operations.isEmpty()) throw invalid(path + ".operations", "must not be empty");
        boolean divide = false;
        for (String operation : operations) {
            try { ChatEventEngine.MathOperation parsed = ChatEventEngine.MathOperation.valueOf(operation.toUpperCase(Locale.ROOT)); divide |= parsed == ChatEventEngine.MathOperation.DIVIDE; }
            catch (IllegalArgumentException ex) { throw invalid(path + ".operations", "contains unsupported operation " + operation); }
        }
        int min = event.getInt("min-operand", 1); int max = event.getInt("max-operand", 10);
        if (min > max) throw invalid(path + ".max-operand", "must be >= min-operand");
        if (min < -1_000_000 || max > 1_000_000) throw invalid(path + ".min-operand", "operand bounds must stay within -1000000..1000000");
        if (divide && min == 0 && max == 0) throw invalid(path + ".operations", "DIVIDE requires at least one non-zero operand");
    }

    private static void validateLines(ConfigurationSection section, String key, String path, boolean requireNonEmpty, boolean strict) {
        if (section == null || !section.contains(key)) return;
        List<String> values = section.isString(key) ? List.of(section.getString(key, "")) : section.getStringList(key);
        if (requireNonEmpty && values.isEmpty()) throw invalid(path, "must contain at least one line");
        for (String line : values) if (strict) validateStrictMiniMessage(path, line == null ? "" : line); else validateLegacyMiniMessage(path, line == null ? "" : line);
    }
    private static void validateChannels(ConfigurationSection section, String key, String path) { if (section == null || !section.contains(key)) return; List<String> values = section.getStringList(key); if (values.isEmpty()) throw invalid(path, "must contain LOCAL and/or GLOBAL when present"); for (String value : values) if (ChatChannel.fromName(value) == null) throw invalid(path, "contains unknown channel " + value); }
    private static void validateMatching(ConfigurationSection section, String path) { if (section == null) return; String form = section.getString("unicode-normalization", "NFKC").toUpperCase(Locale.ROOT); try { Normalizer.Form.valueOf(form); } catch (IllegalArgumentException ex) { throw invalid(path + ".unicode-normalization", "must be NFC, NFD, NFKC or NFKD"); } }
    private static void requireStrings(List<String> values, String path) { if (values.isEmpty() || values.stream().allMatch(value -> value == null || value.isBlank())) throw invalid(path, "must contain at least one non-empty value"); }
    private static void section(ConfigurationSection root, String path) { if (root.contains(path) && !root.isConfigurationSection(path)) throw invalid("chat-events." + path, "must be a YAML section"); }
    private static void validateLegacyMiniMessage(String path, String value) { try { LEGACY_MINI_MESSAGE.deserialize(value); } catch (RuntimeException ex) { throw invalid(path, "contains invalid MiniMessage: " + ex.getMessage()); } }
    private static void validateStrictMiniMessage(String path, String value) { try { STRICT_MINI_MESSAGE.deserialize(value); } catch (RuntimeException ex) { throw invalid(path, "contains invalid MiniMessage: " + ex.getMessage()); } }
    private static void validateSound(ConfigurationSection root, String path) { if (!root.contains(path)) return; String value = root.getString(path, "NONE"); if (value == null || value.isBlank() || value.equalsIgnoreCase("NONE")) return; try { Sound resolved = null; if (!value.contains(":") && value.equals(value.toUpperCase(Locale.ROOT))) { try { resolved = (Sound) Sound.class.getField(value).get(null); } catch (ReflectiveOperationException ignored) { } } NamespacedKey key = NamespacedKey.fromString(value.toLowerCase(Locale.ROOT)); if (key == null || (Bukkit.getServer() != null && resolved == null && Registry.SOUNDS.get(key) == null)) throw new IllegalArgumentException(); } catch (RuntimeException ex) { throw invalid("chat-events." + path, "is invalid: " + value); } }
    private static void range(long value, long min, long max, String path) { if (value < min || value > max) throw invalid(path, "must be between " + min + " and " + max); }
    private static IllegalArgumentException invalid(String path, String message) { return new IllegalArgumentException(path + " " + message); }
}
