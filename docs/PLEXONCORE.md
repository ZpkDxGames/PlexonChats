# PlexonChats and PlexonCore

PlexonChats 3.1.0 is a first-class PlexonCore module while remaining a fully functional standalone chat plugin when PlexonCore is absent or incompatible.

## Ownership boundary

PlexonCore owns shared ecosystem infrastructure:

- module registration and supported API-range validation
- STARTING, READY, DEGRADED, and FAILED lifecycle state
- ecosystem integration publication and diagnostics
- optional shared low-level utilities where semantics are demonstrably equivalent

PlexonChats continues to own the chat domain:

- public chat routing
- LOCAL and GLOBAL channels
- shortcut-prefix routing
- message validation, cooldowns, and duplicate protection
- private messages and `/reply`
- player preferences in `players.yml`
- MiniMessage and placeholder security policy
- interactive chat/player components
- item previews
- mentions
- join/quit messages
- GUI behavior
- auto-messages and scheduled tips/puns/announcements
- DiscordSRV bridge behavior
- `PlexonChatEvent`
- `PlexonChatsAPI`

Core adoption does not introduce a second chat route or a second Discord forwarding path.

## Module identity

```text
Module ID: chats
Display name: PlexonChats
Supported Core API: >=1.0 <2.0
```

The module publishes only capabilities that PlexonChats actually implements, including chat routing, local/global chat, private messages, its public API/event, player preferences, scheduled messages, item showcase, MiniMessage, PlaceholderAPI integration, Vault-backed player information, and the optional DiscordSRV bridge.

## Runtime modes

### CORE

When PlexonCore is installed, enabled, its Bukkit service is available, and Core API is within `>=1.0 <2.0`, PlexonChats registers module `chats` and publishes health/integration state.

### STANDALONE

When PlexonCore is absent, disabled, unavailable, incompatible, or cannot be linked safely, PlexonChats continues with its own chat engine. The standalone path does not reference Core API classes during normal startup, preventing optional-dependency linkage errors.

Standalone mode retains public/local/global chat, PMs, GUI, auto-messages, preferences, `PlexonChatEvent`, `PlexonChatsAPI`, and DiscordSRV when DiscordSRV itself is available.

## Lifecycle

Startup follows this order conceptually:

```text
resolve PlexonCore
→ register chats as STARTING when compatible
→ load validated configuration
→ load player preferences
→ initialize rendering/integration hooks
→ initialize chat and PM services
→ initialize item previews, scheduler, GUI, Discord bridge
→ register listeners and commands
→ register PlexonChatsAPI
→ publish READY or DEGRADED health
```

A critical startup failure marks the module FAILED when Core owns the registration and then disables PlexonChats safely.

Optional configured integrations can make the module DEGRADED without making the chat engine unusable. Intentionally disabled optional integrations do not make the module failed.

## Shutdown

Shutdown cancels/closes the existing PlexonChats resources, unregisters Bukkit services, and finally unregisters Core module `chats`. No Core lifecycle action creates an additional chat listener, scheduler, or Discord bridge.

## Diagnostics

Use:

```text
/plexon modules
/plexon integrations
/plexon diagnostics
/chat diagnostics
```

A healthy Core-backed instance reports `PlexonChats — READY`, `Mode: CORE`, and Core API 1.0.x. Diagnostics report operational state only; player chat and private-message contents are never published to Core health.
