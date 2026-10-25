# Source audit before implementation — 2026-10-25

Remote GitHub API and git independently resolve `main` to
`f98b1a233ca3fc68ac9d94a45dfc414d02634300` (4.0.1). Local feature branch
`feat/velocity-universal-chat` starts at that commit. No drift from the handoff.

| Area / baseline evidence | Initial status / required change |
|---|---|
| `ChatChannel.java:7-8` | CHANGE REQUIRED: append UNIVERSAL; retain LOCAL/GLOBAL ordinals |
| `chat/ChatManager.java:60-150` | CHANGE REQUIRED: generalized prefix resolution, all-world U candidates, pre-delivery bridge admission, recheck eligible recipients after API edits |
| `chat/ChatListener.java:29-38` | PASS: one synchronous HIGHEST owner; capture moderated message and native viewers |
| `chat/ChatComponentFactory.java:29` | CHANGE REQUIRED: remove GLOBAL/else LOCAL format assumption; source label |
| `command/ChatCommand.java:39,99,346` and Global/Local commands | CHANGE REQUIRED: U command/selection/help/diagnostics |
| `gui/GuiAction.java:4,13`; `gui/ChatGUI.java:307-320` | CHANGE REQUIRED: U state, action and collision-free button addition |
| `config/ConfigManager.java:121,294` | CHANGE REQUIRED: validate third channel and bounded bridge settings transactionally |
| `config/ConfigUpgrader.java:15`; `resources/config.yml:7` | CHANGE REQUIRED: actual v10/v8 drift confirmed; additive v11 with exact backup |
| `player/PreferenceStore.java:43-47,74-83` | PASS: enum names persisted, UUID keyed, atomic writes; U round-trip tests required |
| `api/PlexonChatsAPI.java`; `api/PlexonChatEvent.java:30,38` | PASS signatures retained; U inherits moderation but Discord flag stays GLOBAL-only |
| `integration/DiscordSrvBridge.java:81,114` | PASS: GLOBAL-only outbound/inbound; retain unchanged, no Universal Discord bridging in this version |
| `event/ChatEventConfig.java:64`; `ChatEventManager.acceptAnswer`; Bingo models | PASS default LOCAL/GLOBAL; remote U uses presentation-only path; prevent U answers explicitly |
| `message/PrivateMessageManager`, `command/MessageCommand`, auto messages, item previews | PASS preserve local behavior; remote identity must not suggest a local PM or expose local item tokens |
| `pom.xml`; `.github/workflows/build.yml` | CHANGE REQUIRED: independent protocol/Paper/Velocity artifacts; Java 25; pinned published API versions |
| Live Paper/Velocity versions, route mapping, optional plugins | BLOCKED: production access/deployment not authorized; staging certification pending |

## Implementation decision

Use first-party Velocity companion and versioned plugin messaging. Redis/TCP adds
infrastructure and listeners; direct proxy rendering bypasses Paper permissions.
Retain the source paths and public Java packages. A Maven reactor builds a shared
protocol and separate Paper and Velocity JARs; platform APIs remain provided.

In addition to connection provenance, require dedicated per-backend 32-byte HMAC
keys (operator-created files, unrelated to forwarding secrets) so direct clients
cannot spoof Paper receives. Bridge disabled by default; no new listener.
Handshake/TTL health gates sending. A bounded origin queue retains a delivery
callback until proxy acceptance, avoiding a false local-only success. No retries
of accepted chat; all delivery remains best effort, without history.

Remote presentation transmits only bounded legacy color/decorations and plain
identity fields. No arbitrary component JSON, MiniMessage, click/hover payload,
commands, item-preview tokens or reward state crosses the wire. Origin rendering
retains existing player formatting/interactions. API methods/event descriptors
stay unchanged; downstream exhaustive enum switches must handle the new value.

Automated baseline/build results and final audit are recorded in TEST_REPORT.md.

## Final implementation review

| Area | Final repository status |
|---|---|
| Enum, commands/aliases/help/tab completion, GUI state/actions/previews | PASS automated; UNIVERSAL appended and `/u` implemented |
| Native ownership, moderation cancellation/edits, viewer subset and channel limits | PASS automated; same ChatManager route, eligibility rechecked; restricted U cannot expand remotely |
| GLOBAL/LOCAL isolation, inclusive100-block boundary, all-world GLOBAL | PASS automated; only U has a wire representation |
| Preferences, UUID data, PMs/reply, data.yml and SQLite models | PASS regression; unchanged implementations/data schema |
| Public API/event | PASS source descriptor review; API interface unchanged, event comment only; enum exhaustive-switch caveat documented |
| DiscordSRV | PASS regression; production bridge source byte-for-byte unchanged, GLOBAL-only; remote path has no bridge/event hooks |
| Chat Events/Bingo/rewards/statistics | PASS regression; U explicitly ineligible; existing code unchanged except answer guard |
| Config schema drift, exact backups, custom GUI collections | PASS automated; schema11 additive, bundled stock migrations materialized; custom layouts/values preserved |
| Protocol, proxy connection provenance, HMAC, bounded queues/dedup | PASS unit/mock transport; no new network listener |
| Platform compilation/metadata/shading, test totals | PASS CI; see TEST_REPORT.md and generated distribution evidence |
| Real network routing, installed integrations, client visuals, TPS/MSPT | BLOCKED / NOT RUN pending authorized staging access and acceptance |

All initial CHANGE REQUIRED items have implementations/tests. PASS above means
source/automated verification, not production certification. Actual final source
SHA and artifact hashes are recorded by the CI build, not inferred from filenames.
