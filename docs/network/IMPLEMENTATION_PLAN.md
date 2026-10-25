# Implementation record

1. Read attached contract/directive in full; independently verified API and git
   main SHA. Traced all channel enum sites, native moderation and viewer handling,
   GUI/preferences/config, Discord, local events/rewards/statistics and build flow.
2. Recorded source audit/transport decision before source edits, on a feature branch.
3. Built version 1 shared codec and dedicated Velocity relay; added HMAC per backend,
   current connection/author association, handshake, queue limits, fail-closed TTL
   dedup, audience snapshots, rejection/partial acceptance and shutdown cleanup.
4. Extended the existing Paper ChatManager, formats, commands, GUI and schema 11.
   Remote presentation is a separate display-only method, never another chat owner.
5. Added tests for all origins, security and failure paths; retained existing suite.
   Verified exact4.0.1 baseline independently in CI. New build targets Paper 26.3 /
   Velocity 4.3.0, Java 25, with distinct platform artifacts and provided API checks.
6. Prepared operator examples, migration/rollback, test matrix, provenance/checksums
   and review-only PR. No main merge, tag, stable release or service changes.

Remaining: live staging acceptance, real optional integrations/client presentation,
route verification, representative load measurements, then separate production
approval. Repository implementation is not production certification.
