package ru.neverland.townydistricts.model;
import java.util.*;
/** Towny cells, not a hardcoded Minecraft chunk size. */
public record Cell(UUID world,int x,int z) {
    public Cell { Objects.requireNonNull(world); }
    public String encoded(){return world+":"+x+":"+z;}
    public static Cell decode(String raw){String[] p=raw.split(":");if(p.length!=3)throw new IllegalArgumentException("Участок");return new Cell(UUID.fromString(p[0]),Integer.parseInt(p[1]),Integer.parseInt(p[2]));}
    public static Set<Cell> rectangle(Cell first,Cell second,int limit){
        if(!first.world.equals(second.world))throw new IllegalArgumentException("Обе точки должны быть в одном мире");
        long width=Math.abs((long)first.x-second.x)+1, depth=Math.abs((long)first.z-second.z)+1;
        if(width>limit||depth>limit||width*depth>limit)throw new IllegalArgumentException("Выделено слишком много участков: предел "+limit);
        Set<Cell> cells=new HashSet<>();
        for(long x=Math.min(first.x,second.x);x<=Math.max(first.x,second.x);x++)
            for(long z=Math.min(first.z,second.z);z<=Math.max(first.z,second.z);z++)cells.add(new Cell(first.world,(int)x,(int)z));
        return Set.copyOf(cells);
    }
    public static boolean connected(Set<Cell> cells){
        if(cells.isEmpty())return false;
        Set<Cell> remaining=new HashSet<>(cells);Deque<Cell> queue=new ArrayDeque<>();
        Cell first=remaining.iterator().next();remaining.remove(first);queue.add(first);
        while(!queue.isEmpty()){
            Cell c=queue.removeFirst();
            for(Cell n:List.of(new Cell(c.world,c.x+1,c.z),new Cell(c.world,c.x-1,c.z),new Cell(c.world,c.x,c.z+1),new Cell(c.world,c.x,c.z-1)))
                if(remaining.remove(n))queue.add(n);
        }
        return remaining.isEmpty();
    }
}
