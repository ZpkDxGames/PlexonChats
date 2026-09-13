# PlexonChats Bingo

PlexonChats 3.4.0 includes a real interactive `BINGO` Chat Event engine. It is separate from the ordinary TYPE, UNSCRAMBLE, MATH, TRIVIA and REVERSE answer generators.

## Lifecycle

A Bingo run occupies the one global Chat Events slot for its complete lifecycle:

```text
JOINING -> ACTIVE -> WON / TIMED_OUT / CANCELLED
```

The server sends one general join invitation. Only players who explicitly join the exact active run become participants. Duplicate joins are harmless; joins after the deadline or from ineligible players are rejected. Players who do nothing are not enrolled.

If the join window closes below `min-participants`, the run is cancelled with no reward and no win statistic.

## 75-ball boards

The stable implementation uses traditional 75-ball Bingo:

```text
B =  1-15
I = 16-30
N = 31-45
G = 46-60
O = 61-75
```

Each participant receives one unique server-generated 5x5 board. Values are unique on the board and stay unchanged for the entire run. With `free-center: true`, the center cell is FREE and pre-marked.

A reconnecting participant receives the same existing board while the run remains active. Reconnecting does not create another participant or another card.

## Participant-only traffic

The general server audience may receive the initial join invitation and the final winner/cancellation result. Repeated draw messages and board rendering are sent only to joined participants.

By default, a full card is shown on join, after a successful mark, when `/chat events bingo card` is used, and after reconnect. Draws use a compact one-line message. Set `redraw-board-each-draw: true` only if the extra chat volume is acceptable.

## Clicking cells

When a drawn board cell is clickable, its action submits only the active run ID and board cell index. Those values are requests, not authority.

The server verifies that:

- the current active event is still Bingo;
- the run ID is current;
- the player joined that run;
- the board belongs to that player;
- the cell exists on that server-owned board;
- the server-resolved number has already been drawn;
- the cell is not already marked.

A player cannot supply a different raw number to force a mark. Old run actions become stale automatically.

## Win patterns

Supported patterns are:

```text
ROW
COLUMN
DIAGONAL
FOUR_CORNERS
FULL_HOUSE
```

The first valid configured pattern detected after a successful mark atomically changes the run to `WON`. The same completion boundary allows exactly one reward attempt, one statistics record and one winner publication.

## Commands

Player commands:

```text
/chat events bingo join <run-id>
/chat events bingo card
```

The clickable board uses an internal `mark` command that is intentionally omitted from normal help. It grants no administrative capability and is fully revalidated server-side.

Staff commands:

```text
/chat events bingo status
/chat events bingo participants
```

## Permissions

```text
plexonchats.events             default: true
plexonchats.events.bingo.play  default: true
plexonchats.events.manage      default: op
```

## Configuration

Global defaults live under `chat-events.bingo`:

```yaml
chat-events:
  bingo:
    enabled: true
    join:
      duration-seconds: 15
      min-participants: 2
    board:
      variant: BINGO_75
      free-center: true
    draw:
      first-delay-seconds: 5
      interval-seconds: 5
      redraw-board-each-draw: false
    timeout-seconds: 300
    win-patterns: [ROW, COLUMN, DIAGONAL]
```

A BINGO definition can override clean per-event values under its own `bingo:` section:

```yaml
chat-events:
  events:
    bingo-classic:
      enabled: true
      name: "Classic Bingo"
      type: BINGO
      weight: 5
      cooldown-seconds: 3600
      reward-profile: epic
      min-online: 3
      bingo:
        join-seconds: 15
        min-participants: 2
        timeout-seconds: 300
        win-patterns: [ROW, COLUMN, DIAGONAL]
```

## Legacy `bingo` event IDs

PlexonChats 3.3.0 used `bingo` as the ID of a bundled TYPE example. Event IDs are administrator-owned identifiers; the stored `type` is authoritative. A v5 event named `bingo` remains exactly that event after migration and is never silently converted to `BINGO`.

Fresh v6 installations instead use `type-rush` for the TYPE example and `bingo-classic` for the real Bingo engine.
