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

/**
 * Территориальные, хозяйственные и торговые механики муниципальной очереди.
 * Все операции выполняются в главном потоке, не загружают чанки принудительно и
 * не требуют других аддонов NeverLand, что исключает циклы загрузки Paper.
 */
public final class CivicService implements Listener, TownyBuildsApi {
    private static final Set<String> AREA_PROJECTS = CivicBlueprintGenerator.AREA_PROJECTS;
    private static final Set<String> LINE_PROJECTS = CivicBlueprintGenerator.LINE_PROJECTS;
    private static final Set<String> STORAGE_PROJECTS = new ru.neverland.townybuilds.construction.BuildingBlueprintGenerator().supportedProjects();
    private static final Set<Material> SAPLINGS = Set.of(
            Material.OAK_SAPLING, Material.BIRCH_SAPLING, Material.SPRUCE_SAPLING,
            Material.JUNGLE_SAPLING, Material.ACACIA_SAPLING, Material.DARK_OAK_SAPLING,
            Material.CHERRY_SAPLING, Material.MANGROVE_PROPAGULE
    );
    private static final Set<Material> PLANTABLE_GROUND = Set.of(
            Material.GRASS_BLOCK, Material.DIRT, Material.COARSE_DIRT, Material.PODZOL,
            Material.ROOTED_DIRT, Material.MOSS_BLOCK, Material.MUD
    );
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.##",
            DecimalFormatSymbols.getInstance(Locale.forLanguageTag("ru-RU")));
    private static final List<RecycleRecipe> RECIPES = List.of(
            new RecycleRecipe(Material.ROTTEN_FLESH, 8, Material.LEATHER, 1),
            new RecycleRecipe(Material.GLASS_BOTTLE, 8, Material.GLASS, 1),
            new RecycleRecipe(Material.COBBLESTONE, 16, Material.GRAVEL, 4),
            new RecycleRecipe(Material.POISONOUS_POTATO, 8, Material.BONE_MEAL, 2)
    );

    private final JavaPlugin plugin;
    private final TownyHook towny;
    private final DataStore dataStore;
    private final MessageService messages;
    private final RussianItemNames itemNames;
    private final Map<UUID, DraftSelection> selections = new HashMap<>();
    private final ru.neverland.townybuilds.construction.BuildingFootprints footprints = new ru.neverland.townybuilds.construction.BuildingFootprints();
    private BukkitTask automationTask;
    private BukkitTask previewTask;

    public CivicService(JavaPlugin plugin, TownyHook towny, DataStore dataStore, MessageService messages, RussianItemNames itemNames) {
        this.plugin = plugin;
        this.towny = towny;
        this.dataStore = dataStore;
        this.messages = messages;
        this.itemNames = itemNames;
    }

    public void start() {
        stopTasks();
        long interval = Math.max(100L, plugin.getConfig().getLong("settings.civic.automation-interval-ticks", 1200L));
        automationTask = Bukkit.getScheduler().runTaskTimer(plugin, this::runAutomation, interval, interval);
        previewTask = Bukkit.getScheduler().runTaskTimer(plugin, this::renderSelections, 20L, 20L);
        Bukkit.getServicesManager().unregister(TownyBuildsApi.class, this);
        Bukkit.getServicesManager().register(TownyBuildsApi.class, this, plugin, ServicePriority.Normal);
    }

    public void stop() {
        stopTasks();
        selections.clear();
        Bukkit.getServicesManager().unregister(TownyBuildsApi.class, this);
    }

    private void stopTasks() {
        if (automationTask != null) automationTask.cancel();
        if (previewTask != null) previewTask.cancel();
        automationTask = null;
        previewTask = null;
    }

    public void handle(Player player, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            status(player);
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> help(player);
            case "select" -> select(player, args);
            case "storage" -> openStorage(player, args);
            case "shop" -> shop(player, args);
            case "insurance" -> insurance(player, args);
            case "bulletin" -> bulletin(player, args);
            default -> help(player);
        }
    }

    public void setStall(Player player, String rawId) {
        String id = normalizeId(rawId);
        if (id.isBlank()) {
            messages.send(player, "civic-invalid-stall");
            return;
        }
        Location location = player.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
        String root = "settings.civic.spawn-shops.stalls." + id;
        plugin.getConfig().set(root + ".world", location.getWorld().getUID().toString());
        plugin.getConfig().set(root + ".x", location.getX());
        plugin.getConfig().set(root + ".y", location.getY());
        plugin.getConfig().set(root + ".z", location.getZ());
        plugin.getConfig().set(root + ".yaw", player.getLocation().getYaw());
        plugin.saveConfig();
        messages.send(player, "civic-stall-set", Map.of("stall", id));
    }

    public void removeStall(org.bukkit.command.CommandSender sender, String rawId) {
        String id = normalizeId(rawId);
        if (stallLocation(id) == null) {
            messages.send(sender, "civic-stall-missing", Map.of("stall", id));
            return;
        }
        plugin.getConfig().set("settings.civic.spawn-shops.stalls." + id, null);
        plugin.saveConfig();
        for (TownData data : dataStore.towns().values()) {
            if (data.shopStall().equals(id)) data.setShopStall("");
        }
        dataStore.markDirty();
        dataStore.save();
        messages.send(sender, "civic-stall-removed", Map.of("stall", id));
    }

    public List<String> stallIds() {
        var section = plugin.getConfig().getConfigurationSection("settings.civic.spawn-shops.stalls");
        return section == null ? List.of() : section.getKeys(false).stream().sorted().toList();
    }

    private void help(Player player) {
        messages.send(player, "civic-help");
    }

    private Town requireTown(Player player) {
        Town town = towny.town(player);
        if (town == null) messages.send(player, "no-town");
        return town;
    }

    private boolean requireMayor(Player player, Town town) {
        if (towny.isMayor(player, town)) return true;
        messages.send(player, "civic-only-mayor");
        return false;
    }

    private boolean requireProject(Player player, TownData data, String projectId) {
        if (data.operationalLevel(projectId) > 0) return true;
        if(data.level(projectId)>0){player.sendMessage(ColorUtil.component("&cЗдание НЕАКТИВНО. Содержание: /t upkeep; энергия: /t power"));return false;}
        messages.send(player, "civic-project-required", Map.of("project", projectName(projectId)));
        return false;
    }

    private void select(Player player, String[] args) {
        if (args.length < 3) {
            messages.send(player, "civic-select-help");
            return;
        }
        Town town = requireTown(player);
        if (town == null || !requireMayor(player, town)) return;
        String projectId = projectId(args[1]);
        if (!AREA_PROJECTS.contains(projectId) && !LINE_PROJECTS.contains(projectId)) {
            messages.send(player, "civic-select-project");
            return;
        }
        TownData data = dataStore.town(town.getUUID());
        if (!requireProject(player, data, projectId)) return;
        String action = args[2].toLowerCase(Locale.ROOT);
        if (action.equals("clear")) {
            data.setCivicArea(projectId, null);
            data.setCivicLine(projectId, null);
            selections.remove(player.getUniqueId());
            dataStore.markDirty();
            dataStore.save();
            messages.send(player, "civic-selection-cleared", Map.of("project", projectName(projectId)));
            return;
        }
        DraftSelection draft = selections.computeIfAbsent(player.getUniqueId(), ignored -> new DraftSelection(projectId));
        if (!draft.projectId.equals(projectId)) {
            draft = new DraftSelection(projectId);
            selections.put(player.getUniqueId(), draft);
        }
        if (action.equals("pos1") || action.equals("pos2")) {
            Location point = selectionLocation(player);
            if (action.equals("pos1")) draft.first = point; else draft.second = point;
            messages.send(player, "civic-selection-point", Map.of(
                    "point", action.substring(3), "x", point.getBlockX(), "y", point.getBlockY(), "z", point.getBlockZ()));
            return;
        }
        if (!action.equals("save")) {
            messages.send(player, "civic-select-help");
            return;
        }
        saveSelection(player, town, data, draft);
    }

    private Location selectionLocation(Player player) {
        Block target = player.getTargetBlockExact(16, FluidCollisionMode.NEVER);
        return target == null ? player.getLocation().getBlock().getLocation()
                : target.getLocation().add(0, 1, 0);
    }

    private void saveSelection(Player player, Town town, TownData data, DraftSelection draft) {
        if (draft == null || draft.first == null || draft.second == null) {
            messages.send(player, "civic-selection-incomplete");
            return;
        }
        if (!draft.first.getWorld().equals(draft.second.getWorld())) {
            messages.send(player, "civic-selection-world");
            return;
        }
        int level = data.operationalLevel(draft.projectId);
        if (AREA_PROJECTS.contains(draft.projectId)) {
            CivicArea area = new CivicArea(draft.first.getWorld().getUID(),
                    Math.min(draft.first.getBlockX(), draft.second.getBlockX()),
                    Math.max(draft.first.getBlockX(), draft.second.getBlockX()),
                    Math.min(draft.first.getBlockZ(), draft.second.getBlockZ()),
                    Math.max(draft.first.getBlockZ(), draft.second.getBlockZ()));
            int maximumChunks = level * level;
            if (draft.projectId.equals("forestry")) maximumChunks += data.operationalLevel("world_tree") * 12;
            if (draft.projectId.equals("irrigation_station")) maximumChunks += data.operationalLevel("great_canal") * 8;
            if (area.chunkCount() > maximumChunks) {
                messages.send(player, "civic-selection-too-large", Map.of("current", area.chunkCount(), "maximum", maximumChunks));
                return;
            }
            if (!ownedArea(town, draft.first.getWorld(), area)) {
                messages.send(player, "civic-selection-outside");
                return;
            }
            data.setCivicArea(draft.projectId, area);
            messages.send(player, "civic-area-saved", Map.of("project", projectName(draft.projectId), "chunks", area.chunkCount()));
        } else {
            CivicLine line = new CivicLine(draft.first.getWorld().getUID(),
                    draft.first.getBlockX(), draft.first.getBlockY(), draft.first.getBlockZ(),
                    draft.second.getBlockX(), draft.second.getBlockY(), draft.second.getBlockZ());
            int maximum = Math.max(16, plugin.getConfig().getInt("settings.civic.linear-blocks-per-level", 32)) * level;
            if (draft.projectId.equals("dam")) maximum += data.operationalLevel("great_canal") * 256;
            if (line.length() > maximum) {
                messages.send(player, "civic-line-too-long", Map.of("current", line.length(), "maximum", maximum));
                return;
            }
            if (!ownedLine(town, draft.first.getWorld(), line)) {
                messages.send(player, "civic-selection-outside");
                return;
            }
            data.setCivicLine(draft.projectId, line);
            messages.send(player, "civic-line-saved", Map.of("project", projectName(draft.projectId), "length", line.length()));
        }
        selections.remove(player.getUniqueId());
        dataStore.markDirty();
        dataStore.save();
    }

    private boolean ownedArea(Town town, World world, CivicArea area) {
        for (int chunkX = Math.floorDiv(area.minX(), 16); chunkX <= Math.floorDiv(area.maxX(), 16); chunkX++) {
            for (int chunkZ = Math.floorDiv(area.minZ(), 16); chunkZ <= Math.floorDiv(area.maxZ(), 16); chunkZ++) {
                Town owner = towny.townAt(new Location(world, chunkX * 16 + 8, world.getMinHeight(), chunkZ * 16 + 8));
                if (owner == null || !owner.getUUID().equals(town.getUUID())) return false;
            }
        }
        return true;
    }

    private boolean ownedLine(Town town, World world, CivicLine line) {
        int steps = Math.max(Math.abs(line.x2() - line.x1()), Math.abs(line.z2() - line.z1()));
        for (int step = 0; step <= steps; step++) {
            double fraction = steps == 0 ? 0 : (double) step / steps;
            int x = (int) Math.round(line.x1() + (line.x2() - line.x1()) * fraction);
            int z = (int) Math.round(line.z1() + (line.z2() - line.z1()) * fraction);
            Town owner = towny.townAt(new Location(world, x, world.getMinHeight(), z));
            if (owner == null || !owner.getUUID().equals(town.getUUID())) return false;
        }
        return true;
    }

    private void openStorage(Player player, String[] args) {
        if(args.length<2){player.sendMessage("/t civic storage <здание>");return;}
        ((ru.neverland.townybuilds.NeverLandTownyBuilds)plugin).storage().openStorage(player,projectId(args[1]));
    }

    private int inventorySize(String projectId, int level) {
        if (projectId.equals("forestry")) return level >= 5 ? 54 : Math.max(9, level * 9);
        return Math.max(9, Math.min(54, (level + 1) * 9));
    }

    private void insurance(Player player, String[] args) {
        Town town = requireTown(player);
        if (town == null) return;
        TownData data = dataStore.town(town.getUUID());
        if (!requireProject(player, data, "insurance_chamber")) return;
        if (args.length == 1 || args[1].equalsIgnoreCase("status")) {
            messages.send(player, "civic-insurance-status", Map.of("amount", MONEY.format(data.insuranceReserve()),
                    "coverage", Math.round(benefit(town.getUUID(), CivicBenefit.INSURANCE_COVERAGE) * 100)));
            return;
        }
        if (args.length < 3 || !args[1].equalsIgnoreCase("deposit") || !requireMayor(player, town)) {
            messages.send(player, "civic-insurance-help");
            return;
        }
        try {
            double amount = Double.parseDouble(args[2].replace(',', '.'));
            if (!Double.isFinite(amount) || amount <= 0) throw new NumberFormatException();
            if (!town.getAccount().canPayFromHoldings(amount)
                    || !town.getAccount().withdraw(amount, "Пополнение страхового резерва")) {
                messages.send(player, "not-enough-money", Map.of("amount", MONEY.format(amount)));
                return;
            }
            data.setInsuranceReserve(data.insuranceReserve() + amount);
            dataStore.markDirty();
            dataStore.save();
            messages.send(player, "civic-insurance-deposited", Map.of("amount", MONEY.format(amount),
                    "reserve", MONEY.format(data.insuranceReserve())));
        } catch (NumberFormatException exception) {
            messages.send(player, "civic-invalid-number");
        }
    }

    private void bulletin(Player player, String[] args) {
        Town town = requireTown(player);
        if (town == null || !requireMayor(player, town)) return;
        TownData data = dataStore.town(town.getUUID());
        if (!requireProject(player, data, "printing_house")) return;
        if (args.length < 2) {
            messages.send(player, "civic-bulletin-help");
            return;
        }
        String text = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))
                .replace('&', '＆').strip();
        int maximum = 40 + data.operationalLevel("printing_house") * 40;
        if (text.length() > maximum) text = text.substring(0, maximum);
        data.setBulletin(text);
        dataStore.markDirty();
        dataStore.save();
        for (Player online : Bukkit.getOnlinePlayers()) {
            Town onlineTown = towny.town(online);
            if (onlineTown != null && onlineTown.getUUID().equals(town.getUUID())) {
                online.sendMessage(ColorUtil.component("&#E7D7A5&lБюллетень " + town.getName() + " &8» &f" + text));
            }
        }
    }

    private void shop(Player player, String[] args) {
        if (args.length == 1 || args[1].equalsIgnoreCase("browse")) {
            openNearestShop(player);
            return;
        }
        Town town = requireTown(player);
        if (town == null) return;
        TownData data = dataStore.town(town.getUUID());
        if (!requireProject(player, data, "merchant_guild")) return;
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "stock" -> openCivicStorage(player, town, data, "merchant_guild");
            case "claim" -> claimShop(player, town, data, args);
            case "release" -> releaseShop(player, town, data);
            case "price" -> setShopPrice(player, town, data, args);
            case "list" -> listShop(player, town, data);
            default -> messages.send(player, "civic-shop-help");
        }
    }

    private void openCivicStorage(Player player, Town town, TownData data, String projectId) {
        ((ru.neverland.townybuilds.NeverLandTownyBuilds)plugin).storage().openStorage(player,projectId);
    }

    private void claimShop(Player player, Town town, TownData data, String[] args) {
        if (!requireMayor(player, town)) return;
        if (data.operationalLevel("merchant_guild") < 3) {
            messages.send(player, "civic-shop-level");
            return;
        }
        if (args.length < 3) {
            messages.send(player, "civic-shop-help");
            return;
        }
        String id = normalizeId(args[2]);
        Location stall = stallLocation(id);
        if (stall == null) {
            messages.send(player, "civic-stall-missing", Map.of("stall", id));
            return;
        }
        double radius = Math.max(2, plugin.getConfig().getDouble("settings.civic.spawn-shops.claim-radius", 8));
        if (!stall.getWorld().equals(player.getWorld()) || stall.distanceSquared(player.getLocation()) > radius * radius) {
            messages.send(player, "civic-shop-not-at-stall", Map.of("stall", id));
            return;
        }
        for (TownData other : dataStore.towns().values()) {
            if (!other.townId().equals(town.getUUID()) && other.shopStall().equals(id)) {
                messages.send(player, "civic-shop-occupied", Map.of("stall", id));
                return;
            }
        }
        data.setShopStall(id);
        dataStore.markDirty();
        dataStore.save();
        messages.send(player, "civic-shop-claimed", Map.of("stall", id));
    }

    private void releaseShop(Player player, Town town, TownData data) {
        if (!requireMayor(player, town)) return;
        data.setShopStall("");
        dataStore.markDirty();
        dataStore.save();
        messages.send(player, "civic-shop-released");
    }

    private void setShopPrice(Player player, Town town, TownData data, String[] args) {
        if (!requireMayor(player, town)) return;
        if (args.length < 4) {
            messages.send(player, "civic-shop-help");
            return;
        }
        Material material = MaterialNameConfig.matchMaterial(args[2]);
        if (material == null || !material.isItem() || !allowedMaterials().contains(material)) {
            messages.send(player, "civic-shop-material");
            return;
        }
        try {
            double price = Double.parseDouble(args[3].replace(',', '.'));
            double maximum = Math.max(1, plugin.getConfig().getDouble("settings.civic.spawn-shops.maximum-unit-price", 1_000_000));
            if (!Double.isFinite(price) || price < 0 || price > maximum) throw new NumberFormatException();
            int limit = 4 + data.operationalLevel("merchant_guild") * 4 + data.operationalLevel("crystal_palace") * 16;
            if (price > 0 && data.shopPrice(material.name()) <= 0 && data.shopPrices().size() >= limit) {
                messages.send(player, "civic-shop-listing-limit", Map.of("limit", limit));
                return;
            }
            data.setShopPrice(material.name(), price);
            dataStore.markDirty();
            dataStore.save();
            messages.send(player, price <= 0 ? "civic-shop-price-removed" : "civic-shop-price-set", Map.of(
                    "material", itemNames.name(material), "price", MONEY.format(price)));
        } catch (NumberFormatException exception) {
            messages.send(player, "civic-invalid-number");
        }
    }

    private void listShop(Player player, Town town, TownData data) {
        player.sendMessage(ColorUtil.component("&#FFD45B&lЛавка города " + town.getName()
                + " &8• &f" + (data.shopStall().isBlank() ? "место не занято" : data.shopStall())));
        if (data.shopPrices().isEmpty()) {
            player.sendMessage(ColorUtil.component("&7Товары ещё не выставлены."));
            return;
        }
        data.shopPrices().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                player.sendMessage(ColorUtil.component("&8• &f" + itemNames.name(MaterialNameConfig.matchMaterial(entry.getKey())) + " &7— &e"
                        + MONEY.format(entry.getValue()) + " &7за шт.")));
    }

    private void openNearestShop(Player player) {
        TownData nearest = null;
        double best = Double.MAX_VALUE;
        double radius = Math.max(2, plugin.getConfig().getDouble("settings.civic.spawn-shops.browse-radius", 8));
        for (TownData data : dataStore.towns().values()) {
            if (data.shopStall().isBlank() || data.operationalLevel("merchant_guild")==0) continue;
            Location location = stallLocation(data.shopStall());
            if (location == null || !location.getWorld().equals(player.getWorld())) continue;
            double distance = location.distanceSquared(player.getLocation());
            if (distance <= radius * radius && distance < best) {
                best = distance;
                nearest = data;
            }
        }
        if (nearest == null) {
            messages.send(player, "civic-shop-none-nearby");
            return;
        }
        openShop(player, nearest);
    }

    private void openShop(Player player, TownData sellerData) {
        if(sellerData.operationalLevel("merchant_guild")==0){player.sendMessage(ColorUtil.component("&cЛавка временно не работает: проверьте содержание и питание гильдии."));return;}
        Town seller = towny.town(sellerData.townId());
        if (seller == null) {
            messages.send(player, "civic-shop-unavailable");
            return;
        }
        Map<Integer, Material> displayed = new LinkedHashMap<>();
        Inventory inventory = Bukkit.createInventory(new ShopHolder(sellerData.townId(), displayed), 54,
                ColorUtil.component("&#FFD45BЛавка &8• &f" + seller.getName()));
        ItemStack[] stock = sellerData.civicInventory("merchant_guild", 54);
        int slot = 10;
        for (Map.Entry<String, Double> listing : sellerData.shopPrices().entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            Material material = MaterialNameConfig.matchMaterial(listing.getKey());
            if (material == null || listing.getValue() <= 0) continue;
            int amount = countPlain(stock, material);
            if (amount <= 0) continue;
            while (slot % 9 == 0 || slot % 9 == 8) slot++;
            if (slot >= 44) break;
            ItemStack icon = new ItemStack(material, Math.min(amount, material.getMaxStackSize()));
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(ColorUtil.component(itemNames.name(material)));
            meta.lore(List.of(
                    ColorUtil.component("&7Цена за единицу: &e" + MONEY.format(listing.getValue())),
                    ColorUtil.component("&7В наличии: &f" + amount),
                    ColorUtil.component("&#63E6BEЛКМ: 1 &8• &#63E6BEShift+ЛКМ: стак")
            ));
            icon.setItemMeta(meta);
            inventory.setItem(slot, icon);
            displayed.put(slot, material);
            slot++;
        }
        player.openInventory(inventory);
    }

    private Location stallLocation(String id) {
        String root = "settings.civic.spawn-shops.stalls." + id;
        String rawWorld = plugin.getConfig().getString(root + ".world", "");
        if (rawWorld.isBlank()) return null;
        try {
            World world = Bukkit.getWorld(UUID.fromString(rawWorld));
            if (world == null) return null;
            return new Location(world, plugin.getConfig().getDouble(root + ".x"),
                    plugin.getConfig().getDouble(root + ".y"), plugin.getConfig().getDouble(root + ".z"),
                    (float) plugin.getConfig().getDouble(root + ".yaw"), 0);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof ShopHolder shop) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0
                    || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
            Material material = shop.displayed.get(event.getRawSlot());
            if (material != null) buy(player, shop.townId, material, event.isShiftClick());
            return;
        }
        if (!(holder instanceof CivicStorageHolder storage)) return;
        if (!storage.mayor && (event.getRawSlot() < event.getView().getTopInventory().getSize()
                || event.getClick() == ClickType.DOUBLE_CLICK)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) messages.send(player, "storage-withdraw-denied");
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ShopHolder) event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof CivicStorageHolder holder)) return;
        TownData data = dataStore.town(holder.townId);
        data.setCivicInventory(holder.projectId, event.getInventory().getContents());
        dataStore.markDirty();
    }

    private void buy(Player player, UUID sellerTownId, Material material, boolean stack) {
        if(dataStore.town(sellerTownId).operationalLevel("merchant_guild")==0){player.sendMessage(ColorUtil.component("&cЛавка временно не работает."));return;}
        if(dataStore.storageBusy(sellerTownId,"merchant_guild")){player.sendMessage("Склад гильдии открыт; покупка временно недоступна.");return;}
        TownData sellerData = dataStore.town(sellerTownId);
        Town seller = towny.town(sellerTownId);
        Resident buyer = towny.resident(player);
        double unitPrice = sellerData.shopPrice(material.name());
        if (seller == null || buyer == null || unitPrice <= 0 || !allowedMaterials().contains(material)) {
            messages.send(player, "civic-shop-unavailable");
            return;
        }
        ItemStack[] stock = sellerData.civicInventory("merchant_guild", 54);
        int available = countPlain(stock, material);
        int amount = Math.min(available, stack ? material.getMaxStackSize() : 1);
        if (amount <= 0) {
            messages.send(player, "civic-shop-sold-out");
            return;
        }
        ItemStack purchased = new ItemStack(material, amount);
        if (!canFit(player.getInventory().getStorageContents(), purchased)) {
            messages.send(player, "civic-shop-inventory-full");
            return;
        }
        double total = unitPrice * amount;
        if (!Double.isFinite(total) || total <= 0 || !buyer.getAccount().canPayFromHoldings(total)) {
            messages.send(player, "not-enough-money", Map.of("amount", MONEY.format(total)));
            return;
        }
        TownShopTradeEvent tradeEvent = new TownShopTradeEvent(player.getUniqueId(), sellerTownId, purchased, total);
        Bukkit.getPluginManager().callEvent(tradeEvent);
        if (tradeEvent.isCancelled()) {
            messages.send(player, "civic-shop-cancelled");
            return;
        }
        ItemStack[] changed = cloneContents(stock);
        if (removePlain(changed, material, amount) != 0
                || !buyer.getAccount().withdraw(total, "Покупка в лавке города " + seller.getName())) {
            messages.send(player, "not-enough-money", Map.of("amount", MONEY.format(total)));
            return;
        }
        if (!seller.getAccount().deposit(total, "Продажа в городской лавке")) {
            buyer.getAccount().deposit(total, "Возврат: лавка города недоступна");
            messages.send(player, "civic-shop-unavailable");
            return;
        }
        sellerData.setCivicInventory("merchant_guild", changed);
        player.getInventory().addItem(purchased);
        dataStore.markDirty();
        dataStore.save();
        messages.send(player, "civic-shop-purchased", Map.of("amount", amount, "material", itemNames.name(material),
                "price", MONEY.format(total), "town", seller.getName()));
        openShop(player, sellerData);
    }

    private Set<Material> allowedMaterials() {
        Set<Material> configured = new java.util.LinkedHashSet<>();
        for (String value : plugin.getConfig().getStringList("settings.civic.spawn-shops.allowed-materials")) {
            Material material = MaterialNameConfig.matchMaterial(value);
            if (material != null && material.isItem()) configured.add(material);
        }
        return Set.copyOf(configured);
    }

    private int countPlain(ItemStack[] contents, Material material) {
        int result = 0;
        for (ItemStack item : contents) if (plain(item, material)) result += item.getAmount();
        return result;
    }

    private int removePlain(ItemStack[] contents, Material material, int requested) {
        int remaining = requested;
        for (int index = 0; index < contents.length && remaining > 0; index++) {
            ItemStack item = contents[index];
            if (!plain(item, material)) continue;
            int removed = Math.min(item.getAmount(), remaining);
            remaining -= removed;
            if (removed == item.getAmount()) contents[index] = null; else item.setAmount(item.getAmount() - removed);
        }
        return remaining;
    }

    private boolean plain(ItemStack item, Material material) {
        if (item == null || item.getType() != material || !allowedMaterials().contains(material)) return false;
        if (!item.hasItemMeta()) return true;
        ItemMeta meta = item.getItemMeta();
        return !meta.hasDisplayName() && !meta.hasLore() && meta.getEnchants().isEmpty()
                && meta.getPersistentDataContainer().isEmpty();
    }

    private boolean canFit(ItemStack[] contents, ItemStack item) {
        int capacity = 0;
        for (ItemStack slot : contents) {
            if (slot == null || slot.getType().isAir()) capacity += item.getMaxStackSize();
            else if (slot.isSimilar(item)) capacity += Math.max(0, slot.getMaxStackSize() - slot.getAmount());
            if (capacity >= item.getAmount()) return true;
        }
        return false;
    }

    private ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int index = 0; index < contents.length; index++) copy[index] = contents[index] == null ? null : contents[index].clone();
        return copy;
    }

    private void status(Player player) {
        Town town = requireTown(player);
        if (town == null) return;
        TownData data = dataStore.town(town.getUUID());
        player.sendMessage(ColorUtil.component("&#63E6BE&lМуниципальные службы " + town.getName()));
        player.sendMessage(ColorUtil.component("&7Водная цепочка: " + (waterNetworkActive(town.getUUID()) ? "&aактивна" : "&cнеполна")
                + " &8(башня → резервуар → насосная)"));
        CivicArea forest = data.civicArea("forestry");
        CivicArea irrigation = data.civicArea("irrigation_station");
        player.sendMessage(ColorUtil.component("&7Лесничество: &f" + (forest == null ? "участок не задан" : forest.chunkCount() + " чанков")
                + " &8• &7Ирригация: &f" + (irrigation == null ? "участок не задан" : irrigation.chunkCount() + " чанков")));
        player.sendMessage(ColorUtil.component("&7Лавка: &f" + (data.shopStall().isBlank() ? "не открыта" : data.shopStall())
                + " &8• &7Страховой резерв: &e" + MONEY.format(data.insuranceReserve())));
        if (data.operationalLevel("census_bureau") > 0) {
            player.sendMessage(ColorUtil.component("&7Перепись: &f" + town.getResidents().size() + " жителей"));
        }
        if (!data.bulletin().isBlank()) {
            player.sendMessage(ColorUtil.component("&7Последний бюллетень: &f" + data.bulletin()));
        }
        for (String project : LINE_PROJECTS) {
            CivicLine line = data.civicLine(project);
            if (line != null) player.sendMessage(ColorUtil.component("&7" + projectName(project) + ": &f" + line.length() + " блоков"));
        }
    }

    private void runAutomation() {
        for (Map.Entry<UUID, TownData> entry : dataStore.towns().entrySet()) {
            Town town = towny.town(entry.getKey());
            if (town == null) continue;
            TownData data = entry.getValue();
            runForestry(town, data);
            runIrrigation(town, data);
            runRecycling(data);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            Town town = towny.town(player);
            if (town == null || !(player.getVehicle() instanceof AbstractHorse horse)) continue;
            int level = dataStore.town(town.getUUID()).operationalLevel("stables");
            if (level > 0 && town.equals(towny.townAt(player.getLocation()))) {
                horse.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                        Math.max(140, plugin.getConfig().getInt("settings.civic.automation-interval-ticks", 1200) + 40),
                        level >= 4 ? 1 : 0, true, false, true));
            }
        }
        dataStore.saveIfDirty();
    }

    private void runForestry(Town town, TownData data) {
        if(dataStore.storageBusy(data.townId(),"forestry")||dataStore.storageBusy(data.townId(),"warehouse"))return;
        int level = data.operationalLevel("forestry");
        CivicArea area = data.civicArea("forestry");
        if (level <= 0 || area == null) return;
        World world = Bukkit.getWorld(area.worldId());
        if (world == null) return;
        int planted = 0;
        int attempts = Math.min(640, 80 * level);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int plantingLimit = ru.neverland.integration.DistrictBonuses.output(level * 3 + data.operationalLevel("world_tree") * 8,
                ru.neverland.integration.DistrictBonuses.multiplier(data.townId(), "forestry"));
        for (int attempt = 0; attempt < attempts && planted < plantingLimit; attempt++) {
            int x = random.nextInt(area.minX(), area.maxX() + 1);
            int z = random.nextInt(area.minZ(), area.maxZ() + 1);
            if (!world.isChunkLoaded(Math.floorDiv(x, 16), Math.floorDiv(z, 16))) continue;
            int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Block ground = world.getBlockAt(x, y, z);
            Block above = ground.getRelative(org.bukkit.block.BlockFace.UP);
            if (!PLANTABLE_GROUND.contains(ground.getType()) || !above.getType().isAir()) continue;
            Town owner = towny.townAt(ground.getLocation());
            if (owner == null || !owner.getUUID().equals(town.getUUID()) || nearbyTree(above)) continue;
            Material sapling = takeSapling(data);
            if (sapling == null) break;
            above.setType(sapling, false);
            planted++;
        }
        if (planted > 0) dataStore.markDirty();
    }

    private boolean nearbyTree(Block center) {
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) for (int y = -1; y <= 3; y++) {
            Material type = center.getRelative(x, y, z).getType();
            if (type.name().endsWith("_LOG") || type.name().endsWith("_SAPLING") || type == Material.MANGROVE_PROPAGULE) return true;
        }
        return false;
    }

    private Material takeSapling(TownData data) {
        ItemStack[] forestry = data.civicInventory("forestry", 54);
        Material found = takeFirst(forestry, SAPLINGS);
        if (found != null) {
            data.setCivicInventory("forestry", forestry);
            return found;
        }
        if(Bukkit.getPluginManager().isPluginEnabled("NeverLandTownyLogistics"))return null;
        ItemStack[] city = data.storage();
        found = takeFirst(city, SAPLINGS);
        if (found != null) data.setStorage(city, plugin.getConfig().getInt("settings.storage.size", 54));
        return found;
    }

    private Material takeFirst(ItemStack[] contents, Set<Material> allowed) {
        for (int index = 0; index < contents.length; index++) {
            ItemStack item = contents[index];
            if (item == null || !allowed.contains(item.getType())) continue;
            Material found = item.getType();
            if (item.getAmount() == 1) contents[index] = null; else item.setAmount(item.getAmount() - 1);
            return found;
        }
        return null;
    }

    private void runIrrigation(Town town, TownData data) {
        int level = data.operationalLevel("irrigation_station");
        CivicArea area = data.civicArea("irrigation_station");
        if (level <= 0 || area == null || !waterNetworkActive(town.getUUID())) return;
        World world = Bukkit.getWorld(area.worldId());
        if (world == null) return;
        int hydrated = 0;
        int hydrationLimit = ru.neverland.integration.DistrictBonuses.output(level * 32,
                ru.neverland.integration.ResearchBonuses.production(data.townId(),"irrigation_station",ru.neverland.integration.DistrictBonuses.multiplier(data.townId(), "irrigation_station")));
        int attempts = Math.min(1000, 140 * level);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < attempts && hydrated < hydrationLimit; attempt++) {
            int x = random.nextInt(area.minX(), area.maxX() + 1);
            int z = random.nextInt(area.minZ(), area.maxZ() + 1);
            if (!world.isChunkLoaded(Math.floorDiv(x, 16), Math.floorDiv(z, 16))) continue;
            int top = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
            for (int y = top; y >= Math.max(world.getMinHeight(), top - 4); y--) {
                Block block = world.getBlockAt(x, y, z);
                if (block.getBlockData() instanceof Farmland farmland && farmland.getMoisture() < farmland.getMaximumMoisture()) {
                    Town owner = towny.townAt(block.getLocation());
                    if (owner != null && owner.getUUID().equals(town.getUUID())) {
                        farmland.setMoisture(farmland.getMaximumMoisture());
                        block.setBlockData(farmland, false);
                        hydrated++;
                    }
                    break;
                }
            }
        }
    }

    private void runRecycling(TownData data) {
        if(dataStore.storageBusy(data.townId(),"recycling_yard"))return;
        int level = data.operationalLevel("recycling_yard");
        if (level <= 0) return;
        ItemStack[] inventory = data.civicInventory("recycling_yard", 54);
        boolean changed = false;
        for (RecycleRecipe recipe : RECIPES) {
            int operations = Math.min(level, countMaterial(inventory, recipe.input) / recipe.inputAmount);
            if (operations <= 0) continue;
            ItemStack result = new ItemStack(recipe.output, ru.neverland.integration.DistrictBonuses.output(operations * recipe.outputAmount,
                    ru.neverland.integration.DistrictBonuses.multiplier(data.townId(), "recycling_yard")));
            if (!canFit(inventory, result)) continue;
            removeMaterial(inventory, recipe.input, operations * recipe.inputAmount);
            addMaterial(inventory, result);
            changed = true;
        }
        if (changed) {
            data.setCivicInventory("recycling_yard", inventory);
            dataStore.markDirty();
        }
    }

    private int countMaterial(ItemStack[] inventory, Material material) {
        int count = 0;
        for (ItemStack item : inventory) if (item != null && item.getType() == material) count += item.getAmount();
        return count;
    }

    private void removeMaterial(ItemStack[] inventory, Material material, int amount) {
        int remaining = amount;
        for (int index = 0; index < inventory.length && remaining > 0; index++) {
            ItemStack item = inventory[index];
            if (item == null || item.getType() != material) continue;
            int removed = Math.min(remaining, item.getAmount());
            remaining -= removed;
            if (removed == item.getAmount()) inventory[index] = null; else item.setAmount(item.getAmount() - removed);
        }
    }

    private void addMaterial(ItemStack[] inventory, ItemStack result) {
        int remaining = result.getAmount();
        for (ItemStack item : inventory) {
            if (item == null || !item.isSimilar(result) || item.getAmount() >= item.getMaxStackSize()) continue;
            int added = Math.min(remaining, item.getMaxStackSize() - item.getAmount());
            item.setAmount(item.getAmount() + added);
            remaining -= added;
            if (remaining == 0) return;
        }
        for (int index = 0; index < inventory.length && remaining > 0; index++) {
            if (inventory[index] != null) continue;
            ItemStack stack = result.clone();
            stack.setAmount(Math.min(remaining, stack.getMaxStackSize()));
            inventory[index] = stack;
            remaining -= stack.getAmount();
        }
    }

    private void renderSelections() {
        for (Map.Entry<UUID, DraftSelection> entry : new ArrayList<>(selections.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                selections.remove(entry.getKey());
                continue;
            }
            DraftSelection draft = entry.getValue();
            if (draft.first != null) marker(player, draft.first, Particle.END_ROD);
            if (draft.second != null) marker(player, draft.second, Particle.HAPPY_VILLAGER);
            if (draft.first != null && draft.second != null && draft.first.getWorld().equals(draft.second.getWorld())) {
                if (AREA_PROJECTS.contains(draft.projectId)) drawRectangle(player, draft.first, draft.second);
                else drawLine(player, draft.first, draft.second);
            }
        }
    }

    private void marker(Player player, Location location, Particle particle) {
        if (!location.getWorld().equals(player.getWorld())) return;
        player.spawnParticle(particle, location.clone().add(0.5, 0.2, 0.5), 3, 0.2, 0.2, 0.2, 0);
    }

    private void drawRectangle(Player player, Location first, Location second) {
        int minX = Math.min(first.getBlockX(), second.getBlockX());
        int maxX = Math.max(first.getBlockX(), second.getBlockX());
        int minZ = Math.min(first.getBlockZ(), second.getBlockZ());
        int maxZ = Math.max(first.getBlockZ(), second.getBlockZ());
        double y = Math.max(first.getY(), second.getY()) + 0.2;
        int stride = Math.max(1, Math.max(maxX - minX, maxZ - minZ) / 24);
        for (int x = minX; x <= maxX; x += stride) {
            particle(player, first.getWorld(), x, y, minZ);
            particle(player, first.getWorld(), x, y, maxZ);
        }
        for (int z = minZ; z <= maxZ; z += stride) {
            particle(player, first.getWorld(), minX, y, z);
            particle(player, first.getWorld(), maxX, y, z);
        }
    }

    private void drawLine(Player player, Location first, Location second) {
        int steps = Math.max(Math.abs(first.getBlockX() - second.getBlockX()), Math.abs(first.getBlockZ() - second.getBlockZ()));
        int stride = Math.max(1, steps / 32);
        for (int step = 0; step <= steps; step += stride) {
            double fraction = steps == 0 ? 0 : (double) step / steps;
            particle(player, first.getWorld(), first.getX() + (second.getX() - first.getX()) * fraction,
                    first.getY() + (second.getY() - first.getY()) * fraction + 0.2,
                    first.getZ() + (second.getZ() - first.getZ()) * fraction);
        }
    }

    private void particle(Player player, World world, double x, double y, double z) {
        if (world.equals(player.getWorld())) player.spawnParticle(Particle.END_ROD, x + 0.5, y, z + 0.5, 1, 0, 0, 0, 0);
    }

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

    private double benefitLevel(TownData data,String project) {
        return data.operationalLevel(project)*ru.neverland.integration.DistrictBonuses.multiplier(data.townId(),project);
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
            data.setInsuranceReserve(data.insuranceReserve() - paid);
            dataStore.markDirty();
            dataStore.save();
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

    private String projectId(String raw) {
        String normalized = raw.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "лесничество" -> "forestry";
            case "ирригация" -> "irrigation_station";
            case "стена" -> "fortress_wall";
            case "ров" -> "city_moat";
            case "дамба" -> "dam";
            case "гильдия" -> "merchant_guild";
            case "переработка" -> "recycling_yard";
            default -> normalizeId(normalized);
        };
    }

    private String normalizeId(String raw) {
        return raw == null ? "" : raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
    }

    private String projectName(String id) {
        return switch (id) {
            case "printing_house" -> "Типография";
            case "forestry" -> "Лесничество";
            case "customs" -> "Таможня";
            case "trade_port" -> "Торговый порт";
            case "mint" -> "Монетный двор";
            case "merchant_guild" -> "Гильдия торговцев";
            case "stables" -> "Конюшни";
            case "fortress_wall" -> "Крепостная стена";
            case "city_moat" -> "Городской ров";
            case "archery_range" -> "Стрельбище";
            case "port_fort" -> "Портовый форт";
            case "census_bureau" -> "Бюро переписи";
            case "insurance_chamber" -> "Страховая палата";
            case "pumping_station" -> "Насосная станция";
            case "irrigation_station" -> "Ирригационная станция";
            case "recycling_yard" -> "Перерабатывающий двор";
            case "dam" -> "Дамба";
            default -> id;
        };
    }

    private record RecycleRecipe(Material input, int inputAmount, Material output, int outputAmount) { }

    private static final class DraftSelection {
        private final String projectId;
        private Location first;
        private Location second;
        private DraftSelection(String projectId) { this.projectId = projectId; }
    }

    private interface CivicHolder extends InventoryHolder {
        @Override default Inventory getInventory() { throw new UnsupportedOperationException("Marker holder"); }
    }

    private record CivicStorageHolder(UUID townId, String projectId, boolean mayor) implements CivicHolder { }
    private record ShopHolder(UUID townId, Map<Integer, Material> displayed) implements CivicHolder { }
}
