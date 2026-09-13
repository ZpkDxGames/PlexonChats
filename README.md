# PlexonChats

PlexonChats **3.4.0** is the stable Plexon communication plugin for Paper 26.2 / Java 25. It owns LOCAL/GLOBAL public chat, private messages, safe MiniMessage presentation, GUI/preferences, scheduled messages, persistent Chat Events, interactive Bingo, diagnostics, and optional DiscordSRV bridging.

## 3.4.0 — Enhanced Chat Events

Chat Events retain the accepted 3.3.0 architecture: one bounded coordinator, at most one active run, one exact winner, and rewards only after the validated winner boundary.

Built-in event types:

- `TYPE` — type a configured value first;
- `UNSCRAMBLE` — unscramble a configured word;
- `MATH` — bounded integer ADD/SUBTRACT/MULTIPLY/exact DIVIDE rounds;
- `TRIVIA` — administrator-authored questions with accepted aliases;
- `REVERSE` — reverse a configured value using Unicode code points;
- `BINGO` — opt-in 75-ball Bingo with participant-specific clickable cards.

Fresh installations provide `type-rush`, `unscramble`, `math-normal`, `math-hard`, `trivia`, `reverse`, and `bingo-classic`. Existing administrator-owned IDs are preserved during migration.

### Event cards

Event start, winner, timeout and cancellation output uses configurable multi-line Adventure cards under `chat-events.presentation`. Blank lines before/after the card are configurable from 0–3, making events visibly distinct from ordinary chat history without altering chat routing.

Dynamic values such as player names are inserted as Components rather than reparsed as MiniMessage source.

### Persistent wins

PlexonChats stores Chat Event wins in:

```text
plugins/PlexonChats/chat-events.db
```

The SQLite schema tracks total wins, per-type wins, per-definition wins and winner history. `run_id` is unique, giving persistence-level duplicate protection. Reads/writes stay off the ordinary chat hot path through one controlled database executor and cache-backed command/GUI reads.

Commands:

```text
/chat events stats
/chat events stats <player>
/chat events leaderboard
```

See [Chat Event Statistics](docs/CHAT-EVENT-STATS.md).

## Interactive Bingo

Bingo is a real event engine, not a TYPE event named `bingo`.

A Bingo run uses:

```text
JOINING -> ACTIVE -> WON / TIMED_OUT / CANCELLED
```

The server announces one clickable join invitation. Players must explicitly join the exact run. Only participants receive their board, number draws, mark feedback and progress traffic; non-participants are not flooded with repeated numbers.

Each participant receives one server-owned 5×5 75-ball board:

```text
B =  1-15
I = 16-30
N = 31-45
G = 46-60
O = 61-75
```

The center can be FREE. Drawn cells are clickable, but the client never has authority over marks: the server validates the active run, participant, board cell, server-side number and draw history. Supported win patterns are `ROW`, `COLUMN`, `DIAGONAL`, `FOUR_CORNERS`, and `FULL_HOUSE`.

The first valid final mark atomically closes the run. Rewards, persistent statistics and winner publication are each guarded by the same exact-once completion boundary.

See [Bingo](docs/BINGO.md).

## Answer source and moderation

For non-Bingo answer events, only accepted native Minecraft public chat can win. Console, commands, `/g`/`/l` shortcut sends, `/msg`/`/reply`, Discord-origin messages, auto-messages, connection messages, broadcasts and synthetic/plugin sends are not valid submissions.

The synchronous cancellable `PlexonChatEvent` remains authoritative before answer acceptance. Player-controlled raw chat is never trusted MiniMessage and is never expanded into reward commands.

## Rewards

Reusable reward profiles may combine:

- Vault economy/cash;
- optional PlexonKeys tiers (`BASIC`, `RARE`, `EPIC`, `LEGENDARY`);
- trusted console commands with controlled placeholders.

Missing optional integrations fail only their reward component. A reward failure does not reopen the event or choose another winner.

## Administration GUI

`/chat events` opens the Chat Events dashboard for players; console receives the text status surface.

The holder-routed GUI includes:

- dashboard with live master/scheduler/active-event/integration/database state;
- paginated event browser;
- event details and manual preview/start controls;
- reward profile inspection;
- Bingo status/card access;
- statistics and leaderboard information.

Event browser interactions:

```text
LEFT CLICK  -> details
RIGHT CLICK -> preview
SHIFT+LEFT  -> manual start
```

The existing secure `InventoryHolder` routing remains in place. GUI holders carry the configuration revision plus selected-page/run context so stale inventories cannot mutate a newer runtime generation or stop a different active run.

## Stable communication boundary

- **Single chat authority:** Paper chat → PlexonChats → synchronous cancellable `PlexonChatEvent` → one delivery path.
- **No per-message scheduler handoff:** native public chat remains on the accepted Paper event path.
- **Safe MiniMessage boundary:** administrator templates may use MiniMessage; player/provider values are component data.
- **Transactional configuration:** invalid candidates do not replace the known-good runtime generation.
- **Bounded tasks:** one auto-message scheduler, one Chat Events coordinator, one database executor; no per-player Bingo repeating task.
- **DiscordSRV isolation:** approved GLOBAL chat only; Discord-origin traffic never counts as a Chat Event answer.
- **Public API compatibility:** existing `PlexonChatsAPI` and synchronous cancellable `PlexonChatEvent` remain compatible.

