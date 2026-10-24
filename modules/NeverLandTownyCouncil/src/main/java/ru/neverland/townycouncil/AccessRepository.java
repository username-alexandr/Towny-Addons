package ru.neverland.townycouncil;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.core.AtomicFiles;

/** Access overrides and their audit are written together; old council.yml is untouched. */
public final class AccessRepository {
    private final Path file;private Map<UUID,AccessRules> cities=Map.of();private List<String> history=List.of();private boolean writable;
    public AccessRepository(Path file){this.file=file;}
    public boolean writable(){return writable&&AtomicFiles.writable(file);}
    public Map<UUID,AccessRules> cities(){return cities;}
    public List<String> history(){return history;}
    public void load()throws Exception{
        writable=false;var next=new HashMap<UUID,AccessRules>();var y=new YamlConfiguration();
        if(Files.exists(file)){
            y.load(file.toFile());if(y.getInt("schema",-1)!=1||!y.isConfigurationSection("cities")||!y.isList("history"))throw new IOException("Повреждён файл прав города");
            var cities=y.getConfigurationSection("cities");
            for(String id:cities.getKeys(false)){
                var s=cities.getConfigurationSection(id);if(s==null)throw new IOException("Повреждён город");
                var roles=new HashMap<String,Map<String,Boolean>>();var people=new HashMap<UUID,Map<String,Boolean>>();
                var rs=section(s,"roles");for(String role:rs.getKeys(false))roles.put(role,read(section(rs,role)));
                var ps=section(s,"residents");for(String resident:ps.getKeys(false))people.put(UUID.fromString(resident),read(section(ps,resident)));
                Object rev=s.get("revision");if(!(rev instanceof Integer||rev instanceof Long))throw new IOException("Повреждена версия прав");
                next.put(UUID.fromString(id),new AccessRules(UUID.fromString(Objects.requireNonNull(s.getString("mayor"))),((Number)rev).longValue(),roles,people));
            }
            if(y.getList("history").stream().anyMatch(v->!(v instanceof String)))throw new IOException("Повреждён журнал прав");
        }
        cities=Map.copyOf(next);history=List.copyOf(y.getStringList("history"));AtomicFiles.loaded(file);writable=true;
    }
    private static ConfigurationSection section(ConfigurationSection s,String key)throws IOException{var r=s.getConfigurationSection(key);if(r==null)throw new IOException("Повреждён раздел "+key);return r;}
    private static Map<String,Boolean> read(ConfigurationSection s)throws IOException{
        var result=new HashMap<String,Boolean>();for(String action:s.getKeys(false)){Object v=s.get(action);if(!(v instanceof Boolean b))throw new IOException("Право должно быть true или false");TownAction.parse(action);result.put(action,b);}return result;
    }
    public void commit(Map<UUID,AccessRules> next,String entry)throws IOException{
        if(!writable())throw new IOException("Права приостановлены после ошибки сохранения");
        var audit=new ArrayList<>(history);audit.add(entry);if(audit.size()>500)audit=new ArrayList<>(audit.subList(audit.size()-500,audit.size()));
        var y=new YamlConfiguration();y.set("schema",1);y.createSection("cities");y.set("history",audit);
        next.forEach((id,r)->{String p="cities."+id+".";y.set(p+"mayor",r.mayor().toString());y.set(p+"revision",r.revision());
            y.createSection(p+"roles");y.createSection(p+"residents");
            r.roles().forEach((role,m)->m.forEach((a,v)->y.set(p+"roles."+role+"."+a,v)));
            r.residents().forEach((resident,m)->m.forEach((a,v)->y.set(p+"residents."+resident+"."+a,v)));
        });
        try{AtomicFiles.write(file,y::saveToString);cities=Map.copyOf(next);history=List.copyOf(audit);}catch(IOException ex){writable=false;throw ex;}
    }
}
