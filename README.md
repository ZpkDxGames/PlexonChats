# PlexonChats

PlexonChats **3.3.0** is the stable Plexon communication plugin for Paper 26.2 / Java 25. It owns LOCAL/GLOBAL public chat, private messages, safe MiniMessage presentation, GUI/preferences, scheduled messages, configurable Chat Events, diagnostics, and optional DiscordSRV bridging.

## 3.3.0 — Chat Events

Chat Events are lightweight server-wide competitions driven by genuine Minecraft public chat. The first eligible correct answer atomically wins the active run; exactly one reward bundle is attempted and later answers cannot win.

Built-in event types:

- `TYPE` — type a configured word first.
- `UNSCRAMBLE` — unscramble a configured word.
- `MATH` — bounded integer ADD/SUBTRACT/MULTIPLY/exact DIVIDE rounds.
- `TRIVIA` — administrator-authored questions with explicit accepted aliases.
- `REVERSE` — reverse a configured value using Unicode code points.

The subsystem uses one coordinator and allows at most one active event. It supports weighted rotation, per-definition cooldowns, minimum-online/audience filters, LOCAL/GLOBAL acceptance rules, deterministic answer normalization, bounded timeouts, manual preview/start/stop, and runtime pause/resume.

`chat-events.enabled` is the persistent master toggle. `chat-events.scheduler.enabled` controls only automatic scheduling: with the scheduler disabled, staff may still start manual events. Disabling the master cancels an active run without granting a reward.

### Answer source and moderation

Only accepted native Minecraft public chat can win. Console, commands, `/g`/`/l` shortcut sends, `/msg`/`/reply`, Discord-origin messages, auto-messages, connection messages, broadcasts, and synthetic/plugin sends are not valid submissions.

During an active competition, PlexonChats keeps its synchronous cancellable `PlexonChatEvent` contract first. A message cancelled by that integration point cannot win. A correct answer is then evaluated before duplicate-message history so a word typed before the event cannot invalidate a legitimate event answer. Wrong answers continue through the normal cooldown/duplicate moderation and delivery path. When no event is active, the stable chat hot path remains unchanged.

## Rewards

Reward profiles are reusable and may combine independent components:

- Vault economy/cash;
- optional PlexonKeys tiers (`BASIC`, `RARE`, `EPIC`, `LEGENDARY`) through the existing Bukkit service API;
- trusted console commands with controlled placeholders.

PlexonKeys and Vault are optional. A missing/incompatible integration fails only that reward component and does not reopen the competition or choose another winner. Console rewards support `{player_name}`, `{player_uuid}`, `{event_id}`, and `{event_type}`; raw player answers are never expanded into commands.

Default examples are `$500` for basic, `$2,500 + 1 Rare key`, and `$5,000 + 1 Epic key`. All mappings remain configurable.

## Stable communication boundary

- **Single chat authority:** Paper chat → PlexonChats → synchronous cancellable `PlexonChatEvent` → one delivery path.
- **No per-message scheduler handoff:** native public chat remains on the accepted Paper event path.
- **Safe MiniMessage boundary:** administrator templates may use MiniMessage; player/provider values are inserted as components, not reparsed as trusted markup.
- **Transactional configuration:** candidates are migrated/validated before publication; failed reload retains the previous known-good generation.
- **One auto-message scheduler + one Chat Events coordinator:** reload replaces rather than stacks tasks.
- **DiscordSRV isolation:** only approved GLOBAL Minecraft chat is exported; Discord-origin traffic is not echoed and never counts as a Chat Event answer.
- **Public API compatibility:** `PlexonChatsAPI` and synchronous cancellable `PlexonChatEvent` remain compatible; Chat Events do not add breaking abstract API methods.

AFK remains PlexonUtility-owned and is not duplicated in Chats.

## Requirements

- Paper **26.2**
- Java **25**
- Optional PlexonCore **2.x**; supported Core API range `>=1.0 <3.0`
- Optional Vault + economy provider for cash rewards/player presentation
- Optional PlexonKeys for key rewards
- Optional LuckPerms, PlaceholderAPI, PlexonRanks
- Optional DiscordSRV 1.30.5

