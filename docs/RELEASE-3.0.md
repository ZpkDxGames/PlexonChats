# PlexonChats 3.0 Release

The 3.0 release is built from the latest checked `2.0-Update` source, commit `cefcdbe0679f75a1000d5401fb6a94fc86378e03`, on `3.0-Release`. It supersedes the withdrawn 1.1.0 proposal that incorrectly used `main`.

## What is included

- Configurable channel formats, nickname hover/click/name, channel badges, separators, item layouts, and PM layouts.
- Configurable player/admin/about GUIs with permission-aware buttons, format previews, safe inventory interactions, and persistent player preferences.
- Scheduled auto-messages and tips: independent groups, shuffled/sequential rotation, chat/action-bar/title delivery, audience filters, sounds, and preview/send/pause/resume controls.
- Optional two-way DiscordSRV integration for global chat, duplicate prevention, status reporting, and opt-in announcements. Local chat and PMs are never sent through this bridge.
- Join/quit/first-join message customization, online-count placeholders, and protection for previously hidden messages.
- Safer config migration/reloads, atomic preference saves, shared chat limits, safe placeholder rendering, recipient-only mention notifications, and bounded item previews.
- Developer moderation event, regression coverage, source-ancestry verification, and release checksums.

## Requirements and defaults

- Build/test target: **Paper 1.21.11, Java 25**. Folia is not supported.
- Optional: Vault with the corresponding providers, PlaceholderAPI with the expansions you use.
- Optional Discord adapter API: **DiscordSRV 1.30.5**. Configure the bot and game-channel mapping in DiscordSRV separately; the adapter is disabled by default.
- Example scheduled message groups are **enabled**: customize or disable them before production.
- Join/quit modes default to `DEFAULT`, retaining native messages until configured.
- Configuration schema is **3**.

## Upgrade

1. Stop the server and back up the existing JAR and `plugins/PlexonChats/` folder.
2. Install only `PlexonChats-3.0.jar`; remove the old version from the plugin directory.
3. Start the server. Missing config options are added, existing values/custom collections are kept, and the original config is backed up before migration.
4. Review the now-active saved chat/item formats. Use `/chat admin` for previews and `/chat status` for bridge/scheduler status.
5. Edit `config.yml` and use `/chat reload`; invalid reloads keep live settings.

The release assets include a SHA-256 checksum. Automated tests use MockBukkit and mocked Discord endpoints; they do not establish a live bot connection or verify Minecraft client visuals. Validate those on staging before a live-server rollout.

[Configuration guide](https://github.com/ZpkDxGames/PlexonChats/blob/v3.0/docs/CONFIGURATION.md) · [Upgrade and staging checklist](https://github.com/ZpkDxGames/PlexonChats/blob/v3.0/docs/UPGRADING.md) · [Full changelog](https://github.com/ZpkDxGames/PlexonChats/blob/v3.0/CHANGELOG.md)

Created by Tonim / ZpkDxGames.
