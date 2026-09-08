# PlexonChats 3.1.0

PlexonChats 3.1.0 migrates the mature 3.0 chat system to the current PlexonCore/Paper platform without redesigning player-facing chat behavior.

## Highlights

- PlexonCore 1.0.0 module integration with module ID `chats`
- Core API support range `>=1.0 <2.0`
- STARTING / READY / DEGRADED / FAILED lifecycle publication
- safe standalone mode when PlexonCore is absent or unavailable
- new Bukkit `PlexonChatsAPI` service
- existing `PlexonChatEvent` contract preserved
- `/chat diagnostics`
- Paper 26.2 / Java 25 platform alignment
- tag-driven release workflow and `SHA256SUMS.txt`

## Compatibility

This release preserves the existing 3.0 package namespace, commands, aliases, permissions, configuration schema, `players.yml`, LOCAL/GLOBAL semantics, PM behavior, GUI, item previews, auto-message scheduler, MiniMessage security policy, PlaceholderAPI/Vault behavior, and DiscordSRV routing.

No data-folder reset is required. Keep the existing `config.yml` and `players.yml` during upgrade.

## Core integration

With PlexonCore 1.0.0 available, `/plexon modules` reports PlexonChats as a Core-native module. Core owns lifecycle/observability only; PlexonChats continues to own chat routing and all chat-domain behavior.

PlexonCore is a provided/soft runtime dependency and is not bundled into the PlexonChats JAR.

## Public API

Plugins can resolve `com.antondev.chats.api.PlexonChatsAPI` through Bukkit `ServicesManager` for controlled channel, preference, public-chat, PM, scheduler, and Discord-status access.

`PlexonChatEvent` remains the synchronous cancellable pre-delivery moderation event for public chat only.

## Upgrade

Stop the server, back up the old JAR and `plugins/PlexonChats/`, replace the JAR only, then start on Paper 26.2 / Java 25. See `docs/MIGRATION_3_1.md` for validation and rollback steps.
