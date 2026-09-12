# Chat Events — PlexonChats 3.3.0

Chat Events are lightweight public-chat competitions. One immutable run is active at most; the first eligible correct native Minecraft public-chat answer wins and exactly one configured reward bundle is attempted.

## Feature and scheduler toggles

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
```

`chat-events.enabled` is the persistent master. `false` prevents both automatic and manual playable events and cancels an active run without reward.

`scheduler.enabled` controls only automatic scheduling. When it is `false`, staff may still use `/chat events start ...`. `/chat events pause` is runtime-only and does not rewrite configuration; `/chat events resume` starts a fresh initial-delay window.

The subsystem uses one Bukkit coordinator task, monotonic deadlines, no catch-up burst, and no per-player/per-message tasks. At most one competition may be `ACTIVE`.

## Event lifecycle

```text
SCHEDULED -> ACTIVE -> WON | TIMED_OUT | CANCELLED
```

Runs are ephemeral and are never restored after restart. A run owns its unique ID, definition/type, generated prompt, canonical accepted answers, participant snapshot, start/deadline, reward profile, winner, and completion reason.

Timeout, admin stop, master disable, successful configuration reload, and plugin shutdown never grant a reward. A successful reload cancels an active run and starts the scheduler from a fresh initial delay. A failed reload leaves the current run/configuration unchanged.

## Valid answers

Valid submissions must originate from accepted native Minecraft public chat in an allowed `LOCAL` or `GLOBAL` channel. They must come from an online player who passes the event's world/permission filters.

These do **not** count:

- console;
- commands, including `/g`, `/l`, `/msg`, `/reply`;
- broadcasts, auto-messages, connection messages;
- DiscordSRV-origin traffic;
- plugin/bot synthetic chat;
- messages cancelled before the synchronous `PlexonChatEvent` integration point accepts them;
- players outside configured audience filters.

During an active event, structural chat validation and the normal cancellable `PlexonChatEvent` still apply first. A correct answer is then checked before duplicate-message history so typing the same word before the event cannot steal a valid win. Wrong answers continue through normal cooldown/duplicate moderation and delivery.

## Matching

```yaml
chat-events:
  defaults:
    matching:
      case-sensitive: false
      trim: true
      collapse-whitespace: true
      unicode-normalization: NFKC
      ignore-diacritics: false
```

Answers are raw data, never trusted MiniMessage. Case folding uses `Locale.ROOT`. No fuzzy/AI matching is used. Trivia aliases must be explicitly configured.

## Audience defaults

```yaml
chat-events:
  defaults:
    duration-seconds: 30
    reveal-answer-on-timeout: true
    min-online: 1
    permission: ""
    worlds: []
    excluded-worlds: []
    accepted-channels: [LOCAL, GLOBAL]
```

Empty `worlds` means all worlds; `excluded-worlds` wins. An empty permission adds no event-specific permission. Prompts/results are sent only to the participant snapshot. Eligibility is rechecked before accepting a winner.

## Event types

### TYPE

```yaml
bingo:
  enabled: true
  type: TYPE
  weight: 12
  cooldown-seconds: 1800
  reward-profile: basic
  values: ["bingo", "plexon", "diamond"]
  prompt: "<gold><bold>CHAT EVENT</bold></gold> <dark_gray>» <gray>Type <yellow>\"{value}\"</yellow> first!"
```

### UNSCRAMBLE

```yaml
unscramble:
  enabled: true
  type: UNSCRAMBLE
  weight: 10
  cooldown-seconds: 1800
  reward-profile: rare
  words: ["diamond", "redstone", "village", "elytra", "netherite"]
  prompt: "<gold><bold>CHAT EVENT</bold></gold> <dark_gray>» <gray>Unscramble <aqua>{scrambled}</aqua>!"
```

The generator is bounded: very short/repetitive values cannot create an infinite reshuffle loop.

### MATH

```yaml
math-normal:
  enabled: true
  type: MATH
  weight: 10
  cooldown-seconds: 1200
  reward-profile: rare
  operations: [ADD, SUBTRACT, MULTIPLY]
  min-operand: 2
  max-operand: 25
  allow-negative-result: false
  prompt: "<gold><bold>CHAT EVENT</bold></gold> <dark_gray>» <gray>What's <aqua>{expression}</aqua>?"
```

Operations: `ADD`, `SUBTRACT`, `MULTIPLY`, `DIVIDE`. Division always generates an exact integer equation and never divides by zero. Arithmetic is checked/bounded and does not use floating-point equality.

### TRIVIA

```yaml
trivia:
  enabled: true
  type: TRIVIA
  weight: 6
  cooldown-seconds: 2400
  reward-profile: rare
  entries:
    - question: "Which dimension contains End Cities?"
      accepted-answers: ["the end", "end"]
  prompt: "<gold><bold>CHAT EVENT</bold></gold> <dark_gray>» <gray>{question}"
