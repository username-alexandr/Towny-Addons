package cc.crafto.ui.server;
import org.bukkit.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import java.util.*;
import java.util.concurrent.*;
import static cc.crafto.ui.server.PublicMethods.*;

/** Persisted site identity + public blueprint API. Block reads stay on each owning chunk's region. */
final class CityBuildReader {
    record Result(List<CitySnapshot.Row> builds,List<CitySnapshot.Row> resources,Map<String,Integer> levels,Map<String,String> names,boolean known,List<SiteInfo> sites){}
    record SiteInfo(String id,String label,int total,int placed,boolean known,Map<Material,Integer> missing,Map<String,Material> expected,Set<String> installed){}
    private record Block(int x,int y,int z,Material material,Material supply){}
    private record Count(int known,int total,Map<Material,Integer> missing){}
    private final CraftoUIPlugin plugin;private final AddonFiles files;
    private final Map<UUID,Timed> cache=new HashMap<>();private record Timed(long at,CompletableFuture<Result> result){}
    CityBuildReader(CraftoUIPlugin plugin,AddonFiles files){this.plugin=plugin;this.files=files;}
    synchronized CompletableFuture<Result> read(Plugin addon,UUID town){
        var old=cache.get(town);long now=System.nanoTime();if(old!=null&&now-old.at()<8_000_000_000L)return old.result();
        if(cache.size()>256)cache.clear();
        CompletableFuture<Result> result;
        try{result=readNow(addon,town);}catch(Exception e){result=CompletableFuture.completedFuture(unavailable("Не удалось прочитать постройки."));}
        cache.put(town,new Timed(now,result));return result;
    }
    static Result unavailable(String text){return new Result(List.of(new CitySnapshot.Row(text)),List.of(new CitySnapshot.Row("Ресурсы пока недоступны.")),Map.of(),Map.of(),false,List.of());}
    private CompletableFuture<Result> readNow(Plugin addon,UUID town)throws Exception{
        if(addon==null||!addon.isEnabled())return CompletableFuture.completedFuture(unavailable("Аддон построек недоступен."));
        var snapshot=files.read(addon.getDataFolder().toPath().resolve("town-data.yml"));
        var data=CityBuildSchema.town(snapshot,town);
        Map<String,String> names=new HashMap<>();
        for(String file:List.of("projects.yml","custom-projects.yml")){
            var definitions=files.read(addon.getDataFolder().toPath().resolve(file));
            for(String section:List.of("buildings","wonders")){var root=definitions.getConfigurationSection(section);if(root!=null)for(String id:root.getKeys(false))names.put(id,CitySnapshot.clean(root.getString(id+".name",id)));}
        }
        Map<String,Integer> levels=new HashMap<>();if(data!=null&&data.getConfigurationSection("levels")!=null)for(String id:data.getConfigurationSection("levels").getKeys(false)){if(levels.size()>=128)break;levels.put(id,data.getInt("levels."+id));}
        var sites=data==null?null:data.getConfigurationSection("construction");
        List<CompletableFuture<SiteResult>> reads=new ArrayList<>();
        if(sites!=null)for(String id:new TreeSet<>(sites.getKeys(false))){var s=sites.getConfigurationSection(id);if(s!=null&&s.getBoolean("active")){if(reads.size()>=12)break;reads.add(site(addon,id,names.getOrDefault(id,id),s));}}
        return CompletableFuture.allOf(reads.toArray(CompletableFuture[]::new)).thenApply(v->{
            List<CitySnapshot.Row> builds=new ArrayList<>(),resources=new ArrayList<>();
            List<SiteInfo> details=new ArrayList<>();
            for(var future:reads){var result=future.join();details.add(result.info());builds.add(result.build());for(var row:result.resources())if(resources.size()<64)resources.add(row);}
            if(builds.isEmpty())builds.add(new CitySnapshot.Row("Активных строек нет."));
            if(resources.isEmpty())resources.add(new CitySnapshot.Row("Нет подтверждённых недостающих блоков."));
            return new Result(List.copyOf(builds),List.copyOf(resources),Map.copyOf(levels),Map.copyOf(names),true,List.copyOf(details));
        });
    }
    private record SiteResult(CitySnapshot.Row build,List<CitySnapshot.Row> resources,SiteInfo info){}
    private CompletableFuture<SiteResult> site(Plugin addon,String id,String name,ConfigurationSection s){
        String label=name+" • этап "+s.getInt("target-stage")+(s.getBoolean("admin-paused")?" • пауза":"");
        try{
            UUID worldId=UUID.fromString(s.getString("world"));World world=Bukkit.getWorld(worldId);if(world==null)throw new IllegalArgumentException("World unavailable");
            ClassLoader loader=addon.getClass().getClassLoader();String base="ru.neverland.townybuilds.construction.";
            Class<?> siteType=Class.forName(base+"ConstructionSite",true,loader);
            Class<?> styleApi=null;try{styleApi=Class.forName(base+"BuildingStyle",false,loader);}catch(ClassNotFoundException olderBuilds){}
            int architecture=CityBuildSchema.architecture(s,siteType.getField("CURRENT_ARCHITECTURE_VERSION").getInt(null),styleApi);
            Object generator=Class.forName(base+"BuildingBlueprintGenerator",true,loader).getConstructor().newInstance();
            Object site=siteType.getConstructor(String.class,UUID.class,int.class,int.class,int.class,BlockFace.class,int.class,int.class,int.class,boolean.class,int.class)
                .newInstance(id,worldId,s.getInt("x"),s.getInt("y"),s.getInt("z"),BlockFace.valueOf(s.getString("facing","NORTH")),s.getInt("completed-stage"),s.getInt("target-stage"),s.getInt("build-from-stage",1),true,architecture);
            String pin=CityBuildSchema.blueprintSet(s);Object plan;
            if(pin.isEmpty())plan=generator.getClass().getMethod("generateForArchitecture",String.class,int.class,int.class).invoke(generator,id,s.getInt("target-stage"),architecture);
            else{
                siteType.getMethod("blueprintSet",String.class).invoke(site,pin);
                plan=Class.forName(base+"BlueprintCatalog",true,loader).getMethod("sitePlan",siteType,int.class,generator.getClass()).invoke(null,site,s.getInt("target-stage"),generator);
            }
            if(plan==null)throw new IllegalArgumentException("Blueprint unavailable");
            var blocks=(Map<?,?>)call(plan,"blocks");if(blocks.size()>30000)throw new IllegalArgumentException("Blueprint too large");
            var locate=site.getClass().getMethod("location",World.class,Class.forName(base+"BlockOffset",true,loader));
            var supply=Class.forName("ru.neverland.townybuilds.service.ConstructionSupply",true,loader).getMethod("material",Material.class);
            Map<Material,Material> materials=new EnumMap<>(Material.class);
            Map<Long,List<Block>> chunks=new HashMap<>();int count=0;
            for(var entry:blocks.entrySet()){
                var expected=entry.getValue();if(!call(expected,"role").toString().equals("RESIDENT")||((Number)call(expected,"stage")).intValue()<s.getInt("build-from-stage",1))continue;
                Location at=(Location)locate.invoke(site,null,entry.getKey());int x=at.getBlockX(),y=at.getBlockY(),z=at.getBlockZ();
                if(y<world.getMinHeight()||y>=world.getMaxHeight())throw new IllegalArgumentException("Invalid height");
                long key=((long)(x>>4)<<32)|((z>>4)&0xffffffffL);chunks.computeIfAbsent(key,k->new ArrayList<>()).add(new Block(x,y,z,(Material)call(expected,"material"),(Material)supply.invoke(null,(Material)call(expected,"material"))));count++;
            }
            if(chunks.size()>64)throw new IllegalArgumentException("Too many chunks");
            Map<String,Material> expected=new HashMap<>();Set<String> installed=ConcurrentHashMap.newKeySet();
            for(var positions:chunks.values())for(var b:positions)expected.put(worldId+":"+b.x()+":"+b.y()+":"+b.z(),b.material());
            List<CompletableFuture<Count>> tasks=new ArrayList<>();
            for(var entry:chunks.entrySet()){
                var positions=List.copyOf(entry.getValue());var future=new CompletableFuture<Count>();tasks.add(future);
                int cx=(int)(entry.getKey()>>32),cz=(int)(long)entry.getKey();
                if(!world.isChunkLoaded(cx,cz)){future.complete(new Count(0,positions.size(),Map.of()));continue;}
                var reader=new java.util.function.Consumer<io.papermc.paper.threadedregions.scheduler.ScheduledTask>(){
                    int index;final Map<Material,Integer> missing=new EnumMap<>(Material.class);final Set<String> present=new HashSet<>();
                    @Override public void accept(io.papermc.paper.threadedregions.scheduler.ScheduledTask task){
                        if(future.isDone())return;
                        try{
                            if(!world.isChunkLoaded(cx,cz)){future.complete(new Count(0,positions.size(),Map.of()));return;}
                            int end=Math.min(positions.size(),index+256);long deadline=System.nanoTime()+2_000_000L;
                            do{var b=positions.get(index++);if(world.getBlockAt(b.x(),b.y(),b.z()).getType()!=b.material())missing.merge(b.supply(),1,Integer::sum);else present.add(worldId+":"+b.x()+":"+b.y()+":"+b.z());}while(index<end&&System.nanoTime()<deadline&&!future.isDone());
                            if(future.isDone())return;
                            if(index<positions.size())plugin.getServer().getRegionScheduler().runDelayed(plugin,world,cx,cz,this,1);
                            else{installed.addAll(present);future.complete(new Count(positions.size(),positions.size(),Map.copyOf(missing)));}
                        }catch(Exception e){future.complete(new Count(0,positions.size(),Map.of()));}
                    }
                };
                plugin.getServer().getRegionScheduler().run(plugin,world,cx,cz,reader);
                future.completeOnTimeout(new Count(0,positions.size(),Map.of()),6,TimeUnit.SECONDS);
            }
            final int total=count;
            return CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).thenApply(v->{
                int known=0;Map<Material,Integer> missing=new TreeMap<>();
                for(var future:tasks){var result=future.join();known+=result.known();result.missing().forEach((m,n)->missing.merge(m,n,Integer::sum));}
                if(known<total)return new SiteResult(new CitySnapshot.Row(label+" — часть чанков не загружена"),List.of(new CitySnapshot.Row(name+": ресурсы уточнятся возле площадки.")),new SiteInfo(id,label,total,installed.size(),false,Map.of(),Map.copyOf(expected),Set.copyOf(installed)));
                int remaining=missing.values().stream().mapToInt(Integer::intValue).sum();List<CitySnapshot.Row> rows=new ArrayList<>();
                missing.forEach((material,n)->rows.add(new CitySnapshot.Row(name+": ещё "+n+" × ",material.translationKey())));
                return new SiteResult(new CitySnapshot.Row(label+" — установлено "+(total-remaining)+" / "+total),List.copyOf(rows),new SiteInfo(id,label,total,total-remaining,true,Map.copyOf(missing),Map.copyOf(expected),Set.copyOf(installed)));
            });
        }catch(Exception e){return CompletableFuture.completedFuture(new SiteResult(new CitySnapshot.Row(label+" — подсчёт блоков недоступен"),List.of(new CitySnapshot.Row(name+": проверьте ресурсы в меню построек.")),new SiteInfo(id,label,0,0,false,Map.of(),Map.of(),Set.of())));}
    }
}
