# Changelog

## 3.6.1 — compact interactive Chat Event feedback

### Changed

- Reworked stock Chat Event cards to remove repeated `CHAT EVENT`, event-name, `Reward:` and `Time:` labels and give the actual challenge higher visual priority.
- Added compact semantic state symbols: `✦` active, `✔` complete, `⌛` expired, `✕` cancelled, `◆` reward, `⏱` timer and `♛` winner.
- Reward/timer metadata now carries Adventure hover information instead of additional explanatory lines.
- Winner total/per-type statistics moved into hover text on the winner component, reducing visible card noise while retaining the persistent statistics data.
- Successful standard events now show the canonical answer directly in the completion card.
- Stock TYPE, UNSCRAMBLE, MATH and REVERSE prompts were shortened to avoid repeating the event type in the challenge line.
- Bingo start, draw and invalid-claim messages were tightened while preserving the accepted shared authoritative board, automatic marks and first-valid-claim mechanics.

### Migration / compatibility

- Configuration schema advances from v8 to **v9**.
- The v8 → v9 migrator updates only exact known stock presentation strings; administrator-customized Chat Event cards and prompts remain untouched.
- Existing multi-generation v6/v7/v8 migration behavior remains intact, including preservation of administrator-owned event definitions and Discord webhook secrets.
- Upgrades continue to write a pre-migration backup, now named `config-before-v9-<timestamp>.yml`.
- Event authority, timing, answers, rewards, statistics, Discord one-message lifecycle, normal chat routing and Bingo gameplay are unchanged.
- Build/release provenance is anchored to stable `v3.6.0` / `e3bc1f04483ea235b947bbed2c5e2c99e6df8261`.
- Rollback: `v3.6.0` / `e3bc1f04483ea235b947bbed2c5e2c99e6df8261`.

## 3.6.0 — Discord Chat Events embed synchronization

### Added

- Generalized Discord embed presentation for every current Chat Event type: TYPE, UNSCRAMBLE, MATH, TRIVIA, REVERSE and BINGO.
- One-message event lifecycle with retained Discord message references: start creates, live state edits, and terminal state edits the same message.
- DiscordSRV 1.30.5/JDA event transport plus generalized asynchronous webhook create/edit transport.
- `AUTO` transport selection that prefers DiscordSRV and falls back to a configured webhook.
- Live Bingo Discord renderer using the exact authoritative `BingoRun` board, draw history, last call, draw count and winning pattern.
- Bounded/coalesced Discord publishing with stale-update suppression, per-run ordering, one safe deleted-message recreation and controlled shutdown.
- `/chat events discord status` and `/chat events discord test` plus `plexonchats.admin.events.discord`.
- Chat Events GUI/diagnostic visibility for safe event-sync state without exposing webhook credentials.
- Regression tests for transport fallback, message reuse/recovery, asynchronous ordering, all implemented event renderers, Bingo authoritative-board rendering and admin secret safety.

### Changed

- Configuration schema advances from v7 to **v8** and introduces the generalized `chat-events.discord` section.
- `participation-mode: DISPLAY_ONLY` is enforced for 3.6.0 so Discord remains presentation-only and Minecraft stays authoritative.
- Bingo Discord updates now edit one persistent embed rather than sending a new raw/ANSI webhook message for every lifecycle change.
- The old Bingo-only `BingoDiscordWebhook` runtime implementation is removed; webhook support now uses the reusable event transport.
- Build/release provenance now anchors to stable `v3.5.0` / `4353eb80b8b1e3efaac4e5ab5f82ff6e6c3f1210` and records live runtime certification as `FOLLOW_UP_REQUIRED` until real PlexonCraft evidence exists.

### Migration / compatibility

- The v7 → v8 migrator maps existing Bingo webhook enabled state, URL, username and draw/winner delivery intent into the generalized event-sync schema where possible.
- Valid webhook secrets are preserved exactly during migration and removed from the obsolete duplicate location.
- Administrator-owned event definitions, reward profiles, timers, enabled states and unrelated configuration remain intact.
- The earlier v6 → v7 shared-board Bingo migration remains supported for multi-generation upgrades.
- Accepted 3.5.0 Bingo mechanics, exact-once rewards/statistics, normal chat routing, PM/reply, scheduled messages, DiscordSRV player-chat synchronization, `PlexonChatsAPI` and synchronous cancellable `PlexonChatEvent` remain unchanged.
- Rollback: `v3.5.0` / `4353eb80b8b1e3efaac4e5ab5f82ff6e6c3f1210`.

## 3.5.0 — shared real-life Bingo mechanics correction

### Changed

