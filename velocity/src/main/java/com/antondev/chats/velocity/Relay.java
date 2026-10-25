package com.antondev.chats.velocity;

import com.antondev.chats.protocol.*;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

/** Platform-independent relay state machine; all entry points serialized, fanout bounded to allowlist. */
public final class Relay implements AutoCloseable {
    public interface Transport {
        boolean current(String backend,UUID carrier,UUID author,String name);
        Set<UUID> audience(String backend);
        boolean send(String backend,byte[] data);
    }
    private record Lease(UUID session,long seen) {}
    private final Map<String,byte[]> keys;
    private final Transport transport;
    private final Map<String,Lease> leases=new HashMap<>();
    private final Map<String,Long> helloTimes=new HashMap<>();
    private record Work(String origin,UUID carrier,Envelope message) {}
    private final ArrayBlockingQueue<Work> queue;
    private final RecentIds ids=new RecentIds(8192,60_000);
    private long rejected,duplicates,drops,accepted,delivered,incompatible;
    private boolean closed;
    public Relay(Map<String,byte[]> keys,Transport transport,int capacity) {
        if(keys.isEmpty()||keys.size()>16||capacity<1||capacity>1024)throw new IllegalArgumentException();
        Map<String,byte[]> copy=new HashMap<>();keys.forEach((id,key)->{if(!Protocol.validServer(id)||key.length!=32)throw new IllegalArgumentException();copy.put(id,key.clone());});
        this.keys=Map.copyOf(copy);this.transport=transport;queue=new ArrayBlockingQueue<>(capacity);
    }
    public synchronized void receive(String actualBackend,UUID carrier,byte[] data,long now) {
        if(closed)return;
        try {
            byte[] key=keys.get(actualBackend);if(key==null)throw new IllegalArgumentException();
            Envelope e=Protocol.decode(data,key,now);
            if(!actualBackend.equals(e.server()) || !transport.current(actualBackend,carrier,e.author(),e.name()))throw new IllegalArgumentException();
            if(e.type()==Envelope.Type.HELLO) {
                if(now-helloTimes.getOrDefault(actualBackend,Long.MIN_VALUE/2)<1000){drops++;return;}
                helloTimes.put(actualBackend,now);
                leases.put(actualBackend,new Lease(e.session(),now));
                if(!transport.send(actualBackend,Protocol.encode(e.withType(Envelope.Type.READY,e.session()),key)))drops++;
                return;
            }
            Lease lease=leases.get(actualBackend);
            if(e.type()!=Envelope.Type.SUBMIT || lease==null || now-lease.seen()>10_000 || !lease.session().equals(e.session()) || e.body().isBlank())throw new IllegalArgumentException();
            if(!ids.add(e.id(),now)){duplicates++;return;}
            if(!queue.offer(new Work(actualBackend,carrier,e))){drops++;reject(actualBackend,e,"BUSY");}
        } catch(IllegalArgumentException ex){rejected++;if("Incompatible protocol".equals(ex.getMessage()))incompatible++;}
    }
    public synchronized void drain(long now) {
        if(closed)return;
        ids.cleanup(now);
        for(int i=0;i<64;i++) {
            Work work=queue.poll();if(work==null)break;
            Envelope e=work.message();Lease origin=leases.get(work.origin());
            if(now-e.createdAt()>Protocol.MAX_AGE_MS || origin==null || !origin.session().equals(e.session()) || !transport.current(work.origin(),work.carrier(),e.author(),e.name())) {drops++;reject(work.origin(),e,"MOVED_OR_EXPIRED");continue;}
            boolean partial=false;
            for(String target:new TreeSet<>(keys.keySet())) {
                if(target.equals(work.origin()))continue;
                Set<UUID> audience=new HashSet<>(transport.audience(target));
                // Snapshot membership and exclude every initial origin viewer: server switching may lose a live
                // message, but must never show the origin copy plus a destination copy to the same player.
                if(e.restricted())audience.clear();
                else if(e.audience()!=null)audience.removeAll(e.audience());
                if(audience.isEmpty())continue;
                if(audience.size()>Protocol.MAX_AUDIENCE){partial=true;drops++;continue;}
                Lease destination=leases.get(target);
                if(destination==null||now-destination.seen()>10_000){partial=true;drops++;continue;}
                if(transport.send(target,Protocol.encode(new Envelope(Envelope.Type.DELIVER,e.id(),destination.session(),e.createdAt(),e.server(),e.author(),e.name(),e.display(),e.rank(),e.body(),new ArrayList<>(audience),false),keys.get(target))))delivered++;
                else {partial=true;drops++;}
            }
            Envelope ack=new Envelope(Envelope.Type.ACCEPT,e.id(),e.session(),now,e.server(),e.author(),e.name(),"","",partial?"PARTIAL":"ACCEPTED",null,false);
            if(!transport.send(work.origin(),Protocol.encode(ack,keys.get(work.origin()))))drops++;
            accepted++;
        }
    }
    private void reject(String origin,Envelope e,String reason) {
        transport.send(origin,Protocol.encode(new Envelope(Envelope.Type.REJECT,e.id(),e.session(),e.createdAt(),origin,e.author(),e.name(),"","",reason,null,false),keys.get(origin)));
    }
    public synchronized String status(){return "protocol=1 queue="+queue.size()+" accepted="+accepted+" backendSends="+delivered+" rejected="+rejected+" duplicates="+duplicates+" drops="+drops+" incompatible="+incompatible;}
    public synchronized int pending(){return queue.size();}
    public synchronized void close(){closed=true;queue.clear();ids.clear();leases.clear();helloTimes.clear();}
}
