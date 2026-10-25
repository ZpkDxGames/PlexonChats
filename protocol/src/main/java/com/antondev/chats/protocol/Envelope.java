package com.antondev.chats.protocol;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable v1 data; null audience means all eligible recipients, empty means nobody. */
public record Envelope(Type type, UUID id, UUID session, long createdAt, String server,
                       UUID author, String name, String display, String rank, String body, List<UUID> audience, boolean restricted) {
    public enum Type { HELLO, READY, SUBMIT, DELIVER, ACCEPT, REJECT }
    public Envelope {
        Objects.requireNonNull(type); Objects.requireNonNull(id); Objects.requireNonNull(session);
        Objects.requireNonNull(server); Objects.requireNonNull(author); Objects.requireNonNull(name);
        Objects.requireNonNull(display); Objects.requireNonNull(rank); Objects.requireNonNull(body);
        if (audience != null) audience = List.copyOf(audience);
    }
    public Envelope withType(Type value, UUID targetSession) {
        return new Envelope(value, id, targetSession, createdAt, server, author, name, display, rank, body, audience, restricted);
    }
}
