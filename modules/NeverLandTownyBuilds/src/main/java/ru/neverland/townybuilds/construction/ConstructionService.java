package ru.neverland.townybuilds.construction;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import ru.neverland.townybuilds.service.ConstructionSupply;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rail;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Player;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.townybuilds.data.DataStore;
import ru.neverland.townybuilds.data.TownData;
import ru.neverland.townybuilds.integration.TownyHook;
import ru.neverland.townybuilds.model.ProjectDefinition;
import ru.neverland.townybuilds.service.MessageService;
import ru.neverland.townybuilds.service.RussianItemNames;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ConstructionService implements Listener {
    @FunctionalInterface
    public interface CompletionHandler {
        void complete(Player player, Town town, String projectId, int level);
    }

    private final JavaPlugin plugin;
    private final TownyHook towny;
    private final DataStore dataStore;
    private final MessageService messages;
    private final RussianItemNames itemNames;
    private final BuildingBlueprintGenerator generator;
    private final CompletionHandler completionHandler;
    private final Map<UUID, TextDisplay> guideDisplays = new HashMap<>();
    private final Map<UUID, ObstructionPreview> obstructionPreviews = new HashMap<>();
    private record Obstruction(Location location, Material material) {}
    private record ObstructionPreview(long expiresAt, List<Obstruction> blocks) {}
    private BukkitTask renderTask;

    public ConstructionService(JavaPlugin plugin, TownyHook towny, DataStore dataStore,
                               MessageService messages, RussianItemNames itemNames,
                               BuildingBlueprintGenerator generator, CompletionHandler completionHandler) {
        this.plugin = plugin;
        this.towny = towny;
        this.dataStore = dataStore;
        this.messages = messages;
        this.itemNames = itemNames;
        this.generator = generator;
        this.completionHandler = completionHandler;
    }

    public void start() {
        stop();
        migrateLegacySites();
        repairCompletedSites();
        long interval = Math.max(5L, plugin.getConfig().getLong("settings.construction.preview-interval-ticks", 10L));
        renderTask = Bukkit.getScheduler().runTaskTimer(plugin, this::renderMissingBlocks, interval, interval);
    }

    public void stop() {
        if (renderTask != null) renderTask.cancel();
        renderTask = null;
        for (TextDisplay display : guideDisplays.values()) {
            if (display != null && display.isValid()) display.remove();
        }
        guideDisplays.clear();
        obstructionPreviews.clear();
    }

    public boolean supports(String projectId) {
        return generator.supportedProjects().contains(projectId);
    }

    public boolean supports(String projectId, int targetLevel) {
        return supports(projectId) && ConstructionStagePolicy.requiresConstruction(
                generator.maximumStage(projectId), targetLevel);
    }

    public ConstructionPreparation prepare(Player player, Town town, ProjectDefinition project,
                                           int currentLevel, int targetLevel) {
        obstructionPreviews.remove(player.getUniqueId());
        if (!supports(project.id(), targetLevel)) {
            return ConstructionPreparation.failed(ConstructionPreparation.Status.UNKNOWN_BLUEPRINT,
                    "Для уровня " + targetLevel + " физическая стадия не предусмотрена");
        }
        BlueprintPlan plan = generator.generate(project.id(), targetLevel);
        if (plan == null) return ConstructionPreparation.failed(ConstructionPreparation.Status.UNKNOWN_BLUEPRINT, project.id());
        TownData data = dataStore.town(town.getUUID());
        ConstructionSite existing = data.constructionSite(project.id());
        if (existing != null && existing.active()) {
            return new ConstructionPreparation(ConstructionPreparation.Status.ALREADY_ACTIVE, town.getUUID(),
                    existing, plan, List.of());
        }

        ConstructionSite site;
        if (existing == null) {
            Block target = player.getTargetBlockExact(Math.max(6, plugin.getConfig().getInt("settings.construction.target-distance", 16)),
                    FluidCollisionMode.NEVER);
            if (target == null || !target.getType().isSolid()) {
                return ConstructionPreparation.failed(ConstructionPreparation.Status.NO_GROUND_TARGET, "Наведитесь на твёрдый блок земли");
            }
            BlockFace facing = ConstructionSite.cardinal(player.getFacing());
            site = new ConstructionSite(project.id(), target.getWorld().getUID(), target.getX(), target.getY() + 1,
                    target.getZ(), facing, 0, targetLevel, 1, true);
        } else {
            site = new ConstructionSite(existing.projectId(), existing.worldId(), existing.originX(), existing.originY(),
                    existing.originZ(), existing.facing(), existing.completedStage(), targetLevel, targetLevel, true);
            site.setArchitectureVersion(existing.architectureVersion());
        }

        plan = planForSite(site, targetLevel);
        if (plan == null) return ConstructionPreparation.failed(ConstructionPreparation.Status.UNKNOWN_BLUEPRINT, project.id());
        World world = Bukkit.getWorld(site.worldId());
        if (world == null) return ConstructionPreparation.failed(ConstructionPreparation.Status.NO_GROUND_TARGET, "Мир строительной площадки не загружен");
        if (!player.getWorld().getUID().equals(site.worldId())) {
            return ConstructionPreparation.failed(ConstructionPreparation.Status.NO_GROUND_TARGET,
                    "Площадка находится в мире " + world.getName() + " на " + coordinates(site.location(world, new BlockOffset(0, 0, 0))));
        }
        int firstStage = existing == null ? 1 : targetLevel;
        List<Obstruction> obstacles = new ArrayList<>();
        List<String> problems = validate(town, world, site, plan, firstStage, currentLevel, obstacles);
        if (!obstacles.isEmpty()) {
            long seconds = Math.max(5, plugin.getConfig().getLong("settings.construction.obstacle-preview-seconds", 90));
            obstructionPreviews.put(player.getUniqueId(), new ObstructionPreview(System.currentTimeMillis() + seconds * 1000, obstacles));
            renderObstructions();
        }
        if (!problems.isEmpty()) {
            ConstructionPreparation.Status status = problems.get(0).startsWith("Территория")
                    ? ConstructionPreparation.Status.OUTSIDE_TOWN
                    : problems.get(0).startsWith("Рельеф")
                    ? ConstructionPreparation.Status.UNEVEN_GROUND : ConstructionPreparation.Status.OBSTRUCTED;
            return new ConstructionPreparation(status, town.getUUID(), site, plan, problems);
        }
        return new ConstructionPreparation(ConstructionPreparation.Status.READY, town.getUUID(), site, plan, List.of());
    }

    public void commit(ConstructionPreparation preparation, Player player, Town town) {
        if (preparation.status() != ConstructionPreparation.Status.READY || preparation.site() == null) {
            throw new IllegalStateException("Попытка активировать неподготовленный чертёж");
        }
        TownData data = dataStore.town(preparation.townId());
        data.setConstructionSite(preparation.site());
        dataStore.markDirty();
        dataStore.save();
        int cleared = clearExcavation(preparation.site(), preparation.plan());
        if (cleared > 0) {
            messages.send(player, "construction-excavated", Map.of("count", cleared));
        }
        checkCompletion(player, town, preparation.site());
    }

    public ConstructionProgress progress(Town town, String projectId) {
        if (town == null) return new ConstructionProgress(false, 0, 0, 0, "");
        ConstructionSite site = dataStore.town(town.getUUID()).constructionSite(projectId);
        if (site == null || !site.active()) return new ConstructionProgress(false, 0, 0, 0, "");
        BlueprintPlan plan = planForSite(site, site.targetStage());
        World world = Bukkit.getWorld(site.worldId());
        if (plan == null || world == null) return new ConstructionProgress(true, site.targetStage(), 0, 0, "");
        int total = 0;
        int placed = 0;
        for (Map.Entry<BlockOffset, BlueprintBlock> entry : plan.blocks().entrySet()) {
            BlueprintBlock expected = entry.getValue();
            if (expected.role() != BlockRole.RESIDENT || expected.stage() < site.buildFromStage()) continue;
            total++;
            if (site.location(world, entry.getKey()).getBlock().getType() == expected.material()) placed++;
        }
        return new ConstructionProgress(true, site.targetStage(), placed, total, plan.stageName());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        LocatedBlock located = locate(event.getBlockPlaced().getLocation(), true);
        if (located == null) return;
        Town playerTown = towny.town(event.getPlayer());
        if (playerTown == null || !playerTown.getUUID().equals(located.townId())) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "construction-not-resident");
            return;
        }
        BlueprintBlock expected = located.expected();
        ConstructionSite site = located.site();
        if (!site.active() || expected.stage() < site.buildFromStage() || expected.stage() > site.targetStage()
                || expected.role() == BlockRole.DECORATION) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "construction-protected");
            return;
        }
        if (event.getBlockPlaced().getType() != ConstructionSupply.material(expected.material())) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "construction-wrong-block", Map.of(
                    "expected", itemNames.name(new ItemStack(ConstructionSupply.material(expected.material())))));
            return;
        }
        // The supplied slab completes this structural block of the blueprint.
        if (event.getBlockPlaced().getType() != expected.material()) {
            event.getBlockPlaced().setType(expected.material(), false);
        }
        applyExpectedState(event.getBlockPlaced(), expected, site);
        event.getPlayer().playSound(event.getBlockPlaced().getLocation(), Sound.BLOCK_STONE_PLACE, 0.35f, 1.35f);
        ItemStack suppliedBlock = event.getItemInHand().clone();
        suppliedBlock.setAmount(1);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.getBlockPlaced().getType() != expected.material()) return;
            if (event.getPlayer().getGameMode() == GameMode.SURVIVAL
                    || event.getPlayer().getGameMode() == GameMode.ADVENTURE) {
                event.getPlayer().getInventory().addItem(suppliedBlock).values().forEach(item ->
                        event.getBlockPlaced().getWorld().dropItemNaturally(event.getPlayer().getLocation(), item));
            }
            ConstructionProgress progress = progress(playerTown, site.projectId());
            messages.send(event.getPlayer(), "construction-progress", Map.of(
                    "placed", progress.placed(), "total", progress.total(), "percent", progress.percent()));
            checkCompletion(event.getPlayer(), playerTown, site);
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        LocatedBlock located = locate(event.getBlock().getLocation(), false);
        if (located == null || event.getPlayer().hasPermission("neverlandtownybuilds.admin.bypass")) return;
        event.setCancelled(true);
        messages.send(event.getPlayer(), "construction-protected");
    }

    private List<String> validate(Town town, World world, ConstructionSite site, BlueprintPlan plan,
                                  int fromStage, int currentLevel, List<Obstruction> obstacles) {
        List<String> problems = new ArrayList<>();
        int maximum = Math.max(1, plugin.getConfig().getInt("settings.construction.validation-max-errors", 5));
        BlueprintPlan previous = currentLevel <= 0 ? null : planForSite(site, currentLevel);
        Set<BlockOffset> excavation = ExcavationPlanner.offsets(plan, fromStage);
        for (Map.Entry<BlockOffset, BlueprintBlock> entry : plan.blocks().entrySet()) {
            if (entry.getValue().stage() < fromStage) continue;
            Location location = site.location(world, entry.getKey());
            Town owner = towny.townAt(location);
            if (owner == null || !owner.getUUID().equals(town.getUUID())) {
                addProblem(problems, "Территория: блок " + coordinates(location) + " находится вне города", maximum);
                continue;
            }
            Block current = location.getBlock();
            if (entry.getKey().y() == 0
                    && !location.clone().add(0, -1, 0).getBlock().getType().isSolid()
                    && !FoundationSupportPolicy.hasPlannedSupport(plan, entry.getKey(), fromStage)) {
                addProblem(problems, "Рельеф: под фундаментом " + coordinates(location) + " нет опоры", maximum);
            }
            BlueprintBlock old = previous == null ? null : previous.blocks().get(entry.getKey());
            boolean expectedBlock = current.getType() == entry.getValue().material();
            boolean completedBlock = old != null && current.getType() == old.material();
            if (!expectedBlock && !completedBlock) {
                boolean safeExcavation = excavation.contains(entry.getKey())
                        && ExcavationPlanner.canClear(current.getType());
                boolean blocked = excavation.contains(entry.getKey())
                        ? !safeExcavation
                        : !replaceable(current.getType());
                if (blocked) {
                    obstacles.add(new Obstruction(location.clone(), current.getType()));
                    addProblem(problems, "Препятствие: " + materialName(current.getType())
                            + " на " + coordinates(location), maximum);
                }
            }
            if (problems.size() >= maximum) break;
        }
        if (problems.size() < maximum) {
            for (BlockOffset offset : excavation) {
                if (plan.blocks().containsKey(offset)) continue;
                Location location = site.location(world, offset);
                Town owner = towny.townAt(location);
                if (owner == null || !owner.getUUID().equals(town.getUUID())) {
                    addProblem(problems, "Территория: расчистка " + coordinates(location)
                            + " находится вне города", maximum);
                } else if (!isCompletedBlock(previous, offset, location.getBlock().getType())
                        && !ExcavationPlanner.canClear(location.getBlock().getType())) {
                    obstacles.add(new Obstruction(location.clone(), location.getBlock().getType()));
                    addProblem(problems, "Препятствие: " + materialName(location.getBlock().getType())
                            + " в подземном объёме " + coordinates(location), maximum);
                }
                if (problems.size() >= maximum) break;
            }
        }
        return problems;
    }

    private BlueprintPlan planForSite(ConstructionSite site, int level) {
        return generator.generateForArchitecture(site.projectId(), level, site.architectureVersion());
    }

    static boolean isCompletedBlock(BlueprintPlan previous, BlockOffset offset, Material actual) {
        BlueprintBlock old = previous == null ? null : previous.blocks().get(offset);
        return old != null && old.material() == actual;
    }

    private int clearExcavation(ConstructionSite site, BlueprintPlan plan) {
        World world = Bukkit.getWorld(site.worldId());
        if (world == null) return 0;
        BlueprintPlan previous = site.completedStage() <= 0 ? null : planForSite(site, site.completedStage());
        int cleared = 0;
        for (BlockOffset offset : ExcavationPlanner.offsets(plan, site.buildFromStage())) {
            Block block = site.location(world, offset).getBlock();
            if (isCompletedBlock(previous, offset, block.getType())) continue;
            BlueprintBlock expected = plan.blocks().get(offset);
            if (expected != null && block.getType() == expected.material()) continue;
            if (!ExcavationPlanner.canClear(block.getType()) || block.getType().isAir()) continue;
            block.setType(Material.AIR, false);
            cleared++;
        }
        return cleared;
    }

    private void addProblem(List<String> problems, String problem, int maximum) {
        if (problems.size() < maximum) problems.add(problem);
    }

    private boolean replaceable(Material material) {
        return material.isAir() || !material.isSolid() || material == Material.SNOW || material == Material.VINE;
    }

    private void checkCompletion(Player player, Town town, ConstructionSite site) {
        if (!site.active()) return;
        BlueprintPlan plan = planForSite(site, site.targetStage());
        World world = Bukkit.getWorld(site.worldId());
        if (plan == null || world == null) return;
        for (Map.Entry<BlockOffset, BlueprintBlock> entry : plan.blocks().entrySet()) {
            BlueprintBlock expected = entry.getValue();
            if (expected.role() != BlockRole.RESIDENT || expected.stage() < site.buildFromStage()) continue;
            Block block = site.location(world, entry.getKey()).getBlock();
            if (block.getType() != expected.material()) return;
            applyExpectedState(block, expected, site);
        }
        for (Map.Entry<BlockOffset, BlueprintBlock> entry : orderedEntries(plan)) {
            BlueprintBlock expected = entry.getValue();
            if (expected.role() != BlockRole.DECORATION || expected.stage() < site.buildFromStage()) continue;
            Block block = site.location(world, entry.getKey()).getBlock();
            block.setType(expected.material(), false);
            applyExpectedState(block, expected, site);
        }
        normalizePlanStates(world, site, plan, site.buildFromStage());
        completionHandler.complete(player, town, site.projectId(), site.targetStage());
        site.complete();
        dataStore.markDirty();
        dataStore.save();
    }

    private void migrateLegacySites() {
        int migrated = 0;
        int removed = 0;
        int restored = 0;
        boolean changed = false;
        for (TownData town : dataStore.towns().values()) {
            for (ConstructionSite site : town.constructionSites().values()) {
                if (site.architectureVersion() >= ConstructionSite.CURRENT_ARCHITECTURE_VERSION) continue;
                World world = Bukkit.getWorld(site.worldId());
                int visibleLevel = site.active() ? site.targetStage() : site.completedStage();
                BlueprintPlan legacy = visibleLevel <= 0 ? null
                        : generator.generateForArchitecture(site.projectId(), visibleLevel, site.architectureVersion());
                BlueprintPlan modern = visibleLevel <= 0 ? null : generator.generate(site.projectId(), visibleLevel);
                if (world == null || (visibleLevel > 0 && (legacy == null || modern == null))) continue;

                if (legacy != null && modern != null) {
                    for (Map.Entry<BlockOffset, BlueprintBlock> entry : legacy.blocks().entrySet()) {
                        BlueprintBlock replacement = modern.blocks().get(entry.getKey());
                        boolean compatibleReplacement = replacement != null
                                && replacement.material() == entry.getValue().material();
                        if (compatibleReplacement) continue;
                        Block block = site.location(world, entry.getKey()).getBlock();
                        Town owner = towny.townAt(block.getLocation());
                        if (owner == null || !owner.getUUID().equals(town.townId())) continue;
                        if (block.getType() != entry.getValue().material()) continue;
                        preserveContainer(block, site);
                        block.setType(Material.AIR, false);
                        removed++;
                    }
                }

                int restoreLevel = site.active() ? site.completedStage() : visibleLevel;
                BlueprintPlan completedPlan = restoreLevel <= 0 ? null : generator.generate(site.projectId(), restoreLevel);
                if (completedPlan != null) {
                    for (Map.Entry<BlockOffset, BlueprintBlock> entry : orderedEntries(completedPlan)) {
                        Block block = site.location(world, entry.getKey()).getBlock();
                        Town owner = towny.townAt(block.getLocation());
                        if (owner == null || !owner.getUUID().equals(town.townId())) continue;
                        if (block.getType() != entry.getValue().material()) {
                            if (!replaceable(block.getType())) continue;
                            block.setType(entry.getValue().material(), false);
                            restored++;
                        }
                        applyExpectedState(block, entry.getValue(), site);
                    }
                }
                site.setArchitectureVersion(ConstructionSite.CURRENT_ARCHITECTURE_VERSION);
                migrated++;
                changed = true;
            }
        }
        if (changed) {
            dataStore.markDirty();
            dataStore.save();
            plugin.getLogger().info("Обновлена ванильная архитектура площадок: " + migrated
                    + "; удалено старых блоков: " + removed + "; восстановлено новых: " + restored + ".");
        }
    }

    private void preserveContainer(Block block, ConstructionSite site) {
        if (!(block.getState() instanceof Container container)) return;
        Location drop = new Location(block.getWorld(), site.originX() + 0.5, site.originY() + 1.0, site.originZ() + 0.5);
        for (ItemStack item : container.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) block.getWorld().dropItemNaturally(drop, item.clone());
        }
        container.getInventory().clear();
    }

    private void repairCompletedSites() {
        int repaired = 0;
        for (TownData town : dataStore.towns().values()) {
            for (ConstructionSite site : town.constructionSites().values()) {
                if (site.active() || site.completedStage() <= 0) continue;
                World world = Bukkit.getWorld(site.worldId());
                BlueprintPlan plan = planForSite(site, site.completedStage());
                if (world == null || plan == null) continue;
                for (Map.Entry<BlockOffset, BlueprintBlock> entry : orderedEntries(plan)) {
                    BlueprintBlock expected = entry.getValue();
                    if (expected.stage() > site.completedStage()) continue;
                    Block block = site.location(world, entry.getKey()).getBlock();
                    Town owner = towny.townAt(block.getLocation());
                    if (owner == null || !owner.getUUID().equals(town.townId())) continue;
                    if (block.getType() != expected.material() && replaceable(block.getType())) {
                        block.setType(expected.material(), false);
                        repaired++;
                    }
                    if (block.getType() == expected.material()) applyExpectedState(block, expected, site);
                }
                normalizePlanStates(world, site, plan, 1);
            }
        }
        if (repaired > 0) plugin.getLogger().info("Обновлены завершённые процедурные здания: восстановлено блоков " + repaired + ".");
    }

    private void normalizePlanStates(World world, ConstructionSite site, BlueprintPlan plan, int fromStage) {
        for (Map.Entry<BlockOffset, BlueprintBlock> entry : orderedEntries(plan)) {
            BlueprintBlock expected = entry.getValue();
            if (expected.stage() < fromStage) continue;
            Block block = site.location(world, entry.getKey()).getBlock();
            if (block.getType() == expected.material()) applyExpectedState(block, expected, site);
        }
    }

    private List<Map.Entry<BlockOffset, BlueprintBlock>> orderedEntries(BlueprintPlan plan) {
        List<Map.Entry<BlockOffset, BlueprintBlock>> entries = new ArrayList<>(plan.blocks().entrySet());
        entries.sort(Comparator
                .comparingInt((Map.Entry<BlockOffset, BlueprintBlock> entry) -> placementRank(entry.getValue()))
                .thenComparingInt(entry -> entry.getKey().y())
                .thenComparingInt(entry -> entry.getKey().x())
                .thenComparingInt(entry -> entry.getKey().z()));
        return entries;
    }

    private int placementRank(BlueprintBlock block) {
        if (!block.material().name().endsWith("_DOOR")) return 0;
        return block.half() == Bisected.Half.TOP ? 2 : 1;
    }

    private void applyExpectedState(Block block, BlueprintBlock expected, ConstructionSite site) {
        BlockData data = block.getBlockData();
        if (data instanceof Orientable orientable && expected.axis() != null) {
            org.bukkit.Axis axis = site.worldAxis(expected.axis());
            if (orientable.getAxes().contains(axis)) orientable.setAxis(axis);
        }
        if (data instanceof Directional directional && expected.facing() != null) {
            BlockFace facing = site.worldFace(expected.facing());
            if (directional.getFaces().contains(facing)) directional.setFacing(facing);
        }
        if (data instanceof Rail rail && expected.axis() != null) {
            org.bukkit.Axis axis = site.worldAxis(expected.axis());
            Rail.Shape shape = axis == org.bukkit.Axis.X ? Rail.Shape.EAST_WEST : Rail.Shape.NORTH_SOUTH;
            if (rail.getShapes().contains(shape)) rail.setShape(shape);
        }
        if (data instanceof Bisected bisected && expected.half() != null) bisected.setHalf(expected.half());
        if (data instanceof Door door && expected.hinge() != null) door.setHinge(expected.hinge());
        if (data instanceof Openable openable) openable.setOpen(false);
        if (data instanceof MultipleFacing multipleFacing) connect(block, multipleFacing);
        block.setBlockData(data, false);
    }

    private void connect(Block block, MultipleFacing data) {
        for (BlockFace face : data.getAllowedFaces()) {
            if (face != BlockFace.NORTH && face != BlockFace.EAST
                    && face != BlockFace.SOUTH && face != BlockFace.WEST) continue;
            Material neighbor = block.getRelative(face).getType();
            String name = neighbor.name();
            boolean connected = neighbor == block.getType() || neighbor.isOccluding()
                    || name.endsWith("_FENCE") || name.endsWith("_FENCE_GATE")
                    || name.endsWith("_WALL") || name.endsWith("_GLASS_PANE")
                    || neighbor == Material.IRON_BARS || name.endsWith("_GLASS");
            data.setFace(face, connected);
        }
    }

    private LocatedBlock locate(Location location, boolean activeOnly) {
        if (location.getWorld() == null) return null;
        UUID worldId = location.getWorld().getUID();
        for (Map.Entry<UUID, TownData> town : dataStore.towns().entrySet()) {
            for (ConstructionSite site : town.getValue().constructionSites().values()) {
                if (!site.worldId().equals(worldId) || (activeOnly && !site.active())) continue;
                int visibleLevel = site.active() ? site.targetStage() : site.completedStage();
                BlueprintPlan plan = planForSite(site, visibleLevel);
                if (plan == null) continue;
                BlueprintBlock expected = plan.blocks().get(site.offset(location));
                if (expected != null) return new LocatedBlock(town.getKey(), site, expected);
            }
        }
        return null;
    }

    private void renderObstructions() {
        long now = System.currentTimeMillis();
        Particle.DustOptions red = new Particle.DustOptions(Color.fromRGB(255, 55, 55), 1.1f);
        var iterator = obstructionPreviews.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            ObstructionPreview preview = entry.getValue();
            if (player == null || preview.expiresAt() <= now) { iterator.remove(); continue; }
            preview.blocks().removeIf(obstacle -> {
                Location location = obstacle.location();
                World world = location.getWorld();
                return world == null || (world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)
                        && location.getBlock().getType() != obstacle.material());
            });
            if (preview.blocks().isEmpty()) { iterator.remove(); continue; }
            for (Obstruction obstacle : preview.blocks()) {
                Location location = obstacle.location();
                if (player.getWorld().equals(location.getWorld())
                        && player.getLocation().distanceSquared(location) <= 64 * 64) {
                    renderOutline(player, location, red);
                }
            }
        }
    }

    private void renderMissingBlocks() {
        renderObstructions();
        if (!plugin.getConfig().getBoolean("settings.construction.preview-enabled", true)) return;
        double distance = Math.max(16, plugin.getConfig().getDouble("settings.construction.preview-distance", 64));
        Particle.DustOptions outline = new Particle.DustOptions(Color.fromRGB(255, 212, 90), 0.9f);
        for (Player player : Bukkit.getOnlinePlayers()) {
            Town town = towny.town(player);
            if (town == null) {
                removeGuide(player.getUniqueId());
                continue;
            }
            TownData data = dataStore.town(town.getUUID());
            PreviewTarget nearest = null;
            for (ConstructionSite site : data.constructionSites().values()) {
                if (!site.active() || !player.getWorld().getUID().equals(site.worldId())) continue;
                BlueprintPlan plan = planForSite(site, site.targetStage());
                if (plan == null) continue;
                Optional<Map.Entry<BlockOffset, BlueprintBlock>> missing = plan.blocks().entrySet().stream()
                        .filter(entry -> entry.getValue().role() == BlockRole.RESIDENT)
                        .filter(entry -> entry.getValue().stage() >= site.buildFromStage())
                        .filter(entry -> site.location(player.getWorld(), entry.getKey()).getBlock().getType() != entry.getValue().material())
                        .sorted(Comparator.comparingDouble(entry -> site.location(player.getWorld(), entry.getKey()).distanceSquared(player.getLocation())))
                        .findFirst();
                if (missing.isEmpty()) {
                    checkCompletion(player, town, site);
                    continue;
                }
                Map.Entry<BlockOffset, BlueprintBlock> entry = missing.get();
                Location location = site.location(player.getWorld(), entry.getKey());
                double distanceSquared = location.clone().add(0.5, 0.5, 0.5).distanceSquared(player.getLocation());
                if (distanceSquared <= distance * distance
                        && (nearest == null || distanceSquared < nearest.distanceSquared())) {
                    nearest = new PreviewTarget(location, entry.getValue(), distanceSquared);
                }
            }
            if (nearest == null) {
                removeGuide(player.getUniqueId());
                continue;
            }
            renderOutline(player, nearest.location(), outline);
            Location target = nearest.location();
            renderGuideDisplay(player, nearest);
            player.sendActionBar(messages.component("construction-guide", Map.of(
                    "expected", itemNames.name(new ItemStack(ConstructionSupply.material(nearest.expected().material()))),
                    "x", target.getBlockX(), "y", target.getBlockY(), "z", target.getBlockZ(),
                    "distance", String.format(Locale.ROOT, "%.1f", Math.sqrt(nearest.distanceSquared()))
            )));
        }
        for (UUID playerId : List.copyOf(guideDisplays.keySet())) {
            if (Bukkit.getPlayer(playerId) == null) removeGuide(playerId);
        }
    }

    private void renderGuideDisplay(Player player, PreviewTarget target) {
        if (!plugin.getConfig().getBoolean("settings.construction.guide-label-enabled", true)) {
            removeGuide(player.getUniqueId());
            return;
        }
        double height = plugin.getConfig().getDouble("settings.construction.guide-label-height", 1.35);
        Location labelLocation = target.location().clone().add(0.5, Math.max(0.6, height), 0.5);
        TextDisplay display = guideDisplays.get(player.getUniqueId());
        if (display == null || !display.isValid() || !display.getWorld().equals(labelLocation.getWorld())) {
            removeGuide(player.getUniqueId());
            display = labelLocation.getWorld().spawn(labelLocation, TextDisplay.class, created -> {
                created.setPersistent(false);
                created.setInvulnerable(true);
                created.setGravity(false);
                created.setVisibleByDefault(false);
                created.setBillboard(Display.Billboard.CENTER);
                created.setViewRange(1.0f);
                created.setLineWidth(260);
                created.setAlignment(TextDisplay.TextAlignment.CENTER);
                created.setShadowed(true);
                created.setSeeThrough(true);
                created.setDefaultBackground(false);
                created.setBackgroundColor(Color.fromARGB(170, 18, 36, 58));
            });
            guideDisplays.put(player.getUniqueId(), display);
        } else {
            display.teleport(labelLocation);
        }
        display.text(messages.component("construction-guide-display", Map.of(
                "expected", itemNames.name(new ItemStack(ConstructionSupply.material(target.expected().material()))),
                "x", target.location().getBlockX(),
                "y", target.location().getBlockY(),
                "z", target.location().getBlockZ(),
                "distance", String.format(Locale.ROOT, "%.1f", Math.sqrt(target.distanceSquared()))
        )));
        player.showEntity(plugin, display);
    }

    private void removeGuide(UUID playerId) {
        TextDisplay display = guideDisplays.remove(playerId);
        if (display != null && display.isValid()) display.remove();
    }

    private void renderOutline(Player player, Location block, Particle.DustOptions dust) {
        double inset = 0.04;
        double minX = block.getBlockX() + inset, maxX = block.getBlockX() + 1.0 - inset;
        double minY = block.getBlockY() + inset, maxY = block.getBlockY() + 1.0 - inset;
        double minZ = block.getBlockZ() + inset, maxZ = block.getBlockZ() + 1.0 - inset;
        int pointsPerEdge = Math.max(2, plugin.getConfig().getInt("settings.construction.guide-points-per-edge", 4));
        for (int step = 0; step <= pointsPerEdge; step++) {
            double factor = step / (double) pointsPerEdge;
            double x = minX + (maxX - minX) * factor;
            double y = minY + (maxY - minY) * factor;
            double z = minZ + (maxZ - minZ) * factor;
            particle(player, x, minY, minZ, dust); particle(player, x, minY, maxZ, dust);
            particle(player, x, maxY, minZ, dust); particle(player, x, maxY, maxZ, dust);
            particle(player, minX, y, minZ, dust); particle(player, minX, y, maxZ, dust);
            particle(player, maxX, y, minZ, dust); particle(player, maxX, y, maxZ, dust);
            particle(player, minX, minY, z, dust); particle(player, minX, maxY, z, dust);
            particle(player, maxX, minY, z, dust); particle(player, maxX, maxY, z, dust);
        }
    }

    private void particle(Player player, double x, double y, double z, Particle.DustOptions dust) {
        player.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, dust);
    }

    private String coordinates(Location location) {
        return location.getWorld().getName() + " [" + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + "]";
    }

    private String materialName(Material material) {
        if (material.isItem()) return itemNames.name(new ItemStack(material));
        String value = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return value.isEmpty() ? material.name() : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private record LocatedBlock(UUID townId, ConstructionSite site, BlueprintBlock expected) { }
    private record PreviewTarget(Location location, BlueprintBlock expected, double distanceSquared) { }
}
