package cc.crafto.ui.server;

import java.math.BigDecimal;
import java.util.*;
import org.bukkit.plugin.Plugin;
import static cc.crafto.ui.server.CivicView.*;

/** Strict read-only projections. Missing providers never become zero-cost promises. */
record DevelopmentEffects(Section upkeep,Section power) {
    record Section(boolean known,String reason,long period,List<Row> rows,List<String> assumptions) {
        static Section unavailable(String reason){return new Section(false,reason,0,List.of(),List.of());}
    }
    static DevelopmentEffects unavailable(String why){return new DevelopmentEffects(Section.unavailable(why),Section.unavailable(why));}
    static DevelopmentEffects read(Plugin upkeep,Plugin power,UUID town,DevelopmentView plan){
        if(!plan.available()||plan.steps().isEmpty())return unavailable("Сначала добавьте здание в план");
        var levels=new TreeMap<String,Integer>();plan.finalLevels().forEach((id,n)->{if(n>0)levels.put(id,n);});
        return new DevelopmentEffects(readOne(upkeep,"ru.neverland.townyupkeep.api.TownyUpkeepApi",town,levels,true),readOne(power,"ru.neverland.townypower.api.TownyPowerApi",town,levels,false));
    }
    private static Section readOne(Plugin plugin,String api,UUID town,Map<String,Integer> levels,boolean upkeep){
        if(plugin==null||!plugin.isEnabled())return Section.unavailable("Нет доступа к разделу или аддон временно недоступен");
        try{
            var type=Class.forName(api,false,plugin.getClass().getClassLoader());var registration=plugin.getServer().getServicesManager().getRegistration(type);
            if(registration==null||registration.getPlugin()!=plugin)throw new IllegalArgumentException();
            return decode(type.getMethod("development",UUID.class,Map.class).invoke(registration.getProvider(),town,Map.copyOf(levels)),upkeep);
        }catch(ReflectiveOperationException|RuntimeException|LinkageError ex){return Section.unavailable("Не удалось проверить последствия. Обновите аддон и повторите расчёт.");}
    }
    static Section decode(Object value,boolean upkeep){
        var root=map(value);if(!(root.get("known") instanceof Boolean known))throw new IllegalArgumentException();String reason=text(root,"reason");
        if(!known)return Section.unavailable(reason);
        long period=number(root,"periodSeconds");if(period<1||period>31_536_000)throw new IllegalArgumentException();
        var before=map(root.get("before"));var after=map(root.get("after"));var delta=map(root.get("delta"));var rows=new ArrayList<Row>();
        if(upkeep){
            long old=amount(before,"cents"),next=amount(after,"cents");checkDelta(old,next,number(delta,"cents"));
            rows.add(Row.info("Содержание за "+duration(period),"Сейчас: "+DevelopmentView.money(old)+" · после плана: "+DevelopmentView.money(next)+" · изменение: "+signedMoney(next-old)));
            var a=resources(before.get("resources"),false);var b=resources(after.get("resources"),false);var d=resources(delta.get("resources"),true);var keys=new TreeSet<>(a.keySet());keys.addAll(b.keySet());keys.addAll(d.keySet());
            for(String key:keys){long x=a.getOrDefault(key,0L),y=b.getOrDefault(key,0L);checkDelta(x,y,d.getOrDefault(key,0L));rows.add(Row.info(resourceName(key),"Содержание за тот же период: "+units(x)+" → "+units(y)+" ед."));}
            var invoices=list(root,"existingInvoices",4096);for(Object invoice:invoices){var i=map(invoice);UUID.fromString(text(i,"id"));String project=text(i,"project");long cents=amount(i,"cents");var req=resources(i.get("resources"),false);String materials=req.isEmpty()?"": " · ресурсы: "+String.join(", ",req.entrySet().stream().map(e->resourceName(e.getKey())+" "+units(e.getValue())).toList());rows.add(Row.info("Ранее выставленный счёт · "+title(root,project),DevelopmentView.money(cents)+materials+". Его условия сохраняются."));}
        }else{
            for(String key:List.of("generation","demand","deficit")){long x=amount(before,key),y=amount(after,key);checkDelta(x,y,number(delta,key));String name=switch(key){case "generation"->"Выработка энергии";case "demand"->"Потребность в энергии";default->"Непокрытая потребность";};rows.add(Row.info(name,"Сейчас: "+x+" · после плана: "+y+" ед. мощности"));}
        }
        for(Object building:list(root,"buildings",512)){var row=map(building);String title=text(row,"title"),why=text(row,"reason");rows.add(Row.info(title,why));}
        var assumptions=new ArrayList<String>();for(Object item:list(root,"assumptions",32)){if(!(item instanceof String s)||s.length()>2048)throw new IllegalArgumentException();assumptions.add(s);}
        return new Section(true,reason,period,List.copyOf(rows),List.copyOf(assumptions));
    }
    private static String title(Map<?,?> root,String project){for(Object raw:list(root,"buildings",512)){var row=map(raw);if(project.equals(text(row,"project")))return text(row,"title");}return "здание вне текущего плана";}
    private static Map<?,?> map(Object v){if(!(v instanceof Map<?,?> m))throw new IllegalArgumentException();return m;}
    private static List<?> list(Map<?,?> m,String key,int max){if(!(m.get(key) instanceof List<?> a)||a.size()>max)throw new IllegalArgumentException();return a;}
    private static String text(Map<?,?> m,String key){if(!(m.get(key) instanceof String s)||s.length()>2048)throw new IllegalArgumentException();return s;}
    private static long number(Map<?,?> m,String key){Object n=m.get(key);if(!(n instanceof Integer)&&!(n instanceof Long))throw new IllegalArgumentException();long value=((Number)n).longValue();if(value< -100_000_000_000_000L||value>100_000_000_000_000L)throw new IllegalArgumentException();return value;}
    private static long amount(Map<?,?> m,String key){long n=number(m,key);if(n<0)throw new IllegalArgumentException();return n;}
    private static Map<String,Long> resources(Object value,boolean signed){var input=map(value);if(input.size()>128)throw new IllegalArgumentException();var out=new TreeMap<String,Long>();for(var e:input.entrySet()){if(!(e.getKey() instanceof String key)||!key.matches("[a-z0-9_-]{1,64}"))throw new IllegalArgumentException();long n=number(input,key);if(!signed&&n<0)throw new IllegalArgumentException();out.put(key,n);}return out;}
    private static void checkDelta(long old,long next,long delta){if(Math.subtractExact(next,old)!=delta)throw new IllegalArgumentException();}
    private static String duration(long seconds){if(seconds%86400==0)return seconds/86400+" сут.";if(seconds%3600==0)return seconds/3600+" ч.";if(seconds%60==0)return seconds/60+" мин.";return seconds+" сек.";}
    private static String units(long n){return BigDecimal.valueOf(n,3).stripTrailingZeros().toPlainString();}
    private static String signedMoney(long cents){return (cents>0?"+":"")+DevelopmentView.money(cents);}
    private static String resourceName(String id){return switch(id){case "wood"->"Древесина";case "stone"->"Камень";case "food"->"Продовольствие";case "metal"->"Металл";case "tools"->"Инструменты";case "fuel"->"Топливо";default->id;};}
    void populate(CivicView v){populate(v,"Содержание",upkeep);populate(v,"Энергоснабжение",power);}
    private static void populate(CivicView v,String title,Section section){
        if(!section.known()){v.rows.add(Row.info(title+" · пока неизвестно",section.reason()));return;}
        v.rows.add(Row.info(title+" · после завершения",section.reason()));v.rows.addAll(section.rows());for(String text:section.assumptions())v.rows.add(Row.info("Условие расчёта",text));
    }
}
