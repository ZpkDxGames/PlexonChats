# Phase 3 — Chats Consolidation Audit

Status: AUDIT CHECKPOINT — NO STABLE PROMOTION

Baseline: `phase2/3.2.0-premium-communication` @ `2578e2900fc3fc1b40c8f1a735567388ee8f1e3d`

## Canonical ownership

PlexonChats remains authoritative for:

- chat formatting;
- global/local channel behavior;
- DiscordSRV bridge integration;
- social messaging only where already required by PlexonCraft.

The current candidate already declares:

- `/msg <player> <message>` with aliases `/tell` and `/w`;
- `/reply <message>` with alias `/r`;
- permission `plexonchats.tell`.

Therefore `/msg` and `/reply` are already first-party equivalents and require no new Phase 3 implementation.

## `/ignore` decision gate

`/ignore` is not currently declared by PlexonChats. The available PlexonCraft evidence does not establish an active Essentials `/ignore` workflow, MyCommand alias, or player-facing dependency that requires it.

Therefore `/ignore` is **DEFERRED**. Do not add it for Essentials parity.

Re-open this gate only if the live config/command/permission audit proves it is currently relied upon.

## AFK integration boundary

PlexonChats must never be the authoritative AFK state store.

At this checkpoint PlexonUtility AFK is deferred because no current consumer has been established. Accordingly, PlexonChats receives no AFK integration code in this branch.

If the Utility AFK gate later opens, Chats may consume a stable Utility API/event to render AFK state or announcements, but it must not maintain competing AFK timers or activity listeners.

## DiscordSRV

Current production boot evidence shows the PlexonChats DiscordSRV adapter is active. DiscordSRV integration remains in PlexonChats and is not part of any decommission plan.

## Social-message permission migration

Only migrate historical permission nodes that actually exist in the production LuckPerms export and whose workflow is still required.

| Historical/source node | Target |
|---|---|
| Essentials private-message send permission, if present | `plexonchats.tell` |
| Essentials reply permission, if present | `plexonchats.tell` |
| Essentials ignore permission | **do not migrate yet** — no Plexon `/ignore` implementation exists |

Do not infer exact old Essentials node names from memory; capture them from the live LuckPerms export before mutation.

## Command collision audit

Before retiring old aliases, inspect:

- Paper command map;
- MyCommand custom commands;
- GUIPlus aliases;
- `commands.yml`;
- other installed plugins that may claim `/msg`, `/tell`, `/w`, `/reply` or `/r`.

Essentials is absent from the current runtime evidence, but historical aliases/configuration may remain.

## Placeholder migration

PlexonChats should not manufacture compatibility placeholders for unused Essentials behavior.

Search all live chat/scoreboard/menu/Skript/MyCommand consumers for `%essentials_*%`. Replace only placeholders that have a first-party semantic equivalent. An AFK-related hit re-opens the Utility AFK decision gate rather than creating an AFK implementation inside Chats.

## Production actions requiring operator approval

- alias/command-owner changes;
- LuckPerms edits;
- any future `/ignore` enablement;
- any future AFK display integration;
- server reload/restart.

No production changes are performed by this Phase 3 audit commit.
