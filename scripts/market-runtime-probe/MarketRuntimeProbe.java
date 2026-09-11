package ru.neverland.runtime;

import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import ru.neverland.townybuilds.api.MarketStorageApi;
import ru.neverland.townybuilds.data.DataStore;
import ru.neverland.townybuilds.construction.ConstructionSite;
import ru.neverland.townyupkeep.api.TownyUpkeepApi;
import ru.neverland.townyupkeep.service.UpkeepService;
import ru.neverland.townypower.api.TownyPowerApi;
import ru.neverland.townypower.service.PowerService;
import ru.neverland.townypolicies.api.TownyPoliciesApi;
import ru.neverland.townypolicies.service.PoliciesService;
import ru.neverland.townymarket.*;
import static ru.neverland.townymarket.MarketData.*;

/** Destructive fixtures for an explicitly marked disposable loopback server only. */
public final class MarketRuntimeProbe extends JavaPlugin {
    private DataStore data;
    private MarketStorageApi stock;
    private MarketRepository book;
    private MarketGateway gateway;
    private final UUID sellerId = id("seller"), buyerId = id("buyer"), residentId = id("resident");
    private final UUID globalLot = id("global-lot"), localLot = id("local-lot");
    private final UUID globalOrder = id("global-order"), localOrder = id("local-order"), restartOrder = id("restart-order");

