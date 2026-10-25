# PlexonChats network protocol v1

Channel `plexonchats:network`. Big-endian binary, HMAC-SHA256 suffix (32 bytes).
Paper and Velocity use the same protocol artifact; it contains no platform APIs.

Wire order:
1. magic `0x50434854` (u32), version `1` (u16), message type (u8), channel `1=UNIVERSAL` (u8);
2. random message/challenge UUID (two i64), destination/session-generation UUID (two i64), UTC creation millis (i64);
3. origin routing ID (u16 UTF-8 byte length + bytes, max32), author UUID;
4. author login name (max16 bytes), safe styled display (max1024), safe styled rank prefix (max1024), message (max8192);
5. restricted flag (boolean), audience count (i16: -1=null, 0=none, maximum512), UUIDs;
6. HMAC-SHA256 over all preceding bytes using the relevant backend's key.

Hard packet maximum24000 bytes including signature. Field lengths are checked
before allocation; UTF-8 is strict. Reject controls, CR/LF, bidi overrides/isolates,
invalid surrogates, unknown magic/version/type/channel, trailing bytes, timestamps
older than15000ms or more than5000ms in the future. Server IDs match
`[a-z0-9][a-z0-9_-]{0,31}`; player names match `[A-Za-z0-9_]{1,16}`.
This name policy matches the stated Java network; nonstandard username plugins
need a separately reviewed extension. Offline UUIDs are not authentication.

Types in ordinal order: HELLO=0, READY=1, SUBMIT=2, DELIVER=3, ACCEPT=4, REJECT=5.
- HELLO/READY: signed challenge echoed for the current Paper generation; heartbeat
  every3s, Paper lease8s, proxy backend lease10s. No negotiation downgrade: a v1
  mismatch is rejected and health never becomes READY. Clock sync is required.
- SUBMIT: only Paper->proxy, transmitted using the actual author's connection.
  Proxy checks that carrier, claimed author/name and current backend agree.
  Audience is an immutable snapshot of origin viewers. restricted=true suppresses
  remote fanout. GLOBAL/LOCAL and commands have no protocol representation.
- DELIVER: proxy->other Paper backends, signed with each destination's separate
  key and addressed to its latest session. Audience is a proxy membership snapshot,
  excluding every origin viewer. Receiving backend intersects it with current
  online players and current receive permissions. Never send back to origin.
- ACCEPT/REJECT: proxy->origin pending message ID/session/author; success commits
  one origin presentation, REJECT commits none. ACCEPT body `PARTIAL` reports an
  occupied unavailable destination; `ACCEPTED` means attempts accepted, not proof
  of every client's display. No end-client acknowledgement/history is claimed.

Presentation strings use Adventure legacy section color/decorations, never raw
component JSON/MiniMessage. Literal section characters and U+E000 are escaped by
NetworkText before serialization (`U+E0001` / `U+E0000`) and restored as component
text after decoding. Arbitrary clicks/hover/insertion/NBT are not transported.

Dedup is UUID-based, not text/name-based:8192 IDs per proxy/Paper generation,
60s TTL. A full cache rejects new IDs without evicting live replay protection.
Queues default256, configurable1..1024; drain at most64 messages per50ms proxy
batch / Paper tick. Origin acceptance timeout4s. All transient state clears at
reload/disable. Empty destination backends need no carrier and receive no history.

Best-effort live delivery is at-most-once per accepted message and captured
recipient within one active generation. Disconnect/switch/restart/acknowledgement
loss can drop copies; no atomic network-wide exactly-once guarantee is possible
with this transport. TCP order is retained within an origin's queue; no global
order across senders/backends is defined.
