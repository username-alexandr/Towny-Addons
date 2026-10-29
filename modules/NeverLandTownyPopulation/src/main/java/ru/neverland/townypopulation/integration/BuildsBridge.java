package ru.neverland.townypopulation.integration;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import java.lang.reflect.Method;
import java.util.*;

/** Uses Builds' public service, never its private datastore or construction files. */
public final class BuildsBridge {
    public Map<String,Integer> levels(UUID town, Set<String> projects) throws ReflectiveOperationException { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        // A disabled supplier must not look like a real loss of housing or food capacity.
        ready("Электроснабжение",ru.neverland.core.ApiServices.connect("NeverLandTownyPower","ru.neverland.townypower.api.TownyPowerApi",1,"powered"));
        ready("Содержание зданий",ru.neverland.core.ApiServices.connect("NeverLandTownyUpkeep","ru.neverland.townyupkeep.api.TownyUpkeepApi",1,"active"));
        ready("Специализация",ru.neverland.core.ApiServices.connect("NeverLandTownySpecialization","ru.neverland.townyspecialization.api.TownySpecializationApi",1,"canUseBuilding"));
        ready("Районы",ru.neverland.core.ApiServices.connect("NeverLandTownyDistricts","ru.neverland.townydistricts.api.TownyDistrictsApi",1,"multiplier"));
        ready("Политики",ru.neverland.core.ApiServices.connect("NeverLandTownyPolicies","ru.neverland.townypolicies.api.TownyPoliciesApi",1,"effect"));
        var api=ru.neverland.core.ApiServices.require("NeverLandTownyBuilds","ru.neverland.townybuilds.api.TownyBuildsApi","operationalLevel");
        Map<String,Integer> levels = new HashMap<>();
        for (String id : projects) levels.put(id,Math.max(0,((Number)api.invoke("operationalLevel",new Class<?>[]{UUID.class,String.class},town,id)).intValue()));
        return levels;
    } }
    private static void ready(String title,ru.neverland.core.ApiServices.Connection connection){if(connection.state()!=ru.neverland.core.ApiServices.State.NOT_INSTALLED&&!connection.ready())throw new IllegalStateException(title+" временно недоступно; подтверждённые жители сохранены");}
}
