package ru.neverland.townyupkeep.api;
import ru.neverland.townyupkeep.model.*;
public record UpkeepSnapshot(Entry.Key key,String name,String icon,int level,int priority,boolean active,String status,long seconds,Cost cost,Entry.Invoice invoice) {}
