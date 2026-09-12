package ru.neverland.townypower.data;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.townypower.config.PowerSettings;
import ru.neverland.townypower.model.TownPowerState;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.*;
public final class PowerRepository {
    private final Path file;private boolean writable;private Map<UUID,TownPowerState> towns=Map.of();
    public PowerRepository(Path file){this.file=file;}public Map<UUID,TownPowerState> towns(){return towns;}
    public void load()throws Exception{
        writable=false;if(!Files.exists(file)){towns=Map.of();ru.neverland.core.AtomicFiles.loaded(file);writable=true;return;}
        var y=new YamlConfiguration();y.load(file.toFile());if(PowerSettings.number(y.get("schema"))!=1||!y.isConfigurationSection("towns"))throw new IOException("Неверная схема power-data.yml");
        Map<UUID,TownPowerState> next=new HashMap<>();var root=y.getConfigurationSection("towns");
        for(String id:root.getKeys(false)){var t=root.getConfigurationSection(id);if(t==null||!(t.get("stopped") instanceof List<?> raw)||!t.isConfigurationSection("priorities"))throw new IOException("Неверная запись города");
            Set<String> stopped=new HashSet<>();for(var v:raw)if(!(v instanceof String name)||!stopped.add(name))throw new IOException("Неверный список остановленных зданий");
            Map<String,Integer> priorities=new HashMap<>();for(String name:t.getConfigurationSection("priorities").getKeys(false))priorities.put(name,Math.toIntExact(PowerSettings.number(t.get("priorities."+name))));next.put(UUID.fromString(id),new TownPowerState(stopped,priorities));}
        towns=Map.copyOf(next);ru.neverland.core.AtomicFiles.loaded(file);writable=true;
    }
    public void replace(Map<UUID,TownPowerState> next)throws IOException{
        if(!writable||!ru.neverland.core.AtomicFiles.writable(file))throw new IOException("База не загружена; запись запрещена");next=Map.copyOf(next);if(next.equals(towns))return;
        var y=new YamlConfiguration();y.set("schema",1);y.createSection("towns");for(var e:next.entrySet()){String k="towns."+e.getKey()+".";y.set(k+"stopped",e.getValue().stopped().stream().sorted().toList());y.createSection(k+"priorities",e.getValue().priorities());}
        ru.neverland.core.AtomicFiles.write(file,y::saveToString);towns=next;
    }
}
