package ru.neverland.townybuilds.planning;

import java.util.*;

/** Pure bounded estimate. Every building level and every available item is counted once. */
public final class DevelopmentPlan {
    public static final int MAX_PROJECTS=128, MAX_STEPS=512, MAX_ITEMS=1024;
    public record Item(String key,String name,String material,String sample,boolean custom) {
        public Item { Objects.requireNonNull(key);Objects.requireNonNull(name);Objects.requireNonNull(material);Objects.requireNonNull(sample);if(key.isBlank()||key.length()>128||sample.length()>131072)throw new IllegalArgumentException("Неверный материал сметы"); }
    }
    public record Level(long cents,Map<Item,Long> materials,int bonusBlocks,List<String> effects) {
        public Level { if(cents<0||cents>100_000_000_000L)throw new IllegalArgumentException("Неверная цена здания");materials=positive(materials);effects=List.copyOf(effects); }
    }
    public record Project(String id,String name,Map<String,Integer> requires,Map<Integer,Level> levels,List<String> notices) {
        public Project { Objects.requireNonNull(id);Objects.requireNonNull(name);requires=Map.copyOf(requires);levels=Map.copyOf(levels);notices=List.copyOf(notices);if(levels.isEmpty()||levels.size()>32)throw new IllegalArgumentException("Неверные уровни здания");for(var n:requires.values())if(n<1||n>32)throw new IllegalArgumentException("Неверная зависимость здания"); }
    }
    public record Fund(int level,Map<Item,Long> items) {public Fund {items=positive(items);}}
    public record City(Map<String,Integer> levels,Map<String,Fund> funds,Map<Item,Long> stock,boolean stockKnown,Set<String> active,Set<String> uncertain) {
        public City { levels=Map.copyOf(levels);funds=Map.copyOf(funds);stock=positive(stock);active=Set.copyOf(active);uncertain=Set.copyOf(uncertain); }
    }
    public record Step(String project,String name,int level,long cents,Map<Item,Long> materials,Map<Item,Long> donated,int bonusBlocks,List<String> effects,List<String> notices) {}
    public record Supply(Item item,long required,long donated,long stored,long missing) {}
    public record Result(List<Step> steps,List<Supply> supplies,Map<String,Integer> finalLevels,long cents,int bonusBlocks,List<String> notices,boolean known) {}
    private DevelopmentPlan(){}
    private static <K> Map<K,Long> positive(Map<K,Long> input){if(input.size()>MAX_ITEMS)throw new IllegalArgumentException("Слишком много материалов");var out=new LinkedHashMap<K,Long>();input.forEach((k,v)->{if(v==null||v<0||v>1_000_000_000L)throw new IllegalArgumentException("Неверное количество материала");if(v>0)out.put(Objects.requireNonNull(k),v);});return Collections.unmodifiableMap(out);}
    public static Result estimate(Map<String,Project> catalog,City city,Map<String,Integer> goals){
        if(catalog.size()>MAX_PROJECTS||goals.isEmpty()||goals.size()>8)throw new IllegalArgumentException("Выберите от одного до восьми зданий");
        var targets=new TreeMap<String,Integer>();var visiting=new HashSet<String>();var order=new LinkedHashSet<String>();
        for(var e:new TreeMap<>(goals).entrySet())visit(e.getKey(),e.getValue(),catalog,city.levels(),targets,visiting,order);
        var steps=new ArrayList<Step>();var required=new LinkedHashMap<Item,Long>();var donated=new LinkedHashMap<Item,Long>();var finalLevels=new TreeMap<>(city.levels());var notices=new LinkedHashSet<String>();long cents=0;int bonus=0;boolean known=true;
        for(String id:order){var project=catalog.get(id);int current=city.levels().getOrDefault(id,0),target=targets.get(id);finalLevels.put(id,target);
            if(city.active().contains(id)){notices.add(project.name()+": уже строится. Завершите текущую стройку и обновите смету.");known=false;}
            if(city.uncertain().contains(id)){notices.add(project.name()+": предыдущая оплата требует сверки.");known=false;}
            for(int level=current+1;level<=target;level++){
                if(steps.size()>=MAX_STEPS)throw new IllegalArgumentException("Слишком длинная цепочка построек");var price=project.levels().get(level);if(price==null)throw new IllegalArgumentException("Не задан уровень "+level+" здания "+project.name());
                var fund=city.funds().get(id);var used=new LinkedHashMap<Item,Long>();
                for(var item:price.materials().entrySet()){
                    long amount=item.getValue(),paid=fund!=null&&fund.level()==level?Math.min(amount,fund.items().getOrDefault(item.getKey(),0L)):0;
                    required.merge(item.getKey(),amount,Math::addExact);if(paid>0){donated.merge(item.getKey(),paid,Math::addExact);used.put(item.getKey(),paid);}
                }
                cents=Math.addExact(cents,price.cents());bonus=Math.addExact(bonus,price.bonusBlocks());
                steps.add(new Step(id,project.name(),level,price.cents(),price.materials(),Map.copyOf(used),price.bonusBlocks(),price.effects(),project.notices()));
            }
        }
        if(required.size()>MAX_ITEMS)throw new IllegalArgumentException("Слишком много материалов сметы");
        var supply=new ArrayList<Supply>();for(var e:required.entrySet()){long paid=donated.getOrDefault(e.getKey(),0L),need=e.getValue()-paid,stored=city.stockKnown()?Math.min(need,city.stock().getOrDefault(e.getKey(),0L)):0;supply.add(new Supply(e.getKey(),e.getValue(),paid,stored,need-stored));}
        supply.sort(Comparator.comparing(s->s.item().key()));
        if(!city.stockKnown())notices.add("Склад открыт или недоступен. Количество для сбора уточнится после его закрытия.");
        return new Result(List.copyOf(steps),List.copyOf(supply),Map.copyOf(finalLevels),cents,bonus,List.copyOf(notices),known&&city.stockKnown());
    }
    private static void visit(String id,int level,Map<String,Project> catalog,Map<String,Integer> current,Map<String,Integer> targets,Set<String> visiting,Set<String> order){
        var p=catalog.get(id);if(p==null||level<1||!p.levels().containsKey(level))throw new IllegalArgumentException("Здание или уровень отсутствует: "+id+" / "+level);
        if(current.getOrDefault(id,0)>=level)return;
        if(visiting.contains(id))throw new IllegalArgumentException("Зависимости зданий образуют круг: "+p.name());
        if(targets.getOrDefault(id,0)>=level)return;
        if(visiting.size()>=MAX_PROJECTS)throw new IllegalArgumentException("Слишком много зависимостей");targets.put(id,level);visiting.add(id);
        for(var e:new TreeMap<>(p.requires()).entrySet())visit(e.getKey(),e.getValue(),catalog,current,targets,visiting,order);
        visiting.remove(id);order.add(id);
    }
}
