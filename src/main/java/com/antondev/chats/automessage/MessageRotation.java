package com.antondev.chats.automessage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/** Every enabled entry appears once per cycle. Preview never consumes an entry. */
public final class MessageRotation<T> {
    private final List<T> entries;
    private final boolean shuffle;
    private final Random random;
    private final Deque<T> remaining = new ArrayDeque<>();
    private T last;
    public MessageRotation(List<T> entries, boolean shuffle, Random random) {
        this.entries = List.copyOf(entries);
        this.shuffle = shuffle;
        this.random = random;
    }
    private void refill() {
        if (!remaining.isEmpty() || entries.isEmpty()) return;
        List<T> next = new ArrayList<>(entries);
        if (shuffle) {
            Collections.shuffle(next, random);
            if (next.size() > 1 && next.getFirst().equals(last)) {
                Collections.swap(next, 0, 1 + random.nextInt(next.size() - 1));
            }
        }
        remaining.addAll(next);
    }
    public T peek() { refill(); return remaining.peekFirst(); }
    public T next() { refill(); T value = remaining.pollFirst(); if (value != null) last = value; return value; }
}
