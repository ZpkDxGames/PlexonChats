# PlexonChats

PlexonChats 3.2.0 is the stable Plexon communication plugin for Paper 26.2 / Java 25. It owns local/global public chat, private messages, safe MiniMessage presentation, GUI/preferences, scheduled messages, diagnostics, and optional DiscordSRV bridging.

## 3.2.0 stable boundary

Stable 3.2.0 promotes the accepted 3.2.0-rc.1 / Phase 3 source line without adding speculative Essentials parity or a parallel chat architecture.

- **Single chat authority:** Paper chat → PlexonChats → synchronous cancellable `PlexonChatEvent` → one delivery path.
- **No scheduler handoff per chat message:** native public chat is handled through Paper's main-thread `ChatEvent`.
- **Safe MiniMessage boundary:** server-authored templates may use MiniMessage; dynamic player/provider values are inserted as components rather than reparsed as trusted markup.
- **Transactional configuration:** candidates are parsed and validated before publication; failed subsystem refresh restores the previous known-good generation.
- **One auto-message scheduler:** every configured group shares one bounded coordinator; reload/disable replaces rather than stacks tasks.
- **DiscordSRV ownership:** only GLOBAL Minecraft chat is exported, Discord-origin traffic is not echoed, and LOCAL/PM remain private.
- **Stable public integration:** `PlexonChatsAPI` and synchronous cancellable `PlexonChatEvent` remain the supported cross-plugin surface.

The Phase 3 consolidation audit intentionally leaves `/ignore` deferred until production evidence establishes a real dependency. AFK state remains PlexonUtility-owned and is not duplicated in Chats.

## Requirements

- Paper **26.2**
- Java **25**
- Optional PlexonCore **2.x**; supported Core API range remains `>=1.0 <3.0`
- Optional LuckPerms for cached prefix/group presentation
- Optional Vault
- Optional PlaceholderAPI
- Optional PlexonRanks presentation context
- Optional DiscordSRV 1.30.5

Paper/Bukkit/Adventure, PlexonCore, LuckPerms, PlaceholderAPI and DiscordSRV runtime/API classes are provided externally and are not bundled into the release JAR.

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

Aliases `/tell`, `/w`, and `/r` remain first-party PlexonChats behavior.

## Chat and integration semantics

PlexonChats observes Paper public chat at `HIGHEST` with `ignoreCancelled = true`, preserves lower-priority cancellation, captures message/viewer restrictions, then performs its authoritative routing. `PlexonChatEvent` fires synchronously before public delivery and Discord export; cancellation stops both. PMs do not fire it.

LuckPerms presentation uses already-cached user metadata only. PlexonChats does not perform LuckPerms storage loads on the chat path and does not mutate PlexonRanks/LuckPerms authority.

Optional plugin hooks are lifecycle-aware. Missing or failed optional integrations degrade their own feature surface without creating a second chat route.

## Scheduled messages

One shared coordinator services all groups. It uses monotonic timing, no catch-up bursts, and one Bukkit repeating task. Preview does not broadcast or advance rotation; test-send is explicit; send-now resets that group's next deadline. Reload and disable cancel the previous coordinator.

## Configuration and reload

Configuration schema v4 validates chat formats, MiniMessage, click actions/URLs, GUI rows/materials/actions/slots, auto-message timing/content/delivery, sounds, and connection-message modes before a candidate becomes active.

Conceptual lifecycle:

```text
parse → migrate → validate → publish candidate → refresh runtime
```

If validation fails, the live generation is untouched. If a later refresh fails, PlexonChats restores the previous snapshot and rebuilds that known-good generation; a failed rollback disables safely rather than continuing partially.

## Build and release

With Maven 3.9+ and JDK 25, CI provisions the pinned PlexonCore 2.0.4 API artifact and runs:

```sh
mvn --batch-mode --no-transfer-progress clean verify
```

The stable artifact is `PlexonChats-3.2.0.jar`. CI/release verification requires a non-empty all-green test suite, Java class major 69, Paper 26.2 plugin metadata, required API/event/diagnostic classes, dependency isolation, checksum integrity and accepted source ancestry.

Stable releases are rebuilt from the exact final `main` source through the generic `release/stable` workflow and include the JAR, `SHA256SUMS.txt`, `TEST_SUMMARY.txt`, and `PROVENANCE.txt`.

## Upgrade from 3.1.1 / RC

1. Stop Paper.
2. Back up the current JAR and complete `plugins/PlexonChats/` directory.
3. Replace the old JAR with `PlexonChats-3.2.0.jar`.
4. Keep `config.yml` and `players.yml`; schema migration is idempotent and backs up an older config before mutation.
5. Start Paper and inspect `/chat diagnostics`.

Rollback baseline remains `v3.1.1` / `c7191c21654b11ea55c03e5fecae629e20898319`.

Live PlexonCraft rollout/smoke testing is a separate operational follow-up. It does not block the GitHub source/release closure when CI and artifact contracts are independently satisfied.

Created by **Tonim / ZpkDxGames**.
