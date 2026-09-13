# PlexonChats

PlexonChats **3.5.0** is the stable Plexon communication plugin for Paper 26.2 / Java 25. It owns LOCAL/GLOBAL public chat, private messages, safe MiniMessage presentation, GUI/preferences, scheduled messages, persistent Chat Events, shared claim-based Bingo, diagnostics, and optional DiscordSRV bridging.

## Chat Events

Chat Events retain the accepted exact-winner architecture: one bounded coordinator, at most one globally active run, and rewards/statistics only after the validated winner boundary.

Built-in event types:

- `TYPE` — type a configured value first;
- `UNSCRAMBLE` — unscramble a configured word;
- `MATH` — bounded integer ADD/SUBTRACT/MULTIPLY/exact DIVIDE rounds;
- `TRIVIA` — administrator-authored questions with accepted aliases;
- `REVERSE` — reverse a configured value using Unicode code points;
- `BINGO` — one shared 75-ball board with automatic calls/marks and a first-valid-claim winner.

Fresh installations provide `type-rush`, `unscramble`, `math-normal`, `math-hard`, `trivia`, `reverse`, and `bingo-classic`. Existing administrator-owned IDs are preserved during migration.

Event start, winner, timeout and cancellation output uses configurable multi-line Adventure cards under `chat-events.presentation`. Player/provider values are inserted as Components rather than reparsed as MiniMessage source.

Persistent wins are stored in:

```text
plugins/PlexonChats/chat-events.db
```

The SQLite schema tracks total wins, per-type wins, per-definition wins and winner history. `run_id` remains unique, so duplicate completion callbacks cannot create duplicate persistent wins.

## Bingo 3.5.0

Bingo is a real `BINGO` event engine and occupies the same single global Chat Events slot as other event types. There is no participant join phase and no per-player card state.

Lifecycle:

```text
IDLE -> STARTING -> ACTIVE -> WON / TIMED_OUT / CANCELLED
```

Each active Bingo run owns exactly one shared traditional 75-ball card. The chat display is six columns by six rows including the header:

```text
# |  B |  I |  N |  G |  O
1 | .. | .. | .. | .. | ..
2 | .. | .. | .. | .. | ..
3 | .. | .. | .. | .. | ..
4 | .. | .. | .. | .. | ..
5 | .. | .. | .. | .. | ..
```

The 25 playable values use traditional ranges:

```text
B =  1-15
I = 16-30
N = 31-45
G = 46-60
O = 61-75
```

The center is a normal randomized N-column number. There is **no FREE tile**.

The server shuffles one `1..75` draw pool and calls each number at most once. If a called number exists on the shared card, its cell is marked automatically from the authoritative draw history. Marked cells render as bold green bracketed values such as `[07]`. Players never send mark/cell state.

Enabled winning patterns are horizontal, vertical, diagonal, and optionally Full House. A player claims with either:

```text
/bingo claim
```

or the exact word `bingo` in accepted native Minecraft public chat. The public-chat route is evaluated only after the existing synchronous cancellable `PlexonChatEvent` boundary. Console, PM/reply, Discord-origin, broadcasts, auto-messages and synthetic/plugin messages cannot win.

The server independently evaluates the shared board against draw history. The first valid claim atomically transitions the run from `ACTIVE` to `WON`; all later claims lose. One exact-once completion gate then owns winner publication, reward execution and persistent statistics.

`/bingo` privately displays the same authoritative live board, last call, draw count, active patterns and claim reminder. `/bingo start|stop|status` delegates to the same Chat Events manager used by `/chat events`.

Optional Bingo-specific Discord synchronization uses a configured webhook and an ANSI monospaced board. Webhook requests are asynchronous, bounded and isolated from gameplay; the webhook URL is never exposed through commands, GUI or diagnostics. Discord-origin messages still cannot claim a win.

See [Bingo](docs/BINGO.md).

## Answer source and moderation

For ordinary answer events, only accepted native Minecraft public chat can win. Console, commands, `/g`/`/l` shortcut sends, `/msg`/`/reply`, Discord-origin messages, auto-messages, connection messages, broadcasts and synthetic/plugin sends are not valid submissions.

The synchronous cancellable `PlexonChatEvent` remains authoritative before answer acceptance. Player-controlled raw chat is never trusted MiniMessage and is never expanded into reward commands.

## Rewards

Reusable reward profiles may combine:

- Vault economy/cash;
- optional PlexonKeys tiers (`BASIC`, `RARE`, `EPIC`, `LEGENDARY`);
- trusted console commands with controlled placeholders.

