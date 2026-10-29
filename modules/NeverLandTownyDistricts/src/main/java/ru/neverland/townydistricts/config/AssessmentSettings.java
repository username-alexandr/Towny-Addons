package ru.neverland.townydistricts.config;

import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import ru.neverland.townydistricts.model.DistrictType;
import ru.neverland.townydistricts.model.DistrictAssessment.*;

public final class AssessmentSettings {
    private AssessmentSettings(){}
    public static Settings load(ConfigurationSection config){
        Object raw=config.get("assessment.service-range-cells",8);if(!(raw instanceof Integer n))throw new IllegalArgumentException("assessment.service-range-cells: требуется целое число");
        var needs=new EnumMap<DistrictType,List<Need>>(DistrictType.class);var section=config.getConfigurationSection("assessment.services");if(section==null){if(config.contains("assessment.services"))throw new IllegalArgumentException("assessment.services: требуется список групп служб");return defaults(n); }
        for(String type:section.getKeys(false)){DistrictType parsed=DistrictType.parse(type);var group=section.getConfigurationSection(type);if(group==null)throw new IllegalArgumentException("Нет списка служб района");var rows=new ArrayList<Need>();for(String id:group.getKeys(false)){var entry=group.getConfigurationSection(id);if(entry==null||!(entry.get("title") instanceof String title)||!entry.isList("buildings")||entry.getList("buildings").stream().anyMatch(v->!(v instanceof String)))throw new IllegalArgumentException("Неверная служба района: "+id);rows.add(new Need(id,title,new HashSet<>(entry.getStringList("buildings"))));}needs.put(parsed,rows);}
        return new Settings(n,needs);
    }
    public static Settings defaults(int range){
        var water=new Need("water","Водоснабжение",Set.of("aqueduct","water_tower","reservoir"));var medical=new Need("medical","Медицина",Set.of("infirmary"));var storage=new Need("storage","Склад",Set.of("warehouse","cargo_terminal"));var transport=new Need("transport","Транспортные службы",Set.of("roads","post_station","caravanserai"));
        return new Settings(range,Map.of(DistrictType.RESIDENTIAL,List.of(water,medical,new Need("market","Рынок",Set.of("market"))),DistrictType.INDUSTRIAL,List.of(storage,new Need("power","Источники энергии",Set.of("generator","power_station","water_wheel"))),DistrictType.AGRICULTURAL,List.of(new Need("irrigation","Орошение",Set.of("irrigation_station","reservoir","aqueduct")),storage),DistrictType.COMMERCIAL,List.of(storage,transport),DistrictType.MILITARY,List.of(new Need("supply","Снабжение армии",Set.of("arsenal","armory","warehouse")),medical),DistrictType.PORT,List.of(storage,transport),DistrictType.ADMINISTRATIVE,List.of(water,new Need("communications","Связь и документы",Set.of("post_station","archive")))));
    }
}
