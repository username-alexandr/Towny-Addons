package ru.neverland.townyjobs;
import java.util.*;
/** Persistent profession and experience; a working shift is deliberately volatile. */
public record Career(Profession profession,Map<Profession,Long> seconds,UUID town,String building,long assignedAt,long changedAt,long revision){
    public Career{seconds=Map.copyOf(seconds);if(building==null||!building.matches("[a-z0-9_]{0,80}")||(town==null)!=building.isEmpty()||town!=null&&profession==null||assignedAt<0||changedAt<0||revision<0||seconds.values().stream().anyMatch(n->n<0||n>60000000))throw new IllegalArgumentException("Повреждена профессия жителя");}
    public static Career empty(){return new Career(null,Map.of(),null,"",0,0,0);}
    public Career choose(Profession p,long now){return new Career(p,seconds,null,"",0,now,Math.addExact(revision,1));}
    public Career work(UUID t,String b,long now){return new Career(profession,seconds,t,b,now,changedAt,Math.addExact(revision,1));}
    public Career leave(){return work(null,"",0);}
    public Career earn(int amount){if(amount<0||amount>60||profession==null)throw new IllegalArgumentException("Недопустимый опыт");var next=new EnumMap<Profession,Long>(Profession.class);next.putAll(seconds);next.put(profession,Math.min(60000000,seconds.getOrDefault(profession,0L)+amount));return new Career(profession,next,town,building,assignedAt,changedAt,revision);}
}
