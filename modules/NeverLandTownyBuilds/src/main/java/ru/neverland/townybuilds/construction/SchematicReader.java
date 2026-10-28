package ru.neverland.townybuilds.construction;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Bounded Sponge v2/v3 reader. Pure data, no world access and no entity/NBT placement. */
public final class SchematicReader {
    public static final int MAX_VOLUME=262144,MAX_DIMENSION=128,MAX_PALETTE=4096;
    private static final int MAX_BYTES=16*1024*1024,MAX_TAGS=65536;
    public record Model(int version,int dataVersion,int width,int height,int length,List<Integer> offset,Map<Integer,String> palette,List<Integer> blocks,int blockEntities){
        public Model{offset=List.copyOf(offset);palette=Map.copyOf(palette);blocks=List.copyOf(blocks);}
        public int at(int x,int y,int z){if(x<0||x>=width||y<0||y>=height||z<0||z>=length)throw new IndexOutOfBoundsException();return blocks.get(x+z*width+y*width*length);}
    }
    private SchematicReader(){}
    public static Model read(Path file)throws IOException{
        if(Files.size(file)>MAX_BYTES)throw new IOException("Файл схемы превышает 16 МиБ");
        try(var source=new BufferedInputStream(Files.newInputStream(file))){
            source.mark(2);int first=source.read(),second=source.read();source.reset();
            try(var input=new DataInputStream(new Bounded(first==0x1f&&second==0x8b?new GZIPInputStream(source):source))){
                var parser=new Nbt(input);if(input.readUnsignedByte()!=10)throw new IOException("Корень схемы должен быть NBT Compound");input.readUTF();
                var root=compound(parser.tag(10,0),"Schematic");if(input.read()!=-1)throw new IOException("Лишние данные после схемы");
                if(!root.containsKey("Version")&&root.get("Schematic") instanceof Map<?,?>)root=compound(root.get("Schematic"),"Schematic");
                return model(root);
            }
        }catch(IllegalArgumentException|ArithmeticException error){throw new IOException("Повреждена схема: "+error.getMessage(),error);}
    }
    private static Map<String,Object> compound(Object value,String name)throws IOException{
        if(!(value instanceof Map<?,?> map))throw new IOException("Нет раздела "+name);
        var out=new LinkedHashMap<String,Object>();for(var e:map.entrySet()){if(!(e.getKey() instanceof String key))throw new IOException("Неверный ключ "+name);out.put(key,e.getValue());}return out;
    }
    private static int integer(Object value,String name)throws IOException{if(!(value instanceof Integer number))throw new IOException("Нужен целый NBT Int: "+name);return number;}
    private static int dimension(Object value,String name)throws IOException{if(!(value instanceof Short number))throw new IOException("Нужен NBT Short: "+name);int n=number&0xffff;if(n<1||n>MAX_DIMENSION)throw new IOException("Размер "+name+" вне диапазона 1.."+MAX_DIMENSION);return n;}
    private static int count(Object value,String name)throws IOException{if(value==null)return 0;if(!(value instanceof List<?> list))throw new IOException("Нужен NBT List: "+name);return list.size();}
    private static Model model(Map<String,Object> root)throws IOException{
        int version=integer(root.get("Version"),"Version");if(version!=2&&version!=3)throw new IOException("Поддерживаются Sponge .schem версий 2 и 3");
        int data=integer(root.get("DataVersion"),"DataVersion");if(data<1)throw new IOException("Неверная версия Minecraft в схеме");
        int width=dimension(root.get("Width"),"Width"),height=dimension(root.get("Height"),"Height"),length=dimension(root.get("Length"),"Length");
        long volume=(long)width*height*length;if(volume>MAX_VOLUME)throw new IOException("Объём схемы превышает "+MAX_VOLUME+" ячеек");
        List<Integer> offset=List.of(0,0,0);if(root.get("Offset")!=null){if(!(root.get("Offset") instanceof int[] array)||array.length!=3)throw new IOException("Offset должен содержать 3 координаты");offset=List.of(array[0],array[1],array[2]);}
        if(count(root.get("Entities"),"Entities")>0)throw new IOException("Экспортируйте здание без существ и предметов-сущностей");
        var blocks=version==3?compound(root.get("Blocks"),"Blocks"):root;var rawPalette=compound(blocks.get("Palette"),"Palette");
        if(rawPalette.isEmpty()||rawPalette.size()>MAX_PALETTE)throw new IOException("Неверный размер палитры схемы");
        var palette=new LinkedHashMap<Integer,String>();int maximum=0;
        for(var entry:rawPalette.entrySet()){
            int id=integer(entry.getValue(),"Palette");String state=entry.getKey();
            if(id<0||state.length()>512||!state.matches("minecraft:[a-z0-9_]+(?:\\[[a-z0-9_=,]+\\])?"))throw new IOException("Неверное состояние блока в палитре");
            if(palette.putIfAbsent(id,state)!=null)throw new IOException("Два блока используют один ID палитры");maximum=Math.max(maximum,id);
        }
        if(version==2&&root.get("PaletteMax")!=null&&integer(root.get("PaletteMax"),"PaletteMax")<=maximum)throw new IOException("PaletteMax меньше ID палитры");
        Object raw=blocks.get(version==3?"Data":"BlockData");if(!(raw instanceof byte[] bytes))throw new IOException("Отсутствует массив блоков схемы");
        var positions=new ArrayList<Integer>((int)volume);int cursor=0;
        for(int n=0;n<volume;n++){
            int value=0,shift=0;while(true){if(cursor>=bytes.length||shift>28)throw new IOException("Оборванный varint массива блоков");int b=bytes[cursor++]&255;if(shift==28&&(b&0xf8)!=0)throw new IOException("Переполнение varint схемы");value|=(b&127)<<shift;if((b&128)==0)break;shift+=7;}
            if(!palette.containsKey(value))throw new IOException("Блок ссылается на отсутствующий ID палитры");positions.add(value);
        }
        if(cursor!=bytes.length)throw new IOException("В массиве блоков больше данных, чем ячеек здания");
        int tiles=count(blocks.get("BlockEntities"),"BlockEntities");
        return new Model(version,data,width,height,length,offset,palette,positions,tiles);
    }
    private static final class Bounded extends FilterInputStream{
        private int read;Bounded(InputStream source){super(source);}
        private void used(int n)throws IOException{if(n>0&&((long)read+n)>MAX_BYTES)throw new IOException("Распакованная схема превышает 16 МиБ");if(n>0)read+=n;}
        public int read()throws IOException{int n=in.read();if(n>=0)used(1);return n;}
        public int read(byte[] b,int offset,int length)throws IOException{int n=in.read(b,offset,Math.min(length,MAX_BYTES-read+1));used(n);return n;}
    }
    private static final class Nbt{
        final DataInputStream input;int tags;Nbt(DataInputStream input){this.input=input;}
        int size(int bytes)throws IOException{int n=input.readInt();if(n<0||(long)n*bytes>MAX_BYTES)throw new IOException("Слишком большой NBT-массив");return n;}
        Object tag(int kind,int depth)throws IOException{
            if(depth>32||++tags>MAX_TAGS)throw new IOException("Слишком сложная структура NBT");
            return switch(kind){
                case 1->input.readByte();case 2->input.readShort();case 3->input.readInt();case 4->input.readLong();case 5->input.readFloat();case 6->input.readDouble();
                case 7->{int n=size(1);byte[] bytes=new byte[n];input.readFully(bytes);yield bytes;}
                case 8->input.readUTF();
                case 9->{int type=input.readUnsignedByte(),n=size(1);if(type==0&&n!=0||n>MAX_TAGS-tags)throw new IOException("Неверный или слишком большой NBT List");var list=new ArrayList<Object>(n);for(int i=0;i<n;i++)list.add(tag(type,depth+1));yield list;}
                case 10->{var map=new LinkedHashMap<String,Object>();int type;while((type=input.readUnsignedByte())!=0){String name=input.readUTF();if(map.containsKey(name))throw new IOException("Повторный NBT-ключ");map.put(name,tag(type,depth+1));}yield map;}
                case 11->{int n=size(4);int[] values=new int[n];for(int i=0;i<n;i++)values[i]=input.readInt();yield values;}
                case 12->{int n=size(8);long[] values=new long[n];for(int i=0;i<n;i++)values[i]=input.readLong();yield values;}
                default->throw new IOException("Неизвестный тип NBT "+kind);
            };
        }
    }
}
