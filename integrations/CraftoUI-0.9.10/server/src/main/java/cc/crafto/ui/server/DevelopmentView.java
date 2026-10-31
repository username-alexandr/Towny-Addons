package cc.crafto.ui.server;

import java.util.*;
import java.util.concurrent.*;
import org.bukkit.plugin.Plugin;
import static cc.crafto.ui.server.CivicView.*;

/** Immutable public-API estimates. This screen never spends money or changes building data. */
record DevelopmentView(boolean available,String problem,List<Building> catalog,List<Step> steps,List<Supply> supplies,
                       long cents,int bonusBlocks,boolean known,boolean stockKnown,List<String> notices,Map<String,Integer> finalLevels) {
    record Building(String id,String name,int current,int maximum){}
    record Step(String id,String name,int level,long cents,int bonus,List<String> notices){}
    record Supply(String key,String name,String material,boolean custom,long required,long donated,long stored,long missing){}
    static DevelopmentView unavailable(String reason){return new DevelopmentView(false,reason,List.of(),List.of(),List.of(),0,0,false,false,List.of(),Map.of());}
    static CompletableFuture<DevelopmentView> read(Plugin builds,UUID town,Map<String,Integer> goals){
        if(town==null)return CompletableFuture.completedFuture(unavailable("Сначала вступите в город"));
        if(builds==null||!builds.isEnabled())return CompletableFuture.completedFuture(unavailable("Аддон построек временно недоступен"));
        try{
            var type=Class.forName("ru.neverland.townybuilds.api.BuildingPlanningApi",false,builds.getClass().getClassLoader());
            var registration=builds.getServer().getServicesManager().getRegistration(type);
            if(registration==null||registration.getPlugin()!=builds)throw new IllegalStateException();
            Object result=type.getMethod("development",UUID.class,Map.class).invoke(registration.getProvider(),town,Map.copyOf(goals));
            if(!(result instanceof CompletionStage<?> pending))throw new IllegalStateException();
            return pending.toCompletableFuture().orTimeout(12,TimeUnit.SECONDS).handle((value,error)->{
                if(error!=null)return unavailable("Не удалось рассчитать план. Обновите экран; прежние стройки и ресурсы сохранены.");
                try{return decode(value,town,goals.isEmpty());}catch(RuntimeException ex){return unavailable("План передан в неподдерживаемом формате. Обновите Builds и CraftoUI вместе.");}
            });
        }catch(ReflectiveOperationException|RuntimeException|LinkageError ex){return CompletableFuture.completedFuture(unavailable("Для планировщика нужен обновлённый NeverLandTownyBuilds с API планирования."));}
    }
    static DevelopmentView decode(Object value,UUID town,boolean catalogOnly){
        Map<?,?> root=map(value);if(number(root,"schema")!=1||!text(root,"town").equals(town.toString()))throw new IllegalArgumentException();
        var catalog=new ArrayList<Building>();var ids=new HashSet<String>();for(Object raw:list(root,"catalog",128)){
            var row=map(raw);String id=text(row,"id");int current=integer(row,"current",0,32),maximum=integer(row,"maximum",1,32);if(!id.matches("[a-z0-9_-]{1,64}")||!ids.add(id)||current>maximum)throw new IllegalArgumentException();catalog.add(new Building(id,text(row,"name"),current,maximum));
        }
        if(catalogOnly)return new DevelopmentView(true,"",List.copyOf(catalog),List.of(),List.of(),0,0,true,true,List.of(),Map.of());
        var steps=new ArrayList<Step>();long total=0;int bonus=0;var unique=new HashSet<String>();
        for(Object raw:list(root,"steps",512)){var row=map(raw);String id=text(row,"project");int level=integer(row,"level",1,32);if(!ids.contains(id)||!unique.add(id+":"+level))throw new IllegalArgumentException();long cents=nonnegative(row,"cents");int extra=integer(row,"bonusBlocks",0,1_000_000);total=Math.addExact(total,cents);bonus=Math.addExact(bonus,extra);steps.add(new Step(id,text(row,"name"),level,cents,extra,strings(row,"notices",64)));}
        if(total!=nonnegative(root,"cents")||bonus!=integer(root,"bonusBlocks",0,100_000_000))throw new IllegalArgumentException();
        var supplies=new ArrayList<Supply>();var keys=new HashSet<String>();for(Object raw:list(root,"supply",1024)){
            var row=map(raw);String key=text(row,"key");if(!keys.add(key))throw new IllegalArgumentException();long required=nonnegative(row,"required"),donated=nonnegative(row,"donated"),stored=nonnegative(row,"stored"),missing=nonnegative(row,"missing");if(Math.addExact(Math.addExact(donated,stored),missing)!=required)throw new IllegalArgumentException();supplies.add(new Supply(key,text(row,"name"),text(row,"material"),bool(row,"custom"),required,donated,stored,missing));
        }
        var levels=new TreeMap<String,Integer>();var rawLevels=map(root.get("finalLevels"));if(rawLevels.size()>128)throw new IllegalArgumentException();rawLevels.forEach((key,n)->{if(!(key instanceof String id)||!id.matches("[a-z0-9_-]{1,64}")||(!(n instanceof Integer)&&!(n instanceof Long))||((Number)n).longValue()<0||((Number)n).longValue()>32)throw new IllegalArgumentException();levels.put(id,((Number)n).intValue());});
        return new DevelopmentView(true,"",List.copyOf(catalog),List.copyOf(steps),List.copyOf(supplies),total,bonus,bool(root,"known"),bool(root,"stockKnown"),strings(root,"notices",256),Map.copyOf(levels));
    }
    private static Map<?,?> map(Object v){if(!(v instanceof Map<?,?> m))throw new IllegalArgumentException();return m;}
    private static List<?> list(Map<?,?> m,String key,int maximum){if(!(m.get(key) instanceof List<?> a)||a.size()>maximum)throw new IllegalArgumentException();return a;}
    private static List<String> strings(Map<?,?> m,String key,int max){var out=new ArrayList<String>();for(Object v:list(m,key,max)){if(!(v instanceof String s)||s.length()>2048)throw new IllegalArgumentException();out.add(s);}return List.copyOf(out);}
    private static String text(Map<?,?> m,String key){if(!(m.get(key) instanceof String s)||s.length()>1024)throw new IllegalArgumentException();return s;}
    private static long number(Map<?,?> m,String key){Object v=m.get(key);if(!(v instanceof Long)&&!(v instanceof Integer))throw new IllegalArgumentException();return ((Number)v).longValue();}
    private static long nonnegative(Map<?,?> m,String key){long n=number(m,key);if(n<0||n>100_000_000_000_000L)throw new IllegalArgumentException();return n;}
    private static int integer(Map<?,?> m,String key,int min,int max){long n=number(m,key);if(n<min||n>max)throw new IllegalArgumentException();return (int)n;}
    private static boolean bool(Map<?,?> m,String key){if(!(m.get(key) instanceof Boolean b))throw new IllegalArgumentException();return b;}
    private static String clip(String value,int limit){return value.length()<=limit?value:value.substring(0,limit-1)+"…";}
    static String money(long cents){return java.math.BigDecimal.valueOf(cents,2).stripTrailingZeros().toPlainString()+" монет";}
    void populate(CivicView v,String route,String id,Map<String,Integer> goals){
        v.title="План развития города";if(!available){v.rows.add(Row.info("План пока недоступен",problem));if(!goals.isEmpty())v.rows.add(new Row("Очистить план","Только этот расчёт; постройки и ресурсы сохранятся",new Action("plan-clear","")));return;}
        v.lines.add("Это расчёт будущих построек. Деньги и ресурсы пока не расходуются и не резервируются.");
        if(route.equals("planner-catalog")){
            v.title="Добавить здание в план";for(var b:catalog)if(b.current()<b.maximum())v.rows.add(new Row(b.name(),"Сейчас: "+b.current()+" · максимум: "+b.maximum(),new Action("plan-project",b.id())));return;
        }
        if(route.equals("planner-project")){
            var b=catalog.stream().filter(x->x.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Здание больше недоступно"));v.title=b.name()+" · выбрать уровень";
            for(int level=b.current()+1;level<=b.maximum();level++)v.rows.add(new Row("Достроить до уровня "+level,"Нужные предыдущие уровни и здания добавятся в смету",new Action("plan-level",b.id(),Integer.toString(level))));
            if(goals.containsKey(id))v.rows.add(new Row("Убрать из плана","Уже построенные здания не изменятся",new Action("plan-remove",id)));return;
        }
        for(String notice:notices.stream().limit(8).toList())v.lines.add(clip(notice,320));
        for(String notice:notices.stream().skip(8).toList())v.rows.add(Row.info("Проверьте план",clip(notice,800)));
        if(route.equals("planner-supply")){
            v.title="Материалы для всего плана";v.lines.add("Фонд каждого уровня и общий склад учтены по одному разу. Заказы на будущие уровни открываются после предыдущего строительства.");
            for(var item:supplies)v.rows.add(Row.info(item.name(),"Нужно: "+item.required()+" · уже сдано: "+item.donated()+" · склад: "+(stockKnown?item.stored():"уточняется")+" · собрать: "+(stockKnown?item.missing():"уточняется")));
            if(supplies.isEmpty())v.rows.add(Row.info("Материалы не требуются","Добавьте здание или выберите следующий уровень."));return;
        }
        if(route.equals("planner-effects")){
            v.title="Что изменится после строительства";v.lines.add("Дополнительных участков по ценам уровней: "+bonusBlocks+". Реальные эффекты включаются после завершения и запуска здания.");
            for(var step:steps){v.rows.add(Row.info(step.name()+" · уровень "+step.level(),"Цена уровня: "+money(step.cents())+" · дополнительных участков: "+step.bonus()));for(String note:step.notices())v.rows.add(Row.info(step.name(),note));}
            return;
        }
        v.rows.add(new Row("Добавить здание","До восьми целей; общие зависимости учитываются один раз",new Action("route","planner-catalog")));
        if(goals.isEmpty()){v.rows.add(Row.info("План пока пуст","Выберите здание и желаемый уровень."));return;}
        v.rows.add(Row.info("Стоимость строительства",known?money(cents):"Требуется уточнить план перед покупкой"));
        v.rows.add(new Row("Материалы и снабжение","Вся цепочка, уже сданные ресурсы и общий склад",new Action("route","planner-supply")));
        v.rows.add(new Row("Последствия для города","Участки, содержание, энергоснабжение и условия зданий",new Action("route","planner-effects")));
        for(var b:catalog)if(goals.containsKey(b.id()))v.rows.add(new Row(b.name()+" → уровень "+goals.get(b.id()),"Изменить цель или убрать из плана",new Action("plan-project",b.id())));
        for(var step:steps)v.rows.add(new Row(step.name()+" · уровень "+step.level(),"В очереди строительства: "+money(step.cents()),new Action("build",step.id())));
        v.rows.add(new Row("Очистить план","Только этот расчёт; городские постройки сохранятся",new Action("plan-clear","")));
    }
}
