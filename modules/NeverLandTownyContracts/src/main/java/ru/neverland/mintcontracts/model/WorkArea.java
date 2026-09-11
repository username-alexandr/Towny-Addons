package ru.neverland.mintcontracts.model;
import java.util.*;

/** ROAD coordinates are blocks; SCOUT coordinates are Minecraft chunks. Never loads a chunk. */
public record WorkArea(UUID world,int minX,int y,int minZ,int maxX,int maxZ,Set<String> required) {
    public WorkArea {
        Objects.requireNonNull(world);required=Set.copyOf(required);
        long area=((long)maxX-minX+1)*((long)maxZ-minZ+1);
        if(minX>maxX||minZ>maxZ||Math.abs((long)minX)>30_000_000||Math.abs((long)maxX)>30_000_000||Math.abs((long)minZ)>30_000_000||Math.abs((long)maxZ)>30_000_000||area<1||area>4096||required.isEmpty()||required.size()>area)
            throw new IllegalArgumentException("Недопустимый размер участка");
        for(String cell:required){int[] p=point(cell);if(p[0]<minX||p[0]>maxX||p[1]<minZ||p[1]>maxZ)throw new IllegalArgumentException("Точка вне участка");}
    }
    public boolean contains(int x,int z){return x>=minX&&x<=maxX&&z>=minZ&&z<=maxZ;}
    public static String key(int x,int z){return x+":"+z;}
    public static int[] point(String key){String[] p=key.split(":",-1);if(p.length!=2)throw new IllegalArgumentException("Некорректная точка");int x=Integer.parseInt(p[0]),z=Integer.parseInt(p[1]);if(!key(x,z).equals(key))throw new IllegalArgumentException("Неканоническая точка");return new int[]{x,z};}
    public static Set<String> rectangle(int x1,int z1,int x2,int z2,int limit){
        int a=Math.min(x1,x2),b=Math.max(x1,x2),c=Math.min(z1,z2),d=Math.max(z1,z2);long size=((long)b-a+1)*((long)d-c+1);
        if(size<1||size>limit)throw new IllegalArgumentException("Участок слишком большой: максимум "+limit+" точек");
        Set<String> keys=new LinkedHashSet<>();for(int x=a;x<=b;x++)for(int z=c;z<=d;z++)keys.add(key(x,z));return keys;
    }
}
