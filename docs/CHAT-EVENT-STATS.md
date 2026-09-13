# Chat Event Win Statistics

PlexonChats 3.4.0 persists Chat Event wins in a dedicated SQLite database:

```text
plugins/PlexonChats/chat-events.db
```

Statistics are separate from `players.yml` and ordinary chat preferences.

## Recorded data

The database maintains:

- total wins per player UUID;
- wins per event type;
- wins per event definition ID;
- first/last win timestamps and last known player name;
- immutable winner history containing run ID, event, type, reward profile and elapsed time.

Player identity is UUID-based. A later player-name change updates the last known name without creating a second statistical identity.

## Exact-once boundary

A completed run has either zero winners or one winner. The winner history table uses `run_id` as a unique key. Persistence inserts the history row first inside one transaction; aggregate counters are changed only if that unique insert succeeds.

Therefore a duplicate callback for the same run cannot increment totals, type counters or definition counters twice.

Timeouts, cancellations, failed Bingo joins, invalid Bingo marks, late answers and reward retries do not create wins.

## I/O and performance

The main chat path performs no SQLite reads or writes. PlexonChats uses one dedicated database executor and prepared/transactional repository operations. SQLite is configured with WAL mode and a bounded busy timeout.

Winner presentation updates the in-memory cache immediately, allowing `{winner_total_wins}` and `{winner_type_wins}` to be rendered without waiting for a cold database read. The confirmed write then runs asynchronously. Plugin shutdown drains the controlled executor before closing during ordinary shutdown.

There is no thread-per-event or query-per-placeholder model.

## Commands

```text
/chat events stats
/chat events stats <player>
/chat events leaderboard
```

`/chat events stats` displays the sender's cached statistics. Inspecting another player requires `plexonchats.events.stats.others`. The leaderboard reads the in-memory cache rather than scanning SQLite on the main server thread.

## GUI

The Chat Events Statistics page includes the viewer's total/per-type counters, top winners and current database health. The main Events dashboard also exposes the total number of recorded wins and database state.

## Diagnostics

`/chat diagnostics` exposes:

```text
Chat Events DB state
Chat Events DB file
Chat Events DB schema
Chat Events DB executor
Chat Events DB pending writes
Chat Events DB last write
Chat Events DB last failure
Recorded Chat Event wins
Cached event players
```

A database failure never reopens an event, selects a second winner, reruns a reward or blocks ordinary chat. The service records degraded state and diagnostics instead.

## Backup and rollback

Back up `chat-events.db` together with the PlexonChats plugin data directory when taking server backups. Rolling the JAR back to v3.3.0 does not require deleting the database; v3.3.0 simply does not use the 3.4 statistics subsystem. Preserve the file if a later return to 3.4.0 is expected.
