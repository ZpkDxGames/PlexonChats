import com.antondev.chats.protocol.*;
import java.util.*;

/** Isolated codec microbenchmark, not Paper MSPT/TPS or live-network certification. */
public final class ProtocolBenchmark {
    public static void main(String[] args) {
        byte[] key=new byte[32];UUID author=UUID.randomUUID(),session=UUID.randomUUID();
        int count=20_000;long bytes=0;long[] duration=new long[count];
        for(int i=-2000;i<count;i++){
            long now=System.currentTimeMillis();
            Envelope e=new Envelope(Envelope.Type.SUBMIT,UUID.randomUUID(),session,now,"lobby",author,"Tonim","§bTonim","§6Owner ","Hello 🌍 ".repeat(50),List.of(author),false);
            long start=System.nanoTime();byte[] data=Protocol.encode(e,key);Envelope result=Protocol.decode(data,key,now);
            if(!result.equals(e))throw new AssertionError();
            if(i>=0){duration[i]=System.nanoTime()-start;bytes+=data.length;}
        }
        Arrays.sort(duration);long sum=0;for(long value:duration)sum+=value;
        System.out.printf(Locale.ROOT,"codec_round_trips=%d mean_us=%.2f p95_us=%.2f p99_us=%.2f mean_bytes=%d%n",count,sum/(count*1000.0),duration[(int)(count*.95)]/1000.0,duration[(int)(count*.99)]/1000.0,bytes/count);
    }
}
