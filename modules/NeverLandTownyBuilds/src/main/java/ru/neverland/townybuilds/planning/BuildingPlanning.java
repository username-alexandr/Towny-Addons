package ru.neverland.townybuilds.planning;

import java.util.*;
import java.util.concurrent.*;
import java.nio.charset.StandardCharsets;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.*;
import ru.neverland.townybuilds.api.BuildingPlanningApi;
import ru.neverland.townybuilds.data.*;
import ru.neverland.townybuilds.model.*;
import ru.neverland.townybuilds.service.*;
import ru.neverland.townybuilds.util.ColorUtil;
import static ru.neverland.townybuilds.planning.DevelopmentPlan.*;

/** A bounded worker captures in-memory state; no addon files or world blocks are read. */
public final class BuildingPlanning implements BuildingPlanningApi {
    private final JavaPlugin plugin;private final DataStore data;private final DefinitionRegistry definitions;private final RussianItemNames names;
    private final Semaphore requests=new Semaphore(2);
    public BuildingPlanning(JavaPlugin plugin,DataStore data,DefinitionRegistry definitions,RussianItemNames names){this.plugin=plugin;this.data=data;this.definitions=definitions;this.names=names;Bukkit.getServicesManager().register(BuildingPlanningApi.class,this,plugin,ServicePriority.Normal);}
    @Override public CompletableFuture<Map<String,Object>> development(UUID town,Map<String,Integer> goals){
        Objects.requireNonNull(town);Map<String,Integer> copy=Map.copyOf(goals);if(copy.size()>8)throw new IllegalArgumentException("В плане может быть до восьми зданий");
        var future=new CompletableFuture<Map<String,Object>>();if(!requests.tryAcquire())return CompletableFuture.failedFuture(new IllegalStateException("Расчёт занят. Обновите план через несколько секунд."));
        try{Bukkit.getAsyncScheduler().runNow(plugin,task->{try{if(future.isDone())return;Capture capture;try(var guard=SuiteState.guard()){capture=capture(town,copy);}
            if(copy.isEmpty()){future.complete(Map.of("schema",1,"town",town.toString(),"catalog",capture.catalogRows()));return;}
            var result=estimate(capture.catalog(),capture.city(),copy);future.complete(view(town,copy,capture,result));
        }catch(Exception ex){future.completeExceptionally(ex);}finally{requests.release();}});}catch(RuntimeException ex){requests.release();future.completeExceptionally(ex);}
        return future.orTimeout(10,TimeUnit.SECONDS);
    }
    private record Capture(Map<String,Project> catalog,City city,List<Map<String,Object>> catalogRows,List<String> notices){}
    private Capture capture(UUID town,Map<String,Integer> goals){
        if(!plugin.isEnabled()||SuiteState.frozen(plugin.getName())||!data.writable()||com.palmergames.bukkit.towny.TownyAPI.getInstance().getTown(town)==null)throw new IllegalStateException("Планирование города временно недоступно");
        var state=data.existingTown(town);Map<String,Integer> levels=state==null?Map.of():state.levels();
        var rows=new ArrayList<Map<String,Object>>();if(definitions.all().size()>MAX_PROJECTS)throw new IllegalStateException("Каталог слишком велик для одного плана");
        for(var def:definitions.all())rows.add(Map.of("id",def.id(),"name",ColorUtil.plain(def.name()),"current",levels.getOrDefault(def.id(),0),"maximum",def.maxLevel(),"icon",def.icon().name()));
        rows.sort(Comparator.comparing(m->m.get("id").toString()));
        if(goals.isEmpty())return new Capture(Map.of(),new City(levels,Map.of(),Map.of(),true,Set.of(),Set.of()),List.copyOf(rows),List.of());
        var selected=new TreeSet<String>();var seen=new HashMap<String,Integer>();var queue=new ArrayDeque<>(goals.entrySet());
        while(!queue.isEmpty()){var next=queue.removeFirst();String id=next.getKey();int target=next.getValue();if(seen.getOrDefault(id,0)>=target)continue;seen.put(id,target);selected.add(id);var def=definitions.project(id);if(def==null)throw new IllegalArgumentException("Здание отсутствует: "+id);if(selected.size()>MAX_PROJECTS)throw new IllegalArgumentException("Слишком много зависимостей");if(levels.getOrDefault(id,0)<target)queue.addAll(def.requirements().entrySet());}
        var catalog=new LinkedHashMap<String,Project>();var samples=new LinkedHashMap<Item,ItemStack>();var intern=new HashMap<String,Item>();var notices=new ArrayList<String>();int encoded=0;
        for(String id:selected){var def=definitions.project(id);var prices=new TreeMap<Integer,Level>();var notes=new ArrayList<String>();notes.addAll(def.description().stream().map(ColorUtil::plain).toList());
            var specialization=ru.neverland.integration.SpecializationRules.required(id);if(!specialization.isEmpty())notes.add("Требуется специализация: "+ru.neverland.integration.SpecializationAccess.requirement(id));
            if(def.type()==ProjectType.WONDER)notes.add("Артефакты для Чуда проверяются отдельно в меню здания; в материальную смету они не включены.");
            if(id.equals("army")&&(state==null||!state.armyEstablished()))notes.add("Завершение первой армии снимает защиту города новичков от нападений.");
            for(var definition:def.levels().entrySet()){
                var required=new LinkedHashMap<Item,Long>();
                for(ItemStack source:definition.getValue().resources()){
                    if(source.getAmount()<1)throw new IllegalArgumentException("Неверный ресурс здания "+id);var sample=source.clone();sample.setAmount(1);
                    String bytes=Base64.getEncoder().encodeToString(sample.serializeAsBytes());boolean custom=sample.hasItemMeta();String key=custom?"custom:"+UUID.nameUUIDFromBytes(bytes.getBytes(StandardCharsets.UTF_8)):sample.getType().name();
                    Item item=intern.get(key);if(item==null){encoded=Math.addExact(encoded,bytes.length());if(encoded>2_097_152||intern.size()>=MAX_ITEMS)throw new IllegalArgumentException("Слишком большой каталог материалов");item=new Item(key,names.name(sample),sample.getType().name(),bytes,custom);intern.put(key,item);samples.put(item,sample);}
                    required.merge(item,(long)source.getAmount(),Math::addExact);
                }
                var lev=definition.getValue();prices.put(definition.getKey(),new Level(ru.neverland.townybuilds.construction.ConstructionReceipt.cents(lev.money()),required,lev.bonusBlocks(),List.of()));
            }
            catalog.put(id,new Project(id,ColorUtil.plain(def.name()),def.requirements(),prices,notes));
        }
        var ordered=new ArrayList<>(samples.keySet());ordered.sort(Comparator.comparing(Item::custom).reversed().thenComparing(Item::key));
        var funds=new HashMap<String,Fund>();var active=new HashSet<String>();var uncertain=new HashSet<String>();
        if(state!=null)for(String id:selected){var fund=state.existingResourceFund(id);if(fund!=null){var inventory=fund.entries().stream().map(entry->{var item=entry.template();item.setAmount(entry.amount());return item;}).toArray(ItemStack[]::new);var level=catalog.get(id).levels().get(fund.targetLevel());funds.put(id,new Fund(fund.targetLevel(),level==null?Map.of():allocate(ordered,samples,inventory,level.materials())));}
            var site=state.constructionSite(id);if(site!=null&&site.active())active.add(id);var receipt=state.constructionReceipts().get(id);if(receipt!=null&&Set.of("DEBIT_PENDING","DEBIT_CONFIRMED","ARTIFACT_PENDING").contains(receipt.state()))uncertain.add(id);
        }
        boolean stockKnown=!data.storageBusy(town,"warehouse");var demand=new LinkedHashMap<Item,Long>();var unfunded=estimate(catalog,new City(levels,funds,Map.of(),true,active,uncertain),goals);for(var item:unfunded.supplies())demand.put(item.item(),item.required()-item.donated());var stock=state==null||!stockKnown?Map.<Item,Long>of():allocate(ordered,samples,state.storage(),demand);
        return new Capture(Map.copyOf(catalog),new City(levels,funds,stock,stockKnown,active,uncertain),List.copyOf(rows),List.copyOf(notices));
    }
    private static Map<Item,Long> allocate(List<Item> order,Map<Item,ItemStack> samples,ItemStack[] source,Map<Item,Long> demand){
        long[] remaining=new long[source.length];for(int i=0;i<source.length;i++)remaining[i]=source[i]==null?0:Math.max(0,source[i].getAmount());var result=new LinkedHashMap<Item,Long>();
        for(Item item:order){long total=0,need=demand.getOrDefault(item,0L);for(int i=0;i<source.length&&total<need;i++)if(remaining[i]>0&&ResourceMatcher.matches(source[i],samples.get(item))){long take=Math.min(remaining[i],need-total);total=Math.addExact(total,take);remaining[i]-=take;}if(total>0)result.put(item,total);}
        return result;
    }
    private static Map<String,Object> view(UUID town,Map<String,Integer> goals,Capture capture,Result result){
        var steps=new ArrayList<Map<String,Object>>();for(var s:result.steps())steps.add(Map.of("project",s.project(),"name",s.name(),"level",s.level(),"cents",s.cents(),"bonusBlocks",s.bonusBlocks(),"notices",s.notices()));
        var supply=new ArrayList<Map<String,Object>>();for(var s:result.supplies())supply.add(Map.of("key",s.item().key(),"name",s.item().name(),"material",s.item().material(),"custom",s.item().custom(),"required",s.required(),"donated",s.donated(),"stored",s.stored(),"missing",s.missing()));
        var out=new LinkedHashMap<String,Object>();out.put("schema",1);out.put("town",town.toString());out.put("goals",Map.copyOf(goals));out.put("catalog",capture.catalogRows());out.put("steps",List.copyOf(steps));out.put("supply",List.copyOf(supply));out.put("finalLevels",result.finalLevels());out.put("cents",result.cents());out.put("bonusBlocks",result.bonusBlocks());out.put("known",result.known());out.put("stockKnown",capture.city().stockKnown());out.put("notices",result.notices());out.put("estimatedAt",System.currentTimeMillis());return Map.copyOf(out);
    }
}
