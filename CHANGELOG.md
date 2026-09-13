# Changelog

## 4.0.1 — aligned Bingo table renderer and configurable call pacing

### Added

- `TABLE` Bingo renderer with deterministic fixed-width Unicode table geometry under `minecraft:uniform`.
- `COMPACT` renderer mode preserving the 4.0.0 borderless presentation.
- Explicit validation for Bingo renderer style/font/dimensions and bounded call timing.
- Direct regression coverage for row widths, separator indices, mark/winner geometry stability, 25 run-bound mark clicks, run-bound claim action, compact fallback, fresh 5-second defaults, administrator interval preservation, and schema-1 renderer compatibility.

### Changed

- Fresh 4.0.1 `data.yml` now uses `draws.interval-seconds: 5` while retaining `first-call-delay-seconds: 5`.
- Fresh renderer defaults are `style: TABLE`, `left-padding: 2`, `cell-width: 4`, `column-gap: 0`, and `show-border: true`.
- Marked cells remain fixed width and change color only; winning cells use color/underline rather than width-changing bold or marker characters.
- The entire grid explicitly uses the configured fixed-width font, including borders, separators, headers and cells.
- Build/release provenance now anchors to stable `v4.0.0` (`657d27766bce60235bc50602b4dd4d14b9e6998c`).

### Migration / compatibility

- `data.yml` remains schema 1. Existing administrator-owned timing and renderer values are not silently rewritten.
- Existing 4.0.0 renderer blocks without `render.style` retain compatibility using their existing `show-border` behavior.
- `data.yml` remains the only Bingo draw-interval authority; no competing 4.0.1 interval is introduced in `config.yml`.
- `render.on-every-draw` remains false by default to prevent full-card chat flooding at the five-second cadence.
- Live visual/runtime certification remains `FOLLOW_UP_REQUIRED` until real Paper 26.2 client evidence confirms pixel alignment and call readability.

## 4.0.0 — joinable manual-mark Bingo and minigame data layer

### Added

- Real Bingo `LOBBY` state with explicit clickable `/bingo join`, idempotent joins, lobby leave/active forfeit handling, and stock 60/30/15/5 second reminders.
- Stable participant-owned traditional 75-ball cards. Each participant owns their card and manual marked-cell set for the run.
- Run-bound clickable cell marking. Old card links cannot mutate newer runs.
- Action-bar rejection for uncalled/invalid mark attempts with no state mutation.
- Fixed-width `minecraft:uniform` card rendering with configurable left padding/cell width/gap and green marked numbers without brackets.
- Admin `/bingo start now` one-participant test path without reducing scheduled production minimums.
- `plugins/PlexonChats/data.yml` schema 1 for scheduler, randomizer, minigame pools/ranges/weights/cooldowns/durations/eligibility, and Bingo gameplay rules.
- v3.6.2 -> v4 first-start gameplay migration with `config-before-v4-<timestamp>.yml` backup and administrator-value preservation.
- Direct regression coverage for manual-mark authority, stale clicks, non-participant/not-on-card rejection, scheduled production minimum, reload cancellation, data migration, renderer alignment, concurrency, and Discord metadata-only Bingo.

### Changed

- Removed the v3.6.2 shared automatic-mark Bingo authority. A draw now changes global call history only; it never changes a participant's marks.
- Bingo claims now evaluate the claimant's manual marked-cell indices only. Global draw history alone cannot produce a winner.
- Discord Bingo no longer exposes a shared/fake card. It remains display-only and publishes lobby/live/terminal metadata through the existing one-message lifecycle.
- Gameplay definitions move from legacy `config.yml` Chat Events definitions into `data.yml`; presentation, reward-profile bodies, master enabled state, and credentials remain in `config.yml`.
- Standard TYPE, UNSCRAMBLE, MATH, TRIVIA, and REVERSE gameplay/rewards/statistics/Discord synchronization remain compatible.
- Build/release distribution verification now requires `data.yml`, `ChatEventDataStore`, and participant-owned Bingo classes in the shipped JAR.

