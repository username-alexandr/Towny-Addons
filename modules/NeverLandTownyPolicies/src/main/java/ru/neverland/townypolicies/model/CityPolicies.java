package ru.neverland.townypolicies.model;
import java.util.*;
import ru.neverland.integration.PolicyEffects;
public record CityPolicies(long revision,Map<String,Choice> choices,List<Change> history){
    public record Choice(String mode,long chosenAt,long nextChangeAt){public Choice{if(mode==null||!mode.matches("[a-z_]{1,32}")||chosenAt<1||nextChangeAt<chosenAt)throw new IllegalArgumentException("Неверный сохранённый указ");}}
    public record Change(long at,String group,String before,String after,UUID actor,String actorName){public Change{if(at<1||!PolicyEffects.GROUPS.contains(group)||before==null||after==null||actor==null||actorName==null||actorName.isBlank())throw new IllegalArgumentException("Неверная история указов");}}
    public CityPolicies{choices=Map.copyOf(choices);history=List.copyOf(history);if(revision<0||history.size()>30||!PolicyEffects.GROUPS.containsAll(choices.keySet())||(revision==0&&!choices.isEmpty()))throw new IllegalArgumentException("Неверная база политик");}
    public static CityPolicies empty(){return new CityPolicies(0,Map.of(),List.of());}
    public String mode(PolicyGroup group){var c=choices.get(group.id());return c==null?group.standard():c.mode();}
    public long next(String group){var c=choices.get(group);return c==null?0:c.nextChangeAt();}
}
