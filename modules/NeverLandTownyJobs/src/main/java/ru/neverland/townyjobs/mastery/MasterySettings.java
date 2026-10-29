package ru.neverland.townyjobs.mastery;
import java.util.*;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import ru.neverland.townyjobs.*;
import static ru.neverland.townyjobs.mastery.MasteryModel.*;
public record MasterySettings(boolean enabled,Map<String,Definition> definitions) {
    public MasterySettings{definitions=Map.copyOf(definitions);if(definitions.isEmpty()||definitions.size()>64)throw new IllegalArgumentException("Нужно 1..64 мастерских направления");}
    public static MasterySettings load(ConfigurationSection config,JobsSettings jobs){
        var allowed=new EnumMap<Profession,Set<String>>(Profession.class);jobs.profiles().forEach((role,profile)->allowed.put(role,profile.buildings()));var result=parse(config,allowed);
        for(var d:result.definitions().values()){var icon=Material.matchMaterial(d.icon());if(icon==null||!icon.isItem()||icon.isAir())throw new IllegalArgumentException("Иконка направления должна быть предметом: "+d.id());}return result;
    }
    /** Structural parser is independent of a running Minecraft item registry; load additionally checks that registry. */
    public static MasterySettings parse(ConfigurationSection config,Map<Profession,Set<String>> allowed){
        if(!config.isBoolean("enabled"))throw new IllegalArgumentException("mastery.enabled: true/false");var root=config.getConfigurationSection("specialties");if(root==null)throw new IllegalArgumentException("Отсутствуют направления мастерства");var result=new LinkedHashMap<String,Definition>();
        for(String id:root.getKeys(false)){
            var row=root.getConfigurationSection(id);if(row==null)throw new IllegalArgumentException("Неверное направление "+id);var profession=Profession.parse(row.getString("profession",""));
            var steps=new ArrayList<Step>();if(!(row.get("steps") instanceof List<?> configured))throw new IllegalArgumentException("Нужен список этапов направления "+id);for(var value:configured){if(!(value instanceof Map<?,?> raw)||!(raw.get("text") instanceof String text))throw new IllegalArgumentException("Неверное описание этапа "+id);var step=new Step(Action.valueOf(String.valueOf(raw.get("action"))),String.valueOf(raw.get("target")),Math.toIntExact(JobsSettings.integer(raw.get("amount"))),text);if(step.action()==Action.HUNT){if(!EntityType.valueOf(step.target()).isAlive())throw new IllegalArgumentException("Нужен живой моб");}else if(Material.matchMaterial(step.target())==null)throw new IllegalArgumentException("Неизвестный предмет или урожай "+step.target());steps.add(step);}
            if(!(row.get("bonus",.02) instanceof Number))throw new IllegalArgumentException("Бонус направления должен быть числом");
            var definition=new Definition(id,profession,row.getString("title"),row.getString("icon"),Math.toIntExact(JobsSettings.integer(row.get("minimum-level",2))),row.getDouble("bonus",.02),Set.copyOf(row.getStringList("buildings")),steps);
            var icon=Material.matchMaterial(definition.icon());if(icon==null||!allowed.getOrDefault(profession,Set.of()).containsAll(definition.buildings()))throw new IllegalArgumentException("Неверная иконка или здания направления "+id);result.put(id,definition);
        }return new MasterySettings(config.getBoolean("enabled"),result);
    }
}
