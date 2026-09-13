# Chat Events — PlexonChats 3.6.0

Chat Events are server competitions managed by one coordinator with at most one globally active run. PlexonChats 3.6.0 preserves the established TYPE, UNSCRAMBLE, MATH, TRIVIA, REVERSE and shared-board BINGO gameplay while adding a presentation-only Discord event synchronization layer.

## Coordinator and authority

The normal weighted scheduler remains authoritative. Event selection still obeys the master enabled state, scheduler state, definition enabled state, weight, cooldown, minimum-online requirements and immediate-repeat avoidance. Bingo does not create a second scheduler.

Minecraft/PlexonChats remains authoritative for:

- event creation and timing;
- answer/claim eligibility;
- Bingo board generation, draw order and marked state;
- winner selection;
- rewards and winner statistics;
- timeout, cancellation and completion.

Discord only mirrors those lifecycle states. `participation-mode: DISPLAY_ONLY` is required in 3.6.0, so Discord messages cannot answer an event or claim Bingo.

## Public answer boundary

Ordinary answer events accept answers only through native Minecraft public chat. PlexonChats raises its synchronous cancellable `PlexonChatEvent` before answer acceptance and delivery. If another integration cancels there, the message cannot win.

For Bingo, only the exact public-chat word `bingo` is treated as a claim through that accepted route. `/bingo claim` is the explicit command alternative. PM/reply, Discord-origin messages, console, auto-messages, connection messages, broadcasts and synthetic sends never enter winner validation.

## Exact-once winner path

A validated winner owns one terminal transition. Reward execution, persistent statistics and winner publication happen once for the run ID. Persistence remains in:

```text
plugins/PlexonChats/chat-events.db
```

The database tracks total wins, per-type wins, per-definition wins and winner history. A Bingo winner records a normal Chat Event win plus `BINGO` type/definition statistics.

## Discord event publisher

Every published event run normally owns one Discord message reference.

```text
START                  -> create embed
LIVE UPDATE             -> edit original embed
WIN/TIMEOUT/CANCEL/END  -> edit original embed into terminal state
```

The publisher uses a bounded daemon worker, coalesces pending live updates, and maintains per-run sequence state. Terminal state supersedes queued live state, so a delayed Bingo draw callback cannot overwrite a later winner/cancel/timeout result. Old run IDs retain separate registry entries and cannot mutate a newer event run.

If an edit fails while an event is still live, the publisher may recreate that Discord message once and replace its stored reference. It does not retry indefinitely. Publisher/transport failures are logged concisely and never feed back into gameplay, reward or statistics state.

### Transport selection

`chat-events.discord.transport` accepts:

- `AUTO` — prefer a compatible DiscordSRV transport, otherwise configured webhook fallback;
- `DISCORDSRV` — DiscordSRV 1.30.5 shaded JDA create/edit operations;
- `WEBHOOK` — webhook create with `wait=true` plus message PATCH editing.

Webhook transport is bounded and asynchronous, uses short network timeouts, performs at most one retry for retryable failures, and honors Discord `Retry-After` timing when available.

Normal DiscordSRV player-chat synchronization remains separate. Chat Event system cards are sent directly to Minecraft recipients, so they are not passed through PlexonChats' ordinary player-chat forwarding path in addition to the event embed.

## Standard event embeds

TYPE, UNSCRAMBLE, MATH, TRIVIA and REVERSE use the shared standard renderer. A start embed contains the event/challenge and optional reward summary. Winner, timeout and cancellation edit that original message rather than producing a redundant second event card.

Typical lifecycle titles include:

```text
CHAT EVENT • <TYPE>
CHAT EVENT • COMPLETED
CHAT EVENT • EXPIRED
CHAT EVENT • CANCELLED
```

## Bingo event type and live embed

Bingo retains one shared 25-number board, automatic unique calls from `1..75`, automatic server-side marking, no FREE center and first-valid-claim victory. Horizontal, vertical and diagonal patterns are enabled by default; Full House is optional.

Discord renders that exact active `BingoRun`. It does not generate a second board. The live embed can show the board, last call, draw count, enabled patterns, claim instruction and reward summary. Drawn card values are visually marked from authoritative draw history; a winning embed can distinguish the accepted winning cells.

Bingo uses one persistent Discord message:

```text
BINGO • LIVE BOARD
BINGO • WINNER
BINGO • TIMED OUT
BINGO • CANCELLED
BINGO • ENDED
```

Draws edit the live message rather than posting one message per call.

See [BINGO.md](BINGO.md).

## Administration

Player dashboard:

```text
/chat events
```

Text/admin routes:

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
/chat events discord status
/chat events discord test
```

`/chat events discord status` and `/chat events discord test` require `plexonchats.admin.events.discord`. The test action emits a clearly marked presentation-only embed; it does not create an event, issue a reward or modify statistics.

The Chat Events dashboard exposes safe Discord state: publishing enabled/disabled, transport, transport readiness, target-channel configuration, display-only participation, active-message state and pending-update state. No webhook URL is rendered.

## Configuration

The 3.6.0 Discord section is under `chat-events.discord`:

```yaml
chat-events:
  discord:
    enabled: false
    transport: AUTO
    channel-id: ""
    participation-mode: DISPLAY_ONLY
    webhook:
      enabled: false
      url: ""
      username: "PlexonChats Events"
    embeds:
      timestamp: true
      show-reward: true
      show-winner-avatar: false
      show-event-type: true
      show-footer: true
    updates:
      edit-existing-message: true
      minimum-edit-interval-ms: 1000
    events:
      bingo:
        enabled: true
        show-live-board: true
        update-on-draw: true
        show-last-call: true
        show-draw-count: true
        show-patterns: true
        announce-winner: true
      math: { enabled: true }
      type: { enabled: true }
      unscramble: { enabled: true }
      trivia: { enabled: true }
      reverse: { enabled: true }
```

Webhook URLs are credentials. Commands, GUI, diagnostics, errors and embeds report only safe state and never print the configured URL.

## Reload and migration

3.6.0 uses `config-version: 8`. A valid older configuration is backed up as:

```text
config-before-v8-<timestamp>.yml
```

The v7 → v8 migration maps the former `chat-events.bingo.discord` values into the generalized Discord section when possible. Existing webhook URL/username and Bingo draw/winner delivery intent are retained without exposing the secret. The obsolete duplicate subtree is then removed.

`chat-events.events` and `chat-events.reward-profiles` remain administrator-owned. Custom IDs, weights, cooldowns, reward references, timers, enable states and unrelated definitions are not replaced by bundled defaults. Earlier v6 → v7 Bingo migration remains supported for multi-generation upgrades.

Invalid upgraded configuration rejects transactionally and keeps the previous live runtime generation.

## Performance and lifecycle boundary

- no per-player repeating Bingo task or card state;
- no synchronous SQLite work on the public-chat path;
- no synchronous Discord HTTP/JDA wait on the Paper thread;
- bounded Discord queues/executors only;
- coalesced Bingo live updates;
- controlled publisher/transport shutdown on reload/disable;
- no resume assumption for stale Discord messages after a server restart;
- no repeated board generation or draw-pool reshuffle within a run.

Live PlexonCraft/Discord behavior still requires real-server certification after source/release closure when host evidence is not available during engineering.
