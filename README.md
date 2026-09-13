# PlexonChats

PlexonChats **4.0.1** is the stable Plexon communication plugin for Paper 26.2 / Java 25. It owns LOCAL/GLOBAL public chat, private messages, safe MiniMessage presentation, GUI/preferences, scheduled messages, persistent Chat Events, joinable participant-owned Bingo, diagnostics, optional DiscordSRV chat bridging, and stateful Discord Chat Event embeds.

## Chat Events

Chat Events use one bounded coordinator and allow at most one globally reserved/active competition at a time. Built-in types remain `TYPE`, `UNSCRAMBLE`, `MATH`, `TRIVIA`, `REVERSE`, and `BINGO`. Standard events retain the exact-once winner/reward/statistics boundary introduced in 3.x.

Gameplay definitions live in `plugins/PlexonChats/data.yml`. Presentation, reward profile bodies, and integration credentials remain in `config.yml`. The master `chat-events.enabled` switch remains in `config.yml`; scheduler/randomizer/minigame gameplay values are read from `data.yml`.

Persistent wins remain in `plugins/PlexonChats/chat-events.db`, with unique run IDs protecting against duplicate completion records.

## Bingo 4.0.1

Bingo remains the participant-owned manual-mark game introduced in 4.0.0. Every run uses an explicit lobby followed by stable personal cards:

```text
IDLE -> LOBBY -> STARTING -> ACTIVE -> WON / TIMED_OUT / CANCELLED
```

Scheduled Bingo uses the configured lobby countdown (stock: 60 seconds) and emits the configured 60/30/15/5 second join reminders once. Players join explicitly with the clickable chat action or `/bingo join`; no eligible player is enrolled automatically.

Each participant receives one stable traditional 75-ball card for that run. The B/I/N/G/O ranges are B 1–15, I 16–30, N 31–45, G 46–60, O 61–75. The stock card has 25 numbers and no FREE center.

The server calls numbers globally, but **a draw is not a mark**. A player marks only their own card by clicking a cell. The click is bound to the current run ID. If the number has not been called, state is unchanged and the player receives an action-bar rejection.

Fresh 4.0.1 data renders the card as a true fixed-width `TABLE` using `minecraft:uniform`, four-character cells, aligned box separators, and no width-changing mark decorations. Marked cells use color only; winning cells use color/underline rather than bold. `COMPACT` preserves the 4.0.0 borderless renderer as a compatibility mode. The full board is not reprinted on every draw by default.

Fresh `data.yml` also changes the recommended Bingo call interval from 8 seconds to **5 seconds**. `minigames.bingo-classic.draws.interval-seconds` remains configurable and authoritative. Existing administrator-owned `data.yml` values are not silently replaced during upgrade.

Winning evaluation uses only that participant's manual marked-cell set. Global draw history cannot create a win. The first accepted claim transitions the run once and owns exactly one winner publication, reward execution, and persistent statistic.

Admins can open a normal test lobby with `/bingo start`, or activate an already-joined admin lobby with `/bingo start now`. The one-player admin path does not reduce the configured minimum for scheduled production Bingo.

See [Bingo](docs/BINGO.md).

## Discord Chat Event synchronization

Discord remains presentation-only. Minecraft/PlexonChats is authoritative for event creation, Bingo participation, cards, marks, claims, winner selection, rewards, statistics, timeout, and cancellation.

Each published run normally owns one Discord message:

```text
START/LOBBY -> create
LIVE UPDATE -> edit same message
WIN/TIMEOUT/CANCEL -> edit same message to terminal state
```

For Bingo, Discord shows only safe shared metadata such as phase, participant count, last call, draw count, winner, and pattern. Participant-owned cards are never exposed in a shared Discord channel. Discord cannot join, mark, claim, or issue rewards.

Transport modes remain `AUTO`, `DISCORDSRV`, and `WEBHOOK`. Webhook URLs remain secrets and are never printed by normal commands, GUI, diagnostics, embeds, or player-facing errors.

## Configuration and migration

`config.yml` remains configuration schema **v10**. `data.yml` remains schema **1** for scheduler/randomizer/minigame gameplay data; 4.0.1 does not bump that schema simply to introduce new stock renderer defaults.

On first v4 startup when `data.yml` does not exist, PlexonChats:

1. backs up the current configuration as `config-before-v4-<timestamp>.yml`;
2. reads compatible pre-v4 Chat Events gameplay values;
3. creates schema-1 `data.yml`;
4. preserves administrator-owned enabled flags, weights, cooldowns, durations, minimum-online values, reward references, channels, content pools, math ranges, and compatible Bingo timing/pattern values;
5. adds current lobby/manual-mark settings from bundled defaults;
6. keeps Discord/webhook secrets in `config.yml`.

