# PlexonChats

Premium communication and presentation for Paper: local/global chat, safe MiniMessage formatting, rank/prefix presentation, interactive hover/click components, mentions, private messages, item previews, player/admin GUIs, scheduled messages, diagnostics, and optional DiscordSRV bridging.

## 3.2.0 Phase 2 release candidate

`3.2.0-rc.1` is a compatibility-preserving minor release from published `v3.1.1` (`c7191c21654b11ea55c03e5fecae629e20898319`). It is a prerelease until PlexonCraft runtime certification completes.

Phase 2 keeps the mature PlexonChats-owned delivery model and focuses on UX, configuration, integrations, diagnostics, reload reliability, and latency:

- **Single chat authority:** Paper chat → PlexonChats → synchronous cancellable `PlexonChatEvent` → one delivery path. No duplicate Core/native broadcaster is introduced.
- **No explicit task per player message:** native chat is handled on Paper's main-thread `ChatEvent`, removing the previous scheduler handoff while keeping Bukkit-sensitive routing safe.
- **Safe MiniMessage boundary:** server-authored templates may use MiniMessage; player text and dynamic player/provider values are inserted as Adventure components and are not reparsed as trusted markup.
- **Rank presentation:** cached LuckPerms user metadata is preferred when available, with Vault fallback. PlexonChats never calls LuckPerms storage/load operations from chat and never mutates LuckPerms or PlexonRanks authority.
- **Transactional reload:** schema-v4 candidates are parsed and validated before publication. If a later subsystem refresh fails, the previous known-good runtime generation is restored.
- **Scheduled messages:** one shared scheduler coordinates all groups. Routine defaults use CHAT/TITLE; legacy ACTION_BAR entries remain readable for upgrade compatibility. Preview is private and non-advancing; test-send is explicit.
- **DiscordSRV provenance:** only global Minecraft chat is exported, Discord-origin messages are not re-exported, local chat/PMs stay private, and integration failures remain non-fatal.
- **Diagnostics:** delivery/cancellation/format counters, ownership model, config revision and reload status, scheduler state, and optional integration states are available without storage/network calls.

See [the Phase 2 implementation specification](docs/PHASE2-3.2.0-IMPLEMENTATION.md) and [RC release notes](docs/RELEASE-3.2.0-rc.1.md).

## Requirements

- Paper **26.2** and **Java 25**. Folia is not supported.
- Optional **PlexonCore 2.0.4**; supported Core API range is `>=1.0 <3.0`. PlexonChats remains operational in standalone mode when Core is absent.
- Optional **LuckPerms** for cached prefix/group presentation.
- Optional **Vault** with a chat/permissions or economy provider.
- Optional **PlaceholderAPI** with the expansions used by your templates.
- Optional **PlexonRanks**; PlexonChats treats it as presentation context only and does not become the rank authority.
- Optional **DiscordSRV 1.30.5**.

Paper, Bukkit, Adventure, PlexonCore, LuckPerms, PlaceholderAPI, and DiscordSRV runtime/API classes are not shaded into the release JAR.

## Upgrade from 3.1.1

1. Stop the server.
2. Back up the current JAR and the complete `plugins/PlexonChats/` folder.
3. Replace the JAR with `PlexonChats-3.2.0-rc.1.jar` only for RC/staging validation.
4. Keep your existing `config.yml` and `players.yml`.
5. Start Paper and inspect `/chat diagnostics` before player validation.

Schema upgrades are idempotent. Existing compatible settings and player preferences are retained; an old config is backed up before migration. A malformed or invalid candidate is rejected rather than partially installed.

Rollback baseline: `v3.1.1` / `c7191c21654b11ea55c03e5fecae629e20898319`. For rollback, stop the server, restore the 3.1.1 JAR and matching configuration backup, then restart.

## Chat format and MiniMessage safety

Channel formats are server-authored MiniMessage templates. Keep `{message}` in each enabled channel format. `{player}` is the interactive display-name component and `{rank_prefix}` is the separately resolved prefix component.

```yaml
channels:
  global:
    format: "{channel_badge} {rank_prefix}{player}{separator}<white>{message}"

chat-components:
  player:
    name-format: "<white>{display_name}</white>"
    hover:
      enabled: true
      lines:
        - "<gray>Name <dark_gray>› <white>{player_name}"
        - "<gray>Rank <dark_gray>› <white>{rank}"
    click:
      action: SUGGEST_COMMAND
      value: "/msg {player_name} "
```

Dynamic player chat is content, not trusted MiniMessage source. Display names/provider values have inherited click/hover/insertion interactions removed before use where required. Do not deliberately enable advanced player tags for ordinary players.

## Rank, LuckPerms, Vault and PlexonRanks

`{rank_prefix}` and `{rank}` are presentation values. PlexonChats first checks the already-cached LuckPerms `User`; it never calls `loadUser` or performs a database/network lookup for a message. If cached LuckPerms data is unavailable, Vault presentation remains the fallback where configured.

PlexonRanks and external LuckPerms groups remain authoritative. PlexonChats does not create, delete, rename, or modify groups/ranks.

## Hover and click actions

Supported server-configured actions are `NONE`, `SUGGEST_COMMAND`, `RUN_COMMAND`, `OPEN_URL`, and `COPY_TO_CLIPBOARD`. Configuration validation rejects malformed actions/URLs before activation. Use `SUGGEST_COMMAND` for ordinary player-chat interactions unless a real installed command specifically requires another action.

## Auto-messages and tips

One shared coordinator services every group. The default tips group uses a 300-second interval, 60-second initial delay, SHUFFLE rotation, minimum-online 1, opt-out support, CHAT delivery, and the pling sound at volume 1.0 / pitch 1.1.

