# PlexonChats Bingo — 3.6.0

PlexonChats 3.6.0 preserves the accepted 3.5.0 server-authoritative shared Bingo board and adds stateful Discord embed synchronization on top of it. Discord never owns Bingo gameplay state.

## Authoritative model

There is exactly one board and one draw history for the active run. Players do not join, receive private cards, click cells, restore cards after reconnect, or manually mark anything.

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

Unmarked cells are gray/white in Minecraft. A called card value is rendered as a bold green bracketed value such as `[07]`. Final winning cells may use a distinct highlight after the winner has been accepted.

## Draw engine

At run creation PlexonChats creates and shuffles one server-owned `1..75` pool. Each number can be called at most once. The run retains ordered draw history, remaining pool, last call, next-draw deadline, start time and terminal state.

Default timing:

```yaml
chat-events:
  bingo:
    draw:
      first-delay-seconds: 5
      interval-seconds: 5
```

The existing Chat Events coordinator advances Bingo. There is no independent global timer and no per-player scheduler.

On each call PlexonChats:

1. removes exactly one value from the remaining pool;
2. appends it to authoritative draw history;
3. derives all marked cells from that history;
4. announces the called value;
5. broadcasts the refreshed shared board to currently eligible Minecraft players;
6. requests a coalesced Discord refresh when event synchronization is enabled.

A called number that is not one of the card's 25 values remains a valid call but marks no cell.

## Viewing and claiming

`/bingo` privately renders the same authoritative board and includes last call, draw count, enabled patterns, next-call information and a claim reminder.

Players may claim using `/bingo claim` or by typing the exact word `bingo` in accepted native Minecraft public chat. The public-chat form enters validation only after the synchronous cancellable `PlexonChatEvent` boundary. Console, Discord-origin messages, PMs, `/reply`, auto-messages, connection messages, broadcasts and synthetic/plugin messages cannot claim.

A claim never proves victory by itself. The server checks the active run, current eligibility, authoritative draw history and enabled patterns, then atomically acquires `ACTIVE → WON`. Only the first successful atomic claim is the winner.

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

- **Horizontal:** any one row has all five values called.
- **Vertical:** any B/I/N/G/O column has all five values called.
- **Diagonal:** either five-cell diagonal has all values called.
- **Full House:** when enabled, all 25 card values must be called.

Full House is an additional allowed pattern; it does not disable normal line wins.

## Exact-once completion

Once a valid claimant acquires `ACTIVE → WON`, further draws stop. One exact-once completion gate owns final board/winning-pattern presentation, one winner announcement, one reward-profile execution, one `chat-events.db` winner record keyed by run ID, and run closure.

Timeout, administrator cancellation, plugin shutdown, Chat Events disable/reload, or draw-pool exhaustion produce no reward and no winner statistic.

## Discord live embed

Bingo now uses the generalized `chat-events.discord` publisher instead of the removed Bingo-only raw webhook sender.

On start, one Discord embed is created and its message reference is retained. Subsequent draws edit that same message. Winner, timeout, cancellation and exhaustion edit it into terminal state.

The Discord board is rendered from the exact active `BingoRun`:

- no Discord-only board generation;
- no FREE center;
- board values match Minecraft;
- drawn values derive from the same draw history;
- last call and draw count derive from the same run;
- winning cells derive from the server-accepted winning pattern.

Example lifecycle titles:

```text
BINGO • LIVE BOARD
BINGO • WINNER
BINGO • TIMED OUT
BINGO • CANCELLED
BINGO • ENDED
```

Live draw updates are coalesced. Terminal state has priority, so a delayed old draw callback cannot overwrite a winner/cancel/timeout embed. If the active Discord message was deleted, one safe recreation may occur; the publisher does not retry forever.

Discord transport failures cannot cancel a draw, change the board, select a winner, issue a reward or alter statistics.

## Discord configuration

Bingo-specific presentation switches live under the generalized section:

```yaml
chat-events:
  discord:
    enabled: false
    transport: AUTO
    participation-mode: DISPLAY_ONLY
    events:
      bingo:
        enabled: true
        show-live-board: true
        update-on-draw: true
        show-last-call: true
        show-draw-count: true
        show-patterns: true
        announce-winner: true
```

`DISPLAY_ONLY` is mandatory in 3.6.0. Discord users may observe Bingo but cannot claim through Discord.

The webhook URL, when configured as a transport/fallback, is secret configuration and is not printed by commands, diagnostics, GUI pages, player errors, embeds or normal status output.

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

Existing `/chat events bingo board|claim|start|stop|status` routes delegate to the same manager. Discord synchronization administration is under `/chat events discord status|test` with `plexonchats.admin.events.discord`.

## Configuration migration

3.6.0 advances schema from v7 to v8. Before an upgraded config is written, PlexonChats creates:

```text
config-before-v8-<timestamp>.yml
```

The former `chat-events.bingo.discord` values are mapped into `chat-events.discord` when possible. A valid legacy webhook URL is copied exactly to the new secret location and the obsolete duplicate subtree is removed. Administrator-owned event definitions, rewards, timing and enabled state remain intact.

The previous v6 → v7 shared-board migration remains supported when upgrading from older installations.

## Runtime acceptance follow-up

After GitHub source/release closure, production PlexonCraft validation should verify startup, Discord transport readiness, exactly one Math embed per run, same-message standard completion, exactly one Bingo embed, exact `/bingo` board parity, same-message draw updates, no FREE center, invalid-claim stability, winner same-message completion, exact-once reward/statistics, cancellation, fallback transport where configured, and secret non-disclosure.

If no real host evidence is collected during release engineering, provenance remains:

```text
runtime_certification=FOLLOW_UP_REQUIRED
```