### Migration / compatibility

- `config.yml` remains schema v10; `data.yml` starts at schema 1.
- Existing `data.yml` values are never replaced by stale legacy gameplay settings.
- Discord/webhook secrets are not copied into `data.yml`.
- Malformed individual minigames are quarantined where safe rather than taking down unrelated chat features.
- Target: Paper 26.2, Java 25 / class major 69, PlexonCore 2.0.4 API target.
- Authoritative baseline and rollback: `v3.6.2` / `2099d8b50d267b9a430bc1ef34a18415b020318c`.
- Live runtime certification remains `FOLLOW_UP_REQUIRED` until real PlexonCraft client/server evidence is collected.

## 3.6.2 — Chat Events recovery and content expansion

- Advanced configuration to schema v10 and repaired the historical case where upgraded servers could reach an empty `chat-events.events` collection.
- Restored the stock event library only when that collection was missing/empty; non-empty administrator-owned definitions remained untouched.
- Expanded stock Type Rush, Unscramble, Reverse, Trivia, and Math content/ranges while preserving customized pools/ranges.
- Refreshed compact Chat Event presentation and preserved the v3.6.1 shared-board Bingo model as the pre-4.0 behavior.

## 3.6.1 — compact interactive Chat Event feedback

### Changed

- Reworked stock Chat Event cards to reduce repeated labels and give the challenge higher visual priority.
- Added compact semantic state symbols and hover-based reward/timer/winner-stat details.
- Successful standard events show the canonical answer directly in the completion card.
- Configuration schema advanced from v8 to v9 while preserving administrator-customized presentation values.

## 3.6.0 — Discord Chat Events embed synchronization

### Added

- Generalized Discord embed presentation for TYPE, UNSCRAMBLE, MATH, TRIVIA, REVERSE, and BINGO.
- One-message event lifecycle with retained Discord message references.
- DiscordSRV/JDA transport plus asynchronous webhook create/edit transport and AUTO fallback.
- Bounded/coalesced publication, stale-update suppression, deleted-message recovery, and safe administration/status surfaces.

### Changed

- Configuration schema advanced from v7 to v8 and introduced `chat-events.discord`.
- `participation-mode: DISPLAY_ONLY` kept Minecraft authoritative.
- Bingo Discord updates edited one persistent message instead of posting one raw message per call.

## 3.5.0 — shared real-life Bingo mechanics correction

- Replaced 3.4.0 participant cards with one shared board and automatic draw-derived marks.
- Removed joining/manual marks/FREE center from that release line.
- Added first-valid-claim victory and generalized Bingo administration.
- This shared-board architecture is intentionally superseded by 4.0.0.

## 3.4.0 — enhanced Chat Events, persistent statistics and interactive Bingo

- Added configurable multi-line Chat Event cards and persistent `chat-events.db` winner statistics.
- Added the first BINGO implementation and administration GUI.
- Historical 3.4.0 Bingo used participant-specific cards; 3.5.0 replaced it and 4.0.0 later introduced the current redesigned participant-owned model.

## 3.3.0 — configurable Chat Events

- Added the configurable Chat Events coordinator, TYPE/UNSCRAMBLE/MATH/TRIVIA/REVERSE generators, answer normalization, reward profiles and event administration.
- Established the exact-once winner/reward boundary and native-public-chat answer-source restrictions.

## 3.2.0 — stable repository closure

- Promoted the accepted Phase 3 source lineage to stable 3.2.0, preserving synchronous cancellable `PlexonChatEvent`, public API compatibility, PM/reply, chat ownership and stable release provenance checks.

## 3.1.0

- Added PlexonCore lifecycle integration, public API services, diagnostics and Java 25 / Paper 26.2 build/release normalization.

## 3.0

- Added configurable chat UI, preferences, auto-messages, optional DiscordSRV integration, connection messages, synchronous cancellable `PlexonChatEvent`, transactional configuration migration and regression coverage based on the accepted 2.0-Update lineage.
