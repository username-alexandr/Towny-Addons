package ru.neverland.core;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Administrative evidence, never a payment instruction. Names and town membership are snapshots. */
public record AuditRecord(String id,long at,String module,String kind,String outcome,String operation,
                          Party actor,Party from,Party to,String asset,long quantity,String money,String details) {
    public record Party(String type,String id,String name,String town) {
        public Party { Objects.requireNonNull(type);Objects.requireNonNull(id);Objects.requireNonNull(name);Objects.requireNonNull(town); }
        public static Party unknown(){return new Party("UNKNOWN","","Неизвестно","");}
        public static Party system(){return new Party("SYSTEM","","Автоматически","");}
    }
    public AuditRecord {
        UUID.fromString(id);if(at<0||quantity<0)throw new IllegalArgumentException("Некорректная запись аудита");
        for(String s:List.of(module,kind,outcome,operation,asset,money,details))if(s.length()>262144)throw new IllegalArgumentException("Слишком большая запись аудита");
        Objects.requireNonNull(actor);Objects.requireNonNull(from);Objects.requireNonNull(to);
        if(!money.isEmpty()&&new BigDecimal(money).signum()<0)throw new IllegalArgumentException("Отрицательная сумма");
    }
    public static String id(String module,String operation,String step){return UUID.nameUUIDFromBytes((module+":"+operation+":"+step).getBytes(StandardCharsets.UTF_8)).toString();}
    public List<String> fields(){var f=new ArrayList<String>(List.of("1",id,Long.toString(at),module,kind,outcome,operation));for(var p:List.of(actor,from,to))f.addAll(List.of(p.type(),p.id(),p.name(),p.town()));f.addAll(List.of(asset,Long.toString(quantity),money,details));return List.copyOf(f);}
    public static AuditRecord parse(List<String> f){if(f.size()!=23||!f.get(0).equals("1"))throw new IllegalArgumentException("Неизвестная схема аудита");return new AuditRecord(f.get(1),Long.parseLong(f.get(2)),f.get(3),f.get(4),f.get(5),f.get(6),party(f,7),party(f,11),party(f,15),f.get(19),Long.parseLong(f.get(20)),f.get(21),f.get(22));}
    private static Party party(List<String> f,int i){return new Party(f.get(i),f.get(i+1),f.get(i+2),f.get(i+3));}
    // A replay may happen after a rename, town change or restart. Keep the first snapshot.
    public List<String> identity(){return List.of(module,kind,outcome,operation,from.type(),from.id(),to.type(),to.id(),asset,Long.toString(quantity),money);}
    public boolean town(String uuid){return from.town().equals(uuid)||to.town().equals(uuid)||actor.town().equals(uuid);}
    public boolean player(String uuid){return List.of(actor,from,to).stream().anyMatch(p->p.type().equals("PLAYER")&&p.id().equals(uuid));}
    public boolean intercity(){return !from.town().isEmpty()&&!to.town().isEmpty()&&!from.town().equals(to.town());}
}
