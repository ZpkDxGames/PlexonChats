package com.antondev.chats.config;

import com.antondev.chats.PlexonChats;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Owns plugins/PlexonChats/data.yml. Runtime state is never written here. */
public final class ChatEventDataStore {
    public static final int SCHEMA_VERSION = 1;
    private static final Set<String> EVENT_KEYS = Set.of(
            "type", "name", "enabled", "weight", "cooldown-seconds", "duration-seconds", "reward-profile",
            "min-online", "accepted-channels", "permission", "worlds", "excluded-worlds", "prompt", "values",
            "words", "entries", "operations", "min-operand", "max-operand", "allow-negative-result", "matching");

    private final PlexonChats plugin;
    private final Path path;
    private final YamlConfiguration defaults = new YamlConfiguration();
    private volatile YamlConfiguration data;

    public ChatEventDataStore(PlexonChats plugin) {
        this.plugin = plugin;
        this.path = plugin.getDataFolder().toPath().resolve("data.yml");
        try (var reader = new InputStreamReader(Objects.requireNonNull(plugin.getResource("data.yml")), StandardCharsets.UTF_8)) {
            defaults.load(reader);
        } catch (IOException | InvalidConfigurationException ex) {
            throw new IllegalStateException("Cannot read bundled data.yml", ex);
        }
        reload();
    }

    public synchronized void reload() {
        try {
            if (!Files.exists(path)) {
                YamlConfiguration migrated = cloneYaml(defaults);
                migrateLegacyGameplay(migrated, plugin.getConfigManager().section("chat-events"));
                validateAndSanitize(migrated);
                backupLegacyConfig();
                AtomicFiles.write(path, migrated.saveToString());
                data = migrated;
                plugin.getLogger().info("Created data.yml schema 1 and migrated existing Chat Events gameplay values; integration secrets remained in config.yml.");
                return;
            }
            YamlConfiguration candidate = new YamlConfiguration();
            candidate.options().parseComments(true);
            candidate.load(path.toFile());
            int schema = candidate.getInt("schema-version", 0);
            if (schema > SCHEMA_VERSION) throw new IllegalArgumentException("data.yml was created by a newer PlexonChats version");
            if (schema < SCHEMA_VERSION) {
                Path backup = path.resolveSibling("data-before-v" + SCHEMA_VERSION + "-" + System.currentTimeMillis() + ".yml");
                Files.copy(path, backup, StandardCopyOption.COPY_ATTRIBUTES);
                mergeMissing(candidate, defaults);
                candidate.set("schema-version", SCHEMA_VERSION);
                validateAndSanitize(candidate);
                AtomicFiles.write(path, candidate.saveToString());
                plugin.getLogger().info("Migrated data.yml to schema " + SCHEMA_VERSION + "; backup=" + backup.getFileName());
            } else {
                validateAndSanitize(candidate);
            }
            data = candidate;
        } catch (IOException | InvalidConfigurationException | IllegalArgumentException ex) {
            throw new IllegalStateException("data.yml could not be applied: " + ex.getMessage(), ex);
        }
    }

    private void backupLegacyConfig() throws IOException {
        Path config = plugin.getDataFolder().toPath().resolve("config.yml");
        if (!Files.exists(config)) return;
        Path backup = config.resolveSibling("config-before-v4-" + System.currentTimeMillis() + ".yml");
        Files.copy(config, backup, StandardCopyOption.COPY_ATTRIBUTES);
    }

