package ru.neverland.townypolicies.model;
import java.util.*;
import ru.neverland.integration.PolicyEffects;
/** A proposal is computed completely before one repository commit; selection never changes a partial group. */
public final class PolicyPresets {
    private PolicyPresets(){}
    public record Preset(String id,String name,String icon,String description,Map<String,String> modes){
        public Preset{if(id==null||!id.matches("[a-z_]{1,32}")||name==null||name.isBlank()||name.length()>80||icon==null||!icon.matches("[A-Z0-9_]{1,80}")||description==null||description.isBlank()||description.length()>200)throw new IllegalArgumentException("Неверный набор политик");modes=Map.copyOf(modes);if(modes.isEmpty()||!PolicyEffects.GROUPS.containsAll(modes.keySet()))throw new IllegalArgumentException("Укажите известные политики набора");}
    }
    public record Conflict(String id,Map<String,String> modes,String reason){
        public Conflict{if(id==null||!id.matches("[a-z_]{1,48}")||reason==null||reason.isBlank()||reason.length()>200)throw new IllegalArgumentException("Неверное правило совместимости");modes=Map.copyOf(modes);if(modes.size()<2||!PolicyEffects.GROUPS.containsAll(modes.keySet()))throw new IllegalArgumentException("Конфликт требует двух политик");}
        public boolean matches(Map<String,String> selection){return modes.entrySet().stream().allMatch(e->e.getValue().equals(selection.get(e.getKey())));}
    }
    public record Plan(CityPolicies before,CityPolicies after,Map<String,String> selected,List<String> changed,List<String> blockers,List<String> warnings){
        public Plan{selected=Map.copyOf(selected);changed=List.copyOf(changed);blockers=List.copyOf(blockers);warnings=List.copyOf(warnings);}
        public boolean ready(){return !changed.isEmpty()&&blockers.isEmpty();}
    }
    public static Plan plan(CityPolicies state,Map<String,PolicyGroup> groups,Preset preset,List<Conflict> conflicts,Map<String,String> unavailable,long now,long cooldown,UUID actor,String actorName,boolean admin){
        var modes=new TreeMap<String,String>();groups.forEach((id,g)->modes.put(id,state.mode(g)));var changed=new ArrayList<String>();var blockers=new ArrayList<String>();var warnings=new ArrayList<String>();
        for(var e:new TreeMap<>(preset.modes()).entrySet()){
            var group=groups.get(e.getKey());if(group==null||!group.options().containsKey(e.getValue()))throw new IllegalArgumentException("Набор ссылается на неизвестный режим");
            modes.put(e.getKey(),e.getValue());String reason=unavailable.getOrDefault(e.getKey(),"");if(!reason.isEmpty()&&!e.getValue().equals(group.standard()))blockers.add(group.name()+": "+reason);
            if(!state.mode(group).equals(e.getValue())){changed.add(e.getKey());if(!admin&&now<state.next(e.getKey()))blockers.add(group.name()+": до смены осталось "+((state.next(e.getKey())-now+59999)/60000)+" мин.");}
        }
        for(var rule:conflicts)if(rule.matches(modes))blockers.add(rule.reason());
        if(changed.isEmpty())blockers.add("Режимы этого набора уже выбраны");
        var positive=new HashSet<String>();var negative=new HashSet<String>();for(var entry:modes.entrySet()){var p=groups.get(entry.getKey()).options().get(entry.getValue());if(p==null)continue;if(p.effect("production")>0)positive.addAll(p.production());if(p.effect("production")<0)negative.addAll(p.production());}
        boolean offset=!positive.isEmpty()&&!negative.isEmpty()&&(positive.contains("*")||negative.contains("*")||positive.stream().anyMatch(negative::contains));if(offset)warnings.add("Мобилизация снижает выпуск в том числе у производств с субсидиями");
        CityPolicies next=state;if(blockers.isEmpty())for(String group:changed)next=SelectionPolicy.choose(next,groups.get(group),modes.get(group),now,cooldown,next.revision(),actor,actorName,admin,true);
        return new Plan(state,next,modes,changed,blockers,warnings);
    }
    public static double multiplier(Map<String,PolicyGroup> groups,Map<String,String> selected,String project,boolean upkeep){
        java.math.BigDecimal value=java.math.BigDecimal.ONE;for(var e:groups.entrySet()){var option=e.getValue().options().get(selected.getOrDefault(e.getKey(),e.getValue().standard()));if(option!=null&&e.getValue().enabled()&&option.targets(project,upkeep))value=value.multiply(java.math.BigDecimal.ONE.add(java.math.BigDecimal.valueOf(option.effect(upkeep?"upkeep":"production"))));}
        return PolicyEffects.bound(value.doubleValue(),upkeep?1:.25,upkeep?3:2,1);
    }
}
