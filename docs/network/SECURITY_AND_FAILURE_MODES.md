# Security and failure model

Trusted: this Paper plugin, companion, backend service accounts, operator-created
per-backend key files and protected backend connections. Untrusted: clients,
player text/names/UUID claims, malformed plugin messages and external services.
A compromised backend/key can author chat as players actually on that backend;
this plugin is not a remedy for a compromised server. No message grants a
permission or claims Mojang-signed chat. Remote copies are system/decorated chat.

Velocity claims the channel before source checks, including when disabled or
misconfigured. Client-origin payloads cannot be forwarded to Paper or reflected.
Only ServerConnection sources whose connection is still current can submit.
Relay additionally validates registered backend allowlist, signed origin,
carrier UUID=author UUID, username, current player/server association and lease.

Paper requires its independent destination HMAC and generation UUID before
queueing display. No username/offline UUID is a proof of identity. Dedicated
32-byte keys are unrelated to Velocity forwarding.secret, PlexonPanel or Discord.
Key files are operator-owned, never generated into a committed config or logged;
examples contain filenames only. Direct client connections lack the key, but
existing protected backend network/forwarding boundaries must remain intact.
Do not change account authentication, routing or listeners for this feature.

| Condition | Behavior |
|---|---|
| Network disabled/key missing/no companion/wrong version | U unavailable; L/G/PM/events remain independent |
| No online player on origin | NO_CARRIER; no history queued |
| Empty destination | Skip; no audience exists |
| Occupied destination without healthy companion | PARTIAL warning; skip this destination |
| Slow destination/failed platform send | Bounded queues, drop metrics, no retry/history |
| Origin moves/disconnects before relay drain | Reject; no remote submission from old connection |
| Recipient switches | Fixed per-destination snapshot excludes original viewers; may miss a copy |
| Lost ACK after fanout | Origin warns delivery unconfirmed; remote copies may exist; no automatic retry |
| Bad signature/length/UTF-8/control/version/type/server/session | Reject, increment metrics; no display or side effects |
| Duplicate UUID/reentrant packet | Suppress; no reward/event/Discord path |
| Queue/cache saturation | Fail closed; no live-ID eviction, no unbounded memory growth |
| Reload/disable | Close old worker/channels, clear queues/leases; pending origin sends warn |
| Invalid config reload | Previous config revision/runtime retained |

Diagnostics: `/chat diagnostics` or `/chat network status` requires
`plexonchats.manage`; proxy `/plexonchatsbridge` requires
`plexonchats.bridge.manage`. Show state/protocol, queues, drops, rejected and
duplicate counters. No key material, webhook or forwarding secret is printed.

Bounds: at most16 backend keys/leases,1024 queued origin/proxy/inbound entries,
8192 seen IDs,24KB raw packet,8KB body,512 UUID audience. Paper pending callbacks
are bounded by queue capacity and expire after 4s. No per-recipient network task,
HTTP/socket listener, Redis service, database operation or blocking external I/O
is introduced on the chat path. Key reads occur only at startup/config reload.

Production security, performance, source routing and real protocol interoperation
remain pending staging certification. Unit/MockBukkit tests are not live proof.
