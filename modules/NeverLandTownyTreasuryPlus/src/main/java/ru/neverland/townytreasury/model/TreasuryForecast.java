package ru.neverland.townytreasury.model;

import java.util.*;

/** Read-only commitments, independent of real bank operations and hypothetical future income. */
public final class TreasuryForecast {
    private TreasuryForecast(){}
    public record Item(String id,String title,Budget category,long cents,boolean known){
        public Item{if(id==null||id.isBlank()||id.length()>128||title==null||title.length()>256||category==null)throw new IllegalArgumentException("Неверная строка прогноза");Money.positive(cents);}
    }
    public static Map<String,Object> calculate(CityLedger city,int seconds,Collection<Item> items,List<String> missing){
        if(seconds<1||seconds>604800||items.size()>1024)throw new IllegalArgumentException("Прогноз: от секунды до семи суток, не более 1024 обязательств");
        long total=0;boolean complete=city.pending()==null&&missing.isEmpty();var costs=new EnumMap<Budget,Long>(Budget.class);var ids=new HashSet<String>();var rows=new ArrayList<Map<String,Object>>();
        for(var item:items){if(!ids.add(item.id()))throw new IllegalArgumentException("Повтор обязательства в прогнозе");total=Money.add(total,item.cents());costs.merge(item.category(),item.cents(),Money::add);complete&=item.known();rows.add(Map.of("id",item.id(),"title",item.title(),"category",item.category().id(),"cents",item.cents(),"known",item.known()));}
        var categoryCosts=new LinkedHashMap<String,Long>();var deficits=new LinkedHashMap<String,Long>();
        for(var b:Budget.values()){long planned=costs.getOrDefault(b,0L);categoryCosts.put(b.id(),planned);deficits.put(b.id(),city.enabled()?Math.max(0,planned-city.funds().get(b)):b==Budget.FREE?Math.max(0,total-Math.max(0,city.balance())):0L);}
        var result=new LinkedHashMap<String,Object>();result.put("seconds",seconds);result.put("balanceCents",city.balance());result.put("plannedCents",total);result.put("afterCents",Math.subtractExact(city.balance(),total));result.put("shortageCents",Math.max(0,Math.subtractExact(total,city.balance())));result.put("categoryCents",Map.copyOf(categoryCosts));result.put("categoryShortages",Map.copyOf(deficits));result.put("complete",complete);result.put("issues",List.copyOf(missing));result.put("items",List.copyOf(rows));result.put("revision",city.revision());return Collections.unmodifiableMap(result);
    }
}