AFK remains PlexonUtility-owned and is not duplicated in Chats.

## Requirements

- Paper **26.2**
- Java **25**
- Optional PlexonCore **2.x**; supported Core API range `>=1.0 <3.0`
- Optional Vault + economy provider
- Optional PlexonKeys
- Optional LuckPerms, PlaceholderAPI, PlexonRanks
- Optional DiscordSRV 1.30.5

SQLite JDBC is bundled for standalone `chat-events.db` operation. Paper/Bukkit/Adventure, PlexonCore, PlexonKeys, DiscordSRV, PlaceholderAPI, LuckPerms and other server-provided APIs are not bundled.

## Commands

| Command | Purpose | Permission |
| --- | --- | --- |
| `/chat gui` | Player channel/alert preferences | `plexonchats.gui` |
| `/chat channel <local\|global>` | Select outgoing channel | channel permission |
| `/g [message]`, `/l [message]` | Select/send GLOBAL or LOCAL chat | `plexonchats.global`, `plexonchats.local` |
| `/msg <player> <message>`, `/reply <message>` | Private messages | `plexonchats.tell` |
| `/announce <message>` | Server-wide announcement | `plexonchats.announce` |
| `/chat admin`, `/chat status` | Administration/state | `plexonchats.manage` |
| `/chat diagnostics` | Runtime/integration/DB/Bingo diagnostics | `plexonchats.manage` |
| `/chat reload` | Transactional config validation/reload | `plexonchats.reload` |
| `/chat automessages ...` | Scheduled-message administration | `plexonchats.automessages` |
| `/chat events` | Event dashboard / console status | `plexonchats.events` |
| `/chat events list` | List definitions and cooldowns | `plexonchats.events` |
| `/chat events stats [player]` | Win statistics | self: `plexonchats.events`; others: `plexonchats.events.stats.others` |
| `/chat events leaderboard` | Cached top winners | `plexonchats.events` |
| `/chat events bingo card` | Reopen own active Bingo card | `plexonchats.events.bingo.play` |
| `/chat events bingo join <run-id>` | Join active Bingo | `plexonchats.events.bingo.play` |
| `/chat events enable\|disable` | Persistent master state | `plexonchats.events.manage` |
| `/chat events pause\|resume` | Runtime automatic scheduler | `plexonchats.events.manage` |
| `/chat events start <id\|random>` | Manual start | `plexonchats.events.manage` |
| `/chat events stop` | Cancel active event without reward/stat | `plexonchats.events.manage` |
| `/chat events preview <id>` | Private event preview | `plexonchats.events.manage` |
| `/chat events bingo status` | Bingo runtime status | `plexonchats.events.manage` |
| `/chat events bingo participants` | Joined participant list | `plexonchats.events.manage` |

## Configuration and migration

Configuration schema is **v6**. Upgrading a valid older configuration creates:

```text
config-before-v6-<timestamp>.yml
```

The migration adds missing 3.4 options without silently replacing administrator-owned Chat Event definitions. In particular, a v5 TYPE event whose ID is literally `bingo` remains a TYPE event; IDs are opaque and the stored `type` field is authoritative.

Fresh v6 defaults use `type-rush` for the TYPE example and `bingo-classic` for the real BINGO event.

A successful reload safely replaces runtime coordinators after validation. A failed candidate leaves the previous configuration and active event untouched.

See [Configuration](docs/CONFIGURATION.md), [Chat Events](docs/CHAT-EVENTS.md), and [Upgrading](docs/UPGRADING.md).

## Diagnostics

`/chat diagnostics` reports the established chat/integration state plus:

- Chat Events master/scheduler/task state;
- active event/run/remaining time;
- last result/winner;
- Vault and PlexonKeys reward readiness;
- SQLite state/file/schema/executor/pending writes/last success/failure;
- total recorded winners and cached player statistics;
- Bingo phase/participants/draw count/last draw/remaining pool.

Internal Bingo action tokens/nonces are never exposed.

## Build and release

With Maven 3.9+ and JDK 25:

```sh
mvn --batch-mode --no-transfer-progress clean verify
```

The stable artifact is `PlexonChats-3.4.0.jar`. Build/release verification checks Java class major 69, Paper 26.2 metadata, required Chat Event/Bingo/statistics/SQLite classes, provided-API isolation, checksums and provenance.

Stable publication rebuilds the exact final `main` source through `release/stable` and publishes:

- `PlexonChats-3.4.0.jar`
- `SHA256SUMS.txt`
- `TEST_SUMMARY.txt`
- `PROVENANCE.txt`

Rollback baseline: `v3.3.0` / `8b79743c5e8f1751031b0988ff927fdc17fa93ed`.

Live PlexonCraft deployment/smoke testing remains a separate operational follow-up when direct host evidence is unavailable; GitHub source/release closure does not fabricate an in-game PASS.

Created by **Tonim / ZpkDxGames**.
