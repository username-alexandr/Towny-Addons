package ru.neverland.mintevents.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.core.AtomicFiles;
import ru.neverland.core.SafeYaml;
import ru.neverland.mintevents.model.FireDamage;

public final class FireDamageRepository {
    private final Path file;
    private Map<UUID,FireDamage> records=Map.of();
    private boolean loaded;
    public FireDamageRepository(Path file){this.file=file;}
    public boolean writable(){return loaded&&AtomicFiles.writable(file);}
    public void load() throws IOException {
        loaded=false;Map<UUID,FireDamage> next=new LinkedHashMap<>();
        try {
            if(Files.exists(file)){
                YamlConfiguration y=new YamlConfiguration();y.load(file.toFile());
                if(SafeYaml.intValue(y,"schema")!=1||!y.isConfigurationSection("damage"))throw new IOException("Неверная схема повреждений пожара");
                ConfigurationSection root=y.getConfigurationSection("damage");
                for(String key:root.getKeys(false)){
                    ConfigurationSection s=root.getConfigurationSection(key);if(s==null)throw new IOException("Повреждена запись ремонта");
                    FireDamage damage=new FireDamage(UUID.fromString(SafeYaml.stringValue(s,"town")),UUID.fromString(SafeYaml.stringValue(s,"world")),SafeYaml.intValue(s,"x"),SafeYaml.intValue(s,"y"),SafeYaml.intValue(s,"z"),SafeYaml.stringValue(s,"block"),SafeYaml.longValue(s,"incident"));
                    org.bukkit.Material.valueOf(damage.material());
                    if(!damage.id().toString().equals(key)||!FireMaterials.combustible(damage.material()))throw new IOException("Неверный блок или координаты ремонта");
                    next.put(damage.id(),damage);
                }
            }
        }catch(Exception error){throw new IOException("Нельзя загрузить fire-damage.yml; изменения остановлены",error);}
        records=Map.copyOf(next);AtomicFiles.loaded(file);loaded=true;
    }
    public List<FireDamage> town(UUID town){return records.values().stream().filter(d->d.town().equals(town)).sorted(java.util.Comparator.comparing(d->d.id().toString())).toList();}
    public FireDamage get(UUID id){return records.get(id);}
    public boolean pending(UUID world,int x,int y,int z){return records.containsKey(FireDamage.id(world,x,y,z));}
    public boolean prepare(FireDamage damage) throws IOException {
        requireWritable();FireDamage old=records.get(damage.id());
        if(old!=null){if(!old.equals(damage))throw new IllegalArgumentException("Место уже ожидает ремонта");return false;}
        if(!FireMaterials.combustible(damage.material()))throw new IllegalArgumentException("Материал не поддерживает пожар");
        Map<UUID,FireDamage> next=new LinkedHashMap<>(records);next.put(damage.id(),damage);save(next);return true;
    }
    /** Caller has verified placed materials and saved the affected worlds first. */
    public void confirm(Set<UUID> repaired) throws IOException {
        requireWritable();Map<UUID,FireDamage> next=new LinkedHashMap<>(records);repaired.forEach(next::remove);if(next.size()!=records.size())save(next);
    }
    private void requireWritable() throws IOException{if(!writable())throw new IOException("Журнал ремонта заблокирован после ошибки записи");}
    private void save(Map<UUID,FireDamage> next) throws IOException {
        requireWritable();AtomicFiles.write(file,()->{
            YamlConfiguration y=new YamlConfiguration();y.set("schema",1);y.createSection("damage");
            next.forEach((id,d)->{String p="damage."+id+".";y.set(p+"town",d.town().toString());y.set(p+"world",d.world().toString());y.set(p+"x",d.x());y.set(p+"y",d.y());y.set(p+"z",d.z());y.set(p+"block",d.blockData());y.set(p+"incident",d.incident());});return y.saveToString();
        });records=Map.copyOf(next);
    }
}
