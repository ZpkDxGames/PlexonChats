package com.antondev.chats.velocity;

import com.antondev.chats.protocol.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RelayTest {
    private static final long NOW=100_000;
    private final byte[] key=new byte[32];
    private final Map<String,UUID> players=new LinkedHashMap<>(),sessions=new HashMap<>();
    private final Map<String,List<Envelope>> received=new HashMap<>();
    private boolean online=true,sendWorks=true;
    private Relay relay(int capacity){
        for(String server:List.of("lobby","smp","towny")){players.put(server,UUID.randomUUID());sessions.put(server,UUID.randomUUID());received.put(server,new ArrayList<>());}
        return new Relay(Map.of("lobby",key,"smp",key,"towny",key),new Relay.Transport(){
            public boolean current(String backend,UUID carrier,UUID author,String name){return online&&Objects.equals(players.get(backend),author)&&carrier.equals(author)&&name.equals("Tonim");}
            public Set<UUID> audience(String backend){return players.get(backend)==null?Set.of():Set.of(players.get(backend));}
            public boolean send(String backend,byte[] data){if(!sendWorks)return false;received.get(backend).add(Protocol.decode(data,key,NOW));return true;}
        },capacity);
    }
    private Envelope packet(String server,Envelope.Type type){return new Envelope(type,UUID.randomUUID(),sessions.get(server),NOW,server,players.get(server),"Tonim","§bTonim","§6Owner","hello",List.of(players.get(server)),false);}
    private void ready(Relay r){for(String server:players.keySet())r.receive(server,players.get(server),Protocol.encode(packet(server,Envelope.Type.HELLO),key),NOW);received.values().forEach(List::clear);}
    @Test void allThreeOriginsFanOutExactlyOnceAndAckOnlyAtOrigin(){for(String origin:List.of("lobby","smp","towny")){Relay r=relay(256);ready(r);Envelope e=packet(origin,Envelope.Type.SUBMIT);byte[] data=Protocol.encode(e,key);r.receive(origin,players.get(origin),data,NOW);r.receive(origin,players.get(origin),data,NOW);r.drain(NOW);for(String target:players.keySet()){assertEquals(1,received.get(target).size());Envelope delivered=received.get(target).getFirst();assertEquals(origin,delivered.server());assertEquals(origin.equals(target)?Envelope.Type.ACCEPT:Envelope.Type.DELIVER,delivered.type());if(!target.equals(origin)){assertEquals(sessions.get(target),delivered.session());assertEquals(List.of(players.get(target)),delivered.audience());}}r.close();}}
    @Test void forgedBackendPlayerNameAndCarrierRejected(){Relay r=relay(256);ready(r);Envelope e=packet("lobby",Envelope.Type.SUBMIT);r.receive("smp",players.get("smp"),Protocol.encode(e,key),NOW);r.receive("lobby",UUID.randomUUID(),Protocol.encode(e,key),NOW);online=false;r.receive("lobby",players.get("lobby"),Protocol.encode(e,key),NOW);r.drain(NOW);assertTrue(received.values().stream().allMatch(List::isEmpty));}
    @Test void queueSaturationIsBoundedAndRejectsSender(){Relay r=relay(1);ready(r);for(int i=0;i<100;i++)r.receive("lobby",players.get("lobby"),Protocol.encode(packet("lobby",Envelope.Type.SUBMIT),key),NOW);assertEquals(1,r.pending());assertEquals(99,received.get("lobby").size());r.drain(NOW);assertEquals(0,r.pending());assertEquals(1,received.get("smp").size());}
    @Test void noHandshakeAndExpiredHealthCannotSubmit(){Relay r=relay(256);Envelope e=packet("lobby",Envelope.Type.SUBMIT);r.receive("lobby",e.author(),Protocol.encode(e,key),NOW);assertEquals(0,r.pending());ready(r);e=packet("lobby",Envelope.Type.SUBMIT);r.receive("lobby",e.author(),Protocol.encode(e,key),NOW+10_001);assertEquals(0,r.pending());}
    @Test void disconnectBetweenEnqueueAndDrainCannotFanOut(){Relay r=relay(256);ready(r);Envelope e=packet("lobby",Envelope.Type.SUBMIT);r.receive("lobby",e.author(),Protocol.encode(e,key),NOW);online=false;r.drain(NOW);assertTrue(received.get("smp").isEmpty());assertEquals(Envelope.Type.REJECT,received.get("lobby").getFirst().type());}
    @Test void restrictedModerationDoesNotExpandRemoteViewers(){Relay r=relay(256);ready(r);Envelope e=packet("lobby",Envelope.Type.SUBMIT);e=new Envelope(e.type(),e.id(),e.session(),e.createdAt(),e.server(),e.author(),e.name(),e.display(),e.rank(),e.body(),e.audience(),true);r.receive("lobby",e.author(),Protocol.encode(e,key),NOW);r.drain(NOW);assertTrue(received.get("smp").isEmpty());assertTrue(received.get("towny").isEmpty());}
    @Test void originViewerSwitchIsExcludedAtRemoteSnapshot(){Relay r=relay(256);ready(r);Envelope e=packet("lobby",Envelope.Type.SUBMIT);UUID switching=players.get("smp");e=new Envelope(e.type(),e.id(),e.session(),e.createdAt(),e.server(),e.author(),e.name(),e.display(),e.rank(),e.body(),List.of(e.author(),switching),false);r.receive("lobby",e.author(),Protocol.encode(e,key),NOW);r.drain(NOW);assertTrue(received.get("smp").isEmpty());assertEquals(1,received.get("towny").size());}
    @Test void reloadAndQueueExpiryDropOldGeneration(){Relay r=relay(256);ready(r);Envelope e=packet("lobby",Envelope.Type.SUBMIT);r.receive("lobby",e.author(),Protocol.encode(e,key),NOW);r.drain(NOW+Protocol.MAX_AGE_MS+1);assertTrue(received.get("smp").isEmpty());r.close();assertEquals(0,r.pending());r.receive("lobby",e.author(),Protocol.encode(e,key),NOW);assertEquals(0,r.pending());}
    @Test void emptyDestinationIsSkippedWithoutQueueingHistory(){Relay r=relay(256);ready(r);Envelope e=packet("lobby",Envelope.Type.SUBMIT);players.put("towny",null);r.receive("lobby",e.author(),Protocol.encode(e,key),NOW);r.drain(NOW);assertTrue(received.get("towny").isEmpty());assertEquals("ACCEPTED",received.get("lobby").getFirst().body());}
}
