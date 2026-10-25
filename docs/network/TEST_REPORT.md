# Test report — TonimNetwork development candidate

Baseline: `main` = `f98b1a233ca3fc68ac9d94a45dfc414d02634300`,4.0.1.
Feature branch: `feat/velocity-universal-chat`; PR [#18](https://github.com/ZpkDxGames/PlexonChats/pull/18).

## Verified baseline

CI independently built the original source with Java 25 and ran154 tests:
154 passed,0 failures,0 errors,0 skipped. The source and suite were not substituted
with the new branch. Baseline evidence is retained by the workflow.

## Most recent completed candidate verification

Code at `9ad82a1880d58e3ca38257684e7376c09dd4283a` passed the complete clean Maven
reactor and distribution checks:191 tests,0 failures,0 errors,0 skipped.
[CI run40216869058](https://github.com/ZpkDxGames/PlexonChats/actions/runs/40216869058).
Protocol10; Paper170 (all154 prior tests plus16 Universal cases); Velocity11.

The next candidate additionally checks fully normalized bundled migration content
and incompatible-version diagnostics; its final result will be recorded before
handoff. Exact packaged source/checksums are in the delivered PROVENANCE.txt,
SHA256SUMS.txt and TEST_SUMMARY.json, generated at the tested CI HEAD.

## Environment and artifacts

- Maven clean verify reactor: shared protocol, Paper, Velocity.
- Java 25 / class major69; CI Temurin25.0.4.
- Paper API26.3.build.223-stable, Velocity API4.3.0.
- MockBukkit 26.3 4.118.0, JUnit6.0.3, Mockito5.23.0.
- Optional Core2.0.4 API checksum verified before build; DiscordSRV1.30.5 provided.
- Paper/Velocity/Bukkit/Adventure/Core/Discord API classes excluded from shipped JARs.
- Paper SQLite JDBC3.50.3.0 preserved. Companion has only the shared protocol bundled.
- Paper metadata/plugin.yml, Velocity plugin descriptor, protocol classes and class
  major69 checked by scripts/verify-network-distribution.py.

## Measured performance

Local isolated codec benchmark, Temurin25.0.1+8,20,000 measured authenticated
encode+decode round trips after2,000 warmups,702-byte average packet:
mean16.48µs,p95 26.43µs,p99 48.79µs. Source: scripts/ProtocolBenchmark.java.
The check asserts every decoded message equals its input. This is a codec
microbenchmark, not backend TPS/MSPT, memory-profile or production load evidence.
Queue/cache capacities and burst task counts are asserted separately.

## Regression findings resolved

The initial header correction exposed previously implicit stock presentation
migrations. Bundled templates were materialized to the current schema rather
than weakening existing assertions. A follow-up content-normalization test covers
numeric math defaults and Trivia expansion too. MockBukkit 26.3 cancels repeating
workers in waitAsyncTasksFinished, so the Discord test now observes the worker's
actual completion before teardown; production Discord code is unchanged.

## Live certification

NOT RUN: actual Paper 26.3 + Velocity 4.3.0 clients on TonimLobby/SMP/Towny, route/port
mapping, real installed optional-plugin stack, disconnect/restart races, client
rendering and representative idle/burst TPS/MSPT/allocations. No production access,
service restart, config mutation, merge, tag or stable release was performed.

Automated mock transports are not live interoperability evidence. Remaining
acceptance and rollback procedures are in MIGRATION_AND_ROLLBACK.md/TEST_MATRIX.md.
