# PlexonChats Configuration — 3.6.0

Edit `plugins/PlexonChats/config.yml` using UTF-8 and spaces, then run `/chat reload`. PlexonChats parses, migrates and validates the complete candidate before publishing it. Invalid YAML, MiniMessage, GUI, Chat Event, reward, Bingo or Discord event-sync data does not replace the known-good runtime generation.

Configuration schema is `8`. A successful upgrade creates `config-before-v8-<timestamp>.yml` before writing the migrated file.

## Discord Chat Events

Discord event synchronization is presentation-only in 3.6.0:

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
      math:
        enabled: true
      type:
        enabled: true
      unscramble:
        enabled: true
      trivia:
        enabled: true
      reverse:
        enabled: true
```

### Transport

`transport` accepts `AUTO`, `DISCORDSRV`, or `WEBHOOK`.

- `AUTO` prefers DiscordSRV when installed/compatible and falls back to an enabled webhook.
- `DISCORDSRV` requires DiscordSRV and resolves `channel-id` when supplied; otherwise it can use the configured PlexonChats DiscordSRV game-channel destination.
- `WEBHOOK` requires `webhook.enabled: true` and a valid HTTP(S) webhook URL.

DiscordSRV and webhook operations are asynchronous. Webhook delivery uses bounded work, short timeouts, one bounded retry for retryable errors and Discord retry timing when available.

### Participation

`participation-mode` must be `DISPLAY_ONLY` in 3.6.0. Discord users may see event state but Discord-origin messages do not become answers or Bingo claims.

### One-message lifecycle

`updates.edit-existing-message` must remain `true` in 3.6.0. Each event run creates one Discord message, then edits it on live changes and terminal completion. `minimum-edit-interval-ms` accepts `250..30000`; live Bingo updates may be coalesced while gameplay continues immediately.

### Secrets

`chat-events.discord.webhook.url` is a secret. It is never emitted by normal commands, GUI, diagnostics, embeds or player-visible errors. Status surfaces expose only whether the relevant transport/channel is configured and ready.

## Bingo

Fresh shared-board defaults:

```yaml
chat-events:
  bingo:
    enabled: true
    draw:
      first-delay-seconds: 5
      interval-seconds: 5
    winning:
      horizontal: true
      vertical: true
      diagonal: true
      full-house: false
    messages:
      start:
        - "{separator}"
        - "<gold><bold>BINGO</bold></gold> <gray>Watch the shared board and claim the first completed pattern.</gray>"
        - "<gray>Use <white>/bingo claim</white> or type <white>bingo</white> in public chat.</gray>"
        - "<gray>First call in:</gray> <yellow>5s</yellow>"
        - "{separator}"
      draw:
        - "<gold>[BINGO]</gold> <gray>Call</gray> <yellow>#{draw_count}</yellow><gray>:</gray> <white>{drawn_number}</white>"
      invalid-claim:
        - "<yellow>[BINGO] No enabled winning pattern is complete yet.</yellow>"

  events:
    bingo-classic:
      enabled: true
      name: "Classic Bingo"
      type: BINGO
      weight: 5
      cooldown-seconds: 3600
      reward-profile: epic
      min-online: 2
      duration-seconds: 420
```

### Timing

- `draw.first-delay-seconds`: `0..300`.
- `draw.interval-seconds`: `1..300` and is the authoritative interval between automatic calls.
- Event `duration-seconds` controls the overall run timeout.

Bingo uses the existing global Chat Events coordinator. There is no second timer service.

### Winning patterns

At least one winning pattern must be enabled. Horizontal, vertical and diagonal are enabled by default. `full-house: true` adds the 25-cell blackout pattern; it does not disable normal line wins.

### Discord Bingo presentation

Bingo-specific Discord switches are under `chat-events.discord.events.bingo`, not under the gameplay section. The renderer reads the same active `BingoRun` board/draw history used by Minecraft. It never creates a separate board and never renders a FREE center.

## Bingo event definitions

Each `type: BINGO` definition remains a normal scheduler definition. Administrator-owned values include event ID/name, enabled state, weight, cooldown, reward-profile reference, minimum online, duration, and normal world/permission/channel eligibility inherited from Chat Events.

Gameplay-specific shared-board behavior lives under `chat-events.bingo`; per-player Bingo subtrees are obsolete.

## Event definitions and migration ownership

`chat-events.events` is administrator-owned. Migration does not replace the collection with bundled defaults. Custom event IDs and unrelated event definitions survive. The explicit `type` field is authoritative; an ordinary `type: TYPE` definition with ID `bingo` remains TYPE.

`chat-events.reward-profiles` is also retained. Profiles may combine Vault economy, PlexonKeys and administrator-authored console commands. Reward execution remains downstream of the exact-once winner boundary.

## v7 → v8 migration

The 3.6.0 migrator preserves administrator-owned event definitions, reward profiles, timers and enable states. The old 3.5.0 Bingo-only Discord subtree:

```text
chat-events.bingo.discord
```

is mapped into the generalized `chat-events.discord` model when present. Existing enabled state, webhook URL, username, draw updates and winner announcement intent are carried forward where possible. A valid webhook URL is preserved exactly, then removed from the obsolete duplicate location.

The existing v6 → v7 shared-Bingo migration still runs first for older configurations, so multi-generation upgrades preserve the 3.5.0 shared-board corrections.

## Removed historical Bingo concepts

These participant-card concepts remain inactive:

```text
join.duration-seconds
join.min-participants
join-seconds
min-participants
board.free-center
FREE center
participant card state
manual/click mark settings
participant-only draw traffic
```

## Presentation and trust boundary

Administrator-authored text fields may use MiniMessage where supported. Raw player/provider values are inserted as Components, not parsed as trusted MiniMessage. Discord embed content is generated from validated configuration plus authoritative event state.

## Transactional reload

`/chat reload` validates the whole candidate, including schema migration and Discord event-sync settings, before replacing the current runtime generation. On failure the live configuration remains active. The pre-v8 backup provides the rollback copy for a migrated file.
