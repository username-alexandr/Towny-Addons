package ru.neverland.townyresearch.model;
import java.util.*;
import static ru.neverland.townyresearch.model.CityStudy.Phase.*;
/** Durable intent precedes every external reservation mutation. Progress uses active server seconds only. */
public final class ResearchProcessor {
    public interface Store { CityStudy get(UUID town);void put(UUID town,CityStudy value)throws Exception; }
    public interface Gateway { String status(UUID invoice)throws Exception;boolean reserve(UUID invoice,UUID town,long cost)throws Exception;void settle(UUID invoice,boolean consume)throws Exception;void forget(UUID invoice)throws Exception; }
    private final Store store;private final Gateway gateway;
    public ResearchProcessor(Store store,Gateway gateway){this.store=store;this.gateway=gateway;}
    public void begin(UUID town,Technology technology,Map<String,Integer> buildings)throws Exception{
        var state=store.get(town);if(state.active()!=null)throw new IllegalArgumentException("В городе уже есть исследование");if(!state.cleanup().isEmpty())throw new IllegalStateException("Ожидается завершение прошлой операции");
        int level=state.learned().getOrDefault(technology.id(),0)+1;if(!technology.enabled()||level>technology.levels().size())throw new IllegalArgumentException("Технология недоступна или полностью изучена");var quote=technology.levels().get(level-1);
        if(!ready(quote.buildings(),buildings)||!ready(quote.requires(),state.learned()))throw new IllegalArgumentException("Не выполнены требования зданий или технологий");
        store.put(town,state.study(new CityStudy.Study(UUID.randomUUID(),technology.id(),level,quote.knowledge(),quote.seconds(),quote.seconds(),quote.buildings(),PREPARED)));
        tick(town,0,true);
    }
    public static boolean ready(Map<String,Integer> required,Map<String,Integer> actual){return required.entrySet().stream().allMatch(e->actual.getOrDefault(e.getKey(),0)>=e.getValue());}
    public void cancel(UUID town)throws Exception{var state=store.get(town);if(state.active()==null)throw new IllegalArgumentException("Нет текущего исследования");if(state.active().phase()==COMPLETING)throw new IllegalStateException("Исследование уже завершается; дождитесь сохранения результата");store.put(town,state.study(state.active().phase(CANCELLING)));tick(town,0,false);}
    public void tick(UUID town,int seconds,boolean ready)throws Exception{
        if(seconds<0||seconds>60)throw new IllegalArgumentException("Шаг расчёта: 0..60 секунд");
        for(UUID invoice:store.get(town).cleanup()){gateway.forget(invoice);store.put(town,store.get(town).forget(invoice));}
        var state=store.get(town);var s=state.active();if(s==null)return;
        if(s.phase()==CANCELLING){String status=gateway.status(s.invoice());if(status.equals("HELD"))gateway.settle(s.invoice(),false);else if(!Set.of("NONE","RELEASED").contains(status))throw new IllegalStateException("Нельзя вернуть уже использованные знания");store.put(town,state.finish(false));return;}
        if(!ready)return;
        if(s.phase()==PREPARED){String status=gateway.status(s.invoice());if(status.equals("NONE")){if(!gateway.reserve(s.invoice(),town,s.cost()))return;}else if(!status.equals("HELD"))throw new IllegalStateException("Несогласованная квитанция исследования");store.put(town,state.study(s.phase(RUNNING)));return;}
        if(s.phase()==RUNNING){if(!gateway.status(s.invoice()).equals("HELD"))throw new IllegalStateException("Резерв знаний отсутствует");if(seconds==0)return;store.put(town,state.study(s.advance(seconds)));state=store.get(town);s=state.active();}
        if(s.phase()==COMPLETING){String status=gateway.status(s.invoice());if(status.equals("HELD"))gateway.settle(s.invoice(),true);else if(!status.equals("CONSUMED"))throw new IllegalStateException("Нет подтверждения расхода знаний");store.put(town,state.finish(true));}
    }
}
