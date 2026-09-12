# Changelog

## 3.3.0 — configurable Chat Events

### Added

- Fully configurable Chat Events subsystem with independent persistent master and automatic-scheduler toggles.
- One bounded coordinator, one active competition at a time, monotonic deadlines, weighted selection, per-event cooldowns, minimum-online/audience filters, immediate-repeat avoidance, timeout and cancellation lifecycle.
- `TYPE`, `UNSCRAMBLE`, `MATH`, `TRIVIA`, and Unicode-safe `REVERSE` generators.
- Deterministic answer normalization with case, trim, whitespace, Unicode-normalization, and optional diacritic controls.
- Exact-once winner transition and exact-once reward attempt, including near-simultaneous answer regression coverage.
- Reusable reward profiles combining Vault economy, optional PlexonKeys service grants, and controlled console commands.
- `/chat events status|list|enable|disable|pause|resume|start|stop|preview` with permission-aware completion.
- `plexonchats.events` and `plexonchats.events.manage`, included under `plexonchats.admin`.
- Compact Chat Events administration GUI using the existing custom-holder/click-routing protections.
- Chat Events status/reward-integration/failure fields in `/chat diagnostics` and concise state in `/chat status`.
- Configuration schema v5 migration with Chat Events defaults and `config-before-v5-*` backup.

### Reliability / security

- Only genuine accepted native Minecraft public chat may win; command shortcuts, PMs, Discord-origin messages, broadcasts, auto-messages, console and synthetic sends cannot count.
- `PlexonChatEvent` cancellation remains authoritative before an answer is accepted. Correct active-event answers are checked before stale duplicate-history throttling; wrong answers remain subject to normal moderation.
- Reward integrations execute only after an exact winner exists and only on the primary thread. Partial reward failure never reopens a competition or chooses a second winner.
- PlexonKeys is consumed through its existing Bukkit service boundary without vendoring/bundling another plugin JAR.
- Player answers are data only: they are never parsed as trusted MiniMessage or injected into console rewards.
- Timeout/cancel/reload/master-disable paths grant nothing; timeout still announces even when answer reveal is disabled.
- Invalid Chat Events configuration rejects the entire reload candidate while the previous live runtime remains active.
- Existing LOCAL/GLOBAL chat, PM/reply, item display, mentions, player preferences, GUI, auto-messages, DiscordSRV isolation, public API, synchronous `PlexonChatEvent`, and transactional reload behavior remain covered by the full suite.

### Compatibility

- Target remains Paper 26.2 / Java 25 / PlexonCore 2.0.4 compile boundary.
- Vault and PlexonKeys are optional runtime integrations; their absence does not disable normal chat or Chat Events without those reward components.
- Existing `PlexonChatsAPI` binary surface is not broken by Chat Events.

## 3.2.0 — stable repository closure

- Promote the accepted `3.2.0-rc.1` / Phase 3 source lineage to stable `3.2.0` without introducing a parallel chat route or speculative Essentials parity.
- Preserve synchronous cancellable `PlexonChatEvent`, public `PlexonChatsAPI`, first-party `/msg` + `/reply`, GLOBAL/LOCAL ownership, DiscordSRV no-echo behavior, and PlexonCore diagnostic integration.
- Preserve the no-per-message-scheduler chat path, cached-only LuckPerms presentation, safe MiniMessage component boundaries, transactional runtime reload rollback, and one shared auto-message scheduler.
- Keep `/ignore` deferred for lack of production dependency evidence and keep AFK ownership outside PlexonChats.
- Generalize Build CI around the Maven project version instead of hard-coded RC artifact names.
- Replace RC-tag publication with one exact-`main` stable release workflow.
- Require a non-empty all-green test suite, Java 25/class major 69, Paper 26.2 metadata, required API/event/diagnostic classes, dependency isolation, checksums and provenance before stable publication.
- Keep live PlexonCraft rollout as a separate non-blocking operational follow-up.

## 3.1.0

Based on the verified production `3.0-Release` commit `4bff64e1691e79f0199baace428ec4349be9400b`. The stale historical `main` branch was not used as the migration baseline.

### Added

- PlexonCore 1.0.0 lifecycle bridge with module ID `chats`, supported Core API range `>=1.0 <2.0`, and STARTING/READY/DEGRADED/FAILED publication.
- Safe standalone Core bridge/factory that avoids Core API linkage when PlexonCore is absent, disabled, unavailable, or incompatible.
- Bukkit `PlexonChatsAPI` service with immutable player-preference snapshots and controlled channel/public-chat/private-message operations.
- `/chat diagnostics` for plugin/platform/Core/API/preferences/scheduler/item-preview/GUI/PAPI/Vault/DiscordSRV state without exposing chat content.
- Core lifecycle regression tests covering compatibility, fallback, duplicate registration ownership, health transitions, and clean unregister.
- Tag-driven release workflow design with pinned PlexonCore provisioning, distribution verification, and `SHA256SUMS.txt`.
- API, PlexonCore, migration, and 3.1.0 release documentation.

