# Upgrading PlexonChats

## 3.2.0 → 3.3.0

PlexonChats 3.3.0 adds configurable Chat Events while preserving the accepted 3.2.0 communication boundary. The migration is additive but advances configuration schema **4 → 5**.

### Before installing

1. Use a staging Paper **26.2 / Java 25** server first.
2. Stop the server.
3. Back up `PlexonChats-3.2.0.jar` and the entire `plugins/PlexonChats/` directory.
4. Replace only the JAR with `PlexonChats-3.3.0.jar`.
5. Keep the existing `config.yml` and `players.yml`.
6. Start the server and inspect console plus `/chat diagnostics` before normal player traffic resumes.

The migration creates a `config-before-v5-*.yml` backup before adding missing v5 defaults. Existing administrator settings and intentionally customized GUI/auto-message collections remain authoritative. Do **not** delete the data directory or overwrite the live config with the bundled defaults.

### What schema v5 adds

- `chat-events.enabled` master toggle;
- independent `chat-events.scheduler.enabled` and interval settings;
- audience/matching defaults;
- reusable reward profiles;
- TYPE, UNSCRAMBLE, MATH, TRIVIA, and REVERSE definitions;
- configurable event sounds/messages;
- Chat Events administration GUI page;
- `plexonchats.events` and `plexonchats.events.manage` permissions.

The default config enables Chat Events and automatic scheduling. Review reward values and event frequency before putting 3.3.0 into production. In particular, the default `rare`/`epic` profiles expect optional PlexonKeys, and cash profiles expect an available Vault economy provider. Missing integrations fail their reward component safely but should still be configured intentionally.

### Existing behavior preserved

3.3.0 does not redesign LOCAL/GLOBAL routing, synchronous cancellable `PlexonChatEvent`, `PlexonChatsAPI`, `/msg`/`/reply`, item display, mentions, player preferences, connection messages, auto-messages, DiscordSRV isolation, or the transactional reload model.

Only genuine accepted native Minecraft public chat can answer an event. `/g`, `/l`, PMs, Discord-origin messages, console, broadcasts, auto-messages, and synthetic/plugin sends do not count. During an active run, `PlexonChatEvent` cancellation is honored before answer acceptance.

### First validation after upgrade

Run:

```text
/chat status
/chat diagnostics
/chat events status
/chat events list
/chat events preview bingo
/chat admin
```

Expect:

- version `3.3.0`;
- Chat Events master/scheduler/task state visible;
- six default definitions loaded (`bingo`, `unscramble`, `math-normal`, `math-hard`, `trivia`, `reverse`);
- Vault/PlexonKeys reward integration state reported explicitly;
- one Chat Events coordinator only.

### Staging smoke sequence

1. Verify normal LOCAL and GLOBAL chat still deliver once.
2. Verify `/g`, `/l`, `/msg`, and `/reply` remain functional.
3. Open `/chat admin` → Chat Events and verify the compact page.
4. Run `/chat events preview bingo`; confirm it is private and does not start/reward anything.
5. Run `/chat events start bingo`.
6. Send a wrong answer; the run must remain active.
7. Send the correct answer through normal native public chat; exactly one player must win.
8. Confirm only the reward components actually available/configured are reported as granted.
9. Attempt a second correct answer; no second reward may occur.
10. Start another event and let it time out; no reward may occur.
11. Test `/chat events stop`; cancellation grants nothing.
12. Set scheduler disabled while master remains enabled; manual start must still work.
13. Disable the master; no playable event may start.
14. Re-enable and test `/chat reload`; successful reload cancels an active run without reward and restarts scheduling from a fresh initial delay.
15. Verify auto-messages continue independently.
16. If DiscordSRV is enabled, verify Discord-origin messages cannot answer and that global chat still has no echo loop.
17. Re-run `/chat diagnostics` and inspect logs/TPS/MSPT for task leaks.

Repeated `/chat reload` calls must retain one preference writer, one auto-message timer, one Chat Events coordinator, and one item-preview cleanup timer—never one task per event/player/message.

### Validation of transactional rejection

On staging, deliberately make one invalid candidate (for example an unknown Chat Event type or invalid MATH operand range) and run `/chat reload`. The reload must fail, identify the exact configuration path, and retain the prior live configuration. Repair the file before continuing.

### Runtime deployment note

GitHub source/release closure is allowed without direct PlexonCraft host access when build/tests/release provenance are exact. If production access is unavailable during release publication, mark live deployment/smoke testing as a separate pending operational step; do not infer an in-game PASS from CI.

## Rollback from 3.3.0 to 3.2.0

1. Stop the server.
2. Restore `PlexonChats-3.2.0.jar`.
3. Restore the pre-v5 `config-before-v5-*.yml` as `config.yml` before starting 3.2.0. Schema 4 does not know the v5 Chat Events section.
4. Preserve `players.yml`; its preference format is unchanged.
5. Start the server and validate normal LOCAL/GLOBAL chat, PMs, GUI, auto-messages, DiscordSRV, and `/chat diagnostics`.

3.3.0 does not perform irreversible player-data migration. The config backup is the clean rollback boundary.

---

## Historical 3.0 → 3.1.0 notes

PlexonChats 3.1.0 was a Core/platform/reliability migration based on production `3.0-Release`. It retained schema 3, preserved all communication features, and introduced the optional PlexonCore lifecycle/API bridge. When Core was unavailable or incompatible, Chats continued in standalone mode.

## Historical 2.0 → 3.0 notes

Version 3.0 introduced configuration schema 3, configurable rich channel formats, persistent player preferences, scheduled-message groups, configurable GUI pages, `PlexonChatEvent`, connection messages, and optional DiscordSRV routing. Existing 2.0/unversioned configurations were conservatively upgraded with backups while preserving explicitly configured values and intentionally empty custom collections.
