# Upgrading PlexonChats

## 3.0 → 3.1.0

PlexonChats 3.1.0 is a Core/platform/reliability migration based on production commit `4bff64e1691e79f0199baace428ec4349be9400b` from `3.0-Release`. It does not require a config or player-data reset.

### Before installing

1. Use a staging **Paper 26.2 / Java 25** server first.
2. Stop the server.
3. Back up `PlexonChats-3.0.jar` and the entire `plugins/PlexonChats/` directory.
4. Replace only the JAR with `PlexonChats-3.1.0.jar`.
5. Keep the current `config.yml` and `players.yml`.
6. Start the server and inspect console/diagnostics before inviting players back.

PlexonCore 1.0.0 is recommended for Core-native operation but is still optional at runtime. When Core is unavailable or outside the supported API range `>=1.0 <2.0`, PlexonChats continues in standalone mode.

### Configuration and player data

The configuration remains schema 3. Existing customized channel formats, GUI layouts, PM formats, join/quit formats, auto-message groups/content, Discord settings, sounds, permissions, and world filters remain authoritative.

`players.yml` remains the player preference store and retains selected channel, mentions, tips, and private-message reception. A malformed `players.yml` is still not overwritten automatically.

Do not delete the data folder or copy bundled defaults over a live customized configuration.

### Validation

Run:

```text
/plexon modules
/plexon integrations
/plexon diagnostics
/chat status
/chat diagnostics
/chat gui
/chat admin
/chat automessages list
```

With compatible PlexonCore present, expect Core mode and module `PlexonChats — READY`, except that a configured optional integration that is temporarily unavailable can produce `DEGRADED` while chat remains operational.

Test one normal global message, one local message with near/far recipients, `/g`, `/l`, `/msg`, `/reply`, GUI toggles, one item preview, one scheduled-message group, and Discord if enabled. Every logical public message must route once; Discord must receive global chat at most once and must never receive local chat or PMs.

Run `/chat reload` repeatedly. There must still be one auto-message task, one preference-save task, one item-preview cleanup task, one effective listener set, and one Discord bridge. Test an invalid candidate configuration and verify the prior live configuration remains active.

Run `/plexon reload`, then immediately send chat and re-check `/plexon modules`. PlexonChats must remain operational without duplicate tasks/listeners.

For the standalone gate, remove PlexonCore on staging and restart. Public chat, local/global routing, PMs, GUI, auto-messages, preferences, `PlexonChatEvent`, and `PlexonChatsAPI` must still operate without linkage errors.

Restart staging at least twice and confirm preferences persist, config remains unchanged, Core/API registrations are single, and scheduled/Discord tasks are not duplicated.

See [MIGRATION_3_1.md](MIGRATION_3_1.md) for the focused live deployment/rollback procedure.

## Rollback from 3.1.0

1. Stop the server.
2. Restore `PlexonChats-3.0.jar`.
3. Restore the backed-up `plugins/PlexonChats/` directory only if its data was actually damaged.
4. Start the server and validate 3.0.

3.1.0 intentionally avoids irreversible configuration or player-data migrations.

---

## Historical 2.0 → 3.0 notes

Version 3.0 introduced configuration schema 3, configurable rich channel formats, persistent player preferences, scheduled-message groups, configurable GUI pages, `PlexonChatEvent`, connection messages, and optional DiscordSRV routing. Existing 2.0/unversioned configurations were conservatively upgraded with backups while preserving explicitly configured values and intentionally empty custom collections.

The main visible 2.0 → 3.0 difference was that stored channel/item formats began to be rendered rather than ignored by the old hardcoded output. Advanced MiniMessage player tags also became permission/config gated. Those 3.0 behaviors are preserved unchanged by 3.1.0.
