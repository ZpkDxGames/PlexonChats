# Channel and API semantics — 4.1 candidate

| Channel | Audience | Native answers | Discord | Shortcut |
|---|---|---|---|---|
| LOCAL | Same backend/world, squared distance <= configured radius² (100 default) | Existing accepted-channel rules | Never | `/l`, existing configurable prefix |
| GLOBAL | Same backend, every world, no distance limit | Existing accepted-channel rules | Existing GLOBAL bridge only | `/g`, `!` default |
| UNIVERSAL | Eligible live players across allowlisted backends | Never, even if added to an event definition | Never in this version | `/u`, prefix disabled by default |

`/u` selects; `/u text` sends once without changing selection, matching `/g` and
`/l`. `/chat channel universal` and `/chat universal` select the new channel.
`plexonchats.universal` defaults true. `channels.universal.receive-permission`
controls viewing independently. Existing default-channel and saved LOCAL/GLOBAL
preferences are retained. All three channels still enter one ChatManager route.

Paper's synchronous ChatEvent preserves earlier cancellation, message edits and
native viewer restrictions. PlexonChatEvent remains synchronous/cancellable and
fires once before submission. Its message edits are serialized after moderation.
Post-event recipients are rechecked against native viewers, receive permissions
and LOCAL bounds. For Universal, any removed native/API viewer makes the packet
restricted: only the remaining origin viewers may see it; remote delivery is
suppressed. A backend cannot infer remote recipients of another plugin's local
moderation decision, so restrictions must never be expanded across the network.

An accepted Universal message commits its local copy after the proxy acknowledges
fanout attempts. No companion/health, invalid payload, full queue or rejected send
produces no local success echo. Timeout is *indeterminate*: remote players may
already have seen it if the acknowledgement was lost. Partial fanout is reported.
No persistence, retry, delayed join history or global total ordering is promised.

Remote fields carry color/decorations and text only. Source labels are locally
configured by routing ID. Remote display has no remote-controlled clicks, hovers,
local PM suggestion or item-preview tokens. Origin interactions and existing
LOCAL/GLOBAL formatting stay intact. Private messages/replies remain backend-local.
Remote receives never invoke native/API events, mention alerts, console logging,
Discord, answer recognition, Bingo, rewards or statistics.

Public API method descriptors and existing enum ordinals are unchanged. UNIVERSAL
is appended to ChatChannel. Consumers using exhaustive enum switches need a new
case/default; the new constant cannot preserve such assumptions. Existing GLOBAL
Discord event flags remain GLOBAL-only. No cross-server preference sync is added.
