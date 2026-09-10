package ru.neverland.townybuilds.storage;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.townybuilds.data.*;
import ru.neverland.townybuilds.api.BuildingDepot;
import ru.neverland.townybuilds.integration.TownyHook;
import java.util.*;
import java.io.*;
public final class ProductionService {
    private record Recipe(String project,List<ItemStack> input,List<ItemStack> output,boolean districtBonus){}
    private final JavaPlugin plugin;private final DataStore data;private final BuildingStorageService storage;private final TownyHook towny=new TownyHook();
    private List<Recipe> recipes=List.of();private BukkitTask task;
    public ProductionService(JavaPlugin plugin,DataStore data,BuildingStorageService storage){this.plugin=plugin;this.data=data;this.storage=storage;}
    public void start(){
        try{
            var config=new YamlConfiguration();config.load(new File(plugin.getDataFolder(),"production.yml"));
            List<Recipe> next=new ArrayList<>();var root=config.getConfigurationSection("recipes");if(root==null)throw new IllegalArgumentException("Нет recipes");
            for(String id:root.getKeys(false)){var r=root.getConfigurationSection(id);String project=r.getString("building","");
                if(!new ru.neverland.townybuilds.construction.BuildingBlueprintGenerator().supportedProjects().contains(project))throw new IllegalArgumentException("Неизвестное здание "+project);
                var output=items(r.getConfigurationSection("output"));if(output.isEmpty())throw new IllegalArgumentException("Пустой выпуск "+id);
                next.add(new Recipe(project,items(r.getConfigurationSection("input")),output,r.getBoolean("district-bonus",true)));}
            int interval=config.getInt("interval-ticks",1200);if(interval<200||interval>72000)throw new IllegalArgumentException("interval-ticks: 200..72000");
            stop();recipes=List.copyOf(next);if(config.getBoolean("enabled",true))task=Bukkit.getScheduler().runTaskTimer(plugin,this::produce,interval,interval);
        }catch(Exception ex){plugin.getLogger().warning("Настройки производства не применены: "+ex.getMessage());}
    }
    private List<ItemStack> items(ConfigurationSection section){List<ItemStack> result=new ArrayList<>();if(section==null)return result;
        for(String key:section.getKeys(false)){Material material=Material.matchMaterial(key);int count=section.getInt(key);
            if(material==null||!material.isItem()||material.isAir()||count<1||count>64)throw new IllegalArgumentException("Ресурс рецепта "+key);
            result.add(new ItemStack(material,count));}return List.copyOf(result);}
    public void stop(){if(task!=null){task.cancel();task=null;}}
    private boolean available(UUID town,BuildingDepot d){World w=Bukkit.getWorld(d.world());if(w==null)return false;
        int size=com.palmergames.bukkit.towny.object.Coord.getCellSize();
        for(int x=Math.floorDiv(d.minX(),16);x<=Math.floorDiv(d.maxX(),16);x++)for(int z=Math.floorDiv(d.minZ(),16);z<=Math.floorDiv(d.maxZ(),16);z++)if(!w.isChunkLoaded(x,z))return false;
        for(int x=Math.floorDiv(d.minX(),size);x<=Math.floorDiv(d.maxX(),size);x++)for(int z=Math.floorDiv(d.minZ(),size);z<=Math.floorDiv(d.maxZ(),size);z++){
            var owner=towny.townAt(new Location(w,(double)x*size,w.getMinHeight(),(double)z*size));if(owner==null||!town.equals(owner.getUUID()))return false;}return true;}
    private void produce(){
        Map<String,ItemStack[]> before=new LinkedHashMap<>();Map<String,TownData> owners=new HashMap<>();
        try{
            for(var town:data.towns().values()){
                var depots=storage.depots(town.townId());
                for(Recipe recipe:recipes){var depot=depots.get(recipe.project());if(depot==null||storage.busy(town.townId(),recipe.project())||!available(town.townId(),depot))continue;
                    ItemStack[] stock=storage.read(town,recipe.project());boolean changed=false;
                    for(int op=0;op<Math.min(5,depot.level());op++){
                        double bonus=recipe.districtBonus()?ru.neverland.integration.DistrictBonuses.multiplier(town.townId(),recipe.project()):1;
                        ItemStack[] output=recipe.output().stream().map(s->{var v=s.clone();v.setAmount(ru.neverland.integration.DistrictBonuses.output(s.getAmount(),bonus));return v;}).toArray(ItemStack[]::new);
                        var result=StockMath.recipe(stock,recipe.input(),output);if(result.isEmpty())break;stock=result.get();changed=true;
                    }
                    if(changed){String key=town.townId()+"/"+recipe.project();before.putIfAbsent(key,storage.read(town,recipe.project()));owners.put(key,town);storage.write(town,recipe.project(),stock);}
                }
            }
            if(!before.isEmpty())data.saveOrThrow();
        }catch(Exception ex){before.forEach((key,items)->storage.write(owners.get(key),key.substring(key.indexOf('/')+1),items));plugin.getLogger().warning("Производство откатило несохранённый цикл: "+ex.getMessage());}
    }
}
