# Staging installation, migration and rollback

This runbook is prepared only. No VPS access, config mutation, live restart,
merge, tag or release has been performed. Obtain separate owner authorization
before any production rollout. This candidate is not live certified.

## Preconditions and independent routing check

Use Java 25, Paper 26.3 and Velocity 4.3.0. The compiled Paper API is pinned to
26.3.build.223-stable, Velocity API4.3.0. First stage isolated copies matching the
real plugins/configuration. Existing offline-account, whitelist and forwarding
policies must remain unchanged.

Independently verify Velocity's actual registered IDs `lobby`, `smp`, `towny` and
where `/server lobby`, `/server smp`, `/server towny` land. Inspect the destination
console/server identity and ports rather than only the NPC label or PlexonPanel
instance key. The reported Towny->SMP routing issue is separate; correct mapping
must be confirmed before chat certification. Do not change routing for this PR.

## Backups

For each Paper instance separately, plan a clean stop with the owner, then back
up existing JAR and the entire `plugins/PlexonChats/` directory: config.yml,
data.yml, players.yml and chat-events.db. Include SQLite WAL/SHM siblings if
present; a stopped-instance copy or SQLite backup API is required for consistency.
Back up the companion JAR/directory and reference hashes on Velocity. Preserve
service-user ownership, modes and timestamps. Never merge instance data folders,
copy identity/forwarding secrets into this package or broaden permissions.

## Dedicated chat authentication keys

Generate three independent 32-byte random keys on an operator-controlled machine,
using a restrictive umask. Example **operator-only command, not executed here**:

```sh
umask 077
openssl rand -hex 32 > lobby.key
openssl rand -hex 32 > smp.key
openssl rand -hex 32 > towny.key
```

Place the Lobby key as `plugins/PlexonChats/network.key` on Lobby, and the same
bytes as `plugins/plexonchats-velocity/keys/lobby.key` on Velocity. Repeat for SMP
and Towny. Only the relevant backend and proxy service user should read a key.
These are *new chat keys*, not forwarding.secret, PlexonPanel identity or Discord
credentials. Keep them out of source control, screenshots and shared artifacts.
No key values are included in the delivered examples.

## Staging sequence

1. Check SHA256SUMS.txt against both delivered JARs; retain provenance/test files.
2. On an isolated Velocity 4.3.0 staging proxy, install
   `PlexonChats-Velocity-4.1.0-SNAPSHOT.jar` in its plugins directory. First start
   creates `plugins/plexonchats-velocity/bridge.properties`, disabled by default.
3. Stage the three key files. Merge `examples/velocity-bridge.properties` with
   that file, ensure the allowlist matches registered servers, then restart the
   staging proxy normally. No hot-loading.
4. Stage `PlexonChats-4.1.0-SNAPSHOT.jar` on one matching Paper backend. First load
   upgrades config.yml additively to schema 11 and saves an exact
   `config-before-v11-<timestamp>.yml`. The bundled header and stock presentation
   now match the real schema; prior exact-stock v8->v10 transformations remain
   supported. data.yml stays schema 1; existing data/players/statistics survive.
5. Merge only the `network` section from the matching Paper example into the
   existing config. Keep the administrator's default-channel, formats, reward
   profiles, gameplay values and player preferences. Configure instance-id exactly.
6. Apply valid Paper configuration with `/chat reload`; a new binary requires a
   normal Paper restart. With a connected player, allow one heartbeat (~3s), then
   check `/chat network status` and `/chat diagnostics`. Key errors/unknown version
   or missing companion must not be treated as network-ready.
7. Verify L/G/PM/GUI/events on that backend, then stage other Paper instances.
8. Perform the complete live matrix below before requesting production approval.

New Universal GUI choice uses an unused slot without moving existing buttons.
An intentionally empty custom collection stays empty; a completely full custom
layout cannot receive an automatic entry. Add an explicit UNIVERSAL action at an
operator-chosen available slot/expanded layout in that exceptional case; `/u`
works independently. No administrator collection is replaced to force defaults.

## Live acceptance evidence (NOT RUN)

With eligible players connected on all three staging backends:

| Origin | Lobby expected | SMP expected | Towny expected |
|---|---|---|---|
| Lobby `/u U01` | one `[Lobby]` | one `[Lobby]` | one `[Lobby]` |
| SMP `/u U02` | one `[SMP]` | one `[SMP]` | one `[SMP]` |
| Towny `/u U03` | one `[Towny]` | one `[Towny]` | one `[Towny]` |

Then test `/g` in Lobby and Towny: only that backend's players, all worlds.
Test `/l` at99/100/101 blocks plus another world/backend:99/100 included only in
same world/backend. Record actual client captures, console versions, route IDs,
plugin lists and timings; redact any secrets.

Exercise `/u` select/send, `/chat channel universal`, GUI and denied send/receive
permissions; restart and verify all saved channel/alert/PM preferences. Trigger
and cancel/modify moderation. Confirm remote answers cannot win Chat Events or
Bingo and local winners issue one reward/stat. Confirm existing Discord GLOBAL
posting remains one per send, Universal/LOCAL/PM never post, Discord incoming
remains backend GLOBAL. Test the actual Core/DiscordSRV/LuckPerms/PAPI/Vault stack.

Disconnect/switch players during bursts; empty/restart a destination; stop the
companion in staging; reload configs and binaries normally; force malformed or
wrong-key packets only in isolated tests. Verify honest U unavailable/partial
messages, no local success echo on failed submission, no duplicate/late history,
no extra task/channel registrations and L/G continuity. Measure idle and burst
CPU, allocations, MSPT/TPS and memory against baseline under representative player
counts. Codec timings alone are not a performance certification.

## Rollback (operator-only, not executed)

1. Announce/authorize a rollback window, stop only affected Paper services cleanly;
   coordinate the separate proxy restart, which disconnects clients.
2. Restore each instance's original 4.0.1 JAR and its own exact pre-v11 config.yml.
   Old builds reject a newer schema; restoring just the JAR is insufficient.
3. Preserve current players.yml/statistics whenever possible. Before restoring
   an older binary, translate only UNIVERSAL selections back to the operator's
   chosen LOCAL/GLOBAL fallback if needed; do not restore stale player/DB copies
   and lose new wins simply to remove a channel. Back up the current state first.
4. data.yml remains schema 1; restore it only if separately changed. Preserve all
   runtime-created wins and SQLite consistency. No database schema change exists.
5. Remove/restore only the companion JAR/config; keep dedicated chat keys private
   for rotation/removal under the operator's retention policy. Do not touch other
   proxy plugins or forwarding/routing configuration.
6. Start normal services; verify L/G/PM/GUI, local events/Bingo, saved preferences,
   Discord and routing. Record the rollback hashes and test outcome.

Production rollout remains a separate, unperformed operation after successful
staging acceptance and explicit owner authorization.
