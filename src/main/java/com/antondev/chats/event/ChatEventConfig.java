package com.antondev.chats.event;

import com.antondev.chats.ChatChannel;
import com.antondev.chats.event.bingo.BingoPattern;
import org.bukkit.configuration.ConfigurationSection;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Immutable runtime snapshot of the validated chat-events configuration. */
public record ChatEventConfig(
        boolean enabled,
        Scheduler scheduler,
        Defaults defaults,
        Presentation presentation,
        BingoSettings bingo,
        Map<String, RewardProfile> rewards,
        Map<String, Definition> definitions,
        Map<String, SoundSpec> sounds,
        Map<String, String> messages) {

    public ChatEventConfig {
        rewards = Map.copyOf(rewards);
        definitions = Map.copyOf(definitions);
        sounds = Map.copyOf(sounds);
        messages = Map.copyOf(messages);
    }

    public static ChatEventConfig read(ConfigurationSection root) {
        if (root == null) throw new IllegalArgumentException("chat-events section is missing");
        Scheduler scheduler = new Scheduler(
                root.getBoolean("scheduler.enabled", true),
                root.getLong("scheduler.initial-delay-seconds", 180),
                root.getLong("scheduler.min-interval-seconds", 600),
                root.getLong("scheduler.max-interval-seconds", 1200),
                root.getInt("scheduler.min-online", 1),
                root.getBoolean("scheduler.avoid-immediate-repeat", true),
                root.getBoolean("scheduler.pause-when-empty", true));

        ConfigurationSection defaultsSection = root.getConfigurationSection("defaults");
        Defaults defaults = new Defaults(
                integer(defaultsSection, "duration-seconds", 30),
                bool(defaultsSection, "reveal-answer-on-timeout", true),
                integer(defaultsSection, "min-online", 1),
                string(defaultsSection, "permission", ""),
                lowerSet(strings(defaultsSection, "worlds")),
                lowerSet(strings(defaultsSection, "excluded-worlds")),
                channels(strings(defaultsSection, "accepted-channels"), Set.of(ChatChannel.LOCAL, ChatChannel.GLOBAL)),
                matching(defaultsSection == null ? null : defaultsSection.getConfigurationSection("matching"), Matching.standard()));

        Presentation presentation = presentation(root.getConfigurationSection("presentation"));
        BingoSettings bingo = bingo(root.getConfigurationSection("bingo"), null);

        Map<String, RewardProfile> rewards = new LinkedHashMap<>();
        ConfigurationSection rewardSection = root.getConfigurationSection("reward-profiles");
        if (rewardSection != null) {
            for (String id : rewardSection.getKeys(false)) {
                ConfigurationSection section = rewardSection.getConfigurationSection(id);
                if (section == null) continue;
                rewards.put(id, new RewardProfile(
                        section.getBoolean("economy.enabled", false),
                        section.getDouble("economy.amount", 0),
                        section.getBoolean("plexonkeys.enabled", false),
                        section.getString("plexonkeys.tier", "BASIC").toUpperCase(Locale.ROOT),
                        section.getLong("plexonkeys.amount", 0),
                        List.copyOf(section.getStringList("console-commands"))));
            }
        }

        Map<String, Definition> definitions = new LinkedHashMap<>();
        ConfigurationSection eventSection = root.getConfigurationSection("events");
        if (eventSection != null) {
            for (String id : eventSection.getKeys(false)) {
                ConfigurationSection section = eventSection.getConfigurationSection(id);
                if (section == null) continue;
                ChatEventEngine.Type type = ChatEventEngine.Type.valueOf(section.getString("type", "TYPE").toUpperCase(Locale.ROOT));
                Matching eventMatching = matching(section.getConfigurationSection("matching"), defaults.matching());
                Set<ChatChannel> acceptedChannels = section.contains("accepted-channels")
                        ? channels(section.getStringList("accepted-channels"), defaults.acceptedChannels())
                        : defaults.acceptedChannels();
                List<TriviaEntry> trivia = new ArrayList<>();
                for (Map<?, ?> entry : section.getMapList("entries")) {
                    Object questionValue = entry.get("question");
                    String question = questionValue == null ? "" : String.valueOf(questionValue);
                    List<String> accepted = stringList(entry.get("accepted-answers"));
                    trivia.add(new TriviaEntry(question, accepted));
                }
                EnumSet<ChatEventEngine.MathOperation> operations = EnumSet.noneOf(ChatEventEngine.MathOperation.class);
                for (String value : section.getStringList("operations")) {
                    operations.add(ChatEventEngine.MathOperation.valueOf(value.toUpperCase(Locale.ROOT)));
                }
                definitions.put(id, new Definition(
                        id,
                        section.getString("name", displayId(id)),
                        type,
                        section.getBoolean("enabled", true),
                        section.getInt("weight", 1),
                        section.getLong("cooldown-seconds", 0),
                        section.getString("reward-profile", ""),
                        section.getInt("duration-seconds", defaults.durationSeconds()),
                        section.getBoolean("reveal-answer-on-timeout", defaults.revealAnswerOnTimeout()),
                        section.getInt("min-online", defaults.minOnline()),
                        section.getString("permission", defaults.permission()),
                        section.contains("worlds") ? lowerSet(section.getStringList("worlds")) : defaults.worlds(),
                        section.contains("excluded-worlds") ? lowerSet(section.getStringList("excluded-worlds")) : defaults.excludedWorlds(),
                        acceptedChannels,
                        eventMatching,
                        section.getString("prompt", ""),
                        List.copyOf(section.getStringList("values")),
                        List.copyOf(section.getStringList("words")),
                        List.copyOf(trivia),
                        Set.copyOf(operations),
                        section.getInt("min-operand", 1),
                        section.getInt("max-operand", 10),
                        section.getBoolean("allow-negative-result", false),
                        bingo(section.getConfigurationSection("bingo"), bingo)));
            }
        }

        Map<String, SoundSpec> sounds = new LinkedHashMap<>();
        for (String id : List.of("start", "win", "timeout")) {
            sounds.put(id, new SoundSpec(
                    root.getString("sounds." + id + ".sound", "NONE"),
                    (float) root.getDouble("sounds." + id + ".volume", 1),
                    (float) root.getDouble("sounds." + id + ".pitch", 1)));
        }

        Map<String, String> messages = new LinkedHashMap<>();
        ConfigurationSection messageSection = root.getConfigurationSection("messages");
        if (messageSection != null) {
            for (String key : messageSection.getKeys(false)) {
                if (messageSection.isString(key)) messages.put(key, messageSection.getString(key, ""));
            }
        }
        messages.putIfAbsent("prefix", "<bold><gradient:#ffd66b:#ff9f43>Chat Event</gradient></bold> <dark_gray>» ");
        messages.putIfAbsent("started", "{prompt}");
        messages.putIfAbsent("winner", "<green><bold>{winner}</bold></green> <gray>answered correctly in <white>{elapsed}</white>! <gray>Reward: {reward_summary}");
        messages.putIfAbsent("timed-out", "<yellow>Time's up!</yellow> <gray>The answer was <white>{answer}</white>.");
        messages.putIfAbsent("cancelled", "<gray>The current chat event was cancelled.");
        messages.putIfAbsent("no-events", "<yellow>No eligible chat events are currently available.");
        messages.putIfAbsent("already-active", "<yellow>A chat event is already active: <white>{event_id}</white>.");
        messages.putIfAbsent("disabled", "<yellow>Chat Events are disabled.");
        return new ChatEventConfig(root.getBoolean("enabled", true), scheduler, defaults, presentation, bingo,
                rewards, definitions, sounds, messages);
    }

    private static Presentation presentation(ConfigurationSection section) {
        String separator = string(section, "separator", "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>");
        Map<String, List<String>> cards = new LinkedHashMap<>();
        cards.put("start", lines(section, "start", List.of("{separator}", "<gold><bold>CHAT EVENT</bold></gold> <dark_gray>•</dark_gray> <aqua>{event_name}</aqua>", "", "{prompt}", "", "<gray>Reward:</gray> <gold>{reward}</gold>", "<gray>Time:</gray> <yellow>{duration_seconds}s</yellow>", "{separator}")));
        cards.put("winner", lines(section, "winner", List.of("{separator}", "<green><bold>CHAT EVENT COMPLETE</bold></green>", "<white>{winner}</white> <gray>won in</gray> <aqua>{elapsed}</aqua>", "<gray>Reward:</gray> <gold>{reward}</gold>", "<gray>Total wins:</gray> <yellow>{winner_total_wins}</yellow>", "{separator}")));
        cards.put("timeout", lines(section, "timeout", List.of("{separator}", "<yellow><bold>CHAT EVENT ENDED</bold></yellow>", "<gray>No winner this round.</gray>", "<gray>Answer:</gray> <white>{answer}</white>", "{separator}")));
        cards.put("cancelled", lines(section, "cancelled", List.of("{separator}", "<gray><bold>CHAT EVENT CANCELLED</bold></gray>", "<gray>No reward was issued.</gray>", "{separator}")));
        EnumMap<ChatEventEngine.Type, String> typeNames = new EnumMap<>(ChatEventEngine.Type.class);
        for (ChatEventEngine.Type type : ChatEventEngine.Type.values()) {
            typeNames.put(type, string(section, "type-names." + type.name(), displayId(type.name())));
        }
        return new Presentation(integer(section, "blank-lines-before", 1), integer(section, "blank-lines-after", 1), separator, cards, typeNames);
    }

    private static BingoSettings bingo(ConfigurationSection section, BingoSettings fallback) {
        BingoSettings base = fallback == null ? BingoSettings.defaults() : fallback;
        Set<BingoPattern> patterns = EnumSet.noneOf(BingoPattern.class);
        List<String> configured = section == null ? List.of() : section.getStringList("win-patterns");
        if (configured.isEmpty()) patterns.addAll(base.winPatterns());
        else for (String value : configured) patterns.add(BingoPattern.valueOf(value.toUpperCase(Locale.ROOT)));
        Map<String, List<String>> messages = new LinkedHashMap<>(base.messages());
        if (section != null) {
            ConfigurationSection messageSection = section.getConfigurationSection("messages");
            if (messageSection != null) {
                for (String key : messageSection.getKeys(false)) messages.put(key, readLines(messageSection, key));
            }
        }
        return new BingoSettings(
                bool(section, "enabled", base.enabled()),
                integer(section, section != null && section.contains("join-seconds") ? "join-seconds" : "join.duration-seconds", base.joinSeconds()),
                integer(section, section != null && section.contains("min-participants") ? "min-participants" : "join.min-participants", base.minParticipants()),
                bool(section, "board.free-center", bool(section, "free-center", base.freeCenter())),
                integer(section, section != null && section.contains("draw-interval-seconds") ? "draw-interval-seconds" : "draw.first-delay-seconds", base.firstDrawDelaySeconds()),
                integer(section, section != null && section.contains("draw-interval-seconds") ? "draw-interval-seconds" : "draw.interval-seconds", base.drawIntervalSeconds()),
                bool(section, "draw.redraw-board-each-draw", base.redrawBoardEachDraw()),
                integer(section, "timeout-seconds", base.timeoutSeconds()),
                Set.copyOf(patterns),
                Map.copyOf(messages));
    }

    private static Matching matching(ConfigurationSection section, Matching fallback) {
        if (section == null) return fallback;
        Normalizer.Form form;
        try { form = Normalizer.Form.valueOf(section.getString("unicode-normalization", fallback.normalization().name()).toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { form = fallback.normalization(); }
        return new Matching(
                section.getBoolean("case-sensitive", fallback.caseSensitive()),
                section.getBoolean("trim", fallback.trim()),
                section.getBoolean("collapse-whitespace", fallback.collapseWhitespace()),
                form,
                section.getBoolean("ignore-diacritics", fallback.ignoreDiacritics()));
    }

    private static int integer(ConfigurationSection section, String path, int fallback) { return section == null ? fallback : section.getInt(path, fallback); }
    private static boolean bool(ConfigurationSection section, String path, boolean fallback) { return section == null ? fallback : section.getBoolean(path, fallback); }
    private static String string(ConfigurationSection section, String path, String fallback) { return section == null ? fallback : section.getString(path, fallback); }
    private static List<String> strings(ConfigurationSection section, String path) { return section == null ? List.of() : section.getStringList(path); }
    private static List<String> lines(ConfigurationSection section, String path, List<String> fallback) {
        if (section == null || !section.contains(path)) return List.copyOf(fallback);
        List<String> result = readLines(section, path);
        return result.isEmpty() ? List.copyOf(fallback) : result;
    }
    private static List<String> readLines(ConfigurationSection section, String path) {
        if (section.isString(path)) return List.of(section.getString(path, ""));
        return List.copyOf(section.getStringList(path));
    }
    private static String displayId(String id) {
        if (id == null || id.isBlank()) return "Chat Event";
        String[] parts = id.toLowerCase(Locale.ROOT).replace('_', '-').split("-");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }
    private static Set<String> lowerSet(List<String> source) {
        Set<String> result = new LinkedHashSet<>();
        for (String value : source) if (value != null && !value.isBlank()) result.add(value.toLowerCase(Locale.ROOT));
        return Set.copyOf(result);
    }
    private static Set<ChatChannel> channels(List<String> source, Set<ChatChannel> fallback) {
        if (source == null || source.isEmpty()) return fallback;
        EnumSet<ChatChannel> values = EnumSet.noneOf(ChatChannel.class);
        for (String value : source) {
            ChatChannel channel = ChatChannel.fromName(value);
            if (channel != null) values.add(channel);
        }
        return values.isEmpty() ? fallback : Set.copyOf(values);
    }
    private static List<String> stringList(Object value) {
        if (value instanceof List<?> values) return values.stream().map(String::valueOf).toList();
        if (value instanceof String text) return List.of(text);
        return List.of();
    }

    public record Scheduler(boolean enabled, long initialDelaySeconds, long minIntervalSeconds, long maxIntervalSeconds,
                            int minOnline, boolean avoidImmediateRepeat, boolean pauseWhenEmpty) { }
    public record Defaults(int durationSeconds, boolean revealAnswerOnTimeout, int minOnline, String permission,
                           Set<String> worlds, Set<String> excludedWorlds, Set<ChatChannel> acceptedChannels, Matching matching) { }
    public record Presentation(int blankLinesBefore, int blankLinesAfter, String separator,
                               Map<String, List<String>> cards, Map<ChatEventEngine.Type, String> typeNames) {
        public Presentation {
            Map<String, List<String>> copy = new LinkedHashMap<>();
            cards.forEach((key, value) -> copy.put(key, List.copyOf(value)));
            cards = Map.copyOf(copy);
            typeNames = Map.copyOf(typeNames);
        }
        public List<String> card(String state) { return cards.getOrDefault(state, List.of()); }
        public String typeName(ChatEventEngine.Type type) { return typeNames.getOrDefault(type, type.name()); }
    }
    public record BingoSettings(boolean enabled, int joinSeconds, int minParticipants, boolean freeCenter,
                                int firstDrawDelaySeconds, int drawIntervalSeconds, boolean redrawBoardEachDraw,
                                int timeoutSeconds, Set<BingoPattern> winPatterns, Map<String, List<String>> messages) {
        public BingoSettings {
            winPatterns = Set.copyOf(winPatterns);
            Map<String, List<String>> copy = new LinkedHashMap<>();
            messages.forEach((key, value) -> copy.put(key, List.copyOf(value)));
            messages = Map.copyOf(copy);
        }
        public List<String> message(String key) { return messages.getOrDefault(key, List.of()); }
        public static BingoSettings defaults() {
            return new BingoSettings(true, 15, 2, true, 5, 5, false, 300,
                    Set.of(BingoPattern.ROW, BingoPattern.COLUMN, BingoPattern.DIAGONAL),
                    Map.of(
                            "joined", List.of("<green>You joined this Bingo round.</green>", "<gray>Your card is shown below.</gray>"),
                            "draw", List.of("<gold>[BINGO]</gold> <gray>Draw</gray> <yellow>#{draw_count}</yellow><gray>:</gray> <white>{drawn_number}</white> <dark_gray>•</dark_gray> {open_card_button}"),
                            "marked", List.of("<gold>[BINGO]</gold> <green>Marked {drawn_number}.</green>"),
                            "invalid-mark", List.of("<gold>[BINGO]</gold> <red>That cell cannot be marked yet.</red>"),
                            "not-participant", List.of("<red>You are not participating in this Bingo round.</red>")));
        }
    }
    public record Matching(boolean caseSensitive, boolean trim, boolean collapseWhitespace, Normalizer.Form normalization,
                           boolean ignoreDiacritics) {
        public static Matching standard() { return new Matching(false, true, true, Normalizer.Form.NFKC, false); }
    }
    public record RewardProfile(boolean economyEnabled, double economyAmount, boolean keysEnabled, String keyTier,
                                long keyAmount, List<String> consoleCommands) {
        public RewardProfile { consoleCommands = List.copyOf(consoleCommands); }
    }
    public record TriviaEntry(String question, List<String> acceptedAnswers) {
        public TriviaEntry { acceptedAnswers = List.copyOf(acceptedAnswers); }
    }
    public record SoundSpec(String sound, float volume, float pitch) { }
    public record Definition(String id, String name, ChatEventEngine.Type type, boolean enabled, int weight, long cooldownSeconds,
                             String rewardProfile, int durationSeconds, boolean revealAnswerOnTimeout, int minOnline,
                             String permission, Set<String> worlds, Set<String> excludedWorlds,
                             Set<ChatChannel> acceptedChannels, Matching matching, String prompt, List<String> values,
                             List<String> words, List<TriviaEntry> trivia, Set<ChatEventEngine.MathOperation> mathOperations,
                             int minOperand, int maxOperand, boolean allowNegativeResult, BingoSettings bingo) {
        public Definition {
            worlds = Set.copyOf(worlds);
            excludedWorlds = Set.copyOf(excludedWorlds);
            acceptedChannels = Set.copyOf(acceptedChannels);
            values = List.copyOf(values);
            words = List.copyOf(words);
            trivia = List.copyOf(trivia);
            mathOperations = Set.copyOf(mathOperations);
        }
    }
}
