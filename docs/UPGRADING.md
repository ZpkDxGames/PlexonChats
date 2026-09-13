# Upgrading PlexonChats to 4.0.0

PlexonChats 4.0.0 is built from stable `v3.6.2` (`2099d8b50d267b9a430bc1ef34a18415b020318c`). This is a gameplay architecture upgrade: v3.6.2 shared automatic-mark Bingo is replaced by explicit joining, participant-owned cards, and manual marks.

## Before upgrading

Back up:

- the current PlexonChats JAR;
- `plugins/PlexonChats/config.yml`;
- `plugins/PlexonChats/players.yml`;
- `plugins/PlexonChats/chat-events.db`;
- existing `plugins/PlexonChats/data.yml`, if a pre-release/test build already created one.

The authoritative rollback release is:

```text
v3.6.2
2099d8b50d267b9a430bc1ef34a18415b020318c
```

## First 4.0 startup

`config.yml` remains schema v10. 4.0 introduces `data.yml` schema 1 for Chat Events scheduler/randomizer/minigame gameplay.

When `data.yml` is absent, PlexonChats:

1. writes `config-before-v4-<timestamp>.yml`;
2. reads compatible v3.6.2 Chat Events gameplay values;
3. creates schema-1 `data.yml`;
4. carries across administrator-owned enabled states, weights, cooldowns, durations, minimum-online values, reward references, channels/eligibility, content pools, math ranges, and compatible Bingo timing/pattern settings;
5. supplies the new lobby/manual-mark settings from v4 defaults;
6. leaves Discord webhook credentials and other secrets in `config.yml`.

If `data.yml` already exists, the migration does not replace its administrator values with legacy `config.yml` gameplay.

## Bingo behavior change

The following v3.6.2 behavior is intentionally removed:

- one shared card for all players;
- automatic marking from global draw history;
- claim validation against globally drawn shared cells;
- Discord rendering of a shared live board.

The 4.0 flow is:

1. Bingo opens a `LOBBY`.
2. Scheduled stock reminders are 60/30/15/5 seconds.
3. Players explicitly join with the clickable JOIN action or `/bingo join`.
4. Each participant receives one stable personal card.
5. The server calls numbers globally.
6. Calls do not mark cards.
7. Players click a called number on their own card to mark it.
8. Uncalled clicks are rejected in the action bar with no state change.
9. Successful marks render green without brackets.
10. `/bingo claim` evaluates the claimant's manual marked cells only.
11. Exactly one accepted winner owns reward/statistics completion.

Old run-bound clickable card actions cannot mutate a new run.

## Administration

Player commands:

```text
/bingo
/bingo join
/bingo leave
/bingo claim
```

Admin commands:

```text
/bingo start
/bingo start now
/bingo stop
/bingo status
```

`/bingo start now` is intended for admin testing after at least one participant explicitly joins. It does not reduce scheduled production minimums.

## Discord

Discord remains `DISPLAY_ONLY`. Bingo Discord output now contains shared lifecycle metadata (lobby/countdown, participants, last call/draw count, winner/pattern) and never exposes participant-owned cards. Discord cannot join, mark, claim, or issue a reward.

## Validation after upgrade

Source/CI verification is necessary but not sufficient for live certification. On a real Paper 26.2 / Java 25 server, verify:

1. one scheduled lobby with 60/30/15/5 reminders and no duplicates;
2. clickable JOIN plus `/bingo join`;
3. two participants receive independent cards;
4. called numbers do not auto-mark either card;
5. uncalled click gives action-bar rejection and no mutation;
6. called click marks only that player's cell and renders green/no brackets;
7. early claim is rejected;
8. a manually completed enabled pattern wins exactly once;
9. no later mark/claim creates another reward/stat;
10. table/header alignment under normal client rendering;
11. one-player admin test can activate;
12. scheduled production minimum remains unchanged;
13. `data.yml` exists, reloads, and preserves custom pools/ranges;
14. Discord shows lifecycle metadata but no fake shared card;
15. normal TYPE/UNSCRAMBLE/MATH/TRIVIA/REVERSE events, chat, PM/reply, and DiscordSRV chat still function.

Do not mark runtime certification PASS without this evidence. Release provenance remains `FOLLOW_UP_REQUIRED` until the live checklist is actually executed.

## Rollback

To return to 3.6.2:

1. stop the server/plugin cleanly;
2. restore `PlexonChats-3.6.2.jar`;
3. restore the appropriate pre-v4 configuration backup as `config.yml` if v4-specific edits were made;
4. keep the v4 `data.yml` out of the 3.6.2 runtime (archive it rather than destroying it);
5. preserve/restore `players.yml` and `chat-events.db` according to the operational restore point;
6. start the server and verify 3.6.2 behavior.

The authoritative source rollback tag/SHA is `v3.6.2` / `2099d8b50d267b9a430bc1ef34a18415b020318c`.
