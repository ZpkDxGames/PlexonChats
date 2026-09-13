package com.antondev.chats.event.discord;

import com.antondev.chats.PlexonChats;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Serializes Discord lifecycle writes per run, coalesces live updates and makes terminal states immutable.
 * The bounded worker may wait on Discord futures, but no wait/network operation ever occurs on Paper's main thread.
 */
public final class ChatEventDiscordPublisher implements AutoCloseable {
    private static final int MAX_TRACKED_RUNS = 8;
    private final PlexonChats plugin;
    private final DiscordEventSettings settings;
    private final DiscordEventTransport transport;
    private final ThreadPoolExecutor executor;
    private final Map<UUID, RunState> runs = new LinkedHashMap<>();
    private final AtomicBoolean warned = new AtomicBoolean();
    private volatile boolean closed;

    public ChatEventDiscordPublisher(PlexonChats plugin, DiscordEventSettings settings) {
        this(plugin, settings, DiscordEventTransportFactory.create(plugin, settings));
    }

    /** Visible for deterministic publisher tests. */
    public ChatEventDiscordPublisher(PlexonChats plugin, DiscordEventSettings settings, DiscordEventTransport transport) {
        this.plugin = plugin;
        this.settings = settings;
        this.transport = transport;
        this.executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(8), runnable -> {
                    Thread thread = new Thread(runnable, "PlexonChats-Discord-Events");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    public boolean enabled() { return settings.enabled(); }
    public String transportName() { return transport.name(); }
    public String transportStatus() { return transport.status(); }
    public boolean channelConfigured() { return transport.channelConfigured(); }
    public String participationMode() { return settings.participationMode().name(); }
    public DiscordEventSettings settings() { return settings; }

    public void start(UUID runId, DiscordEventEmbed embed) {
        request(runId, embed, false, true);
    }

    public void update(UUID runId, DiscordEventEmbed embed) {
        request(runId, embed, false, false);
    }

    public void terminal(UUID runId, DiscordEventEmbed embed) {
        request(runId, embed, true, false);
    }

    public CompletableFuture<DiscordEventMessageRef> test(DiscordEventEmbed embed) {
        if (closed || !settings.enabled()) return CompletableFuture.failedFuture(new IllegalStateException("Discord event sync is disabled"));
        return transport.create(embed);
    }

    public synchronized String messageState(UUID runId) {
        if (runId == null) return "NONE";
        RunState state = runs.get(runId);
        if (state == null) return "NONE";
        if (state.terminalApplied) return "TERMINAL";
        if (state.ref != null) return state.desired == null ? "ACTIVE" : "PENDING_UPDATE";
        return state.working ? "CREATING" : "PENDING_CREATE";
    }

    public synchronized boolean pending(UUID runId) {
        RunState state = runId == null ? null : runs.get(runId);
        return state != null && (state.desired != null || state.working);
    }

    private void request(UUID runId, DiscordEventEmbed embed, boolean terminal, boolean start) {
        if (closed || !settings.enabled() || runId == null || embed == null) return;
        synchronized (this) {
            RunState state = runs.computeIfAbsent(runId, ignored -> new RunState());
            if (state.terminalApplied || (state.terminalRequested && !terminal)) return;
            if (start && state.sequence > 0) return;
            if (terminal) state.terminalRequested = true;
            state.desired = new Update(++state.sequence, embed, terminal);
            trimRuns(runId);
            if (!state.working) schedule(runId, state);
        }
    }

    private void schedule(UUID runId, RunState state) {
        state.working = true;
        try { executor.execute(() -> process(runId)); }
        catch (RuntimeException rejected) {
            state.working = false;
            warn("Discord event publisher queue is full; event gameplay is unaffected.");
        }
    }

    private void process(UUID runId) {
        while (!closed) {
            Update update;
            DiscordEventMessageRef ref;
            long delayMillis;
            synchronized (this) {
                RunState state = runs.get(runId);
                if (state == null) return;
                update = state.desired;
                if (update == null || state.terminalApplied) { state.working = false; return; }
                state.desired = null;
                ref = state.ref;
                long minimumNanos = TimeUnit.MILLISECONDS.toNanos(settings.updates().minimumEditIntervalMs());
                long remaining = state.lastWriteNanos + minimumNanos - System.nanoTime();
                delayMillis = update.terminal() ? 0 : Math.max(0, TimeUnit.NANOSECONDS.toMillis(remaining));
            }

            if (delayMillis > 0 && !sleep(delayMillis)) return;
            if (closed) return;

            try {
                DiscordEventMessageRef created = write(ref, update.embed());
                synchronized (this) {
                    RunState state = runs.get(runId);
                    if (state == null) return;
                    if (ref == null) state.ref = created;
                    state.lastWriteNanos = System.nanoTime();
                    warned.set(false);
                    if (update.terminal()) {
                        state.terminalApplied = true;
                        state.desired = null;
                        state.working = false;
                        return;
                    }
                }
            } catch (Exception failure) {
                synchronized (this) {
                    RunState state = runs.get(runId);
                    if (state == null) return;
                    boolean newer = state.desired != null && state.desired.sequence() > update.sequence();
                    if (!newer && ref != null && !update.terminal() && !state.terminalRequested && !state.recreationUsed) {
                        // At most one recreation per run for a deleted/inaccessible live message.
                        state.ref = null;
                        state.recreationUsed = true;
                        state.desired = update;
                    } else if (!newer && update.terminal()) {
                        state.terminalApplied = true; // terminal gameplay state remains immutable even if Discord failed.
                    }
                    warn("Discord event publication failed (" + failure.getClass().getSimpleName() + "); event gameplay is unaffected.");
                }
            }
        }
        synchronized (this) {
            RunState state = runs.get(runId);
            if (state != null) state.working = false;
        }
    }

    private DiscordEventMessageRef write(DiscordEventMessageRef ref, DiscordEventEmbed embed)
            throws InterruptedException, ExecutionException, TimeoutException {
        if (ref == null || !settings.updates().editExistingMessage()) {
            return transport.create(embed).get(12, TimeUnit.SECONDS);
        }
        transport.edit(ref, embed).get(12, TimeUnit.SECONDS);
        return ref;
    }

    private boolean sleep(long millis) {
        try { Thread.sleep(Math.min(millis, 30_000)); return true; }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return false; }
    }

    private void trimRuns(UUID keep) {
        while (runs.size() > MAX_TRACKED_RUNS) {
            UUID first = runs.keySet().iterator().next();
            if (first.equals(keep) && runs.size() > 1) first = runs.keySet().stream().filter(id -> !id.equals(keep)).findFirst().orElse(first);
            runs.remove(first);
        }
    }

    private void warn(String message) {
        if (warned.compareAndSet(false, true)) plugin.getLogger().warning(message);
    }

    @Override public void close() {
        closed = true;
        synchronized (this) { runs.clear(); }
        executor.shutdownNow();
        transport.close();
    }

    private record Update(long sequence, DiscordEventEmbed embed, boolean terminal) { }
    private static final class RunState {
        private long sequence;
        private DiscordEventMessageRef ref;
        private Update desired;
        private long lastWriteNanos;
        private boolean terminalRequested;
        private boolean terminalApplied;
        private boolean recreationUsed;
        private boolean working;
    }
}
