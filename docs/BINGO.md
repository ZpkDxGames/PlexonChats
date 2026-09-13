# PlexonChats Bingo — 4.0.1

PlexonChats 4.0.1 keeps the accepted 4.0.0 explicit-participation, participant-owned, manual-mark Bingo model and focuses on fixed table geometry plus slower configurable calls. Minecraft remains authoritative; Discord remains presentation-only.

## State model

One Bingo run uses:

```text
IDLE -> LOBBY -> STARTING -> ACTIVE -> WON / TIMED_OUT / CANCELLED
```

Bingo reserves the same global Chat Events slot as other event types. A pending Bingo lobby therefore prevents another Chat Event from starting concurrently.

## Lobby and joining

Scheduled Bingo opens a join lobby instead of activating immediately. Stock gameplay data uses:

```yaml
lobby:
  duration-seconds: 60
  reminders-seconds: [60, 30, 15, 5]
  minimum-participants: 2
  admin-minimum-participants: 1
  admin-lobby-seconds: 15
  admin-fast-lobby-seconds: 5
  allow-late-join: false
  require-online-at-start: true
  disconnect-policy: KEEP
```

Players explicitly join through the clickable JOIN action or `/bingo join`. Joining is idempotent. `/bingo leave` removes a participant during the lobby; leaving an active run forfeits that participant instead of mutating anyone else's state. There is no automatic enrollment of otherwise eligible players.

Scheduled activation uses the production minimum. Admin `/bingo start` opens an admin lobby, while `/bingo start now` can activate an admin test after at least one participant has explicitly joined. The admin path does not weaken normal scheduled minimums.

## Personal cards

Each participant owns one stable card for the run. Cards use traditional 75-ball ranges:

- B: `1–15`
- I: `16–30`
- N: `31–45`
- G: `46–60`
- O: `61–75`

Stock 4.0.1 uses 25 numeric cells with no FREE center. Values are unique inside a card. Different participants receive independently generated cards, while reopening `/bingo` for the same run returns that participant's same card.

No participant card is a shared authority object.

## Calls and manual marking

The server owns one shuffled `1..75` call pool and ordered draw history. A number is called at most once.

**Calling a number never marks a card.** Draw history and manual marks are separate state.

Every playable card cell is rendered as a run-bound click action. Internally the click carries both the run UUID and number. PlexonChats validates:

1. the click belongs to the current run;
2. the player joined that run and has not forfeited;
3. the run is active;
4. the number exists on that participant's card;
5. the number has actually been called;
6. the cell is not already marked.

A successful mark mutates only that participant's marked-cell set and immediately refreshes their card. An uncalled click returns `NOT_CALLED`, leaves state unchanged, and reports the problem through the action bar. Old chat cards from an earlier run return `STALE_RUN` and cannot mutate a newer run.

## Table rendering

Fresh 4.0.1 gameplay data uses the `TABLE` renderer:

```text
✦ BINGO — YOUR CARD

┌────┬────┬────┬────┬────┐
│ B  │ I  │ N  │ G  │ O  │
├────┼────┼────┼────┼────┤
│ 05 │ 27 │ 40 │ 57 │ 69 │
│ 15 │ 24 │ 32 │ 47 │ 65 │
│ 04 │ 20 │ 41 │ 53 │ 61 │
│ 07 │ 21 │ 45 │ 54 │ 63 │
│ 12 │ 25 │ 39 │ 48 │ 73 │
└────┴────┴────┴────┴────┘

Last: B-9    Draws: 21/75
[ CALL BINGO ]
```

The complete grid explicitly uses the configured fixed-width font, stock `minecraft:uniform`. Borders, separators, headers, number cells, and in-grid padding all use the same font. Number cells render as two digits inside four visible fixed-width characters, for example `" 05 "`.

Marked and unmarked values use exactly the same visible cell text. Marked cells change color only. Winning cells may change color and use underline, but never add markers or bold that changes glyph advance. Separators are non-clickable while each number remains independently clickable.

Stock settings:

```yaml
render:
  style: TABLE
  font: "minecraft:uniform"
  left-padding: 2
  cell-width: 4
  column-gap: 0
  show-border: true
  show-last-call: true
  show-draw-count: true
  on-join: true
  on-start: true
  after-successful-mark: true
  on-every-draw: false
```

