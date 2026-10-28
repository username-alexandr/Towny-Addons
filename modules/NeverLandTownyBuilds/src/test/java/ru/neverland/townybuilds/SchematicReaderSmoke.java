package ru.neverland.townybuilds;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import ru.neverland.townybuilds.construction.SchematicReader;

/** Hand-built independent NBT fixtures, including malformed lengths, palette and compression. */
public final class SchematicReaderSmoke {
    static int checks;static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    static void name(DataOutputStream out,int type,String name)throws IOException{out.writeByte(type);out.writeUTF(name);}
    static void integer(DataOutputStream out,String name,int value)throws IOException{name(out,3,name);out.writeInt(value);}
    static byte[] fixture(int version,boolean wrap,boolean invalidPalette,byte[] data,int width,boolean entities)throws IOException{
        var bytes=new ByteArrayOutputStream();try(var out=new DataOutputStream(bytes)){
            name(out,10,wrap?"":"Schematic");if(wrap)name(out,10,"Schematic");integer(out,"Version",version);integer(out,"DataVersion",5000);
            name(out,2,"Width");out.writeShort(width);name(out,2,"Height");out.writeShort(2);name(out,2,"Length");out.writeShort(2);
            name(out,11,"Offset");out.writeInt(3);out.writeInt(-7);out.writeInt(0);out.writeInt(9);
            if(entities){name(out,9,"Entities");out.writeByte(10);out.writeInt(1);out.writeByte(0);}
            if(version==3)name(out,10,"Blocks");else integer(out,"PaletteMax",301);
            name(out,10,"Palette");integer(out,"minecraft:air",0);integer(out,"minecraft:oak_stairs[facing=east,half=top,shape=straight,waterlogged=false]",300);
            if(invalidPalette)integer(out,"minecraft:stone",300);out.writeByte(0);
            name(out,7,version==3?"Data":"BlockData");out.writeInt(data.length);out.write(data);
            // Metadata is counted, but is never returned as executable/world NBT.
            name(out,9,"BlockEntities");out.writeByte(10);out.writeInt(1);name(out,8,"Id");out.writeUTF("minecraft:chest");out.writeByte(0);
            if(version==3)out.writeByte(0);if(wrap)out.writeByte(0);out.writeByte(0);
        }return bytes.toByteArray();
    }
    static SchematicReader.Model read(Path root,String name,byte[] bytes,boolean gzip)throws Exception{
        Path path=root.resolve(name);if(gzip){try(var out=new GZIPOutputStream(Files.newOutputStream(path))){out.write(bytes);}}else Files.write(path,bytes);return SchematicReader.read(path);
    }
    interface Run{void run()throws Exception;}
    static void reject(Run run,String message)throws Exception{try{run.run();}catch(IOException expected){checks++;return;}throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("schematic-reader-");
        // 8 cells, x fastest, then z, then y. ID 300 is independently encoded AC 02.
        byte[] cells={0,(byte)0xac,2,0,0,0,0,(byte)0xac,2,0};
        var v2=read(root,"v2.schem",fixture(2,false,false,cells,2,false),true);
        check(v2.version()==2&&v2.width()==2&&v2.height()==2&&v2.length()==2,"compressed Sponge v2 dimensions");
        check(v2.at(1,0,0)==300&&v2.at(0,1,1)==300&&v2.at(1,1,1)==0,"palette varints follow x-z-y order");
        check(v2.offset().equals(List.of(-7,0,9))&&v2.blockEntities()==1,"offset and unsupported tile metadata are explicitly represented");
        var v3=read(root,"v3.schem",fixture(3,true,false,cells,2,false),true);check(v3.blocks().equals(v2.blocks())&&v3.palette().equals(v2.palette()),"wrapped Sponge v3 block container matches v2");
        check(read(root,"plain.schem",fixture(3,false,false,cells,2,false),false).blocks().equals(v2.blocks()),"uncompressed root also supported");
        reject(()->read(root,"future.schem",fixture(4,false,false,cells,2,false),true),"future formats must not be guessed");
        reject(()->read(root,"duplicate.schem",fixture(3,false,true,cells,2,false),false),"duplicate palette IDs rejected");
        reject(()->read(root,"short.schem",fixture(3,false,false,new byte[]{0},2,false),false),"truncated block array rejected");
        reject(()->read(root,"extra.schem",fixture(3,false,false,new byte[9],2,false),false),"extra block entries rejected");
        reject(()->read(root,"overflow.schem",fixture(3,false,false,new byte[]{(byte)255,(byte)255,(byte)255,(byte)255,127},2,false),false),"overflowing varint rejected");
        reject(()->read(root,"unknown.schem",fixture(3,false,false,new byte[]{1,0,0,0,0,0,0,0},2,false),false),"unknown palette reference rejected");
        reject(()->read(root,"zero.schem",fixture(3,false,false,cells,0,false),false),"zero dimension rejected");
        reject(()->read(root,"large.schem",fixture(3,false,false,cells,65535,false),false),"unsigned dimensions cannot bypass limit");
        reject(()->read(root,"entities.schem",fixture(3,false,false,cells,2,true),false),"entity-bearing export is never pasted as blocks");
        byte[] truncated=Arrays.copyOf(fixture(3,false,false,cells,2,false),20);reject(()->read(root,"truncated.schem",truncated,true),"truncated NBT rejected");
        var badLength=new ByteArrayOutputStream();try(var out=new DataOutputStream(badLength)){name(out,10,"Schematic");name(out,7,"BlockData");out.writeInt(Integer.MAX_VALUE);}
        reject(()->read(root,"huge-array.schem",badLength.toByteArray(),true),"declared array cannot allocate unbounded memory");
        System.out.println("SchematicReaderSmoke PASS "+checks+" checks");
    }
}
