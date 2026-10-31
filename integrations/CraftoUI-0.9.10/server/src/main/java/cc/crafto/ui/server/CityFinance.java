package cc.crafto.ui.server;

import com.google.gson.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import static cc.crafto.ui.Protocol.*;

/** Actual cash flows, in integer cents, for the TreasuryPlus UTC accounting week. */
record CityFinance(boolean known,String reason,String week,long since,long observed,
                   long income,long expenses,long refunds,long adjustments,boolean pending,
                   Map<String,Long> expenseSources) {
    CityFinance {
        expenseSources=Map.copyOf(expenseSources);
        if(income<0||expenses<0||refunds<0||since<0||observed<since)throw new IllegalArgumentException("Invalid finance totals");
        Math.addExact(income,refunds);
        Math.subtractExact(Math.addExact(income,refunds),expenses);
    }
    static CityFinance unavailable(String reason){return new CityFinance(false,reason,"",0,0,0,0,0,0,false,Map.of());}
    static String weekAt(long now){return Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();}
    static long startOfWeek(long now){return LocalDate.parse(weekAt(now)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();}
    long received(){return Math.addExact(income,refunds);}
    long net(){return Math.subtractExact(received(),expenses);}
    boolean uncertain(){return pending||adjustments!=0;}
    String status(){return !known?"Нет данных":uncertain()?"Нужна сверка":net()>0?"Казна в плюсе":net()<0?"Казна в минусе":"Без изменения";}
    String tone(){return !known?"neutral":uncertain()?"warning":net()>0?"positive":net()<0?"negative":"neutral";}
    static String money(long cents){return CityDashboardView.formatBalance(BigDecimal.valueOf(cents,2).toPlainString());}
    static String signed(long cents){return (cents>0?"+":"")+money(cents);}
    String period(){return known?"Неделя с "+LocalDate.parse(week).format(DateTimeFormatter.ofPattern("dd.MM"))+" · UTC":"Текущая неделя";}
    private String time(long at){return Instant.ofEpochMilli(at).atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm 'UTC'"));}
    String explanation(){
        if(!known)return reason;
        String text="Поступления минус списания, включая возвраты. Фактические операции, не прогноз доходности. Учёт с "+time(since)+".";
        if(refunds!=0)text+=" Возвраты в поступлениях: "+money(refunds)+".";
        if(adjustments!=0)text+=" Неучтённое изменение баланса: "+signed(adjustments)+"; в итог операций не включено.";
        if(pending)text+=" Есть незавершённое списание — требуется сверка.";
        return text;
    }
    JsonObject dashboard(){
        var metrics=new JsonArray();
        metrics.add(object("label","Расходы города","value",known?money(expenses):"—","hint",known?"Списано за эту неделю":"Нет данных","detail",known?"Все учтённые списания городского счёта. "+period():reason,"icon","expense","tone","neutral"));
        metrics.add(object("label","Поступления","value",known?money(received()):"—","hint",known?"Включая взносы и возвраты":"Нет данных","detail",known?"Все поступления городского счёта, включая возвраты: "+money(refunds)+". "+period():reason,"icon","income","tone","neutral"));
        metrics.add(object("label","Итог за неделю","value",known?signed(net()):"—","hint",status(),"detail",explanation(),"icon","treasury","tone",tone()));
        return object("period",period(),"known",known,"metrics",metrics,"observedAt",observed);
    }
    void addDetails(CivicView view){
        if(!known){view.rows.add(CivicView.Row.info("Финансы недоступны",reason));return;}
        view.lines.add(period()+" · учёт с "+time(since));
        view.rows.add(CivicView.Row.info("Расходы города",money(expenses)+" · все записанные списания"));
        view.rows.add(CivicView.Row.info("Поступления",money(received())+" · в том числе возвраты "+money(refunds)));
        view.rows.add(CivicView.Row.info("Итог: "+status(),signed(net())+" · поступления минус расходы"));
        if(uncertain())view.rows.add(CivicView.Row.info("Требуется сверка",explanation()));
        var groups=new TreeMap<String,Long>();
        expenseSources.forEach((key,value)->groups.merge(sourceLabel(key),value,Math::addExact));
        var sorted=groups.entrySet().stream().filter(e->e.getValue()>0)
                .sorted(Map.Entry.<String,Long>comparingByValue().reversed().thenComparing(Map.Entry::getKey)).toList();
        long other=0;
        for(int i=0;i<sorted.size();i++){
            var e=sorted.get(i);if(i>=12){other=Math.addExact(other,e.getValue());continue;}
            view.rows.add(CivicView.Row.info("Расход: "+e.getKey(),money(e.getValue())));
        }
        if(other>0)view.rows.add(CivicView.Row.info("Остальные расходы",money(other)));
    }
    private static String sourceLabel(String key){return switch(key){
        case "tax"->"Налоги";case "trade"->"Караваны и торговля";case "tariff"->"Транзитные пошлины";
        case "construction"->"Строительство";case "upkeep"->"Содержание зданий";case "contracts"->"Городские заказы";
        case "ideology"->"Развитие идеологий";case "espionage"->"Разведка";case "insurance"->"Страхование";
        case "shop"->"Городские лавки";case "pending"->"Отложенные операции";default->"Прочие списания";};}
}