### Changed

- Version is now 3.1.0.
- Build/runtime target is Paper 26.2 / Java 25.
- `plugin.yml` API metadata is aligned to Paper 26.2.
- PlexonCore 1.0.0 is a `provided` Maven dependency and a runtime soft dependency.
- GitHub CI/release handling is normalized for maintained `main`/release branches and tag-driven production publication.

### Preserved

- `com.antondev.chats` package namespace.
- Existing commands, aliases, and permission nodes.
- Configuration schema 3 and existing live `config.yml` compatibility.
- `players.yml` format and ordered asynchronous persistence architecture.
- LOCAL/GLOBAL semantics, configured local radius, shortcut prefixes, validation/cooldown/duplicate protection.
- One authoritative public-chat route and one `PlexonChatEvent` per logical public message.
- Existing synchronous, cancellable `PlexonChatEvent` timing/accessors/recipient mutability and PM exclusion.
- Paper lower-priority moderation cancellation, message edits, and viewer restrictions.
- Private-message and `/reply` behavior.
- One auto-message scheduler with current group content/rotation/audience behavior.
- GUI behavior and inventory protections.
- Exact item-preview snapshots and single cleanup task.
- MiniMessage/PlaceholderAPI/display-name/click-action security policies.
- Vault/player-info behavior.
- DiscordSRV as the only Minecraft→Discord chat path; global at most once, local/PM never.
- Invalid reload behavior: rejected candidate configuration does not replace the working live configuration.

### Compatibility

- Target: Paper 26.2, Java 25.
- PlexonCore 1.0.0 is optional at runtime; compatible Core produces CORE mode, otherwise PlexonChats remains standalone.
- DiscordSRV remains optional and is not bundled.
- No configuration or player-data reset is required from 3.0.

## 3.0

Based on `2.0-Update` (`cefcdbe0679f75a1000d5401fb6a94fc86378e03`), not `main`.

### Added

- Configurable nickname hover/click/name, badge/separator interactions, and private-message formats.
- Configurable main/admin/creator menus with permission-aware actions, 1–6 rows, custom materials/lore, and private format preview.
- Persistent channel, mention, tip, and private-message preferences.
- Interval-based auto-message groups with sequential/shuffled rotation, chat/action-bar/title delivery, audience filters, and manual preview/send/pause/resume controls.
- Optional DiscordSRV 1.30.5 adapter for global chat, inbound filtered components, status reporting, and explicitly enabled announcements.
- Join/quit/first-join customization with DEFAULT/CUSTOM/HIDDEN modes, online-count placeholders, and silent/hidden-message protection.
- Synchronous cancellable `PlexonChatEvent` for moderation/integration of all public chat entry points.
- Versioned config migration with backup, atomic preference/config writes, and invalid-reload protection.
- Regression tests including the committed 2.0 config fixture and connection-message behavior; Java 25 Maven CI and release publishing with checksums.

### Fixed

- Public channel and item format settings were ignored by hardcoded rendering.
- Permission, enabled-channel, and routing checks now apply consistently to ordinary chat, shortcuts, `/g`, and `/l`.
- Native moderated message/viewer data is respected before delivery.
- PM mentions no longer alert unrelated players; public mention alerts are limited to actual recipients.
- GUI inventory transfer and stale-view actions are blocked; actions recheck authorization.
- Built-in/PAPI/rank values cannot inject new MiniMessage tags or recursive placeholders.
- Existing display-name interactions cannot override the configured nickname hover/click behavior.
- Item preview storage has a configurable bound/expiry and uses cloned snapshots.
- Reloads replace old scheduling/hooks without stacking timers or Discord listeners.

### Changed

- Release version is 3.0; configuration schema is version 3.
- Player message formatting is permission-gated, with advanced tags disabled by default.
- Added configurable message length, cooldown, and duplicate protection.
- Vault and PAPI hooks refresh on reload/plugin enable/disable.
- Generated Maven output is ignored instead of committed.
- Supersedes the withdrawn 1.1.0 proposal; 3.0 has the correct 2.0-Update commit ancestry.

### Compatibility

- Build/test target: Paper 1.21.11, Java 25, DiscordSRV 1.30.5.
- DiscordSRV is optional and disabled in the default config. Local chat and PMs are never bridged.
- See [UPGRADING.md](docs/UPGRADING.md) for appearance changes caused by previously ignored formats taking effect.
