package com.antondev.chats.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Blocking SQLite repository. Callers must keep every method off the chat/main hot path. */
public final class ChatEventDatabase {
    public static final int SCHEMA_VERSION = 1;

    public record WinRecord(UUID runId, UUID playerId, String playerName, String eventId, String eventType,
                            String rewardProfile, long wonAt, long elapsedMs) { }
    public record StoredPlayer(UUID playerId, String name, long totalWins, Long firstWinAt, Long lastWinAt,
                               Map<String, Long> typeWins, Map<String, Long> definitionWins) {
        public StoredPlayer {
            typeWins = Map.copyOf(typeWins);
            definitionWins = Map.copyOf(definitionWins);
        }
    }
    public record RecentWin(UUID runId, UUID playerId, String playerName, String eventId, String eventType,
                            String rewardProfile, long wonAt, long elapsedMs) { }

    private final Path file;

    public ChatEventDatabase(Path file) { this.file = file.toAbsolutePath().normalize(); }
    public Path file() { return file; }

    public void initialize() throws SQLException, IOException {
        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA busy_timeout=3000");
            statement.execute("CREATE TABLE IF NOT EXISTS chat_event_players (" +
                    "player_uuid TEXT PRIMARY KEY," +
                    "last_known_name TEXT NOT NULL," +
                    "total_wins INTEGER NOT NULL DEFAULT 0," +
                    "first_win_at INTEGER," +
                    "last_win_at INTEGER," +
                    "updated_at INTEGER NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS chat_event_type_wins (" +
                    "player_uuid TEXT NOT NULL," +
                    "event_type TEXT NOT NULL," +
                    "wins INTEGER NOT NULL DEFAULT 0," +
                    "last_win_at INTEGER," +
                    "PRIMARY KEY (player_uuid, event_type))");
            statement.execute("CREATE TABLE IF NOT EXISTS chat_event_definition_wins (" +
                    "player_uuid TEXT NOT NULL," +
                    "event_id TEXT NOT NULL," +
                    "wins INTEGER NOT NULL DEFAULT 0," +
                    "last_win_at INTEGER," +
                    "PRIMARY KEY (player_uuid, event_id))");
            statement.execute("CREATE TABLE IF NOT EXISTS chat_event_history (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "run_id TEXT NOT NULL UNIQUE," +
                    "player_uuid TEXT NOT NULL," +
                    "player_name TEXT NOT NULL," +
                    "event_id TEXT NOT NULL," +
                    "event_type TEXT NOT NULL," +
                    "reward_profile TEXT," +
                    "won_at INTEGER NOT NULL," +
                    "elapsed_ms INTEGER NOT NULL)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_chat_event_history_won_at ON chat_event_history(won_at DESC)");
            statement.execute("PRAGMA user_version=" + SCHEMA_VERSION);
        }
    }

    /** Returns false when this run_id already exists; no aggregate counters are changed in that case. */
    public boolean recordWin(WinRecord win) throws SQLException {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                int inserted;
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT OR IGNORE INTO chat_event_history(run_id,player_uuid,player_name,event_id,event_type,reward_profile,won_at,elapsed_ms) VALUES(?,?,?,?,?,?,?,?)")) {
                    statement.setString(1, win.runId().toString());
                    statement.setString(2, win.playerId().toString());
                    statement.setString(3, win.playerName());
                    statement.setString(4, win.eventId());
                    statement.setString(5, win.eventType());
                    statement.setString(6, win.rewardProfile());
                    statement.setLong(7, win.wonAt());
                    statement.setLong(8, win.elapsedMs());
                    inserted = statement.executeUpdate();
                }
                if (inserted == 0) {
                    connection.rollback();
                    return false;
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO chat_event_players(player_uuid,last_known_name,total_wins,first_win_at,last_win_at,updated_at) VALUES(?,?,1,?,?,?) " +
                                "ON CONFLICT(player_uuid) DO UPDATE SET last_known_name=excluded.last_known_name,total_wins=chat_event_players.total_wins+1,last_win_at=excluded.last_win_at,updated_at=excluded.updated_at")) {
                    statement.setString(1, win.playerId().toString());
                    statement.setString(2, win.playerName());
                    statement.setLong(3, win.wonAt());
                    statement.setLong(4, win.wonAt());
                    statement.setLong(5, win.wonAt());
                    statement.executeUpdate();
                }
                upsertCounter(connection, "chat_event_type_wins", "event_type", win.playerId(), win.eventType(), win.wonAt());
                upsertCounter(connection, "chat_event_definition_wins", "event_id", win.playerId(), win.eventId(), win.wonAt());
                connection.commit();
                return true;
            } catch (SQLException failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private static void upsertCounter(Connection connection, String table, String keyColumn, UUID playerId, String key, long timestamp) throws SQLException {
        String sql = "INSERT INTO " + table + "(player_uuid," + keyColumn + ",wins,last_win_at) VALUES(?,?,1,?) " +
                "ON CONFLICT(player_uuid," + keyColumn + ") DO UPDATE SET wins=" + table + ".wins+1,last_win_at=excluded.last_win_at";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, key);
            statement.setLong(3, timestamp);
            statement.executeUpdate();
        }
    }

    public List<StoredPlayer> loadPlayers() throws SQLException {
        Map<UUID, MutableStoredPlayer> players = new LinkedHashMap<>();
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("SELECT player_uuid,last_known_name,total_wins,first_win_at,last_win_at FROM chat_event_players");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                UUID id = UUID.fromString(result.getString(1));
                Long first = nullableLong(result, 4);
                Long last = nullableLong(result, 5);
                players.put(id, new MutableStoredPlayer(id, result.getString(2), result.getLong(3), first, last));
            }
            loadCounters(connection, players, "chat_event_type_wins", "event_type", true);
            loadCounters(connection, players, "chat_event_definition_wins", "event_id", false);
        }
        return players.values().stream().map(MutableStoredPlayer::freeze).toList();
    }

    private static void loadCounters(Connection connection, Map<UUID, MutableStoredPlayer> players, String table,
                                     String keyColumn, boolean type) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT player_uuid," + keyColumn + ",wins FROM " + table);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                MutableStoredPlayer player = players.get(UUID.fromString(result.getString(1)));
                if (player == null) continue;
                (type ? player.typeWins : player.definitionWins).put(result.getString(2).toUpperCase(Locale.ROOT), result.getLong(3));
            }
        }
    }

    public List<RecentWin> loadRecent(int limit) throws SQLException {
        List<RecentWin> values = new ArrayList<>();
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT run_id,player_uuid,player_name,event_id,event_type,reward_profile,won_at,elapsed_ms FROM chat_event_history ORDER BY won_at DESC,id DESC LIMIT ?")) {
            statement.setInt(1, Math.max(1, Math.min(limit, 100)));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) values.add(new RecentWin(
                        UUID.fromString(result.getString(1)), UUID.fromString(result.getString(2)), result.getString(3),
                        result.getString(4), result.getString(5), result.getString(6), result.getLong(7), result.getLong(8)));
            }
        }
        return List.copyOf(values);
    }

    public long historyCount() throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM chat_event_history")) {
            return result.next() ? result.getLong(1) : 0;
        }
    }

    private Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
        try (Statement statement = connection.createStatement()) { statement.execute("PRAGMA busy_timeout=3000"); }
        return connection;
    }

    private static Long nullableLong(ResultSet result, int index) throws SQLException {
        long value = result.getLong(index);
        return result.wasNull() ? null : value;
    }

    private static final class MutableStoredPlayer {
        private final UUID id;
        private final String name;
        private final long total;
        private final Long first;
        private final Long last;
        private final Map<String, Long> typeWins = new LinkedHashMap<>();
        private final Map<String, Long> definitionWins = new LinkedHashMap<>();
        private MutableStoredPlayer(UUID id, String name, long total, Long first, Long last) {
            this.id = id; this.name = name; this.total = total; this.first = first; this.last = last;
        }
        private StoredPlayer freeze() { return new StoredPlayer(id, name, total, first, last, typeWins, definitionWins); }
    }
}
