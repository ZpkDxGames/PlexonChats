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

/** Immutable runtime snapshot. config.yml supplies presentation/integrations; data.yml supplies gameplay. */
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

    /** Legacy-compatible read used by tests and rollback paths. */
    public static ChatEventConfig read(ConfigurationSection legacyRoot) { return read(legacyRoot, null); }

    /** Read a runtime generation with gameplay from dataRoot when present. */
    public static ChatEventConfig read(ConfigurationSection legacyRoot, ConfigurationSection dataRoot) {
        if (legacyRoot == null) throw new IllegalArgumentException("chat-events section is missing");
        ConfigurationSection schedulerRoot = dataRoot == null ? legacyRoot.getConfigurationSection("scheduler") : dataRoot.getConfigurationSection("scheduler");
        ConfigurationSection randomizer = dataRoot == null ? null : dataRoot.getConfigurationSection("randomizer");
        Scheduler scheduler = new Scheduler(
                bool(schedulerRoot, "enabled", true),
                longValue(schedulerRoot, "initial-delay-seconds", 180),
                longValue(schedulerRoot, "min-interval-seconds", 600),
                longValue(schedulerRoot, "max-interval-seconds", 1200),
                integer(schedulerRoot, dataRoot == null ? "min-online" : "minimum-online", 1),
                randomizer == null ? bool(schedulerRoot, "avoid-immediate-repeat", true) : bool(randomizer, "avoid-immediate-repeat", true),
                bool(schedulerRoot, "pause-when-empty", true),
                string(randomizer, "mode", "WEIGHTED").toUpperCase(Locale.ROOT),
                integer(randomizer, "history-size", 2));

        ConfigurationSection defaultsSection = legacyRoot.getConfigurationSection("defaults");
        Defaults defaults = new Defaults(
                integer(defaultsSection, "duration-seconds", 30),
                bool(defaultsSection, "reveal-answer-on-timeout", true),
                integer(defaultsSection, "min-online", 1),
                string(defaultsSection, "permission", ""),
                lowerSet(strings(defaultsSection, "worlds")),
                lowerSet(strings(defaultsSection, "excluded-worlds")),
                channels(strings(defaultsSection, "accepted-channels"), Set.of(ChatChannel.LOCAL, ChatChannel.GLOBAL)),
                matching(defaultsSection == null ? null : defaultsSection.getConfigurationSection("matching"), Matching.standard()));

        Presentation presentation = presentation(legacyRoot.getConfigurationSection("presentation"));
        ConfigurationSection legacyBingo = legacyRoot.getConfigurationSection("bingo");
        ConfigurationSection eventRoot = dataRoot == null ? legacyRoot.getConfigurationSection("events") : dataRoot.getConfigurationSection("minigames");
        ConfigurationSection primaryBingo = findBingo(eventRoot);
        BingoSettings bingo = bingo(primaryBingo == null ? legacyBingo : primaryBingo, legacyBingo);

        Map<String, RewardProfile> rewards = new LinkedHashMap<>();
        ConfigurationSection rewardSection = legacyRoot.getConfigurationSection("reward-profiles");
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
        if (eventRoot != null) {
            for (String id : eventRoot.getKeys(false)) {
                ConfigurationSection section = eventRoot.getConfigurationSection(id);
                if (section == null) continue;
                ChatEventEngine.Type type;
                try { type = ChatEventEngine.Type.valueOf(section.getString("type", inferType(id)).toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException ex) { continue; }
                Matching eventMatching = matching(section.getConfigurationSection("matching"), defaults.matching());
                Set<ChatChannel> acceptedChannels = section.contains("accepted-channels")
                        ? channels(section.getStringList("accepted-channels"), defaults.acceptedChannels())
                        : defaults.acceptedChannels();
                List<TriviaEntry> trivia = new ArrayList<>();
                for (Map<?, ?> entry : section.getMapList("entries")) {
                    Object questionValue = entry.get("question");
                    String question = questionValue == null ? "" : String.valueOf(questionValue);
                    trivia.add(new TriviaEntry(question, stringList(entry.get("accepted-answers"))));
                }
                EnumSet<ChatEventEngine.MathOperation> operations = EnumSet.noneOf(ChatEventEngine.MathOperation.class);
                for (String value : section.getStringList("operations")) {
                    try { operations.add(ChatEventEngine.MathOperation.valueOf(value.toUpperCase(Locale.ROOT))); }
                    catch (IllegalArgumentException ignored) { }
                }
                BingoSettings eventBingo = type == ChatEventEngine.Type.BINGO ? bingo(section, legacyBingo) : bingo;
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
                        eventBingo));
            }
        }

        Map<String, SoundSpec> sounds = new LinkedHashMap<>();
        for (String id : List.of("start", "win", "timeout")) {
            sounds.put(id, new SoundSpec(
                    legacyRoot.getString("sounds." + id + ".sound", "NONE"),
                    (float) legacyRoot.getDouble("sounds." + id + ".volume", 1),
                    (float) legacyRoot.getDouble("sounds." + id + ".pitch", 1)));
        }

        Map<String, String> messages = new LinkedHashMap<>();
        ConfigurationSection messageSection = legacyRoot.getConfigurationSection("messages");
        if (messageSection != null) for (String key : messageSection.getKeys(false)) if (messageSection.isString(key)) messages.put(key, messageSection.getString(key, ""));
        messages.putIfAbsent("prefix", "<bold><gradient:#ffd66b:#ff9f43>Chat Event</gradient></bold> <dark_gray>» ");
        messages.putIfAbsent("started", "{prompt}");
        messages.putIfAbsent("winner", "<green><bold>{winner}</bold></green> <gray>answered correctly in <white>{elapsed}</white>! <gray>Reward: {reward_summary}");
        messages.putIfAbsent("timed-out", "<yellow>Time's up!</yellow> <gray>The answer was <white>{answer}</white>.");
        messages.putIfAbsent("cancelled", "<gray>The current chat event was cancelled.</gray>");
        messages.putIfAbsent("no-events", "<yellow>No eligible chat events are currently available.</yellow>");
        messages.putIfAbsent("already-active", "<yellow>A chat event is already active: <white>{event_id}</white>.</yellow>");
        messages.putIfAbsent("disabled", "<yellow>Chat Events are disabled.</yellow>");
        boolean enabled = dataRoot == null ? legacyRoot.getBoolean("enabled", true) : dataRoot.getBoolean("enabled", legacyRoot.getBoolean("enabled", true));
        return new ChatEventConfig(enabled, scheduler, defaults, presentation, bingo, rewards, definitions, sounds, messages);
    }

    private static ConfigurationSection findBingo(ConfigurationSection events) {
        if (events == null) return null;
        for (String id : events.getKeys(false)) {
            ConfigurationSection section = events.getConfigurationSection(id);
            if (section != null && "BINGO".equalsIgnoreCase(section.getString("type", inferType(id)))) return section;
        }
        return null;
    }

    private static Presentation presentation(ConfigurationSection section) {
        String separator = string(section, "separator", "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>");
        Map<String, List<String>> cards = new LinkedHashMap<>();
        cards.put("start", lines(section, "start", List.of("{separator}", "<gold><bold>CHAT EVENT</bold></gold> <dark_gray>•</dark_gray> <aqua>{event_name}</aqua>", "", "{prompt}", "", "<gray>Reward:</gray> <gold>{reward}</gold>", "<gray>Time:</gray> <yellow>{duration_seconds}s</yellow>", "{separator}")));
        cards.put("winner", lines(section, "winner", List.of("{separator}", "<green><bold>CHAT EVENT COMPLETE</bold></green>", "<white>{winner}</white> <gray>won in</gray> <aqua>{elapsed}</aqua>", "<gray>Reward:</gray> <gold>{reward}</gold>", "<gray>Total wins:</gray> <yellow>{winner_total_wins}</yellow>", "{separator}")));
        cards.put("timeout", lines(section, "timeout", List.of("{separator}", "<yellow><bold>CHAT EVENT ENDED</bold></yellow>", "<gray>No winner this round.</gray>", "<gray>Answer:</gray> <white>{answer}</white>", "{separator}")));
        cards.put("cancelled", lines(section, "cancelled", List.of("{separator}", "<gray><bold>CHAT EVENT CANCELLED</bold></gray>", "<gray>No reward was issued.</gray>", "{separator}")));
        EnumMap<ChatEventEngine.Type, String> typeNames = new EnumMap<>(ChatEventEngine.Type.class);
        for (ChatEventEngine.Type type : ChatEventEngine.Type.values()) typeNames.put(type, string(section, "type-names." + type.name(), displayId(type.name())));
        return new Presentation(integer(section, "blank-lines-before", 1), integer(section, "blank-lines-after", 1), separator, cards, typeNames);
    }

    private static BingoSettings bingo(ConfigurationSection section, ConfigurationSection fallback) {
        BingoSettings base = BingoSettings.defaults();
        Set<BingoPattern> patterns = EnumSet.noneOf(BingoPattern.class);
        boolean horizontal = value(section, fallback, "winning.horizontal", true);
        boolean vertical = value(section, fallback, "winning.vertical", true);
        boolean diagonal = value(section, fallback, "winning.diagonal", true);
        boolean fullHouse = value(section, fallback, "winning.full-house", false);
        if (horizontal) patterns.add(BingoPattern.ROW);
        if (vertical) patterns.add(BingoPattern.COLUMN);
        if (diagonal) patterns.add(BingoPattern.DIAGONAL);
        if (fullHouse) patterns.add(BingoPattern.FULL_HOUSE);
        if (patterns.isEmpty()) patterns.add(BingoPattern.ROW);

        int firstDelay = intValue(section, fallback, section != null && section.contains("draws.first-call-delay-seconds") ? "draws.first-call-delay-seconds" : "draw.first-delay-seconds", base.firstDrawDelaySeconds());
        int interval = intValue(section, fallback, section != null && section.contains("draws.interval-seconds") ? "draws.interval-seconds" : "draw.interval-seconds", base.drawIntervalSeconds());
        ConfigurationSection lobby = section == null ? null : section.getConfigurationSection("lobby");
        BingoLobby baseLobby = base.lobby();
        List<Integer> reminders = lobby == null || !lobby.contains("reminders-seconds") ? baseLobby.remindersSeconds() : lobby.getIntegerList("reminders-seconds");
        BingoLobby lobbySettings = new BingoLobby(
                integer(lobby, "duration-seconds", baseLobby.durationSeconds()), reminders,
                integer(lobby, "minimum-participants", baseLobby.minimumParticipants()),
                integer(lobby, "admin-minimum-participants", baseLobby.adminMinimumParticipants()),
                integer(lobby, "admin-lobby-seconds", baseLobby.adminLobbySeconds()),
                integer(lobby, "admin-fast-lobby-seconds", baseLobby.adminFastLobbySeconds()),
                bool(lobby, "allow-late-join", baseLobby.allowLateJoin()),
                bool(lobby, "require-online-at-start", baseLobby.requireOnlineAtStart()),
                string(lobby, "disconnect-policy", baseLobby.disconnectPolicy()).toUpperCase(Locale.ROOT));
        ConfigurationSection render = section == null ? null : section.getConfigurationSection("render");
        BingoRender baseRender = base.render();
        BingoRender renderSettings = new BingoRender(
                renderStyle(render, baseRender),
                string(render, "font", baseRender.font()),
                integer(render, "left-padding", baseRender.leftPadding()),
                integer(render, "cell-width", baseRender.cellWidth()),
                integer(render, "column-gap", baseRender.columnGap()),
                bool(render, "show-border", baseRender.showBorder()),
                bool(render, "show-last-call", baseRender.showLastCall()),
                bool(render, "show-draw-count", baseRender.showDrawCount()),
                bool(render, "on-join", baseRender.onJoin()),
                bool(render, "on-start", baseRender.onStart()),
                bool(render, "after-successful-mark", baseRender.afterSuccessfulMark()),
                bool(render, "on-every-draw", baseRender.onEveryDraw()));
        Map<String, List<String>> messages = new LinkedHashMap<>(base.messages());
        ConfigurationSection fallbackMessages = fallback == null ? null : fallback.getConfigurationSection("messages");
        if (fallbackMessages != null) for (String key : fallbackMessages.getKeys(false)) messages.put(key, readLines(fallbackMessages, key));
        ConfigurationSection messageSection = section == null ? null : section.getConfigurationSection("messages");
        if (messageSection != null) for (String key : messageSection.getKeys(false)) messages.put(key, readLines(messageSection, key));
        ConfigurationSection discordSection = fallback == null ? null : fallback.getConfigurationSection("discord");
        BingoDiscord discord = new BingoDiscord(bool(discordSection, "enabled", false), string(discordSection, "webhook-url", ""),
                string(discordSection, "username", "PlexonChats Bingo"), bool(discordSection, "send-start", true),
                bool(discordSection, "send-draws", true), bool(discordSection, "send-win", true));
        boolean enabled = section == null ? bool(fallback, "enabled", true) : section.getBoolean("enabled", true);
        boolean freeCenter = section != null && section.getBoolean("card.free-center", false);
        return new BingoSettings(enabled, firstDelay, interval, Set.copyOf(patterns), lobbySettings, freeCenter, renderSettings, discord, Map.copyOf(messages));
    }

    private static BingoRenderStyle renderStyle(ConfigurationSection render, BingoRender fallback) {
        if (render == null) return fallback.style();
        if (render.contains("style")) {
            String raw = render.getString("style", fallback.style().name());
            try { return BingoRenderStyle.valueOf(raw.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Unknown Bingo render style: " + raw, ex); }
        }
        if (render.contains("show-border")) return render.getBoolean("show-border") ? BingoRenderStyle.TABLE : BingoRenderStyle.COMPACT;
        return fallback.style();
    }

    private static boolean value(ConfigurationSection section, ConfigurationSection fallback, String path, boolean defaultValue) {
        return section != null && section.contains(path) ? section.getBoolean(path, defaultValue) : bool(fallback, path, defaultValue);
    }
    private static int intValue(ConfigurationSection section, ConfigurationSection fallback, String path, int defaultValue) {
        return section != null && section.contains(path) ? section.getInt(path, defaultValue) : integer(fallback, path, defaultValue);
    }
    private static Matching matching(ConfigurationSection section, Matching fallback) {
        if (section == null) return fallback;
        Normalizer.Form form;
        try { form = Normalizer.Form.valueOf(section.getString("unicode-normalization", fallback.normalization().name()).toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { form = fallback.normalization(); }
        return new Matching(section.getBoolean("case-sensitive", fallback.caseSensitive()), section.getBoolean("trim", fallback.trim()),
                section.getBoolean("collapse-whitespace", fallback.collapseWhitespace()), form,
                section.getBoolean("ignore-diacritics", fallback.ignoreDiacritics()));
    }
    private static int integer(ConfigurationSection section, String path, int fallback) { return section == null ? fallback : section.getInt(path, fallback); }
    private static long longValue(ConfigurationSection section, String path, long fallback) { return section == null ? fallback : section.getLong(path, fallback); }
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
        for (String part : parts) { if (part.isBlank()) continue; if (!result.isEmpty()) result.append(' '); result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)); }
        return result.toString();
    }
    private static String inferType(String id) {
        String value = id == null ? "" : id.toLowerCase(Locale.ROOT);
        if (value.contains("bingo")) return "BINGO";
        if (value.contains("unscramble")) return "UNSCRAMBLE";
        if (value.contains("math")) return "MATH";
        if (value.contains("trivia")) return "TRIVIA";
        if (value.contains("reverse")) return "REVERSE";
        return "TYPE";
    }
    private static Set<String> lowerSet(List<String> source) { Set<String> result = new LinkedHashSet<>(); for (String value : source) if (value != null && !value.isBlank()) result.add(value.toLowerCase(Locale.ROOT)); return Set.copyOf(result); }
    private static Set<ChatChannel> channels(List<String> source, Set<ChatChannel> fallback) { if (source == null || source.isEmpty()) return fallback; EnumSet<ChatChannel> values = EnumSet.noneOf(ChatChannel.class); for (String value : source) { ChatChannel channel = ChatChannel.fromName(value); if (channel != null) values.add(channel); } return values.isEmpty() ? fallback : Set.copyOf(values); }
    private static List<String> stringList(Object value) { if (value instanceof List<?> values) return values.stream().map(String::valueOf).toList(); if (value instanceof String text) return List.of(text); return List.of(); }

    public record Scheduler(boolean enabled, long initialDelaySeconds, long minIntervalSeconds, long maxIntervalSeconds,
                            int minOnline, boolean avoidImmediateRepeat, boolean pauseWhenEmpty, String randomizerMode, int historySize) { }
    public record Defaults(int durationSeconds, boolean revealAnswerOnTimeout, int minOnline, String permission,
                           Set<String> worlds, Set<String> excludedWorlds, Set<ChatChannel> acceptedChannels, Matching matching) { }
    public record Presentation(int blankLinesBefore, int blankLinesAfter, String separator,
                               Map<String, List<String>> cards, Map<ChatEventEngine.Type, String> typeNames) {
        public Presentation { Map<String, List<String>> copy = new LinkedHashMap<>(); cards.forEach((key, value) -> copy.put(key, List.copyOf(value))); cards = Map.copyOf(copy); typeNames = Map.copyOf(typeNames); }
        public List<String> card(String state) { return cards.getOrDefault(state, List.of()); }
        public String typeName(ChatEventEngine.Type type) { return typeNames.getOrDefault(type, type.name()); }
    }
    public record BingoLobby(int durationSeconds, List<Integer> remindersSeconds, int minimumParticipants,
                             int adminMinimumParticipants, int adminLobbySeconds, int adminFastLobbySeconds,
                             boolean allowLateJoin, boolean requireOnlineAtStart, String disconnectPolicy) {
        public BingoLobby { remindersSeconds = List.copyOf(remindersSeconds); }
    }
    public enum BingoRenderStyle { TABLE, COMPACT }
    public record BingoRender(BingoRenderStyle style, String font, int leftPadding, int cellWidth, int columnGap, boolean showBorder,
                              boolean showLastCall, boolean showDrawCount, boolean onJoin, boolean onStart,
                              boolean afterSuccessfulMark, boolean onEveryDraw) { }
    public record BingoSettings(boolean enabled, int firstDrawDelaySeconds, int drawIntervalSeconds,
                                Set<BingoPattern> winPatterns, BingoLobby lobby, boolean freeCenter,
                                BingoRender render, BingoDiscord discord, Map<String, List<String>> messages) {
        public BingoSettings {
            winPatterns = Set.copyOf(winPatterns);
            Map<String, List<String>> copy = new LinkedHashMap<>(); messages.forEach((key, value) -> copy.put(key, List.copyOf(value))); messages = Map.copyOf(copy);
        }
        public List<String> message(String key) { return messages.getOrDefault(key, List.of()); }
        public static BingoSettings defaults() {
            return new BingoSettings(true, 5, 5, Set.of(BingoPattern.ROW, BingoPattern.COLUMN, BingoPattern.DIAGONAL),
                    new BingoLobby(60, List.of(60, 30, 15, 5), 2, 1, 15, 5, false, true, "KEEP"), false,
                    new BingoRender(BingoRenderStyle.TABLE, "minecraft:uniform", 2, 4, 0, true, true, true, true, true, true, false),
                    new BingoDiscord(false, "", "PlexonChats Bingo", true, true, true),
                    Map.of(
                            "lobby", List.of("{separator}", "<gold><bold>✦ BINGO • STARTS IN {lobby_time}</bold></gold>", "", "<gray>Reward:</gray> <gold>{reward}</gold>", "<click:run_command:'/bingo join'><hover:show_text:'<gray>Join this Bingo round</gray>'><aqua><bold>[ JOIN BINGO ]</bold></aqua></hover></click>", "", "<gray>Joined:</gray> <white>{participant_count}</white>", "{separator}"),
                            "reminder", List.of("<gold>✦ BINGO</gold> <gray>•</gray> <yellow>{lobby_seconds}s until start</yellow> <click:run_command:'/bingo join'><aqua><bold>[ JOIN ]</bold></aqua></click>"),
                            "draw", List.of("<gold>◈ BINGO</gold> <gray>•</gray> <white>{drawn_number}</white> <gray>•</gray> <yellow>{draw_count}/75</yellow>"),
                            "invalid-claim", List.of("<red>✕ No completed Bingo pattern yet.</red>")));
        }
    }
    public record BingoDiscord(boolean enabled, String webhookUrl, String username, boolean sendStart, boolean sendDraws, boolean sendWin) { }
    public record Matching(boolean caseSensitive, boolean trim, boolean collapseWhitespace, Normalizer.Form normalization, boolean ignoreDiacritics) { public static Matching standard() { return new Matching(false, true, true, Normalizer.Form.NFKC, false); } }
    public record RewardProfile(boolean economyEnabled, double economyAmount, boolean keysEnabled, String keyTier, long keyAmount, List<String> consoleCommands) { public RewardProfile { consoleCommands = List.copyOf(consoleCommands); } }
    public record TriviaEntry(String question, List<String> acceptedAnswers) { public TriviaEntry { acceptedAnswers = List.copyOf(acceptedAnswers); } }
    public record SoundSpec(String sound, float volume, float pitch) { }
    public record Definition(String id, String name, ChatEventEngine.Type type, boolean enabled, int weight, long cooldownSeconds,
                             String rewardProfile, int durationSeconds, boolean revealAnswerOnTimeout, int minOnline,
                             String permission, Set<String> worlds, Set<String> excludedWorlds,
                             Set<ChatChannel> acceptedChannels, Matching matching, String prompt, List<String> values,
                             List<String> words, List<TriviaEntry> trivia, Set<ChatEventEngine.MathOperation> mathOperations,
                             int minOperand, int maxOperand, boolean allowNegativeResult, BingoSettings bingo) {
        public Definition { worlds = Set.copyOf(worlds); excludedWorlds = Set.copyOf(excludedWorlds); acceptedChannels = Set.copyOf(acceptedChannels); values = List.copyOf(values); words = List.copyOf(words); trivia = List.copyOf(trivia); mathOperations = Set.copyOf(mathOperations); }
    }
}