If `data.yml` already exists, legacy `config.yml` gameplay values do not overwrite it. Existing schema-1 files are not rewritten merely to force 4.0.1's 5-second stock interval or `TABLE` style. A 4.0.0 renderer block without `style` retains compatibility from its existing `show-border` value. Malformed individual minigames are quarantined where safe instead of disabling unrelated chat functionality.

See [Configuration](docs/CONFIGURATION.md) and [Upgrading](docs/UPGRADING.md).

## Commands

| Command | Purpose | Permission |
| --- | --- | --- |
| `/chat gui` | Player channel/alert preferences | `plexonchats.gui` |
| `/chat channel <local\|global>` | Select outgoing channel | channel permission |
| `/g [message]`, `/l [message]` | Select/send GLOBAL or LOCAL chat | `plexonchats.global`, `plexonchats.local` |
| `/msg <player> <message>`, `/reply <message>` | Private messages | `plexonchats.tell` |
| `/announce <message>` | Server-wide announcement | `plexonchats.announce` |
| `/chat admin`, `/chat status`, `/chat diagnostics` | Administration/runtime state | `plexonchats.manage` |
| `/chat reload` | Transactional `config.yml` + `data.yml` reload | `plexonchats.reload` |
| `/chat automessages ...` | Scheduled-message administration | `plexonchats.automessages` |
| `/chat events` | Event dashboard / console status | `plexonchats.events` |
| `/chat events list` | List definitions and cooldowns | `plexonchats.events` |
| `/chat events stats [player]` | Win statistics | self: `plexonchats.events`; others: `plexonchats.events.stats.others` |
| `/chat events leaderboard` | Cached top winners | `plexonchats.events` |
| `/chat events enable\|disable\|pause\|resume\|start\|stop\|preview` | Event administration | `plexonchats.events.manage` |
| `/chat events discord status\|test` | Safe Discord event-sync administration | `plexonchats.admin.events.discord` |
| `/bingo` | View your current participant card | `plexonchats.events.bingo.play` |
| `/bingo join`, `/bingo leave` | Join/leave the current lobby | `plexonchats.events.bingo.play` |
| `/bingo claim` | Claim a manually completed pattern | `plexonchats.events.bingo.play` |
| `/bingo start [now]`, `/bingo stop`, `/bingo status` | Bingo administration/testing | `plexonchats.events.manage` |

Cell-mark commands are generated as run-bound clickable actions by the server and are not intended as a normal player-facing command workflow.

See [Commands and permissions](docs/COMMANDS.md).

## Stable communication boundary

- Paper chat -> PlexonChats -> synchronous cancellable `PlexonChatEvent` -> one delivery path.
- Administrator templates may use MiniMessage; raw player/provider values remain component data.
- Configuration reload is transactional; an invalid candidate does not replace the known-good runtime generation.
- Active Bingo marks/cards are in memory and are not written into `data.yml` every tick/click.
- Discord networking remains asynchronous and isolated from gameplay authority.
- Existing `PlexonChatsAPI` and synchronous cancellable `PlexonChatEvent` remain compatible.

AFK remains PlexonUtility-owned and is not duplicated in Chats.

## Requirements

- Paper **26.2**
- Java **25** / class major **69**
- Optional PlexonCore **2.x**; supported Core API range `>=1.0 <3.0`
- Optional Vault + economy provider
- Optional PlexonKeys
- Optional LuckPerms, PlaceholderAPI, PlexonRanks
- Optional DiscordSRV **1.30.5**

SQLite JDBC is bundled for `chat-events.db`. Server-provided Paper/Bukkit/Adventure and optional plugin APIs are not bundled.

## Build and release

Canonical verification:

```sh
mvn --batch-mode clean verify
```

Stable release assets:

- `PlexonChats-4.0.1.jar`
- `SHA256SUMS.txt`
- `TEST_SUMMARY.txt`
- `PROVENANCE.txt`

Authoritative baseline and rollback: `v4.0.0` / `657d27766bce60235bc50602b4dd4d14b9e6998c`.

Source/release verification does not imply live-server visual certification. Until the table is checked on a real Paper 26.2 client at representative GUI scales, release provenance records `runtime_certification=FOLLOW_UP_REQUIRED`.

Created by **Tonim / ZpkDxGames**.
