package ru.neverland.townypower.model;
import java.util.*;
public record PowerProfile(String id,String name,String icon,boolean enabled,int priority,List<Long> generation,List<Long> demand) {
    public static final long MAX=1_000_000_000L;
    public PowerProfile {
        if(id==null||!id.matches("[a-z0-9_-]{1,64}")||name==null||name.isBlank()||icon==null||icon.isBlank()||priority<0||priority>100)throw new IllegalArgumentException("Неверный профиль энергетики");
        generation=List.copyOf(generation);demand=List.copyOf(demand);
        if(generation.size()!=demand.size()||(generation.size()!=1&&generation.size()!=5))throw new IllegalArgumentException("Нужна мощность для 1 или 5 уровней: "+id);
        for(var values:List.of(generation,demand)){long previous=0;for(long n:values){if(n<previous||n>MAX)throw new IllegalArgumentException("Мощность должна возрастать: 0..1000000000 для "+id);previous=n;}}
        if(generation.stream().anyMatch(n->n>0)&&demand.stream().anyMatch(n->n>0))throw new IllegalArgumentException("Генератор не может зависеть от своей сети: "+id);
    }
    public long generation(int level){return at(generation,level);}public long demand(int level){return at(demand,level);}
    private long at(List<Long> values,int level){return level<=0?0:values.get(Math.min(level,values.size())-1);}
    public boolean producer(){return generation.stream().anyMatch(n->n>0);}public boolean consumer(){return demand.stream().anyMatch(n->n>0);}public boolean relevant(){return producer()||consumer();}
}
