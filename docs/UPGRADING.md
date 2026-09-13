# Upgrading PlexonChats to 4.0.1

PlexonChats 4.0.1 is a focused Bingo renderer/pacing release built directly from stable `v4.0.0` (`657d27766bce60235bc50602b4dd4d14b9e6998c`). The 4.0.0 participant-owned/manual-mark authority model remains unchanged.

## Before upgrading

Back up:

- the current PlexonChats JAR;
- `plugins/PlexonChats/config.yml`;
- `plugins/PlexonChats/data.yml`;
- `plugins/PlexonChats/players.yml`;
- `plugins/PlexonChats/chat-events.db`.

The authoritative 4.0.1 rollback release is:

```text
v4.0.0
657d27766bce60235bc50602b4dd4d14b9e6998c
```

## What changes in 4.0.1

Fresh 4.0.1 gameplay data changes two stock Bingo presentation/pacing defaults:

- renderer: `TABLE` with fixed-width `minecraft:uniform`, four-character cells, aligned separators and borders;
- draw cadence: `draws.interval-seconds: 5` instead of the 4.0.0 stock value of 8.

`COMPACT` remains available as the 4.0.0-compatible borderless renderer. The grid never uses width-changing bold/marker characters for mark or winner state.

The following 4.0.0 mechanics remain authoritative and are not migrated or rewritten:

- explicit Bingo join lobby;
- stable participant-owned cards;
- global draw history separate from manual marks;
- server-validated run-bound cell clicks;
- winning evaluation from manual marked cells only;
- exact-once reward/stat/winner completion;
- production minimums distinct from one-player admin testing;
- Discord display-only behavior.

## Existing `data.yml` is preserved

`data.yml` remains schema **1**. 4.0.1 does not bump the schema merely to change defaults, so an existing administrator-owned schema-1 file is not rewritten to force new values.

That means an existing 4.0.0 server commonly retains:

```yaml
draws:
  first-call-delay-seconds: 5
  interval-seconds: 8

render:
  font: "minecraft:uniform"
  left-padding: 3
  cell-width: 4
  column-gap: 1
  show-border: false
```

If `render.style` is absent, 4.0.1 preserves compatibility from the existing `show-border` setting. A typical 4.0.0 borderless block therefore remains `COMPACT` at runtime rather than being silently converted.

## Adopt the recommended 5-second cadence

To opt an existing installation into the fresh 4.0.1 call pacing, edit:

```yaml
# plugins/PlexonChats/data.yml
minigames:
  bingo-classic:
    draws:
      first-call-delay-seconds: 5
      interval-seconds: 5
```

Then apply it with:

```text
/chat reload
```

Do not add a second Bingo draw interval to `config.yml`; `data.yml` remains the sole gameplay authority.

## Adopt the new table renderer

To opt an existing 4.0.0 installation into the fresh table presentation, use:

```yaml
# plugins/PlexonChats/data.yml
minigames:
  bingo-classic:
    render:
      style: TABLE
      font: "minecraft:uniform"
      left-padding: 2
      cell-width: 4
      column-gap: 0
      show-border: true
      show-last-call: true
      show-draw-count: true
      on-join: true
      on-start: true
      after-successful-mark: true
      on-every-draw: false
```

`COMPACT` may be selected explicitly if the old borderless presentation is preferred.

## Reload behavior

`/chat reload` validates `config.yml` and `data.yml` as a new runtime generation. A valid renderer/pacing change affects future Bingo state according to the normal reload lifecycle. Malformed Bingo timing/style/font/geometry is isolated to the malformed minigame where safe instead of disabling unrelated chat features.

No stale draw scheduler should continue after reload, and the plugin must not create a duplicate draw scheduler.

## Visual/runtime validation after upgrade

Source/CI verification cannot prove Minecraft pixel alignment. On a real Paper 26.2 / Java 25 client, preferably at two normal GUI scales, verify:

1. the top/header/data/bottom separators form straight vertical columns;
2. B/I/N/G/O headers are centered over their columns;
3. one-digit source values render as two digits without shifting cells;
4. marking cells green does not move separators;
5. winner color/underline does not widen cells;
6. every number cell remains clickable and run-bound;
7. uncalled cells are still server-authoritatively rejected;
8. five-second calls are readable;
9. the full board is not reprinted every five seconds by default;
10. `[ CALL BINGO ]` remains clear and clickable;
11. draw history still does not auto-mark or create a win;
12. exactly one claimant can own reward/stat completion;
13. scheduled participant minimums remain intact and one-player admin testing still works;
14. Discord shows lifecycle metadata only and no participant card.

If Unicode box drawing is visibly inconsistent under the target client/font, use the deterministic ASCII fallback rather than accepting a misaligned table.

Do not mark runtime certification PASS without real-client evidence. Release provenance remains:

```text
runtime_certification=FOLLOW_UP_REQUIRED
```

## Historical first-v4 migration

For servers upgrading from pre-v4 rather than from 4.0.0: `config.yml` remains schema v10 and v4 introduced `data.yml` schema 1. When `data.yml` is absent, PlexonChats creates `config-before-v4-<timestamp>.yml`, migrates compatible older Chat Events gameplay values into `data.yml`, and leaves Discord/webhook credentials and other secrets in `config.yml`.

The v4 architecture intentionally replaced shared automatic-mark Bingo with explicit joining, participant-owned cards, manual server-validated marks, and Discord metadata-only presentation. Those mechanics remain the foundation of 4.0.1.

## Rollback

To return from 4.0.1 to 4.0.0:

1. stop the server/plugin cleanly;
2. restore `PlexonChats-4.0.0.jar`;
3. restore the pre-4.0.1 `data.yml` backup if you changed renderer/timing settings specifically for 4.0.1;
4. preserve `players.yml` and `chat-events.db` according to the operational restore point;
5. start the server and verify the 4.0.0 behavior/configuration you expect.

The authoritative source rollback tag/SHA is `v4.0.0` / `657d27766bce60235bc50602b4dd4d14b9e6998c`.
