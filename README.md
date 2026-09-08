# PlexonChats

Configurable local/global chat, interactive nicknames and item previews, private messages, player menus, scheduled tips, and an optional DiscordSRV bridge for Paper.

## Version 3.0

Built from `2.0-Update` at `cefcdbe0679f75a1000d5401fb6a94fc86378e03`, on the `3.0-Release` branch. This replaces the withdrawn 1.1.0 proposal based on `main`.

- **Chat you control:** channel layouts, nickname text, hover lines, click actions, badges, separators, and private-message layouts live in `config.yml`.
- **Configurable menus:** 1–6 rows, custom slots/materials/lore/actions, permission-aware buttons, an administration page, and private format previews.
- **Saved player settings:** outgoing channel, mention alerts, optional tips, and private-message reception survive reconnects and restarts.
- **Scheduled messages:** independent interval-based groups, sequential or shuffled rotation, chat/action-bar/title delivery, audience filters, and pause/resume/preview controls.
- **Optional DiscordSRV:** global chat ↔ one mapped Discord game channel, duplicate suppression, integration status, and explicit opt-in announcement forwarding.
- **Join/leave customization:** native, custom, or hidden messages; first-join text, accurate online counts, and respect for previously hidden messages.
- **Safer operation:** backed-up config migration, rejected invalid reloads, bounded item previews, chat rate limits, safe placeholder insertion, and recipient-only mention alerts.

## Requirements

- Paper **1.21.11** and **Java 25**. This is the build/test target; newer server releases need their own compatibility check. Folia is not supported.
- Optional: **PlaceholderAPI** with the expansions you use; **Vault** with a chat/permissions or economy provider.
- Optional: **DiscordSRV 1.30.5**. The adapter targets its 1.x API and is disabled until configured. No DiscordSRV classes are bundled in PlexonChats.

## Install or upgrade

Download the JAR and checksum from the [3.0 release](https://github.com/ZpkDxGames/PlexonChats/releases/tag/v3.0). See [release notes](docs/RELEASE-3.0.md).

1. Stop the server and back up the PlexonChats plugin folder and old JAR.
2. Replace the old JAR with `PlexonChats-3.0.jar` in `plugins/`. Keep only one PlexonChats JAR.
3. Start the server. Existing 2.0 settings are retained, missing options are added, and the original config is backed up before migration.
4. Edit `plugins/PlexonChats/config.yml`, then run `/chat reload`. Use `/chat admin` to preview formats and `/chat status` to inspect the scheduler/bridge.

**Upgrade note:** 2.0 ignored some format settings. Version 3.0 actually renders your saved channel/item formats, so the appearance may change. See [the upgrade guide](docs/UPGRADING.md), including rollback and staging checks.

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

`{player}` is the interactive nickname component; keep it in the channel format to retain the configured hover/click. Put `{rank_prefix}` separately where you want the Vault prefix.

See the [configuration guide](docs/CONFIGURATION.md) for placeholders, custom GUI buttons, scheduled-message examples, join/leave formats, and DiscordSRV setup. The [bundled config](src/main/resources/config.yml) contains the complete defaults and inline comments.

## Commands

| Command | Purpose | Permission |
| --- | --- | --- |
| `/chat gui` | Player channel and alert preferences | `plexonchats.gui` |
| `/chat channel <local\|global>` | Select outgoing channel | Corresponding channel permission |
| `/g [message]`, `/l [message]` | Select a channel, or send once to it | `plexonchats.global`, `plexonchats.local` |
| `/msg <player> <message>`, `/reply <message>` | Private messages | `plexonchats.tell` |
| `/announce <message>` | Server-wide announcement | `plexonchats.announce` |
| `/chat admin`, `/chat status` | Administration menu and status | `plexonchats.manage` |
| `/chat reload` | Validate and apply configuration | `plexonchats.reload` |
| `/chat automessages list` | Show scheduler state and groups | `plexonchats.automessages` |
| `/chat automessages preview <group>` | Private, silent preview; does not advance rotation | `plexonchats.automessages` |
| `/chat automessages send <group>` | Send the next message to eligible players now | `plexonchats.automessages` |
| `/chat automessages pause`, `resume` | Temporarily pause/resume automatic delivery | `plexonchats.automessages` |

All `/chat` subcommands also require `plexonchats.use`. Aliases: `/tell`, `/w`, `/r`, `/broadcast`, and `/bc`.

Normal players get chat, GUI, items, mentions, PMs, and color/gradient formatting by default. Administration and bypass permissions default to operators. Full definitions are in [plugin.yml](src/main/resources/plugin.yml). Advanced player click/hover tags need both the explicit config option and `plexonchats.formatting.advanced`.

## Build and test

With Maven 3.9+ and JDK 25:

```sh
mvn --batch-mode --no-transfer-progress clean verify
```

The deployable JAR is `target/PlexonChats-3.0.jar`. Test dependencies are not bundled. GitHub Actions verifies the 2.0 source ancestry, runs the same build, and uploads the JAR/checksum. A push to `3.0-Release` publishes `v3.0` only after verification passes; existing tags/releases are not overwritten. Pull requests and manual test runs do not publish.

Tests cover the committed 2.0 config migration, safe formatting, routing/permissions, GUI actions, preference persistence, message rotation/delivery, join/leave behavior, and DiscordSRV API behavior with mocked endpoints. They do **not** log a bot into Discord or replace a staging test on a real Paper server.

## Integration notes

Local chat and private messages are never sent through the PlexonChats Discord bridge. Global receive permissions are separate from send permissions. Players still receive public channels while choosing which channel to send to.

Moderation integrations can listen to the synchronous, cancellable [PlexonChatEvent](src/main/java/com/antondev/chats/api/PlexonChatEvent.java), which covers ordinary public chat and `/g`/`/l`. Cancel the event, change its message/recipients, or set `setDiscordAllowed(false)` before delivery. Native Paper chat cancellation and viewers set before PlexonChats' HIGHEST listener are respected. Command-based chat requires the PlexonChats event for moderation.

Created by **Tonim / ZpkDxGames** · [GitHub](https://github.com/ZpkDxGames/PlexonChats) · [Other projects](https://www.spigotmc.org/resources/authors/tonim.2341103/)
