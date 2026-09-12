# Configuration guide

Edit `plugins/PlexonChats/config.yml` using UTF-8 and spaces. Keep one copy of each top-level YAML key. The bundled configuration is the authoritative schema example; snippets below are partial sections to merge into an existing file.

Run `/chat reload` after editing. PlexonChats parses, migrates, and validates the complete candidate before publishing it. Invalid YAML or invalid Chat Events/scheduler/reward data does not replace the known-good live generation. Schema v5 migration backs up an older file as `config-before-v5-*.yml` before mutation.

## Text and trust boundaries

Administrator-owned templates use MiniMessage. Dynamic player/provider values are inserted as components, not reparsed as trusted markup. Player Chat Event answers are always raw data: they are not MiniMessage, commands, or PlaceholderAPI templates.

Common placeholders include `{player}`, `{player_name}`, `{display_name}`, `{rank}`, `{rank_prefix}`, `{balance}`, `{world}`, `{uuid}`, `{playtime}`, `{ping}`, `{online}`, `{max_players}`, `{server_name}`, `{version}`, `{channel_badge}`, `{separator}`, `{message}`, `{channel}`, `{channel_id}`, `{radius}`, `{global_shortcut}`, and GUI status placeholders.

Chat Event prompt/result placeholders additionally include trusted generated/configured values such as `{event_id}`, `{event_type}`, `{prompt}`, `{question}`, `{value}`, `{scrambled}`, `{expression}`, `{answer}`, `{winner}`, `{winner_uuid}`, `{elapsed}`, `{elapsed_ms}`, `{reward_profile}`, `{reward_summary}`, and `{remaining}`. Do not place the raw player-submitted answer into command or trusted-template expansion.

## Public channels

```yaml
channels:
  local:
    enabled: true
    radius: 100
    format: "{channel_badge} {rank_prefix}{player}{separator}<gray>{message}"
    receive-permission: ""
  global:
    enabled: true
    format: "{channel_badge} {rank_prefix}{player}{separator}<white>{message}"
    shortcut-prefix: "!"
    receive-permission: ""
```

LOCAL delivery is same-world and radius-bounded. Channel permissions control sending; an optional receive permission independently restricts recipients. The selected channel controls outgoing public messages only.

`PlexonChatEvent` remains synchronous and cancellable before public delivery. Chat Events accept answers only from the native public-chat route after that cancellation point; command shortcuts and synthetic sends are not valid competition submissions.

## Player formatting, PMs, mentions, items

- `chat-components.player` configures nickname format/hover/click behavior.
- `private-messages.sent-format` supports `{target}`/`{message}`; received format supports `{sender}`/`{message}`.
- `mentions.format`, action-bar text, and sound control mention presentation.
- `item-display` controls `[item]`/`@hand`, rich item hover, and bounded preview tokens.
- `formatting.allow-advanced-player-tags` should remain false unless advanced player MiniMessage tags are intentionally trusted.

## Connection messages

`connection-messages.join|quit.mode` accepts `DEFAULT`, `CUSTOM`, or `HIDDEN`. CUSTOM mode uses the configured MiniMessage template, DEFAULT preserves the native event message, and HIDDEN removes it. `respect-hidden` and the configured silent permission preserve vanish/privacy behavior.

## GUI

`gui`, `gui.admin`, `gui.events`, and `gui.creator` use configurable row counts and item maps. Slots are zero-based. The stable custom-holder and centralized click router block inventory transfer, drag, shift-click, hotbar swaps, and stale-view actions.

Core actions include:

- player: `LOCAL`, `GLOBAL`, `TOGGLE_MENTIONS`, `TOGGLE_TIPS`, `TOGGLE_PRIVATE_MESSAGES`;
- navigation: `OPEN_MAIN`, `OPEN_ADMIN`, `OPEN_CHAT_EVENTS`, `OPEN_CREATOR`, `CLOSE`;
- admin: `RELOAD`, `PREVIEW_FORMAT`, `TOGGLE_AUTO_MESSAGES`;
- Chat Events: `TOGGLE_CHAT_EVENTS`, `PAUSE_CHAT_EVENTS`, `RESUME_CHAT_EVENTS`, `START_RANDOM_CHAT_EVENT`, `STOP_CHAT_EVENT`;
- utility: `PLAYER_COMMAND`, `MESSAGE`, `LINK`, `NONE`.

Button `permission` adds a requirement; it never removes an action's built-in permission. `hide-without-permission` hides rather than merely locks the entry.

## Auto-messages

Auto-messages remain separate from Chat Events. One shared coordinator services every enabled group. Groups support `SEQUENTIAL`/`SHUFFLE`, CHAT/ACTION_BAR/TITLE delivery, initial delay/interval, audience filters, tip preference, sound, and optional safe Discord forwarding.

```yaml
auto-messages:
  enabled: true
  groups:
    tips:
      enabled: true
      interval-seconds: 300
      initial-delay-seconds: 60
      order: SHUFFLE
      min-online: 1
      respect-tip-preference: true
```

