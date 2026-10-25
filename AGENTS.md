# AGENTS.md — PlexonChats / TonimNetwork Network Chat Upgrade

**Repository:** `ZpkDxGames/PlexonChats`  
**Document role:** Root-level engineering contract for an agent working on the PlexonChats source tree.  
**Primary companion document:** `PLEXONCHATS_VELOCITY_NETWORK_ANALYSIS_DIRECTIVE_2026-10-25.md`  
**Prepared:** 2026-10-25

## 0. Reading order, authority, and execution boundary

1. Read this `AGENTS.md` in full.
2. Read the companion architecture/audit directive in full.
3. Inspect `README.md`, `pom.xml`, `CHANGELOG.md`, `docs/API.md`, `docs/COMMANDS.md`, `docs/CONFIGURATION.md`, `docs/CHAT-EVENTS.md`, `docs/BINGO.md`, `docs/UPGRADING.md`, `.github/workflows/`, and relevant source/tests.
4. Verify actual remote `main` and local branch HEAD at execution time. The previously observed baseline was `f98b1a233ca3fc68ac9d94a45dfc414d02634300`, PlexonChats 4.0.1; **this is a reference, not permission to overwrite more recent work**.
5. Record a source-grounded audit and a brief implementation decision before changing code. Then implement the authorized task on a new feature branch.

**Scope reconciliation:** The companion analysis directive describes Phase A as analysis-only. The accompanying **GPT 6.1 Max execution prompt expressly authorizes source implementation, local builds, tests, packaging, and a review-ready PR**. That later explicit authorization supersedes the *analysis-only* phase restriction for the **repository development environment only**; all prohibitions on unapproved production access, service restarts, secrets, deployments, releases, and merges remain in force. If no execution prompt is supplied, this document alone does not authorize repository or infrastructure mutations.

Do not push directly to `main`, force-push, merge, tag/release, change Vercel, change Oracle VPS files, restart Velocity/Paper, edit whitelists, forwarding mode or secrets, touch PlexonPanel credentials, or deploy artifacts unless the owner separately authorizes the precise operation. A feature-branch push and PR are allowed *only* if the execution prompt authorizes them and the repository GitHub integration is available.

## 1. Product contract — three distinct channels

PlexonChats must remain the **single chat authority on each Paper backend**. Preserve the existing trusted moderation and formatting path while adding one network-wide channel.

| ID | Coverage | Distance | Network delivery |
|---|---|---|---|
| `LOCAL` | Same backend **and** same world | Configurable; default **100 blocks**, inclusive | **Never** |
| `GLOBAL` | All eligible players, all worlds, on the **sender's backend only** | Unlimited | **Never** |
| `UNIVERSAL` | Eligible players on **Lobby + SMP + Towny**, including origin backend | Unlimited | **Yes**, via trusted Velocity companion |

Mandatory semantics:

- Exactly **one** visible UNIVERSAL message per eligible recipient; show server of origin (`Lobby`, `SMP`, `Towny`) and retain safe Adventure formatting.
- Preserve synchronous, cancellable `PlexonChatEvent` moderation for origin native/command public sends before any local or network delivery. Respect cancellation, modified message, permitted viewer set, permission checks, cooldown/duplicate filtering, and channel-specific receiving permissions.
- LOCAL uses same-world `distanceSquared <= radius * radius`; GLOBAL sends to the current backend without radius checks; neither reaches other backends or Discord except under unchanged explicit existing local configuration rules (LOCAL must not bridge to Discord).
- Received UNIVERSAL packets are **presentation-only**; they must not be injected back into native chat, executed as commands, re-forwarded, awarded points, counted as Chat Event answers, or cause duplicated mentions/Discord posts.
- Offline, incompatible or missing Velocity companion must not break LOCAL/GLOBAL. UNIVERSAL must surface a truthful unavailable/degraded result; do not silently relabel a local-only echo as network delivery.
- Do not globally coordinate Bingo/Chat Events, private messages, economy, player data, ranks, inventory or preferences in this task.

## 2. Target topology and identity boundaries

