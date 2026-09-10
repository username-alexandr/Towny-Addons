package ru.neverland.townypower.model;
import java.util.*;
/** Stateless capacity allocation. No stored energy, offline accrual or feedback from the previous power result. */
public final class PowerEngine {
    private PowerEngine(){}
    public record Building(int level,boolean owned,boolean maintained,double districtBonus) {
        public Building{if(level<0||level>5)throw new IllegalArgumentException("Уровень: 0..5");}
    }
    public enum Status {
        NOT_BUILT("Не построено"), FOREIGN("Площадка не принадлежит городу"), UNPAID("Содержание не оплачено"),
        STOPPED("Остановлено городом"), EXEMPT("Энергоснабжение отключено в настройках"),
        ASSEMBLY("Энергоузел ещё не смонтирован"), PRODUCING("Вырабатывает энергию"),
        LOW_LEVEL("На этом уровне энергия не требуется"), SUPPLIED("Питание подано"), SHORTAGE("Не хватает энергии"), UNAVAILABLE("Расчёт сети недоступен");
        public final String title;Status(String title){this.title=title;}
    }
    public record Allocation(String project,int level,int priority,boolean powered,long generation,long demand,long supplied,Status status) {}
    public record Grid(long generation,long demand,long supplied,Map<String,Allocation> buildings) {
        public Grid{buildings=Collections.unmodifiableMap(new LinkedHashMap<>(buildings));if(generation<0||demand<0||supplied<0||supplied>generation||supplied>demand)throw new IllegalArgumentException("Нарушен баланс мощности");}
        public long spare(){return generation-supplied;}public long deficit(){return demand-supplied;}
    }
    public static Grid calculate(Map<String,PowerProfile> profiles,Map<String,Building> buildings,TownPowerState town){
        List<PowerProfile> ordered=new ArrayList<>(profiles.values());ordered.sort(Comparator.comparingInt((PowerProfile p)->town.priorities().getOrDefault(p.id(),p.priority())).thenComparing(PowerProfile::id));
        Map<String,Allocation> result=new LinkedHashMap<>();long generation=0,demand=0,used=0;
        for(var p:ordered){var b=buildings.get(p.id());int level=b==null?0:b.level(),priority=town.priorities().getOrDefault(p.id(),p.priority());
            Status status=level==0?Status.NOT_BUILT:!b.owned()?Status.FOREIGN:!b.maintained()?Status.UNPAID:!p.enabled()?Status.EXEMPT:town.stopped().contains(p.id())&&p.relevant()?Status.STOPPED:null;
            long output=0,need=0;boolean active=false;
            if(status==Status.EXEMPT)active=true;
            if(status==null){output=p.generation(level);need=p.demand(level);double bonus=Double.isFinite(b.districtBonus())?Math.max(1,Math.min(3,b.districtBonus())):1;
                output=(long)Math.floor(output*bonus);generation=Math.addExact(generation,output);demand=Math.addExact(demand,need);
                status=p.producer()?(output>0?Status.PRODUCING:Status.ASSEMBLY):need==0?Status.LOW_LEVEL:Status.SHORTAGE;active=need==0;
            }
            result.put(p.id(),new Allocation(p.id(),level,priority,active,output,need,0,status));
        }
        // Allocate whole-building demand. Skip an unaffordable consumer so a smaller one can still work.
        for(var p:ordered){var a=result.get(p.id());if(a.status()!=Status.SHORTAGE)continue;
            if(a.demand()<=generation-used){used=Math.addExact(used,a.demand());result.put(p.id(),new Allocation(a.project(),a.level(),a.priority(),true,0,a.demand(),a.demand(),Status.SUPPLIED));}
        }
        return new Grid(generation,demand,used,result);
    }
}
