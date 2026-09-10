package ru.neverland.townypolicies.model;
import java.util.*;
public final class SelectionPolicy {
    private SelectionPolicy(){}
    public static boolean manager(boolean own,boolean mayor,boolean delegate){return own&&(mayor||delegate);}
    public static CityPolicies choose(CityPolicies state,PolicyGroup group,String mode,long now,long cooldown,long revision,UUID actor,String actorName,boolean admin,boolean ready){
        if(now<1||cooldown<0||state.revision()!=revision)throw new IllegalArgumentException("Выбор устарел. Откройте меню заново.");
        if(!group.options().containsKey(mode)||(!group.enabled()&&!mode.equals(group.standard())))throw new IllegalArgumentException("Режим отключён или не найден");
        if(state.mode(group).equals(mode))throw new IllegalArgumentException("Этот режим уже выбран");
        if(!ready&&!mode.equals(group.standard()))throw new IllegalArgumentException("Не готовы необходимые городские системы");
        if(!admin&&now<state.next(group.id()))throw new IllegalArgumentException("Срок ожидания смены этой политики ещё не прошёл");
        var next=new HashMap<>(state.choices());next.put(group.id(),new CityPolicies.Choice(mode,now,Math.addExact(now,cooldown)));var history=new ArrayList<>(state.history());history.add(new CityPolicies.Change(now,group.id(),state.mode(group),mode,actor,actorName));if(history.size()>30)history.remove(0);return new CityPolicies(Math.addExact(revision,1),next,history);
    }
}
