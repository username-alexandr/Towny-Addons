package cc.crafto.ui;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static cc.crafto.ui.Protocol.*;

class ProtocolTest {
    private byte[] frame(String raw){byte[] json=raw.getBytes(StandardCharsets.UTF_8);return ByteBuffer.allocate(6+json.length).put((byte)1).put((byte)0).putInt(json.length).put(json).array();}
    @Test void allMessagesRoundTripWithCyrillic(){for(Kind kind:Kind.values()){JsonObject body=object("text","Профиль — Александр","n",42,"ok",true);Message read=decode(encode(kind,body));assertEquals(kind,read.kind());assertEquals(body,read.body());}}
    @Test void bigEndianBukkitFrameMatchesCodec(){byte[] b=encode(Kind.HELLO,object("min",1,"max",1));assertEquals(1,b[0]);assertEquals(0,b[1]);assertEquals(b.length-6,ByteBuffer.wrap(b,2,4).getInt());}
    @Test void invalidHeaderAndLengthRejected(){byte[] good=encode(Kind.ACTION,object("component","button"));for(int n=0;n<good.length;n++){byte[] shortFrame=Arrays.copyOf(good,n);assertThrows(IllegalArgumentException.class,()->decode(shortFrame));}byte[] unknown=good.clone();unknown[1]=99;assertThrows(IllegalArgumentException.class,()->decode(unknown));good[0]=2;assertThrows(IllegalArgumentException.class,()->decode(good));}
    @Test void trailingJsonAndInvalidUtf8Rejected(){assertThrows(IllegalArgumentException.class,()->decode(frame("{}{}")));assertThrows(IllegalArgumentException.class,()->decode(frame("{a:1}")));byte[] malformed=frame("{\"a\":\"x\"}");malformed[malformed.length-3]=(byte)0xFF;assertThrows(IllegalArgumentException.class,()->decode(malformed));}
    @Test void deepAndOversizedPayloadRejected(){assertThrows(IllegalArgumentException.class,()->decode(frame("{\"a\":"+"[".repeat(30)+"0"+"]".repeat(30)+"}")));assertThrows(IllegalArgumentException.class,()->encode(Kind.OPEN,object("text","x".repeat(5000))));JsonObject b=new JsonObject();for(int i=0;i<20;i++)b.addProperty("x"+i,"я".repeat(2000));assertThrows(IllegalArgumentException.class,()->encode(Kind.OPEN,b));}
    @Test void fuzzedBytesNeverEscapeAsUncheckedParserErrors(){Random r=new Random(4);for(int i=0;i<2000;i++){byte[] b=new byte[r.nextInt(1000)];r.nextBytes(b);try{decode(b);}catch(IllegalArgumentException expected){} }}
    @Test void admissionRecoversAndDoesNotGrowUnbounded(){RateGate gate=new RateGate(2,1,0);assertTrue(gate.allow(0));assertTrue(gate.allow(0));assertFalse(gate.allow(0));assertFalse(gate.allow(500_000_000));assertTrue(gate.allow(1_000_000_000));assertFalse(gate.allow(1_000_000_000));assertTrue(gate.allow(Long.MAX_VALUE));assertTrue(gate.allow(Long.MAX_VALUE));assertFalse(gate.allow(Long.MAX_VALUE));}
}
