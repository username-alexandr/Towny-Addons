package ru.neverland.townybuilds.construction;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Immutable compact versions, addressed by the hash of their canonical uncompressed contents. */
public final class SchematicArchive {
    private static final int MAGIC=0x4e4c5343,SCHEMA=1,MAX_RAW=32*1024*1024;
    public record Level(int width,int height,int length,int originX,int originZ,int dataVersion,int ignoredTiles,List<String> palette,short[] cells){
        public Level{
            palette=List.copyOf(palette);cells=cells.clone();
            if(width<1||height<1||length<1||width>128||height>128||length>128||(long)width*height*length>SchematicReader.MAX_VOLUME||cells.length!=(long)width*height*length||originX<0||originX>=width||originZ<0||originZ>=length||dataVersion<1||ignoredTiles<0||palette.isEmpty()||palette.size()>4096)throw new IllegalArgumentException("Неверные размеры сохранённого чертежа");
            for(String state:palette)if(state.length()>512||!state.matches("minecraft:[a-z0-9_]+(?:\\[[a-z0-9_=,]+\\])?"))throw new IllegalArgumentException("Неверное состояние блока сохранённого чертежа");
            for(short cell:cells)if((cell&0xffff)>=palette.size())throw new IllegalArgumentException("Неверная ссылка на палитру сохранённого чертежа");
        }
        @Override public short[] cells(){return cells.clone();}
        public int size(){return cells.length;}
        public int code(int index){return cells[index]&0xffff;}
        public String state(int index){return palette.get(code(index));}
    }
    public record Family(String project,String style,Map<Integer,Level> levels){
        public Family{
            levels=Map.copyOf(levels);
            if(project==null||!project.matches("[a-z0-9_]{1,80}")||!Set.of("medieval","modern","fantasy").contains(style)||levels.isEmpty()||levels.size()>5||levels.keySet().stream().anyMatch(n->n<1||n>5))throw new IllegalArgumentException("Неверное семейство чертежей");
        }
        public long cells(){return levels.values().stream().mapToLong(Level::size).sum();}
    }
    private SchematicArchive(){}
    private static String digest(byte[] data){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}catch(NoSuchAlgorithmException error){throw new AssertionError(error);}}
    public static Path path(Path directory,String pin){if(pin==null||!pin.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Неверный ID версии чертежа");return directory.resolve(pin+".nltbp");}
    private static byte[] encode(Family family)throws IOException{
        var bytes=new ByteArrayOutputStream();try(var out=new DataOutputStream(bytes)){
            out.writeInt(MAGIC);out.writeInt(SCHEMA);out.writeUTF(family.project());out.writeUTF(family.style());out.writeInt(family.levels().size());
            for(var entry:new TreeMap<>(family.levels()).entrySet()){
                var level=entry.getValue();out.writeInt(entry.getKey());out.writeInt(level.width());out.writeInt(level.height());out.writeInt(level.length());out.writeInt(level.originX());out.writeInt(level.originZ());out.writeInt(level.dataVersion());out.writeInt(level.ignoredTiles());
                out.writeInt(level.palette().size());for(String state:level.palette())out.writeUTF(state);out.writeInt(level.size());for(int i=0;i<level.size();i++)out.writeShort(level.code(i));
            }
        }if(bytes.size()>MAX_RAW)throw new IOException("Семейство чертежей превышает допустимый размер");return bytes.toByteArray();
    }
    public static String id(Family family)throws IOException{return digest(encode(family));}
    public static String save(Path directory,Family family)throws IOException{
        byte[] raw=encode(family);String pin=digest(raw);Path target=path(directory,pin);Files.createDirectories(directory);
        if(Files.exists(target)){load(directory,pin);return pin;}
        Path temporary=Files.createTempFile(directory,"schematic-",".tmp");
        try{
            try(var file=new FileOutputStream(temporary.toFile());var gzip=new GZIPOutputStream(file)){gzip.write(raw);gzip.finish();gzip.flush();file.getFD().sync();}
            try{Files.createLink(target,temporary);}
            catch(FileAlreadyExistsException exists){load(directory,pin);}
            catch(UnsupportedOperationException noHardLinks){Files.move(temporary,target);}
            try(var folder=FileChannel.open(directory,StandardOpenOption.READ)){folder.force(true);}
            return pin;
        }finally{Files.deleteIfExists(temporary);}
    }
    private static int bounded(int value,int maximum,String field)throws IOException{if(value<1||value>maximum)throw new IOException("Повреждён размер "+field+" в сохранённом чертеже");return value;}
    public static Family load(Path directory,String pin)throws IOException{
        Path path=path(directory,pin);if(Files.size(path)>16*1024*1024)throw new IOException("Файл версии чертежа слишком большой");byte[] raw;
        try(var in=new GZIPInputStream(Files.newInputStream(path))){raw=in.readNBytes(MAX_RAW+1);if(raw.length>MAX_RAW)throw new IOException("Распакованная версия чертежа слишком большая");}
        if(!digest(raw).equals(pin))throw new IOException("Контрольная сумма версии чертежа не совпадает: "+pin);
        try(var in=new DataInputStream(new ByteArrayInputStream(raw))){
            if(in.readInt()!=MAGIC||in.readInt()!=SCHEMA)throw new IOException("Неизвестный формат сохранённого чертежа");String project=in.readUTF(),style=in.readUTF();int count=bounded(in.readInt(),5,"уровней");var levels=new TreeMap<Integer,Level>();
            for(int n=0;n<count;n++){
                int level=bounded(in.readInt(),5,"уровня"),width=bounded(in.readInt(),128,"ширины"),height=bounded(in.readInt(),128,"высоты"),length=bounded(in.readInt(),128,"длины");
                if((long)width*height*length>SchematicReader.MAX_VOLUME)throw new IOException("Объём версии чертежа слишком большой");
                int originX=in.readInt(),originZ=in.readInt(),dataVersion=in.readInt(),tiles=in.readInt(),size=bounded(in.readInt(),4096,"палитры");var palette=new ArrayList<String>(size);for(int i=0;i<size;i++)palette.add(in.readUTF());
                int cells=bounded(in.readInt(),SchematicReader.MAX_VOLUME,"ячеек");if(cells!=(long)width*height*length)throw new IOException("Размеры версии чертежа не совпадают");short[] codes=new short[cells];for(int i=0;i<cells;i++)codes[i]=in.readShort();
                if(levels.putIfAbsent(level,new Level(width,height,length,originX,originZ,dataVersion,tiles,palette,codes))!=null)throw new IOException("Повторный уровень сохранённого чертежа");
            }
            if(in.read()!=-1)throw new IOException("Лишние данные сохранённого чертежа");return new Family(project,style,levels);
        }catch(IllegalArgumentException error){throw new IOException("Повреждена сохранённая версия чертежа",error);}
    }
}
