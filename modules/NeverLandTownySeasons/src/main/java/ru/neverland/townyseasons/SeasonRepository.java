package ru.neverland.townyseasons;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.core.*;

/** Clock origin and manual overrides commit together, before publishing memory. */
public final class SeasonRepository {
    private final Path file;
    private long epoch;
    private Map<UUID,Season> overrides=Map.of();
    private boolean loaded;
    public SeasonRepository(Path file) { this.file=file; }
    public void load(long now) throws IOException {
        loaded=false;
        if(!Files.exists(file)) { if(now<1)throw new IllegalArgumentException("Неверная дата"); write(now,Map.of()); }
        else {
            var y=SafeYaml.load(file);SafeYaml.keys(y,"schema","epoch","overrides");
            if(SafeYaml.intValue(y,"schema")!=1)throw new IllegalArgumentException("Неподдерживаемый календарь");
            long start=SafeYaml.longValue(y,"epoch");if(start<1)throw new IllegalArgumentException("Неверное начало календаря");
            var next=new HashMap<UUID,Season>();var section=SafeYaml.section(y,"overrides");
            if(section!=null)for(String key:section.getKeys(false))next.put(UUID.fromString(key),Season.parse(SafeYaml.text(section,key)));
            epoch=start;overrides=Map.copyOf(next);
        }
        AtomicFiles.loaded(file);loaded=true;
    }
    public boolean healthy() { return loaded&&AtomicFiles.writable(file); }
    public long epoch() { require();return epoch; }
    public Map<UUID,Season> overrides() { require();return overrides; }
    public void set(UUID world,Season season) throws IOException {
        require();Objects.requireNonNull(world);var next=new HashMap<>(overrides);
        if(season==null)next.remove(world);else next.put(world,season);
        write(epoch,next);
    }
    private void require() { if(!healthy())throw new IllegalStateException("Календарь заблокирован: проверьте calendar.yml и перезапустите плагин"); }
    private void write(long start,Map<UUID,Season> next) throws IOException {
        var y=new YamlConfiguration();y.set("schema",1);y.set("epoch",start);
        next.forEach((id,s)->y.set("overrides."+id,s.name()));
        AtomicFiles.write(file,y::saveToString);epoch=start;overrides=Map.copyOf(next);
    }
}