    private void migrateLegacyGameplay(YamlConfiguration target, ConfigurationSection legacy) {
        if (legacy == null) return;
        // The global Chat Events master switch remains in config.yml so existing admin controls retain authority.
        copy(target, "scheduler.enabled", legacy, "scheduler.enabled");
        copy(target, "scheduler.initial-delay-seconds", legacy, "scheduler.initial-delay-seconds");
        copy(target, "scheduler.min-interval-seconds", legacy, "scheduler.min-interval-seconds");
        copy(target, "scheduler.max-interval-seconds", legacy, "scheduler.max-interval-seconds");
        if (legacy.contains("scheduler.min-online")) target.set("scheduler.minimum-online", legacy.getInt("scheduler.min-online"));
        copy(target, "scheduler.pause-when-empty", legacy, "scheduler.pause-when-empty");
        if (legacy.contains("scheduler.avoid-immediate-repeat")) target.set("randomizer.avoid-immediate-repeat", legacy.getBoolean("scheduler.avoid-immediate-repeat"));

        ConfigurationSection events = legacy.getConfigurationSection("events");
        if (events != null) {
            for (String id : events.getKeys(false)) {
                ConfigurationSection source = events.getConfigurationSection(id);
                if (source == null) continue;
                String base = "minigames." + id;
                for (String key : EVENT_KEYS) if (source.contains(key)) copyDeep(target, base + "." + key, source, key);
                if (!source.contains("type")) target.set(base + ".type", inferType(id));
            }
        }

        ConfigurationSection legacyBingo = legacy.getConfigurationSection("bingo");
        if (legacyBingo != null) {
            String id = firstBingoId(target);
            if (id != null) {
                String base = "minigames." + id;
                if (legacyBingo.contains("draw.first-delay-seconds")) target.set(base + ".draws.first-call-delay-seconds", legacyBingo.getInt("draw.first-delay-seconds"));
                if (legacyBingo.contains("draw.interval-seconds")) target.set(base + ".draws.interval-seconds", legacyBingo.getInt("draw.interval-seconds"));
                for (String key : List.of("horizontal", "vertical", "diagonal", "full-house")) {
                    if (legacyBingo.contains("winning." + key)) target.set(base + ".winning." + key, legacyBingo.getBoolean("winning." + key));
                }
            }
        }
    }

    private static String firstBingoId(YamlConfiguration yaml) {
        ConfigurationSection minigames = yaml.getConfigurationSection("minigames");
        if (minigames == null) return null;
        for (String id : minigames.getKeys(false)) {
            ConfigurationSection section = minigames.getConfigurationSection(id);
            if (section != null && "BINGO".equalsIgnoreCase(section.getString("type", inferType(id)))) return id;
        }
        return null;
    }

    private void validateAndSanitize(YamlConfiguration yaml) {
        if (yaml.getInt("schema-version", 0) != SCHEMA_VERSION) throw new IllegalArgumentException("schema-version must be " + SCHEMA_VERSION);
        long min = yaml.getLong("scheduler.min-interval-seconds", 900);
        long max = yaml.getLong("scheduler.max-interval-seconds", 1800);
        if (min <= 0 || max <= 0 || min > max) throw new IllegalArgumentException("scheduler interval range is invalid");
        if (yaml.getLong("scheduler.initial-delay-seconds", 300) < 0) throw new IllegalArgumentException("scheduler.initial-delay-seconds must be non-negative");
        if (yaml.getInt("scheduler.minimum-online", 2) < 0) throw new IllegalArgumentException("scheduler.minimum-online must be non-negative");
        String mode = yaml.getString("randomizer.mode", "WEIGHTED").toUpperCase(Locale.ROOT);
        if (!Set.of("WEIGHTED", "UNIFORM").contains(mode)) throw new IllegalArgumentException("randomizer.mode must be WEIGHTED or UNIFORM");
        if (yaml.getInt("randomizer.history-size", 2) < 0) throw new IllegalArgumentException("randomizer.history-size must be non-negative");

        ConfigurationSection games = yaml.getConfigurationSection("minigames");
        if (games == null || games.getKeys(false).isEmpty()) throw new IllegalArgumentException("minigames must define at least one game");
        for (String id : games.getKeys(false)) {
            ConfigurationSection game = games.getConfigurationSection(id);
            if (game == null) { yaml.set("minigames." + id + ".enabled", false); continue; }
            try { validateGame(id, game); }
            catch (IllegalArgumentException failure) {
                yaml.set("minigames." + id + ".enabled", false);
                plugin.getLogger().warning("Disabled malformed data.yml minigame '" + id + "' for this runtime generation: " + failure.getMessage());
            }
        }
    }

