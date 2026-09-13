# PlexonChats

PlexonChats **3.6.1** is the stable Plexon communication plugin for Paper 26.2 / Java 25. It owns LOCAL/GLOBAL public chat, private messages, safe MiniMessage presentation, GUI/preferences, scheduled messages, persistent Chat Events, shared claim-based Bingo, diagnostics, optional DiscordSRV chat bridging, and stateful Discord Chat Event embeds.

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

### Compact interactive feedback — 3.6.1

Stock Chat Event feedback is intentionally concise. The event type appears once in the state header, the challenge receives the main visual emphasis, and compact symbols communicate lifecycle state: `✦` active, `✔` complete, `⌛` expired and `✕` cancelled. Reward and timer metadata use `◆` and `⏱`; their explanatory labels are available through Adventure hover text instead of repeated visible words.

Winner cards use `♛` and expose total/per-type persistent win statistics through hover text. The canonical answer is shown on successful completion. Stock TYPE, UNSCRAMBLE, MATH and REVERSE prompts are also shortened so the event name is not repeated in the challenge sentence.

Schema v9 migrates only exact known stock v8 presentation strings. If an administrator customized a card or event prompt, that custom value remains untouched.

Persistent wins are stored in:

```text
plugins/PlexonChats/chat-events.db
```

The SQLite schema tracks total wins, per-type wins, per-definition wins and winner history. `run_id` remains unique, so duplicate completion callbacks cannot create duplicate persistent wins.

## Discord Chat Event synchronization — 3.6.0+

Discord is a presentation surface only. Minecraft/PlexonChats remains authoritative for event creation, timing, answers, Bingo draws/claims, winner selection, rewards, statistics, cancellation and timeout. Discord-origin messages do not count as Chat Event answers or Bingo claims.

Each event run normally owns one Discord message:

```text
START             -> create one embed
LIVE UPDATE       -> edit the same embed
WIN/TIMEOUT/CANCEL-> edit the same embed into terminal state
```

All implemented event types receive embed presentation. Standard events show the challenge, reward information where enabled, and a terminal winner/answer/timeout/cancel state. Bingo uses the same authoritative `BingoRun` board shown in Minecraft; it never generates a Discord-only card. Drawn cells, last call, draw count, active patterns and the winning line are derived from the same run state.

The publisher coalesces live updates, uses bounded asynchronous work, prevents stale callbacks from overwriting terminal states, and attempts at most one safe recreation when an active Discord message was deleted. Discord failures never stop or delay gameplay and never duplicate rewards/statistics.

Transport modes:

- `AUTO` — prefer DiscordSRV when available, otherwise use a configured webhook;
- `DISCORDSRV` — use DiscordSRV 1.30.5/JDA message create/edit operations;
- `WEBHOOK` — use asynchronous webhook create (`wait=true`) and message PATCH editing.

Webhook URLs are secrets and are never rendered through normal commands, GUI, diagnostics, player errors or embeds.

## Bingo

Bingo remains the accepted v3.5.0 shared-board model and occupies the same single global Chat Events slot as other event types. There is no participant join phase and no per-player card state.

Each active Bingo run owns one shared traditional 75-ball card. The 25 playable values use traditional ranges:

```text
B =  1-15
I = 16-30
N = 31-45
G = 46-60
O = 61-75
```

The center is a normal randomized N-column number. There is **no FREE tile**. The server shuffles one `1..75` draw pool and calls each number at most once. If a called number exists on the shared card, its cell is marked automatically from authoritative draw history.

Enabled winning patterns are horizontal, vertical, diagonal, and optionally Full House. A player claims with `/bingo claim` or the exact word `bingo` in accepted native Minecraft public chat. The first valid claim atomically transitions the run to `WON`; one exact-once completion gate owns winner publication, reward execution and persistent statistics.

`/bingo` privately displays the same authoritative live board, last call, draw count, active patterns and claim reminder. `/bingo start|stop|status` delegates to the same Chat Events manager used by `/chat events`.

See [Bingo](docs/BINGO.md).

## Answer source and moderation

For ordinary answer events, only accepted native Minecraft public chat can win. Console, commands, `/g`/`/l` shortcut sends, `/msg`/`/reply`, Discord-origin messages, auto-messages, connection messages, broadcasts and synthetic/plugin sends are not valid submissions.

The synchronous cancellable `PlexonChatEvent` remains authoritative before answer acceptance. Player-controlled raw chat is never trusted MiniMessage and is never expanded into reward commands.

## Rewards

Reusable reward profiles may combine:

- Vault economy/cash;
- optional PlexonKeys tiers (`BASIC`, `RARE`, `EPIC`, `LEGENDARY`);
- trusted console commands with controlled placeholders.

