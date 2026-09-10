# PlexonChats 3.2.0 — Phase 2 implementation specification

## Certified starting point

- Published rollback baseline: `v3.1.1`
- Rollback/source SHA: `c7191c21654b11ea55c03e5fecae629e20898319`
- Build: Maven
- Runtime: Java 25 / Paper 26.2
- PlexonCore: provided dependency, API range `>=1.0 <3.0`
- Existing public contracts retained: `PlexonChatsAPI`, synchronous cancellable `PlexonChatEvent`, config/players files, LOCAL/GLOBAL channels, PM, GUI, auto-message scheduler, PlaceholderAPI/Vault and DiscordSRV behavior.

## SemVer decision

Phase 2 is `3.2.0`, not `4.0.0`. The work is additive and reliability-oriented: public API/event names, package namespace, persisted player preferences, existing channel IDs and compatible configuration keys remain valid. Legacy `ACTION_BAR` auto-message entries remain readable for backward compatibility, but Phase 2 defaults and administration target CHAT/TITLE.

## Source audit findings

1. Chat routing is mature and centralized in `ChatManager`; do not replace it with a second delivery path.
2. `ComponentTemplate` already inserts dynamic values as Adventure components rather than reparsing them as MiniMessage source. Preserve this trust boundary.
3. Native `AsyncChatEvent` currently schedules one Bukkit task per chat message before routing. This violates the Phase 2 performance contract and must be removed without creating a duplicate Core/native route.
4. The auto-message system already uses one shared one-second coordinator and cancels/recreates it on reload. Preserve that ownership model.
5. DiscordSRV already separates Minecraft-to-Discord from Discord-to-Minecraft delivery and prevents the native DiscordSRV chat path from duplicating PlexonChats global delivery. Preserve that provenance boundary and surface failures diagnostically.
6. Rank presentation currently uses cached runtime Vault providers. Add direct cached LuckPerms presentation support without storage loads and retain Vault fallback. PlexonRanks remains an optional presentation authority and is never mutated by PlexonChats.
7. Config publication is atomic at the YAML snapshot level, but runtime reload needs rollback/diagnostic state if a post-parse subsystem refresh fails.
8. Existing diagnostics expose component state but not chat counters, event ownership, config generation/reload result, format failures, optional rank integrations, or recent bridge failures.

## Implementation contract

### Chat ownership/performance

Use exactly one native chat observation path. Route native chat on Paper's synchronous `ChatEvent` so Bukkit/world/permission/custom-event access remains main-thread safe while eliminating the explicit scheduler task previously created for every message. Preserve moderated message content and the event's existing audience restrictions before cancelling native delivery and passing it once to `ChatManager`.

The public `/g` and `/l` command paths continue to call the same `ChatManager` and the same `PlexonChatEvent`. No Core duplicate route is introduced.

### Formatting safety

Keep `ComponentTemplate` component placeholders as the only interpolation mechanism for dynamic player/message values. User text remains untrusted and may only receive the explicitly permitted formatting policy in `PlaceholderHandler`. Server-authored format templates may use MiniMessage. Never reparse a rendered player component.

### Rank/prefix presentation

Prefer an already-cached LuckPerms `User` from `UserManager#getUser(UUID)`; never invoke `loadUser`, storage, database, or network work from chat. Resolve primary group/prefix from cached metadata once per presentation call, with Vault fallback when LuckPerms is absent/unloaded. Empty prefixes render as empty components and no prefix is duplicated. PlexonRanks/LuckPerms provider states are diagnostic only; PlexonChats does not mutate either authority.

### Reload/config validation

Expand pre-publication validation for:

- channel formats and required `{message}` token;
- MiniMessage templates used by chat components and connection messages;
- GUI rows, materials, actions, slots and duplicate slots;
- auto-message intervals/delays, min-online, delivery names and non-empty message lines;
- known click actions and web URL requirements where configured;
- sound names where the runtime registry can resolve them.

On reload, snapshot the current known-good config first. If a post-publication runtime refresh fails, restore that snapshot, rebuild runtime services from it and record a failed reload rather than leaving a partially refreshed runtime.

### Auto-messages

Retain one shared coordinator. Preserve pause/resume/list/preview/send and add explicit persisted enable/disable administration. Preview remains private and non-advancing. Test-send is explicit. Default routine content uses CHAT/TITLE; legacy ACTION_BAR remains parser-compatible only so upgrades are not destructive.

### Diagnostics

Add a low-allocation diagnostics service with counters/state for:

- native chat observed;
- public messages delivered and recipient deliveries;
- custom-event cancellations;
- format failures;
- current ownership model;
- config revision;
- last reload result;
- recent integration failure summary;
- auto-message state/group count;
- DiscordSRV, PlaceholderAPI, Vault, LuckPerms and PlexonRanks availability.

Diagnostics must not perform provider storage/network calls.

### Tests/distribution

Update and expand MockBukkit tests for synchronous native routing/no scheduler handoff, reload rollback state, strict configuration rejection, diagnostics counters, rank provider fallback boundaries, preview non-broadcast behavior, repeated reload scheduler uniqueness and release metadata.

CI must verify Java 25 class major 69 and ensure these runtime dependencies are not shaded: PlexonCore, Paper/Bukkit/Adventure, DiscordSRV, LuckPerms and PlaceholderAPI. The RC artifact set is:

- `PlexonChats-3.2.0-rc.1.jar`
- `SHA256SUMS.txt`
- `TEST_SUMMARY.txt`
- `PROVENANCE.txt`

The PR stays OPEN / DRAFT / UNMERGED. Stable promotion is blocked until PlexonCraft runtime certification completes.