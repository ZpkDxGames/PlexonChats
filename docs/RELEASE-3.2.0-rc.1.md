# PlexonChats 3.2.0-rc.1

Phase 2 release candidate for the Plexon communication layer.

## What changed

- Preserves the established LOCAL/GLOBAL chat route and synchronous `PlexonChatEvent` contract.
- Removes the explicit Bukkit scheduler task previously allocated for every native player chat message while keeping routing on the main server thread.
- Keeps player text isolated from trusted MiniMessage templates through Adventure component placeholders.
- Adds cached-only LuckPerms prefix/group presentation with Vault fallback; no LuckPerms storage load is performed by the chat path.
- Adds transactional runtime-generation rollback for configuration reload failures and schema v4 validation.
- Expands `/chat diagnostics` with delivery counters, ownership, format failures, config revision/reload state and optional integration states.
- Adds explicit auto-message enable/disable/test administration while preserving the single shared scheduler and private non-advancing preview.
- Hardens DiscordSRV duplicate prevention for the Phase 2 native chat event path.

## Compatibility

This is a minor release line from `v3.1.1`, not a public-contract reset. Existing package names, `PlexonChatsAPI`, `PlexonChatEvent`, channel IDs, `players.yml`, permissions and compatible configuration values remain supported. Legacy `ACTION_BAR` auto-message entries remain accepted for migration compatibility; Phase 2 routine defaults/documentation target CHAT/TITLE.

## Rollback

Published rollback baseline: `v3.1.1` at `c7191c21654b11ea55c03e5fecae629e20898319`.

Back up `plugins/PlexonChats/`, stop the server, restore the 3.1.1 JAR and the matching configuration backup if rollback is required.

## Runtime certification

This prerelease is not stable-certified until the PlexonCraft runtime matrix is completed, including 3.1.1 migration, chat presentation, prefix/rank integrations, repeated/failed reload, scheduler behavior, DiscordSRV both directions/no echo, PlaceholderAPI/API/Core checks, Spark/MSPT comparison and at least a 30-minute soak with zero HIGH/CRITICAL defects.
