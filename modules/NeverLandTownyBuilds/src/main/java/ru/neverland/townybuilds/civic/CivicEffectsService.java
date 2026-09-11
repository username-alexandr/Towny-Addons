package ru.neverland.townybuilds.civic;
import ru.neverland.localization.MaterialNameConfig;

import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.entity.Player;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.townybuilds.api.CivicBenefit;
import ru.neverland.townybuilds.api.TownShopTradeEvent;
import ru.neverland.townybuilds.api.TownyBuildsApi;
import ru.neverland.townybuilds.construction.CivicBlueprintGenerator;
import ru.neverland.townybuilds.data.DataStore;
import ru.neverland.townybuilds.data.TownData;
import ru.neverland.townybuilds.integration.TownyHook;
import ru.neverland.townybuilds.service.MessageService;
import ru.neverland.townybuilds.service.RussianItemNames;
import ru.neverland.townybuilds.util.ColorUtil;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Public building projections and effects, independent of menus and scheduled world work. */
public final class CivicEffectsService implements TownyBuildsApi {
    private final DataStore dataStore;private final TownyHook towny;private final JavaPlugin plugin;
    private final ru.neverland.townybuilds.construction.BuildingFootprints footprints=new ru.neverland.townybuilds.construction.BuildingFootprints();
    public CivicEffectsService(JavaPlugin plugin,TownyHook towny,DataStore dataStore){this.plugin=plugin;this.towny=towny;this.dataStore=dataStore;}
    @Override public java.util.Set<String> supportedProjects(){return new ru.neverland.townybuilds.construction.BuildingBlueprintGenerator().supportedProjects();}
    @Override
    public int projectLevel(UUID townId, String projectId) {
        return townId == null || projectId == null ? 0 : dataStore.town(townId).level(projectId.toLowerCase(Locale.ROOT));
    }

    @Override
    public int operationalLevel(UUID townId,String projectId){return townId==null||projectId==null?0:dataStore.town(townId).operationalLevel(projectId.toLowerCase(Locale.ROOT));}

    @Override
    public Map<String,ru.neverland.townybuilds.api.BuildingFootprint> buildingFootprints(UUID townId) {
        if(townId==null || towny.town(townId)==null)return Map.of();
        TownData data=dataStore.town(townId);
        Map<String,ru.neverland.townybuilds.api.BuildingFootprint> result=new HashMap<>();
        data.constructionSites().forEach((id,site)->footprints.footprint(site,data.level(id)).ifPresent(value->result.put(id,value)));
        return Map.copyOf(result);
    }

    @Override public Map<String,ru.neverland.townybuilds.api.BuildingWorkplace> workplaces(UUID townId){
        Map<String,ru.neverland.townybuilds.api.BuildingWorkplace> result=new HashMap<>();if(townId==null||towny.town(townId)==null)return Map.of();var data=dataStore.town(townId);
        buildingFootprints(townId).forEach((id,area)->{var site=data.constructionSites().get(id);if(site!=null)result.put(id,new ru.neverland.townybuilds.api.BuildingWorkplace(area.worldId(),area.minX(),area.minZ(),area.maxX(),area.maxZ(),site.originY(),area.completedLevel()));});return Map.copyOf(result);
    }
    private double benefitLevel(TownData data,String project) {
        return data.operationalLevel(project)*ru.neverland.integration.JobsAccess.multiplier(data.townId(),project,ru.neverland.integration.DistrictBonuses.multiplier(data.townId(),project));
    }

