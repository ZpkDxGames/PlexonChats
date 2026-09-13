# Changelog

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
