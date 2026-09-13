package com.antondev.chats.event;

import com.antondev.chats.storage.ChatEventDatabase;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Cache-first statistics facade. SQLite work is serialized on one dedicated bounded executor. */
public final class ChatEventStatisticsService implements AutoCloseable {
    public enum State { STARTING, READY, DEGRADED, CLOSED }

    public record PlayerStats(UUID playerId, String playerName, long totalWins, Map<String, Long> typeWins,
                              Map<String, Long> definitionWins, Long firstWinAt, Long lastWinAt) {
        public PlayerStats {
            typeWins = Map.copyOf(typeWins);
            definitionWins = Map.copyOf(definitionWins);
        }
        public long typeWins(String type) { return typeWins.getOrDefault(type.toUpperCase(Locale.ROOT), 0L); }
        public long definitionWins(String id) { return definitionWins.getOrDefault(id.toUpperCase(Locale.ROOT), 0L); }
        public static PlayerStats empty(UUID id, String name) { return new PlayerStats(id, name, 0, Map.of(), Map.of(), null, null); }
    }

    public record RecentWin(UUID runId, UUID playerId, String playerName, String eventId, String eventType,
                            String rewardProfile, long wonAt, long elapsedMs) { }

    private final Logger logger;
    private final ChatEventDatabase database;
    private final ExecutorService executor;
    private final Map<UUID, PlayerStats> cache = new ConcurrentHashMap<>();
    private final Map<String, UUID> nameIndex = new ConcurrentHashMap<>();
    private final Set<UUID> acceptedRuns = ConcurrentHashMap.newKeySet();
    private final Object recentLock = new Object();
    private final AtomicInteger pendingWrites = new AtomicInteger();
    private final AtomicLong totalRecorded = new AtomicLong();
    private volatile List<RecentWin> recent = List.of();
    private volatile State state = State.STARTING;
    private volatile long lastWriteSuccess;
    private volatile String lastFailure = "NONE";

    public ChatEventStatisticsService(Path file, Logger logger) {
        this.logger = logger;
        this.database = new ChatEventDatabase(file);
        this.executor = Executors.newSingleThreadExecutor(runnable -> Thread.ofPlatform()
                .name("PlexonChats-ChatEventDB").daemon(true).unstarted(runnable));
        execute(this::initialize, false);
    }

    private void initialize() {
        try {
            database.initialize();
            reloadCacheFromDatabase();
            state = State.READY;
        } catch (Exception ex) {
            fail("database initialization", ex);
        }
    }

    /**
     * Updates cache immediately for winner presentation and queues the durable transaction.
     * Duplicate run IDs are idempotent before they reach SQLite.
     */
    public PlayerStats recordWin(UUID runId, UUID playerId, String playerName, String eventId, ChatEventEngine.Type type,
                                 String rewardProfile, long elapsedMs) {
        if (runId == null || playerId == null || !acceptedRuns.add(runId)) return get(playerId, playerName);
        long now = System.currentTimeMillis();
        String typeName = type.name();
        PlayerStats updated = cache.compute(playerId, (id, old) -> increment(old == null ? PlayerStats.empty(id, playerName) : old,
                playerName, eventId, typeName, now));
        nameIndex.put(playerName.toLowerCase(Locale.ROOT), playerId);
        totalRecorded.incrementAndGet();
        addRecent(new RecentWin(runId, playerId, playerName, eventId, typeName, rewardProfile, now, elapsedMs));
        pendingWrites.incrementAndGet();
        ChatEventDatabase.WinRecord record = new ChatEventDatabase.WinRecord(runId, playerId, playerName, eventId, typeName,
                rewardProfile, now, elapsedMs);
        execute(() -> persist(record), true);
        return updated;
    }

    private void persist(ChatEventDatabase.WinRecord record) {
        try {
            boolean inserted = database.recordWin(record);
            if (!inserted) {
                // Persistence is authoritative across restarts; repair an optimistic duplicate defensively.
                reloadCacheFromDatabase();
            } else {
                lastWriteSuccess = System.currentTimeMillis();
                if (state != State.CLOSED) state = State.READY;
            }
        } catch (SQLException first) {
            // One bounded retry handles transient SQLITE_BUSY without creating a retry loop.
            try {
                Thread.sleep(100L);
                boolean inserted = database.recordWin(record);
                if (!inserted) reloadCacheFromDatabase();
                lastWriteSuccess = System.currentTimeMillis();
                if (state != State.CLOSED) state = State.READY;
            } catch (Exception second) {
                fail("win persistence for run " + record.runId(), second);
            }
        } finally {
            pendingWrites.decrementAndGet();
        }
    }

    private static PlayerStats increment(PlayerStats old, String name, String eventId, String type, long now) {
        Map<String, Long> types = new LinkedHashMap<>(old.typeWins());
        types.merge(type.toUpperCase(Locale.ROOT), 1L, Long::sum);
        Map<String, Long> definitions = new LinkedHashMap<>(old.definitionWins());
        definitions.merge(eventId.toUpperCase(Locale.ROOT), 1L, Long::sum);
        return new PlayerStats(old.playerId(), name, old.totalWins() + 1, types, definitions,
                old.firstWinAt() == null ? now : old.firstWinAt(), now);
    }