`COMPACT` preserves the 4.0.0 borderless presentation. Existing schema-1 4.0.0 files are not rewritten merely to add `render.style`; when the key is absent, the runtime preserves compatibility from the existing `show-border` value. `TABLE` with `show-border: false` retains deterministic aligned cells/internal separators without exterior box borders.

Unicode box drawing is the stock representation, but real-client alignment is authoritative. If the target client proves those glyphs visually inconsistent under `minecraft:uniform`, prefer an ASCII `+----+` / `| 05 |` table over decorative misalignment.

## Call pacing

Bingo timing is owned only by the Bingo definition in `data.yml`:

```yaml
draws:
  first-call-delay-seconds: 5
  interval-seconds: 5
  range-min: 1
  range-max: 75
```

Fresh 4.0.1 data therefore calls the first number after the configured five-second delay and subsequent numbers every five seconds. The scheduler consumes the loaded definition; the interval is not hardcoded in Java and is not duplicated in `config.yml`.

Existing administrator-owned values are preserved. A server upgrading from 4.0.0 that still has `interval-seconds: 8` remains at 8 until the administrator changes it.

To adopt the recommended cadence on an existing server:

```yaml
# plugins/PlexonChats/data.yml
minigames:
  bingo-classic:
    draws:
      first-call-delay-seconds: 5
      interval-seconds: 5
```

Then run:

```text
/chat reload
```

The full card is deliberately not reprinted every five seconds by default. Stock `render.on-every-draw` remains `false`; each call uses the compact draw announcement, while full cards appear on configured join/start/mark/view paths.

## Claims and winning patterns

`/bingo claim` asks the server to evaluate only the claimant's manually marked cell indices. Global draw history alone can never satisfy a pattern.

Enabled patterns are horizontal, vertical, diagonal, and optional Full House. A claim that does not satisfy an enabled pattern is rejected without ending the run.

The first valid claimant acquires the terminal winner transition. One completion guard then owns:

- winner presentation;
- one reward-profile execution;
- one `chat-events.db` win record keyed by run ID;
- terminal Discord publication;
- run closure.

Later marks/claims cannot reopen the run or issue duplicate rewards/statistics.

## Discord

Discord does not receive participant cards. The shared channel sees lifecycle metadata only:

```text
BINGO — JOINING
Starts in 60s
Participants: 4

BINGO — LIVE
Players: 7
Last call: N-43
Draws: 4/75

BINGO — WINNER
Winner: PlayerName
Pattern: Horizontal
Draws: 38/75
```

The generalized event publisher keeps the existing one-message lifecycle and bounded/coalesced update behavior. Discord cannot join, mark, claim, or issue a reward.

## Commands and permissions

Player routes:

```text
/bingo
/bingo join
/bingo leave
/bingo claim
```

Admin routes:

```text
/bingo start
/bingo start now
/bingo stop
/bingo status
```

Permissions:

- `plexonchats.events.bingo.play` — join, leave, view personal card, use server-generated mark actions, and claim.
- `plexonchats.events.manage` — start, fast-start, stop, and inspect Bingo administration state.

The `mark` subcommand exists as the target of server-generated clickable cells and deliberately includes the run ID. It is not advertised as a normal manual command.

## `data.yml`

Bingo gameplay configuration is owned by `plugins/PlexonChats/data.yml` schema 1 under its BINGO minigame definition. This includes lobby timing, reminders/minimums, card rules, draws, winning patterns, render geometry, weight/cooldown/duration, reward-profile reference, and normal event eligibility values.

Presentation/reward-profile bodies/Discord credentials remain in `config.yml`. Runtime cards, marks, call deadlines, and cooldown timestamps are not persisted into `data.yml` on each tick.

## Upgrade behavior

4.0.1 does not bump the `data.yml` schema solely for renderer defaults or call pacing. Existing schema-1 files remain administrator-owned and are validated as-is. A custom interval such as 7 seconds remains 7 after reload; an existing 8-second 4.0.0 interval is not silently converted to 5.

Malformed Bingo renderer/timing values disable the malformed minigame generation where safe rather than taking down unrelated normal chat or standard Chat Events.

## Runtime certification

GitHub source/CI closure is not a substitute for real-client visual/runtime evidence. Production validation should confirm at representative GUI scales that all vertical separators form straight columns, marks and winner highlighting do not shift geometry, cells remain clickable, five-second calls are readable, full cards do not flood chat, manual-mark authority remains intact, and Discord remains metadata-only.

Until that evidence is collected, release provenance remains:

```text
runtime_certification=FOLLOW_UP_REQUIRED
```
