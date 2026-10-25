package com.antondev.chats.protocol;

import java.util.LinkedHashMap;
import java.util.UUID;

/** Fail-closed bounded TTL set. Never evict a live ID to admit a replay during a flood. */
public final class RecentIds {
    private final int capacity;
    private final long ttl;
    private final LinkedHashMap<UUID,Long> entries=new LinkedHashMap<>();
    public RecentIds(int capacity,long ttl) {if(capacity<1||ttl<Protocol.MAX_AGE_MS+Protocol.FUTURE_SKEW_MS)throw new IllegalArgumentException();this.capacity=capacity;this.ttl=ttl;}
    public synchronized boolean add(UUID id,long now) {cleanup(now);if(entries.containsKey(id)||entries.size()>=capacity)return false;entries.put(id,now);return true;}
    public synchronized void cleanup(long now) {var iterator=entries.entrySet().iterator();while(iterator.hasNext()){var entry=iterator.next();if(now-entry.getValue()<ttl)break;iterator.remove();}}
    public synchronized int size(){return entries.size();}
    public synchronized void clear(){entries.clear();}
}
