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

/** Scheduled world work. The public API and shop do not own this task. */
public final class CivicAutomationService {
    private static final Set<Material> SAPLINGS = Set.of(
            Material.OAK_SAPLING, Material.BIRCH_SAPLING, Material.SPRUCE_SAPLING,
            Material.JUNGLE_SAPLING, Material.ACACIA_SAPLING, Material.DARK_OAK_SAPLING,
            Material.CHERRY_SAPLING, Material.MANGROVE_PROPAGULE
    );
    private static final Set<Material> PLANTABLE_GROUND = Set.of(
            Material.GRASS_BLOCK, Material.DIRT, Material.COARSE_DIRT, Material.PODZOL,
            Material.ROOTED_DIRT, Material.MOSS_BLOCK, Material.MUD
    );
    private static final List<RecycleRecipe> RECIPES = List.of(
            new RecycleRecipe(Material.ROTTEN_FLESH, 8, Material.LEATHER, 1),
            new RecycleRecipe(Material.GLASS_BOTTLE, 8, Material.GLASS, 1),
            new RecycleRecipe(Material.COBBLESTONE, 16, Material.GRAVEL, 4),
            new RecycleRecipe(Material.POISONOUS_POTATO, 8, Material.BONE_MEAL, 2)
    );

    private final JavaPlugin plugin;private final TownyHook towny;private final DataStore dataStore;private final TownyBuildsApi api;private BukkitTask task;
    public CivicAutomationService(JavaPlugin plugin,TownyHook towny,DataStore dataStore,TownyBuildsApi api){this.plugin=plugin;this.towny=towny;this.dataStore=dataStore;this.api=api;}
    public void start(){stop();long interval=Math.max(100L,plugin.getConfig().getLong("settings.civic.automation-interval-ticks",1200L));task=Bukkit.getScheduler().runTaskTimer(plugin,this::runAutomation,interval,interval);}
    public void stop(){if(task!=null)task.cancel();task=null;}
    private boolean waterNetworkActive(UUID town){return api.waterNetworkActive(town);}
    private int inventorySize(String projectId, int level) {
        if (projectId.equals("forestry")) return level >= 5 ? 54 : Math.max(9, level * 9);
        return Math.max(9, Math.min(54, (level + 1) * 9));
    }

    private boolean canFit(ItemStack[] stock,ItemStack item){return ru.neverland.townybuilds.storage.StockMath.insert(ru.neverland.townybuilds.storage.StockMath.copy(stock),new ItemStack[]{item});}
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
                ru.neverland.integration.JobsAccess.multiplier(data.townId(),"forestry",ru.neverland.integration.DistrictBonuses.multiplier(data.townId(), "forestry")));
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
                ru.neverland.integration.JobsAccess.multiplier(data.townId(),"irrigation_station",ru.neverland.integration.ResearchBonuses.production(data.townId(),"irrigation_station",ru.neverland.integration.DistrictBonuses.multiplier(data.townId(), "irrigation_station"))));
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
                    ru.neverland.integration.JobsAccess.multiplier(data.townId(),"recycling_yard",ru.neverland.integration.DistrictBonuses.multiplier(data.townId(), "recycling_yard"))));
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

    private record RecycleRecipe(Material input,int inputAmount,Material output,int outputAmount){}
}
