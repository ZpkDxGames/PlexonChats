# Upgrading PlexonChats to 3.6.0

PlexonChats 3.6.0 is built directly on stable `v3.5.0` (`4353eb80b8b1e3efaac4e5ab5f82ff6e6c3f1210`). The release preserves accepted shared-board Bingo gameplay and adds generalized Discord Chat Event embed synchronization with configuration schema v8.

## Before upgrading

Keep a copy of the current plugin JAR, `plugins/PlexonChats/config.yml`, `players.yml` and `chat-events.db`.

On the first successful configuration upgrade PlexonChats creates:

```text
config-before-v8-<timestamp>.yml
```

Do not replace a customized live configuration with the bundled file.

## Schema v7 → v8

The migrator preserves administrator-owned:

- `chat-events.events` definitions and explicit event types;
- event enabled state, weights, cooldowns, timing and eligibility;
- `chat-events.reward-profiles`;
- unrelated customized configuration.

The former 3.5.0 Bingo-only Discord subtree:

```text
chat-events.bingo.discord
```

is generalized into:

```text
chat-events.discord
```

When present, the migrator carries forward the legacy enabled state, webhook URL, username, draw-update intent and winner-announcement intent where possible. A valid webhook URL is copied exactly into the new secret location and then removed from the obsolete duplicate subtree.

The older v6 → v7 shared-Bingo migration remains part of the upgrader, so installations crossing multiple schema generations still receive the accepted 3.5.0 Bingo corrections before v8 is applied.

## Discord behavioral change

3.5.0 optional Bingo Discord output used a dedicated webhook and emitted separate raw/ANSI messages.

3.6.0 uses one generalized presentation layer for all Chat Event types:

```text
START -> one Discord embed
UPDATE -> edit the same message
WIN/TIMEOUT/CANCEL -> edit the same message into terminal state
```

Bingo draw updates edit one persistent live embed rather than sending one new message per draw. DiscordSRV is preferred in `AUTO` mode when available; a configured webhook is the fallback.

`participation-mode: DISPLAY_ONLY` is mandatory in 3.6.0. Discord messages do not count as event answers or Bingo claims.

## Commands

Existing player/event/Bingo commands remain valid. New safe administration routes are:

```text
/chat events discord status
/chat events discord test
```

They require:

```text
plexonchats.admin.events.discord
```

The test route is presentation-only: it does not create a Chat Event, grant a reward or alter statistics.

## Secret handling

Webhook URLs are credentials. The 3.6.0 command, GUI and diagnostics surfaces report only safe state such as transport, readiness and channel configuration. They never print the webhook URL.

## Validation after upgrade

Before production promotion verify the exact source/CI tests and packaged JAR. On a real Paper 26.2 / Java 25 server, follow up with:

1. clean PlexonChats startup;
2. Discord event transport reports ready;
3. start a Math event and confirm exactly one Discord embed;
4. answer correctly in Minecraft and confirm that same message becomes completed;
5. start Bingo and confirm exactly one live Bingo embed;
6. confirm the Discord board matches `/bingo` and contains no FREE center;
7. confirm subsequent calls edit the same message and drawn values update;
8. confirm an invalid early claim leaves the Discord state LIVE;
9. obtain a valid pattern and claim in Minecraft;
10. confirm the same Discord message becomes WINNER;
11. confirm reward and statistics increment exactly once;
12. start/cancel another event and confirm its original message becomes CANCELLED;
13. test webhook fallback independently if configured;
14. confirm no command/GUI/diagnostics output exposes the webhook URL;
15. confirm LOCAL/GLOBAL chat, PM/reply and normal DiscordSRV player chat remain unaffected.

Do not mark runtime certification PASS without this real-server evidence.

## Rollback

Authoritative rollback target:

```text
v3.5.0
4353eb80b8b1e3efaac4e5ab5f82ff6e6c3f1210
```

To roll back after v8 migration:

1. stop the server/plugin cleanly;
2. restore `PlexonChats-3.5.0.jar`;
3. restore the matching pre-v8 `config-before-v8-*.yml` as `config.yml`;
4. preserve `players.yml` and `chat-events.db` unless a separate operational reason requires restoring their backups;
5. start the server and verify 3.5.0 behavior.

Because 3.5.0 expects schema v7 and the old Bingo-specific Discord configuration location, restoring the pre-v8 config is part of rollback.
