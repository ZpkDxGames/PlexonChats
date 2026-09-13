# PlexonChats Bingo — 3.5.0

PlexonChats 3.5.0 replaces the 3.4.0 participant-card implementation with one server-authoritative shared Bingo board per active `BINGO` Chat Event.

## Authoritative model

There is exactly one board and one draw history for the run. Players do not join, receive private cards, click cells, restore cards after reconnect, or manually mark anything.

Lifecycle:

```text
IDLE
  ↓
STARTING
  ↓
ACTIVE
  ↓
WON / TIMED_OUT / CANCELLED
```

All players who satisfy the event's normal eligibility rules may view the board and submit a claim while the run is `ACTIVE`.

## Board

The canonical Minecraft surface is a 6 × 6 display including the header:

```text
# |  B |  I |  N |  G |  O
1 | .. | .. | .. | .. | ..
2 | .. | .. | .. | .. | ..
3 | .. | .. | .. | .. | ..
4 | .. | .. | .. | .. | ..
5 | .. | .. | .. | .. | ..
```

The playable card has 25 unique values. Each column contains five values from its traditional range:

- B: `1–15`
- I: `16–30`
- N: `31–45`
- G: `46–60`
- O: `61–75`

The row-3 N cell is a normal random value from `31–45`. There is no FREE tile.

Unmarked cells are gray/white. A called card value is rendered as a bold green bracketed value such as `[07]`. The winning cells may use a distinct final highlight after the winner has been accepted.

## Draw engine

At run creation PlexonChats creates and shuffles one server-owned `1..75` pool. Each number can be called at most once. The run retains the ordered draw history, remaining pool, last call, next-draw deadline, start time and terminal state.

Default timing:

```yaml
chat-events:
  bingo:
    draw:
      first-delay-seconds: 5
      interval-seconds: 5
```

`interval-seconds` must be `1..300`; the first delay accepts `0..300`.

The existing Chat Events coordinator advances Bingo. There is no independent global timer and no per-player scheduler.

On each call PlexonChats:

1. removes exactly one value from the remaining pool;
2. appends it to authoritative draw history;
3. automatically derives all marked cells from that history;
4. announces the called value;
5. broadcasts the refreshed shared board to currently eligible players;
6. optionally queues the Discord ANSI update.

A called number that is not one of the card's 25 values remains a valid call but marks no cell.

## Viewing the board

```text
/bingo
```

When a Bingo run is active, `/bingo` privately renders the same authoritative board and includes the last call, draw count, enabled patterns, next-call information and a claim reminder. No GUI is required to play.

## Claiming

Players may claim using:

```text
/bingo claim
```

or by typing the exact word:

```text
bingo
```

in accepted native Minecraft public chat.

The public-chat form enters Bingo validation only through PlexonChats' normal native public-chat route after the synchronous cancellable `PlexonChatEvent` boundary. A cancelled chat message cannot win. Console, Discord-origin messages, PMs, `/reply`, auto-messages, connection messages, broadcasts and synthetic/plugin messages cannot claim.

A claim never proves victory by itself. The server checks:

1. an active Bingo run exists;
2. the player is currently eligible;
3. the run is still `ACTIVE`;
4. an enabled winning pattern is complete in authoritative draw history;
5. the `ACTIVE → WON` transition can still be atomically acquired.

Only the first successful atomic claim is the winner. Near-simultaneous claims cannot produce two winners.

Invalid claims leave the event active and produce only concise feedback to the claimant.

## Winning patterns

```yaml
chat-events:
  bingo:
    winning:
      horizontal: true
      vertical: true
      diagonal: true
      full-house: false
```

- **Horizontal:** any one of the five rows has all five values called.
- **Vertical:** any B/I/N/G/O column has all five values called.
- **Diagonal:** either full five-cell diagonal has all values called.
- **Full House:** when enabled, all 25 card values must be called.

Full House is an additional allowed pattern; it does not disable normal line wins.

## Exact-once completion

Once a valid claimant acquires `ACTIVE → WON`, further draws stop immediately. One exact-once completion gate owns:

- final board/winning-pattern presentation;
- one global winner announcement;
- one reward-profile execution;
- one `chat-events.db` winner record keyed by run ID;
- run closure.

Bingo uses the existing Chat Event reward profiles and statistics database. It does not have a second reward or persistence engine.

Timeout, administrator cancellation, plugin shutdown, Chat Events disable/reload, or draw-pool exhaustion produce no reward and no winner statistic.

## Commands and permissions

```text
/bingo
/bingo claim
/bingo start
/bingo stop
/bingo status
```

- `plexonchats.events.bingo.play` — view/claim the live board.
- `plexonchats.events.manage` — start/stop/status administration.

Existing `/chat events ...` routes remain valid. `/chat events bingo board|claim|start|stop|status` delegates to the same manager; there is no competing Bingo management system.

## Discord webhook synchronization

Optional Bingo-only webhook configuration:

```yaml
chat-events:
  bingo:
    discord:
      enabled: false
      webhook-url: ""
      username: "PlexonChats Bingo"
      send-start: true
      send-draws: true
      send-win: true
```

Discord receives a monospaced fenced `ansi` board. Called card cells use ANSI bold green styling. Minecraft MiniMessage/legacy color strings are never assumed to render on Discord.

Webhook I/O is submitted through a bounded asynchronous transport with short timeouts. HTTP callbacks never mutate gameplay state. Delivery failure cannot cancel draws/wins, duplicate rewards, block the Paper main thread or stall the coordinator.

The webhook URL is secret configuration. It is not printed by diagnostics, status commands, GUI pages, player errors or normal logs.

## Configuration migration

3.5.0 advances configuration schema from v6 to v7. Before an upgraded config is written, PlexonChats creates:

```text
config-before-v7-<timestamp>.yml
```

Migration preserves administrator-owned `chat-events.events`. For explicit `type: BINGO` definitions it preserves identity/name/weight/cooldown/reward/minimum-online while converting the gameplay to shared-board defaults. A legacy nested Bingo timeout becomes event `duration-seconds` when no duration was already supplied.

Obsolete active mechanics are removed/ignored, including join duration/minimum participants, participant cards, FREE center, click/manual marking and participant-only traffic.

A non-Bingo event whose ID happens to be `bingo` is not reinterpreted; its explicit `type` remains authoritative.

## Runtime acceptance follow-up

After GitHub source/release closure, production PlexonCraft validation should verify startup, `/bingo start`, the 6×6 board/header, normal-number center, automatic green marking, shared `/bingo` view, unique draws, false-claim rejection, first-valid-claim winner, exact-once reward/statistics, scheduled selection, Discord ANSI rendering when enabled, and no regression to normal chat/PM/other events/DiscordSRV.

If no real host evidence was collected during release engineering, provenance remains:

```text
runtime_deployment=FOLLOW_UP_NON_BLOCKING
```
