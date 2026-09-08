# Upgrading to PlexonChats 3.0

Source baseline: `2.0-Update` commit `cefcdbe0679f75a1000d5401fb6a94fc86378e03`. The release branch is `3.0-Release`; `main` is not the release baseline.

## Before installing

1. Use a staging Paper 1.21.11 server on Java 25 first.
2. Stop the server. Back up the entire `plugins/PlexonChats/` folder and the existing JAR outside `plugins/`.
3. Replace the old JAR with `PlexonChats-3.0.jar`. Do not leave two versions in the plugin directory.
4. Start and inspect the console before inviting players back.

## Configuration migration

The schema is now `config-version: 3`. When loading a 2.0/unversioned config or schema-2 preview config, PlexonChats:

- Reads and validates the candidate before publishing it.
- Preserves explicitly configured values.
- Adds missing options and saves the original as `config-before-v3-<timestamp>.yml` before writing.
- Preserves an existing custom GUI item map or scheduled group map as a collection, including `{}`; it does not inject default buttons/groups into an intentionally customized map.

Migration/YAML serialization can change comments or whitespace. The untouched backup retains the original. Do not lower `config-version` just to force defaults back into a customized file. Compare against the [bundled defaults](../src/main/resources/config.yml) when adding features deliberately.

After migration, `/chat reload` accepts valid edits and keeps live settings if loading/validation fails. At initial startup there is no previous live config: an invalid config prevents the plugin from enabling until repaired.

## Visible changes to review

**Channel formats now work.** Version 2.0 stored channel formats but rendered a hardcoded layout. Version 3.0 preserves and renders your existing `channels.local.format`/`channels.global.format`. The saved format may look different from the old output.

For the new default rich layout, explicitly set:

```yaml
channels:
  local:
    format: "{channel_badge} {rank_prefix}{player}{separator}<gray>{message}"
  global:
    format: "{channel_badge} {rank_prefix}{player}{separator}<white>{message}"
```

`{player}` is only the interactive nickname; `{rank_prefix}` is separate. Literal `[L]`/`[G]` labels are valid but do not have the configured badge interactions. A layout must contain `{message}`.

**Item formats now work too.** Your saved `item-display.format` takes effect. The generated item component retains its own preview/hover events even inside a channel layout.

**GUI rows do not move buttons automatically.** If a legacy config has fewer than three rows, reposition newly added default buttons or choose three rows. Invalid/out-of-range slots are skipped with warnings. The main, admin, and creator pages are independently configurable.

**New scheduled defaults are enabled.** Review/disable `auto-messages.enabled` before production if the example tips/notices are not appropriate. Groups restart their initial delay and rotation on reload/restart. They are interval-based, not a cron scheduler.

**Preferences persist.** `players.yml` stores channel, mention, tip, and PM settings. Malformed files are not overwritten; repair them while stopped, then restart.

**Formatting permissions are tighter.** Players can use colors/gradients with `plexonchats.formatting`; arbitrary click/hover tags need a config opt-in and the advanced permission. Staff-only bypass permissions default to operators.

**Discord is opt-in.** Set up DiscordSRV's bot/mapping first, then enable `integrations.discordsrv`. Version 3.0 targets DiscordSRV 1.30.5; do not assume a future major API is compatible.

**Join/leave messages are configurable.** The 2.0 README listed this feature, but its committed source lacked the handler/config. Version 3.0 implements it under `connection-messages`. Both modes default to `DEFAULT`, preserving native behavior. Choose `CUSTOM` for templates or `HIDDEN` to suppress a message. Custom mode respects messages suppressed by earlier plugins.

**If you downloaded the withdrawn 1.1.0 preview:** replace that JAR with 3.0; do not install both. Schema-2 configs are migrated with a backup, preserving custom collections/preferences. PR #1 and its version numbering are superseded, not the source for this release's ancestry.

## Staging checklist

- [ ] Plugin loads without Vault, PAPI, or DiscordSRV.
- [ ] Actual server JDK is 25; server is the expected Paper version.
- [ ] Local chat respects world/radius and global chat reaches the intended recipients.
- [ ] `!`, `/g`, `/l`, and GUI switches all enforce channel permissions/enabled flags.
- [ ] Player hover, nickname click, channel badge, separator, PAPI values, and item preview look correct in a real client.
- [ ] GUI layout fits; dragging, shift-clicking, and number keys cannot take/insert items.
- [ ] Saved channel and opt-outs survive reconnect and a clean restart.
- [ ] A PM mention does not alert a third party; a blocked recipient requires the staff bypass.
- [ ] Normal-player cooldown/duplicate/length limits behave as intended.
- [ ] Tip preview does not broadcast or consume rotation; pause/resume, filters, action bar, and titles work.
- [ ] Repeated `/chat reload` does not duplicate timers/messages; an invalid config is rejected.
- [ ] Join/quit/first-join formats and online counts are correct; hidden players stay hidden.
- [ ] Discord receives each global message once, and never local chat or PMs.
- [ ] Discord-to-Minecraft sends once, honors receive permissions, and does not echo back.
- [ ] Discord reconnect/offline behavior is checked with the actual bot; no token is in PlexonChats config.
- [ ] If other moderation/chat plugins are installed, their native events and `PlexonChatEvent` integration are verified, especially for command-based chat.

Automated unit/MockBukkit/Discord API tests exercise these paths without a real Minecraft client or Discord login. They supplement, not replace, this checklist.

## Rollback

Stop the server, put the backed-up 2.0 JAR back, and restore the matching backed-up plugin folder/config. Keep the 3.0 files somewhere recoverable if you want to inspect or retry the upgrade. Do not run both versions, and do not downgrade a config marked for a future version in place.

Generated `target/` build output is no longer tracked in Git. Previous committed copies remain recoverable from Git history; Maven regenerates them.
