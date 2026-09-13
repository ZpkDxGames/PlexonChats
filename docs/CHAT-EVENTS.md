# Chat Events — PlexonChats 3.5.0

Chat Events are server competitions managed by one coordinator with at most one globally active run. PlexonChats 3.5.0 preserves the established TYPE, UNSCRAMBLE, MATH, TRIVIA and REVERSE behavior, persistent statistics, reward profiles and exact-winner boundary while correcting Bingo gameplay.

## Coordinator and active-run boundary

The normal weighted scheduler remains authoritative. Event selection still obeys:

- master `chat-events.enabled`;
- scheduler enabled state;
- definition enabled state;
- weight;
- cooldown;
- minimum-online requirements;
- immediate-repeat avoidance.

Bingo does not create a second server-wide scheduler. Scheduled `type: BINGO` definitions are selected by the same coordinator as other event types. Manual `/bingo start` and `/chat events start <id>` also enter the same manager/state validation.

## Public answer boundary

Ordinary answer events accept answers only through native Minecraft public chat. PlexonChats raises its synchronous cancellable `PlexonChatEvent` before answer acceptance and delivery. If another integration cancels there, the message cannot win.

For Bingo, only the exact public-chat word `bingo` is treated as a claim, and only through that same accepted route. `/bingo claim` is the explicit command alternative. PM/reply, Discord-origin messages, console, auto-messages, connection messages, broadcasts and synthetic sends never enter winner validation.

## Exact-once winner path

A validated winner owns one terminal transition. Reward execution, persistent statistics and winner publication happen once for the run ID. Chat Event persistence remains in:

```text
plugins/PlexonChats/chat-events.db
```

The database continues to track total wins, per-type wins, per-definition wins and winner history. A Bingo winner records a normal Chat Event win plus `BINGO` type/definition statistics.

## Bingo event type

3.5.0 Bingo uses one shared 25-number board, automatic unique calls from `1..75`, automatic server-side marking, no FREE center and first-valid-claim victory. Horizontal, vertical and diagonal patterns are enabled by default; Full House is optional.

There is no participant joining, player-specific board, clickable cell state or manual marking command.

See [BINGO.md](BINGO.md).

## Administration

Player dashboard:

```text
/chat events
```

Text/admin routes remain available:

```text
/chat events status
/chat events list
/chat events stats [player]
/chat events leaderboard
/chat events enable|disable
/chat events pause|resume
/chat events start <id|random>
/chat events stop
/chat events preview <id>
/chat events bingo board|claim|start|stop|status
```

Dedicated Bingo routes:

```text
/bingo
/bingo claim
/bingo start
/bingo stop
/bingo status
```

The Bingo GUI page shows the same manager state: run ID/state, last draw, draw count, remaining pool, next-draw ETA, active patterns, shared-board preview, reward profile, Start/Stop controls and webhook enabled/disabled state. GUI actions cannot bypass command/server validation.

## Presentation

Normal event cards remain under `chat-events.presentation`, including 0–3 configurable blank lines before/after. Bingo uses the same spacing philosophy and renders its shared chat table through Adventure Components. Player/provider values remain Components rather than trusted MiniMessage source.

## Rewards

Reward profiles remain reusable across all event types:

```yaml
chat-events:
  reward-profiles:
    epic:
      economy:
        enabled: true
        amount: 5000.0
      plexonkeys:
        enabled: true
        tier: EPIC
        amount: 1
      console-commands: []
```

Bingo has no separate reward engine. Existing Vault/PlexonKeys/console-command exact-once protections apply unchanged.

## Reload and migration

3.5.0 uses `config-version: 7`. Valid older configurations are backed up as:

```text
config-before-v7-<timestamp>.yml
```

`chat-events.events` remains administrator-owned during migration. Custom IDs, weights, cooldowns, reward references and unrelated definitions are not replaced by bundled defaults. Explicit `type: BINGO` definitions migrate from the 3.4 participant-card mechanics to the shared-board model. A TYPE definition whose ID is literally `bingo` remains TYPE.

Invalid upgraded configuration rejects transactionally and keeps the previous live runtime generation.

## Performance boundary

- no per-player repeating Bingo task;
- no per-player Bingo card state;
- no full-player scan every tick;
- no synchronous SQLite work on the public-chat path;
- no synchronous Discord webhook HTTP;
- no repeated board generation or draw-pool reshuffle within a run.

The board is 25 values and the pool is 75 values; runtime work stays correspondingly small.
