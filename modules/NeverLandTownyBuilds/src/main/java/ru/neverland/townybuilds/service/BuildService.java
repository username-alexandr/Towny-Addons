package ru.neverland.townybuilds.service;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townybuilds.data.DataStore;
import ru.neverland.townybuilds.data.ResourceFund;
import ru.neverland.townybuilds.data.TownData;
import ru.neverland.townybuilds.integration.TownyHook;
import ru.neverland.townybuilds.integration.ArchaeologyBridge;
import ru.neverland.townybuilds.model.LevelDefinition;
import ru.neverland.townybuilds.model.ProjectDefinition;
import ru.neverland.townybuilds.model.ProjectType;
import ru.neverland.townybuilds.util.ColorUtil;
import ru.neverland.townybuilds.construction.ConstructionPreparation;
import ru.neverland.townybuilds.construction.ConstructionProgress;
import ru.neverland.townybuilds.construction.ConstructionService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class BuildService {
    private final JavaPlugin plugin;
    private final TownyHook towny;
    private final DataStore dataStore;
    private final MessageService messages;
    private final ArchaeologyBridge archaeology;
    private final RussianItemNames itemNames;
    private final DefinitionRegistry definitions;
    private ConstructionService construction;

    public BuildService(JavaPlugin plugin, TownyHook towny, DataStore dataStore, MessageService messages,
                        ArchaeologyBridge archaeology, RussianItemNames itemNames,
                        DefinitionRegistry definitions) {
        this.plugin = plugin;
        this.towny = towny;
        this.dataStore = dataStore;
        this.messages = messages;
        this.archaeology = archaeology;
        this.itemNames = itemNames;
        this.definitions = definitions;
    }

    public void setConstruction(ConstructionService construction) {
        this.construction = construction;
    }

    public UpgradeResult upgrade(Player player, ProjectDefinition project) {
        Town town = towny.town(player);
        if (town == null) {
            return UpgradeResult.of(UpgradeResult.Status.NO_TOWN);
        }
        if (!towny.isMayor(player, town)) {
            return UpgradeResult.of(UpgradeResult.Status.NOT_MAYOR);
        }
        TownData data = dataStore.town(town.getUUID());
        int current = data.level(project.id());
        if (current >= project.maxLevel()) {
            return placePreviouslyUnlockedWonder(player, town, data, project, current);
        }
        int next = current + 1;
        LevelDefinition level = project.level(next);
        if (level == null) {
            return UpgradeResult.of(UpgradeResult.Status.MAX_LEVEL);
        }
        ConstructionPreparation preparation = null;
        boolean procedural = usesConstruction(project);
        if (procedural) {
            preparation = construction.prepare(player, town, project, current, next);
            UpgradeResult constructionFailure = constructionFailure(preparation, level.money(), next);
            if (constructionFailure != null) return constructionFailure;
        }
        if (project.type() == ProjectType.WONDER) {
            List<String> missingArtifacts = archaeology.missingLines(town.getUUID(), project.id());
            if (!missingArtifacts.isEmpty()) {
                return new UpgradeResult(UpgradeResult.Status.NOT_ENOUGH_ARTIFACTS, level.money(), missingArtifacts, next);
            }
        }
        if (!town.getAccount().canPayFromHoldings(level.money())) {
            return new UpgradeResult(UpgradeResult.Status.NOT_ENOUGH_MONEY, level.money(), List.of(), next);
        }
        ResourceFund fund = data.resourceFund(project.id(), next);
        List<String> missing = missing(level.resources(), fund);
        if (!missing.isEmpty()) {
            return new UpgradeResult(UpgradeResult.Status.NOT_ENOUGH_RESOURCES, level.money(), missing, next);
        }
        String reason = plugin.getConfig().getString("settings.economy.withdraw-reason", "Городская постройка")
                .replace("{project}", ColorUtil.plain(project.name())).replace("{level}", String.valueOf(next));
        if (level.money() > 0 && !town.getAccount().withdraw(level.money(), reason)) {
            return UpgradeResult.of(UpgradeResult.Status.ECONOMY_ERROR);
        }
        if (project.type() == ProjectType.WONDER && !archaeology.consume(town.getUUID(), project.id(), player.getUniqueId())) {
            if (level.money() > 0) town.getAccount().deposit(level.money(), "Возврат: артефакты для Чуда Света не списаны");
            List<String> missingArtifacts = archaeology.missingLines(town.getUUID(), project.id());
            if (missingArtifacts.isEmpty()) missingArtifacts = List.of("Операция музея отменена");
            return new UpgradeResult(UpgradeResult.Status.NOT_ENOUGH_ARTIFACTS, level.money(), missingArtifacts, next);
        }
        if (procedural) {
            try {
                construction.commit(preparation, player, town);
            } catch (RuntimeException exception) {
                plugin.getLogger().severe("Не удалось активировать чертёж " + project.id() + ": " + exception.getMessage());
                if (level.money() > 0) town.getAccount().deposit(level.money(), "Возврат: чертёж не активирован");
                return UpgradeResult.of(UpgradeResult.Status.ECONOMY_ERROR);
            }
        }
        data.clearResourceFund(project.id());
        if (procedural) {
            dataStore.markDirty();
            dataStore.save();
            return new UpgradeResult(UpgradeResult.Status.CONSTRUCTION_STARTED, level.money(), List.of(), next);
        }
        data.setLevel(project.id(), next);
        town.setBonusBlocks(town.getBonusBlocks() + level.bonusBlocks());
        town.save();
        dataStore.markDirty();
        dataStore.save();
        runCommands(player, town, level);
        if (project.type() == ProjectType.WONDER) {
            completeWonder(player, town, project);
        }
        return new UpgradeResult(UpgradeResult.Status.SUCCESS, level.money(), List.of(), next);
    }

    public ConstructionProgress constructionProgress(Town town, ProjectDefinition project) {
        if (!usesConstruction(project)) {
            return new ConstructionProgress(false, 0, 0, 0, "");
        }
        return construction.progress(town, project.id());
    }

    public boolean usesConstruction(ProjectDefinition project) {
        return project != null && construction != null && construction.supports(project.id());
    }

    public boolean needsPhysicalPlacement(Town town, ProjectDefinition project) {
        if (town == null || project == null || project.type() != ProjectType.WONDER || !usesConstruction(project)) {
            return false;
        }
        TownData data = dataStore.town(town.getUUID());
        return data.level(project.id()) >= project.maxLevel() && data.constructionSite(project.id()) == null;
    }

    public void completeConstruction(Player player, Town town, String projectId, int level) {
        ProjectDefinition project = definitions.project(projectId);
        if (project == null || town == null) return;
        TownData data = dataStore.town(town.getUUID());
        if (data.level(projectId) >= level) {
            if (project.type() == ProjectType.WONDER) {
                messages.send(player, "wonder-placement-complete", Map.of("project", project.name()));
            }
            return;
        }
        LevelDefinition definition = project.level(level);
        if (definition == null) return;
        data.setLevel(projectId, level);
        town.setBonusBlocks(town.getBonusBlocks() + definition.bonusBlocks());
        town.save();
        dataStore.markDirty();
        dataStore.save();
        runCommands(player, town, definition);
        messages.send(player, "construction-complete", Map.of("project", project.name(), "level", level));
        if (project.type() == ProjectType.WONDER) completeWonder(player, town, project);
    }

    private UpgradeResult placePreviouslyUnlockedWonder(Player player, Town town, TownData data,
                                                        ProjectDefinition project, int current) {
        if (project.type() != ProjectType.WONDER || !usesConstruction(project)) {
            return UpgradeResult.of(UpgradeResult.Status.MAX_LEVEL);
        }
        var existing = data.constructionSite(project.id());
        if (existing != null) {
            return UpgradeResult.of(existing.active()
                    ? UpgradeResult.Status.CONSTRUCTION_IN_PROGRESS : UpgradeResult.Status.MAX_LEVEL);
        }
        ConstructionPreparation preparation = construction.prepare(player, town, project, 0, current);
        UpgradeResult failure = constructionFailure(preparation, 0, current);
        if (failure != null) return failure;
        try {
            construction.commit(preparation, player, town);
            dataStore.markDirty();
            dataStore.save();
            return new UpgradeResult(UpgradeResult.Status.CONSTRUCTION_STARTED, 0, List.of(), current);
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("Не удалось разместить ранее открытое Чудо " + project.id()
                    + ": " + exception.getMessage());
            return UpgradeResult.of(UpgradeResult.Status.ECONOMY_ERROR);
        }
    }

    private UpgradeResult constructionFailure(ConstructionPreparation preparation, double money, int level) {
        return switch (preparation.status()) {
            case READY -> null;
            case ALREADY_ACTIVE -> new UpgradeResult(UpgradeResult.Status.CONSTRUCTION_IN_PROGRESS, money, List.of(), level);
            case NO_GROUND_TARGET -> new UpgradeResult(UpgradeResult.Status.CONSTRUCTION_NO_TARGET, money, preparation.details(), level);
            case OUTSIDE_TOWN -> new UpgradeResult(UpgradeResult.Status.CONSTRUCTION_OUTSIDE_TOWN, money, preparation.details(), level);
            case UNEVEN_GROUND -> new UpgradeResult(UpgradeResult.Status.CONSTRUCTION_UNEVEN_GROUND, money, preparation.details(), level);
            case OBSTRUCTED -> new UpgradeResult(UpgradeResult.Status.CONSTRUCTION_OBSTRUCTED, money, preparation.details(), level);
            case UNKNOWN_BLUEPRINT -> new UpgradeResult(UpgradeResult.Status.CONSTRUCTION_UNAVAILABLE, money, preparation.details(), level);
        };
    }

    public List<String> artifactRequirements(Town town, ProjectDefinition project) {
        if (town == null || project == null || project.type() != ProjectType.WONDER) return List.of();
        return archaeology.requirementLines(town.getUUID(), project.id());
    }

    public int contributed(Town town, ProjectDefinition project, int targetLevel, ItemStack required) {
        if (town == null || project == null || required == null) return 0;
        TownData data = dataStore.town(town.getUUID());
        ResourceFund fund = data.existingResourceFund(project.id());
        if (fund == null || fund.targetLevel() != targetLevel) return 0;
        return Math.min(required.getAmount(), fund.count(required));
    }

    public ContributionResult contributeFromPlayer(Player player, ProjectDefinition project) {
        Town town = towny.town(player);
        if (town == null) return ContributionResult.of(ContributionResult.Status.NO_TOWN);
        return contribute(player, town, project, false);
    }

    public ContributionResult contributeFromStorage(Player player, ProjectDefinition project) {
        Town town = towny.town(player);
        if (town == null) return ContributionResult.of(ContributionResult.Status.NO_TOWN);
        if (!towny.isMayor(player, town)) return ContributionResult.of(ContributionResult.Status.NOT_MAYOR);
        return contribute(player, town, project, true);
    }

    private ContributionResult contribute(Player player, Town town, ProjectDefinition project, boolean cityStorage) {
        TownData data = dataStore.town(town.getUUID());
        int targetLevel = data.level(project.id()) + 1;
        LevelDefinition level = project.level(targetLevel);
        if (level == null) return ContributionResult.of(ContributionResult.Status.MAX_LEVEL);
        if (level.resources().isEmpty()) return ContributionResult.of(ContributionResult.Status.NOTHING_NEEDED);

        ResourceFund fund = data.resourceFund(project.id(), targetLevel);
        ItemStack[] contents = cityStorage
                ? data.storage()
                : cloneContents(player.getInventory().getStorageContents());
        List<PendingContribution> pending = new ArrayList<>();
        List<String> details = new ArrayList<>();

        for (ItemStack required : level.resources()) {
            int needed = Math.max(0, required.getAmount() - fund.count(required));
            if (needed == 0) continue;
            int before = ResourceTransfer.count(contents, required);
            int requested = Math.min(before, needed);
            if (requested <= 0) continue;
            int notRemoved = ResourceTransfer.remove(contents, required, requested);
            int removed = requested - notRemoved;
            if (removed > 0) pending.add(new PendingContribution(required, removed));
        }
        if (pending.isEmpty()) return ContributionResult.of(ContributionResult.Status.NOTHING_MATCHED);

        if (cityStorage) {
            data.setStorage(contents, plugin.getConfig().getInt("settings.storage.size", 54));
        } else {
            Map<ItemStack, Integer> beforeCounts = new java.util.IdentityHashMap<>();
            for (PendingContribution contribution : pending) {
                beforeCounts.put(contribution.template(), ResourceTransfer.count(player.getInventory().getStorageContents(), contribution.template()));
            }
            try {
                player.getInventory().setStorageContents(contents);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Не удалось синхронизировать взнос игрока " + player.getName()
                        + ": " + exception.getMessage());
                return ContributionResult.of(ContributionResult.Status.INVENTORY_SYNC_FAILED);
            }
            ItemStack[] actual = player.getInventory().getStorageContents();
            List<PendingContribution> verified = new ArrayList<>();
            for (PendingContribution contribution : pending) {
                int before = beforeCounts.getOrDefault(contribution.template(), 0);
                int after = ResourceTransfer.count(actual, contribution.template());
                int removed = Math.min(contribution.amount(), Math.max(0, before - after));
                if (removed > 0) verified.add(new PendingContribution(contribution.template(), removed));
            }
            pending = verified;
            if (pending.isEmpty()) {
                return ContributionResult.of(ContributionResult.Status.INVENTORY_SYNC_FAILED);
            }
        }

        int total = 0;
        for (PendingContribution contribution : pending) {
            fund.add(contribution.template(), contribution.amount());
            total += contribution.amount();
            details.add(itemNames.name(contribution.template()) + " x" + contribution.amount());
        }
        dataStore.markDirty();
        dataStore.save();
        return new ContributionResult(ContributionResult.Status.SUCCESS, total, details);
    }

    private ItemStack[] cloneContents(ItemStack[] source) {
        ItemStack[] copy = new ItemStack[source.length];
        for (int index = 0; index < source.length; index++) {
            copy[index] = source[index] == null ? null : source[index].clone();
        }
        return copy;
    }

    private List<String> missing(List<ItemStack> requirements, ResourceFund fund) {
        List<String> missing = new ArrayList<>();
        for (ItemStack required : requirements) {
            int found = fund.count(required);
            if (found < required.getAmount()) {
                missing.add(itemNames.name(required) + " x" + (required.getAmount() - found));
            }
        }
        return missing;
    }

    private record PendingContribution(ItemStack template, int amount) { }

    private void runCommands(Player player, Town town, LevelDefinition level) {
        for (String command : level.commands()) {
            String parsed = command.replace("{player}", player.getName()).replace("{town}", town.getName())
                    .replace("{level}", String.valueOf(level.level()));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed.startsWith("/") ? parsed.substring(1) : parsed);
        }
    }

    private void completeWonder(Player player, Town town, ProjectDefinition project) {
        messages.send(player, "wonder-complete-local", Map.of("project", project.name()));
        if (plugin.getConfig().getBoolean("settings.announcements.wonder-complete", true)) {
            Bukkit.broadcast(messages.component("wonder-broadcast", Map.of("town", town.getName(), "project", project.name())));
        }
        if (!plugin.getConfig().getBoolean("settings.announcements.fireworks", true)) {
            return;
        }
        int count = Math.max(1, plugin.getConfig().getInt("settings.announcements.firework-count", 8));
        Location origin = player.getLocation().clone();
        for (int index = 0; index < count; index++) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> launchFirework(origin), index * 8L);
        }
    }

    private void launchFirework(Location origin) {
        if (origin.getWorld() == null) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location location = origin.clone().add(random.nextDouble(-4, 4), 1, random.nextDouble(-4, 4));
        Firework firework = origin.getWorld().spawn(location, Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        meta.setPower(1);
        meta.addEffect(FireworkEffect.builder().flicker(true).trail(true)
                .with(random.nextBoolean() ? FireworkEffect.Type.BALL_LARGE : FireworkEffect.Type.STAR)
                .withColor(Color.fromRGB(random.nextInt(80, 256), random.nextInt(60, 220), random.nextInt(120, 256)))
                .withFade(Color.AQUA).build());
        firework.setFireworkMeta(meta);
    }

}