Administration:

```text
/chat automessages list
/chat automessages enable
/chat automessages disable
/chat automessages pause
/chat automessages resume
/chat automessages preview <group>
/chat automessages test <group>
/chat automessages send <group>
```

`preview` is private and does not broadcast or advance the group. `test` is an explicit live test. `send` performs the normal immediate group send and resets that group's next scheduled deadline. Reload/disable cancels the old coordinator before a replacement is created.

## DiscordSRV

Set `integrations.discordsrv.enabled: true` only after DiscordSRV is installed and its game-channel mapping exists. Minecraft GLOBAL messages may relay to Discord; LOCAL and PM traffic never does. Discord-origin traffic is rendered distinctly through the configured incoming wrapper and is not exported back to Discord.

Network-facing sends are not executed as blocking operations on the server thread. If DiscordSRV is absent/unavailable, Minecraft chat continues and diagnostics reports the integration state.

## Moderation and mentions

PlexonChats observes Paper chat at `HIGHEST` with `ignoreCancelled = true`, so an already-cancelled lower-priority moderation result remains cancelled. It captures the event's edited message and viewer restrictions before taking over delivery. `PlexonChatEvent` is the synchronous, cancellable pre-delivery integration boundary for PlexonChats public chat.

Mentions use player identity matching rather than arbitrary substring delivery. Mention alerts respect per-player preferences and only notify recipients that actually received the public message. PM text cannot notify uninvolved third parties.

## Commands

| Command | Purpose | Permission |
| --- | --- | --- |
| `/chat gui` | Player channel and alert preferences | `plexonchats.gui` |
| `/chat channel <local\|global>` | Select outgoing channel | corresponding channel permission |
| `/g [message]`, `/l [message]` | Select/send to global or local chat | `plexonchats.global`, `plexonchats.local` |
| `/msg <player> <message>`, `/reply <message>` | Private messages | `plexonchats.tell` |
| `/announce <message>` | Server-wide announcement | `plexonchats.announce` |
| `/chat admin`, `/chat status` | Administration and concise state | `plexonchats.manage` |
| `/chat diagnostics` | Runtime/ownership/integration diagnostics | `plexonchats.manage` |
| `/chat reload` | Transactional config validation/reload | `plexonchats.reload` |
| `/chat automessages ...` | Scheduled-message administration | `plexonchats.automessages` |

All `/chat` subcommands also require `plexonchats.use`. Existing aliases remain supported.

## Configuration validation and reload lifecycle

Before a candidate becomes live, PlexonChats validates relevant MiniMessage templates, required chat message tokens, GUI rows/materials/actions/slots, duplicate slots, auto-message timing/min-online/order/delivery/content, configured sounds, connection-message modes, and click definitions.

Reload sequence is conceptually:

```text
parse → migrate → validate → publish immutable config generation → refresh integrations/services
```

If parse/validation fails, the current runtime is untouched. If a later runtime refresh fails, PlexonChats restores the previous configuration snapshot and rebuilds that known-good generation. Repeated reloads replace—not multiply—scheduler/integration state.

## Diagnostics

`/chat diagnostics` reports the plugin/runtime versions, Core mode/module state, active chat ownership model, native/public delivery counters, `PlexonChatEvent` cancellations, format failures, configuration revision, last reload result, recent integration failure summary, channel/API/preference state, auto-message groups/task state, item-preview/GUI state, and PlaceholderAPI/Vault/LuckPerms/PlexonRanks/DiscordSRV availability.

Diagnostics reads in-memory/runtime state only; it does not issue provider storage or network calls.

## PlaceholderAPI and public API

Existing PlaceholderAPI behavior remains compatible; placeholder resolution must not cause PlexonChats to issue synchronous database/network/provider-storage work. PlexonChats does not duplicate PlexonRanks placeholders simply to expose the same authority twice.

Resolve `com.antondev.chats.api.PlexonChatsAPI` through Bukkit `ServicesManager`. Existing API package names remain stable. Mutating API operations are primary-thread-only.

`PlexonChatEvent` remains synchronous, cancellable, and pre-delivery. A cancellation prevents Minecraft delivery and Discord export. PMs do not fire it.

## PlexonCore ownership

PlexonCore supplies module lifecycle/health integration when available. PlexonChats remains the chat authority unless a future public Core chat contract explicitly replaces that ownership. Phase 2 does not create parallel Core + local routes.

## Build and release verification

With Maven 3.9+ and JDK 25, provision PlexonCore 2.0.4 as the pinned `provided` API dependency and run:

```sh
mvn --batch-mode --no-transfer-progress clean verify
```

The RC artifact is `target/PlexonChats-3.2.0-rc.1.jar`. GitHub Actions also produces `SHA256SUMS.txt`, `TEST_SUMMARY.txt`, and `PROVENANCE.txt`, verifies Java class major **69**, required resources, dependency isolation, checksum integrity, source whitespace, and exact candidate ancestry.

Tags `v3.2.0-rc.*` rebuild and retest the exact tag before publishing a GitHub **prerelease**. Stable `v3.2.0` must not be published until runtime certification succeeds.

## PlexonCraft runtime certification

Stable promotion remains blocked until a representative 3.1.1 migration validates ordinary chat, LuckPerms/PlexonRanks presentation, hover/click rendering, MiniMessage injection safety, cancelled-message interoperability, repeated and failed reloads, CHAT/TITLE auto-messages, opt-out/min-online/scheduler lifecycle, DiscordSRV both directions with no echo, moderation/mentions, PlaceholderAPI/API/Core/cross-plugin behavior, Spark/MSPT comparison, and at least a 30-minute soak with zero HIGH/CRITICAL defects.

Created by **Tonim / ZpkDxGames**.