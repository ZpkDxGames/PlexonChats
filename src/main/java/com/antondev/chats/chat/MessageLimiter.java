package com.antondev.chats.chat;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Uses monotonic time; rejected attempts do not extend a player's cooldown. */
public final class MessageLimiter {
    private record Sent(long time, String text) {}
    public record Result(long remainingMillis, boolean duplicate) {}
    private final Map<UUID, Sent> sent = new HashMap<>();
    private final LongSupplier clock;
    public MessageLimiter(LongSupplier clock) { this.clock = clock; }
    public Result check(UUID player, String message, long cooldownMs, long duplicateMs) {
        long now = clock.getAsLong();
        String text = message.strip().toLowerCase(Locale.ROOT);
        Sent last = sent.get(player);
        if (last != null) {
            long elapsed = Math.max(0, (now - last.time()) / 1_000_000L);
            if (elapsed < cooldownMs) return new Result(cooldownMs - elapsed, false);
            if (elapsed < duplicateMs && text.equals(last.text())) return new Result(0, true);
        }
        sent.put(player, new Sent(now, text));
        return new Result(0, false);
    }
    public void remove(UUID player) { sent.remove(player); }
    public void clear() { sent.clear(); }
}
