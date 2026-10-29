package ru.neverland.townyspecialization.production;

import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import ru.neverland.integration.SpecializationRules;

/** Recipes name accounted city goods; they never create an inventory item or a currency balance. */
public record ProductionCatalog(boolean enabled,Limits limits,Map<String,Product> products,Map<String,Joint> projects) {
    public record Limits(int activeBatchesCity,int activeBatchesServer,int batchSize,int goodsCity,int goodsServer,int activeProjectsCity,int activeProjectsServer){
        public Limits{if(activeBatchesCity<1||activeBatchesCity>20||activeBatchesServer<activeBatchesCity||activeBatchesServer>1000||batchSize<1||batchSize>64||goodsCity<batchSize||goodsCity>10000||goodsServer<goodsCity||goodsServer>1_000_000||activeProjectsCity<1||activeProjectsCity>20||activeProjectsServer<activeProjectsCity||activeProjectsServer>1000)throw new IllegalArgumentException("Неверные пределы производственных партий");}
    }
    public record Product(String id,String profile,String title,String icon,Map<String,Long> materials){
        public Product{identifier(id);if(!SpecializationRules.PROJECTS.containsValue(profile)||title==null||title.isBlank()||title.length()>100||icon==null)throw new IllegalArgumentException("Неверный товар специализации");materials=Map.copyOf(materials);if(materials.isEmpty()||materials.size()>16)throw new IllegalArgumentException("Смета партии: 1–16 материалов");for(var e:materials.entrySet())if(!e.getKey().matches("[A-Z0-9_]{1,64}")||e.getValue()<1||e.getValue()>100_000)throw new IllegalArgumentException("Неверный материал партии");}
        public Map<String,Long> costs(int amount){if(amount<1||amount>64)throw new IllegalArgumentException("Количество партий: 1–64");var costs=new TreeMap<String,Long>();materials.forEach((key,value)->costs.put(key,Math.multiplyExact(value,amount)));return Map.copyOf(costs);}
    }
    public record Joint(String id,String title,String result,int minimumCities,Map<String,Long> goods){
        public Joint{identifier(id);if(title==null||title.isBlank()||title.length()>100||result==null||result.isBlank()||result.length()>200||minimumCities<2||minimumCities>7)throw new IllegalArgumentException("Неверный совместный проект");goods=Map.copyOf(goods);if(goods.size()<2||goods.size()>7||goods.values().stream().anyMatch(v->v<1||v>64))throw new IllegalArgumentException("Совместному проекту нужны партии разных направлений");}
    }
    public ProductionCatalog{products=Map.copyOf(products);projects=Map.copyOf(projects);if(products.size()>32||projects.size()>32)throw new IllegalArgumentException("Не более 32 товаров и проектов");for(var goal:projects.values()){var profiles=new HashSet<String>();for(String id:goal.goods().keySet()){var product=products.get(id);if(product==null)throw new IllegalArgumentException("Неизвестный товар проекта "+id);profiles.add(product.profile());}if(profiles.size()<goal.minimumCities())throw new IllegalArgumentException("Проект требует больше направлений, чем предусмотрено товаров");}}
    static void identifier(String value){if(value==null||!value.matches("[a-z0-9_]{1,48}"))throw new IllegalArgumentException("Неверный ID партии или проекта");}
    private static long number(Object value){try{return new java.math.BigDecimal(String.valueOf(value)).longValueExact();}catch(Exception ex){throw new IllegalArgumentException("Нужно целое количество");}}
    private static int integer(ConfigurationSection y,String key){return Math.toIntExact(number(y.get(key)));}
    private static Map<String,Long> amounts(ConfigurationSection y){if(y==null)throw new IllegalArgumentException("Не задана смета");var out=new TreeMap<String,Long>();for(String key:y.getKeys(false))out.put(key,number(y.get(key)));return out;}
    public static ProductionCatalog load(ConfigurationSection y){
        if(!(y.get("enabled") instanceof Boolean enabled))throw new IllegalArgumentException("enabled: true или false");
        var limits=new Limits(integer(y,"limits.active-batches-city"),integer(y,"limits.active-batches-server"),integer(y,"limits.batch-size"),integer(y,"limits.goods-city"),integer(y,"limits.goods-server"),integer(y,"limits.active-projects-city"),integer(y,"limits.active-projects-server"));
        var products=new LinkedHashMap<String,Product>();var p=y.getConfigurationSection("products");if(p==null)throw new IllegalArgumentException("Нет products");for(String id:p.getKeys(false)){var r=p.getConfigurationSection(id);if(r==null)throw new IllegalArgumentException("Неверный товар "+id);products.put(id,new Product(id,r.getString("profile"),r.getString("title"),r.getString("icon"),amounts(r.getConfigurationSection("materials"))));}
        var projects=new LinkedHashMap<String,Joint>();var j=y.getConfigurationSection("projects");if(j==null)throw new IllegalArgumentException("Нет projects");for(String id:j.getKeys(false)){var r=j.getConfigurationSection(id);if(r==null)throw new IllegalArgumentException("Неверный проект "+id);projects.put(id,new Joint(id,r.getString("title"),r.getString("result"),integer(r,"minimum-cities"),amounts(r.getConfigurationSection("goods"))));}
        return new ProductionCatalog(enabled,limits,products,projects);
    }
}
