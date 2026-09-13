# Upgrading PlexonChats

## 3.3.0 → 3.4.0

PlexonChats 3.4.0 expands Chat Events while preserving the accepted v3.3.0 chat/event architecture. Configuration advances **v5 → v6**.

### Before installing

1. Stage on Paper **26.2 / Java 25** first.
2. Stop the server.
3. Back up `PlexonChats-3.3.0.jar` and the entire `plugins/PlexonChats/` directory.
4. Replace only the JAR with `PlexonChats-3.4.0.jar`.
5. Keep the existing `config.yml` and `players.yml`.
6. Start the server and inspect console plus `/chat diagnostics` before normal traffic resumes.

A valid v5 configuration is backed up as `config-before-v6-<timestamp>.yml` before missing v6 defaults are written. Do not replace a customized live config with the bundled file.

### What v6 adds

- configurable multi-line event cards and blank spacing;
- event display names;
- persistent `chat-events.db` win statistics;
- `/chat events stats`, leaderboard and statistics GUI;
- multi-page Chat Events administration GUI;
- true `BINGO` engine with explicit join phase;
- 75-ball participant boards and clickable server-validated marks;
- participant-only repeated Bingo draw traffic;
- ROW/COLUMN/DIAGONAL/FOUR_CORNERS/FULL_HOUSE support;
- expanded database/Bingo diagnostics.

SQLite JDBC is bundled in the plugin JAR for standalone database operation. Paper, Adventure, PlexonCore, PlexonKeys, DiscordSRV and other server-provided APIs remain unbundled.

### Event ID compatibility

PlexonChats 3.3.0 shipped a bundled TYPE example whose ID was `bingo`. In v6, event IDs remain administrator-owned opaque identifiers and the stored `type` field remains authoritative.

Therefore migration does **not** silently convert an existing event named `bingo` to BINGO or rename it. Fresh v6 installations instead use:

```text
type-rush     -> TYPE
bingo-classic -> BINGO
```

If the existing v5 `bingo` event was customized, it remains exactly as configured after upgrade.

### Existing behavior preserved

3.4.0 does not replace LOCAL/GLOBAL routing, the synchronous cancellable `PlexonChatEvent`, `PlexonChatsAPI`, PM/reply, item display, mentions, player preferences, auto-messages, DiscordSRV isolation or transactional reload.

Ordinary Chat Event answers still count only from accepted native public chat. Rewards still run only after an exact winner exists and only once. Bingo participates in the same one-active-event boundary but has its own JOINING/ACTIVE lifecycle.

### First validation after upgrade

Run:

```text
/chat status
/chat diagnostics
/chat events status
/chat events list
/chat events stats
/chat events preview type-rush
/chat events preview bingo-classic
/chat events
```

Expect:

- version `3.4.0`;
- config schema 6;
- Chat Events coordinator active exactly once;
- database state/file/schema visible;
- no duplicate scheduler tasks after reloads;
- existing customized event IDs preserved;
- newly added v6 defaults available only where they do not overwrite administrator-owned collections.

### Normal event smoke sequence

1. Verify normal LOCAL/GLOBAL chat still delivers once.
2. Verify `/g`, `/l`, `/msg` and `/reply` still work.
3. Run `/chat events start type-rush`.
4. Confirm the event card has configured spacing and multiple lines.
5. Send a wrong answer; the event stays active.
6. Send the correct answer through accepted native chat.
7. Confirm exactly one winner/reward.
8. Run `/chat events stats`; confirm total/type count increments once.
9. Attempt another correct answer; no second reward/stat increment.
10. Restart the server and confirm the statistics remain.

### Bingo smoke sequence

Use at least two participating players plus one non-participant.

1. `/chat events start bingo-classic`.
2. Confirm the general join invitation appears once.
3. Have two players click JOIN; leave the third idle.
4. Confirm each participant receives one 5×5 card.
5. After JOINING closes, confirm participant-only draw messages begin.
6. Confirm the non-participant receives no repeated draw/card traffic.
7. Click an undrawn cell through any stale/forged action; it must be rejected.
8. Click a legitimately drawn cell; only that player's server-owned card changes.
9. Disconnect/reconnect a participant; the existing card should be restored rather than regenerated.
10. Complete a configured pattern; exactly one player wins.
11. Confirm one reward and one BINGO statistics increment.
12. Start another round and let it timeout/cancel; no reward/stat should be issued.

### GUI validation

Open `/chat events` and verify:

- live dashboard state;
- paginated event browser;
- left-click details;
- right-click preview;
- shift-left manual start only for authorized staff;
- reward inspection;
- Bingo page;
- statistics page;
- stale inventory actions do not affect a newer config generation or event run.

### Transactional rejection

Deliberately make one invalid candidate on staging, such as:

- `blank-lines-before: 4`;
- malformed MiniMessage in a presentation card;
- invalid Bingo pattern;
- invalid Bingo interval/timeout;
- unknown event type or reward profile.

`/chat reload` must reject the entire candidate and keep the previous runtime active. Repair the file before continuing.

### Operational task-count check

Repeated `/chat reload` calls must retain bounded shared tasks only:

- one preference writer;
- one auto-message coordinator;
- one Chat Events coordinator;
- one Chat Event DB executor;
- one item-preview cleanup timer.

There must not be a scheduler/thread per chat message, event, player or Bingo participant.

### Runtime deployment note

Stable GitHub source/release closure may complete when exact source, CI and release provenance are proven even if production host access is unavailable. In that case live PlexonCraft deployment remains `FOLLOW_UP_NON_BLOCKING`; do not infer an in-game PASS from CI.

## Rollback from 3.4.0 to 3.3.0

1. Stop the server.
2. Restore `PlexonChats-3.3.0.jar`.
3. Restore the pre-v6 `config-before-v6-*.yml` as `config.yml` before starting 3.3.0.
4. Preserve `players.yml`.
5. Preserve `chat-events.db` if a later return to 3.4.0 is expected; v3.3.0 simply does not use it.
6. Start the server and validate normal chat, PMs, GUI, auto-messages, DiscordSRV and the original 3.3 Chat Events commands.

Rollback source boundary:

```text
v3.3.0
8b79743c5e8f1751031b0988ff927fdc17fa93ed
```

## Historical 3.2.0 → 3.3.0

PlexonChats 3.3.0 introduced configuration schema v5 and the first configurable Chat Events subsystem with TYPE, UNSCRAMBLE, MATH, TRIVIA and REVERSE, reusable Vault/PlexonKeys/command rewards, one bounded coordinator, exact-once winner/reward state and the initial compact administration page.

## Historical 3.0 → 3.2.0

Earlier releases established the current Paper 26.2 / Java 25 communication boundary: one native public-chat route, synchronous cancellable `PlexonChatEvent`, public `PlexonChatsAPI`, transactional configuration, first-party PM/reply, bounded auto-messages and optional PlexonCore/DiscordSRV integration.
