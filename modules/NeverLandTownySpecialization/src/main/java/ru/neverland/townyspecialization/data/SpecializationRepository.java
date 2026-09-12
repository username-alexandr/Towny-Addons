package ru.neverland.townyspecialization.data;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.townyspecialization.model.CityChoice;
import ru.neverland.townyspecialization.config.SpecializationSettings;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.*;
public final class SpecializationRepository {
    private final Path file;private boolean writable;private volatile Map<UUID,CityChoice> towns=Map.of();
    public SpecializationRepository(Path file){this.file=file;}public Map<UUID,CityChoice> towns(){return towns;}public CityChoice get(UUID town){return towns.getOrDefault(town,CityChoice.empty());}
    public void load()throws Exception{writable=false;if(!Files.exists(file)){towns=Map.of();ru.neverland.core.AtomicFiles.loaded(file);writable=true;return;}var y=new YamlConfiguration();y.load(file.toFile());if(SpecializationSettings.number(y.get("schema"))!=1||!y.isConfigurationSection("towns"))throw new IOException("Неверная база специализаций");Map<UUID,CityChoice> next=new HashMap<>();var root=y.getConfigurationSection("towns");for(String key:root.getKeys(false)){var t=root.getConfigurationSection(key);if(t==null)throw new IOException("Неверный город");next.put(UUID.fromString(key),new CityChoice(t.getString("specialization"),SpecializationSettings.number(t.get("chosen-at")),SpecializationSettings.number(t.get("next-change-at")),SpecializationSettings.number(t.get("revision"))));}towns=Map.copyOf(next);ru.neverland.core.AtomicFiles.loaded(file);writable=true;}
    public void put(UUID town,CityChoice state)throws IOException{var next=new HashMap<>(towns);next.put(town,state);replace(next);}
    public void replace(Map<UUID,CityChoice> next)throws IOException{if(!writable||!ru.neverland.core.AtomicFiles.writable(file))throw new IOException("База не загружена; запись запрещена");next=Map.copyOf(next);if(next.equals(towns))return;var y=new YamlConfiguration();y.set("schema",1);y.createSection("towns");for(var e:next.entrySet()){String k="towns."+e.getKey()+".";var s=e.getValue();y.set(k+"specialization",s.specialization());y.set(k+"chosen-at",s.chosenAt());y.set(k+"next-change-at",s.nextChangeAt());y.set(k+"revision",s.revision());}
        ru.neverland.core.AtomicFiles.write(file,y::saveToString);towns=next;
    }
}
