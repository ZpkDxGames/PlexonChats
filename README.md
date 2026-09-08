# PlexonChats

Configurable local/global chat, interactive nicknames and item previews, private messages, player menus, scheduled messages, and an optional DiscordSRV bridge for Paper.

## Version 3.1.0

PlexonChats 3.1.0 is built from the verified `3.0-Release` baseline (`4bff64e1691e79f0199baace428ec4349be9400b`) and migrates the existing 3.0 chat system to Paper 26.2 and PlexonCore without redesigning player-facing chat.

- **Core-native, still standalone:** registers module `chats` with PlexonCore 1.0.0 when available; otherwise the full chat engine continues in standalone mode.
- **Stable integration API:** `PlexonChatsAPI` is registered through Bukkit `ServicesManager`; the existing synchronous cancellable `PlexonChatEvent` remains the public-chat moderation boundary.
- **Chat you control:** channel layouts, nickname text, hover lines, click actions, badges, separators, and private-message layouts remain in `config.yml`.
- **Configurable menus:** 1–6 rows, custom slots/materials/lore/actions, permission-aware buttons, an administration page, and private format previews.
- **Saved player settings:** outgoing channel, mention alerts, optional tips, and private-message reception remain in `players.yml` and survive reconnects/restarts.
- **Scheduled messages:** one scheduler handles independent groups with sequential/shuffled rotation, chat/action-bar/title delivery, audience filters, and pause/resume/preview controls.
- **Optional DiscordSRV:** global chat ↔ one mapped Discord game channel, duplicate suppression, integration status, and explicit opt-in announcement forwarding. Local chat and PMs never bridge.
- **Safer operation:** rejected invalid reloads, bounded item previews, chat rate limits, safe placeholder insertion, recipient-only mention alerts, and diagnostics without message-content exposure.

## Requirements

- Paper **26.2** and **Java 25**. Folia is not supported.
- Optional: **PlexonCore 1.0.0**. Core API `>=1.0 <2.0` is supported; the plugin remains operational without Core.
- Optional: **PlaceholderAPI** with the expansions you use; **Vault** with a chat/permissions or economy provider.
- Optional: **DiscordSRV 1.30.5**. The adapter remains optional and no DiscordSRV classes are bundled in PlexonChats.

## Install or upgrade

For 3.0 → 3.1.0, stop the server, back up the old JAR and `plugins/PlexonChats/`, then replace only the JAR with `PlexonChats-3.1.0.jar`.

**Keep your existing `config.yml` and `players.yml`.** Version 3.1.0 does not require deleting the data folder, changing the configuration schema, or migrating player preferences to another storage system.

After startup, run:

```text
/plexon modules
/plexon diagnostics
/chat diagnostics
/chat status
```

With compatible PlexonCore present, PlexonChats should report `Mode: CORE` and module state `READY` (or `DEGRADED` only for a configured optional integration that is currently unavailable).

See [the 3.1 migration guide](docs/MIGRATION_3_1.md), [release notes](docs/RELEASE-3.1.0.md), and [upgrade guide](docs/UPGRADING.md).

## Customize a nickname

Edit these keys in the existing configuration; this is a partial example, not a replacement config:

```yaml
channels:
  global:
    format: "{channel_badge} {rank_prefix}{player}<dark_gray> » <white>{message}"

chat-components:
  player:
    name-format: "<aqua>{display_name}</aqua>"
    hover:
      enabled: true
      lines:
        - "<gold>{player_name}"
        - "<gray>Rank: <white>{rank}"
        - "<gray>World: <white>{world}"
        - "<gray>Playtime: <white>{playtime}"
        - "<yellow>Click to message"
    click:
      action: SUGGEST_COMMAND
      value: "/msg {player_name} "
```

`{player}` is the interactive nickname component; keep it in the channel format to retain configured hover/click behavior. Put `{rank_prefix}` separately where you want the Vault prefix.

See the [configuration guide](docs/CONFIGURATION.md) and the [bundled config](src/main/resources/config.yml) for complete settings.

## Commands

| Command | Purpose | Permission |
| --- | --- | --- |
| `/chat gui` | Player channel and alert preferences | `plexonchats.gui` |
| `/chat channel <local\|global>` | Select outgoing channel | Corresponding channel permission |
| `/g [message]`, `/l [message]` | Select a channel, or send once to it | `plexonchats.global`, `plexonchats.local` |
| `/msg <player> <message>`, `/reply <message>` | Private messages | `plexonchats.tell` |
| `/announce <message>` | Server-wide announcement | `plexonchats.announce` |
| `/chat admin`, `/chat status` | Administration menu and concise status | `plexonchats.manage` |
| `/chat diagnostics` | Core/chat/scheduler/integration diagnostics | `plexonchats.manage` |
| `/chat reload` | Validate and apply configuration | `plexonchats.reload` |
| `/chat automessages list` | Show scheduler state and groups | `plexonchats.automessages` |
| `/chat automessages preview <group>` | Private, silent preview; does not advance rotation | `plexonchats.automessages` |
| `/chat automessages send <group>` | Send the next message to eligible players now | `plexonchats.automessages` |
| `/chat automessages pause`, `resume` | Temporarily pause/resume automatic delivery | `plexonchats.automessages` |

All `/chat` subcommands also require `plexonchats.use`. Aliases remain `/tell`, `/w`, `/r`, `/broadcast`, and `/bc`. Full permission definitions are in [plugin.yml](src/main/resources/plugin.yml).

## Public integration API

Resolve `com.antondev.chats.api.PlexonChatsAPI` through Bukkit `ServicesManager`. It provides immutable preference snapshots and controlled channel/public-chat/PM operations while keeping internal stores, scheduler groups, PM maps, GUI state, and item-preview caches private.

Mutating API calls are primary-thread-only. See [API.md](docs/API.md).

Moderation integrations can continue listening to [PlexonChatEvent](src/main/java/com/antondev/chats/api/PlexonChatEvent.java). The event fires once, synchronously on the primary thread, after Paper lower-priority moderation edits/viewer restrictions have been captured and before PlexonChats delivers public chat. PMs do not fire it.

## PlexonCore integration

Core provides module lifecycle and observability; PlexonChats still owns chat. Core does not create a second public route, scheduler, preference store, or Discord forwarding path. See [PLEXONCORE.md](docs/PLEXONCORE.md).

## Build and test

With Maven 3.9+ and JDK 25, provision PlexonCore 1.0.0 into the local Maven repository as a `provided` dependency, then run:

```sh
mvn --batch-mode --no-transfer-progress clean verify
```

The deployable artifact is `target/PlexonChats-3.1.0.jar`. PlexonCore, Paper, and DiscordSRV runtime classes must not be bundled.

GitHub Actions provisions the pinned official PlexonCore 1.0.0 JAR, verifies its SHA-256, builds against Paper 26.2/Java 25, runs regression tests, checks distribution contents, and uploads `PlexonChats-3.1.0.jar` plus `SHA256SUMS.txt`. Production GitHub releases are tag-driven (`v*`) rather than published from ordinary branch pushes.

The test suite covers routing/permissions, moderation viewer/cancellation behavior, GUI safety, preference persistence, scheduler rotation/delivery, text/MiniMessage security, join/leave behavior, DiscordSRV behavior, and PlexonCore lifecycle contracts. Automated tests do not replace the required real Paper 26.2 staging validation before production deployment.

Created by **Tonim / ZpkDxGames** · [GitHub](https://github.com/ZpkDxGames/PlexonChats)
