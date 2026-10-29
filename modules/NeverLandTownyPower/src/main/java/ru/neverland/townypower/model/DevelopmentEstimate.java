package ru.neverland.townypower.model;

import java.util.*;
import ru.neverland.townypower.config.PowerSettings;
import ru.neverland.townypower.model.PowerEngine.Building;

/** Conditional steady-state scenario. Never reserves/discharges energy or promises live power. */
public final class DevelopmentEstimate {
    private DevelopmentEstimate(){}
    public static Map<String,Integer> levels(Map<String,Integer> input){
        Objects.requireNonNull(input);if(input.size()>512)throw new IllegalArgumentException("Слишком много зданий в плане");var out=new TreeMap<String,Integer>();
        for(var e:input.entrySet()){TownPowerState.valid(e.getKey());if(e.getValue()==null||e.getValue()<1||e.getValue()>5)throw new IllegalArgumentException("Прогноз поддерживает итоговые уровни от I до V");out.put(e.getKey(),e.getValue());}return Map.copyOf(out);
    }
    public static Map<String,Object> calculate(PowerSettings settings,Map<String,Building> current,Map<String,Integer> planned,TownPowerState controls,Map<String,Boolean> allowed){
        planned=levels(planned);if(current.size()>512)throw new IllegalArgumentException("Слишком много зданий в городе");var future=new HashMap<>(current);var conditional=new TreeSet<String>();
        for(var e:planned.entrySet()){var old=current.get(e.getKey());if(old!=null&&e.getValue()<old.level())throw new IllegalArgumentException("Прогноз строительства не поддерживает понижение уровня");
            if(old==null||e.getValue()>old.level()){conditional.add(e.getKey());future.put(e.getKey(),new Building(e.getValue(),old==null||old.owned(),old==null||old.maintained(),old==null?1:old.districtBonus(),Boolean.TRUE.equals(allowed.get(e.getKey()))));}
        }
        var before=PowerEngine.calculate(settings.profiles(),current,controls);var after=PowerEngine.calculate(settings.profiles(),future,controls);var rows=new ArrayList<Map<String,Object>>();var ids=new TreeSet<>(current.keySet());ids.addAll(planned.keySet());
        for(String id:ids){var p=settings.profiles().get(id);var old=before.buildings().get(id);var next=after.buildings().get(id);var row=new LinkedHashMap<String,Object>();row.put("project",id);row.put("title",p==null?id:p.name());row.put("beforeLevel",current.containsKey(id)?current.get(id).level():0);row.put("afterLevel",future.containsKey(id)?future.get(id).level():0);row.put("conditional",conditional.contains(id));row.put("before",allocation(old));row.put("after",allocation(next));row.put("reason",next==null?"Профиль энергетики не задан":next.status().title);rows.add(Map.copyOf(row));}
        var result=new LinkedHashMap<String,Object>();result.put("known",true);result.put("reason","Условный расчёт после завершения; фактическое подключение проверяется отдельно");result.put("periodSeconds",settings.interval());result.put("before",grid(before));result.put("after",grid(after));result.put("delta",Map.of("generation",after.generation()-before.generation(),"demand",after.demand()-before.demand(),"deficit",after.deficit()-before.deficit()));result.put("buildings",List.copyOf(rows));result.put("conditionalProjects",List.copyOf(conditional));result.put("assumptions",List.of("Новые здания предполагаются завершёнными на своей территории с оплаченным содержанием; это не подтверждение подключения","Для уже существующих зданий сохраняются текущие владение, содержание, остановки и приоритеты","Районный бонус сохраняется у существующего здания; для нового применяется базовая выработка без бонуса","Заряд накопителей не расходуется и в постоянный баланс не включён"));return Map.copyOf(result);
    }
    private static Map<String,Object> grid(PowerEngine.Grid grid){return Map.of("generation",grid.generation(),"demand",grid.demand(),"deficit",grid.deficit());}
    private static Map<String,Object> allocation(PowerEngine.Allocation a){return a==null?Map.of("generation",0L,"demand",0L,"status","UNCONFIGURED"):Map.of("generation",a.generation(),"demand",a.demand(),"status",a.status().name());}
}