Bingo reuses this existing reward engine. Missing optional integrations fail only their reward component; a reward failure does not reopen the event or select another winner.

## Administration GUI

`/chat events` opens the Chat Events dashboard for players; console receives the text status surface. The holder-routed GUI includes live master/scheduler state, the event browser, event details, reward profiles, statistics, and a Bingo page.

The 3.5.0 Bingo page shows run state/ID, last draw, draw count, remaining pool, next-draw ETA, active winning patterns, a shared board preview, reward profile, Start/Stop controls and only the Discord webhook enabled/disabled state. It does not contain joining, participant lists, per-player cards, click marking or FREE-center controls.

## Stable communication boundary

- **Single chat authority:** Paper chat → PlexonChats → synchronous cancellable `PlexonChatEvent` → one delivery path.
- **No per-message scheduler handoff:** native public chat remains on the accepted Paper event path.
- **Safe MiniMessage boundary:** administrator templates may use MiniMessage; player/provider values are component data.
- **Transactional configuration:** invalid candidates do not replace the known-good runtime generation.
- **Bounded tasks:** one auto-message scheduler, one Chat Events coordinator and one database executor; Bingo has no per-player repeating scheduler.
- **DiscordSRV isolation:** approved GLOBAL chat only; Discord-origin traffic never counts as a Chat Event answer or Bingo claim.
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
| `/chat admin`, `/chat status`, `/chat diagnostics` | Administration/runtime state | `plexonchats.manage` |
| `/chat reload` | Transactional config validation/reload | `plexonchats.reload` |
| `/chat automessages ...` | Scheduled-message administration | `plexonchats.automessages` |
| `/chat events` | Event dashboard / console status | `plexonchats.events` |
| `/chat events list` | List definitions and cooldowns | `plexonchats.events` |
| `/chat events stats [player]` | Win statistics | self: `plexonchats.events`; others: `plexonchats.events.stats.others` |
| `/chat events leaderboard` | Cached top winners | `plexonchats.events` |
| `/chat events enable\|disable\|pause\|resume\|start\|stop\|preview` | Event administration | `plexonchats.events.manage` |
| `/bingo` | View the authoritative active board | `plexonchats.events.bingo.play` |
| `/bingo claim` | Submit a server-validated Bingo claim | `plexonchats.events.bingo.play` |
| `/bingo start\|stop\|status` | Bingo administration through Chat Events | `plexonchats.events.manage` |
| `/chat events bingo board\|claim\|start\|stop\|status` | Compatible Chat Events Bingo routes | matching play/manage permission |

## Configuration and migration

Configuration schema is **v7**. Upgrading a valid older configuration creates:

```text
config-before-v7-<timestamp>.yml
```

The v6 → v7 migration preserves administrator-owned `chat-events.events`, including custom IDs, names, weights, cooldowns, minimum-online values and reward-profile references. Existing `type: BINGO` definitions are migrated to the shared-board model. Obsolete join, participant-card, manual-mark and FREE-center settings are removed/ignored.

A non-Bingo event whose ID is literally `bingo` remains whatever its explicit `type` says; IDs are opaque and never reinterpreted.

See [Configuration](docs/CONFIGURATION.md), [Chat Events](docs/CHAT-EVENTS.md), and [Upgrading](docs/UPGRADING.md).

## Diagnostics

`/chat diagnostics` reports established chat/integration state plus Chat Events coordinator/database state and Bingo phase, draw count, last draw, remaining pool, next draw, enabled patterns and webhook enabled/disabled state. The webhook URL is never emitted.

## Build and release

With Maven 3.9+ and JDK 25:

```sh
mvn --batch-mode --no-transfer-progress clean verify
```

The stable artifact is `PlexonChats-3.5.0.jar`. Build/release verification checks Java class major 69, Paper 26.2 metadata, required Chat Event/Bingo/statistics/SQLite classes, provided-API isolation, checksums and provenance.

Stable publication rebuilds the exact final `main` source through `release/stable` and publishes:

- `PlexonChats-3.5.0.jar`
- `SHA256SUMS.txt`
- `TEST_SUMMARY.txt`
- `PROVENANCE.txt`

Rollback baseline: `v3.4.0` / `26cdf6fcff09d0443bbea64c5ec5ae3c4c1cd3a9`.

Live PlexonCraft deployment/smoke testing remains a separate operational follow-up when direct host evidence is unavailable; GitHub source/release closure does not fabricate an in-game PASS.

Created by **Tonim / ZpkDxGames**.
