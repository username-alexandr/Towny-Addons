package ru.neverland.townybuilds.data;

import org.bukkit.inventory.ItemStack;
import ru.neverland.townybuilds.civic.CivicArea;
import ru.neverland.townybuilds.civic.CivicLine;
import ru.neverland.townybuilds.construction.ConstructionSite;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TownData {
    private final Map<UUID,ru.neverland.townybuilds.api.MarketStock> marketStock=new HashMap<>();
    public Map<UUID,ru.neverland.townybuilds.api.MarketStock> marketStock(){return Map.copyOf(marketStock);}
    public void putMarketStock(ru.neverland.townybuilds.api.MarketStock value){marketStock.put(value.id(),value);}
    public void removeMarketStock(UUID id){marketStock.remove(id);}

    private final Map<UUID,ru.neverland.townybuilds.api.TradeCargo> tradeCargo=new HashMap<>();
    public Map<UUID,ru.neverland.townybuilds.api.TradeCargo> tradeCargo(){return Map.copyOf(tradeCargo);}
    public void putTradeCargo(ru.neverland.townybuilds.api.TradeCargo value){tradeCargo.put(value.id(),value);}
    public void removeTradeCargo(UUID id){tradeCargo.remove(id);}

    private final UUID townId;
    private final Map<UUID,ru.neverland.townybuilds.api.CargoShipment> shipments = new HashMap<>();
    public Map<UUID,ru.neverland.townybuilds.api.CargoShipment> shipments(){return Map.copyOf(shipments);}
    public void putShipment(ru.neverland.townybuilds.api.CargoShipment value){shipments.put(value.id(),value);}
    public void removeShipment(UUID id){shipments.remove(id);}
    private final Map<String, Integer> levels = new HashMap<>();
    private final Map<String, ConstructionSite> constructionSites = new HashMap<>();
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

    public UUID townId() {
        return townId;
    }

    public int level(String projectId) {
        return levels.getOrDefault(projectId, 0);
    }

    /** Operational effects only. Physical levels and the construction history stay intact. */
    public int operationalLevel(String projectId) {
        return ru.neverland.integration.BuildingOperations.level(townId,projectId,level(projectId));
    }

    public void setLevel(String projectId, int level) {
        if (level <= 0) {
            levels.remove(projectId);
        } else {
            levels.put(projectId, level);
        }
    }

    public Map<String, Integer> levels() {
        return Map.copyOf(levels);
    }

    public void loadLevels(Map<String, Integer> loaded) {
        levels.clear();
        levels.putAll(loaded);
    }

    public ConstructionSite constructionSite(String projectId) {
        return constructionSites.get(projectId);
    }

    public void setConstructionSite(ConstructionSite site) {
        constructionSites.put(site.projectId(), site);
    }

    public Map<String, ConstructionSite> constructionSites() {
        return Map.copyOf(constructionSites);
    }

    public void loadConstructionSites(Map<String, ConstructionSite> loaded) {
        constructionSites.clear();
        constructionSites.putAll(loaded);
    }

    public ResourceFund resourceFund(String projectId, int targetLevel) {
        ResourceFund current = resourceFunds.get(projectId);
        if (current == null || current.targetLevel() != targetLevel) {
            current = new ResourceFund(targetLevel);
            resourceFunds.put(projectId, current);
        }
        return current;
    }

    public ResourceFund existingResourceFund(String projectId) {
        return resourceFunds.get(projectId);
    }

    public void clearResourceFund(String projectId) {
        resourceFunds.remove(projectId);
    }

    public Map<String, ResourceFund> resourceFunds() {
        return Map.copyOf(resourceFunds);
    }

    public void loadResourceFunds(Map<String, ResourceFund> loaded) {
        resourceFunds.clear();
        resourceFunds.putAll(loaded);
    }

    public ItemStack[] storage() {
        ItemStack[] copy = new ItemStack[storage.length];
        for (int index = 0; index < storage.length; index++) {
            copy[index] = storage[index] == null ? null : storage[index].clone();
        }
        return copy;
    }

    public void setStorage(ItemStack[] contents, int configuredSize) {
        storage = new ItemStack[configuredSize];
        int length = Math.min(contents.length, configuredSize);
        for (int index = 0; index < length; index++) {
            storage[index] = contents[index] == null ? null : contents[index].clone();
        }
    }

    public CivicArea civicArea(String projectId) {
        return civicAreas.get(projectId);
    }

    public void setCivicArea(String projectId, CivicArea area) {
        if (area == null) civicAreas.remove(projectId); else civicAreas.put(projectId, area);
    }

    public Map<String, CivicArea> civicAreas() {
        return Map.copyOf(civicAreas);
    }

    public void loadCivicAreas(Map<String, CivicArea> loaded) {
        civicAreas.clear();
        civicAreas.putAll(loaded);
    }

    public CivicLine civicLine(String projectId) {
        return civicLines.get(projectId);
    }

    public void setCivicLine(String projectId, CivicLine line) {
        if (line == null) civicLines.remove(projectId); else civicLines.put(projectId, line);
    }

    public Map<String, CivicLine> civicLines() {
        return Map.copyOf(civicLines);
    }

    public void loadCivicLines(Map<String, CivicLine> loaded) {
        civicLines.clear();
        civicLines.putAll(loaded);
    }

    public int civicOccupiedSlots(String projectId) {
        var items=civicInventories.get(projectId);if(items==null)return 0;
        for(int i=items.length-1;i>=0;i--)if(items[i]!=null&&!items[i].getType().isAir()&&items[i].getAmount()>0)return i+1;return 0;
    }

    public ItemStack[] civicInventory(String projectId, int size) {
        ItemStack[] source = civicInventories.get(projectId);
        ItemStack[] result = new ItemStack[size];
        if (source == null) return result;
        for (int index = 0; index < Math.min(source.length, size); index++) {
            result[index] = source[index] == null ? null : source[index].clone();
        }
        return result;
    }

    public void setCivicInventory(String projectId, ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int index = 0; index < contents.length; index++) {
            copy[index] = contents[index] == null ? null : contents[index].clone();
        }
        civicInventories.put(projectId, copy);
    }

    public Map<String, ItemStack[]> civicInventories() {
        Map<String, ItemStack[]> copy = new HashMap<>();
        civicInventories.forEach((id, contents) -> copy.put(id, civicInventory(id, contents.length)));
        return Map.copyOf(copy);
    }

    public void loadCivicInventories(Map<String, ItemStack[]> loaded) {
        civicInventories.clear();
        loaded.forEach(this::setCivicInventory);
    }

    public String shopStall() { return shopStall; }
    public void setShopStall(String value) { shopStall = value == null ? "" : value; }

    public Map<String, Double> shopPrices() { return Map.copyOf(shopPrices); }
    public double shopPrice(String material) { return shopPrices.getOrDefault(ru.neverland.localization.MaterialLabels.canonicalKey(material), 0.0); }
    public void setShopPrice(String material, double price) {
        material = ru.neverland.localization.MaterialLabels.canonicalKey(material);
        if (price <= 0) shopPrices.remove(material); else shopPrices.put(material, price);
    }
    public void loadShopPrices(Map<String, Double> loaded) {
        shopPrices.clear();
        // Prefer an explicit modern key if both old and new names were saved.
        loaded.entrySet().stream().filter(e -> !e.getKey().equals(ru.neverland.localization.MaterialLabels.canonicalKey(e.getKey())))
                .forEach(e -> setShopPrice(e.getKey(), e.getValue()));
        loaded.entrySet().stream().filter(e -> e.getKey().equals(ru.neverland.localization.MaterialLabels.canonicalKey(e.getKey())))
                .forEach(e -> setShopPrice(e.getKey(), e.getValue()));
    }

    public double insuranceReserve() { return insuranceReserve; }
    public void setInsuranceReserve(double value) { insuranceReserve = Math.max(0, value); }

    public String bulletin() { return bulletin; }
    public void setBulletin(String value) { bulletin = value == null ? "" : value; }
}
