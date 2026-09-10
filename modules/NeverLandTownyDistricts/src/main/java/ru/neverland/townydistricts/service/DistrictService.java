package ru.neverland.townydistricts.service;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Coord;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.WorldCoord;
import com.palmergames.bukkit.towny.event.TownClaimEvent;
import com.palmergames.bukkit.towny.event.town.TownUnclaimEvent;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.townydistricts.api.TownyDistrictsApi;
import ru.neverland.townydistricts.config.DistrictSettings;
import ru.neverland.townydistricts.data.DistrictRepository;
import ru.neverland.townydistricts.integration.BuildsBridge;
import ru.neverland.townydistricts.model.*;
import java.util.*;
import java.io.IOException;

public final class DistrictService implements TownyDistrictsApi,Listener {
    public record View(List<District> districts,List<DistrictRules.Building> buildings,DistrictRules.Evaluation evaluation,boolean paused){
        public View{districts=List.copyOf(districts);buildings=List.copyOf(buildings);}
    }
    private record Snapshot(Map<UUID,View> towns,Map<Cell,District> cells){}
    private final JavaPlugin plugin;private final DistrictRepository repository;private final BuildsBridge builds=new BuildsBridge();
    private final int cellSize;private DistrictSettings settings;private long warningAt;
    private volatile Snapshot snapshot=new Snapshot(Map.of(),Map.of());private BukkitTask task;private volatile boolean dirty;
    public DistrictService(JavaPlugin plugin,DistrictRepository repository,DistrictSettings settings){this.plugin=plugin;this.repository=repository;this.settings=settings;this.cellSize=Coord.getCellSize();}
    public DistrictSettings settings(){return settings;}public int cellSize(){return cellSize;}
    public Town town(Player player){var resident=TownyAPI.getInstance().getResident(player);return resident==null?null:resident.getTownOrNull();}
    public boolean manager(Player player,Town town){var resident=TownyAPI.getInstance().getResident(player);return resident!=null&&town.equals(resident.getTownOrNull())
            &&(town.isMayor(resident)||resident.hasTownRank("assistant")||player.hasPermission("neverlandtownydistricts.manage"));}
    public Cell cell(Player player){var coord=WorldCoord.parseWorldCoord(player.getLocation().getBlock());return new Cell(player.getWorld().getUID(),coord.getX(),coord.getZ());}
    public Set<Cell> owned(Town town){Set<Cell> cells=new HashSet<>();for(TownBlock block:town.getTownBlocks()){
        var coord=block.getWorldCoord();World world=Bukkit.getWorld(coord.getWorldName());if(world!=null)cells.add(new Cell(world.getUID(),coord.getX(),coord.getZ()));}return cells;}
    public List<District> list(UUID town){return repository.all().stream().filter(d->d.town().equals(town)).sorted(Comparator.comparing(District::id)).toList();}
    public District require(UUID town,String id){return repository.get(town,id).orElseThrow(()->new IllegalArgumentException("Район не найден: "+id));}
    public void put(District district,boolean create)throws IOException{
        checkGrid();Town town=TownyAPI.getInstance().getTown(district.town());if(town==null)throw new IllegalArgumentException("Город больше не существует");
        if(create){if(repository.get(district.town(),district.id()).isPresent())throw new IllegalArgumentException("Этот ID уже занят");
            if(list(district.town()).size()>=settings.maxDistricts())throw new IllegalArgumentException("Достигнут предел районов города");}
        DistrictRules.validate(district,repository.all(),owned(town),settings.maxCells());repository.put(district);refresh();
    }
    public void delete(UUID town,String id)throws IOException{require(town,id);repository.delete(town,id);refresh();}
    public void start(){safeRefresh();task=Bukkit.getScheduler().runTaskTimer(plugin,this::safeRefresh,100,100);}
    public void stop(){if(task!=null)task.cancel();snapshot=new Snapshot(Map.of(),Map.of());}
    public void reload(DistrictSettings value){var old=settings;try{settings=value;refresh();}catch(RuntimeException ex){settings=old;throw ex;}}
    private void checkGrid(){if(Coord.getCellSize()!=cellSize)throw new IllegalStateException("Размер участков Towny изменился; требуется миграция базы районов");}
    private void safeRefresh(){try{refresh();}catch(RuntimeException ex){snapshot=new Snapshot(Map.of(),Map.of());warn(ex.getMessage());}}
    public void refresh(){
        checkGrid();Map<UUID,Town> towns=new HashMap<>();Map<UUID,Set<Cell>> owned=new HashMap<>();
        Set<UUID> relevant=new HashSet<>();repository.all().forEach(d->relevant.add(d.town()));
        for(UUID id:relevant){Town t=TownyAPI.getInstance().getTown(id);if(t!=null){towns.put(id,t);owned.put(id,owned(t));}}
        List<District> retained=new ArrayList<>();
        for(District d:repository.all())if(towns.containsKey(d.town())){
            Set<Cell> cells=new HashSet<>();for(Cell c:d.cells())if(Bukkit.getWorld(c.world())==null||owned.get(d.town()).contains(c))cells.add(c);
            if(!cells.isEmpty())retained.add(d.withCells(cells));
        }
        try{repository.replace(retained);}catch(IOException ex){throw new IllegalStateException("Не удалось сохранить районы: "+ex.getMessage(),ex);}
        Map<UUID,View> views=new HashMap<>();Map<Cell,District> cells=new HashMap<>();
        for(var entry:towns.entrySet()){
            UUID town=entry.getKey();List<District> districts=list(town);if(districts.isEmpty())continue;
            List<DistrictRules.Building> buildings;boolean paused=false;
            try{buildings=builds.buildings(town);}catch(ReflectiveOperationException|RuntimeException|LinkageError ex){buildings=List.of();paused=true;warn("Бонусы районов приостановлены: "+ex.getMessage());}
            var result=DistrictRules.evaluate(districts,buildings,settings.types(),owned.get(town),cellSize,settings.matching(),settings.combination(),settings.maximum(),settings.requires());
            views.put(town,new View(districts,buildings,result,paused));
            for(District d:districts)for(Cell c:d.cells())if(owned.get(town).contains(c))cells.put(c,d);
        }
        snapshot=new Snapshot(Map.copyOf(views),Map.copyOf(cells));dirty=false;
    }
    private Snapshot current(){if(dirty){if(Bukkit.isPrimaryThread())safeRefresh();else return new Snapshot(Map.of(),Map.of());}return snapshot;}
    public View view(UUID town){return current().towns().getOrDefault(town,new View(List.of(),List.of(),new DistrictRules.Evaluation(Map.of(),Map.of(),Set.of()),false));}
    @Override public double multiplier(UUID town,String project){return view(town).evaluation().multipliers().getOrDefault(project,1.0);}
    @Override public Collection<District> districts(UUID town){return view(town).districts();}
    @Override public Optional<District> districtAt(UUID world,int x,int z){return Optional.ofNullable(current().cells().get(new Cell(world,Math.floorDiv(x,cellSize),Math.floorDiv(z,cellSize))));}
    public double districtMultiplier(District district){var view=view(district.town());return view.evaluation().districts().entrySet().stream()
            .filter(e->e.getValue().equals(district.id())).mapToDouble(e->view.evaluation().multipliers().getOrDefault(e.getKey(),1.0)).max().orElse(1);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void claim(TownClaimEvent event){invalidate();}
    @EventHandler(priority=EventPriority.MONITOR)public void unclaim(TownUnclaimEvent event){invalidate();}
    private void invalidate(){if(!dirty){dirty=true;Bukkit.getScheduler().runTask(plugin,this::safeRefresh);}}
    private void warn(String message){long now=System.currentTimeMillis();if(now-warningAt>60000){warningAt=now;plugin.getLogger().warning(message);}}
}