- Velocity **4.3.0**, Java **25**, public proxy `tonimnetwork-velocity.service`, expected TCP `25565`.
- Paper **26.3**, Java **25** on three isolated backends: `minecraft@tonimlobby.service` / expected proxy ID `lobby` / 25568; `minecraft@tonimsmp.service` / expected `smp` / 25566; `minecraft@tonimtowny.service` / expected `towny` / 25567.
- Isolated runtime users: `tn-velocity`, `mc-tonimlobby`, `mc-tonimsmp`, `mc-tonimtowny`.
- Vel directory: `/srv/plexonpanel/network/velocity`; Paper directories: `/srv/plexonpanel/servers/{tonimlobby,tonimsmp,tonimtowny}/server`.
- **Do not assume** `smp` and `towny` routing is correct: the owner's prior `/server towny` test appeared to land in SMP. Confirm actual mapping with an independent diagnostic, and do not attribute that unrelated issue to PlexonChats. Do not change production routing as part of the chat project.
- The network may accept offline/cracked accounts and uses whitelists. **Offline UUIDs or player names are not cryptographic proof of identity**. For the chat bridge, use connection provenance (backend connection to Velocity, and proxy-origin packets to Paper), validate claimed player/backend association, and never let chat packets grant permission. Do not modify login/security policy.
- Never put `forwarding.secret`, private keys, Discord webhooks, PlexonPanel identity files, or access tokens in source/config examples, logs, artifacts or chat outputs.

## 3. Architecture contract and transport selection

The recommended design is a dedicated first-party **`PlexonChats-Velocity`** companion and a small shared **versioned network protocol**, with Paper-side integration. Before implementation, verify that this is technically feasible with the actual Paper 26.3 and Velocity 4.3.0 APIs and compare it briefly against alternatives; record the decision.

- Use a namespaced plugin-message channel such as `plexonchats:network` or a justified equivalent, **not** the proxy's player-forwarding secret or the PlexonPanel WebSocket relay.
- Velocity must accept messages **only from `ServerConnection`** (not a player/client), mark or handle messages to prevent client forwarding, and validate the source backend and current sender association as supported by the API.
- The proxy fans out **only to the known configured backend allowlist**; origin receives its local copy exactly once, while other backends receive one packet each. A destination with no connected players can be skipped in player-connection-based plugin messaging.
- Inbound Paper packets are accepted only from the proxy's registered channel; reject malformed/unexpected message types, unknown origin, invalid lengths, unsupported versions, stale/duplicate IDs, invalid UTF-8/control characters, oversized components, and unauthorized identities. **Do not make forged claims about Mojang chat signatures**; received chat is displayed as safe decorated/system chat.
- Use immutable, bounded messages: random message ID, protocol version, origin server, author identity/display name, payload, timestamp or monotonic ordering metadata, and explicit channel `UNIVERSAL`. Origin must be verified from actual transport, not merely payload strings. Include a strategy for cross-server player/connection changes.
- Use bounded send queues, bounded TTL deduplication (by message ID), scheduled cleanup, bounded memory, and explicit backpressure/failure metrics. Do not block the Paper main thread on sockets, SQLite or external services; do not create one expensive scheduled task per recipient/message where batching is possible.
- Paper plugin messaging depends on connected player channels. Handle 0-player backends, server switching, disconnects, plugin reload, and relay outages without fake delivery guarantees. This is **best-effort live delivery**, not persisted message history; no new join history or guaranteed global total ordering.
- No extra publicly exposed listener or unrestricted RCON/HTTP endpoint is authorized.

If these assumptions prove unsound, document the blocker and choose the least-privileged verifiable approach; never silently compromise source validation.

## 4. Required source audit before edits

Find every current two-channel assumption in source, tests, configurations and docs. At minimum inspect:

- `ChatChannel`, `chat/ChatManager.java`, `chat/ChatListener.java`, `chat/ChatComponentFactory.java`, rate limiter, placeholder and text renderers, native viewer restrictions.
- `/g`, `/l`, `/chat`, `/msg`, `/reply`, tab completion, `src/main/resources/plugin.yml` and permission defaults.
- `gui/ChatGUI.java`, `gui/GUIListener.java`, `gui/GuiAction.java`, configured GUI slots, previews, channel-select state, existing admin GUI.
- `player/PreferenceStore.java`, `PlayerPreferences`, `PlayerChatPreferencesView` and UUID-keyed `players.yml` persistence.
- `PlexonChatsAPI`, `PlexonChatsApiImpl`, `PlexonChatEvent` semantics and third-party consumers, including PlexonPanel.
- `ConfigManager`, `ConfigUpgrader`, config/default schema drift, migration/rollback, transactional reload.
- `DiscordSrvBridge`, chat and Chat Events publisher; origin-only posting and no loops.
- `ChatEventManager`, `ChatEventConfig`, `ChatEventValidation`, Bingo, SQLite, winners/rewards/exact-once semantics.
- `README`, docs, Maven dependencies, release/build workflows, jars and checksums.

