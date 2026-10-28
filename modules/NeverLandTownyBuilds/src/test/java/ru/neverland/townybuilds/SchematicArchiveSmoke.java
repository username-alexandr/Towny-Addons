package ru.neverland.townybuilds;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import ru.neverland.townybuilds.construction.*;

/** Actual files, stable versions, defensive ownership and corruption preservation. */
public final class SchematicArchiveSmoke {
    static int checks;static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    interface Run{void run()throws Exception;}static void reject(Run r,String label)throws Exception{try{r.run();}catch(IOException|IllegalArgumentException expected){checks++;return;}throw new AssertionError(label);}
    static SchematicArchive.Level level(String block){return new SchematicArchive.Level(2,2,2,1,1,5000,0,List.of("minecraft:air",block),new short[]{0,1,0,0,0,0,1,0});}
    public static void main(String[] args)throws Exception{
        Path folder=Files.createTempDirectory("schematic-archive-");var old=new SchematicArchive.Family("forge","medieval",Map.of(1,level("minecraft:stone"),2,level("minecraft:stone")));
        String original=SchematicArchive.save(folder,old);check(original.matches("[a-f0-9]{64}")&&Files.exists(SchematicArchive.path(folder,original)),"version is committed before it can be pinned");
        byte[] bytes=Files.readAllBytes(SchematicArchive.path(folder,original));check(SchematicArchive.save(folder,old).equals(original)&&Arrays.equals(bytes,Files.readAllBytes(SchematicArchive.path(folder,original))),"same family reuses immutable archive bytes");
        var loaded=SchematicArchive.load(folder,original);check(loaded.project().equals("forge")&&loaded.style().equals("medieval")&&loaded.levels().size()==2,"saved family retains project, style and levels");
        check(loaded.levels().get(1).state(1).equals("minecraft:stone")&&loaded.levels().get(1).originX()==1,"block state and anchor survive reading");
        short[] mutable={0,1,0,0,0,0,1,0};var held=new SchematicArchive.Level(2,2,2,1,1,5000,0,List.of("minecraft:air","minecraft:stone"),mutable);mutable[1]=0;held.cells()[1]=0;check(held.code(1)==1,"caller arrays cannot change committed geometry");
        var next=new SchematicArchive.Family("forge","medieval",Map.of(1,level("minecraft:bricks"),2,level("minecraft:bricks")));String changed=SchematicArchive.save(folder,next);
        check(!changed.equals(original),"new model creates a distinct version");check(SchematicArchive.load(folder,original).levels().get(1).state(1).equals("minecraft:stone"),"new import cannot mutate an existing site's old version");
        check(SchematicArchive.load(folder,changed).levels().get(1).state(1).equals("minecraft:bricks"),"new sites can use the new version");
        check(!SchematicArchive.id(new SchematicArchive.Family("forge","modern",old.levels())).equals(original),"style is part of version identity");
        reject(()->SchematicArchive.path(folder,"../../outside"),"pin cannot escape version directory");
        reject(()->new SchematicArchive.Level(2,2,2,-1,0,5000,0,List.of("minecraft:air"),new short[8]),"anchor cannot be outside geometry");
        reject(()->new SchematicArchive.Level(2,2,2,0,0,5000,0,List.of("minecraft:air"),new short[]{5,0,0,0,0,0,0,0}),"bad palette index rejected");
        reject(()->new SchematicArchive.Family("forge","unknown",old.levels()),"unknown style rejected");
        Path path=SchematicArchive.path(folder,original);byte[] bad=bytes.clone();bad[bad.length/2]^=1;Files.write(path,bad);
        reject(()->SchematicArchive.load(folder,original),"modified old archive is never accepted as the pinned model");
        reject(()->SchematicArchive.save(folder,old),"import does not overwrite corrupt evidence under an existing pin");check(Arrays.equals(bad,Files.readAllBytes(path)),"corrupt original remains available for recovery");
        System.out.println("SchematicArchiveSmoke PASS "+checks+" checks");
    }
}
