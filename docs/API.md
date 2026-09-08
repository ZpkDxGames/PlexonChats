# PlexonChats API — 3.1.0

PlexonChats 3.1.0 exposes a stable Bukkit service API while preserving the existing `PlexonChatEvent` moderation contract.

## Service lookup

```java
RegisteredServiceProvider<PlexonChatsAPI> registration =
        Bukkit.getServicesManager().getRegistration(PlexonChatsAPI.class);
if (registration == null) {
    return;
}
PlexonChatsAPI chats = registration.getProvider();
```

The service type is:

```text
com.antondev.chats.api.PlexonChatsAPI
```

It is registered only after the chat engine, commands, listeners, scheduler, GUI, and supporting services are initialized. It is unregistered when PlexonChats disables.

## Read operations

The API provides:

```java
ChatChannel channel(Player player);
boolean canReceive(Player player, ChatChannel channel);
PlayerChatPreferencesView preferences(UUID playerId);
String discordStatus();
String autoMessageStatus();
Set<String> autoMessageGroups();
```

`PlayerChatPreferencesView` is an immutable snapshot. Internal preference maps, schedulers, GUI sessions, PM maps, and item-preview token maps are not exposed.

Read methods do not claim asynchronous safety. Callers must respect the Bukkit/threading boundary for the state they access.

## Controlled mutation operations

```java
boolean selectChannel(Player player, ChatChannel channel);
void sendPublic(Player sender, ChatChannel channel, String rawMessage);
boolean sendPrivate(Player sender, Player recipient, String rawMessage);
```

These operations must be called on the primary server thread. The implementation rejects asynchronous use.

`sendPublic` enters the same authoritative PlexonChats route used by normal public chat and `/g`/`/l`. It therefore performs the normal channel permission checks, message validation, recipient resolution, `PlexonChatEvent`, rendering, mentions, console logging, and optional global Discord forwarding.

`sendPrivate` uses the normal private-message route. It does not fire `PlexonChatEvent` and never forwards the message to Discord.

## PlexonChatEvent

The existing event remains at:

```text
com.antondev.chats.api.PlexonChatEvent
```

It is a synchronous, cancellable, primary-thread pre-delivery event for public chat. It covers normal public chat, `/g`, and `/l`; it does not cover private messages.

The event retains:

```java
getPlayer();
getChannel();
getRawMessage();
getMessage();
setMessage(...);
getRecipients();
isDiscordAllowed();
setDiscordAllowed(...);
isCancelled();
setCancelled(...);
```

The recipient set remains mutable so moderation integrations can add or remove recipients before delivery. Message mutation and cancellation are honored before final formatting and delivery.

`getRawMessage()` is the validated player-authored text entering PlexonChats processing; it is not formatted MiniMessage or the final rendered component.

Discord permission is meaningful only for global chat. Local chat cannot be bridged even if a consumer attempts to enable the flag.

## Event timing

The public route is:

```text
validate message
→ resolve recipients
→ process placeholders/chat components
→ fire PlexonChatEvent on the primary thread
→ apply cancellation/message/recipient/Discord changes
→ build final formatted component
→ deliver once
→ console log
→ mention notification
→ optional global Discord forwarding once
```

Paper's lower-priority moderation result is captured before PlexonChats takes ownership of `AsyncChatEvent`, including message edits, cancellation, and viewer restrictions.
