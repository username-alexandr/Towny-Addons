package ru.neverland.townybuilds.data;

import org.bukkit.inventory.ItemStack;
import ru.neverland.townybuilds.civic.CivicArea;
import ru.neverland.townybuilds.civic.CivicLine;
import ru.neverland.townybuilds.construction.ConstructionSite;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TownData {
    private final Map<UUID, ru.neverland.townybuilds.api.WarehouseMovement> warehouseMovements = new HashMap<>();
    public Map<UUID, ru.neverland.townybuilds.api.WarehouseMovement> warehouseMovements(){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {return Map.copyOf(warehouseMovements);} }
    public void putWarehouseMovement(ru.neverland.townybuilds.api.WarehouseMovement value){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {warehouseMovements.put(value.id(),value);} }
    public void removeWarehouseMovement(UUID id){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {warehouseMovements.remove(id);} }

    private final Map<UUID,ru.neverland.townybuilds.api.MunicipalReceipt> municipalReceipts=new HashMap<>();
    public Map<UUID,ru.neverland.townybuilds.api.MunicipalReceipt> municipalReceipts(){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {return Map.copyOf(municipalReceipts);} }
    public void putMunicipalReceipt(ru.neverland.townybuilds.api.MunicipalReceipt value){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {municipalReceipts.put(value.id(),value);} }
    public void removeMunicipalReceipt(UUID id){ try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {municipalReceipts.remove(id);} }

    private final Map<UUID,ru.neverland.townybuilds.api.MarketStock> marketStock=new HashMap<>();
    public Map<UUID,ru.neverland.townybuilds.api.MarketStock> marketStock(){ try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {return Map.copyOf(marketStock);} }
    public void putMarketStock(ru.neverland.townybuilds.api.MarketStock value){ try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {marketStock.put(value.id(),value);} }
    public void removeMarketStock(UUID id){ try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) {marketStock.remove(id);} }

    private final Map<UUID,ru.neverland.townybuilds.api.TradeCargo> tradeCargo=new HashMap<>();
    public Map<UUID,ru.neverland.townybuilds.api.TradeCargo> tradeCargo(){ try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) {return Map.copyOf(tradeCargo);} }
    public void putTradeCargo(ru.neverland.townybuilds.api.TradeCargo value){ try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) {tradeCargo.put(value.id(),value);} }
    public void removeTradeCargo(UUID id){ try (var suiteGuard12 = ru.neverland.core.SuiteState.guard()) {tradeCargo.remove(id);} }

    private final UUID townId;
    private final Map<UUID,ru.neverland.townybuilds.api.CargoShipment> shipments = new HashMap<>();
    public Map<UUID,ru.neverland.townybuilds.api.CargoShipment> shipments(){ try (var suiteGuard13 = ru.neverland.core.SuiteState.guard()) {return Map.copyOf(shipments);} }
    public void putShipment(ru.neverland.townybuilds.api.CargoShipment value){ try (var suiteGuard14 = ru.neverland.core.SuiteState.guard()) {shipments.put(value.id(),value);} }
    public void removeShipment(UUID id){ try (var suiteGuard15 = ru.neverland.core.SuiteState.guard()) {shipments.remove(id);} }
    private final Map<String,ru.neverland.townybuilds.construction.PlacementDraft> placements=new HashMap<>();
    public Map<String,ru.neverland.townybuilds.construction.PlacementDraft> placements(){try (var suiteGuard16 = ru.neverland.core.SuiteState.guard()){return Map.copyOf(placements);}}
    public void placement(String project,ru.neverland.townybuilds.construction.PlacementDraft draft){try (var suiteGuard17 = ru.neverland.core.SuiteState.guard()){if(draft==null)placements.remove(project);else placements.put(project,draft);}}
    private String buildingStyle="medieval";
    private boolean styleChosen;
    private boolean armyEstablished;
    /** A completed army permanently ends this town's pre-army raid protection. */
    public boolean armyEstablished(){try(var g=ru.neverland.core.SuiteState.guard()){return armyEstablished;}}
    public void loadArmyEstablished(boolean value){try(var g=ru.neverland.core.SuiteState.guard()){armyEstablished|=value;}}
    public String buildingStyle(){try(var g=ru.neverland.core.SuiteState.guard()){return buildingStyle;}}
    public boolean styleChosen(){try(var g=ru.neverland.core.SuiteState.guard()){return styleChosen;}}
    public void buildingStyle(String id,boolean chosen){try(var g=ru.neverland.core.SuiteState.guard()){buildingStyle=ru.neverland.townybuilds.construction.BuildingStyle.parse(id).id();styleChosen=chosen;}}
    private final Map<String, Integer> levels = new HashMap<>();
    private final Map<String, ConstructionSite> constructionSites = new HashMap<>();
    private final Map<String, ru.neverland.townybuilds.construction.ConstructionReceipt> constructionReceipts = new HashMap<>();
    public Map<String, ru.neverland.townybuilds.construction.ConstructionReceipt> constructionReceipts() { try (var suiteGuard18 = ru.neverland.core.SuiteState.guard()) { return Map.copyOf(constructionReceipts); } }
    public void receipt(String project, ru.neverland.townybuilds.construction.ConstructionReceipt receipt) { try (var suiteGuard19 = ru.neverland.core.SuiteState.guard()) { if (receipt == null) constructionReceipts.remove(project); else constructionReceipts.put(project, receipt); } }
    public void removeConstructionSite(String project) { try (var suiteGuard20 = ru.neverland.core.SuiteState.guard()) { constructionSites.remove(project); } }
    private final Map<String, ResourceFund> resourceFunds = new HashMap<>();
    private final Map<String, CivicArea> civicAreas = new HashMap<>();
    private final Map<String, CivicLine> civicLines = new HashMap<>();
    private final Map<String, ItemStack[]> civicInventories = new HashMap<>();
    private final Map<String, Double> shopPrices = new HashMap<>();
    private ItemStack[] storage;
    private String shopStall = "";
    private double insuranceReserve;
    private String bulletin = "";

    public TownData(UUID townId, int storageSize) {
        this.townId = townId;
        this.storage = new ItemStack[storageSize];
    }

    public UUID townId() { try (var suiteGuard21 = ru.neverland.core.SuiteState.guard()) {
        return townId;
    } }

    public int level(String projectId) { try (var suiteGuard22 = ru.neverland.core.SuiteState.guard()) {
        return levels.getOrDefault(projectId, 0);
    } }

    /** Operational effects only. Physical levels and the construction history stay intact. */
    public int operationalLevel(String projectId) { try (var suiteGuard23 = ru.neverland.core.SuiteState.guard()) {
        return ru.neverland.integration.BuildingOperations.level(townId,projectId,level(projectId));
    } }

    public void setLevel(String projectId, int level) { try (var suiteGuard24 = ru.neverland.core.SuiteState.guard()) {
        if("army".equals(projectId)&&level>0)armyEstablished=true;
        if (level <= 0) {
            levels.remove(projectId);
        } else {
            levels.put(projectId, level);
        }
    } }

    public Map<String, Integer> levels() { try (var suiteGuard25 = ru.neverland.core.SuiteState.guard()) {
        return Map.copyOf(levels);
    } }

    public void loadLevels(Map<String, Integer> loaded) { try (var suiteGuard26 = ru.neverland.core.SuiteState.guard()) {
        levels.clear();
        levels.putAll(loaded);
        if(level("army")>0)armyEstablished=true;
    } }

    public ConstructionSite constructionSite(String projectId) { try (var suiteGuard27 = ru.neverland.core.SuiteState.guard()) {
        return constructionSites.get(projectId);
    } }

    public void setConstructionSite(ConstructionSite site) { try (var suiteGuard28 = ru.neverland.core.SuiteState.guard()) {
        constructionSites.put(site.projectId(), site);
        if("army".equals(site.projectId())&&site.completedStage()>0)armyEstablished=true;
    } }

    public Map<String, ConstructionSite> constructionSites() { try (var suiteGuard29 = ru.neverland.core.SuiteState.guard()) {
        return Map.copyOf(constructionSites);
    } }

    public void loadConstructionSites(Map<String, ConstructionSite> loaded) { try (var suiteGuard30 = ru.neverland.core.SuiteState.guard()) {
        constructionSites.clear();
        constructionSites.putAll(loaded);
        var army=constructionSites.get("army");if(army!=null&&army.completedStage()>0)armyEstablished=true;
    } }

    public ResourceFund resourceFund(String projectId, int targetLevel) { try (var suiteGuard31 = ru.neverland.core.SuiteState.guard()) {
        ResourceFund current = resourceFunds.get(projectId);
        if (current == null || current.targetLevel() != targetLevel) {
            current = new ResourceFund(targetLevel);
            resourceFunds.put(projectId, current);
        }
        return current;
    } }

    public ResourceFund existingResourceFund(String projectId) { try (var suiteGuard32 = ru.neverland.core.SuiteState.guard()) {
        return resourceFunds.get(projectId);
    } }

    public void clearResourceFund(String projectId) { try (var suiteGuard33 = ru.neverland.core.SuiteState.guard()) {
        resourceFunds.remove(projectId);
    } }

    public Map<String, ResourceFund> resourceFunds() { try (var suiteGuard34 = ru.neverland.core.SuiteState.guard()) {
        return Map.copyOf(resourceFunds);
    } }

    public void loadResourceFunds(Map<String, ResourceFund> loaded) { try (var suiteGuard35 = ru.neverland.core.SuiteState.guard()) {
        resourceFunds.clear();
        resourceFunds.putAll(loaded);
    } }

    public ItemStack[] storage() { try (var suiteGuard36 = ru.neverland.core.SuiteState.guard()) {
        ItemStack[] copy = new ItemStack[storage.length];
        for (int index = 0; index < storage.length; index++) {
            copy[index] = storage[index] == null ? null : storage[index].clone();
        }
        return copy;
    } }

    public void setStorage(ItemStack[] contents, int configuredSize) { try (var suiteGuard37 = ru.neverland.core.SuiteState.guard()) {
        storage = new ItemStack[configuredSize];
        int length = Math.min(contents.length, configuredSize);
        for (int index = 0; index < length; index++) {
            storage[index] = contents[index] == null ? null : contents[index].clone();
        }
    } }

    public CivicArea civicArea(String projectId) { try (var suiteGuard38 = ru.neverland.core.SuiteState.guard()) {
        return civicAreas.get(projectId);
    } }

    public void setCivicArea(String projectId, CivicArea area) { try (var suiteGuard39 = ru.neverland.core.SuiteState.guard()) {
        if (area == null) civicAreas.remove(projectId); else civicAreas.put(projectId, area);
    } }

    public Map<String, CivicArea> civicAreas() { try (var suiteGuard40 = ru.neverland.core.SuiteState.guard()) {
        return Map.copyOf(civicAreas);
    } }

    public void loadCivicAreas(Map<String, CivicArea> loaded) { try (var suiteGuard41 = ru.neverland.core.SuiteState.guard()) {
        civicAreas.clear();
        civicAreas.putAll(loaded);
    } }

    public CivicLine civicLine(String projectId) { try (var suiteGuard42 = ru.neverland.core.SuiteState.guard()) {
        return civicLines.get(projectId);
    } }

    public void setCivicLine(String projectId, CivicLine line) { try (var suiteGuard43 = ru.neverland.core.SuiteState.guard()) {
        if (line == null) civicLines.remove(projectId); else civicLines.put(projectId, line);
    } }

    public Map<String, CivicLine> civicLines() { try (var suiteGuard44 = ru.neverland.core.SuiteState.guard()) {
        return Map.copyOf(civicLines);
    } }

    public void loadCivicLines(Map<String, CivicLine> loaded) { try (var suiteGuard45 = ru.neverland.core.SuiteState.guard()) {
        civicLines.clear();
        civicLines.putAll(loaded);
    } }

    public int civicOccupiedSlots(String projectId) { try (var suiteGuard46 = ru.neverland.core.SuiteState.guard()) {
        var items=civicInventories.get(projectId);if(items==null)return 0;
        for(int i=items.length-1;i>=0;i--)if(items[i]!=null&&!items[i].getType().isAir()&&items[i].getAmount()>0)return i+1;return 0;
    } }

    public ItemStack[] civicInventory(String projectId, int size) { try (var suiteGuard47 = ru.neverland.core.SuiteState.guard()) {
        ItemStack[] source = civicInventories.get(projectId);
        ItemStack[] result = new ItemStack[size];
        if (source == null) return result;
        for (int index = 0; index < Math.min(source.length, size); index++) {
            result[index] = source[index] == null ? null : source[index].clone();
        }
        return result;
    } }

    public void setCivicInventory(String projectId, ItemStack[] contents) { try (var suiteGuard48 = ru.neverland.core.SuiteState.guard()) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int index = 0; index < contents.length; index++) {
            copy[index] = contents[index] == null ? null : contents[index].clone();
        }
        civicInventories.put(projectId, copy);
    } }

    public Map<String, ItemStack[]> civicInventories() { try (var suiteGuard49 = ru.neverland.core.SuiteState.guard()) {
        Map<String, ItemStack[]> copy = new HashMap<>();
        civicInventories.forEach((id, contents) -> copy.put(id, civicInventory(id, contents.length)));
        return Map.copyOf(copy);
    } }

    public void loadCivicInventories(Map<String, ItemStack[]> loaded) { try (var suiteGuard50 = ru.neverland.core.SuiteState.guard()) {
        civicInventories.clear();
        loaded.forEach(this::setCivicInventory);
    } }

    public String shopStall() { try (var suiteGuard51 = ru.neverland.core.SuiteState.guard()) { return shopStall; } }
    public void setShopStall(String value) { try (var suiteGuard52 = ru.neverland.core.SuiteState.guard()) { shopStall = value == null ? "" : value; } }

    public Map<String, Double> shopPrices() { try (var suiteGuard53 = ru.neverland.core.SuiteState.guard()) { return Map.copyOf(shopPrices); } }
    public double shopPrice(String material) { try (var suiteGuard54 = ru.neverland.core.SuiteState.guard()) { return shopPrices.getOrDefault(ru.neverland.localization.MaterialLabels.canonicalKey(material), 0.0); } }
    public void setShopPrice(String material, double price) { try (var suiteGuard55 = ru.neverland.core.SuiteState.guard()) {
        material = ru.neverland.localization.MaterialLabels.canonicalKey(material);
        if (price <= 0) shopPrices.remove(material); else shopPrices.put(material, price);
    } }
    public void loadShopPrices(Map<String, Double> loaded) { try (var suiteGuard56 = ru.neverland.core.SuiteState.guard()) {
        shopPrices.clear();
        // Prefer an explicit modern key if both old and new names were saved.
        loaded.entrySet().stream().filter(e -> !e.getKey().equals(ru.neverland.localization.MaterialLabels.canonicalKey(e.getKey())))
                .forEach(e -> setShopPrice(e.getKey(), e.getValue()));
        loaded.entrySet().stream().filter(e -> e.getKey().equals(ru.neverland.localization.MaterialLabels.canonicalKey(e.getKey())))
                .forEach(e -> setShopPrice(e.getKey(), e.getValue()));
    } }

    public double insuranceReserve() { try (var suiteGuard57 = ru.neverland.core.SuiteState.guard()) { return insuranceReserve; } }
    public void setInsuranceReserve(double value) { try (var suiteGuard58 = ru.neverland.core.SuiteState.guard()) { insuranceReserve = Math.max(0, value); } }

    public String bulletin() { try (var suiteGuard59 = ru.neverland.core.SuiteState.guard()) { return bulletin; } }
    public void setBulletin(String value) { try (var suiteGuard60 = ru.neverland.core.SuiteState.guard()) { bulletin = value == null ? "" : value; } }
}
