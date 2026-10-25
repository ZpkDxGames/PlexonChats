package com.antondev.chats.protocol;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProtocolTest {
    private final byte[] key=new byte[32];private final long now=1_000_000;
    private Envelope message(String body){return new Envelope(Envelope.Type.SUBMIT,UUID.randomUUID(),UUID.randomUUID(),now,"lobby",UUID.randomUUID(),"Tonim","§bTonim","§6Owner ",body,List.of(UUID.randomUUID()),false);}
    @Test void roundTripUnicodeAndBoundedDecorations(){Envelope e=message("§bHello 🌍 <click:run_command:'/op x'>literal</click>");assertEquals(e,Protocol.decode(Protocol.encode(e,key),key,now));}
    @Test void wrongKeyAndModifiedSignatureRejected(){byte[] data=Protocol.encode(message("hi"),key);byte[] other=key.clone();other[0]=1;assertThrows(IllegalArgumentException.class,()->Protocol.decode(data,other,now));data[12]^=1;assertThrows(IllegalArgumentException.class,()->Protocol.decode(data,key,now));}
    @Test void oversizedAndTruncatedPacketsRejected(){assertThrows(IllegalArgumentException.class,()->Protocol.encode(message("x".repeat(8193)),key));assertThrows(IllegalArgumentException.class,()->Protocol.decode(new byte[24001],key,now));assertThrows(IllegalArgumentException.class,()->Protocol.decode(new byte[12],key,now));}
    @Test void freshFutureAndStaleBoundaries(){byte[] data=Protocol.encode(message("hi"),key);assertNotNull(Protocol.decode(data,key,now+Protocol.MAX_AGE_MS));assertThrows(IllegalArgumentException.class,()->Protocol.decode(data,key,now+Protocol.MAX_AGE_MS+1));assertThrows(IllegalArgumentException.class,()->Protocol.decode(data,key,now-Protocol.FUTURE_SKEW_MS-1));}
    @Test void newlineControlsInvalidUnicodeAndBidiRejected(){for(String body:List.of("a\nb","a\rb","\0","\u202eabc","\ud800"))assertThrows(IllegalArgumentException.class,()->Protocol.encode(message(body),key));}
    @Test void dedupeNeverEvictsLiveEntriesUnderFlood(){RecentIds ids=new RecentIds(2,60_000);UUID first=UUID.randomUUID();assertTrue(ids.add(first,now));assertFalse(ids.add(first,now));assertTrue(ids.add(UUID.randomUUID(),now));assertFalse(ids.add(UUID.randomUUID(),now));assertFalse(ids.add(first,now+30_000));assertEquals(2,ids.size());assertTrue(ids.add(first,now+60_000));}
    @Test void nullAndEmptyAudienceAreDistinct(){Envelope e=message("hi");Envelope all=new Envelope(e.type(),e.id(),e.session(),now,e.server(),e.author(),e.name(),e.display(),e.rank(),e.body(),null,true);assertNull(Protocol.decode(Protocol.encode(all,key),key,now).audience());assertEquals(List.of(),Protocol.decode(Protocol.encode(new Envelope(e.type(),e.id(),e.session(),now,e.server(),e.author(),e.name(),"","","hi",List.of(),true),key),key,now).audience());}
    @Test void excessiveAudienceRejected(){Envelope e=message("hi");var crowd=Collections.nCopies(513,UUID.randomUUID());assertThrows(IllegalArgumentException.class,()->Protocol.encode(new Envelope(e.type(),e.id(),e.session(),now,e.server(),e.author(),e.name(),"","","hi",crowd,false),key));}
    @Test void malformedAuthenticatedHeaderFieldsRejected()throws Exception {
        byte[] good=Protocol.encode(message("hello"),key);
        for(int offset:new int[]{0,4,6}){byte[] data=good.clone();data[offset]=(byte)255;resign(data);assertThrows(IllegalArgumentException.class,()->Protocol.decode(data,key,now));}
        byte[] trailing=Arrays.copyOf(good,good.length+1);resign(trailing);assertThrows(IllegalArgumentException.class,()->Protocol.decode(trailing,key,now));
    }
    @Test void invalidUtf8IsRejectedEvenWithValidMac()throws Exception {
        byte[] data=Protocol.encode(message("hello"),key);
        // Locate the final body bytes in this deterministic framing rather than relying on a fixed header size.
        for(int i=0;i<data.length-5-32;i++)if(data[i]=='h'&&data[i+1]=='e'&&data[i+2]=='l'){data[i]=(byte)0xc0;break;}
        resign(data);assertThrows(IllegalArgumentException.class,()->Protocol.decode(data,key,now));
    }
    private void resign(byte[] data)throws Exception {var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec(key,"HmacSHA256"));byte[] hash=mac.doFinal(Arrays.copyOf(data,data.length-32));System.arraycopy(hash,0,data,data.length-32,32);}
}