Paper/Bukkit/Adventure, PlexonCore, DiscordSRV, Vault/PlexonKeys APIs and other runtime integrations are not bundled into the release JAR.

## Commands

| Command | Purpose | Permission |
| --- | --- | --- |
| `/chat gui` | Player channel/alert preferences | `plexonchats.gui` |
| `/chat channel <local|global>` | Select outgoing channel | corresponding channel permission |
| `/g [message]`, `/l [message]` | Select/send GLOBAL or LOCAL chat | `plexonchats.global`, `plexonchats.local` |
| `/msg <player> <message>`, `/reply <message>` | Private messages | `plexonchats.tell` |
| `/announce <message>` | Server-wide announcement | `plexonchats.announce` |
| `/chat admin`, `/chat status` | Administration/state | `plexonchats.manage` |
| `/chat diagnostics` | Runtime/integration diagnostics | `plexonchats.manage` |
| `/chat reload` | Transactional config validation/reload | `plexonchats.reload` |
| `/chat automessages ...` | Scheduled-message administration | `plexonchats.automessages` |
| `/chat events status` | Show Chat Events state | `plexonchats.events` |
| `/chat events list` | List configured events/cooldowns | `plexonchats.events` |
| `/chat events enable` | Persistently enable Chat Events | `plexonchats.events.manage` |
| `/chat events disable` | Persistently disable/cancel Chat Events | `plexonchats.events.manage` |
| `/chat events pause` | Pause automatic scheduling for this runtime | `plexonchats.events.manage` |
| `/chat events resume` | Resume automatic scheduling | `plexonchats.events.manage` |
| `/chat events start <id\|random>` | Start one playable event | `plexonchats.events.manage` |
| `/chat events stop` | Cancel the active event without reward | `plexonchats.events.manage` |
| `/chat events preview <id>` | Privately preview prompt/answers/reward | `plexonchats.events.manage` |

Aliases `/tell`, `/w`, `/r`, and `/chat event` remain supported where applicable.

## Administration GUI

`/chat admin` now contains a compact **Chat Events** entry. Its dedicated page provides the persistent master toggle, scheduler/runtime status, pause/resume, start-random, stop-active, back, and close controls. Specific event selection and staff-only answer preview remain command operations rather than expanding the inventory into a large dynamic browser.

The page uses the existing custom `InventoryHolder`, centralized click routing, permission rechecks, stale-view protection, and transfer/drag blocking.

## Configuration and migration

Configuration schema is **v5**. Upgrading from 3.2.0/v4 backs up the old `config.yml` as `config-before-v5-*.yml`, preserves administrator settings and user-owned GUI/auto-message collections, and adds Chat Events defaults. Invalid definitions, reward profiles, math ranges, sounds, channels, or scheduling values reject the complete candidate before activation.

A successful reload during an active event cancels that run without reward and starts scheduling from a fresh initial delay. A failed reload leaves the existing live event/configuration untouched.

See [Configuration](docs/CONFIGURATION.md) and [Upgrading](docs/UPGRADING.md).

## Diagnostics

`/chat status` stays concise and includes Chat Events state. `/chat diagnostics` additionally reports master/scheduler/task state, configured/eligible definitions, active event/remaining time, last result/winner, Vault reward readiness, PlexonKeys readiness, and recent Chat Events failure information.

## Build and release

With Maven 3.9+ and JDK 25, CI provisions the pinned PlexonCore 2.0.4 API artifact and runs:

```sh
mvn --batch-mode --no-transfer-progress clean verify
```

The stable artifact is `PlexonChats-3.3.0.jar`. CI/release verification requires a non-empty all-green suite, Java class major 69, Paper 26.2 metadata, required source/runtime classes, dependency isolation, checksums, and exact provenance.

Stable releases are rebuilt from the exact final `main` source through `release/stable` and publish:

- `PlexonChats-3.3.0.jar`
- `SHA256SUMS.txt`
- `TEST_SUMMARY.txt`
- `PROVENANCE.txt`

Live PlexonCraft deployment/smoke testing remains a separate operational follow-up when direct host access is unavailable; GitHub source/release closure does not fabricate an in-game PASS.

Created by **Tonim / ZpkDxGames**.
