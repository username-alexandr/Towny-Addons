package ru.neverland.townytreasury.model;
import java.util.*;
import java.time.*;
import java.time.temporal.*;
public record Week(Map<String,Long> income,Map<String,Long> expenses,Map<Budget,Long> categories,long refunds,long adjustments,Map<String,Long> production,boolean productionKnown) {
    public Week{income=valid(income);expenses=valid(expenses);categories=Budget.amounts(categories);Money.positive(refunds);Money.valid(adjustments);production=quantities(production);}
    private static Map<String,Long> quantities(Map<String,Long> values){values.forEach((key,value)->{if(key==null||!key.matches("[a-z0-9_/-]{1,64}")||value==null||value<0)throw new IllegalArgumentException("Некорректный итог ресурсов");});return Map.copyOf(values);}
    private static Map<String,Long> valid(Map<String,Long> values){values.forEach((key,value)->{if(key==null||!key.matches("[a-z0-9_/-]{1,64}"))throw new IllegalArgumentException("Некорректная строка отчёта");Money.positive(value);});return Map.copyOf(values);}
    public static Week empty(){return new Week(Map.of(),Map.of(),Map.of(),0,0,Map.of(),false);}
    public static String key(long at){return Instant.ofEpochMilli(at).atZone(ZoneOffset.UTC).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();}
    public static String previous(long at){return LocalDate.parse(key(at)).minusWeeks(1).toString();}
    public static void validateKey(String key){var date=LocalDate.parse(key);if(date.getDayOfWeek()!=DayOfWeek.MONDAY)throw new IllegalArgumentException("Неверная неделя");}
    public long totalIncome(){return income.values().stream().reduce(0L,Money::add);}public long totalExpense(){return expenses.values().stream().reduce(0L,Money::add);}
    public Week flow(long amount,boolean deposit,Budget category,String source,boolean refund){
        var incoming=new HashMap<>(income);var outgoing=new HashMap<>(expenses);var cats=new EnumMap<Budget,Long>(Budget.class);cats.putAll(categories);
        if(!refund){var map=deposit?incoming:outgoing;map.put(source,Money.add(map.getOrDefault(source,0L),amount));}
        if(!deposit)cats.put(category,Money.add(cats.get(category),amount));
        return new Week(incoming,outgoing,cats,refund?Money.add(refunds,amount):refunds,adjustments,production,productionKnown);
    }
    public Week adjustment(long value){return new Week(income,expenses,categories,refunds,Money.add(adjustments,value),production,productionKnown);}
    public Week produced(Map<String,Long> values){return new Week(income,expenses,categories,refunds,adjustments,values,true);}
}
