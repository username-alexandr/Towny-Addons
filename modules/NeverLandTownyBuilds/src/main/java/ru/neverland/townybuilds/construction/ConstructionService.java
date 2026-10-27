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
    private final ConstructionBlockDisplays blockDisplays;
    private long previewGeneration;
    private boolean running;
    private ConstructionRefunds refunds;
    public void refunds(ConstructionRefunds value) { refunds=value; }
    private boolean refundBlocked(UUID town, String project) { return refunds!=null&&refunds.blocks(town,project); }
    private final Map<UUID, ObstructionPreview> obstructionPreviews = new HashMap<>();
    private record Obstruction(Location location, Material material) {}
    private record ObstructionPreview(long expiresAt, List<Obstruction> blocks) {}
    private BukkitTask renderTask;
    private BukkitTask restorationTask;

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
        this.blockDisplays = new ConstructionBlockDisplays(plugin);
    }

    public void start() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        stop();
        running = true;
        // Events registers its public repair API after Builds enables. Wait until all plugins are ready.
        restorationTask = ru.neverland.core.SuiteScheduler.get().runTask(plugin, () -> {
            for (TownData data : dataStore.towns().values()) for (ConstructionSite site : data.constructionSites().values()) {
                World world = Bukkit.getWorld(site.worldId());
                if (world != null) ru.neverland.core.SuiteScheduler.region(plugin, site.location(world,new BlockOffset(0,0,0)), () -> {
                    migrateLegacySites(); repairCompletedSites();
                });
            }
        });
        long interval = Math.max(5L, plugin.getConfig().getLong("settings.construction.preview-interval-ticks", 10L));
        renderTask = ru.neverland.core.SuiteScheduler.get().runTaskTimer(plugin, this::renderMissingBlocks, interval, interval);
    } }

    public void stop() { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        running = false;
        previewGeneration++;
        if (restorationTask != null) restorationTask.cancel();
        restorationTask = null;
        if (renderTask != null) renderTask.cancel();
        renderTask = null;
        for (TextDisplay display : guideDisplays.values()) {
            ru.neverland.core.WorldTasks.remove(plugin, display);
        }
        guideDisplays.clear();
        blockDisplays.clear();
        obstructionPreviews.clear();
    } }

    public boolean supports(String projectId) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
        return generator.supportedProjects().contains(projectId);
    } }

    public boolean supports(String projectId, int targetLevel) { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {
        return supports(projectId) && ConstructionStagePolicy.requiresConstruction(
                generator.maximumStage(projectId), targetLevel);
    } }

    public ConstructionPreparation prepare(Player player, Town town, ProjectDefinition project,
                                           int currentLevel, int targetLevel) { return prepare(player,town,project,currentLevel,targetLevel,null); }
    public ConstructionPreparation prepare(Player player,Town town,ProjectDefinition project,int currentLevel,int targetLevel,PlacementChoice choice) { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {
        if(!ru.neverland.integration.SpecializationAccess.allowed(town.getUUID(),project.id()))return ConstructionPreparation.failed(ConstructionPreparation.Status.UNKNOWN_BLUEPRINT,ru.neverland.integration.SpecializationAccess.reason(project.id()));
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
            if(choice==null) {
                Block target=player.getTargetBlockExact(Math.max(6,plugin.getConfig().getInt("settings.construction.target-distance",16)),FluidCollisionMode.NEVER);
                if(target==null||!target.getType().isSolid())return ConstructionPreparation.failed(ConstructionPreparation.Status.NO_GROUND_TARGET,"Наведитесь на твёрдый блок земли");
                choice=new PlacementChoice(target.getWorld().getUID(),target.getX(),target.getY()+1,target.getZ(),player.getFacing());
            }
            site=new ConstructionSite(project.id(),choice.world(),choice.x(),choice.y(),choice.z(),choice.facing(),0,targetLevel,1,true);
        } else {
            site = new ConstructionSite(existing.projectId(), existing.worldId(), existing.originX(), existing.originY(),
                    existing.originZ(), existing.facing(), existing.completedStage(), targetLevel, targetLevel, true);
            site.setArchitectureVersion(existing.architectureVersion());
        }

        plan = planForSite(site, targetLevel);
        if (plan == null) return ConstructionPreparation.failed(ConstructionPreparation.Status.UNKNOWN_BLUEPRINT, project.id());
        if (ru.neverland.townybuilds.integration.FireRepairAccess.blocked(town.getUUID(),site,plan)) {
            return ConstructionPreparation.failed(ConstructionPreparation.Status.OBSTRUCTED,"Сначала завершите ремонт участка: /t events repairs");
        }
        World world = Bukkit.getWorld(site.worldId());
        if (world == null) return ConstructionPreparation.failed(ConstructionPreparation.Status.NO_GROUND_TARGET, "Мир строительной площадки не загружен");
        if (!player.getWorld().getUID().equals(site.worldId())) {
            return ConstructionPreparation.failed(ConstructionPreparation.Status.NO_GROUND_TARGET,
                    "Площадка находится в мире " + world.getName() + " на " + coordinates(site.location(world, new BlockOffset(0, 0, 0))));
        }
        for(var offset:plan.blocks().keySet()) {int y=site.originY()+offset.y();if(y<=world.getMinHeight()||y>=world.getMaxHeight())return new ConstructionPreparation(ConstructionPreparation.Status.OBSTRUCTED,town.getUUID(),site,plan,List.of("Здание выходит за пределы высоты мира"));}
        if (!ownsPlan(world,site,plan)) return ConstructionPreparation.failed(ConstructionPreparation.Status.OBSTRUCTED,"Подойдите к площадке: её чанки ещё не готовы");
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
    } }

    public void commit(ConstructionPreparation preparation, Player player, Town town) { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {
        if (preparation.status() != ConstructionPreparation.Status.READY || preparation.site() == null) {
            throw new IllegalStateException("Попытка активировать неподготовленный чертёж");
        }
        if(!ru.neverland.integration.SpecializationAccess.allowed(preparation.townId(),preparation.site().projectId()))throw new IllegalStateException(ru.neverland.integration.SpecializationAccess.reason(preparation.site().projectId()));
        TownData data = dataStore.town(preparation.townId());
        if (ru.neverland.townybuilds.integration.FireRepairAccess.blocked(preparation.townId(),preparation.site(),preparation.plan())) {
            throw new IllegalStateException("Сначала завершите ремонт после пожара: /t events repairs");
        }
        if (!ownsPlan(Bukkit.getWorld(preparation.site().worldId()),preparation.site(),preparation.plan())) throw new IllegalStateException("Площадка сейчас недоступна");
        data.setConstructionSite(preparation.site());
        dataStore.markDirty();
        dataStore.save();
        int cleared = clearExcavation(preparation.site(), preparation.plan());
        if (cleared > 0) {
            messages.send(player, "construction-excavated", Map.of("count", cleared));
        }
        checkCompletion(player, town, preparation.site());
    } }

    public ConstructionReceipt captureReceipt(ConstructionPreparation preparation, double money,
                                               List<ConstructionReceipt.Item> resources) {
        try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {
            ConstructionSite site = preparation.site(); World world = Bukkit.getWorld(site.worldId());
            BlueprintPlan plan = preparation.plan();
            if (!ownsPlan(world, site, plan)) throw new IllegalStateException("Подойдите к строительной площадке");
            Map<BlockOffset, ConstructionReceipt.Change> changes = new HashMap<>();
            for (var entry : plan.blocks().entrySet()) {
                if (entry.getValue().stage() < site.buildFromStage()) continue;
                BlockData expected = entry.getValue().material().createBlockData();
                orientExpectedState(expected, entry.getValue(), site);
                changes.put(entry.getKey(), new ConstructionReceipt.Change(site.location(world, entry.getKey()).getBlock().getBlockData().getAsString(), expected.getAsString()));
            }
            // Only the exact excavation cells validated for this site; never a bounding box or chunk reset.
            for (BlockOffset offset : ExcavationPlanner.offsets(plan, site.buildFromStage())) {
                Block block = site.location(world, offset).getBlock();
                if (!block.getType().isAir() && ExcavationPlanner.canClear(block.getType()))
                    changes.putIfAbsent(offset, new ConstructionReceipt.Change(block.getBlockData().getAsString(), "minecraft:air"));
            }
            return new ConstructionReceipt(UUID.randomUUID(), site.targetStage(), ConstructionReceipt.cents(money),
                    resources, changes, "ACTIVE", 0, List.of(), 0, "", false, resources.size());
        }
    }

    /** Legacy costs require explicit administrator verification. Only registered blueprint cells are removed. */
    public ConstructionReceipt legacyReceipt(ConstructionSite site, double money, List<ConstructionReceipt.Item> resources) {
        try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {
            BlueprintPlan plan = planForSite(site, site.targetStage());
            BlueprintPlan previous = site.completedStage() == 0 ? null : planForSite(site, site.completedStage());
            if (plan == null) throw new IllegalStateException("Чертёж недоступен");
            Map<BlockOffset, ConstructionReceipt.Change> changes = new HashMap<>();
            for (var entry : plan.blocks().entrySet()) {
                if (entry.getValue().stage() < site.buildFromStage()) continue;
                BlockData expected = entry.getValue().material().createBlockData(); orientExpectedState(expected,entry.getValue(),site);
                BlueprintBlock old = previous == null ? null : previous.blocks().get(entry.getKey());
                BlockData before = old == null ? Material.AIR.createBlockData() : old.material().createBlockData();
                if (old != null) orientExpectedState(before,old,site);
                changes.put(entry.getKey(),new ConstructionReceipt.Change(before.getAsString(),expected.getAsString()));
            }
            return new ConstructionReceipt(UUID.randomUUID(),site.targetStage(),ConstructionReceipt.cents(money),resources,changes,"ACTIVE",0,List.of(),0,"",true,resources.size());
        }
    }

    public boolean cancellationTerritoryOwned(UUID townId,ConstructionSite site,ConstructionReceipt receipt) {
        World world=Bukkit.getWorld(site.worldId());if(world==null)return false;
        for(var offset:receipt.changes().keySet()) {
            Town owner=towny.townAt(site.location(world,offset));
            if(owner==null||!owner.getUUID().equals(townId))throw new IllegalStateException("Территория стройки больше не принадлежит городу. Возврат сохранён; верните участки городу перед разбором.");
        }
        return true;
    }

    /** Called on the site region; restart-safe because already-restored cells are skipped. */
    public boolean restoreCancelled(ConstructionSite site, ConstructionReceipt receipt) {
        try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) {
            World world = Bukkit.getWorld(site.worldId()); if (world == null) return false;
            for (var entry : receipt.changes().entrySet()) {
                Location at = site.location(world,entry.getKey());
                if (!ru.neverland.core.SuiteScheduler.owns(at) || !world.isChunkLoaded(at.getBlockX() >> 4,at.getBlockZ() >> 4)) return false;
            }
            // Validate the whole operation before the first write, including non-empty containers.
            for (var entry : receipt.changes().entrySet()) {
                Block block = site.location(world,entry.getKey()).getBlock();
                BlockData before = Bukkit.createBlockData(entry.getValue().before());
                if (block.getBlockData().equals(before)) continue;
                Material expected = Bukkit.createBlockData(entry.getValue().expected()).getMaterial();
                if (block.getType() != expected && !block.getType().isAir())
                    throw new IllegalStateException("На месте стройки изменён блок " + coordinates(block.getLocation()) + ". Возврат сохранён, нужна проверка.");
                if (block.getState() instanceof Container container && !container.getInventory().isEmpty())
                    throw new IllegalStateException("Освободите контейнер на стройке: " + coordinates(block.getLocation()));
            }
            int changed = 0;
            for (var entry : receipt.changes().entrySet()) {
                Block block = site.location(world,entry.getKey()).getBlock();
                BlockData before = Bukkit.createBlockData(entry.getValue().before());
                if (block.getBlockData().equals(before)) continue;
                if (changed++ == 128) return false;
                block.setBlockData(before, false);
            }
            clearPreviews();
            return true;
        }
    }

    public ConstructionProgress progress(Town town, String projectId) { try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) {
        if (town == null) return new ConstructionProgress(false, 0, 0, 0, "");
        ConstructionSite site = dataStore.town(town.getUUID()).constructionSite(projectId);
        if (site == null || !site.active()) return new ConstructionProgress(false, 0, 0, 0, "");
        BlueprintPlan plan = planForSite(site, site.targetStage());
        World world = Bukkit.getWorld(site.worldId());
        if (plan == null || world == null) return new ConstructionProgress(true, site.targetStage(), 0, 0, "");
        if (!ownsPlan(world,site,plan)) return new ConstructionProgress(true,site.targetStage(),0,(int)plan.residentBlocks(site.buildFromStage()),"Подойдите к площадке для проверки");
        int total = 0;
        int placed = 0;
        for (Map.Entry<BlockOffset, BlueprintBlock> entry : plan.blocks().entrySet()) {
            BlueprintBlock expected = entry.getValue();
            if (expected.role() != BlockRole.RESIDENT || expected.stage() < site.buildFromStage()) continue;
            total++;
            if (site.location(world, entry.getKey()).getBlock().getType() == expected.material()) placed++;
        }
        return new ConstructionProgress(true, site.targetStage(), placed, total, (site.adminPaused()?"Пауза • ":"")+plan.stageName());
    } }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) { try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyBuilds")){ru.neverland.core.ModuleGate.stopEvent(event);return;}
        LocatedBlock located = locate(event.getBlockPlaced().getLocation(), true);
        if (located == null) return;
        Town playerTown = towny.town(event.getPlayer());
        if (playerTown == null || !playerTown.getUUID().equals(located.townId())) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "construction-not-resident");
            return;
        }
        if(!ru.neverland.integration.SpecializationAccess.allowed(located.townId(),located.site().projectId())){event.setCancelled(true);messages.send(event.getPlayer(),"specialization-required",Map.of("specialization",ru.neverland.integration.SpecializationAccess.requirement(located.site().projectId())));return;}
        if(!towny.isMayor(event.getPlayer(),playerTown)&&!ru.neverland.core.CouncilAccess.check(event.getPlayer(),playerTown.getUUID(),"build_place",true)){
            event.setCancelled(true);event.getPlayer().sendMessage("§eМэр ограничил участие в этой стройке.");return;
        }
        BlueprintBlock expected = located.expected();
        ConstructionSite site = located.site();
        if(site.adminPaused()||refundBlocked(located.townId(),site.projectId())){event.setCancelled(true);event.getPlayer().sendMessage("§eСтройка приостановлена или ожидает возврата. Средства сохранены.");return;}
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
        ru.neverland.core.SuiteScheduler.region(plugin, event.getBlockPlaced().getLocation(), () -> {
            // The held sample belongs to the builder even if cancellation has already restored the cell.
            // A later protection listener can still cancel placement, in which case Minecraft returns it itself.
            if (event.isCancelled()) return;
            ru.neverland.core.SuiteScheduler.entityLater(plugin, event.getPlayer(), () -> {
                if (event.getPlayer().getGameMode() == GameMode.SURVIVAL || event.getPlayer().getGameMode() == GameMode.ADVENTURE)
                    event.getPlayer().getInventory().addItem(suppliedBlock).values().forEach(item -> event.getPlayer().getWorld().dropItemNaturally(event.getPlayer().getLocation(),item));
            }, () -> ru.neverland.core.SuiteScheduler.region(plugin, event.getBlockPlaced().getLocation(), () ->
                    event.getBlockPlaced().getWorld().dropItemNaturally(event.getBlockPlaced().getLocation(),suppliedBlock)), 1);
            ConstructionProgress progress = progress(playerTown, site.projectId());
            messages.send(event.getPlayer(), "construction-progress", Map.of(
                    "placed", progress.placed(), "total", progress.total(), "percent", progress.percent()));
            checkCompletion(event.getPlayer(), playerTown, site);
        });
    } }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) { try (var suiteGuard12 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyBuilds")){ru.neverland.core.ModuleGate.stopEvent(event);return;}
        LocatedBlock located = locate(event.getBlock().getLocation(), false);
        if (located == null || event.getPlayer().hasPermission("neverlandtownybuilds.admin.bypass")) return;
        event.setCancelled(true);
        messages.send(event.getPlayer(), "construction-protected");
    } }

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

    private boolean ownsPlan(World world, ConstructionSite site, BlueprintPlan plan) {
        if (world == null || plan == null) return false;
        for (BlockOffset offset : plan.blocks().keySet()) if (!ru.neverland.core.SuiteScheduler.owns(site.location(world,offset))) return false;
        return true;
    }
    public BlueprintPlan previewPlan(String project,int level,ConstructionSite existing){try (var suiteGuard13 = ru.neverland.core.SuiteState.guard()){return existing==null?generator.generate(project,level):planForSite(existing,level);}}
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
        if(site.adminPaused()||refundBlocked(town.getUUID(),site.projectId()))return;
        if (!site.active()||!ru.neverland.integration.SpecializationAccess.allowed(town.getUUID(),site.projectId())) return;
        BlueprintPlan plan = planForSite(site, site.targetStage());
        World world = Bukkit.getWorld(site.worldId());
        if (plan == null || world == null || !ownsPlan(world,site,plan)) return;
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
        normalizePlanStates(world, site, plan, site.buildFromStage(), town.getUUID());
        completionHandler.complete(player, town, site.projectId(), site.targetStage());
        site.complete();
        if(refunds!=null)refunds.complete(town.getUUID(),site.projectId());
        dataStore.markDirty();
        dataStore.save();
    }

    private void migrateLegacySites() {
        int migrated = 0;
        int removed = 0;
        int restored = 0;
        boolean changed = false;
        for (TownData town : dataStore.towns().values()) {
            if (towny.town(town.townId()) == null) continue;
            for (ConstructionSite site : town.constructionSites().values()) {
                if (site.architectureVersion() >= ConstructionSite.CURRENT_ARCHITECTURE_VERSION) continue;
                World world = Bukkit.getWorld(site.worldId());
                int visibleLevel = site.active() ? site.targetStage() : site.completedStage();
                BlueprintPlan legacy = visibleLevel <= 0 ? null
                        : generator.generateForArchitecture(site.projectId(), visibleLevel, site.architectureVersion());
                BlueprintPlan modern = visibleLevel <= 0 ? null : generator.generate(site.projectId(), visibleLevel);
                if (world == null || (visibleLevel > 0 && (legacy == null || modern == null))) continue;

                if (ru.neverland.townybuilds.integration.FireRepairAccess.blocked(town.townId(),site,legacy) || ru.neverland.townybuilds.integration.FireRepairAccess.blocked(town.townId(),site,modern)) continue;
                if (legacy != null && !ownsPlan(world,site,legacy) || modern != null && !ownsPlan(world,site,modern)) continue;
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
            if (towny.town(town.townId()) == null) continue;
            for (ConstructionSite site : town.constructionSites().values()) {
                if (site.active() || site.completedStage() <= 0) continue;
                World world = Bukkit.getWorld(site.worldId());
                BlueprintPlan plan = planForSite(site, site.completedStage());
                if (world == null || plan == null || !ownsPlan(world,site,plan)) continue;
                if (ru.neverland.townybuilds.integration.FireRepairAccess.blocked(town.townId(),site,plan)) continue;
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
                normalizePlanStates(world, site, plan, 1, town.townId());
            }
        }
        if (repaired > 0) plugin.getLogger().info("Обновлены завершённые процедурные здания: восстановлено блоков " + repaired + ".");
    }

    private void normalizePlanStates(World world, ConstructionSite site, BlueprintPlan plan, int fromStage, UUID townId) {
        for (Map.Entry<BlockOffset, BlueprintBlock> entry : orderedEntries(plan)) {
            BlueprintBlock expected = entry.getValue();
            if (expected.stage() < fromStage) continue;
            Block block = site.location(world, entry.getKey()).getBlock();
            Town owner = towny.townAt(block.getLocation());
            if (owner == null || !owner.getUUID().equals(townId)) continue;
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
        orientExpectedState(data, expected, site);
        if (data instanceof MultipleFacing multipleFacing) connect(block, multipleFacing);
        block.setBlockData(data, false);
    }

    static void orientExpectedState(BlockData data, BlueprintBlock expected, ConstructionSite site) {
        if(site.projectId().equals("seed_vault")&&data instanceof org.bukkit.block.data.type.Leaves leaves)leaves.setPersistent(true);
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
        for(var entry:new ArrayList<>(obstructionPreviews.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            ObstructionPreview preview = entry.getValue();
            if (player == null || preview.expiresAt() <= now) { obstructionPreviews.remove(entry.getKey()); continue; }
            ru.neverland.core.SuiteScheduler.player(plugin,player,current->{
            if(obstructionPreviews.get(current.getUniqueId())!=preview)return;
            preview.blocks().removeIf(obstacle -> {
                Location location = obstacle.location();
                World world = location.getWorld();
                return world == null || (ru.neverland.core.SuiteScheduler.owns(location) && world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)
                        && location.getBlock().getType() != obstacle.material());
            });
            if (preview.blocks().isEmpty()) { obstructionPreviews.remove(entry.getKey()); return; }
            for (Obstruction obstacle : preview.blocks()) {
                Location location = obstacle.location();
                if (player.getWorld().equals(location.getWorld())
                        && player.getLocation().distanceSquared(location) <= 64 * 64) {
                    renderOutline(player, location, red);
                }
            }
            });
        }
    }

    private void renderMissingBlocks() {
        renderObstructions();
        if (!plugin.getConfig().getBoolean("settings.construction.preview-enabled", true)) {
            clearPreviews();
            return;
        }
        long generation = previewGeneration;
        double distance = Math.max(16, Math.min(128, plugin.getConfig().getDouble("settings.construction.preview-distance", 64)));
        int perPlayer = Math.max(1, Math.min(2048, plugin.getConfig().getInt("settings.construction.preview-max-blocks-per-player", 512)));
        int serverLimit = Math.max(1, Math.min(16384, plugin.getConfig().getInt("settings.construction.preview-max-blocks-total", 4096)));
        float scale = (float) Math.max(0.25, Math.min(0.95, plugin.getConfig().getDouble("settings.construction.preview-block-scale", 0.85)));
        Particle.DustOptions outline = new Particle.DustOptions(Color.fromRGB(255, 212, 90), 0.9f);
        ru.neverland.core.SuiteScheduler.players(plugin, player -> {
            if (!running || previewGeneration != generation) return;
            Town town = towny.town(player);
            if (town == null) { removePreview(player.getUniqueId()); return; }
            TownData data = dataStore.town(town.getUUID());
            World world = player.getWorld();
            Location eye = player.getLocation();
            List<PreviewTarget> targets = new ArrayList<>();
            for (ConstructionSite site : data.constructionSites().values()) {
                if (!site.active() || site.adminPaused() || refundBlocked(town.getUUID(),site.projectId()) || !world.getUID().equals(site.worldId())) continue;
                BlueprintPlan plan = planForSite(site, site.targetStage());
                // An unknown lower block must never be treated as complete on a Folia region boundary.
                if (plan == null || !ownsPlan(world, site, plan)) continue;
                if (plan.blocks().keySet().stream().map(offset -> site.location(world, offset))
                        .anyMatch(loc -> !world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4))) continue;
                ConstructionLayers.Layer layer = ConstructionLayers.inspect(plan, site.buildFromStage(), site.targetStage(),
                        (offset, expected) -> site.location(world, offset).getBlock().getType() == expected.material());
                if (layer.complete()) { checkCompletion(player, town, site); continue; }
                String remaining = remainingMaterials(layer);
                for (ConstructionLayers.Cell cell : layer.missing()) {
                    Location location = site.location(world, cell.offset());
                    double squared = location.clone().add(0.5, 0.5, 0.5).distanceSquared(eye);
                    if (squared <= distance * distance) targets.add(new PreviewTarget(location, cell.block(), squared, site, layer, remaining));
                }
            }
            targets.sort(Comparator.comparingDouble(PreviewTarget::distanceSquared)
                    .thenComparingInt(t -> t.location().getBlockX()).thenComparingInt(t -> t.location().getBlockZ()));
            List<ConstructionBlockDisplays.Hint> hints = new ArrayList<>();
            for (PreviewTarget target : targets.stream().limit(perPlayer).toList()) {
                BlockData block = target.expected().material().createBlockData();
                orientExpectedState(block, target.expected(), target.site());
                hints.add(new ConstructionBlockDisplays.Hint(target.location(), block));
            }
            blockDisplays.update(player, hints, perPlayer, serverLimit, scale);
            if (targets.isEmpty()) { removeGuide(player.getUniqueId()); return; }
            PreviewTarget nearest = targets.get(0);
            renderOutline(player, nearest.location(), outline);
            renderGuideDisplay(player, nearest);
            player.sendActionBar(messages.component("construction-layer-guide", guideValues(nearest)));
        });
        Set<UUID> viewers = new java.util.HashSet<>(guideDisplays.keySet());
        viewers.addAll(blockDisplays.viewers());
        for (UUID playerId : viewers) if (Bukkit.getPlayer(playerId) == null) removePreview(playerId);
    }

    private String remainingMaterials(ConstructionLayers.Layer layer) {
        Map<Material, Integer> remaining = new java.util.TreeMap<>(Comparator.comparing(Material::name));
        layer.missing().forEach(cell -> remaining.merge(ConstructionSupply.material(cell.block().material()), 1, Integer::sum));
        List<String> parts = remaining.entrySet().stream().limit(3)
                .map(e -> itemNames.name(e.getKey()) + " × " + e.getValue()).toList();
        return String.join(", ", parts) + (remaining.size() > 3 ? " и другие" : "");
    }

    private Map<String, Object> guideValues(PreviewTarget target) {
        return Map.of("expected", itemNames.name(ConstructionSupply.material(target.expected().material())),
                "x", target.location().getBlockX(), "y", target.location().getBlockY(), "z", target.location().getBlockZ(),
                "distance", String.format(Locale.ROOT, "%.1f", Math.sqrt(target.distanceSquared())),
                "layer", target.layer().number(), "layers", target.layer().layers(),
                "placed", target.layer().placed(), "total", target.layer().total(), "remaining", target.remaining());
    }

    private void removePreview(UUID playerId) { removeGuide(playerId); blockDisplays.remove(playerId); }

    private void clearPreviews() {
        blockDisplays.clear();
        for (UUID playerId : List.copyOf(guideDisplays.keySet())) removeGuide(playerId);
    }

    @EventHandler public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        try (var suiteGuard14 = ru.neverland.core.SuiteState.guard()) { removePreview(event.getPlayer().getUniqueId()); obstructionPreviews.remove(event.getPlayer().getUniqueId()); }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(org.bukkit.event.player.PlayerTeleportEvent event) {
        try (var suiteGuard15 = ru.neverland.core.SuiteState.guard()) { removePreview(event.getPlayer().getUniqueId()); }
    }

    private void renderGuideDisplay(Player player, PreviewTarget target) {
        if (!ru.neverland.core.WorldTasks.ownsBox(target.location(), 2)) { removeGuide(player.getUniqueId()); return; }
        if (!plugin.getConfig().getBoolean("settings.construction.guide-label-enabled", true)) {
            removeGuide(player.getUniqueId());
            return;
        }
        double height = plugin.getConfig().getDouble("settings.construction.guide-label-height", 1.35);
        Location labelLocation = target.location().clone().add(0.5, Math.max(0.6, height), 0.5);
        TextDisplay display = guideDisplays.get(player.getUniqueId());
        if (display == null || !ru.neverland.core.SuiteScheduler.owns(display) || !display.isValid() || !display.getWorld().equals(labelLocation.getWorld())) {
            removeGuide(player.getUniqueId());
            display = labelLocation.getWorld().spawn(labelLocation, TextDisplay.class, created -> {
                created.setPersistent(false);
                created.setInvulnerable(true);
                created.setGravity(false);
                created.setVisibleByDefault(false);
                created.setBillboard(Display.Billboard.CENTER);
                created.setViewRange(1.0f);
                created.setLineWidth(200);
                created.setAlignment(TextDisplay.TextAlignment.CENTER);
                created.setShadowed(true);
                created.setSeeThrough(true);
                created.setDefaultBackground(false);
                created.setBackgroundColor(Color.fromARGB(170, 18, 36, 58));
            });
            guideDisplays.put(player.getUniqueId(), display);
        } else {
            display.teleportAsync(labelLocation);
        }
        double labelScale=plugin.getConfig().getDouble("settings.construction.guide-label-scale",0.45);
        if(!Double.isFinite(labelScale))labelScale=0.45;
        display.setTransformationMatrix(new org.joml.Matrix4f().scale((float)Math.max(0.2,Math.min(0.8,labelScale))));
        display.setLineWidth(Math.max(100,Math.min(300,plugin.getConfig().getInt("settings.construction.guide-label-width",200))));
        display.text(messages.component("construction-layer-guide-display", guideValues(target)));
        player.showEntity(plugin, display);
    }

    private void removeGuide(UUID playerId) {
        TextDisplay display = guideDisplays.remove(playerId);
        ru.neverland.core.WorldTasks.remove(plugin, display);
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
        return itemNames.name(material);
    }

    private record LocatedBlock(UUID townId, ConstructionSite site, BlueprintBlock expected) { }
    private record PreviewTarget(Location location, BlueprintBlock expected, double distanceSquared,
                                 ConstructionSite site, ConstructionLayers.Layer layer, String remaining) { }
    public java.util.List<ru.neverland.core.ActivityAdmin.Target> adminTargets(){ try (var suiteGuard16 = ru.neverland.core.SuiteState.guard()) {
        ru.neverland.core.ApiServices.primaryThread();var result=new java.util.ArrayList<ru.neverland.core.ActivityAdmin.Target>();
        dataStore.towns().forEach((id,town)->town.constructionSites().values().stream().filter(ConstructionSite::active).forEach(site->result.add(new ru.neverland.core.ActivityAdmin.Target(id+"/"+site.projectId(),site.projectId()+" / этап "+site.targetStage(),java.util.Set.of("status","pause","resume","cancel"),(action,minutes)->{
            ru.neverland.core.ApiServices.primaryThread();if(!site.active())throw new IllegalStateException("Строительство уже завершено");
            if(action.equals("cancel")) {try{return refunds.cancel(id,site.projectId(),null,true,null,"nltadmin");}catch(java.io.IOException ex){throw new java.io.UncheckedIOException(ex);}}
            if(refundBlocked(id,site.projectId()))return ConstructionRefunds.status(refunds.receipt(id,site.projectId()));
            if(action.equals("status"))return id+" / "+site.projectId()+" / "+(site.adminPaused()?"Пауза":"Строительство")+"; этап "+site.targetStage();
            boolean before=site.adminPaused();try{site.adminPaused(action.equals("pause"));dataStore.markDirty();dataStore.save();}catch(Exception ex){site.adminPaused(before);throw ex;}
            return "Строительство "+(site.adminPaused()?"приостановлено":"продолжено")+"; блоки и оплаченный этап сохранены";
        }))));return java.util.List.copyOf(result);
    } }

}
