package ru.neverland.townydistricts.data;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.townydistricts.model.*;
import java.nio.file.*;
import java.util.*;
import java.io.IOException;

public final class DistrictRepository {
    private final Path file;private final int cellSize;private Map<String,District> districts=new LinkedHashMap<>();
    public DistrictRepository(Path file,int cellSize){this.file=file;this.cellSize=cellSize;}
    private String key(District district){return district.town()+"/"+district.id();}
    public Collection<District> all(){return List.copyOf(districts.values());}
    public Optional<District> get(UUID town,String id){return Optional.ofNullable(districts.get(town+"/"+id));}
    public void load() throws Exception {
        if(!Files.exists(file))return;
        var y=new YamlConfiguration();y.load(file.toFile());
        if(y.getInt("schema")!=1||y.getInt("cell-size")!=cellSize)throw new IOException("Версия базы или размер участков Towny изменились");
        var towns=y.getConfigurationSection("towns");if(towns==null)throw new IOException("Нет раздела towns");
        Map<String,District> loaded=new LinkedHashMap<>();Set<Cell> occupied=new HashSet<>();
        for(String town:towns.getKeys(false)){
            var section=towns.getConfigurationSection(town);if(section==null)throw new IOException("Повреждённая запись города");
            for(String id:section.getKeys(false)){
                var p=section.getConfigurationSection(id);if(p==null)throw new IOException("Повреждённая запись района");
                Set<Cell> cells=new HashSet<>();for(String raw:p.getStringList("cells"))if(!cells.add(Cell.decode(raw)))throw new IOException("Повтор участка");
                District d=new District(UUID.fromString(town),id,p.getString("name"),DistrictType.parse(p.getString("type","")),cells);
                if(!Collections.disjoint(occupied,cells))throw new IOException("Пересекающиеся районы в базе");occupied.addAll(cells);loaded.put(key(d),d);
            }
        }
        districts=loaded;
    }
    public void put(District district) throws IOException{var copy=new LinkedHashMap<>(districts);copy.put(key(district),district);replace(copy.values());}
    public void delete(UUID town,String id)throws IOException{var copy=new LinkedHashMap<>(districts);copy.remove(town+"/"+id);replace(copy.values());}
    /** Persist before publishing the mutation in memory. */
    public void replace(Collection<District> values)throws IOException{
        Map<String,District> next=new LinkedHashMap<>();values.forEach(d->next.put(key(d),d));if(next.equals(districts))return;
        var y=new YamlConfiguration();y.set("schema",1);y.set("cell-size",cellSize);y.createSection("towns");
        for(District d:next.values()){
            String key="towns."+d.town()+"."+d.id()+".";y.set(key+"name",d.name());y.set(key+"type",d.type().id());
            y.set(key+"cells",d.cells().stream().map(Cell::encoded).sorted().toList());
        }
        Files.createDirectories(file.toAbsolutePath().getParent());Path tmp=Files.createTempFile(file.toAbsolutePath().getParent(),"districts-",".tmp");
        try{y.save(tmp.toFile());try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException ex){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}districts=next;
        }finally{Files.deleteIfExists(tmp);}
    }
}
