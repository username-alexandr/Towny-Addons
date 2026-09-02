package ru.neverland.mintespionage.service;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintespionage.model.OperationDefinition;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DefinitionRegistry {
    private final JavaPlugin plugin; private final Map<String,OperationDefinition> definitions=new LinkedHashMap<>();
    public DefinitionRegistry(JavaPlugin plugin){this.plugin=plugin;reload();}
    public void reload(){
        definitions.clear();YamlConfiguration yaml=YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(),"operations.yml"));
        ConfigurationSection root=yaml.getConfigurationSection("operations");if(root==null){plugin.getLogger().severe("operations.yml не содержит раздел operations.");return;}
        for(String rawId:root.getKeys(false)){
            String id=rawId.toLowerCase(Locale.ROOT);ConfigurationSection section=root.getConfigurationSection(rawId);if(section==null)continue;
            Material icon=Material.matchMaterial(section.getString("icon","SPYGLASS"));if(icon==null)icon=Material.SPYGLASS;
            OperationDefinition definition=new OperationDefinition(id,section.getString("name",id),icon,section.getString("itemsadder-icon",""),
                    section.getInt("slot",10),List.copyOf(section.getStringList("description")),Math.max(0,section.getDouble("cost",0)),
                    Math.max(1,section.getLong("duration-seconds",60))*1000L,Math.max(0,section.getLong("cooldown-seconds",1800))*1000L,
                    Math.max(1,section.getLong("report-lifetime-hours",72))*3600000L,
                    clamp(section.getDouble("success-chance",.7)),clamp(section.getDouble("detection-chance",.2)));
            definitions.put(id,definition);
        }
    }
    public OperationDefinition get(String id){return id==null?null:definitions.get(id.toLowerCase(Locale.ROOT));}
    public Collection<OperationDefinition> all(){return List.copyOf(definitions.values());}
    private double clamp(double value){return Math.max(0,Math.min(1,value));}
}