```

Every entry needs a non-empty question and at least one explicit answer.

### REVERSE

```yaml
reverse:
  enabled: true
  type: REVERSE
  weight: 6
  cooldown-seconds: 1800
  reward-profile: basic
  values: ["craft", "redstone", "plexon"]
  prompt: "<gold><bold>CHAT EVENT</bold></gold> <dark_gray>» <gray>Reverse <aqua>{value}</aqua>!"
```

Reversal uses Unicode code points rather than naïve UTF-16 character reversal.

## Selection and cooldowns

Every definition supports `enabled`, `weight`, `cooldown-seconds`, and `reward-profile`. Scheduled selection considers only valid, enabled, positive-weight, off-cooldown definitions. `weight: 0` is manual-only for explicit `/chat events start <id>` use; `start random` uses the same positive-weight selection pool as scheduling.

Cooldown begins when a playable run starts. Manual starts respect cooldown. Immediate-repeat avoidance applies after filtering and only when another choice exists.

## Reward profiles

```yaml
chat-events:
  reward-profiles:
    basic:
      economy:
        enabled: true
        amount: 500.0
      plexonkeys:
        enabled: false
        tier: BASIC
        amount: 0
      console-commands: []
    rare:
      economy:
        enabled: true
        amount: 2500.0
      plexonkeys:
        enabled: true
        tier: RARE
        amount: 1
      console-commands: []
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

### Economy

Vault/economy is optional. Zero means no mutation; negative/non-finite values are rejected. If unavailable, the cash component fails safely and is not reported as granted.

### PlexonKeys

PlexonKeys is optional and is accessed through its existing Bukkit `PlexonKeysAPI` service. Supported tier names are `BASIC`, `RARE`, `EPIC`, and `LEGENDARY`. PlexonChats does not recreate key item metadata or identify keys by display text. Missing/incompatible PlexonKeys fails only that component.

### Console commands

```yaml
console-commands:
  - "give {player_name} diamond 1"
```

Supported controlled placeholders:

- `{player_name}`
- `{player_uuid}`
- `{event_id}`
- `{event_type}`

The raw submitted answer is never available to command expansion. Commands run once as console on the primary thread. Failed commands are reported but do not trigger bundle retries.

## Exact-once reward behavior

The winner transition is atomic. Once one player changes `ACTIVE -> WON`, later correct answers are inert. The reward service also has an independent exact-once guard. Partial component failure keeps the original winner and never reopens the competition.

## Commands

| Command | Permission | Behavior |
| --- | --- | --- |
| `/chat events status` | `plexonchats.events` | Master/scheduler/runtime/active/integration state |
| `/chat events list` | `plexonchats.events` | Definitions, type, reward, weight, cooldown |
| `/chat events enable` | `plexonchats.events.manage` | Persist master enabled + transactional reload |
| `/chat events disable` | `plexonchats.events.manage` | Persist disabled; cancel active run, no reward |
| `/chat events pause` | `plexonchats.events.manage` | Runtime scheduler pause only |
| `/chat events resume` | `plexonchats.events.manage` | Resume with fresh scheduling delay |
| `/chat events start <id\|random>` | `plexonchats.events.manage` | Start one playable run |
| `/chat events stop` | `plexonchats.events.manage` | Cancel active run, no reward |
| `/chat events preview <id>` | `plexonchats.events.manage` | Private prompt/answers/reward preview; non-playable |

`preview` does not broadcast, activate, consume cooldown, or grant rewards. Tab completion only exposes management entries/IDs to authorized senders.

## GUI

`/chat admin` → **Chat Events** opens a compact dedicated page containing:

- persistent master toggle;
- scheduler/runtime state;
- pause/resume;
- start random;
- stop active;
- back/close.

Specific event start/preview remains command-driven. All GUI actions use the existing custom holder and centralized permission-aware click routing; GUI items cannot be moved/stolen through drag, shift-click, or hotbar swapping.

## Sounds and messages

`chat-events.sounds.start|win|timeout` accept a valid Bukkit/namespaced sound or `NONE`. Invalid values reject the candidate configuration.

Administrator-owned message/prompt templates use MiniMessage. Generated/player values are inserted as components rather than reparsed. Timeout has separate reveal/no-reveal output; the no-reveal path still announces expiration without leaking the canonical answer.

## Diagnostics

`/chat diagnostics` reports:

- Chat Events master/scheduler/task state;
- configured and currently selectable event counts;
- active ID/type and remaining time;
- last event/result and winner;
- economy reward state;
- PlexonKeys state;
- most recent Chat Events failure summary.

## Reload/migration

3.3.0 uses `config-version: 5`. A v4 configuration is backed up before migration and receives missing Chat Events defaults without replacing existing administrator values or custom GUI/auto-message collections.

Validation rejects the candidate on invalid scheduler bounds, unknown types, missing reward profiles, invalid math ranges/operations, malformed trivia, invalid key tiers/amounts, negative cash values, invalid sounds, or unsafe required text data. Errors identify the exact configuration path.
