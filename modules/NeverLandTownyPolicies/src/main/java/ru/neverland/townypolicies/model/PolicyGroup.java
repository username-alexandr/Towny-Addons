package ru.neverland.townypolicies.model;
import java.util.*;
import ru.neverland.integration.PolicyEffects;
public record PolicyGroup(String id,String name,String icon,String standard,boolean enabled,Map<String,PolicyOption> options){
    public PolicyGroup{options=Collections.unmodifiableMap(new LinkedHashMap<>(options));if(!PolicyEffects.GROUPS.contains(id)||name==null||name.isBlank()||icon==null||icon.isBlank()||options.size()!=3||!options.containsKey(standard))throw new IllegalArgumentException("Неверная группа политик");var base=options.get(standard);if(!base.effects().isEmpty()||!base.requires().isEmpty())throw new IllegalArgumentException("Обычный режим должен быть нейтральным");}
}
