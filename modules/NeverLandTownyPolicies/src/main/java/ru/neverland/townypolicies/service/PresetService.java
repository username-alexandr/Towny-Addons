package ru.neverland.townypolicies.service;
import java.util.*;
import java.util.concurrent.CompletionStage;
import org.bukkit.entity.Player;
import ru.neverland.townypolicies.config.*;
import ru.neverland.townypolicies.model.*;
import ru.neverland.townypolicies.model.PolicyPresets.*;
/** Read-only comparison and whole-set confirmation share the same proposal engine. */
public final class PresetService {
    public record Offer(UUID town,String preset,long revision,PoliciesSettings policies,PresetSettings presets,long expires,boolean admin){}
    private final PoliciesService owner;private volatile PresetSettings settings;private final Map<UUID,Offer> offers=new HashMap<>();
    PresetService(PoliciesService owner,PresetSettings settings){this.owner=owner;this.settings=settings;}
    public PresetSettings settings(){return settings;}
    public void reload(PresetSettings next){settings=next;clearAll();}
    public void prune(Set<UUID> towns){long now=System.currentTimeMillis();offers.values().removeIf(o->o.expires()<now||!towns.contains(o.town()));}
    public void clear(UUID player){offers.remove(player);}public void clearAll(){offers.clear();}
    public Preset find(String id){var preset=settings.presets().get(id);if(!settings.enabled()||preset==null)throw new IllegalArgumentException("Набор политик сейчас недоступен");return preset;}
    private Map<String,String> modes(CityPolicies state){var modes=new LinkedHashMap<String,String>();owner.settings().groups().forEach((id,g)->modes.put(id,state.mode(g)));return modes;}
    public void check(CityPolicies next){if(!settings.enabled())return;var selected=modes(next);for(var conflict:settings.conflicts())if(conflict.matches(selected))throw new IllegalArgumentException(conflict.reason());}
    public Plan plan(UUID town,String id,UUID actor,String actorName,boolean admin){
        if(town==null||owner.policies(town).isEmpty())throw new IllegalArgumentException("Город не найден или его данные ещё не готовы");var preset=find(id);var policies=owner.settings();var reasons=new LinkedHashMap<String,String>();preset.modes().forEach((group,mode)->{var g=policies.groups().get(group);reasons.put(group,owner.unavailable(town,g,g.options().get(mode)));});
        return PolicyPresets.plan(owner.state(town),policies.groups(),preset,settings.conflicts(),reasons,System.currentTimeMillis(),policies.cooldownMillis(),actor,actorName,admin);
    }
    public Plan preview(UUID town,String id){return plan(town,id,new UUID(0,0),"Предпросмотр",false);}
    public Collection<Map<String,Object>> plans(UUID town){
        if(!settings.enabled()||owner.policies(town).isEmpty())return List.of();var rows=new ArrayList<Map<String,Object>>();for(var preset:settings.presets().values()){var plan=preview(town,preset.id());rows.add(Map.of("id",preset.id(),"name",preset.name(),"description",preset.description(),"ready",plan.ready()&&!owner.saving(),"changed",plan.changed(),"blockers",plan.blockers(),"warnings",plan.warnings(),"revision",plan.before().revision()));}return List.copyOf(rows);
    }
    private Map<String,String> effective(UUID town,Map<String,String> selected){var actual=new LinkedHashMap<String,String>();owner.settings().groups().forEach((id,g)->{var mode=selected.getOrDefault(id,g.standard());var option=g.options().get(mode);actual.put(id,!g.enabled()||option==null||!owner.unavailable(town,g,option).isEmpty()?g.standard():mode);});return actual;}
    public Map<String,Object> impact(UUID town,String id,String project){
        if(project==null||!project.matches("[a-z0-9_]{1,64}"))throw new IllegalArgumentException("Неизвестное здание");var plan=preview(town,id);var groups=owner.settings().groups();var before=effective(town,modes(plan.before()));var after=effective(town,plan.selected());
        return Map.of("project",project,"ready",plan.ready()&&!owner.saving(),"beforeProduction",PolicyPresets.multiplier(groups,before,project,false),"afterProduction",PolicyPresets.multiplier(groups,after,project,false),"beforeUpkeep",PolicyPresets.multiplier(groups,before,project,true),"afterUpkeep",PolicyPresets.multiplier(groups,after,project,true));
    }
    public CompletionStage<UUID> choose(Player p,UUID town,String id,long revision,PoliciesSettings quoted,PresetSettings quotedPresets,boolean admin){
        if(!ru.neverland.core.SuiteState.held())throw new IllegalStateException("Нужна согласованная операция выбора");owner.authorize(p,town,admin);if(quoted!=owner.settings()||quotedPresets!=settings||owner.state(town).revision()!=revision)throw new IllegalArgumentException("Условия изменились. Откройте набор заново");
        var plan=plan(town,id,p.getUniqueId(),p.getName(),admin);if(!plan.ready())throw new IllegalArgumentException(String.join("; ",plan.blockers()));return owner.persist(p,town,plan.after());
    }
    public void offer(Player p,UUID town,String id,boolean admin){if(!ru.neverland.core.SuiteState.held())throw new IllegalStateException("Нужна согласованная операция выбора");owner.authorize(p,town,admin);var plan=plan(town,id,p.getUniqueId(),p.getName(),admin);if(!plan.ready())throw new IllegalArgumentException(String.join("; ",plan.blockers()));owner.clearOffer(p.getUniqueId());offers.put(p.getUniqueId(),new Offer(town,id,plan.before().revision(),owner.settings(),settings,System.currentTimeMillis()+60000,admin));}
    public boolean hasOffer(Player p){var offer=offers.get(p.getUniqueId());if(offer!=null&&offer.expires()<System.currentTimeMillis()){offers.remove(p.getUniqueId());return false;}return offer!=null;}
    public CompletionStage<UUID> confirm(Player p){var offer=offers.remove(p.getUniqueId());if(offer==null||offer.expires()<System.currentTimeMillis())throw new IllegalArgumentException("Подтверждение набора истекло");return choose(p,offer.town(),offer.preset(),offer.revision(),offer.policies(),offer.presets(),offer.admin());}
}
