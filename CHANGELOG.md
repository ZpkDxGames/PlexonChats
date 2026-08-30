# Changelog

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