The observed 4.0.1 source has `ConfigUpgrader.VERSION = 10` while the bundled `src/main/resources/config.yml` starts with `config-version: 8`. **Verify and resolve this discrepancy deliberately**; do not silently clobber administrator YAML.

Write a concise `docs/network/SOURCE_AUDIT.md` with a change map, file references, public API impacts, and verified status (PASS/CHANGE REQUIRED/BLOCKED). Do not invent completed work or test evidence.

## 5. Compatibility and migration invariants

- Preserve `LOCAL` and `GLOBAL` enum values and behaviors; append `UNIVERSAL` safely and audit `switch` statements, arrays, formatting branches and event accepted-channel parsing.
- Preserve commands/aliases and `/g` / `/l` semantics; add `/u [message]`, `/chat channel universal`, optional `/chat universal`, `plexonchats.universal`, and receive-permission checks. Avoid conflicts with other network commands.
- Add one well-integrated UNIVERSAL GUI entry with safe slot/layout migration; preserve administrator-owned GUI collections/slots; do not duplicate listener registration or navigation.
- Preserve saved `players.yml` channel selections; no auto-conversion of GLOBAL to UNIVERSAL, no sharing preferences between instances without separate consent.
- Maintain `PlexonChatsAPI` and `PlexonChatEvent` source/binary compatibility **to the extent technically feasible**, and record any incompatibility explicitly; adding enum constants can change downstream exhaustive switches.
- Keep `config.yml` presentation/integration authority separate from `data.yml` gameplay and `chat-events.db` statistics. Preserve custom formats, messages, scheduling, reward profiles, Bingo cards and local event accepted channels. A remote UNIVERSAL message is never an eligible competition answer.
- Preserve MiniMessage trust boundaries: player-authored text or remote packet fields become safe components, never unrestricted formatting source. Keep player hover/click privacy permissions.
- DiscordSRV's existing local GLOBAL behavior stays unchanged by default. UNIVERSAL-to-Discord, if supported, is opt-in and must publish once **from origin only**. Discord-origin chat must not automatically become network-wide. LOCAL is never bridged.
- Cross-server `/msg` and `/reply` are **out of scope**, unless explicitly approved as a separate tracked feature.
- Keep plugin compatible with standalone mode: when the companion is not present, LOCAL/GLOBAL, GUIs, event rewards and existing features must work normally.

## 6. Engineering and performance standards

- Java 25 toolchain, Paper 26.3 API for Paper artifact; Velocity 4.3.0 API for proxy artifact. Verify actual published API artifacts and exact required JDK before changing Maven coordinates.
- Use Maven modules only if they produce clean independent artifacts with deterministic dependency/shading boundaries. Never shade Paper/Velocity platform APIs or duplicate Adventure types accidentally.
- Keep a single authoritative chat delivery pipeline; clear origin-vs-receiver separation. No loops, duplicate listeners or per-recipient synchronous network work.
- Use nonblocking I/O, bounded event queues, single/coalesced scheduled tasks where sensible, no unbounded caches, no synchronous SQLite/HTTP on chat path.
- Ensure safe `onDisable`, configuration reload and reconnect: unregister channels/listeners, cancel tasks, clear or drain buffers safely and close resources.
- Maintain permissions, explicit defaults, error paths and diagnostics. Extend `/chat diagnostics` to show proxy bridge state/queue/drops/duplicates/incompatible version without exposing credentials.
- Respect existing clean UI/preferences styling and localization; no unrelated redesign of GUIs/Chat Events.
- No destructive tests against production players, no deletion of live world/identity files, no permission widening (`chmod -R 777`, broad `chown`, etc.).

## 7. Suggested implementation sequence

