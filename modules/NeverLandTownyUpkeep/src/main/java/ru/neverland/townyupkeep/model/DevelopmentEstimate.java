package ru.neverland.townyupkeep.model;

import java.util.*;
import java.math.BigDecimal;
import ru.neverland.townyupkeep.config.UpkeepSettings;

/** Full-period rate comparison only. Does not prepare an invoice or advance paid time. */
public final class DevelopmentEstimate {
    private DevelopmentEstimate(){}
    public static Map<String,Integer> levels(Map<String,Integer> input){
        Objects.requireNonNull(input);if(input.size()>512)throw new IllegalArgumentException("Слишком много зданий в плане");
        var out=new TreeMap<String,Integer>();for(var e:input.entrySet()){
            if(e.getKey()==null||!e.getKey().matches("[a-z0-9_-]{1,64}")||e.getValue()==null||e.getValue()<1||e.getValue()>5)throw new IllegalArgumentException("Прогноз поддерживает итоговые уровни от I до V");out.put(e.getKey(),e.getValue());
        }return Map.copyOf(out);
    }
    public static Map<String,Object> calculate(UpkeepSettings settings,int plots,int residents,Map<String,Integer> current,Map<String,Integer> planned,Set<String> paused,Map<String,Boolean> allowed,Map<String,Double> policy){
        current=levels(current);planned=levels(planned);var after=new TreeMap<>(current);var ids=new TreeSet<>(current.keySet());ids.addAll(planned.keySet());
        for(var e:planned.entrySet()){if(e.getValue()<current.getOrDefault(e.getKey(),0))throw new IllegalArgumentException("Прогноз строительства не поддерживает понижение уровня");if(Boolean.TRUE.equals(allowed.get(e.getKey())))after.put(e.getKey(),e.getValue());}
        int oldLevels=current.values().stream().mapToInt(Integer::intValue).sum(),newLevels=after.values().stream().mapToInt(Integer::intValue).sum();
        var oldFactor=settings.factor(plots,residents,oldLevels);var newFactor=settings.factor(plots,residents,newLevels);long oldMoney=0,newMoney=0;var oldResources=new TreeMap<String,Long>();var newResources=new TreeMap<String,Long>();var rows=new ArrayList<Map<String,Object>>();
        for(String id:ids){var profile=settings.profiles().get(id);int oldLevel=current.getOrDefault(id,0),newLevel=after.getOrDefault(id,0);boolean specialized=Boolean.TRUE.equals(allowed.get(id)),stopped=paused.contains(id),enabled=profile!=null&&profile.enabled();
            double multiplier=policy.getOrDefault(id,1.0);if(!Double.isFinite(multiplier)||multiplier<1||multiplier>3)throw new IllegalArgumentException("Не удалось проверить текущую стоимость содержания");
            Cost oldCost=rate(profile,oldLevel,oldFactor,multiplier,enabled&&specialized&&!stopped),newCost=rate(profile,newLevel,newFactor,multiplier,enabled&&specialized&&!stopped);
            oldMoney=Math.addExact(oldMoney,oldCost.money());newMoney=Math.addExact(newMoney,newCost.money());merge(oldResources,oldCost.resources());merge(newResources,newCost.resources());
            var row=new LinkedHashMap<String,Object>();row.put("project",id);row.put("title",profile==null?id:profile.name());row.put("beforeLevel",oldLevel);row.put("afterLevel",planned.getOrDefault(id,newLevel));row.put("before",amount(oldCost.money(),oldCost.resources()));row.put("after",amount(newCost.money(),newCost.resources()));row.put("paused",stopped);row.put("eligible",enabled&&specialized&&!stopped);row.put("policyMultiplier",multiplier);row.put("reason",!specialized?"Нужна подходящая специализация":stopped?"Содержание остановлено городом":!enabled?"Для этого здания плата отключена или не задана":"Полная стоимость одного периода");rows.add(Map.copyOf(row));
        }
        var before=new LinkedHashMap<>(amount(oldMoney,oldResources));before.put("sizeFactor",oldFactor.toPlainString());before.put("buildingLevels",oldLevels);var future=new LinkedHashMap<>(amount(newMoney,newResources));future.put("sizeFactor",newFactor.toPlainString());future.put("buildingLevels",newLevels);
        var result=new LinkedHashMap<String,Object>();result.put("known",true);result.put("reason","Стоимость полного периода по текущим правилам, после завершения строительства");result.put("periodSeconds",settings.period());result.put("before",Map.copyOf(before));result.put("after",Map.copyOf(future));result.put("delta",amount(Math.subtractExact(newMoney,oldMoney),difference(oldResources,newResources)));result.put("buildings",List.copyOf(rows));result.put("assumptions",List.of("Новые площадки должны полностью принадлежать городу","Число жителей, участков, политики, специализация и остановки остаются текущими","Открытые счета и уже оплаченный срок не пересчитываются; льгота нового здания не включена"));return Map.copyOf(result);
    }
    private static Cost rate(Profile p,int level,BigDecimal factor,double multiplier,boolean charge){return !charge||level==0?new Cost(0,Map.of()):p.cost().multiply(level,factor).policy(multiplier);}
    private static Map<String,Object> amount(long cents,Map<String,Long> resources){return Map.of("cents",cents,"resources",Map.copyOf(resources));}
    private static void merge(Map<String,Long> target,Map<String,Long> values){values.forEach((id,n)->target.merge(id,n,Math::addExact));}
    private static Map<String,Long> difference(Map<String,Long> before,Map<String,Long> after){var keys=new TreeSet<>(before.keySet());keys.addAll(after.keySet());var out=new TreeMap<String,Long>();for(String key:keys)out.put(key,Math.subtractExact(after.getOrDefault(key,0L),before.getOrDefault(key,0L)));return out;}
}