    private static UUID id(String name) {
        return UUID.nameUUIDFromBytes(("neverland-market-runtime-0180:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override public void onEnable() {
        Path marker = Path.of("ALLOW_DISPOSABLE_MARKET_PROBE");
        if (!Boolean.getBoolean("neverland.runtimeProbe") || !Files.isRegularFile(marker)
                || !"127.0.0.1".equals(getServer().getIp())) {
            getLogger().severe("Probe refused: requires disposable marker, JVM opt-in and loopback server-ip.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getScheduler().runTaskLater(this, this::run, 100L);
    }

    private void run() {
        String stage = "initialization";
        try {
            Files.createDirectories(getDataFolder().toPath());
            var builds = getServer().getPluginManager().getPlugin("NeverLandTownyBuilds");
            var market = (JavaPlugin) getServer().getPluginManager().getPlugin("NeverLandTownyMarket");
            var field = builds.getClass().getDeclaredField("dataStore");
            field.setAccessible(true);
            data = (DataStore) field.get(builds);
            stock = getServer().getServicesManager().load(MarketStorageApi.class);
            check(stock != null, "MarketStorageApi registered");
            check(getServer().getServicesManager().load(MarketApi.class) != null, "MarketApi registered");
            long count = Arrays.stream(getServer().getPluginManager().getPlugins())
                    .filter(p -> p.getName().startsWith("NeverLandTowny") && p.isEnabled()).count();
            check(count == 25, "all 25 add-ons enabled, got " + count);
            check(TownyEconomyHandler.isActive(), "Towny economy active through Vault and EssentialsX");
            check(getServer().getPluginCommand("townymarket") != null, "market command registered");
            check(new MarketCatalog(market).products().size() == 16, "all 16 default products loaded");
            book = new MarketRepository(getDataFolder().toPath().resolve("probe-market.yml"));
            book.load();
            gateway = new MarketGateway(market, book);
            stage = Files.exists(getDataFolder().toPath().resolve("restart-ready")) ? "restart" : "first";
            if (stage.equals("first")) first(); else restart();
            Files.writeString(getDataFolder().toPath().resolve(stage + "-passed.txt"), "PASS\n");
            getLogger().info("MARKET_RUNTIME_" + stage.toUpperCase(Locale.ROOT) + "_PASS");
        } catch (Throwable error) {
            getLogger().log(java.util.logging.Level.SEVERE, "MARKET_RUNTIME_FAIL at " + stage, error);
            try { Files.writeString(getDataFolder().toPath().resolve("failed.txt"), error.toString()); }
            catch (Exception ignored) { }
        } finally {
            getServer().getScheduler().runTaskLater(this, () -> getServer().shutdown(), 20L);
        }
    }

    private Town createTown(String name, UUID townId, String mayorName, UUID mayorId) throws Exception {
        var universe = TownyUniverse.getInstance();
        check(TownyAPI.getInstance().getTown(townId) == null, "fixture city does not already exist");
        universe.newTownInternal(name, townId);
        var town = TownyAPI.getInstance().getTown(townId);
        var resident = universe.getDataSource().newResident(mayorName, mayorId);
        resident.setTown(town);
        town.setMayor(resident);
        var world = getServer().getWorlds().getFirst();
        var townyWorld = universe.getWorld(world.getName());
        int origin = townId.equals(sellerId) ? 0 : 12;
        for (int x = origin; x < origin + 8; x++) for (int z = 0; z < 8; z++) {
            var block = new TownBlock(x, z, townyWorld);
            universe.addTownBlock(block);
            block.setTown(town);
            if (x == origin && z == 0) town.setHomeBlock(block);
            block.save();
        }
        town.setSpawn(new Location(world, origin * Coord.getCellSize() + 4, 64, 4));
        resident.save();
        town.save();
        return town;
    }

    private void first() throws Exception {
        check(book.orders().isEmpty(), "fresh probe journal");
        var seller = createTown("ProbeSeller", sellerId, "ProbeSellerMayor", residentId);
        var buyer = createTown("ProbeBuyer", buyerId, "ProbeBuyerMayor", id("buyer-mayor"));
        check(seller.getAccount().deposit(1000, "Runtime fixture initial funds"), "seed seller bank");
        check(buyer.getAccount().deposit(10000, "Runtime fixture initial funds"), "seed buyer bank");
        var resident = TownyAPI.getInstance().getResident(residentId);
        check(resident.getAccount().deposit(1000, "Runtime fixture personal funds"), "seed resident bank");
        double sellerStart = balance(sellerId), buyerStart = balance(buyerId);
        double personalStart = resident.getAccount().getHoldingBalance();
        getConfig().set("seller-start", sellerStart);
        getConfig().set("buyer-start", buyerStart);
        getConfig().set("personal-start", personalStart);
        saveConfig();
        data.town(sellerId).setLevel("market", 2);
        data.town(sellerId).setLevel("merchant_guild", 1);
        data.town(buyerId).setLevel("market", 1);
        ((PoliciesService) getServer().getServicesManager().load(TownyPoliciesApi.class)).refresh();
        var world = getServer().getWorlds().getFirst();
        data.town(sellerId).setConstructionSite(new ConstructionSite("market", world.getUID(), 24, 64, 24,
                org.bukkit.block.BlockFace.NORTH, 2, 2, 1, false));
        data.town(sellerId).setConstructionSite(new ConstructionSite("merchant_guild", world.getUID(), 72, 64, 24,
                org.bukkit.block.BlockFace.NORTH, 1, 1, 1, false));
        data.town(buyerId).setConstructionSite(new ConstructionSite("market", world.getUID(),
                12 * Coord.getCellSize() + 24, 64, 24, org.bukkit.block.BlockFace.NORTH, 1, 1, 1, false));
        var upkeep = (UpkeepService) getServer().getServicesManager().load(TownyUpkeepApi.class);
        upkeep.reload(upkeep.settings());
        ((PowerService) getServer().getServicesManager().load(TownyPowerApi.class)).refresh();
        ItemStack sample = new ItemStack(Material.IRON_INGOT);
        var meta = sample.getItemMeta();
        meta.setDisplayName("Проверочный слиток");
        meta.getPersistentDataContainer().set(new NamespacedKey(this, "sample"),
                org.bukkit.persistence.PersistentDataType.STRING, "exact-metadata");
        sample.setItemMeta(meta);
        ItemStack[] items = data.town(sellerId).storage();
        for (int i = 0; i < 3; i++) { items[i] = sample.clone(); items[i].setAmount(64); }
        data.town(sellerId).setStorage(items, items.length);
        data.saveOrThrow();
        check(MarketCatalog.decode(MarketCatalog.encode(sample)).isSimilar(sample), "native item metadata roundtrip");
        Listing global = listing(globalLot, sample, Scope.GLOBAL, 128);
        eq("OPEN", stock.open(sellerId, globalLot, sample, 128), "reserve export listing");
        book.put(global);
        eq(64, count(sellerId, sample), "export removed physical stock");
        eq(null, gateway.sellerReady(global), "export buildings active");
        eq(null, gateway.imports(sellerId, buyerId), "actual Policies and Taxes bridge");
        Order purchase = order(globalOrder, globalLot, buyerId, id("buyer-mayor"), true, 32);
        book.put(purchase);
        eq(null, gateway.ready(purchase), "infrastructure budget permits purchase");
        advance(globalOrder, 5);
        check(book.order(globalOrder).finalized(), "global order completed and acknowledged");
        near(sellerStart + 128, balance(sellerId), "seller credited once");
        near(buyerStart - 128, balance(buyerId), "buyer debited once");
        eq(32, count(buyerId, sample), "buyer received exact metadata items");
        advance(globalOrder, 5);
        near(buyerStart - 128, balance(buyerId), "completed payment replay safe");
        eq(32, count(buyerId, sample), "completed delivery replay safe");

        Listing local = listing(localLot, sample, Scope.LOCAL, 32);
        eq("OPEN", stock.open(sellerId, localLot, sample, 32), "reserve local listing");
        book.put(local);
        book.put(order(localOrder, localLot, residentId, residentId, false, 16));
        advance(localOrder, 5);
        eq(Phase.COMPLETE, book.order(localOrder).phase(), "personal order paid");
        eq("PICKUP", gateway.receipt(book.order(localOrder)), "personal goods retained for pickup");
        check(!book.order(localOrder).finalized(), "unclaimed goods are not acknowledged");
        near(personalStart - 64, resident.getAccount().getHoldingBalance(), "real personal bank debit");
        near(sellerStart + 192, balance(sellerId), "local revenue credited to seller");
        eq(2, book.demand().size(), "only completed purchases create demand");

        UUID lock = id("warehouse-lock");
        check(data.lockStorage(sellerId, "warehouse", lock), "open warehouse lock");
        eq("BUSY", stock.close(sellerId, localLot), "closing waits for open warehouse");
        data.unlockStorage(sellerId, "warehouse", lock);
        eq("CLOSED", stock.close(sellerId, localLot), "unreserved remainder returned");
        eq(48, count(sellerId, sample), "only 16 unsold local items returned");
        eq("PICKUP", gateway.receipt(book.order(localOrder)), "closing preserves paid personal goods");

        book.put(order(restartOrder, globalLot, buyerId, id("buyer-mayor"), true, 8));
        advance(restartOrder, 1);
        eq(Phase.PAID, book.order(restartOrder).phase(), "stop after debit before delivery");
        near(buyerStart - 160, balance(buyerId), "second debit committed before restart");
        data.saveOrThrow();
        TownyUniverse.getInstance().getDataSource().saveAll();
        Files.writeString(getDataFolder().toPath().resolve("restart-ready"), "PAID\n");
    }

    private void restart() throws Exception {
        reloadConfig();
        double sellerStart = getConfig().getDouble("seller-start");
        double buyerStart = getConfig().getDouble("buyer-start");
        ItemStack sample = MarketCatalog.decode(book.listing(globalLot).item());
        eq(Phase.PAID, book.order(restartOrder).phase(), "paid phase persisted across full server restart");
        eq("HELD", gateway.receipt(book.order(restartOrder)), "warehouse reservation persisted");
        near(buyerStart - 160, balance(buyerId), "real economy debit persisted");
        advance(restartOrder, 5);
        check(book.order(restartOrder).finalized(), "paid purchase recovered");
        eq(40, count(buyerId, sample), "recovery delivered eight items once");
        near(buyerStart - 160, balance(buyerId), "recovery did not debit again");
        near(sellerStart + 224, balance(sellerId), "recovery credited seller once");
        advance(restartOrder, 5);
        eq(40, count(buyerId, sample), "recovered delivery replay safe");
        eq("PICKUP", gateway.receipt(book.order(localOrder)), "personal pickup survived restart");
        eq(3, book.demand().size(), "demand journal persisted without duplicates");
        eq("CLOSED", stock.close(sellerId, globalLot), "return remaining export stock");
        eq(136, count(sellerId, sample), "stock conservation: 136 seller + 40 buyer + 16 pickup = 192");
        near(getConfig().getDouble("personal-start") - 64,
                TownyAPI.getInstance().getResident(residentId).getAccount().getHoldingBalance(), "personal balance persisted");
    }

    private Listing listing(UUID id, ItemStack item, Scope scope, int amount) {
        String encoded = MarketCatalog.encode(item);
        return new Listing(id, sellerId, encoded, "Проверочный слиток", MarketCatalog.key(encoded),
                scope, false, 400, amount, System.currentTimeMillis(), ListingState.ACTIVE, "runtime probe");
    }
    private Order order(UUID id, UUID lot, UUID buyer, UUID actor, boolean city, int amount) {
        return new Order(id, lot, sellerId, buyer, actor, city, amount, 400,
                System.currentTimeMillis(), Phase.PREPARED, 0, "runtime probe", false);
    }
    private void advance(UUID id, int times) throws Exception {
        for (int i = 0; i < times; i++) MarketPayments.advance(id, System.currentTimeMillis(), 1000, book, gateway);
    }
    private int count(UUID town, ItemStack sample) {
        return Arrays.stream(data.town(town).storage()).filter(Objects::nonNull)
                .filter(sample::isSimilar).mapToInt(ItemStack::getAmount).sum();
    }
    private double balance(UUID town) { return TownyAPI.getInstance().getTown(town).getAccount().getHoldingBalance(); }
    private void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        getLogger().info("PASS: " + label);
    }
    private void eq(Object expected, Object actual, String label) {
        check(Objects.equals(expected, actual), label + " [expected=" + expected + ", actual=" + actual + "]");
    }
    private void near(double expected, double actual, String label) {
        check(Math.abs(expected - actual) < 0.001, label + " [expected=" + expected + ", actual=" + actual + "]");
    }
}
