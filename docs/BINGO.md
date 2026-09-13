# PlexonChats Bingo — 4.0.0

PlexonChats 4.0.0 replaces the v3.6.2 shared automatic-mark Bingo model with explicit participation, participant-owned cards, and manual server-validated marks. Minecraft remains authoritative; Discord remains presentation-only.

## State model

One Bingo run uses:

```text
IDLE -> LOBBY -> STARTING -> ACTIVE -> WON / TIMED_OUT / CANCELLED
```

Bingo still reserves the same global Chat Events slot as other event types. A pending Bingo lobby therefore prevents another Chat Event from starting concurrently.

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

Stock v4 uses 25 numeric cells with no FREE center. Values are unique inside a card. Different participants receive independently generated cards, while reopening `/bingo` for the same run returns that participant's same card.

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

A successful mark mutates only that participant's marked-cell set and immediately refreshes their card. A marked number is green, fixed width, and has no brackets.

An uncalled click returns `NOT_CALLED`, leaves state unchanged, and reports the problem through the action bar. Old chat cards from an earlier run return `STALE_RUN` and cannot mutate a newer run.

## Table rendering

The Minecraft grid deliberately uses the fixed-width `minecraft:uniform` font. Numeric cells use consistent two-digit padding, configured left padding, cell width, and column gap. Styling a cell does not alter its text width, so marked/unmarked rows keep the same alignment.

Relevant stock settings:

```yaml
render:
  font: "minecraft:uniform"
  left-padding: 3
  cell-width: 4
  column-gap: 1
  show-border: false
  show-last-call: true
  show-draw-count: true
  on-join: true
  on-start: true
  after-successful-mark: true
  on-every-draw: false
```

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

## v3.6.2 migration

When v4 first starts without `data.yml`, PlexonChats backs up `config.yml` as `config-before-v4-<timestamp>.yml`, then migrates compatible administrator-owned Chat Events gameplay into schema-1 `data.yml`. Existing custom pools, weights, cooldowns, durations, minimum-online values, reward references, channels, math ranges, and compatible Bingo scheduler/pattern settings are preserved. New lobby/manual-mark fields use v4 defaults. Secrets are not copied.

If `data.yml` already exists, it is authoritative and is not overwritten by legacy gameplay values in `config.yml`.

## Runtime certification

GitHub source/CI closure is not a substitute for real-client visual/runtime evidence. Production validation should confirm the 60/30/15/5 reminders, two distinct participant cards, no automatic marks, action-bar rejection of uncalled cells, green fixed-width marks, exact-one winner/reward/stat, one-player admin test, normal scheduled minimum, reloadable `data.yml`, and Discord metadata-only behavior.

Until that evidence is collected, release provenance remains:

```text
runtime_certification=FOLLOW_UP_REQUIRED
```