Reload replaces the coordinator instead of stacking tasks. Missed intervals do not create catch-up bursts.

# Chat Events

The complete event/reward/administration reference is in [CHAT-EVENTS.md](CHAT-EVENTS.md).

## Master and scheduler

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

- master enabled + scheduler enabled: automatic and manual events are available;
- master enabled + scheduler disabled: manual events remain available;
- master disabled: no playable event may start and an active run is cancelled without reward.

Only one event can be active. The coordinator uses monotonic time, no catch-up burst, and one repeating task. `weight: 0` is manual-only; positive-weight enabled/off-cooldown definitions participate in random scheduling.

## Defaults and matching

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
    matching:
      case-sensitive: false
      trim: true
      collapse-whitespace: true
      unicode-normalization: NFKC
      ignore-diacritics: false
```

Empty `worlds` means all worlds; exclusions win. Empty permission means no extra event permission. Prompts/results are sent only to eligible participants. Valid answers must originate from native accepted Minecraft public chat; PMs, commands, Discord, broadcasts, auto-messages, connection messages, console, and plugin-generated sends do not count.

## Required event types

`TYPE`, `UNSCRAMBLE`, `MATH`, `TRIVIA`, and `REVERSE` are available. Every definition supports:

```yaml
enabled: true
weight: 10
cooldown-seconds: 1800
reward-profile: rare
```

TYPE/REVERSE use `values`; UNSCRAMBLE uses `words`; MATH uses `operations`, `min-operand`, `max-operand`, and `allow-negative-result`; TRIVIA uses administrator-authored `entries` with `question` and `accepted-answers`.

MATH supports ADD/SUBTRACT/MULTIPLY/exact integer DIVIDE. Impossible/unsafe ranges reject the candidate configuration. UNSCRAMBLE is bounded for repetitive/short words. REVERSE uses Unicode code points.

## Reward profiles

```yaml
chat-events:
  reward-profiles:
    rare:
      economy:
        enabled: true
        amount: 2500.0
      plexonkeys:
        enabled: true
        tier: RARE
        amount: 1
      console-commands: []
```

Economy and PlexonKeys are optional component integrations. Supported key tier names are `BASIC`, `RARE`, `EPIC`, and `LEGENDARY`. Missing providers produce a component failure without crashing chat, retrying the bundle, or selecting another winner.

Console commands run as console on the primary thread and may use only `{player_name}`, `{player_uuid}`, `{event_id}`, and `{event_type}`. Empty command lists are valid.

The winner transition and reward attempt are exact-once. Timeout, stop, disable, reload cancellation, and preview grant nothing.

## Chat Events sounds/messages

```yaml
chat-events:
  sounds:
    start: { sound: BLOCK_NOTE_BLOCK_PLING, volume: 1.0, pitch: 1.2 }
    win: { sound: UI_TOAST_CHALLENGE_COMPLETE, volume: 1.0, pitch: 1.0 }
    timeout: { sound: BLOCK_NOTE_BLOCK_BASS, volume: 1.0, pitch: 1.0 }
  messages:
    prefix: "<bold><gradient:#ffd66b:#ff9f43>Chat Event</gradient></bold> <dark_gray>» "
    winner: "<green><bold>{winner}</bold></green> <gray>answered correctly in <white>{elapsed}</white>! <gray>Reward: {reward_summary}"
    timed-out: "<yellow>Time's up!</yellow> <gray>The answer was <white>{answer}</white>."
    timed-out-hidden: "<yellow>Time's up!</yellow>"
```

`NONE` is a valid sound. Invalid configured sounds reject the candidate. The hidden timeout template is used when answer reveal is disabled and must not expose `{answer}`.

## Chat Events commands/permissions

| Command | Permission |
| --- | --- |
| `/chat events status` | `plexonchats.events` |
| `/chat events list` | `plexonchats.events` |
| `/chat events enable|disable` | `plexonchats.events.manage` |
| `/chat events pause|resume` | `plexonchats.events.manage` |
| `/chat events start <id\|random>` | `plexonchats.events.manage` |
| `/chat events stop` | `plexonchats.events.manage` |
| `/chat events preview <id>` | `plexonchats.events.manage` |

`pause` is runtime-only. `preview` is private/non-playable and does not consume cooldown or grant a reward.

## DiscordSRV

DiscordSRV remains optional and isolated. Only approved GLOBAL Minecraft player chat can be exported. Discord-origin messages never count as Chat Event answers. Chat Event prompts/results are Minecraft-only in 3.3.0.

Configure DiscordSRV itself separately, then enable the PlexonChats adapter with `integrations.discordsrv`. LOCAL chat and PMs remain private.

## Operational validation

After configuration changes, check:

```text
/chat status
/chat diagnostics
/chat events status
/chat events list
/chat events preview bingo
```

Then test normal LOCAL/GLOBAL chat, PMs, GUI controls, auto-messages, and an event start/win/timeout/cancel sequence on staging. Repeated reloads must retain exactly one auto-message timer and one Chat Events coordinator rather than stacking tasks.