Bingo and all other events reuse the same reward engine. Missing optional integrations fail only their reward component; a reward failure does not reopen the event or select another winner.

## Administration GUI

`/chat events` opens the Chat Events dashboard for players; console receives the text status surface. The holder-routed GUI includes live master/scheduler state, the event browser, event details, reward profiles, statistics, Bingo, and Discord event-sync status.

Discord status exposes only safe operational state: enabled/disabled, selected/actual transport, readiness, target-channel configuration, participation policy, active-message state and pending-update state. Webhook credentials are never displayed.

## Stable communication boundary

- **Single chat authority:** Paper chat → PlexonChats → synchronous cancellable `PlexonChatEvent` → one delivery path.
- **No per-message scheduler handoff:** native public chat remains on the accepted Paper event path.
- **Safe MiniMessage boundary:** administrator templates may use MiniMessage; player/provider values are component data.
- **Transactional configuration:** invalid candidates do not replace the known-good runtime generation.
- **Bounded tasks:** one auto-message scheduler, one Chat Events coordinator, one database executor, and bounded Discord transport work.
- **DiscordSRV isolation:** ordinary approved GLOBAL chat sync remains separate from Chat Event embeds; event system output is sent directly to Minecraft recipients rather than routed through the normal player-chat bridge.
- **Display-only Discord events:** `participation-mode: DISPLAY_ONLY` remains mandatory.
- **Public API compatibility:** existing `PlexonChatsAPI` and synchronous cancellable `PlexonChatEvent` remain compatible.

AFK remains PlexonUtility-owned and is not duplicated in Chats.

## Requirements

- Paper **26.2**
- Java **25**
- Optional PlexonCore **2.x**; supported Core API range `>=1.0 <3.0`
- Optional Vault + economy provider
- Optional PlexonKeys
- Optional LuckPerms, PlaceholderAPI, PlexonRanks
- Optional DiscordSRV **1.30.5**

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
| `/chat events discord status` | Safe Discord event-sync status | `plexonchats.admin.events.discord` |
| `/chat events discord test` | Send a marked presentation-only test embed | `plexonchats.admin.events.discord` |
| `/bingo` | View the authoritative active board | `plexonchats.events.bingo.play` |
| `/bingo claim` | Submit a server-validated Bingo claim | `plexonchats.events.bingo.play` |
| `/bingo start\|stop\|status` | Bingo administration through Chat Events | `plexonchats.events.manage` |
| `/chat events bingo board\|claim\|start\|stop\|status` | Compatible Chat Events Bingo routes | matching play/manage permission |

## Configuration and migration

Configuration schema is **v9**. Upgrading a valid older configuration creates:

```text
config-before-v9-<timestamp>.yml
```

The v8 → v9 migration refreshes only exact known stock Chat Event presentation cards/prompts. Custom administrator presentation values are preserved. The earlier v7 → v8 migration generalizes the old Bingo-only Discord webhook into `chat-events.discord`, preserving valid webhook URL/username data and removing the obsolete duplicate secret location. Administrator-owned event definitions, reward profiles, timers, enabled states and unrelated configuration remain intact.

The v6 → v7 migration behavior is also retained for installations upgrading across multiple schema generations, including preservation of explicit event types and removal of obsolete participant-card Bingo mechanics.

See [Configuration](docs/CONFIGURATION.md), [Chat Events](docs/CHAT-EVENTS.md), and [Upgrading](docs/UPGRADING.md).

## Diagnostics

`/chat diagnostics` reports established chat/integration state plus Chat Events coordinator/database state, Bingo state, and safe Discord event synchronization state. It never emits the webhook URL.

## Build and release

With Maven 3.9+ and JDK 25:

```sh
mvn --batch-mode --no-transfer-progress clean verify
```

The stable artifact is `PlexonChats-3.6.1.jar`. Build/release verification checks Java class major 69, Paper 26.2 metadata, required Chat Event/Bingo/Discord/statistics/SQLite classes, provided-API isolation, checksums and provenance.

Stable publication rebuilds the exact final `main` source through `release/stable` and publishes:

- `PlexonChats-3.6.1.jar`
- `SHA256SUMS.txt`
- `TEST_SUMMARY.txt`
- `PROVENANCE.txt`

Rollback baseline: `v3.6.0` / `e3bc1f04483ea235b947bbed2c5e2c99e6df8261`.

Live PlexonCraft/Discord runtime certification remains a separate operational follow-up when direct host evidence is unavailable. Release provenance records `runtime_certification=FOLLOW_UP_REQUIRED`; GitHub source/release closure does not fabricate an in-game PASS.

Created by **Tonim / ZpkDxGames**.
