# Configuration guide

Edit `plugins/PlexonChats/config.yml` using UTF-8 and spaces. Keep one copy of each top-level YAML key. Examples below are **partial sections to merge into your existing file**, not complete replacements.

Run `/chat reload` after editing. Invalid YAML, invalid section shapes, unsupported future config versions, and channel formats without `{message}` are rejected without replacing live settings. Numeric limits are clamped; invalid GUI buttons/message entries are skipped with warnings. A valid YAML file is not a guarantee that every material, action, or placeholder is meaningful—check the console and preview.

## Text, names, and placeholders

Administrator-owned templates use [MiniMessage](https://docs.papermc.io/adventure/minimessage/format/), including colors, gradients, and static click/hover tags. Built-in values and PlaceholderAPI results are inserted as components, not parsed again as MiniMessage. Do not embed dynamic placeholders inside a MiniMessage tag argument; use the separate `click.action` and `click.value` fields for dynamic commands/URLs.

| Placeholder | Meaning / scope |
| --- | --- |
| `{player}` | Interactive nickname in public channel formats; username in general templates |
| `{player_name}` | Actual username of the subject player |
| `{display_name}` | Display name with its colors; inherited click/hover interactions are removed |
| `{rank}`, `{rank_prefix}`, `{balance}` | Vault provider values; usable fallbacks when no provider is present |
| `{world}`, `{uuid}`, `{playtime}`, `{ping}` | Subject player information |
| `{online}`, `{max_players}`, `{server_name}`, `{version}` | Server/plugin information |
| `{channel_badge}`, `{separator}`, `{message}` | Configured rich components in a public chat layout |
| `{channel}`, `{channel_id}` | Display name/lowercase ID for the routed public channel |
| `{radius}`, `{global_shortcut}` | Configured local radius and global prefix |
| `{state}` | Rendered availability/toggle label in GUI buttons |
| `{discord_status}`, `{auto_status}`, `{auto_groups}` | Bridge state, scheduler state, and loaded group count |
| `%identifier_parameters%` | PlaceholderAPI token for the subject player, if the plugin/expansion is installed |

A chat hover resolves against its sender; a GUI or scheduled message resolves against its viewer. Console/global Discord announcements have no subject player: avoid player-specific tokens in messages you intend to forward there. Standard underscore-based PAPI token names are supported; unresolved tokens remain visible. PAPI/Vault colors accept `&`, `§`, `&#RRGGBB`, and `&x&R&R&G&G&B&B`.

### Channel layouts and nickname hover

```yaml
channels:
  local:
    radius: 100
    format: "{channel_badge} {rank_prefix}{player}{separator}<gray>{message}"
    receive-permission: ""
  global:
    format: "{channel_badge} {rank_prefix}{player}{separator}<white>{message}"
    shortcut-prefix: "!"
    receive-permission: ""

chat-components:
  player:
    name-format: "<white>{display_name}</white>"
    hover:
      enabled: true
      lines:
        - "<gold><bold>{player_name}</bold></gold>"
        - "<gray>Rank: <white>{rank}"
        - "<gray>World: <white>{world}"
        - "<gray>Playtime: <white>{playtime}"
    click:
      action: SUGGEST_COMMAND
      value: "/msg {player_name} "
  separator:
    format: " <dark_gray>» </dark_gray>"
```

Use `{player}`, not just `{player_name}`, in the channel layout if you want the configured nickname interactions. The same interactive nickname is used in PMs. Configure badge interactions under `chat-components.channel` and separator interactions under `chat-components.separator`.

Set `hover.enabled: false` to disable that configured hover, or `click.action: NONE` to disable its configured click action. Other actions: `SUGGEST_COMMAND`, `RUN_COMMAND`, `OPEN_URL`, `COPY_TO_CLIPBOARD`. Run commands must begin with `/`; links accept only HTTP(S) URLs.

Local delivery is same-world and within the configured radius. `plexonchats.local`/`plexonchats.global` control sending, not receiving; a non-empty `receive-permission` independently restricts recipients. The selected channel controls outgoing messages only. All public entry points share enabled/permission/rate-limit checks.

### Private messages, mentions, and items

- `private-messages.sent-format`: `{target}` and `{message}`.
- `private-messages.received-format`: `{sender}` and `{message}`.
- `mentions.format` controls highlighted mentions; `mentions.actionbar-message` and sound settings control alerts. Only recipients of the actual message are notified.
- `item-display.format` supports `{item_name}`, `{amount}`, and `{material}`. `[item]`/`@hand` insert the held item as a rich component; item hover and preview click can be independently disabled.
- `item-display.preview.expire-seconds` and `max-entries` bound stored item snapshots. Preview tokens expire and are not persistent across restarts.

Player messages are allowed colors/decorations/gradients only when the sender has `plexonchats.formatting`. Player-supplied command/hover tags additionally require `formatting.allow-advanced-player-tags: true` **and** `plexonchats.formatting.advanced`; keep these limited to trusted staff.

## Join, quit, and first-join messages

These settings edit the native Paper event message once; PlexonChats does not send a second broadcast or forward connection events through its chat bridge. DiscordSRV's own join/leave options remain independent.

```yaml
connection-messages:
  respect-hidden: true
  silent-permission: "plexonchats.connection.silent"
  join:
    mode: CUSTOM
    format: "<green>+</green> {player} <gray>joined • {online}/{max_players}"
    first-join-format: "<gold>Welcome {player_name} to {server_name}!"
  quit:
    mode: CUSTOM
    format: "<red>−</red> {player} <gray>left • {online}/{max_players}"
```

Modes are independent: `DEFAULT` keeps the existing event message, `CUSTOM` uses your template, and `HIDDEN` removes it. Both default to `DEFAULT`. An empty first-join format falls back to the regular join format.

`{player}` is the configured interactive nickname; `{player_name}` is the plain username. `{online}` reflects the player count **after** this join/quit. Other built-ins and PAPI values resolve against the player involved.

In CUSTOM mode, `respect-hidden: true` preserves a null message set by an earlier listener (for example, a vanish plugin). The configured silent permission also suppresses the custom message; it defaults to false even for operators. An empty permission string disables that check. Other plugins running later may still modify the final event, so test with your actual vanish/join plugins.

## GUI management

`gui`, `gui.admin`, and `gui.creator` each have a title, row count, and `items` map. Slots start at **0**, with 9 slots per row. Valid row counts are 1–6. Move all buttons when shrinking a page; out-of-range and duplicate slots are skipped rather than remapped.

Main-page example:

```yaml
gui:
  title: "<aqua>Chat settings"
  rows: 1
  items:
    local:
      slot: 1
      action: LOCAL
      material: WHITE_WOOL
      active-material: LIME_CONCRETE
      disabled-material: GRAY_DYE
      glow-when-active: true
      name: "<yellow>Nearby chat"
      lore: ["<gray>{radius} blocks", "{state}"]
    tips:
      slot: 4
      action: TOGGLE_TIPS
      material: PAPER
      name: "<gold>Tips"
      lore: ["{state}"]
    rules:
      slot: 7
      action: PLAYER_COMMAND
      value: "/rules"
      permission: "your.rules.permission"
      hide-without-permission: true
      material: BOOK
      name: "<white>Read the rules"
      lore: []
```

This example replaces the entire main `items` map; it does not disable the admin/creator pages. Use `items: {}` for an intentionally empty page.

| Actions | Behavior |
| --- | --- |
| `LOCAL`, `GLOBAL` | Select outgoing channel after checking permission and enabled state |
| `TOGGLE_MENTIONS`, `TOGGLE_TIPS`, `TOGGLE_PRIVATE_MESSAGES` | Save personal preferences |
| `OPEN_MAIN`, `OPEN_ADMIN`, `OPEN_CREATOR`, `CLOSE` | Navigate/close; admin remains permission-protected |
| `RELOAD`, `PREVIEW_FORMAT` | Validate/reload, or send local/global sample formats privately |
| `TOGGLE_AUTO_MESSAGES` | Persistently toggle the global scheduler flag in config.yml |
| `PLAYER_COMMAND` | Run `value` as the clicking player, with that player's permissions |
| `MESSAGE` | Send the rendered `value` privately to the player |
| `LINK` | Send a clickable HTTP(S) `value`; never open a URL automatically |
| `NONE` | Display-only status/information item |

Button `permission` adds a requirement; it cannot remove an action's built-in permission. `hide-without-permission` hides locked buttons instead of displaying their locked state. `gui.state.*` changes labels; filler/click sound/close-on-select are shared settings.

The admin menu provides scheduler on/off, format preview, reload, and status—not an in-game text editor for arbitrary YAML. Layouts and text remain in the file. Inventory transfer, drag, shift-click, hotbar swaps, and stale-view actions are blocked.

Player preferences are saved in `players.yml`, atomically and off-thread at the configured interval, plus a shutdown flush. A malformed preference file is left untouched to avoid data loss; repair it with the server stopped and restart.

## Scheduled auto-messages and tips

Groups are interval-based, not wall-clock/cron schedules. Each enabled group rotates its enabled entries independently.

```yaml
auto-messages:
  enabled: true
  groups:
    survival-tips:
      enabled: true
      interval-seconds: 300
      initial-delay-seconds: 30
      order: SHUFFLE
      min-online: 1
      respect-tip-preference: true
      permission: ""
      worlds: ["world", "world_nether"]
      excluded-worlds: []
      sound: NONE
      forward-to-discord: false
      messages:
        - id: welcome
          delivery: CHAT
          lines:
            - "<gold>Tip</gold> <gray>Hi {player_name}, protect your base!"
        - id: menu
          delivery: ACTION_BAR
          lines: ["<aqua>Use /chat gui to choose your chat settings."]
        - id: event
          delivery: TITLE
          fade-in-seconds: 0.5
          stay-seconds: 3.0
          fade-out-seconds: 0.5
          lines: ["<aqua>Server event", "<gray>Read chat for details"]
```

- `SEQUENTIAL` follows config order. `SHUFFLE` uses a shuffled bag and avoids an immediate repeat across cycles when there is more than one entry.
- `CHAT` sends every line; `ACTION_BAR` joins lines with spaces; `TITLE` uses the first two lines as title/subtitle.
- `min-online` counts all online players. Permission/world/tip-preference filters are then applied to delivery. Empty `worlds` means all worlds; exclusions win.
- `respect-tip-preference: true` allows the player's tips toggle to opt out. Set it false for mandatory notices.
- `sound` accepts a Bukkit sound name, a namespaced key, or `NONE`. Volume/pitch are configurable per group.
- Intervals are clamped to 10 seconds–7 days; initial delay to 1 second–7 days; up to 100 non-empty groups are loaded.
- Empty/no-eligible audiences do not consume the next message. Lag does not replay missed intervals in a burst.
- Reload/restart resets rotations and initial delays. Pause is temporary; resume waits one group interval. Manual `send` works while paused and resets that group's next due time.
- `preview` is private, silent, chat-only, and never changes the rotation, next due time, or Discord state.

Default tips run every 5 minutes after a 60-second initial delay; default announcements every 15 minutes after 180 seconds. Disable/edit these defaults before production if they do not suit your server.

## DiscordSRV setup

1. Install/configure DiscordSRV separately using its [official setup guide](https://docs.discordsrv.com/installation/initial-setup/). Keep bot credentials exclusively in DiscordSRV's configuration.
2. Map a **game channel key** in DiscordSRV's `Channels` setting, for example:

   ```yaml
   Channels: {"global": "YOUR_DISCORD_CHANNEL_ID"}
   ```

3. Enable the adapter in PlexonChats using that key—not the numeric Discord channel ID:

   ```yaml
   integrations:
     discordsrv:
       enabled: true
       game-channel: "global"
       minecraft-to-discord: true
       discord-to-minecraft: true
       incoming-format: "{message}"
       receive-permission: ""
       suppress-mentions: true
       forward-announcements: false
   ```

4. Restart after installing/changing DiscordSRV itself. PlexonChats-only setting changes use `/chat reload`. Check `/chat status` and test both directions on a staging server.

The adapter targets **DiscordSRV 1.30.5 (1.x)**. It uses DiscordSRV's own outgoing player processing and already-filtered incoming Minecraft component, preserving its account-linking/webhook/filter configuration where that pipeline applies. It does not replace DiscordSRV account linking, roles, bot setup, or permission configuration.

Only approved **global** player chat is forwarded. Local chat and PMs have no bridge path. With the adapter enabled, its listener suppresses native Paper/legacy chat forwarding to prevent duplicate or local-chat delivery. One mapped channel is owned by this adapter; other DiscordSRV channel mappings are left untouched. Other chat plugins that independently send messages to Discord need their own coordination.

Incoming messages use `{message}` for DiscordSRV's complete formatted component. Optional wrapper values are `{discord_name}`, `{discord_user}`, and `{discord_channel}`; `{message}` is **not** a raw message-body token. Both the global channel's receive permission and the Discord adapter's receive permission apply.

`suppress-mentions: true` neutralizes outgoing `@` sequences. Turning it off allows DiscordSRV's own mention policy to govern. Ordinary `/announce` messages are forwarded only if `forward-announcements` and outgoing forwarding are enabled. A scheduled group additionally needs `forward-to-discord: true`, `CHAT` delivery, and **no world, permission, or tip-opt-out filter**. Forwarded announcements use a bot message, not player webhook/account processing; never put private/player-specific data in them.

| Status | Meaning |
| --- | --- |
| `DISABLED` | Adapter disabled or closed |
| `NOT_INSTALLED` | DiscordSRV absent/disabled |
| `WAITING_FOR_DISCORD` | DiscordSRV not ready or JDA not connected |
| `CHANNEL_NOT_MAPPED` | No resolved destination for the configured game key |
| `ACTIVE` | Adapter sees a ready connection and mapped destination |
| `INCOMPATIBLE` | Adapter/API failed to load or an API call failed |

`ACTIVE` is a readiness check, not proof of Discord send permission or successful end-to-end delivery. DiscordSRV's own direction switches, filters, permissions, and account-link requirements can still block delivery. See its [configuration reference](https://docs.discordsrv.com/config/).

## Operational checks

Use `/chat admin` to inspect actual nickname hovers; test GUI controls as both an operator and a normal player. Operators bypass cooldown/duplicate protection by default. Use a non-operator to test `chat.cooldown-milliseconds` and `chat.duplicate-window-seconds` (0 disables either). The default maximum message length is 512.

After changes, test local radius/world boundaries, one global message in each direction, PM privacy, mention/tip opt-out, and a reload/restart. Details are in the [upgrade checklist](UPGRADING.md).
