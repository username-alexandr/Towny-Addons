package ru.neverland.core;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Read-only proof of a particular player inventory save. Run on a bounded I/O worker, never a region/ledger thread. */
public final class PlayerDataMarkers {
    private static final long MAX_COMPRESSED=16L*1024*1024,MAX_EXPANDED=64L*1024*1024;
    private PlayerDataMarkers(){}

    /** False means a valid save without this receipt. Corrupt/unsupported storage cannot acknowledge delivery. */
    public static boolean verify(Path file,UUID player,String markerKey,String markerValue)throws IOException{
        Objects.requireNonNull(file);Objects.requireNonNull(player);
        if(markerKey==null||!markerKey.matches("[a-z0-9._-]+:[a-z0-9/._-]+")||markerKey.length()>256||markerValue==null||markerValue.isEmpty()||markerValue.length()>256)throw new IllegalArgumentException("Invalid save receipt marker");
        Path path=file.toAbsolutePath().normalize();if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))return false;
        try(var channel=FileChannel.open(path,StandardOpenOption.READ,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){
            if(channel.size()<1||channel.size()>MAX_COMPRESSED)throw new IOException("Player save exceeds proof size limit");
            var raw=Channels.newInputStream(channel);
            // Closing gzip must not close the exact channel whose validated contents we force below.
            var borrowed=new FilterInputStream(raw){@Override public void close(){}};
            boolean found;
            try(var gzip=new GZIPInputStream(new BufferedInputStream(borrowed));var counted=new BoundedInput(gzip);var input=new DataInputStream(counted)){
                var parser=new Parser(input,markerKey,markerValue);if(input.readUnsignedByte()!=10)throw new IOException("Player save root is not a compound");input.readUTF();parser.compound(0,true,false);
                // Reads the trailer, checks CRC and rejects appended NBT payloads/extra gzip members with data.
                if(input.read()!=-1)throw new IOException("Trailing player save payload");
                found=player.equals(parser.player)&&parser.marker;
            }
            if(!found)return false;
            channel.force(true);
            // Vanilla replaces the .dat file; the parent directory entry must also be durable.
            try(var directory=FileChannel.open(path.getParent(),StandardOpenOption.READ)){directory.force(true);}
            return true;
        }
    }

    private static final class BoundedInput extends FilterInputStream {
        private long consumed;
        BoundedInput(InputStream in){super(in);}
        private void count(int n)throws IOException{if(n>0){consumed+=n;if(consumed>MAX_EXPANDED)throw new IOException("Expanded player save exceeds proof limit");}if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Player save proof interrupted");}
        @Override public int read()throws IOException{int n=super.read();count(n<0?0:1);return n;}
        @Override public int read(byte[] bytes,int offset,int length)throws IOException{int n=in.read(bytes,offset,length);count(n);return n;}
    }
    private static final class Parser {
        private final DataInputStream input;private final String key,value;private final byte[] buffer=new byte[8192];private long tags;
        private UUID player;private boolean marker,uuidSeen,bukkitSeen,markerSeen;
        Parser(DataInputStream input,String key,String value){this.input=input;this.key=key;this.value=value;}
        private void tick()throws IOException{if(++tags>1_000_000)throw new IOException("Too many player save tags");}
        private int length()throws IOException{int n=input.readInt();if(n<0||n>MAX_EXPANDED)throw new IOException("Invalid player save array length");return n;}
        private void skip(long bytes)throws IOException{if(bytes<0||bytes>MAX_EXPANDED)throw new IOException("Player save array exceeds proof limit");while(bytes>0){int n=(int)Math.min(bytes,buffer.length);input.readFully(buffer,0,n);bytes-=n;}}
        private void compound(int depth,boolean root,boolean bukkit)throws IOException{
            if(depth>64)throw new IOException("Player save nesting exceeds proof limit");
            while(true){int type=input.readUnsignedByte();if(type==0)return;tick();String name=input.readUTF();
                if(root&&name.equals("UUID")){
                    if(uuidSeen)throw new IOException("Duplicate player UUID");uuidSeen=true;
                    if(type==11){int size=length();if(size==4){long high=((long)input.readInt()<<32)|(input.readInt()&0xffffffffL);long low=((long)input.readInt()<<32)|(input.readInt()&0xffffffffL);player=new UUID(high,low);}else skip(size*4L);}else payload(type,depth+1);
                }else if(root&&name.equals("BukkitValues")){
                    if(bukkitSeen)throw new IOException("Duplicate persistent data compound");bukkitSeen=true;if(type==10)compound(depth+1,false,true);else payload(type,depth+1);
                }else if(bukkit&&name.equals(key)){
                    if(markerSeen)throw new IOException("Duplicate receipt marker");markerSeen=true;if(type==8)marker=value.equals(input.readUTF());else payload(type,depth+1);
                }else payload(type,depth+1);
            }
        }
        private void payload(int type,int depth)throws IOException{
            if(depth>64)throw new IOException("Player save nesting exceeds proof limit");
            switch(type){
                case 1->skip(1);case 2->skip(2);case 3,5->skip(4);case 4,6->skip(8);
                case 7->skip(length());case 8->input.readUTF();
                case 9->{int child=input.readUnsignedByte(),size=length();if(child<0||child>12||child==0&&size!=0)throw new IOException("Invalid player save list");for(int i=0;i<size;i++){tick();payload(child,depth+1);}}
                case 10->compound(depth,false,false);case 11->skip(length()*4L);case 12->skip(length()*8L);
                default->throw new IOException("Unknown player save tag type: "+type);
            }
        }
    }
}
