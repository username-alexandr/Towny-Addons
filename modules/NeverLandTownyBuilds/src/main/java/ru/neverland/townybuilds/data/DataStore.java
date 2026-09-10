package ru.neverland.townybuilds.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townybuilds.construction.ConstructionSite;
import ru.neverland.townybuilds.civic.CivicArea;
import ru.neverland.townybuilds.civic.CivicLine;
import ru.neverland.townybuilds.util.ItemCodec;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class DataStore {
    private final JavaPlugin plugin;
    private final File file;
    private final int storageSize;
    private final Map<UUID, TownData> towns = new LinkedHashMap<>();
    private boolean dirty;
    private boolean writable = true;
    private final ru.neverland.townybuilds.storage.StorageSessions storageLocks=new ru.neverland.townybuilds.storage.StorageSessions();
    public synchronized boolean storageBusy(UUID town,String project){return storageLocks.busy(town,project);}
    public synchronized boolean lockStorage(UUID town,String project,UUID session){return storageLocks.acquire(town,project,session);}
    public synchronized boolean ownsStorage(UUID town,String project,UUID session){return storageLocks.owns(town,project,session);}
    public synchronized void unlockStorage(UUID town,String project,UUID session){storageLocks.release(town,project,session);}


    public DataStore(JavaPlugin plugin, int storageSize) {
        this.plugin = plugin;
        this.storageSize = storageSize;
        this.file = new File(plugin.getDataFolder(), "town-data.yml");
    }

    public synchronized void load() {
        writable = false;
        towns.clear();
        if (!file.exists()) {
            writable = true;
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try { yaml.load(file); } catch (Exception ex) { throw new IllegalStateException("town-data.yml повреждён; запись отключена", ex); }
        ConfigurationSection root = yaml.getConfigurationSection("towns");
        if (root == null) {
            if (!yaml.getKeys(false).isEmpty()) throw new IllegalStateException("В базе нет раздела towns; запись отключена");
            writable = true; return;
        }
        for (String rawId : root.getKeys(false)) {
            try {
                UUID townId = UUID.fromString(rawId);
                TownData data = new TownData(townId, storageSize);
                ConfigurationSection section = root.getConfigurationSection(rawId);
                if(section==null)throw new IOException("Повреждена запись города");
                Map<String, Integer> levels = new HashMap<>();
                ConfigurationSection levelSection = section == null ? null : section.getConfigurationSection("levels");
                if (levelSection != null) {
                    for (String project : levelSection.getKeys(false)) {
                        levels.put(project, levelSection.getInt(project));
                    }
                }
                data.loadLevels(levels);
                Map<String, ConstructionSite> sites = new HashMap<>();
                ConfigurationSection construction = section == null ? null : section.getConfigurationSection("construction");
                if (construction != null) {
                    for (String projectId : construction.getKeys(false)) {
                        ConfigurationSection site = construction.getConfigurationSection(projectId);
                        if (site == null) continue;
                        try {
                            UUID worldId = UUID.fromString(site.getString("world", ""));
                            BlockFace facing = BlockFace.valueOf(site.getString("facing", "NORTH"));
                            sites.put(projectId, new ConstructionSite(projectId, worldId,
                                    site.getInt("x"), site.getInt("y"), site.getInt("z"), facing,
                                    site.getInt("completed-stage"), site.getInt("target-stage"),
                                    site.getInt("build-from-stage", 1), site.getBoolean("active"),
                                    site.getInt("architecture-version", 1)));
                        } catch (IllegalArgumentException exception) {
                            plugin.getLogger().warning("Повреждена строительная площадка " + projectId
                                    + " города " + townId + ": " + exception.getMessage());
                        }
                    }
                }
                data.loadConstructionSites(sites);
                Map<String, ResourceFund> funds = new HashMap<>();
                ConfigurationSection fundSection = section == null ? null : section.getConfigurationSection("resource-funds");
                if (fundSection != null) {
                    for (String projectId : fundSection.getKeys(false)) {
                        ConfigurationSection projectFund = fundSection.getConfigurationSection(projectId);
                        if (projectFund == null) continue;
                        ResourceFund fund = new ResourceFund(projectFund.getInt("target-level", 1));
                        ConfigurationSection entries = projectFund.getConfigurationSection("entries");
                        if (entries != null) {
                            for (String entryId : entries.getKeys(false)) {
                                ConfigurationSection entry = entries.getConfigurationSection(entryId);
                                if (entry == null) continue;
                                String encoded = entry.getString("item", "");
                                int amount = entry.getInt("amount");
                                if (encoded.isBlank() || amount <= 0) continue;
                                fund.add(ItemCodec.decodeSingle(encoded), amount);
                            }
                        }
                        funds.put(projectId, fund);
                    }
                }
                data.loadResourceFunds(funds);
                if (section != null) {
                    ItemStack[] contents = ItemCodec.decode(section.getString("inventory"), storageSize);
                    data.setStorage(contents, storageSize);
                    loadCivicData(section, data);
                    loadShipments(section, data);
                    loadTradeCargo(section, data);
                }
                towns.put(townId, data);
            } catch (IllegalArgumentException | IOException | ClassNotFoundException exception) {
                throw new IllegalStateException("Повреждены данные города " + rawId + "; запись отключена", exception);
            }
        }
        writable = true;
        dirty = false;
    }

    public synchronized TownData town(UUID townId) {
        return towns.computeIfAbsent(townId, id -> {
            dirty = true;
            return new TownData(id, storageSize);
        });
    }

    public synchronized void markDirty() {
        dirty = true;
    }

    public synchronized Map<UUID, TownData> towns() {
        return Map.copyOf(towns);
    }

    public synchronized void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    public synchronized void save() {
        if (!writable) return;
        try { saveOrThrow(); } catch (IOException ex) { plugin.getLogger().severe("Не удалось сохранить town-data.yml: " + ex.getMessage()); }
    }

    public synchronized void saveOrThrow() throws IOException {
        if (!writable) throw new IOException("Запись повреждённой базы запрещена");
        YamlConfiguration yaml = new YamlConfiguration();
        for (TownData data : towns.values()) {
            String path = "towns." + data.townId();
            for (Map.Entry<String, Integer> level : data.levels().entrySet()) {
                yaml.set(path + ".levels." + level.getKey(), level.getValue());
            }
            for (ConstructionSite site : data.constructionSites().values()) {
                String root = path + ".construction." + site.projectId();
                yaml.set(root + ".world", site.worldId().toString());
                yaml.set(root + ".x", site.originX());
                yaml.set(root + ".y", site.originY());
                yaml.set(root + ".z", site.originZ());
                yaml.set(root + ".facing", site.facing().name());
                yaml.set(root + ".completed-stage", site.completedStage());
                yaml.set(root + ".target-stage", site.targetStage());
                yaml.set(root + ".build-from-stage", site.buildFromStage());
                yaml.set(root + ".active", site.active());
                yaml.set(root + ".architecture-version", site.architectureVersion());
            }
            for (Map.Entry<String, ResourceFund> fundEntry : data.resourceFunds().entrySet()) {
                String root = path + ".resource-funds." + fundEntry.getKey();
                ResourceFund fund = fundEntry.getValue();
                yaml.set(root + ".target-level", fund.targetLevel());
                int index = 0;
                for (ResourceFund.Snapshot entry : fund.entries()) {
                    try {
                        yaml.set(root + ".entries." + index + ".item", ItemCodec.encodeSingle(entry.template()));
                        yaml.set(root + ".entries." + index + ".amount", entry.amount());
                        index++;
                    } catch (IOException exception) {
                        throw new IOException("Не удалось сериализовать фонд " + fundEntry.getKey(), exception);
                    }
                }
            }
            try {
                yaml.set(path + ".inventory", ItemCodec.encode(data.storage()));
                for (Map.Entry<String, ItemStack[]> inventory : data.civicInventories().entrySet()) {
                    yaml.set(path + ".civic-inventories." + inventory.getKey(), ItemCodec.encode(inventory.getValue()));
                }
            } catch (IOException exception) {
                throw new IOException("Не удалось сериализовать склад " + data.townId(), exception);
            }
            for(var cargo:data.tradeCargo().values()) {
                String c=path+".trade-cargo."+cargo.id();yaml.set(c+".buyer",cargo.buyer().toString());
                yaml.set(c+".sample",ItemCodec.encodeSingle(cargo.sample()));yaml.set(c+".amount",cargo.amount());
                yaml.set(c+".cargo",ItemCodec.encode(cargo.cargo()));yaml.set(c+".status",cargo.status());
            }
            for (var shipment : data.shipments().values()) {
                String cargoPath = path + ".shipments." + shipment.id();
                yaml.set(cargoPath + ".route", shipment.route());yaml.set(cargoPath + ".source", shipment.source());
                yaml.set(cargoPath + ".target", shipment.target());yaml.set(cargoPath + ".status", shipment.status());
                yaml.set(cargoPath + ".created", shipment.createdAt());yaml.set(cargoPath + ".cargo", ItemCodec.encode(shipment.cargo()));
            }
            for (Map.Entry<String, CivicArea> areaEntry : data.civicAreas().entrySet()) {
                CivicArea area = areaEntry.getValue();
                String root = path + ".civic-areas." + areaEntry.getKey();
                yaml.set(root + ".world", area.worldId().toString());
                yaml.set(root + ".min-x", area.minX());
                yaml.set(root + ".max-x", area.maxX());
                yaml.set(root + ".min-z", area.minZ());
                yaml.set(root + ".max-z", area.maxZ());
            }
            for (Map.Entry<String, CivicLine> lineEntry : data.civicLines().entrySet()) {
                CivicLine line = lineEntry.getValue();
                String root = path + ".civic-lines." + lineEntry.getKey();
                yaml.set(root + ".world", line.worldId().toString());
                yaml.set(root + ".x1", line.x1());
                yaml.set(root + ".y1", line.y1());
                yaml.set(root + ".z1", line.z1());
                yaml.set(root + ".x2", line.x2());
                yaml.set(root + ".y2", line.y2());
                yaml.set(root + ".z2", line.z2());
            }
            yaml.set(path + ".shop.stall", data.shopStall().isBlank() ? null : data.shopStall());
            for (Map.Entry<String, Double> price : data.shopPrices().entrySet()) {
                yaml.set(path + ".shop.prices." + price.getKey(), price.getValue());
            }
            yaml.set(path + ".insurance-reserve", data.insuranceReserve() <= 0 ? null : data.insuranceReserve());
            yaml.set(path + ".bulletin", data.bulletin().isBlank() ? null : data.bulletin());
        }
        ru.neverland.townybuilds.util.AtomicYamlFile.write(yaml,file.toPath());
        dirty=false;
    }

    private void loadTradeCargo(ConfigurationSection section,TownData data)throws IOException,ClassNotFoundException {
        var root=section.getConfigurationSection("trade-cargo");
        if(root==null){if(section.contains("trade-cargo"))throw new IOException("Повреждён журнал поставок");return;}
        for(String id:root.getKeys(false)) {
            var c=root.getConfigurationSection(id);if(c==null)throw new IOException("Повреждена поставка");
            UUID buyer=UUID.fromString(c.getString("buyer",""));if(buyer.equals(data.townId()))throw new IOException("Одинаковые города поставки");
            data.putTradeCargo(new ru.neverland.townybuilds.api.TradeCargo(UUID.fromString(id),buyer,ItemCodec.decodeSingle(c.getString("sample","")),
                c.getInt("amount"),ItemCodec.decode(c.getString("cargo"),0),c.getString("status","")));
        }
    }

    private void loadShipments(ConfigurationSection section,TownData data)throws IOException,ClassNotFoundException {
        var root=section.getConfigurationSection("shipments");if(root==null){if(section.contains("shipments"))throw new IOException("Повреждён журнал грузов");return;}
        for(String id:root.getKeys(false)){
            var c=root.getConfigurationSection(id);if(c==null)throw new IOException("Повреждён груз");
            data.putShipment(new ru.neverland.townybuilds.api.CargoShipment(UUID.fromString(id),c.getString("route"),c.getString("source"),c.getString("target"),
                    ItemCodec.decode(c.getString("cargo"),0),c.getString("status"),c.getLong("created")));
        }
    }

    private void loadCivicData(ConfigurationSection section, TownData data)
            throws IOException, ClassNotFoundException {
        Map<String, CivicArea> areas = new HashMap<>();
        ConfigurationSection areaRoot = section.getConfigurationSection("civic-areas");
        if (areaRoot != null) {
            for (String projectId : areaRoot.getKeys(false)) {
                ConfigurationSection area = areaRoot.getConfigurationSection(projectId);
                if (area == null) continue;
                try {
                    areas.put(projectId, new CivicArea(UUID.fromString(area.getString("world", "")),
                            area.getInt("min-x"), area.getInt("max-x"), area.getInt("min-z"), area.getInt("max-z")));
                } catch (IllegalArgumentException exception) {
                    plugin.getLogger().warning("Пропущена повреждённая территория " + projectId
                            + " города " + data.townId() + ": " + exception.getMessage());
                }
            }
        }
        data.loadCivicAreas(areas);

        Map<String, CivicLine> lines = new HashMap<>();
        ConfigurationSection lineRoot = section.getConfigurationSection("civic-lines");
        if (lineRoot != null) {
            for (String projectId : lineRoot.getKeys(false)) {
                ConfigurationSection line = lineRoot.getConfigurationSection(projectId);
                if (line == null) continue;
                try {
                    lines.put(projectId, new CivicLine(UUID.fromString(line.getString("world", "")),
                            line.getInt("x1"), line.getInt("y1"), line.getInt("z1"),
                            line.getInt("x2"), line.getInt("y2"), line.getInt("z2")));
                } catch (IllegalArgumentException exception) {
                    plugin.getLogger().warning("Пропущена повреждённая линия " + projectId
                            + " города " + data.townId() + ": " + exception.getMessage());
                }
            }
        }
        data.loadCivicLines(lines);

        Map<String, ItemStack[]> inventories = new HashMap<>();
        ConfigurationSection inventoryRoot = section.getConfigurationSection("civic-inventories");
        if (inventoryRoot != null) {
            for (String projectId : inventoryRoot.getKeys(false)) {
                inventories.put(projectId, ItemCodec.decode(inventoryRoot.getString(projectId), 54));
            }
        }
        data.loadCivicInventories(inventories);

        data.setShopStall(section.getString("shop.stall", ""));
        Map<String, Double> prices = new HashMap<>();
        ConfigurationSection priceRoot = section.getConfigurationSection("shop.prices");
        if (priceRoot != null) {
            for (String material : priceRoot.getKeys(false)) {
                double price = priceRoot.getDouble(material);
                if (price > 0) prices.put(material, price);
            }
        }
        data.loadShopPrices(prices);
        data.setInsuranceReserve(section.getDouble("insurance-reserve"));
        data.setBulletin(section.getString("bulletin", ""));
    }
}