    private static void validateGame(String id, ConfigurationSection game) {
        if (game.getInt("weight", 1) < 0) throw new IllegalArgumentException("weight must be non-negative");
        if (game.getLong("cooldown-seconds", 0) < 0) throw new IllegalArgumentException("cooldown must be non-negative");
        if (game.getInt("duration-seconds", 30) <= 0) throw new IllegalArgumentException("duration must be positive");
        String type = game.getString("type", inferType(id)).toUpperCase(Locale.ROOT);
        if (!Set.of("TYPE", "UNSCRAMBLE", "MATH", "TRIVIA", "REVERSE", "BINGO").contains(type)) throw new IllegalArgumentException("unknown type " + type);
        if (type.equals("MATH") && game.getInt("min-operand", 1) > game.getInt("max-operand", 10)) throw new IllegalArgumentException("math operand range is invalid");
        if (type.equals("BINGO")) validateBingo(game);
    }

    private static void validateBingo(ConfigurationSection game) {
        ConfigurationSection lobby = game.getConfigurationSection("lobby");
        if (lobby == null || lobby.getInt("duration-seconds", 60) <= 0) throw new IllegalArgumentException("Bingo lobby duration must be positive");
        int duration = lobby.getInt("duration-seconds", 60);
        List<Integer> reminders = lobby.getIntegerList("reminders-seconds");
        if (reminders.isEmpty() || reminders.stream().anyMatch(value -> value <= 0 || value > duration) || reminders.stream().distinct().count() != reminders.size()) throw new IllegalArgumentException("Bingo reminder thresholds are invalid");
        if (lobby.getInt("minimum-participants", 2) < 1 || lobby.getInt("admin-minimum-participants", 1) < 1) throw new IllegalArgumentException("Bingo minimum participants must be at least one");
        if (game.getInt("draws.first-call-delay-seconds", 5) < 0 || game.getInt("draws.interval-seconds", 8) <= 0) throw new IllegalArgumentException("Bingo draw timing is invalid");
        boolean pattern = game.getBoolean("winning.horizontal", true) || game.getBoolean("winning.vertical", true) || game.getBoolean("winning.diagonal", true) || game.getBoolean("winning.full-house", false);
        if (!pattern) throw new IllegalArgumentException("Bingo requires at least one winning pattern");
        int padding = game.getInt("render.left-padding", 3), width = game.getInt("render.cell-width", 4), gap = game.getInt("render.column-gap", 1);
        if (padding < 0 || padding > 16 || width < 2 || width > 12 || gap < 0 || gap > 8) throw new IllegalArgumentException("Bingo table dimensions are out of bounds");
    }

    private static void mergeMissing(YamlConfiguration target, YamlConfiguration source) {
        for (var entry : source.getValues(true).entrySet()) if (!target.contains(entry.getKey()) && !(entry.getValue() instanceof ConfigurationSection)) target.set(entry.getKey(), entry.getValue());
    }
    private static YamlConfiguration cloneYaml(YamlConfiguration source) throws InvalidConfigurationException {
        YamlConfiguration copy = new YamlConfiguration();
        copy.loadFromString(source.saveToString());
        return copy;
    }
    private static void copy(YamlConfiguration target, String targetPath, ConfigurationSection source, String sourcePath) { if (source.contains(sourcePath)) target.set(targetPath, source.get(sourcePath)); }
    private static void copyDeep(YamlConfiguration target, String targetPath, ConfigurationSection source, String sourcePath) {
        ConfigurationSection nested = source.getConfigurationSection(sourcePath);
        if (nested == null) { target.set(targetPath, source.get(sourcePath)); return; }
        for (var entry : nested.getValues(true).entrySet()) if (!(entry.getValue() instanceof ConfigurationSection)) target.set(targetPath + "." + entry.getKey(), entry.getValue());
    }
    private static String inferType(String id) { String value = id.toLowerCase(Locale.ROOT); if (value.contains("bingo")) return "BINGO"; if (value.contains("unscramble")) return "UNSCRAMBLE"; if (value.contains("math")) return "MATH"; if (value.contains("trivia")) return "TRIVIA"; if (value.contains("reverse")) return "REVERSE"; return "TYPE"; }

    public ConfigurationSection root() { return data; }
    public int schemaVersion() { return data == null ? 0 : data.getInt("schema-version", 0); }
    public Path path() { return path; }
}
