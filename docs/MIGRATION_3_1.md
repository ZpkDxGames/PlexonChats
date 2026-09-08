# Migrating PlexonChats 3.0 to 3.1.0

PlexonChats 3.1.0 is a conservative PlexonCore/platform migration. It does not require a configuration reset or player-data migration.

## Before upgrading

1. Stop the server.
2. Back up `PlexonChats-3.0.jar`.
3. Back up the entire `plugins/PlexonChats/` directory.
4. Keep the existing `config.yml` and `players.yml`.

Do not delete or regenerate the PlexonChats data folder.

## Install

Replace only:

```text
PlexonChats-3.0.jar
```

with:

```text
PlexonChats-3.1.0.jar
```

PlexonCore 1.0.0 is recommended for Core-native operation but remains optional at runtime. Without PlexonCore, PlexonChats starts in standalone compatibility mode.

The target server platform is Paper 26.2 with Java 25.

## Preserved data and behavior

The following remain authoritative and compatible across this upgrade:

- `config.yml`
- `players.yml`
- configured LOCAL/GLOBAL channel behavior and radius
- shortcut prefixes
- chat and PM formats
- GUI layouts/actions
- chat components
- join/quit formats
- customized auto-message groups and content
- sounds and audience filters
- DiscordSRV settings
- player selected channel
- mention preference
- tip preference
- private-message preference

The 3.1.0 migration does not move preferences to PlexonCore or a database.

## Validate after startup

Run:

```text
/plexon modules
/plexon diagnostics
/chat diagnostics
/chat status
```

With compatible PlexonCore present, expect:

```text
PlexonChats — READY
Mode: CORE
Core API: 1.0.x
```

Then test:

1. one normal global message
2. one local message with near/far recipients
3. `/g <message>`
4. `/l <message>`
5. `/msg <player> <message>` and `/reply <message>`
6. `/chat gui`
7. one auto-message preview/manual send
8. Discord forwarding if enabled

Each logical public message must be delivered once and globally forwarded to Discord at most once. Local chat and PMs must never be forwarded to Discord.

## Reload validation

Run `/chat reload` repeatedly and verify there are no duplicate chat deliveries, Discord sends, auto-message tasks, preference-save tasks, or item-preview cleanup tasks.

Test one invalid staging configuration. A rejected candidate config must leave the previous live configuration and chat engine operational.

Run `/plexon reload` and then send chat immediately. PlexonChats must remain registered and must not duplicate listeners/tasks.

## Standalone validation

On staging, remove PlexonCore and restart PlexonChats. Verify chat, PMs, GUI, auto-messages, preferences, `PlexonChatEvent`, and `PlexonChatsAPI` still work without linkage errors.

## Rollback

If production validation fails:

1. stop the server
2. restore `PlexonChats-3.0.jar`
3. restore the backed-up `plugins/PlexonChats/` directory only if its data was actually damaged
4. start the server

3.1.0 intentionally avoids irreversible config or player-data migrations.
