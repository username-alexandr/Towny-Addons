package cc.crafto.ui.server;

import java.util.*;
import com.google.gson.*;
import static cc.crafto.ui.Protocol.*;
import static cc.crafto.ui.server.CivicView.*;

/** Presentation only: no I/O, command execution or client-supplied action identifiers. */
final class CityDashboardView {
    record Access(String builds,String contracts,String treasury,String history){}
    static void finances(CivicView v,CityFinance finance,String access){
        var visible=access.isBlank()?finance:CityFinance.unavailable(access);
        v.city.add("finance",visible.dashboard());
        v.financeLink=new Row("Финансы города",visible.period(),"",new Action("route","treasury"),access);
    }
    static void populate(CivicView v,TownyCivic.Context c,CityBuildReader.Result b,CivicData.Snapshot d,
                         String balance,int residents,String limit,String section,Access access){
        var active=new ArrayList<Row>();var buildings=new ArrayList<Row>();var tasks=new ArrayList<Row>();
        for(var site:b.sites()){
            var row=new Row(site.label(),site.known()?"Установлено: "+site.placed()+" / "+site.total():"Подойдите к площадке для подсчёта","",new Action("build",site.id()),access.builds());
            decorate(v,row,"build","Строительство",site.known()?"В работе":"Нет подсчёта",site.placed(),site.total(),site.known());active.add(row);buildings.add(row);
        }
        b.levels().entrySet().stream().filter(e->e.getValue()>0&&b.sites().stream().noneMatch(s->s.id().equals(e.getKey())))
            .sorted(Comparator.comparing((Map.Entry<String,Integer> e)->b.names().getOrDefault(e.getKey(),e.getKey())).thenComparing(Map.Entry::getKey)).forEach(e->{
                var row=new Row(b.names().getOrDefault(e.getKey(),e.getKey()),"Уровень "+e.getValue()+" · открыть управление","",new Action("build",e.getKey()),access.builds());
                decorate(v,row,"build","Постройка","Построено",0,0,false);buildings.add(row);
            });
        d.contracts().stream().sorted(Comparator.comparing((CivicData.Contract t)->!(t.goal()>0&&t.progress()>=t.goal())).thenComparing(CivicData.Contract::title).thenComparing(CivicData.Contract::id)).forEach(t->{
            int contribution=t.contributions().getOrDefault(c.player().toString(),0);
            var row=new Row(t.title(),"Прогресс: "+t.progress()+" / "+t.goal()+" · ваш вклад: "+contribution,"",new Action("contract",t.id()),access.contracts());
            decorate(v,row,"contract","Задание города",t.goal()>0&&t.progress()>=t.goal()?"Условия выполнены":"В работе",t.progress(),t.goal(),t.goal()>0);tasks.add(row);active.add(row);
        });
        var metrics=new JsonArray();
        metrics.add(object("label","Казна","value",access.treasury().isBlank()?shortBalance(balance):"Недоступно","fullValue",access.treasury().isBlank()?formatBalance(balance):"","hint",access.treasury().isBlank()?"Баланс города · открыть":access.treasury(),"icon","treasury"));
        metrics.add(object("label","Жители","value",residents,"hint",c.roles(),"icon","residents"));
        metrics.add(object("label","Занято участков","value",c.claimed(),"hint","Лимит: "+limit,"icon","map"));
        metrics.add(object("label","Можно захватить","value",c.remaining()<0?"Без лимита":Integer.toString(c.remaining()),"hint",c.remaining()==0?"Лимит исчерпан":"Открыть карту участков","icon","claim"));
        v.city=object("role",c.roles(),"section",section,"metrics",metrics,"activeCount",active.size(),"buildCount",buildings.size(),"taskCount",tasks.size(),"buildsKnown",b.known(),"contractsKnown",d.contractsKnown());
        v.metricLinks.put(0,new Row("Казна","Баланс и история операций","",new Action("route","treasury"),access.treasury()));
        v.metricLinks.put(2,new Row("Занято участков","Границы и управление","",new Action("route","map"),""));
        v.metricLinks.put(3,new Row("Можно захватить","Границы и управление","",new Action("route","map"),""));
        link(v,"Постройки и исследования","Здания и исследования, доступные городу","build",new Action("route","tree"),access.builds());
        link(v,"План и смета","Зависимости, смета и закупки материалов","planner",new Action("route","planner"),access.builds());
        link(v,"Материалы","Недостающие блоки, склад и ваш инвентарь","materials",new Action("route","materials"),access.builds());
        link(v,"Общая цель","Приоритет города и личный вклад","goal",new Action("route","goals"),"");
        link(v,"История города","События, даты и участники","history",new Action("route","history"),access.history());
        link(v,"Приглашения","Входящие приглашения в город и компании","inbox",new Action("route","inbox"),"");
        v.city.addProperty("developmentCount",4);
        v.rows.addAll(switch(section){case "builds"->buildings;case "contracts"->tasks;default->active;});
        var warnings=new ArrayList<String>();
        if(!section.equals("contracts")&&!b.known())warnings.add(access.builds().isBlank()?"Данные построек недоступны. Попробуйте обновить экран.":access.builds());
        if(!section.equals("builds")&&!d.contractsKnown())warnings.add(access.contracts().isBlank()?"Данные заданий недоступны. Попробуйте обновить экран.":access.contracts());
        v.city.addProperty("sectionNotice",String.join(" ",warnings));
        v.city.addProperty("emptyTitle",!warnings.isEmpty()?"Данные доступны не полностью":switch(section){case "builds"->"Построек пока нет";case "contracts"->"Активных заданий нет";default->"Нет активных дел";});
        v.city.addProperty("emptyDetail",!warnings.isEmpty()?String.join(" ",warnings):switch(section){case "builds"->"Откройте «Постройки», чтобы выбрать первое здание.";case "contracts"->"Городские задания появятся здесь после принятия.";default->"Завершённые здания находятся во вкладке «Постройки».";});
    }
    private static void decorate(CivicView v,Row row,String icon,String category,String status,int done,int total,boolean known){
        v.presentation.put(row,object("icon",icon,"category",category,"status",status,"done",Math.max(0,done),"total",Math.max(0,total),"known",known&&total>0));
    }
    static String formatBalance(String balance){
        if(!balance.matches("-?\\d{1,20}(?:\\.\\d{1,2})?"))return balance;
        var format=java.text.NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU"));format.setMinimumFractionDigits(2);format.setMaximumFractionDigits(2);
        return format.format(new java.math.BigDecimal(balance)).replace('\u00a0',' ');
    }
    static String shortBalance(String balance){
        if(!balance.matches("-?\\d{1,20}(?:\\.\\d{1,2})?"))return balance;
        var amount=new java.math.BigDecimal(balance);
        if(amount.abs().compareTo(java.math.BigDecimal.valueOf(1_000_000))<0)return formatBalance(balance);
        var locale=Locale.forLanguageTag("ru-RU");
        var format=java.text.NumberFormat.getCompactNumberInstance(locale,java.text.NumberFormat.Style.SHORT);
        format.setMaximumFractionDigits(1);
        return format.format(amount);
    }
    private static void link(CivicView v,String title,String detail,String icon,Action action,String reason){
        var row=new Row(title,detail,"",action,reason);v.shortcuts.add(row);v.presentation.put(row,object("icon",icon));
    }
}
