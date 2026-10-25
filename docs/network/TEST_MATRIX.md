# Verification matrix

Automated checks run against the production source and shared protocol. These are
unit/MockBukkit/mock transport results, never live Minecraft client certification.
Final counts, build source and CI URL are in TEST_REPORT.md and delivered provenance.

| ID | Automated evidence | Live status |
|---|---|---|
| U-01/02/03 | `UniversalChatTest.everyPaperOriginHasCorrectLabelAndOneAcceptedEcho` (3 cases); `RelayTest.allThreeOriginsFanOutExactlyOnceAndAckOnlyAtOrigin` (3×3 simulated matrix) | NOT RUN |
| G-01/02 | Existing ChatRoutingTest + UniversalChatTest all-world check; GLOBAL has no transport route/type | NOT RUN |
| L-01/02 | UniversalChatTest exact99/100/101 + world boundary; existing routing tests | NOT RUN |
| MOD-01 | Universal origin cancellation, edited body, immutable submission, native viewer restriction | NOT RUN with installed moderation plugins |
| CH-01 | `/u`, `/chat universal`, saved U preferences, send permission and GUI action tests; existing PM/GUI regressions | Restart/rename/live clicks NOT RUN |
| AUTH-01 | Client channel claimed when disabled; wrong backend/carrier/current connection, MAC, version/type/channel/length, controls, strict UTF-8, stale/future/session tests | NOT RUN with actual client payloads |
| DUP-01 | Same UUID relayed twice; Paper duplicate display/ACK suppression; cache capacity/TTL test; no remote callbacks | Reconnect/restart race NOT RUN |
| EVT-01/02 | Remote path fires zero API events; Universal answers explicitly rejected; existing local Chat Events/Bingo/reward/SQLite suites retained | Actual economy/Keys/Discord stack NOT RUN |
| DIS-01 | Existing Discord tests retained; explicit U suppression alongside LOCAL; remote has no outbound hook | Actual DiscordSRV connection NOT RUN |
| OUT-01 | Default standalone U unavailable; L/G independent; no-key/health behavior; no carrier; expired relay health | Actual missing companion restart NOT RUN |
| OUT-02 | Empty destination, queue saturation, origin movement, old generation, bounded queues/task-count checks, proxy queue expiry | Slow backend/proxy restart NOT RUN |
| CFG-01 | Customized v10->v11 exact backup, format/default/slot preservation, older fixtures, intentionally empty collections, UUID preference round-trip | Real admin config snapshots unavailable |
| CFG-02 | Invalid queue config retains old bridge/revision; existing invalid YAML/data/GUI and event reload rollback tests | Service lifecycle NOT RUN |
| BUILD-01 | Clean Java 25 Maven reactor; provided API exclusion, metadata, class69 and SHA256 verification script | Live plugin loading NOT RUN |
| PERF-01 | Local20,000 codec round trips, bounded100-message relay flood, no per-chat scheduler tasks | Idle/burst TPS/MSPT/allocation NOT RUN |

The previously shipped 154 tests remain, with version/schema assertions updated
for the candidate and the asynchronous Discord wait corrected for MockBukkit 26.3.
Assertions on formatting, migrations, GUI, rewards, data/SQLite, PMs and Discord
are retained. No skipped MockBukkit behavior is accepted as coverage.

Use MIGRATION_AND_ROLLBACK.md for the actual three-server acceptance procedure.