    @Override
    public double benefit(UUID townId, CivicBenefit benefit) {
        if (townId == null || benefit == null) return 0;
        TownData data = dataStore.town(townId);
        double value = switch (benefit) {
            case PUBLICATION_REACH -> 0.12 * benefitLevel(data, "printing_house") + 0.15 * benefitLevel(data, "crystal_palace");
            case FORESTRY_CAPACITY -> 0.15 * benefitLevel(data, "forestry") + 0.35 * benefitLevel(data, "world_tree");
            case CUSTOMS_EFFICIENCY -> 0.06 * benefitLevel(data, "customs") + 0.03 * benefitLevel(data, "trade_port")
                    + 0.20 * benefitLevel(data, "rhodes_colossus");
            case TRADE_CAPACITY -> 0.10 * benefitLevel(data, "trade_port") + 0.05 * benefitLevel(data, "merchant_guild")
                    + 0.20 * benefitLevel(data, "rhodes_colossus") + 0.25 * benefitLevel(data, "crystal_palace");
            case MINT_FEE_REDUCTION -> 0.04 * benefitLevel(data, "mint");
            case FRAUD_REDUCTION -> 0.08 * benefitLevel(data, "merchant_guild") + 0.15 * benefitLevel(data, "crystal_palace");
            case TRADE_REPUTATION -> 0.05 * benefitLevel(data, "merchant_guild") + 0.25 * benefitLevel(data, "crystal_palace");
            case MOUNT_SPEED -> 0.05 * benefitLevel(data, "stables");
            case FORTIFICATION -> 0.08 * benefitLevel(data, "fortress_wall") + 0.06 * benefitLevel(data, "city_moat")
                    + 0.08 * benefitLevel(data, "port_fort") + 0.12 * benefitLevel(data, "rhodes_colossus")
                    + 0.25 * benefitLevel(data, "terracotta_army");
            case RANGED_TRAINING -> 0.06 * benefitLevel(data, "archery_range") + 0.20 * benefitLevel(data, "terracotta_army");
            case POPULATION_ACCURACY -> 0.20 * benefitLevel(data, "census_bureau") + 0.20 * benefitLevel(data, "terracotta_army");
            case INSURANCE_COVERAGE -> 0.10 * benefitLevel(data, "insurance_chamber");
            case WATER_PRESSURE -> waterNetworkActive(townId)
                    ? 0.12 * benefitLevel(data, "pumping_station") + 0.30 * benefitLevel(data, "great_canal") : 0;
            case IRRIGATION_EFFICIENCY -> waterNetworkActive(townId)
                    ? 0.14 * benefitLevel(data, "irrigation_station") + 0.30 * benefitLevel(data, "great_canal") : 0;
            case RECYCLING_EFFICIENCY -> 0.12 * benefitLevel(data, "recycling_yard");
            case FLOOD_REDUCTION -> 0.12 * benefitLevel(data, "dam") + 0.03 * benefitLevel(data, "city_moat")
                    + 0.35 * benefitLevel(data, "great_canal");
        };
        return Math.max(0, Math.min(1, value));
    }

    @Override
    public boolean waterNetworkActive(UUID townId) {
        if (townId == null) return false;
        TownData data = dataStore.town(townId);
        return data.operationalLevel("water_tower") > 0 && data.operationalLevel("reservoir") > 0 && data.operationalLevel("pumping_station") > 0;
    }

    @Override
    public double insuranceReserve(UUID townId) {
        return townId == null ? 0 : dataStore.town(townId).insuranceReserve();
    }

    @Override
    public double consumeInsurance(UUID townId, double requestedAmount) {
        if (townId == null || !Double.isFinite(requestedAmount) || requestedAmount <= 0) return 0;
        TownData data = dataStore.town(townId);
        double allowed = requestedAmount * benefit(townId, CivicBenefit.INSURANCE_COVERAGE);
        double paid = Math.min(data.insuranceReserve(), allowed);
        if (paid > 0) {
            double previous=data.insuranceReserve();data.setInsuranceReserve(previous-paid);
            dataStore.markDirty();
            try{dataStore.saveOrThrow();}catch(java.io.IOException ex){data.setInsuranceReserve(previous);throw new java.io.UncheckedIOException(ex);}
        }
        return paid;
    }

    @Override
    public Optional<CivicArea> area(UUID townId, String projectId) {
        if (townId == null || projectId == null) return Optional.empty();
        return Optional.ofNullable(dataStore.town(townId).civicArea(projectId.toLowerCase(Locale.ROOT)));
    }

    @Override
    public Optional<CivicLine> line(UUID townId, String projectId) {
        if (townId == null || projectId == null) return Optional.empty();
        return Optional.ofNullable(dataStore.town(townId).civicLine(projectId.toLowerCase(Locale.ROOT)));
    }

}
