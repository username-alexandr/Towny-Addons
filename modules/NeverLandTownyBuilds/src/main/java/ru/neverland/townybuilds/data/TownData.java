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
    public Map<UUID, ru.neverland.townybuilds.api.WarehouseMovement> warehouseMovements(){ synchronized (ru.neverland.core.SuiteState.lock()) {return Map.copyOf(warehouseMovements);} }
    public void putWarehouseMovement(ru.neverland.townybuilds.api.WarehouseMovement value){ synchronized (ru.neverland.core.SuiteState.lock()) {warehouseMovements.put(value.id(),value);} }
    public void removeWarehouseMovement(UUID id){ synchronized (ru.neverland.core.SuiteState.lock()) {warehouseMovements.remove(id);} }

    private final Map<UUID,ru.neverland.townybuilds.api.MunicipalReceipt> municipalReceipts=new HashMap<>();
    public Map<UUID,ru.neverland.townybuilds.api.MunicipalReceipt> municipalReceipts(){ synchronized (ru.neverland.core.SuiteState.lock()) {return Map.copyOf(municipalReceipts);} }
    public void putMunicipalReceipt(ru.neverland.townybuilds.api.MunicipalReceipt value){ synchronized (ru.neverland.core.SuiteState.lock()) {municipalReceipts.put(value.id(),value);} }
    public void removeMunicipalReceipt(UUID id){ synchronized (ru.neverland.core.SuiteState.lock()) {municipalReceipts.remove(id);} }

    private final Map<UUID,ru.neverland.townybuilds.api.MarketStock> marketStock=new HashMap<>();
    public Map<UUID,ru.neverland.townybuilds.api.MarketStock> marketStock(){ synchronized (ru.neverland.core.SuiteState.lock()) {return Map.copyOf(marketStock);} }
    public void putMarketStock(ru.neverland.townybuilds.api.MarketStock value){ synchronized (ru.neverland.core.SuiteState.lock()) {marketStock.put(value.id(),value);} }
    public void removeMarketStock(UUID id){ synchronized (ru.neverland.core.SuiteState.lock()) {marketStock.remove(id);} }

    private final Map<UUID,ru.neverland.townybuilds.api.TradeCargo> tradeCargo=new HashMap<>();
    public Map<UUID,ru.neverland.townybuilds.api.TradeCargo> tradeCargo(){ synchronized (ru.neverland.core.SuiteState.lock()) {return Map.copyOf(tradeCargo);} }
    public void putTradeCargo(ru.neverland.townybuilds.api.TradeCargo value){ synchronized (ru.neverland.core.SuiteState.lock()) {tradeCargo.put(value.id(),value);} }
    public void removeTradeCargo(UUID id){ synchronized (ru.neverland.core.SuiteState.lock()) {tradeCargo.remove(id);} }

    private final UUID townId;
    private final Map<UUID,ru.neverland.townybuilds.api.CargoShipment> shipments = new HashMap<>();
    public Map<UUID,ru.neverland.townybuilds.api.CargoShipment> shipments(){ synchronized (ru.neverland.core.SuiteState.lock()) {return Map.copyOf(shipments);} }
    public void putShipment(ru.neverland.townybuilds.api.CargoShipment value){ synchronized (ru.neverland.core.SuiteState.lock()) {shipments.put(value.id(),value);} }
    public void removeShipment(UUID id){ synchronized (ru.neverland.core.SuiteState.lock()) {shipments.remove(id);} }
    private final Map<String,ru.neverland.townybuilds.construction.PlacementDraft> placements=new HashMap<>();
    public Map<String,ru.neverland.townybuilds.construction.PlacementDraft> placements(){synchronized(ru.neverland.core.SuiteState.lock()){return Map.copyOf(placements);}}
    public void placement(String project,ru.neverland.townybuilds.construction.PlacementDraft draft){synchronized(ru.neverland.core.SuiteState.lock()){if(draft==null)placements.remove(project);else placements.put(project,draft);}}
    private final Map<String, Integer> levels = new HashMap<>();
    private final Map<String, ConstructionSite> constructionSites = new HashMap<>();
    private final Map<String, ru.neverland.townybuilds.construction.ConstructionReceipt> constructionReceipts = new HashMap<>();
    public Map<String, ru.neverland.townybuilds.construction.ConstructionReceipt> constructionReceipts() { synchronized (ru.neverland.core.SuiteState.lock()) { return Map.copyOf(constructionReceipts); } }
    public void receipt(String project, ru.neverland.townybuilds.construction.ConstructionReceipt receipt) { synchronized (ru.neverland.core.SuiteState.lock()) { if (receipt == null) constructionReceipts.remove(project); else constructionReceipts.put(project, receipt); } }
    public void removeConstructionSite(String project) { synchronized (ru.neverland.core.SuiteState.lock()) { constructionSites.remove(project); } }
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

    public UUID townId() { synchronized (ru.neverland.core.SuiteState.lock()) {
        return townId;
    } }

    public int level(String projectId) { synchronized (ru.neverland.core.SuiteState.lock()) {
        return levels.getOrDefault(projectId, 0);
    } }

    /** Operational effects only. Physical levels and the construction history stay intact. */
    public int operationalLevel(String projectId) { synchronized (ru.neverland.core.SuiteState.lock()) {
        return ru.neverland.integration.BuildingOperations.level(townId,projectId,level(projectId));
    } }

    public void setLevel(String projectId, int level) { synchronized (ru.neverland.core.SuiteState.lock()) {
        if (level <= 0) {
            levels.remove(projectId);
        } else {
            levels.put(projectId, level);
        }
    } }

    public Map<String, Integer> levels() { synchronized (ru.neverland.core.SuiteState.lock()) {
        return Map.copyOf(levels);
    } }

    public void loadLevels(Map<String, Integer> loaded) { synchronized (ru.neverland.core.SuiteState.lock()) {
        levels.clear();
        levels.putAll(loaded);
    } }

    public ConstructionSite constructionSite(String projectId) { synchronized (ru.neverland.core.SuiteState.lock()) {
        return constructionSites.get(projectId);
    } }

    public void setConstructionSite(ConstructionSite site) { synchronized (ru.neverland.core.SuiteState.lock()) {
        constructionSites.put(site.projectId(), site);
    } }

    public Map<String, ConstructionSite> constructionSites() { synchronized (ru.neverland.core.SuiteState.lock()) {
        return Map.copyOf(constructionSites);
    } }

    public void loadConstructionSites(Map<String, ConstructionSite> loaded) { synchronized (ru.neverland.core.SuiteState.lock()) {
        constructionSites.clear();
        constructionSites.putAll(loaded);
    } }

    public ResourceFund resourceFund(String projectId, int targetLevel) { synchronized (ru.neverland.core.SuiteState.lock()) {
        ResourceFund current = resourceFunds.get(projectId);
        if (current == null || current.targetLevel() != targetLevel) {
            current = new ResourceFund(targetLevel);
            resourceFunds.put(projectId, current);
        }
        return current;
    } }

    public ResourceFund existingResourceFund(String projectId) { synchronized (ru.neverland.core.SuiteState.lock()) {
        return resourceFunds.get(projectId);
    } }

    public void clearResourceFund(String projectId) { synchronized (ru.neverland.core.SuiteState.lock()) {
        resourceFunds.remove(projectId);
    } }

    public Map<String, ResourceFund> resourceFunds() { synchronized (ru.neverland.core.SuiteState.lock()) {
        return Map.copyOf(resourceFunds);
    } }

    public void loadResourceFunds(Map<String, ResourceFund> loaded) { synchronized (ru.neverland.core.SuiteState.lock()) {
        resourceFunds.clear();
        resourceFunds.putAll(loaded);
    } }

    public ItemStack[] storage() { synchronized (ru.neverland.core.SuiteState.lock()) {
        ItemStack[] copy = new ItemStack[storage.length];
        for (int index = 0; index < storage.length; index++) {
            copy[index] = storage[index] == null ? null : storage[index].clone();
        }
        return copy;
    } }

    public void setStorage(ItemStack[] contents, int configuredSize) { synchronized (ru.neverland.core.SuiteState.lock()) {
        storage = new ItemStack[configuredSize];
        int length = Math.min(contents.length, configuredSize);
        for (int index = 0; index < length; index++) {
            storage[index] = contents[index] == null ? null : contents[index].clone();
        }
    } }

    public CivicArea civicArea(String projectId) { synchronized (ru.neverland.core.SuiteState.lock()) {
        return civicAreas.get(projectId);
    } }

    public void setCivicArea(String projectId, CivicArea area) { synchronized (ru.neverland.core.SuiteState.lock()) {
        if (area == null) civicAreas.remove(projectId); else civicAreas.put(projectId, area);
    } }

    public Map<String, CivicArea> civicAreas() { synchronized (ru.neverland.core.SuiteState.lock()) {
        return Map.copyOf(civicAreas);
    } }

    public void loadCivicAreas(Map<String, CivicArea> loaded) { synchronized (ru.neverland.core.SuiteState.lock()) {
        civicAreas.clear();
        civicAreas.putAll(loaded);
    } }

    public CivicLine civicLine(String projectId) { synchronized (ru.neverland.core.SuiteState.lock()) {
        return civicLines.get(projectId);
    } }

    public void setCivicLine(String projectId, CivicLine line) { synchronized (ru.neverland.core.SuiteState.lock()) {
        if (line == null) civicLines.remove(projectId); else civicLines.put(projectId, line);
    } }

    public Map<String, CivicLine> civicLines() { synchronized (ru.neverland.core.SuiteState.lock()) {
        return Map.copyOf(civicLines);
    } }

    public void loadCivicLines(Map<String, CivicLine> loaded) { synchronized (ru.neverland.core.SuiteState.lock()) {
        civicLines.clear();
        civicLines.putAll(loaded);
    } }

    public int civicOccupiedSlots(String projectId) { synchronized (ru.neverland.core.SuiteState.lock()) {
        var items=civicInventories.get(projectId);if(items==null)return 0;
        for(int i=items.length-1;i>=0;i--)if(items[i]!=null&&!items[i].getType().isAir()&&items[i].getAmount()>0)return i+1;return 0;
    } }

    public ItemStack[] civicInventory(String projectId, int size) { synchronized (ru.neverland.core.SuiteState.lock()) {
        ItemStack[] source = civicInventories.get(projectId);
        ItemStack[] result = new ItemStack[size];
        if (source == null) return result;
        for (int index = 0; index < Math.min(source.length, size); index++) {
            result[index] = source[index] == null ? null : source[index].clone();
        }
        return result;
    } }

    public void setCivicInventory(String projectId, ItemStack[] contents) { synchronized (ru.neverland.core.SuiteState.lock()) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int index = 0; index < contents.length; index++) {
            copy[index] = contents[index] == null ? null : contents[index].clone();
        }
        civicInventories.put(projectId, copy);
    } }

    public Map<String, ItemStack[]> civicInventories() { synchronized (ru.neverland.core.SuiteState.lock()) {
        Map<String, ItemStack[]> copy = new HashMap<>();
        civicInventories.forEach((id, contents) -> copy.put(id, civicInventory(id, contents.length)));
        return Map.copyOf(copy);
    } }

    public void loadCivicInventories(Map<String, ItemStack[]> loaded) { synchronized (ru.neverland.core.SuiteState.lock()) {
        civicInventories.clear();
        loaded.forEach(this::setCivicInventory);
    } }

    public String shopStall() { synchronized (ru.neverland.core.SuiteState.lock()) { return shopStall; } }
    public void setShopStall(String value) { synchronized (ru.neverland.core.SuiteState.lock()) { shopStall = value == null ? "" : value; } }

    public Map<String, Double> shopPrices() { synchronized (ru.neverland.core.SuiteState.lock()) { return Map.copyOf(shopPrices); } }
    public double shopPrice(String material) { synchronized (ru.neverland.core.SuiteState.lock()) { return shopPrices.getOrDefault(ru.neverland.localization.MaterialLabels.canonicalKey(material), 0.0); } }
    public void setShopPrice(String material, double price) { synchronized (ru.neverland.core.SuiteState.lock()) {
        material = ru.neverland.localization.MaterialLabels.canonicalKey(material);
        if (price <= 0) shopPrices.remove(material); else shopPrices.put(material, price);
    } }
    public void loadShopPrices(Map<String, Double> loaded) { synchronized (ru.neverland.core.SuiteState.lock()) {
        shopPrices.clear();
        // Prefer an explicit modern key if both old and new names were saved.
        loaded.entrySet().stream().filter(e -> !e.getKey().equals(ru.neverland.localization.MaterialLabels.canonicalKey(e.getKey())))
                .forEach(e -> setShopPrice(e.getKey(), e.getValue()));
        loaded.entrySet().stream().filter(e -> e.getKey().equals(ru.neverland.localization.MaterialLabels.canonicalKey(e.getKey())))
                .forEach(e -> setShopPrice(e.getKey(), e.getValue()));
    } }

    public double insuranceReserve() { synchronized (ru.neverland.core.SuiteState.lock()) { return insuranceReserve; } }
    public void setInsuranceReserve(double value) { synchronized (ru.neverland.core.SuiteState.lock()) { insuranceReserve = Math.max(0, value); } }

    public String bulletin() { synchronized (ru.neverland.core.SuiteState.lock()) { return bulletin; } }
    public void setBulletin(String value) { synchronized (ru.neverland.core.SuiteState.lock()) { bulletin = value == null ? "" : value; } }
}
