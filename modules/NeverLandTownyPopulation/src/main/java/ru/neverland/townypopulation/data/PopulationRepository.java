package ru.neverland.townypopulation.data;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import ru.neverland.townypopulation.model.PopulationState;
import ru.neverland.core.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** The original schema, acknowledged whole-city batches, and no publish before durable file replacement. */
public final class PopulationRepository {
    private final Path file;private final AcknowledgedState<Map<UUID,PopulationState>> images;private volatile boolean readable;
    public PopulationRepository(Path file){this.file=file;images=new AcknowledgedState<>(file,Map.of(),PopulationRepository::encode,"NeverLand-population-write");}
    public boolean readable(){return readable;}public boolean writable(){return readable&&images.writable();}public boolean busy(){return images.busy();}
    public Map<UUID,PopulationState> state(){return images.get();}public PopulationState get(UUID id){return state().get(id);}public Set<UUID> ids(){return state().keySet();}
    public void load()throws Exception{
        readable=false;var states=new HashMap<UUID,PopulationState>();
        if(!Files.notExists(file)){
            if(Files.size(file)>64*1024*1024)throw new IOException("Слишком большой файл населения");var yaml=SafeYaml.load(file);SafeYaml.keys(yaml,"schema","towns");if(SafeYaml.integer(yaml,"schema")!=1||!yaml.isConfigurationSection("towns"))throw new IOException("Неизвестная версия population-data.yml");var towns=yaml.getConfigurationSection("towns");
            for(String id:towns.getKeys(false)){var row=towns.getConfigurationSection(id);if(row==null)throw new IOException("Повреждена запись населения города");SafeYaml.keys(row,"population","remainder","last-change","last-cycle");states.put(UUID.fromString(id),new PopulationState(Math.toIntExact(integer(row,"population")),decimal(row,"remainder"),Math.toIntExact(integer(row,"last-change")),integer(row,"last-cycle")));}
        }
        images.loaded(Map.copyOf(states));readable=true;
    }
    private static long integer(ConfigurationSection row,String key)throws IOException{Object raw=row.get(key);if(!(raw instanceof Number n))throw new IOException("Неверное целое поле населения: "+key);try{return new java.math.BigDecimal(n.toString()).longValueExact();}catch(NumberFormatException|ArithmeticException error){throw new IOException("Неверное целое поле населения: "+key,error);}}
    private static double decimal(ConfigurationSection row,String key)throws IOException{Object raw=row.get(key);if(!(raw instanceof Number n)||!Double.isFinite(n.doubleValue()))throw new IOException("Неверная дробь населения: "+key);return n.doubleValue();}
    public CompletionStage<Map<UUID,PopulationState>> commit(Map<UUID,PopulationState> expected,Map<UUID,PopulationState> next){if(!writable())return CompletableFuture.failedFuture(new IOException("Запись населения приостановлена; подтверждённые жители сохранены"));return images.commit(expected,Map.copyOf(next));}
    public CompletionStage<Void> prepareClose(){return images.prepareClose();}public void awaitClosed(){images.awaitClosed();}
    private static String encode(Map<UUID,PopulationState> states)throws IOException{
        var yaml=new YamlConfiguration();yaml.set("schema",1);yaml.createSection("towns");new TreeMap<>(states).forEach((id,state)->{String key="towns."+id+".";yaml.set(key+"population",state.population());yaml.set(key+"remainder",state.remainder());yaml.set(key+"last-change",state.lastChange());yaml.set(key+"last-cycle",state.lastCycle());});String text=yaml.saveToString();if(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>64*1024*1024)throw new IOException("Файл населения требует административной проверки объёма");return text;
    }
}
