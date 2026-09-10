package ru.neverland.townyresources.model;
import java.util.*;
public record BuildingProfile(String id,String name,String icon,boolean enabled,int minimumLevel,int maximumLevel,int priority,
                              Map<Resource,Long> produces,Map<Resource,Long> consumes,Map<Resource,Long> capacity) {
    public BuildingProfile {
        if(id==null||!id.matches("[a-z0-9_-]{1,64}")||name==null||name.isBlank()||icon==null||icon.isBlank()) throw new IllegalArgumentException("Неверный профиль здания");
        if(minimumLevel<1||maximumLevel<minimumLevel||maximumLevel>5||priority<0||priority>100) throw new IllegalArgumentException("Уровень или приоритет профиля "+id);
        produces=Amounts.copy(produces);consumes=Amounts.copy(consumes);capacity=Amounts.copy(capacity);
    }
    public int levels(int completed) { return enabled&&completed>=minimumLevel ? Math.min(completed,maximumLevel)-minimumLevel+1 : 0; }
}