- Replaced 3.4.0 participant-specific Bingo cards with one authoritative shared live board per run.
- Removed the JOINING phase, explicit participant opt-in, per-player card state, click/manual marking, reconnect-card restoration and participant-only repeated draw traffic.
- Bingo now renders a 6×6 chat display: `# | B | I | N | G | O` plus five playable rows.
- Cards contain 25 unique traditional 75-ball values: B 1–15, I 16–30, N 31–45, G 46–60 and O 61–75.
- Removed FREE-center behavior; the center is a normal N-column number.
- Draws now come automatically from one shuffled server-owned `1..75` pool and each value can be called at most once.
- Card marks are derived automatically from authoritative draw history. Called card values render as bold green bracketed cells.
- Victory is now a first-valid-claim mechanic via `/bingo claim` or exact native public-chat `bingo`.
- Public-chat claims remain behind the synchronous cancellable `PlexonChatEvent` boundary; PM/Discord/console/broadcast/synthetic sources cannot claim.
- Horizontal, vertical and diagonal patterns remain supported; Full House is an optional additional pattern. The obsolete Four Corners gameplay is removed.
- Bingo administration now includes `/bingo`, `/bingo claim`, `/bingo start`, `/bingo stop` and `/bingo status`, delegating to the existing Chat Events manager.
- The Chat Events Bingo GUI now exposes run state/ID, draw count, last call, remaining pool, next-draw ETA, patterns, shared-board preview, reward profile and Start/Stop controls rather than participant/card controls.

### Added

- `BingoRun`, `BingoPatternEvaluator` and explicit shared-board model components that are testable without Paper.
- `BingoAnsiRenderer` and optional Bingo-specific Discord webhook synchronization with start/draw/win toggles.
- Bounded asynchronous webhook transport with short timeouts and failure isolation; webhook URL is never exposed through normal logs, commands, GUI or diagnostics.
- Dedicated `/bingo` command surface and `plexonchats.events.bingo.play` player permission.
- Regression tests for traditional board generation, no FREE center, unique draws, all line patterns, Full House, invalid claims, simultaneous valid claims, ANSI output and shared audience behavior.

### Exact-once / reliability

- Bingo winner acceptance uses an atomic `ACTIVE → WON` transition.
- One completion guard owns reward execution, persistent statistics and winner publication, preventing near-simultaneous claims from producing duplicate winners.
- Timeout, cancellation, shutdown and draw-pool exhaustion do not issue rewards or win statistics.
- Bingo uses the existing Chat Event reward profiles and `chat-events.db`; no duplicate reward engine/database is introduced.
- No per-player repeating schedulers or per-player board state exist in the 3.5.0 model.

### Migration / compatibility

- Configuration schema advances from v6 to **v7** with `config-before-v7-<timestamp>.yml` backup.
- Administrator-owned `chat-events.events` remains protected during migration.
- Existing explicit BINGO definitions preserve identity/name/weight/cooldown/reward/minimum-online values while obsolete join/FREE/player-card/manual-mark settings are removed or ignored.
- A non-Bingo definition whose ID is `bingo` remains its explicit type and is never silently reinterpreted.
- Existing LOCAL/GLOBAL routing, PM/reply, item display, mentions, auto-messages, DiscordSRV normal-chat isolation, `PlexonChatsAPI`, synchronous cancellable `PlexonChatEvent`, persistent statistics and reward integrations remain unchanged.
- Target remains Paper 26.2 / Java 25 / PlexonCore 2.0.4.
- Rollback: `v3.4.0` / `26cdf6fcff09d0443bbea64c5ec5ae3c4c1cd3a9`.

## 3.4.0 — enhanced Chat Events, persistent statistics and interactive Bingo

- Added configurable multi-line Chat Event cards and persistent `chat-events.db` winner statistics.
- Added the first real BINGO event implementation, its administration GUI and schema v6 migration.
- Added SQLite JDBC bundling and exact-run persistence protection.
- Historical 3.4.0 Bingo used participant-specific cards, explicit joining, optional FREE center and clickable server-validated marks; 3.5.0 intentionally supersedes those gameplay mechanics.

## 3.3.0 — configurable Chat Events

- Added the configurable Chat Events coordinator, TYPE/UNSCRAMBLE/MATH/TRIVIA/REVERSE generators, answer normalization, reward profiles and event administration.
- Established the exact-once winner/reward boundary and native-public-chat answer-source restrictions.

## 3.2.0 — stable repository closure

- Promoted the accepted Phase 3 source lineage to stable 3.2.0, preserving synchronous cancellable `PlexonChatEvent`, public API compatibility, PM/reply, chat ownership and stable release provenance checks.

## 3.1.0

- Added PlexonCore lifecycle integration, public API services, diagnostics and Java 25 / Paper 26.2 build/release normalization.

## 3.0

- Added configurable chat UI, preferences, auto-messages, optional DiscordSRV integration, connection messages, synchronous cancellable `PlexonChatEvent`, transactional configuration migration and regression coverage based on the accepted 2.0-Update lineage.
