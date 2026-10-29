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
    private final Map<UUID,ru.neverland.townybuilds.payments.BuildPayment> payments=new LinkedHashMap<>();
    public Map<UUID,ru.neverland.townybuilds.payments.BuildPayment> payments(){try(var g=ru.neverland.core.SuiteState.guard()){return Map.copyOf(payments);}}
    public void payment(ru.neverland.townybuilds.payments.BuildPayment value){requireMutable();var id=value.terms().id();var old=payments.get(id);
        if(old!=null){if(!old.terms().equals(value.terms()))throw new IllegalStateException("Условия оплаты изменены");old.phase(value.phase());}
        if(old==null&&payments.size()>=ru.neverland.townybuilds.payments.BuildPayment.MAX)throw new IllegalStateException("Журнал оплат заполнен; нужна архивная миграция");
        payments.put(id,value);
    }
    /** Only transaction undo can restore an earlier phase; never an administrative or bank replay API. */
    public void restorePayment(UUID id,ru.neverland.townybuilds.payments.BuildPayment value){if(!staging)throw new IllegalStateException("Только откат неподтверждённого снимка");if(value==null)payments.remove(id);else payments.put(id,value);}

    private final Map<UUID,ru.neverland.townybuilds.storage.RaidStock> raidStocks=new LinkedHashMap<>();
    private final Map<UUID,UUID> warehouseHolds=new HashMap<>();
    public Map<UUID,ru.neverland.townybuilds.storage.RaidStock> raidStocks(){return Map.copyOf(raidStocks);}
    public void raidStock(UUID op,ru.neverland.townybuilds.storage.RaidStock value){if(value==null)raidStocks.remove(op);else raidStocks.put(op,value);dirty=true;}
    public boolean freezeWarehouse(UUID town,UUID op){var prior=warehouseHolds.putIfAbsent(town,op);return prior==null||prior.equals(op);}
    public void thawWarehouse(UUID town,UUID op){warehouseHolds.remove(town,op);}
    public boolean storageSessionBusy(UUID town,String project){return storageLocks.busy(town,project);}
    private boolean dirty;
    private boolean busy,closing,staging;
    private final ru.neverland.core.DurableSnapshots writer;
    private java.util.concurrent.CompletionStage<Void> pending=java.util.concurrent.CompletableFuture.completedFuture(null),close;
    private final java.util.concurrent.CompletableFuture<Void> storageDrained=new java.util.concurrent.CompletableFuture<>();
    public boolean storageEditing(){return storageLocks.any();}
    public java.util.concurrent.CompletionStage<Void> pendingWrite(){try(var g=ru.neverland.core.SuiteState.guard()){return pending;}}
    public boolean busy(){try(var g=ru.neverland.core.SuiteState.guard()){return busy;}}
    private void requireMutable(){if(!staging&&(!writable||busy||closing))throw new IllegalStateException("Дождитесь сохранения данных города");}
    private TownData bind(TownData town){town.mutationGuard(this::requireMutable);return town;}
    /** One detached full-file image; acknowledged mutation is published only after fsync. */
    public java.util.concurrent.CompletionStage<Void> commitAsync(Runnable apply,Runnable undo){try(var g=ru.neverland.core.SuiteState.guard()){
        if(!writable())return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("Данные города ещё сохраняются или недоступны"));
        if(storageLocks.any())return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("Дождитесь закрытия открытых городских складов; оплата пока не началась"));
        YamlConfiguration image;boolean oldDirty=dirty;
        staging=true;try{apply.run();image=capture();}catch(Exception error){return java.util.concurrent.CompletableFuture.failedFuture(error);}finally{try{undo.run();dirty=oldDirty;}finally{staging=false;}}
        busy=true;var result=new java.util.concurrent.CompletableFuture<Void>();pending=result;
        writer.submit(image::saveToString).whenComplete((value,error)->{try(var lock=ru.neverland.core.SuiteState.guard()){
            Throwable failure=error;
            try{if(error!=null)writable=false;else{staging=true;apply.run();dirty=false;}}
            catch(Throwable failed){writable=false;failure=failed;}finally{staging=false;busy=false;}
            if(failure==null)result.complete(null);else result.completeExceptionally(failure);
        }});return result;
    }}
    public java.util.concurrent.CompletionStage<Void> prepareClose(){try(var g=ru.neverland.core.SuiteState.guard()){
        if(close!=null)return close;closing=true;
        if(!storageLocks.any()||org.bukkit.Bukkit.isStopping())storageDrained.complete(null);
        close=pending.handle((v,e)->null).thenCompose(v->storageDrained).thenCompose(v->{try(var lock=ru.neverland.core.SuiteState.guard()){
            if(!writable||!ru.neverland.core.AtomicFiles.writable(file.toPath()))return writer.closeAsync();
            try{var image=capture();return writer.finish(image::saveToString).thenCompose(ignored->writer.closeAsync());}
            catch(Exception error){writer.closeAsync();return java.util.concurrent.CompletableFuture.failedFuture(error);}
        }});return close;
    }}
    private boolean writable = true;
    private final ru.neverland.townybuilds.storage.StorageSessions storageLocks=new ru.neverland.townybuilds.storage.StorageSessions();
    public boolean storageBusy(UUID town,String project){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {return project.equals("warehouse")&&warehouseHolds.containsKey(town)||storageLocks.busy(town,project);} }
    public boolean lockStorage(UUID town,String project,UUID session){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {return writable()&&!(project.equals("warehouse")&&warehouseHolds.containsKey(town))&&storageLocks.acquire(town,project,session);} }
    public boolean ownsStorage(UUID town,String project,UUID session){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {return storageLocks.owns(town,project,session);} }
    public void unlockStorage(UUID town,String project,UUID session){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {storageLocks.release(town,project,session);if(closing&&!storageLocks.any())storageDrained.complete(null);} }
    /** Last contents are accepted from an existing exclusive menu even during orderly shutdown. */
    public void closeStorage(UUID town,String project,UUID session,ItemStack[] contents){try(var guard=ru.neverland.core.SuiteState.guard()){
        if(!storageLocks.owns(town,project,session))return;
        if(!writable||busy)throw new IllegalStateException("Склад не может быть сохранён; нужна проверка");
        var state=towns.get(town);if(state==null)throw new IllegalStateException("Городской склад удалён");
        boolean before=staging;staging=true;
        try{if(project.equals("warehouse"))state.setStorage(contents,state.storage().length);else state.setCivicInventory(project,contents);dirty=true;}
        finally{staging=before;}
    }}


    public DataStore(JavaPlugin plugin, int storageSize) {
        this.plugin = plugin;
        this.storageSize = storageSize;
        this.file = new File(plugin.getDataFolder(), "town-data.yml");
        writer=new ru.neverland.core.DurableSnapshots(file.toPath(),"NeverLand-builds-core-write",1);
    }

    public void load() { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {
        writable = false;
        towns.clear();payments.clear();
        raidStocks.clear();warehouseHolds.clear();
        if (!file.exists()) {
            writable = true; ru.neverland.core.AtomicFiles.loaded(file.toPath());
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try { yaml.load(file); } catch (Exception ex) { throw new IllegalStateException("town-data.yml повреждён; запись отключена", ex); }
        try{var raids=ru.neverland.core.SafeYaml.section(yaml,"raid-stock");if(raids!=null)for(String id:raids.getKeys(false)){
            var c=ru.neverland.core.SafeYaml.section(raids,id);if(c==null)throw new IOException("Нет квитанции добычи");
            var stock=new ru.neverland.townybuilds.storage.RaidStock(UUID.fromString(id),UUID.fromString(ru.neverland.core.SafeYaml.text(c,"defender")),UUID.fromString(ru.neverland.core.SafeYaml.text(c,"attacker")),UUID.fromString(ru.neverland.core.SafeYaml.text(c,"actor")),Math.toIntExact(ru.neverland.core.SafeYaml.integer(c,"bps")),ItemCodec.decode(ru.neverland.core.SafeYaml.text(c,"cargo"),0),ru.neverland.core.SafeYaml.text(c,"state"));raidStocks.put(stock.id(),stock);
        }}catch(Exception error){throw new IllegalStateException("Повреждён резерв добычи рейдов; запись отключена",error);}
        try{var purchaseSection=ru.neverland.core.SafeYaml.section(yaml,"build-payments");if(purchaseSection!=null){
            if(purchaseSection.getKeys(false).size()>ru.neverland.townybuilds.payments.BuildPayment.MAX)throw new IOException("Журнал оплат заполнен");
            for(String key:purchaseSection.getKeys(false)){
                var payment=ru.neverland.townybuilds.payments.BuildPayment.JSON.fromJson(ru.neverland.core.SafeYaml.text(purchaseSection,key),ru.neverland.townybuilds.payments.BuildPayment.class);
                if(payment==null||!payment.terms().id().equals(UUID.fromString(key)))throw new IOException("Повреждена квитанция оплаты");
                payments.put(payment.terms().id(),payment);
            }
        }}catch(Exception error){throw new IllegalStateException("Повреждён журнал оплаты построек; исходные данные сохранены",error);}
        ConfigurationSection root = yaml.getConfigurationSection("towns");
        if (root == null) {
            if (yaml.getKeys(false).stream().anyMatch(k->!k.equals("raid-stock")&&!k.equals("build-payments"))) throw new IllegalStateException("В базе нет раздела towns; запись отключена");
            writable = true; ru.neverland.core.AtomicFiles.loaded(file.toPath()); return;
        }
        for (String rawId : root.getKeys(false)) {
            try {
                UUID townId = UUID.fromString(rawId);
                TownData data = new TownData(townId, storageSize);
                ConfigurationSection section = root.getConfigurationSection(rawId);
                if(section==null)throw new IOException("Повреждена запись города");
                data.buildingStyle(section.getString("building-style","medieval"),section.getBoolean("style-chosen",false));
                data.loadArmyEstablished(ru.neverland.core.SafeYaml.booleanValue(section,"army-established",false));
                Map<String, Integer> levels = new HashMap<>();
                ConfigurationSection levelSection = section == null ? null : section.getConfigurationSection("levels");
                if (levelSection != null) {
                    for (String project : levelSection.getKeys(false)) {
                        levels.put(project, levelSection.getInt(project));
                    }
                }
                data.loadLevels(levels);
                var movements=ru.neverland.core.SafeYaml.section(section,"warehouse-movements");
                if(movements!=null)for(String key:movements.getKeys(false)){
                    var m=ru.neverland.core.SafeYaml.section(movements,key);
                    if(m==null||!m.isBoolean("incoming"))throw new IOException("Повреждена квитанция склада");
                    data.putWarehouseMovement(new ru.neverland.townybuilds.api.WarehouseMovement(UUID.fromString(key),ItemCodec.decodeSingle(ru.neverland.core.SafeYaml.text(m,"item")),Math.toIntExact(ru.neverland.core.SafeYaml.integer(m,"amount")),m.getBoolean("incoming")));
                }
                Map<String, ConstructionSite> sites = new HashMap<>();
                ConfigurationSection construction = section == null ? null : section.getConfigurationSection("construction");
                if (construction != null) {
                    for (String projectId : construction.getKeys(false)) {
                        ConfigurationSection site = construction.getConfigurationSection(projectId);
                        if (site == null) throw new IOException("Повреждена строительная площадка");
                        try {
                            UUID worldId = UUID.fromString(site.getString("world", ""));
                            BlockFace facing = BlockFace.valueOf(site.getString("facing", "NORTH"));
                            sites.put(projectId, new ConstructionSite(projectId, worldId,
                                    site.getInt("x"), site.getInt("y"), site.getInt("z"), facing,
                                    site.getInt("completed-stage"), site.getInt("target-stage"),
                                    site.getInt("build-from-stage", 1), site.getBoolean("active"),
                                    site.getInt("architecture-version", 1)));
                            sites.get(projectId).blueprintSet(ru.neverland.core.SafeYaml.stringValue(site,"blueprint-set",""));
                            sites.get(projectId).adminPaused(ru.neverland.core.SafeYaml.booleanValue(site,"admin-paused",false));
                        } catch (IllegalArgumentException exception) {
                            throw new IOException("Повреждена строительная площадка " + projectId + " города " + townId,exception);
                        }
                    }
                }
                data.loadConstructionSites(sites);
                var receipts = ru.neverland.core.SafeYaml.section(section, "construction-receipts");
                if (receipts != null) for (String project : receipts.getKeys(false)) {
                    var receipt = receipts.getConfigurationSection(project);
                    if (receipt == null) throw new IOException("Повреждена квитанция стройки");
                    data.receipt(project, ru.neverland.townybuilds.construction.ConstructionReceipt.load(receipt));
                }
                var drafts=ru.neverland.core.SafeYaml.section(section,"placement-drafts");
                if(drafts!=null)for(String project:drafts.getKeys(false)){
                    var draft=drafts.getConfigurationSection(project);if(draft==null)throw new IOException("Повреждён чертёж размещения");
                    data.placement(project,new ru.neverland.townybuilds.construction.PlacementDraft(UUID.fromString(ru.neverland.core.SafeYaml.stringValue(draft,"id")),ru.neverland.core.SafeYaml.intValue(draft,"level"),ru.neverland.core.SafeYaml.stringValue(draft,"blueprint-set","")));
                }
                Map<String, ResourceFund> funds = new HashMap<>();
                ConfigurationSection fundSection = section == null ? null : section.getConfigurationSection("resource-funds");
                if (fundSection != null) {
                    for (String projectId : fundSection.getKeys(false)) {
                        ConfigurationSection projectFund = fundSection.getConfigurationSection(projectId);
                        if (projectFund == null) throw new IOException("Повреждён фонд ресурсов");
                        ResourceFund fund = new ResourceFund(projectFund.getInt("target-level", 1));
                        ConfigurationSection entries = projectFund.getConfigurationSection("entries");
                        if (entries != null) {
                            for (String entryId : entries.getKeys(false)) {
                                ConfigurationSection entry = entries.getConfigurationSection(entryId);
                                if (entry == null) throw new IOException("Повреждена запись фонда");
                                String encoded = entry.getString("item", "");
                                int amount = entry.getInt("amount");
                                if (encoded.isBlank() || amount <= 0) throw new IOException("Повреждён предмет фонда");
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
                    loadMarket(section,data);
                    var deposits=section.getConfigurationSection("municipal-receipts");
                    if(deposits==null&&section.contains("municipal-receipts"))throw new IOException("Повреждены квитанции муниципальных поставок");
                    if(deposits!=null)for(String key:deposits.getKeys(false)){var receipt=deposits.getConfigurationSection(key);if(receipt==null)throw new IOException("Повреждена квитанция");data.putMunicipalReceipt(new ru.neverland.townybuilds.api.MunicipalReceipt(UUID.fromString(key),ItemCodec.decodeSingle(receipt.getString("sample","")),receipt.getInt("amount")));}
                }
                towns.put(townId, bind(data));
            } catch (IllegalArgumentException | IOException | ClassNotFoundException exception) {
                throw new IllegalStateException("Повреждены данные города " + rawId + "; запись отключена", exception);
            }
        }
        writable = true; ru.neverland.core.AtomicFiles.loaded(file.toPath());
        dirty = false;
    } }

    public boolean writable() { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {return writable && !busy && !closing && ru.neverland.core.AtomicFiles.writable(file.toPath());} }

    /** Read-only lookup: estimates must not create a town record or dirty the file. */
    public TownData existingTown(UUID townId) { try (var guard=ru.neverland.core.SuiteState.guard()) { return towns.get(townId); } }

    public TownData town(UUID townId) { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {
        var existing=towns.get(townId);if(existing!=null)return existing;
        if(!writable())throw new IllegalStateException("Хранилище построек сохраняется или недоступно; действие не принято");
        return towns.computeIfAbsent(townId, id -> {
            dirty = true;
            return bind(new TownData(id, storageSize));
        });
    } }

    public void markDirty() { try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {
        requireMutable();dirty = true;
    } }

    public Map<UUID, TownData> towns() { try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) {
        return Map.copyOf(towns);
    } }

    public void saveIfDirty() { try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) {
        if (dirty) {
            save();
        }
    } }

    public void save() { try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) {
        if (busy||closing){return;}
        if (!writable) return;
        try { saveOrThrow(); } catch (IOException ex) { writable=false; plugin.getLogger().severe("Не удалось сохранить town-data.yml: " + ex.getMessage()); throw new java.io.UncheckedIOException(ex); }
    } }

    public void saveOrThrow() throws IOException { try (var suiteGuard12 = ru.neverland.core.SuiteState.guard()) {
        if (!writable()) throw new IOException("Данные города сохраняются или запись запрещена");
        ru.neverland.townybuilds.util.AtomicYamlFile.write(capture(),file.toPath());dirty=false;
    } }
    private YamlConfiguration capture()throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.createSection("towns");
        for(var p:payments.values())yaml.set("build-payments."+p.terms().id(),ru.neverland.townybuilds.payments.BuildPayment.JSON.toJson(p));
        for(var stock:raidStocks.values()){String p="raid-stock."+stock.id();yaml.set(p+".defender",stock.defender().toString());yaml.set(p+".attacker",stock.attacker().toString());yaml.set(p+".actor",stock.actor().toString());yaml.set(p+".bps",stock.basisPoints());yaml.set(p+".cargo",ItemCodec.encode(stock.cargo()));yaml.set(p+".state",stock.state());}
        for (TownData data : towns.values()) {
            String path = "towns." + data.townId();
            for(var m:data.warehouseMovements().values()){
                String p=path+".warehouse-movements."+m.id();
                yaml.set(p+".item",ItemCodec.encodeSingle(m.sample()));yaml.set(p+".amount",m.amount());yaml.set(p+".incoming",m.incoming());
            }
            yaml.set(path+".building-style",data.buildingStyle());yaml.set(path+".style-chosen",data.styleChosen());
            yaml.set(path+".army-established",data.armyEstablished());
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
                yaml.set(root + ".build-from-stage", site.buildFromStage());yaml.set(root+".admin-paused",site.adminPaused());
                yaml.set(root + ".active", site.active());
                yaml.set(root + ".architecture-version", site.architectureVersion());
                yaml.set(root + ".blueprint-set",site.blueprintSet().isEmpty()?null:site.blueprintSet());
            }
            for(var draft:data.placements().entrySet()){
                yaml.set(path+".placement-drafts."+draft.getKey()+".blueprint-set",draft.getValue().blueprintSet().isEmpty()?null:draft.getValue().blueprintSet());
                String root=path+".placement-drafts."+draft.getKey();yaml.set(root+".id",draft.getValue().id().toString());yaml.set(root+".level",draft.getValue().level());
            }
            for (var receipt : data.constructionReceipts().entrySet())
                receipt.getValue().save(yaml.createSection(path + ".construction-receipts." + receipt.getKey()));
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
            for(var receipt:data.municipalReceipts().values()){String c=path+".municipal-receipts."+receipt.id();yaml.set(c+".sample",ItemCodec.encodeSingle(receipt.sample()));yaml.set(c+".amount",receipt.amount());}
            for(var lot:data.marketStock().values()) {
                String c=path+".market-stock."+lot.id();yaml.set(c+".sample",ItemCodec.encodeSingle(lot.sample()));yaml.set(c+".total",lot.total());yaml.set(c+".available",lot.available());yaml.set(c+".open",lot.open());
                yaml.createSection(c+".holds");for(var h:lot.holds().values()){String o=c+".holds."+h.id();yaml.set(o+".buyer",h.buyer().toString());yaml.set(o+".city",h.city());yaml.set(o+".amount",h.amount());yaml.set(o+".status",h.status());}
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
        return yaml;
    }

    private void loadMarket(ConfigurationSection section,TownData data)throws IOException,ClassNotFoundException {
        var root=section.getConfigurationSection("market-stock");if(root==null){if(section.contains("market-stock"))throw new IOException("Повреждён резерв рынка");return;}
        for(String id:root.getKeys(false)){
            var c=root.getConfigurationSection(id);if(c==null||!c.isInt("total")||!c.isInt("available")||!c.isBoolean("open"))throw new IOException("Повреждён товар рынка");
            var hs=c.getConfigurationSection("holds");if(hs==null)throw new IOException("Нет квитанций рынка");Map<UUID,ru.neverland.townybuilds.api.MarketStock.Hold> holds=new HashMap<>();
            for(String key:hs.getKeys(false)){var h=hs.getConfigurationSection(key);if(h==null||!h.isInt("amount")||!h.isBoolean("city"))throw new IOException("Повреждена покупка");UUID oid=UUID.fromString(key);holds.put(oid,new ru.neverland.townybuilds.api.MarketStock.Hold(oid,UUID.fromString(h.getString("buyer","")),h.getBoolean("city"),h.getInt("amount"),h.getString("status","")));}
            data.putMarketStock(new ru.neverland.townybuilds.api.MarketStock(UUID.fromString(id),ItemCodec.decodeSingle(c.getString("sample","")),c.getInt("total"),c.getInt("available"),c.getBoolean("open"),holds));
        }
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
