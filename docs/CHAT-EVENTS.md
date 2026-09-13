# Chat Events — PlexonChats 4.0.0

Chat Events remain server-authoritative competitions managed by one bounded coordinator with at most one globally reserved or active run. PlexonChats 4.0.0 keeps TYPE, UNSCRAMBLE, MATH, TRIVIA, and REVERSE behavior while replacing Bingo with a joinable participant-owned manual-mark model and moving gameplay definitions into `data.yml`.

## Authority and scheduling

Minecraft/PlexonChats remains authoritative for event selection, timing, answer eligibility, Bingo participation/cards/calls/marks/claims, winner selection, rewards, statistics, timeout, and cancellation. Discord is presentation-only.

The scheduler builds eligible definitions before choosing a result. Disabled/cooldown-active definitions are excluded, minimum-online requirements are honored, weighted selection and immediate-repeat avoidance remain supported, and a selected Bingo definition reserves its lobby instead of jumping straight to ACTIVE.

Admin starts bypass normal scheduling eligibility only for administration/testing; they do not rewrite production thresholds.

## Standard event types

`TYPE`, `UNSCRAMBLE`, `MATH`, `TRIVIA`, and `REVERSE` continue using the established public-chat answer boundary and exact-once completion path. Rewards, persistent statistics, compact presentation, hover components, Discord synchronization, timeout/cancel behavior, and accepted-channel rules remain intact.

Only native accepted Minecraft public chat can win a standard answer event. PM/reply, Discord-origin messages, console, auto-messages, connection messages, broadcasts, and synthetic sends do not become answers.

## Bingo reservation and lobby

Bingo occupies the same global event slot but now begins in `LOBBY`. Players explicitly join with the clickable JOIN action or `/bingo join`; no player is auto-enrolled. Stock scheduled reminders are 60, 30, 15, and 5 seconds.

Each participant owns a stable 25-cell card. Global calls do not mutate card marks. The participant marks a called cell through the server-generated run-bound click action, and only that participant's manual marked cells can satisfy a Bingo pattern.

See [BINGO.md](BINGO.md).

## Exact-once completion

Every winner path retains an exact-once terminal boundary. For Bingo, concurrent claims compete for one `ACTIVE -> WON` transition and one completion guard. One run can therefore issue at most one winner publication, reward profile execution, and persistent win record.

Persistent statistics remain in:

```text
plugins/PlexonChats/chat-events.db
```

## Discord event publisher

Every published event run normally owns one Discord message reference:

```text
START / BINGO LOBBY      -> create embed
LIVE UPDATE               -> edit original embed
WIN / TIMEOUT / CANCEL    -> terminal edit
```

The publisher remains bounded/coalesced, sequence-aware, and failure-isolated. Terminal state supersedes queued live state so an older update cannot overwrite a winner/cancel/timeout result.

For Bingo, Discord no longer renders any shared or participant card. It displays metadata such as phase, participants, last call, draw count, winner, and accepted pattern. Discord cannot join, mark, claim, or issue rewards.

Transport modes remain `AUTO`, `DISCORDSRV`, and `WEBHOOK`; `participation-mode: DISPLAY_ONLY` remains mandatory. Webhook URLs are credentials and never appear in normal commands, GUI, diagnostics, embeds, or player-facing errors.

## Administration

```text
/chat events
/chat events status
/chat events list
/chat events stats [player]
/chat events leaderboard
/chat events enable|disable
/chat events pause|resume
/chat events start <id|random>
/chat events stop
/chat events preview <id>
/chat events discord status
/chat events discord test
```

Bingo's player/admin surface is `/bingo`:

```text
/bingo
/bingo join
/bingo leave
/bingo claim
/bingo start [now]
/bingo stop
/bingo status
```

See [COMMANDS.md](COMMANDS.md).

## Configuration ownership

The split is deliberate:

- `config.yml`: master Chat Events enable switch, presentation, reward-profile bodies, sounds/messages, Discord/integration credentials.
- `data.yml`: scheduler, randomizer, minigame enabled state/weights/cooldowns/durations/eligibility/content pools, and Bingo lobby/card/draw/winning/render gameplay settings.
- `chat-events.db`: long-lived winner statistics.
- memory: current run/card/mark/call/deadline/cooldown runtime state.

`data.yml` schema starts at 1. It is parsed on load/reload, not on every card click.

## Migration

On first 4.0 startup without `data.yml`, compatible gameplay values from v3.6.2 `chat-events` configuration are migrated after writing `config-before-v4-<timestamp>.yml`. Custom event IDs/content/weights/cooldowns/durations/reward references/channels/math ranges and compatible Bingo timing/pattern settings are preserved. Integration secrets stay in `config.yml`.

Once `data.yml` exists, it is authoritative for gameplay and is not overwritten by stale legacy event definitions in `config.yml`.

Malformed individual minigames are disabled where safe so unrelated chat/event functionality can continue; structural scheduler/data errors still reject the candidate instead of publishing unsafe runtime configuration.

## Performance and lifecycle

- one bounded coordinator, not a task per participant;
- participant cards/marks remain in memory for the active run;
- no scan of every possible player/cell every tick;
- cards render on demand;
- `data.yml` is not rewritten for runtime marks/cooldowns;
- no synchronous Discord networking on the Paper main thread;
- SQLite remains off the public-chat hot path;
- tasks/transports are closed on stop/reload/disable.

Live PlexonCraft runtime certification remains `FOLLOW_UP_REQUIRED` until real server/client evidence is collected.
