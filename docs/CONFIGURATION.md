# PlexonChats Configuration — 4.0.1

PlexonChats 4.0.1 uses two administrator-facing YAML files with separate responsibilities. Run `/chat reload` after editing either file. The plugin validates the candidate generation before replacing the current runtime state.

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

Bingo draw pacing is **not** duplicated in `config.yml`.

### `data.yml`

`data.yml` remains schema **1** and owns gameplay/minigame data:

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
      interval-seconds: 5
      range-min: 1
      range-max: 75
    winning:
      horizontal: true
      vertical: true
      diagonal: true
      full-house: false
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

The bundled file contains the complete stock TYPE/UNSCRAMBLE/MATH/TRIVIA/REVERSE/BINGO library. Runtime cards, manual marks, call history/deadlines, and current cooldown timestamps remain runtime state rather than being rewritten into `data.yml`.

## Bingo renderer settings

`render.style` accepts:

- `TABLE` — deterministic table geometry with fixed cell widths and separators; fresh 4.0.1 default.
- `COMPACT` — 4.0.0-compatible borderless presentation.

`show-border` remains accepted for compatibility. `TABLE` plus `show-border: true` renders the full box; `TABLE` plus `show-border: false` keeps deterministic aligned cell separators without exterior borders. Existing 4.0.0 schema-1 files that do not contain `render.style` are not rewritten solely to add the key; runtime compatibility is inferred from their existing `show-border` value.

The whole table grid uses the configured fixed-width Adventure font. `minecraft:uniform` is the stock value. Marked/winning states must not alter the visible characters inside a cell.

## Scheduler/randomizer validation

The scheduler requires non-negative initial delay/minimum-online and a positive interval range where minimum <= maximum. Randomizer mode accepts `WEIGHTED` or `UNIFORM`; history size must be non-negative.

Disabled or cooldown-active minigames are excluded before selection. The scheduler honors normal minimum-online values and avoids unbounded random retry loops.

## Bingo validation

At minimum:

- lobby duration must be positive;
- reminder thresholds must be positive, unique, and not exceed lobby duration;
- normal/admin participant minimums must be at least 1;
- first-call delay must be `0..300` seconds;
- draw interval must be `1..300` seconds;
- at least one winning pattern must be enabled;
- `render.style` must be `TABLE` or `COMPACT`;
- renderer font must be a nonblank valid Adventure key;
- left padding, cell width, and column gap are bounded;
- event weight/cooldown/duration must be valid.

Where safe, one malformed minigame is disabled for that runtime generation rather than taking down unrelated chat features.

## Bingo mechanics controlled by `data.yml`

The Bingo definition controls lobby timing, participation thresholds, card/free-center rule, draw timing, enabled patterns, renderer behavior/geometry, weight, cooldown, duration, reward-profile reference, channels, world/permission eligibility, and normal minimum-online requirements.

Draw calls and participant marks are separate. A called number is only eligible to be manually marked; it is never automatically inserted into a participant's marked-cell set.

Fresh 4.0.1 data uses a five-second interval, but administrator values remain authoritative. For example, `interval-seconds: 7` remains 7 after reload. Existing 4.0.0 installations retaining `8` are not silently changed.

## Existing-server 5-second cadence

To adopt the recommended 4.0.1 pacing on an existing server, edit:

```yaml
# plugins/PlexonChats/data.yml
minigames:
  bingo-classic:
    draws:
      first-call-delay-seconds: 5
      interval-seconds: 5
```

Then apply it with:

```text
/chat reload
```

No equivalent Bingo interval should be added to `config.yml`.

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

## Migration and preservation

When `data.yml` is absent, PlexonChats creates `config-before-v4-<timestamp>.yml` and migrates compatible administrator-owned older Chat Events gameplay into schema-1 `data.yml`. Secrets remain in `config.yml`.

4.0.1 does not bump schema 1 just to change stock defaults. If `data.yml` already exists, its administrator values are preserved. This includes a deliberately configured 8-second Bingo interval, renderer geometry, and compatibility presentation settings.

## Reload safety

`/chat reload` reloads `config.yml` and `data.yml` as one runtime generation. Invalid structural data/configuration does not silently replace the current known-good configuration. Active event/Bingo runtime state is closed through the normal manager lifecycle rather than persisted as a half-valid YAML snapshot, and stale draw tasks must not continue after reload.

See [BINGO.md](BINGO.md), [CHAT-EVENTS.md](CHAT-EVENTS.md), and [UPGRADING.md](UPGRADING.md).
