package ru.neverland.townycrime;
import java.util.*;
import java.nio.file.*;
import java.io.IOException;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.core.AtomicFiles;
/** Publish snapshots only after a durable replacement; corrupt data never becomes an empty town. */
public final class CrimeRepository {
    private final Path file;private Map<UUID,CrimeState> states=Map.of();private boolean loaded;
    public CrimeRepository(Path file){this.file=file;}
    public Map<UUID,CrimeState> all(){return states;}
    public boolean writable(){return loaded&&AtomicFiles.writable(file);}
    public void load()throws IOException{
        loaded=false;var next=new LinkedHashMap<UUID,CrimeState>();
        if(Files.exists(file))try{
            var y=new YamlConfiguration();y.load(file.toFile());if(integer(y,"schema")!=1)throw new IOException("Неверная схема преступности");var root=section(y,"towns");
            for(String key:root.getKeys(false)){UUID id=UUID.fromString(key);var v=section(root,key);Object level=v.get("level");if(!(level instanceof Number n))throw new IOException("Неверный уровень");CrimeState.Incident incident=null;
                if(v.contains("incident")){var i=section(v,"incident");incident=new CrimeState.Incident(UUID.fromString(text(i,"id")),text(i,"kind"),text(i,"resource"),integer(i,"amount"),text(i,"phase"),integer(i,"created"),integer(i,"until"));}
                next.put(id,new CrimeState(id,n.doubleValue(),integer(v,"next-cycle"),integer(v,"next-incident"),incident));
            }
        }catch(Exception ex){throw new IOException("crime-data.yml повреждён; изменения остановлены",ex);}
        states=Map.copyOf(next);AtomicFiles.loaded(file);loaded=true;
    }
    public void put(CrimeState state)throws IOException{var next=new LinkedHashMap<>(states);next.put(state.town(),state);commit(next);}
    public void commit(Map<UUID,CrimeState> values)throws IOException{
        if(!writable())throw new IOException("Хранилище преступности остановлено до успешной загрузки");var next=Map.copyOf(values);for(var e:next.entrySet())if(!e.getKey().equals(e.getValue().town()))throw new IllegalArgumentException("Не совпадает город");if(next.equals(states))return;
        AtomicFiles.write(file,()->{var y=new YamlConfiguration();y.set("schema",1);y.createSection("towns");
            for(var s:next.values()){var v=y.createSection("towns."+s.town());v.set("level",s.level());v.set("next-cycle",s.nextCycle());v.set("next-incident",s.nextIncident());var i=s.incident();if(i!=null){var a=v.createSection("incident");a.set("id",i.id().toString());a.set("kind",i.kind());a.set("resource",i.resource());a.set("amount",i.amount());a.set("phase",i.phase());a.set("created",i.created());a.set("until",i.until());}}
            return y.saveToString();});states=next;
    }
    static ConfigurationSection section(ConfigurationSection c,String key)throws IOException{var s=c.getConfigurationSection(key);if(s==null)throw new IOException("Нет раздела "+key);return s;}
    static String text(ConfigurationSection c,String key)throws IOException{if(!c.isString(key))throw new IOException("Нет строки "+key);return c.getString(key);}
    static long integer(ConfigurationSection c,String key)throws IOException{Object v=c.get(key);if(!(v instanceof Integer||v instanceof Long))throw new IOException("Нужно целое "+key);return ((Number)v).longValue();}
}
