package ru.neverland.townypolicies.config;
import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import ru.neverland.townypolicies.model.PolicyPresets.*;
public record PresetSettings(boolean enabled,Map<String,Preset> presets,List<Conflict> conflicts) {
    public PresetSettings(Map<String,Preset> presets,List<Conflict> conflicts){this(true,presets,conflicts);}
    public static PresetSettings disabled(){return new PresetSettings(false,Map.of(),List.of());}
    public PresetSettings{presets=Collections.unmodifiableMap(new LinkedHashMap<>(presets));conflicts=List.copyOf(conflicts);if(enabled&&presets.isEmpty()||presets.size()>32||conflicts.size()>64)throw new IllegalArgumentException("Наборы: 1..32, конфликты: до 64");}
    public static PresetSettings load(ConfigurationSection data,PoliciesSettings settings){
        if(!(data.get("enabled",true) instanceof Boolean enabled))throw new IllegalArgumentException("enabled: true/false");if(!enabled)return disabled();
        var root=data.getConfigurationSection("presets");if(root==null)throw new IllegalArgumentException("Отсутствует presets");var presets=new LinkedHashMap<String,Preset>();
        for(String id:root.getKeys(false)){var row=root.getConfigurationSection(id);if(row==null||row.getConfigurationSection("modes")==null)throw new IllegalArgumentException("Неверный набор "+id);var modes=new LinkedHashMap<String,String>();var configured=row.getConfigurationSection("modes");for(String group:configured.getKeys(false)){if(!configured.isString(group))throw new IllegalArgumentException("Режим набора должен быть строкой");modes.put(group,configured.getString(group));}validate(modes,settings);presets.put(id,new Preset(id,row.getString("name"),row.getString("icon"),row.getString("description"),modes));}
        var conflicts=new ArrayList<Conflict>();if(!(data.get("conflicts") instanceof List<?> rows))throw new IllegalArgumentException("conflicts: список правил");var ids=new HashSet<String>();for(var raw:rows){if(!(raw instanceof Map<?,?> row)||!(row.get("modes") instanceof Map<?,?> values)||!(row.get("id") instanceof String id)||!(row.get("reason") instanceof String reason)||!ids.add(id))throw new IllegalArgumentException("Неверное или повторное правило совместимости");var modes=new LinkedHashMap<String,String>();for(var entry:values.entrySet()){if(!(entry.getKey() instanceof String group)||!(entry.getValue() instanceof String mode))throw new IllegalArgumentException("Конфликт: строки политик и режимов");modes.put(group,mode);}validate(modes,settings);conflicts.add(new Conflict(id,modes,reason));}
        return new PresetSettings(presets,conflicts);
    }
    private static void validate(Map<String,String> modes,PoliciesSettings settings){for(var entry:modes.entrySet()){var group=settings.groups().get(entry.getKey());if(group==null||!group.options().containsKey(entry.getValue()))throw new IllegalArgumentException("Набор или правило ссылается на неизвестную политику/режим: "+entry.getKey());}}
}
