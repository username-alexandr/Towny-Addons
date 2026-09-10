package ru.neverland.townyupkeep.model;
import java.util.*;
public record Entry(boolean active,long due,String reason,Invoice invoice) {
    public enum Phase { PREPARED, MONEY_PENDING, MONEY_PAID, CANCELLED }
    public record Key(UUID town,String project) {public Key{Objects.requireNonNull(town);if(project==null||!project.matches("[a-z0-9_-]{1,64}"))throw new IllegalArgumentException("Неверный ID здания");}}
    public record Invoice(UUID id,Cost cost,int period,Phase phase){
        public Invoice{Objects.requireNonNull(id);Objects.requireNonNull(cost);Objects.requireNonNull(phase);if(period<60||period>604800)throw new IllegalArgumentException("Неверный период счёта");}
        public Invoice phase(Phase value){return new Invoice(id,cost,period,value);}
    }
    public Entry{if(due<0||reason==null||reason.isBlank()||(active&&invoice!=null))throw new IllegalArgumentException("Неверное состояние обслуживания");}
    public Entry phase(Phase value){return new Entry(false,due,reason,invoice.phase(value));}
    public static Entry grace(long due){return new Entry(true,due,"Льготный период",null);}
}
