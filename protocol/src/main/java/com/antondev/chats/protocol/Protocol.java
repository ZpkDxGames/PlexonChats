package com.antondev.chats.protocol;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

/** Bounded binary framing. Authentication is checked before any field parsing. No JSON or executable components. */
public final class Protocol {
    public static final String CHANNEL = "plexonchats:network";
    public static final int VERSION = 1, MAX_PACKET = 24_000, MAX_BODY = 8_192, MAX_AUDIENCE = 512;
    public static final long MAX_AGE_MS = 15_000, FUTURE_SKEW_MS = 5_000;
    private static final int MAGIC = 0x50434854, SIGNATURE = 32;
    private Protocol() {}

    public static byte[] readKey(Path path) throws IOException {
        if (Files.size(path) > 256) throw new IOException("Authentication key file too large");
        try {
            byte[] key = HexFormat.of().parseHex(Files.readString(path, StandardCharsets.US_ASCII).strip());
            if (key.length != 32) throw new IllegalArgumentException();
            return key;
        } catch (IllegalArgumentException ex) { throw new IOException("Authentication key must contain exactly 64 hex characters"); }
    }
    public static byte[] encode(Envelope e, byte[] key) {
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            out.writeInt(MAGIC); out.writeShort(VERSION); out.writeByte(e.type().ordinal());
            uuid(out, e.id()); uuid(out, e.session()); out.writeLong(e.createdAt());
            if (!validServer(e.server())) throw new IllegalArgumentException("Invalid server ID");
            string(out,e.server(),32); uuid(out,e.author());
            if (!validName(e.name())) throw new IllegalArgumentException("Invalid player name");
            string(out,e.name(),16); string(out,e.display(),1024); string(out,e.rank(),1024); string(out,e.body(),MAX_BODY);
            if (e.audience()!=null && e.audience().size()>MAX_AUDIENCE) throw new IllegalArgumentException("Audience too large");
            out.writeBoolean(e.restricted());
            out.writeShort(e.audience()==null ? -1 : e.audience().size());
            if (e.audience()!=null) for (UUID id:e.audience()) uuid(out,id);
            out.flush(); byte[] payload=bytes.toByteArray();
            if(payload.length+SIGNATURE>MAX_PACKET) throw new IllegalArgumentException("Packet too large");
            bytes.write(mac(payload,key)); return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }
    public static Envelope decode(byte[] data, byte[] key, long now) {
        if(data.length<SIGNATURE+7 || data.length>MAX_PACKET) throw new IllegalArgumentException("Invalid packet size");
        byte[] payload=Arrays.copyOf(data,data.length-SIGNATURE);
        if(!MessageDigest.isEqual(mac(payload,key),Arrays.copyOfRange(data,data.length-SIGNATURE,data.length)))
            throw new IllegalArgumentException("Unauthenticated packet");
        try {
            var in=new DataInputStream(new ByteArrayInputStream(payload));
            if(in.readInt()!=MAGIC) throw new IllegalArgumentException("Invalid magic");
            if(in.readUnsignedShort()!=VERSION) throw new IllegalArgumentException("Incompatible protocol");
            int type=in.readUnsignedByte();
            if(type>=Envelope.Type.values().length) throw new IllegalArgumentException("Unknown type");
            UUID id=uuid(in), session=uuid(in); long timestamp=in.readLong();
            if(timestamp<0 || timestamp<now-MAX_AGE_MS || timestamp>now+FUTURE_SKEW_MS) throw new IllegalArgumentException("Stale/future packet");
            String server=string(in,32); if(!validServer(server)) throw new IllegalArgumentException("Invalid server");
            UUID author=uuid(in); String name=string(in,16);
            if(!validName(name)) throw new IllegalArgumentException("Invalid name");
            String display=string(in,1024),rank=string(in,1024),body=string(in,MAX_BODY);
            boolean restricted=in.readBoolean();
            int count=in.readShort(); if(count< -1 || count>MAX_AUDIENCE) throw new IllegalArgumentException("Audience size");
            List<UUID> audience=count<0?null:new ArrayList<>(count);
            for(int i=0;i<count;i++) audience.add(uuid(in));
            if(in.available()!=0) throw new IllegalArgumentException("Trailing bytes");
            return new Envelope(Envelope.Type.values()[type],id,session,timestamp,server,author,name,display,rank,body,audience,restricted);
        } catch(IOException ex) { throw new IllegalArgumentException("Malformed packet",ex); }
    }
    public static boolean validServer(String value) { return value!=null && value.matches("[a-z0-9][a-z0-9_-]{0,31}"); }
    public static boolean validName(String value) { return value!=null && value.matches("[A-Za-z0-9_]{1,16}"); }
    private static void string(DataOutputStream out,String text,int max) throws IOException {
        validateText(text); byte[] bytes=text.getBytes(StandardCharsets.UTF_8);
        if(bytes.length>max) throw new IllegalArgumentException("Text too large");
        out.writeShort(bytes.length);out.write(bytes);
    }
    private static String string(DataInputStream in,int max)throws IOException {
        int length=in.readUnsignedShort(); if(length>max || length>in.available()) throw new IllegalArgumentException("Invalid length");
        String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(in.readNBytes(length))).toString();
        validateText(text);return text;
    }
    private static void validateText(String text) {
        for(int i=0;i<text.length();i++) {
            char c=text.charAt(i);
            if(Character.isISOControl(c) || c=='\u2028' || c=='\u2029' || (c>='\u202a'&&c<='\u202e') || (c>='\u2066'&&c<='\u2069')) throw new IllegalArgumentException("Control characters");
            if(Character.isHighSurrogate(c)) { if(i+1>=text.length() || !Character.isLowSurrogate(text.charAt(++i))) throw new IllegalArgumentException("Invalid Unicode"); }
            else if(Character.isLowSurrogate(c)) throw new IllegalArgumentException("Invalid Unicode");
        }
    }
    private static byte[] mac(byte[] data,byte[] key) {
        if(key.length!=32) throw new IllegalArgumentException("Invalid key size");
        try { Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return mac.doFinal(data); }
        catch(java.security.GeneralSecurityException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void uuid(DataOutputStream out,UUID id)throws IOException {out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}
    private static UUID uuid(DataInputStream in)throws IOException {return new UUID(in.readLong(),in.readLong());}
}
