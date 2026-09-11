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
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.##",
            DecimalFormatSymbols.getInstance(Locale.forLanguageTag("ru-RU")));
    private final JavaPlugin plugin;
    private final TownyHook towny;
    private final DataStore dataStore;
    private final MessageService messages;
    private final RussianItemNames itemNames;
    private final Map<UUID, DraftSelection> selections = new HashMap<>();
    private final ru.neverland.townybuilds.construction.BuildingFootprints footprints = new ru.neverland.townybuilds.construction.BuildingFootprints();
    private final CivicEffectsService effectsApi;
    private final CivicAutomationService automation;
    private final CivicShopMenus shops;
    private BukkitTask previewTask;

    public CivicService(JavaPlugin plugin, TownyHook towny, DataStore dataStore, MessageService messages, RussianItemNames itemNames) {
        this.plugin = plugin;
        this.towny = towny;
        this.dataStore = dataStore;
        this.messages = messages;
        this.itemNames = itemNames;
        effectsApi=new CivicEffectsService(plugin,towny,dataStore);
        automation=new CivicAutomationService(plugin,towny,dataStore,effectsApi);
        shops=new CivicShopMenus(plugin,towny,dataStore,messages,itemNames,this);
    }

    public void start() {
        stopTasks();
        automation.start();shops.start();
        previewTask = Bukkit.getScheduler().runTaskTimer(plugin, this::renderSelections, 20L, 20L);
        Bukkit.getServicesManager().unregister(TownyBuildsApi.class, effectsApi);
        Bukkit.getServicesManager().register(TownyBuildsApi.class, effectsApi, plugin, ServicePriority.Normal);
    }

    public void stop() {
        stopTasks();
        selections.clear();
        Bukkit.getServicesManager().unregister(TownyBuildsApi.class, effectsApi);
    }

    private void stopTasks() {
        automation.stop();shops.stop();
        if (previewTask != null) previewTask.cancel();

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
            case "shop" -> shops.shop(player, args);
            case "insurance" -> insurance(player, args);
            case "bulletin" -> bulletin(player, args);
            default -> help(player);
        }
    }

    private void help(Player player) {
        messages.send(player, "civic-help");
    }

     Town requireTown(Player player) {
        Town town = towny.town(player);
        if (town == null) messages.send(player, "no-town");
        return town;
    }

     boolean requireMayor(Player player, Town town) {
        if (towny.isMayor(player, town)) return true;
        messages.send(player, "civic-only-mayor");
        return false;
    }

     boolean requireProject(Player player, TownData data, String projectId) {
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
                    || !ru.neverland.integration.TreasuryAccess.withdraw(town,"social","insurance",amount,"Пополнение страхового резерва")) {
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

    private void openCivicStorage(Player player, Town town, TownData data, String projectId) {
        ((ru.neverland.townybuilds.NeverLandTownyBuilds)plugin).storage().openStorage(player,projectId);
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

    public void setStall(Player p,String id){shops.setStall(p,id);}
    public void removeStall(org.bukkit.command.CommandSender sender,String id){shops.removeStall(sender,id);}
    public List<String> stallIds(){return shops.stallIds();}
    public CivicShopMenus shops(){return shops;}
    public java.util.Set<String> supportedProjects(){return effectsApi.supportedProjects();}
    public int projectLevel(UUID town,String project){return effectsApi.projectLevel(town,project);}
    public int operationalLevel(UUID town,String project){return effectsApi.operationalLevel(town,project);}
    public Map<String,ru.neverland.townybuilds.api.BuildingFootprint> buildingFootprints(UUID town){return effectsApi.buildingFootprints(town);}
    public Map<String,ru.neverland.townybuilds.api.BuildingWorkplace> workplaces(UUID town){return effectsApi.workplaces(town);}
    public double benefit(UUID town,CivicBenefit benefit){return effectsApi.benefit(town,benefit);}
    public boolean waterNetworkActive(UUID town){return effectsApi.waterNetworkActive(town);}
    public double insuranceReserve(UUID town){return effectsApi.insuranceReserve(town);}
    public double consumeInsurance(UUID town,double amount){return effectsApi.consumeInsurance(town,amount);}
    public Optional<CivicArea> area(UUID town,String project){return effectsApi.area(town,project);}
    public Optional<CivicLine> line(UUID town,String project){return effectsApi.line(town,project);}

}
