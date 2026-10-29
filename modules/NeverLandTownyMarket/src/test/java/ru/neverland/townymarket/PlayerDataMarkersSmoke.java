package ru.neverland.townymarket;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import ru.neverland.core.PlayerDataMarkers;

public final class PlayerDataMarkersSmoke {
    private static final UUID PLAYER=new UUID(123,456);private static final String KEY="neverlandtownymarket:receipt",VALUE="verified-market-receipt";private static int checks;
    private interface Write{void write(DataOutputStream out)throws IOException;}
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static byte[] nbt(Write body)throws IOException{var bytes=new ByteArrayOutputStream();try(var gzip=new GZIPOutputStream(bytes);var out=new DataOutputStream(gzip)){out.writeByte(10);out.writeUTF("");body.write(out);out.writeByte(0);}return bytes.toByteArray();}
    private static void uuid(DataOutputStream out,UUID id)throws IOException{out.writeByte(11);out.writeUTF("UUID");out.writeInt(4);out.writeInt((int)(id.getMostSignificantBits()>>32));out.writeInt((int)id.getMostSignificantBits());out.writeInt((int)(id.getLeastSignificantBits()>>32));out.writeInt((int)id.getLeastSignificantBits());}
    private static void marker(DataOutputStream out,String key,String value)throws IOException{out.writeByte(10);out.writeUTF("BukkitValues");out.writeByte(8);out.writeUTF(key);out.writeUTF(value);out.writeByte(0);}
    private static boolean verify(Path file)throws IOException{return PlayerDataMarkers.verify(file,PLAYER,KEY,VALUE);}
    private static void invalid(Path file,byte[] bytes,String reason)throws IOException{Files.write(file,bytes);try{verify(file);}catch(IOException expected){check(true,reason);return;}throw new AssertionError(reason);}
    public static void main(String[] args)throws Exception{
        Path file=Files.createTempDirectory("market-player-marker-").resolve(PLAYER+".dat");check(!verify(file),"missing file cannot acknowledge an inventory save");
        byte[] valid=nbt(out->{uuid(out,PLAYER);out.writeByte(9);out.writeUTF("Inventory");out.writeByte(10);out.writeInt(1);out.writeByte(8);out.writeUTF("id");out.writeUTF("minecraft:stone");out.writeByte(3);out.writeUTF("count");out.writeInt(16);out.writeByte(0);marker(out,KEY,VALUE);});Files.write(file,valid);check(verify(file),"exact persisted marker and player UUID are verified and fsynced");check(Arrays.equals(valid,Files.readAllBytes(file)),"verification does not rewrite native player data");
        Files.write(file,nbt(out->{uuid(out,UUID.randomUUID());marker(out,KEY,VALUE);}));check(!verify(file),"another player's matching marker cannot settle this receipt");
        Files.write(file,nbt(out->{uuid(out,PLAYER);marker(out,KEY,"old-receipt");}));check(!verify(file),"valid older save cannot acknowledge a newer receipt");
        Files.write(file,nbt(out->{uuid(out,PLAYER);out.writeByte(8);out.writeUTF(KEY);out.writeUTF(VALUE);}));check(!verify(file),"marker outside root BukkitValues is not evidence");
        Files.write(file,nbt(out->{uuid(out,PLAYER);out.writeByte(10);out.writeUTF("BukkitValues");out.writeByte(10);out.writeUTF("nested");out.writeByte(8);out.writeUTF(KEY);out.writeUTF(VALUE);out.writeByte(0);out.writeByte(0);}));check(!verify(file),"nested matching text is not the receipt field");
        invalid(file,nbt(out->{uuid(out,PLAYER);uuid(out,PLAYER);marker(out,KEY,VALUE);}),"duplicate player identity refused");
        invalid(file,nbt(out->{uuid(out,PLAYER);marker(out,KEY,VALUE);marker(out,KEY,"old");}),"duplicate PDC compound cannot acknowledge an overwritten marker");
        invalid(file,nbt(out->{uuid(out,PLAYER);out.writeByte(10);out.writeUTF("BukkitValues");for(String value:List.of(VALUE,"old")){out.writeByte(8);out.writeUTF(KEY);out.writeUTF(value);}out.writeByte(0);}),"duplicate marker refused");
        var crc=valid.clone();crc[crc.length-8]^=1;invalid(file,crc,"gzip CRC must be checked after a matching marker");invalid(file,Arrays.copyOf(valid,valid.length-3),"truncated gzip trailer cannot acknowledge save");
        invalid(file,nbt(out->{uuid(out,PLAYER);marker(out,KEY,VALUE);out.writeByte(7);out.writeUTF("bad");out.writeInt(-1);}),"negative array length refused");
        invalid(file,nbt(out->{uuid(out,PLAYER);marker(out,KEY,VALUE);out.writeByte(12);out.writeUTF("bad");out.writeInt(64*1024*1024);}),"oversized expanded array refused before allocation");
        invalid(file,nbt(out->{uuid(out,PLAYER);marker(out,KEY,VALUE);for(int i=0;i<66;i++){out.writeByte(10);out.writeUTF("nested");}for(int i=0;i<66;i++)out.writeByte(0);}),"deeply nested data refused");
        invalid(file,new byte[]{1,2,3,4},"non-gzip player save refused");
        System.out.println("PlayerDataMarkersSmoke PASS "+checks+" checks");
    }
}
