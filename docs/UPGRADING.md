# Upgrading PlexonChats to 3.5.0

PlexonChats 3.5.0 is built directly on stable `v3.4.0` (`26cdf6fcff09d0443bbea64c5ec5ae3c4c1cd3a9`). The release changes Bingo gameplay and configuration schema; unrelated accepted chat/event systems remain in place.

## Before upgrading

Keep a copy of the current plugin JAR, `plugins/PlexonChats/config.yml`, `players.yml` and `chat-events.db`.

On the first successful configuration upgrade PlexonChats creates:

```text
config-before-v7-<timestamp>.yml
```

Do not replace a customized live configuration with the bundled file.

## Schema v6 → v7

The migrator preserves administrator-owned `chat-events.events`; it does not repopulate or replace that collection with fresh defaults.

For existing explicit `type: BINGO` definitions, it preserves:

- event ID;
- name;
- enabled state;
- weight;
- cooldown;
- reward-profile reference;
- minimum-online value;
- existing event duration where supplied.

If a legacy nested Bingo timeout exists and no event duration is already defined, it becomes the event `duration-seconds`.

The following 3.4 Bingo mechanics are removed/ignored:

- opt-in join window and minimum participant count;
- player-specific cards;
- FREE center;
- click/manual cell marking;
- participant-only board/draw traffic;
- reconnect card restoration.

Legacy enabled win-pattern choices are mapped to horizontal/vertical/diagonal/Full House settings where available. Fresh defaults enable horizontal, vertical and diagonal and leave Full House disabled.

A definition whose ID is `bingo` is not special. Only explicit `type: BINGO` definitions are migrated as Bingo. `type: TYPE` remains TYPE.

## Behavioral change

3.4.0:

```text
JOINING -> per-player cards -> player cell marking -> winner
```

3.5.0:

```text
STARTING -> one shared board -> automatic calls/marks -> first valid /bingo claim or public "bingo" -> winner
```

There is no FREE tile. The center is a normal `N` value from `31..45`.

## Commands

Removed gameplay concepts:

```text
/chat events bingo join <run-id>
/chat events bingo mark ...
/chat events bingo participants
```

Use:

```text
/bingo
/bingo claim
/bingo start
/bingo stop
/bingo status
```

Existing `/chat events` management remains available, including the compatible `bingo board|claim|start|stop|status` routes.

## Discord

Bingo can optionally publish the shared board through a dedicated webhook configured under `chat-events.bingo.discord`. Leave `enabled: false` and `webhook-url: ""` to disable it. The existing DiscordSRV normal-chat integration is unchanged.

## Validation after upgrade

Before production promotion verify source/CI tests and the packaged JAR. On a real server, follow up with:

1. clean startup;
2. `/bingo start` creates one active shared board;
3. the display has `# B I N G O` plus five playable rows;
4. center is a normal number;
5. calls are unique and matching card cells auto-mark green/bracketed;
6. `/bingo` displays the same board privately;
7. false command/public claims are rejected;
8. the first valid claim wins exactly once;
9. reward/statistics increment once;
10. scheduled Bingo remains selectable through the normal Chat Events scheduler;
11. optional Discord ANSI output is readable;
12. LOCAL/GLOBAL chat, PM/reply, other events and DiscordSRV remain unaffected.

## Rollback

Authoritative rollback target:

```text
v3.4.0
26cdf6fcff09d0443bbea64c5ec5ae3c4c1cd3a9
```

To roll back after v7 migration:

1. stop the server/plugin cleanly;
2. restore `PlexonChats-3.4.0.jar`;
3. restore the matching pre-v7 `config-before-v7-*.yml` as `config.yml`;
4. preserve `players.yml` and `chat-events.db` unless a separate operational reason requires restoring their backups;
5. start the server and verify 3.4.0 behavior.

Because 3.4.0 expects schema v6 and the old Bingo model, restoring the pre-v7 config is part of rollback.
