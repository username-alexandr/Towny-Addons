package ru.neverland.townycontrol;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Versioned, complete chunk snapshots and immutable compare-and-set plans. No Bukkit world access. */
final class DeletionFiles {
    record Plot(UUID world,int x,int z) {
        String key(){return world+"_"+x+"_"+z;}
        boolean contains(int bx,int bz){return Math.floorDiv(bx,16)==x&&Math.floorDiv(bz,16)==z;}
    }
    record Baseline(Plot plot,UUID town,long claimed,int minY,int maxY,List<String> blocks) {
        Baseline {
            blocks=List.copyOf(blocks);
            if(minY>=maxY||minY< -2048||maxY>4096||blocks.size()!=256L*(maxY-minY-1))throw new IllegalArgumentException("Неполный снимок участка");
            blocks.forEach(DeletionFiles::blockData);
        }
        String at(int x,int y,int z){return blocks.get(((z&15)*16+(x&15))*(maxY-minY-1)+(maxY-1-y));}
    }
    record Change(int x,int y,int z,String before,String after) {
        Change {blockData(before);blockData(after);if(y< -2048||y>=4096)throw new IllegalArgumentException("Высота вне диапазона");}
    }
    record ChunkPlan(Plot plot,int minY,int maxY,List<Change> changes) {
        ChunkPlan {
            changes=List.copyOf(changes);var positions=new HashSet<String>();
            for(var c:changes)if(!plot.contains(c.x(),c.z())||c.y()<=minY||c.y()>=maxY||!positions.add(c.x()+":"+c.y()+":"+c.z()))throw new IllegalArgumentException("План выходит за участок или содержит дубликаты");
        }
    }
    enum Decision { APPLY, ALREADY_APPLIED, SKIP }
    static Decision decide(Change change,String current,boolean tile,boolean claimed){
        if(claimed||tile||protectedBlock(current)||protectedBlock(change.after()))return Decision.SKIP;
        if(current.equals(change.after()))return Decision.ALREADY_APPLIED;
        return current.equals(change.before())?Decision.APPLY:Decision.SKIP;
    }
    static boolean protectedBlock(String data){return Set.of("minecraft:bedrock","minecraft:barrier","minecraft:end_portal","minecraft:end_portal_frame","minecraft:nether_portal","minecraft:command_block","minecraft:chain_command_block","minecraft:repeating_command_block","minecraft:structure_block","minecraft:jigsaw").contains(material(data));}
    static String material(String data){int i=data.indexOf('[');return i<0?data:data.substring(0,i);}
    private static void blockData(String data){if(data==null||data.length()>4096||!data.matches("minecraft:[a-z0-9_]+(?:\\[[a-z0-9_=,]+\\])?"))throw new IllegalArgumentException("Неверные данные блока");}
    @FunctionalInterface interface Writer {void write(DataOutputStream out)throws IOException;}
    static void atomic(Path path,Writer writer)throws IOException {
        Files.createDirectories(path.getParent());Path temp=Files.createTempFile(path.getParent(),"deletion-",".tmp");
        try{
            try(var out=new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(temp))))){writer.write(out);}
            try(var channel=FileChannel.open(temp,StandardOpenOption.WRITE)){channel.force(true);}
            Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(temp);}
    }
    private static void plot(DataOutputStream out,Plot p)throws IOException{out.writeUTF(p.world().toString());out.writeInt(p.x());out.writeInt(p.z());}
    private static Plot plot(DataInputStream in)throws IOException{return new Plot(UUID.fromString(in.readUTF()),in.readInt(),in.readInt());}
    static void saveBaseline(Path path,Baseline b)throws IOException {atomic(path,out->{out.writeInt(0x4E4C4231);plot(out,b.plot());out.writeUTF(b.town().toString());out.writeLong(b.claimed());out.writeInt(b.minY());out.writeInt(b.maxY());out.writeInt(b.blocks().size());for(String data:b.blocks())out.writeUTF(data);});}
    static Baseline loadBaseline(Path path)throws IOException{
        try(var in=input(path)){if(in.readInt()!=0x4E4C4231)throw new IOException("Неизвестная версия снимка");var p=plot(in);UUID town=UUID.fromString(in.readUTF());long claimed=in.readLong();int min=in.readInt(),max=in.readInt(),n=count(in,2_000_000);var blocks=new ArrayList<String>(n);for(int i=0;i<n;i++)blocks.add(in.readUTF());end(in);return new Baseline(p,town,claimed,min,max,blocks);}
        catch(IllegalArgumentException ex){throw new IOException("Снимок повреждён: "+path.getFileName(),ex);}
    }
    static void savePlan(Path path,ChunkPlan p)throws IOException{atomic(path,out->{out.writeInt(0x4E4C5031);plot(out,p.plot());out.writeInt(p.minY());out.writeInt(p.maxY());out.writeInt(p.changes().size());for(var c:p.changes()){out.writeInt(c.x());out.writeInt(c.y());out.writeInt(c.z());out.writeUTF(c.before());out.writeUTF(c.after());}});}
    static ChunkPlan loadPlan(Path path)throws IOException{
        try(var in=input(path)){if(in.readInt()!=0x4E4C5031)throw new IOException("Неизвестная версия плана");var p=plot(in);int min=in.readInt(),max=in.readInt(),n=count(in,2_000_000);var changes=new ArrayList<Change>(n);for(int i=0;i<n;i++)changes.add(new Change(in.readInt(),in.readInt(),in.readInt(),in.readUTF(),in.readUTF()));end(in);return new ChunkPlan(p,min,max,changes);}
        catch(IllegalArgumentException ex){throw new IOException("План повреждён: "+path.getFileName(),ex);}
    }
    private static int count(DataInputStream in,int limit)throws IOException{int n=in.readInt();if(n<0||n>limit)throw new IOException("Недопустимый размер данных");return n;}
    private static DataInputStream input(Path path)throws IOException{return new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(path))));}
    private static void end(DataInputStream in)throws IOException{if(in.read()!=-1)throw new IOException("Лишние данные в файле");}
    static String sha(Path path)throws IOException{try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
