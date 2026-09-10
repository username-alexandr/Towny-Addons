package ru.neverland.townyresearch.data;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.townyresearch.config.ResearchSettings;
import ru.neverland.townyresearch.model.*;
import java.util.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
public final class ResearchRepository implements ResearchProcessor.Store {
    private final Path file;private boolean writable;private volatile Map<UUID,CityStudy> towns=Map.of();
    public ResearchRepository(Path file){this.file=file;}public Map<UUID,CityStudy> towns(){return towns;}public CityStudy get(UUID town){return towns.getOrDefault(town,CityStudy.empty());}
    public void load()throws Exception{writable=false;if(!Files.exists(file)){towns=Map.of();writable=true;return;}var y=new YamlConfiguration();y.load(file.toFile());if(ResearchSettings.integer(y.get("schema"))!=1||!y.isConfigurationSection("towns"))throw new IOException("Неверная схема research-data.yml");Map<UUID,CityStudy> next=new HashMap<>();var root=y.getConfigurationSection("towns");
        for(String id:root.getKeys(false)){var t=root.getConfigurationSection(id);if(t==null||!t.isConfigurationSection("learned")||!(t.get("cleanup") instanceof List<?> raw))throw new IOException("Неверная запись города");Set<UUID> cleanup=new HashSet<>();for(Object receipt:raw)if(!cleanup.add(UUID.fromString(String.valueOf(receipt))))throw new IOException("Повтор квитанции");CityStudy.Study active=null;
            if(t.contains("active")){var a=t.getConfigurationSection("active");if(a==null||!a.isConfigurationSection("buildings"))throw new IOException("Неверное активное исследование");active=new CityStudy.Study(UUID.fromString(a.getString("invoice")),a.getString("technology"),Math.toIntExact(ResearchSettings.integer(a.get("level"))),ResearchSettings.integer(a.get("cost")),Math.toIntExact(ResearchSettings.integer(a.get("duration"))),Math.toIntExact(ResearchSettings.integer(a.get("remaining"))),ResearchSettings.levels(a.getConfigurationSection("buildings")),CityStudy.Phase.valueOf(a.getString("phase")));}
            next.put(UUID.fromString(id),new CityStudy(ResearchSettings.levels(t.getConfigurationSection("learned")),active,cleanup));}
        towns=Map.copyOf(next);writable=true;
    }
    @Override public void put(UUID town,CityStudy value)throws IOException{var next=new HashMap<>(towns);next.put(town,value);replace(next);}
    public void replace(Map<UUID,CityStudy> next)throws IOException{if(!writable)throw new IOException("База не загружена; запись запрещена");next=Map.copyOf(next);if(next.equals(towns))return;var y=new YamlConfiguration();y.set("schema",1);y.createSection("towns");for(var e:next.entrySet()){String key="towns."+e.getKey()+".";var state=e.getValue();y.createSection(key+"learned",state.learned());y.set(key+"cleanup",state.cleanup().stream().map(UUID::toString).sorted().toList());var s=state.active();if(s!=null){String k=key+"active.";y.set(k+"invoice",s.invoice().toString());y.set(k+"technology",s.technology());y.set(k+"level",s.level());y.set(k+"cost",s.cost());y.set(k+"duration",s.duration());y.set(k+"remaining",s.remaining());y.createSection(k+"buildings",s.buildings());y.set(k+"phase",s.phase().name());}}
        Path parent=file.toAbsolutePath().getParent();Files.createDirectories(parent);Path tmp=Files.createTempFile(parent,"research-",".tmp");try{try(var ch=FileChannel.open(tmp,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)){var bytes=ByteBuffer.wrap(y.saveToString().getBytes(StandardCharsets.UTF_8));while(bytes.hasRemaining())ch.write(bytes);ch.force(true);}try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}towns=next;}finally{Files.deleteIfExists(tmp);}
    }
}
