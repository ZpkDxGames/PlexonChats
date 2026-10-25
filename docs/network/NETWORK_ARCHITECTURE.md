# Network architecture decision

Paper remains chat authority. LOCAL and GLOBAL never enter transport. UNIVERSAL
runs existing permission, limiter, placeholders and synchronous cancellable API
moderation once; proxy acceptance commits the origin delivery once. Remote
receives are presentation only and cannot enter Chat Events, Bingo, Discord,
commands, mentions or APIs. Discord policy remains GLOBAL only.

Transport: `plexonchats:network`, version 1, bounded binary envelopes signed with
HMAC-SHA256 using separate backend keys. Velocity marks matching messages handled
before inspecting source; accepts only current ServerConnection traffic, validates
registered allowlist, origin, carrier UUID and claimed author association.
Each destination receives one freshly signed packet. Empty backends are skipped.
No persisted history, no total ordering, no blocking socket or DB operations.

Use existing synchronous native listener and one batching bridge tick per plugin.
Caches, queues, health leases and pending acceptance callbacks have hard limits.
Reload closes old transport, clears transient work and starts a new handshake.
No production configuration, account authentication or routing change is made.

Official API sources checked 2026-10-25:
- https://docs.papermc.io/velocity/dev/plugin-messaging/
- https://docs.papermc.io/paper/dev/chat-events/
- https://docs.papermc.io/paper/dev/project-setup/
- https://docs.papermc.io/velocity/dev/creating-your-first-plugin/
- Published Maven metadata: Paper 26.3.build.223-stable; Velocity API 4.3.0.