1. **Baseline:** verify HEAD, tag, Java/Paper/Velocity versions, build/tests and actual APIs. Create `feat/velocity-universal-chat` (or a unique equivalent) from verified `main`; reconcile drift.
2. **Audit and design:** `docs/network/SOURCE_AUDIT.md`, `CHANNEL_SEMANTICS.md`, `NETWORK_ARCHITECTURE.md`, `PROTOCOL_SPEC.md`, threat model, transport/ordering/failure decisions.
3. **Protocol and proxy:** shared codec/tests, Velocity companion registration/source validation/fan-out/health, protocol compatibility and dedup.
4. **Paper integration:** UNIVERSAL send/receive path; additive config migration; `/u`/GUI/permissions; format/origin labels; standalone fallback.
5. **Regression:** test all existing routes and every contract described below, including previously shipped chat features, zero-player and transport failure.
6. **Build and package:** clean Maven verify, Paper and Velocity JARs, SHA-256, provenance, migration guide, deployment/rollback script/instructions that are **not executed on production**.
7. **Review:** source diff, automated test counts, known limits, measured performance when possible, review-ready PR if explicitly permitted. No merge or deployment.

## 8. Release-blocking test matrix

- **U-01/U-02/U-03:** send UNIVERSAL from Lobby/SMP/Towny respectively; exactly one message to every eligible connected recipient on all three backends; correct source label.
- **G-01/G-02:** GLOBAL goes to all eligible players in the current server and nowhere else.
- **L-01/L-02:** LOCAL 99/100 blocks included; 101 excluded; different world and different backend excluded.
- **MOD-01:** canceled/moderation-edited `PlexonChatEvent` cannot bypass validation or moderation when sent UNIVERSAL.
- **CH-01:** `/u`, `/g`, `/l`, `/chat channel ...`, GUI, permissions, saved preferences, rename/restart/reload, and no old-channel regression.
- **AUTH-01:** client-origin spoof, invalid backend origin, wrong current server/player, unauthenticated network packet, unknown/future protocol and malicious markup rejected.
- **DUP-01:** duplicate/replay/out-of-order/reentrant packets never cause duplicate display, repeated Discord posting or repeat events.
- **EVT-01/02:** remote UNIVERSAL cannot win another server's Chat Event or Bingo; local accepted answers, reward commits, persistent win stats unaffected.
- **DIS-01:** Discord bridge avoids loops and duplicate posts, opt-in Universal behavior only; LOCAL and private messages remain private.
- **OUT-01/02:** no proxy plugin, no connected carrier, empty backend, slow server, restart, reload and queue saturation do not block native chat or pretend success.
- **CFG-01/02:** migrate customized 4.0.1 config/players/data; invalid reload preserves previously active runtime generation, backup and rollback paths.
- **BUILD-01/PERF-01:** clean `mvn --batch-mode clean verify` (or documented modular equivalent), relevant CI, checksums, idle/burst measurements and no unbounded work.

Unit/mock verification **does not prove live network interoperability**; report real end-to-end status separately as PASSED, BLOCKED or NOT RUN, with reasons.

## 9. Definition of done / required artifacts

At minimum deliver:

- `docs/network/SOURCE_AUDIT.md`, `CHANNEL_SEMANTICS.md`, `NETWORK_ARCHITECTURE.md`, `PROTOCOL_SPEC.md`, `SECURITY_AND_FAILURE_MODES.md`, `TEST_MATRIX.md`.
- Versioned Paper + Velocity companion modules/artifacts and shared protocol, with source diffs and validated configuration examples for `lobby`, `smp`, `towny`.
- `docs/network/MIGRATION_AND_ROLLBACK.md` including state preservation, startup sequencing and live acceptance procedure, but **no performed deployment**.
- Test report: explicit counts/pass/fail/skip, what was actually executed vs simulated, source commit, JAR SHA-256, dependency versions, behavior/performance evidence.
- Review-ready feature branch/PR if authorized; otherwise a portable patch or source archive and exact apply instructions.

Never claim release-ready, production-deployed, or fully certified without corresponding evidence. If runtime access is unavailable, finish as much source/build/test work as possible, then mark real Paper/Velocity integration certification pending. Do not hide a blocker behind a mock test.
