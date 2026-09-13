# Configuration guide

Edit `plugins/PlexonChats/config.yml` using UTF-8 and spaces. The bundled v6 configuration is the authoritative complete schema example. Run `/chat reload` after editing.

PlexonChats parses, migrates and validates the entire candidate before publishing it. Invalid YAML, MiniMessage, GUI, Chat Event, reward or Bingo data does not replace the known-good runtime generation. A successful migration to schema v6 first creates `config-before-v6-<timestamp>.yml`.

## Text/trust boundary

Administrator templates may use MiniMessage. Player/provider/generated values are inserted as Adventure Components and are not reparsed as trusted MiniMessage source. Raw player Chat Event answers are data only and cannot become reward commands.

Normal chat placeholders include player/channel/rank/balance/world/server fields. Chat Event presentation additionally supports event ID/type/name, description/prompt, reward, duration/remaining time, winner/UUID, total/type wins, elapsed time, answer, participant count and Bingo draw information.

## Public chat

```yaml
channels:
  local:
    enabled: true
    radius: 100
    format: "{channel_badge} {rank_prefix}{player}{separator}<gray>{message}"
  global:
    enabled: true
    format: "{channel_badge} {rank_prefix}{player}{separator}<white>{message}"
    shortcut-prefix: "!"
```

LOCAL is same-world/radius-bounded. `PlexonChatEvent` remains synchronous and cancellable before delivery. Ordinary answer events accept submissions only from the accepted native public-chat route; commands, PMs, Discord, broadcasts and synthetic/plugin sends do not count.

## GUI

The main/admin/creator menu definitions remain configuration-driven. PlexonChats 3.4 renders the Chat Events administration dashboard dynamically because it depends on live event/database/Bingo state and pagination.

All GUI surfaces retain custom `InventoryHolder` routing, permission rechecks, config-generation protection and inventory transfer/drag blocking. Dynamic Chat Event holders additionally carry selected-page and active-run context to make stale actions harmless.

## Auto-messages

Auto-messages remain independent from Chat Events and use one shared coordinator. Existing `SEQUENTIAL`/`SHUFFLE`, CHAT/TITLE/ACTION_BAR, audience and preference options remain supported.

# Chat Events

See [CHAT-EVENTS.md](CHAT-EVENTS.md) for the complete behavior reference.

## Scheduler/defaults

```yaml
chat-events:
  enabled: true
  scheduler:
    enabled: true
    initial-delay-seconds: 180
    min-interval-seconds: 600
    max-interval-seconds: 1200
    min-online: 1
    avoid-immediate-repeat: true
    pause-when-empty: true
  defaults:
    duration-seconds: 30
    reveal-answer-on-timeout: true
    min-online: 1
    permission: ""
    worlds: []
    excluded-worlds: []
    accepted-channels: [LOCAL, GLOBAL]
    matching:
      case-sensitive: false
      trim: true
      collapse-whitespace: true
      unicode-normalization: NFKC
      ignore-diacritics: false
```

Master disabled means no manual/automatic event can start. Scheduler disabled means automatic selection stops while manual start remains available. Only one event occupies the active slot.

## Presentation

```yaml
chat-events:
  presentation:
    blank-lines-before: 1
    blank-lines-after: 1
    separator: "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>"
    type-names:
      TYPE: "Type Rush"
      UNSCRAMBLE: "Unscramble"
      MATH: "Quick Math"
      TRIVIA: "Trivia"
      REVERSE: "Reverse"
      BINGO: "Bingo"
    start:
      - "{separator}"
      - "<aqua><bold>CHAT EVENT</bold></aqua> <white>{event_name}</white>"
      - ""
      - "{prompt}"
      - ""
      - "<gray>Reward:</gray> <gold>{reward}</gold>"
      - "<gray>Time:</gray> <yellow>{duration_seconds}s</yellow>"
      - "{separator}"
```

Start/winner/timeout/cancelled are line lists. Blank-line counts accept 0–3. MiniMessage is strictly validated before a runtime generation is published.

## Reward profiles

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

Vault/PlexonKeys remain optional. Supported key tiers are BASIC, RARE, EPIC and LEGENDARY. A missing provider fails only that component and does not reopen the event.

## Normal event definitions

```yaml
chat-events:
  events:
    type-rush:
      enabled: true
      name: "Type Rush"
      type: TYPE
      weight: 12
      cooldown-seconds: 1800
      reward-profile: basic
      values: ["bingo", "plexon", "diamond"]
      prompt: "<gray>Type <yellow>\"{value}\"</yellow> before anyone else!</gray>"
```

Supported types are TYPE, UNSCRAMBLE, MATH, TRIVIA, REVERSE and BINGO. `weight: 0` is manual-only. Every start respects enabled state, cooldown and eligibility.

TYPE/REVERSE use `values`; UNSCRAMBLE uses `words`; MATH uses operations/bounds; TRIVIA uses explicit question/accepted-answer entries.

## Bingo defaults

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

Per-definition overrides can be placed under `events.<id>.bingo`. The stable board variant is `BINGO_75`. Supported patterns are ROW, COLUMN, DIAGONAL, FOUR_CORNERS and FULL_HOUSE.

See [BINGO.md](BINGO.md).

## Persistent statistics

Win statistics are stored in `chat-events.db` and are not YAML configuration. There is no synchronous DB query per chat message or per GUI render; command/GUI values are cache-backed.

See [CHAT-EVENT-STATS.md](CHAT-EVENT-STATS.md).

## Commands and permissions

```text
plexonchats.events                  default true
plexonchats.events.bingo.play       default true
plexonchats.events.stats.others     default op
plexonchats.events.manage           default op
```

Player/read commands:

```text
/chat events
/chat events status
/chat events list
/chat events stats [player]
/chat events leaderboard
/chat events bingo join <run-id>
/chat events bingo card
```

Staff commands:

```text
/chat events enable|disable
/chat events pause|resume
/chat events start <id|random>
/chat events stop
/chat events preview <id>
/chat events bingo status
/chat events bingo participants
```

## Migration ownership rule

`chat-events.events` is administrator-owned. v5 → v6 does not replace or reinterpret existing event definitions. If an old customized v5 configuration contains an event ID `bingo` whose stored type is TYPE, it remains TYPE. Fresh v6 defaults use `type-rush` for the TYPE example and `bingo-classic` for real Bingo.

## DiscordSRV

DiscordSRV remains optional and isolated. Only approved GLOBAL player chat may be exported. Discord-origin traffic never counts as an ordinary Chat Event answer. Bingo participant messages are not routed through public chat.

## Operational checks

After edits:

```text
/chat status
/chat diagnostics
/chat events status
/chat events list
/chat events preview type-rush
/chat events preview bingo-classic
```

Then staging-test normal chat, one normal event, statistics persistence across restart, and a two-player Bingo round including a non-participant who must receive no repeated draw traffic.
