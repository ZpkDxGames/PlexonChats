# PlexonChats Configuration — 3.5.0

Edit `plugins/PlexonChats/config.yml` using UTF-8 and spaces, then run `/chat reload`. PlexonChats parses, migrates and validates the complete candidate before publishing it. Invalid YAML, MiniMessage, GUI, Chat Event, reward or Bingo data does not replace the known-good runtime generation.

Configuration schema is `7`. A successful upgrade creates `config-before-v7-<timestamp>.yml` before writing the migrated file.

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
    discord:
      enabled: false
      webhook-url: ""
      username: "PlexonChats Bingo"
      send-start: true
      send-draws: true
      send-win: true
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

Bingo uses the existing global Chat Events coordinator. Do not configure or expect a second timer service.

### Winning patterns

At least one winning pattern must be enabled. Horizontal, vertical and diagonal are enabled by default. `full-house: true` adds the 25-cell blackout pattern; it does not disable normal line wins.

### Discord webhook

`discord.enabled` requires a configured webhook URL. The URL is treated as a secret and is never rendered in normal status/diagnostic/GUI output. Start/draw/win delivery toggles independently control outbound Bingo board messages.

Webhook transport is asynchronous and bounded. Delivery failure is non-authoritative and cannot alter the game.

## Bingo event definitions

Each `type: BINGO` definition remains a normal scheduler definition. Its administrator-owned values include:

- event ID and name;
- enabled state;
- weight;
- cooldown;
- reward-profile reference;
- minimum online;
- duration;
- normal world/permission/channel eligibility inherited from Chat Events.

Gameplay-specific shared-board behavior lives under `chat-events.bingo`; per-player Bingo subtrees are obsolete.

## Removed 3.4 Bingo concepts

These concepts are not active 3.5 settings:

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

The v6 → v7 migrator removes/ignores them for explicit BINGO definitions rather than carrying them forward as active mechanics.

## Event definitions and migration ownership

`chat-events.events` is administrator-owned. Migration does not replace the collection with bundled defaults. Custom event IDs and unrelated event definitions survive.

The explicit `type` field is authoritative. An ordinary `type: TYPE` definition with ID `bingo` remains TYPE after migration and is never converted merely because of its ID.

## Rewards

Bingo uses the same `chat-events.reward-profiles` as all other event types. Profiles may combine Vault economy, PlexonKeys and administrator-authored console commands. Reward execution remains downstream of the exact-once winner boundary.

## Presentation and trust boundary

Administrator-authored text fields may use MiniMessage where supported. Raw player/provider values are inserted as Components, not parsed as trusted MiniMessage. The shared Bingo board is rendered from server state, not from user-supplied strings.

## Transactional reload

`/chat reload` validates the whole candidate, including schema migration, before replacing the current runtime generation. On failure the live configuration remains active. The pre-v7 backup provides the rollback copy for a migrated file.