    private void reloadCacheFromDatabase() throws SQLException {
        List<ChatEventDatabase.StoredPlayer> players = database.loadPlayers();
        Map<UUID, PlayerStats> next = new LinkedHashMap<>();
        Map<String, UUID> names = new LinkedHashMap<>();
        for (ChatEventDatabase.StoredPlayer player : players) {
            PlayerStats stats = new PlayerStats(player.playerId(), player.name(), player.totalWins(), player.typeWins(),
                    player.definitionWins(), player.firstWinAt(), player.lastWinAt());
            next.put(player.playerId(), stats);
            names.put(player.name().toLowerCase(Locale.ROOT), player.playerId());
        }
        cache.clear();
        cache.putAll(next);
        nameIndex.clear();
        nameIndex.putAll(names);
        List<RecentWin> loaded = database.loadRecent(25).stream().map(value -> new RecentWin(value.runId(), value.playerId(),
                value.playerName(), value.eventId(), value.eventType(), value.rewardProfile(), value.wonAt(), value.elapsedMs())).toList();
        synchronized (recentLock) { recent = loaded; }
        totalRecorded.set(database.historyCount());
    }

    private void addRecent(RecentWin value) {
        synchronized (recentLock) {
            ArrayList<RecentWin> values = new ArrayList<>(Math.min(25, recent.size() + 1));
            values.add(value);
            for (RecentWin existing : recent) {
                if (values.size() >= 25) break;
                if (!existing.runId().equals(value.runId())) values.add(existing);
            }
            recent = List.copyOf(values);
        }
    }

    public PlayerStats get(UUID playerId, String fallbackName) {
        return cache.getOrDefault(playerId, PlayerStats.empty(playerId, fallbackName == null ? playerId.toString() : fallbackName));
    }
    public PlayerStats getByName(String name) {
        if (name == null) return null;
        UUID id = nameIndex.get(name.toLowerCase(Locale.ROOT));
        return id == null ? null : cache.get(id);
    }
    public long getTotalWins(UUID playerId) { PlayerStats stats = cache.get(playerId); return stats == null ? 0 : stats.totalWins(); }
    public long getWinsByType(UUID playerId, ChatEventEngine.Type type) { PlayerStats stats = cache.get(playerId); return stats == null ? 0 : stats.typeWins(type.name()); }
    public long getWinsByDefinition(UUID playerId, String eventId) { PlayerStats stats = cache.get(playerId); return stats == null ? 0 : stats.definitionWins(eventId); }
    public List<PlayerStats> getTopPlayers(int limit) {
        return cache.values().stream().sorted(Comparator.comparingLong(PlayerStats::totalWins).reversed()
                .thenComparing(PlayerStats::playerName, String.CASE_INSENSITIVE_ORDER)).limit(Math.max(1, Math.min(limit, 100))).toList();
    }
    public List<RecentWin> getRecentWins(int limit) {
        List<RecentWin> snapshot = recent;
        return snapshot.subList(0, Math.min(snapshot.size(), Math.max(0, limit)));
    }
    public int cachedPlayerCount() { return cache.size(); }
    public long totalRecordedWins() { return totalRecorded.get(); }
    public State state() { return state; }
    public Path file() { return database.file(); }
    public int schemaVersion() { return ChatEventDatabase.SCHEMA_VERSION; }
    public int pendingWrites() { return pendingWrites.get(); }
    public long lastWriteSuccess() { return lastWriteSuccess; }
    public String lastWriteSuccessText() { return lastWriteSuccess == 0 ? "NEVER" : Instant.ofEpochMilli(lastWriteSuccess).toString(); }
    public String lastFailure() { return lastFailure; }
    public String executorState() { return executor.isShutdown() ? "SHUTDOWN" : executor.isTerminated() ? "TERMINATED" : "RUNNING"; }

    private void execute(ThrowingRunnable task, boolean write) {
        try {
            executor.execute(() -> {
                try { task.run(); }
                catch (Exception ex) {
                    if (write) pendingWrites.updateAndGet(value -> Math.max(0, value - 1));
                    fail("database task", ex);
                }
            });
        } catch (RejectedExecutionException ex) {
            if (write) pendingWrites.updateAndGet(value -> Math.max(0, value - 1));
            fail("database executor rejected task", ex);
        }
    }

    private void fail(String operation, Exception ex) {
        if (state != State.CLOSED) state = State.DEGRADED;
        lastFailure = operation + ": " + ex.getClass().getSimpleName() + (ex.getMessage() == null ? "" : " — " + ex.getMessage());
        logger.log(Level.SEVERE, "Chat Events statistics " + operation + " failed", ex);
    }

    @Override public void close() {
        state = State.CLOSED;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    @FunctionalInterface private interface ThrowingRunnable { void run() throws Exception; }
}
