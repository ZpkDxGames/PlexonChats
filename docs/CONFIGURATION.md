# PlexonChats Configuration — 4.0.0

PlexonChats 4.0.0 uses two administrator-facing YAML files with separate responsibilities. Run `/chat reload` after editing either file. The plugin validates the candidate generation before replacing the current runtime state.

## Ownership split

### `config.yml`

`config.yml` remains schema **v10** and owns presentation/integration concerns, including:

- chat formats and channels;
- GUI and player-facing presentation;
- auto-messages and connection messages;
- Chat Events master `chat-events.enabled` switch;
- Chat Event presentation cards/messages/sounds;
- reward-profile bodies;
- Discord event transport/credentials;
- other integrations.

### `data.yml`

`data.yml` starts at schema **1** and owns gameplay/minigame data:

```yaml
schema-version: 1

scheduler:
  enabled: true
  initial-delay-seconds: 300
  min-interval-seconds: 900
  max-interval-seconds: 1800
  minimum-online: 2
  pause-when-empty: true

randomizer:
  mode: WEIGHTED
  avoid-immediate-repeat: true
  history-size: 2

minigames:
  type-rush:
    type: TYPE
    enabled: true
    weight: 20
    cooldown-seconds: 600
    duration-seconds: 20
    reward-profile: basic
    accepted-channels: [LOCAL, GLOBAL]
    values: []

  bingo-classic:
    type: BINGO
    enabled: true
    weight: 4
    cooldown-seconds: 3600
    duration-seconds: 600
    reward-profile: epic
    min-online: 2
    accepted-channels: [LOCAL, GLOBAL]
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
    card:
      free-center: false
    draws:
      first-call-delay-seconds: 5
      interval-seconds: 8
      range-min: 1
      range-max: 75
    winning:
      horizontal: true
      vertical: true
      diagonal: true
      full-house: false
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

The bundled file contains the complete stock TYPE/UNSCRAMBLE/MATH/TRIVIA/REVERSE/BINGO library. Runtime cards, manual marks, call history/deadlines, and current cooldown timestamps remain runtime state rather than being rewritten into `data.yml`.

## Scheduler/randomizer validation

The scheduler requires non-negative initial delay/minimum-online and a positive interval range where minimum <= maximum. Randomizer mode accepts `WEIGHTED` or `UNIFORM`; history size must be non-negative.

Disabled or cooldown-active minigames are excluded before selection. The scheduler honors normal minimum-online values and avoids unbounded random retry loops.

## Bingo validation

At minimum:

- lobby duration must be positive;
- reminder thresholds must be positive, unique, and not exceed lobby duration;
- normal/admin participant minimums must be at least 1;
- first-call delay must be non-negative;
- draw interval must be positive;
- at least one winning pattern must be enabled;
- render padding/cell width/gap are bounded;
- event weight/cooldown/duration must be valid.

Where safe, one malformed minigame is disabled for that runtime generation rather than taking down unrelated chat features.

## Bingo mechanics controlled by `data.yml`

The Bingo definition controls lobby timing, participation thresholds, card/free-center rule, draw timing, enabled patterns, render geometry, weight, cooldown, duration, reward-profile reference, channels, world/permission eligibility, and normal minimum-online requirements.

Draw calls and participant marks are separate. A called number is only eligible to be manually marked; it is never automatically inserted into a participant's marked-cell set.

## Discord configuration

Discord remains in `config.yml` because credentials do not belong in gameplay data:

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
    updates:
      edit-existing-message: true
      minimum-edit-interval-ms: 1000
```

`transport` accepts `AUTO`, `DISCORDSRV`, or `WEBHOOK`. `DISPLAY_ONLY` remains mandatory. Bingo embeds expose shared lifecycle metadata only; participant cards are never rendered to a shared Discord channel.

`chat-events.discord.webhook.url` is a secret. It is not emitted by normal commands, GUI, diagnostics, embeds, or player-facing errors, and it is never migrated into `data.yml`.

## First v4 migration

When `data.yml` is absent, PlexonChats creates:

```text
config-before-v4-<timestamp>.yml
```

Then it migrates compatible administrator-owned v3.6.2 Chat Events gameplay into schema-1 `data.yml`, including custom enabled states, weights, cooldowns, durations, minimum-online values, reward references, channels, content pools, math ranges, and compatible Bingo draw/pattern settings. New lobby/manual-mark settings come from v4 defaults.

If `data.yml` already exists, the first-start migration does not overwrite its administrator values.

## Reload safety

`/chat reload` reloads `config.yml` and `data.yml` as one runtime generation. Invalid structural data/configuration does not silently replace the current known-good configuration. Active event/Bingo runtime state is closed through the normal manager lifecycle rather than persisted as a half-valid YAML snapshot.

See [BINGO.md](BINGO.md), [CHAT-EVENTS.md](CHAT-EVENTS.md), and [UPGRADING.md](UPGRADING.md).
