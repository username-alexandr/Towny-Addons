package ru.neverland.mintevents.scenarios;

import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.core.SafeYaml;
import static ru.neverland.mintevents.scenarios.ScenarioBook.*;

final class ScenarioCodec {
    private ScenarioCodec(){}
    static ConfigurationSection section(ConfigurationSection parent,String key){var s=SafeYaml.section(parent,key);if(s==null)throw new IllegalArgumentException("Отсутствует раздел "+key);return s;}
    static Terms terms(ConfigurationSection s,boolean stored){
        var plans=new ArrayList<Plan>();var p=section(s,"plans");for(String id:p.getKeys(false)){var row=section(p,id);plans.add(new Plan(id,SafeYaml.text(row,"name"),SafeYaml.text(row,"description"),mix(section(row,"response")),mix(section(row,"recovery"))));}
        return new Terms(SafeYaml.text(s,"mode"),SafeYaml.text(s,"title"),Math.multiplyExact(SafeYaml.integer(s,stored?"warning-millis":"warning-seconds"),stored?1:1000),SafeYaml.intValue(s,"recovery-progress-percent"),plans);
    }
    private static Mix mix(ConfigurationSection s){return new Mix(SafeYaml.doubleValue(s,"crop-loss-factor"),SafeYaml.doubleValue(s,"output-loss-factor"));}
    static void terms(ConfigurationSection s,Terms t){
        s.set("mode",t.mode());s.set("title",t.title());s.set("warning-millis",t.warningMillis());s.set("recovery-progress-percent",t.recoveryPercent());var plans=s.createSection("plans");for(var p:t.plans()){var row=plans.createSection(p.id());row.set("name",p.name());row.set("description",p.description());mix(row.createSection("response"),p.response());mix(row.createSection("recovery"),p.recovery());}
    }
    private static void mix(ConfigurationSection s,Mix mix){s.set("crop-loss-factor",mix.crops());s.set("output-loss-factor",mix.output());}
    static String encode(State state){
        var y=new YamlConfiguration();y.set("schema",1);var root=y.createSection("scenarios");for(var e:new TreeMap<>(state.entries()).entrySet()){
            var r=e.getValue();var row=root.createSection(e.getKey().toString());row.set("town",r.town().toString());row.set("town-name",r.townName());row.set("event",r.eventId());row.set("started",r.started());terms(row.createSection("terms"),r.terms());row.set("phase",r.phase().name());row.set("worked",r.worked());row.set("choice",r.choice());if(r.actor()!=null)row.set("actor",r.actor().toString());row.set("actor-name",r.actorName());row.set("chosen",r.chosen());row.set("ended",r.ended());row.set("outcome",r.outcome());
        }return y.saveToString();
    }
    static State decode(ConfigurationSection y){
        if(y.getKeys(false).isEmpty())return empty();SafeYaml.keys(y,"schema","scenarios");if(SafeYaml.integer(y,"schema")!=1)throw new IllegalArgumentException("Неизвестная схема сценариев");var entries=new HashMap<UUID,Scenario>();var root=section(y,"scenarios");for(String id:root.getKeys(false)){
            var row=section(root,id);var key=UUID.fromString(id);entries.put(key,new Scenario(key,UUID.fromString(SafeYaml.text(row,"town")),SafeYaml.text(row,"town-name"),SafeYaml.text(row,"event"),SafeYaml.integer(row,"started"),terms(section(row,"terms"),true),Phase.valueOf(SafeYaml.text(row,"phase")),SafeYaml.integer(row,"worked"),SafeYaml.stringValue(row,"choice",""),row.contains("actor")?UUID.fromString(SafeYaml.text(row,"actor")):null,SafeYaml.stringValue(row,"actor-name",""),SafeYaml.integer(row,"chosen"),SafeYaml.integer(row,"ended"),SafeYaml.stringValue(row,"outcome","")));
            if(entries.size()>MAX_HISTORY)throw new IllegalArgumentException("Журнал сценариев заполнен");
        }return new State(entries);
    }
}
