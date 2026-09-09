package ru.neverland.mintexpeditions.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.mintexpeditions.integration.CampFacade;
import ru.neverland.mintexpeditions.integration.BuildBridge;
import ru.neverland.mintexpeditions.integration.TownyHook;
import ru.neverland.mintexpeditions.model.ActiveExpedition;
import ru.neverland.mintexpeditions.model.BlockPos;
import ru.neverland.mintexpeditions.model.ExpeditionDefinition;
import ru.neverland.mintexpeditions.model.ExpeditionHistory;
import ru.neverland.mintexpeditions.model.ExpeditionStatus;
import ru.neverland.mintexpeditions.model.ItemAmount;
import ru.neverland.mintexpeditions.util.ColorUtil;
import ru.neverland.mintexpeditions.util.TimeUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ExpeditionService {
    public enum StartResult {
        QUEUED, NO_CAMP, INACTIVE, TOO_FAR, STASH_OPEN, LOW_LEVEL, ACTIVE, MISSING, WORLD_BLOCKED
    }

    private final JavaPlugin plugin;
    private final MessageService messages;
    private final ExpeditionRegistry registry;
    private final ExpeditionRepository repository;
    private final CampFacade camps;
    private final SiteService sites;
    private final TownyHook towny;
    private final BuildBridge builds;
    private final ReturnTickets returnTickets;
    private final Map<UUID, Long> shieldWarnings = new HashMap<>();
    private final Set<UUID> returning = new HashSet<>();
    private final Set<UUID> preparing = new HashSet<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private BukkitTask task;
    private BukkitTask shieldTask;

    public ExpeditionService(JavaPlugin plugin, MessageService messages, ExpeditionRegistry registry,
                             ExpeditionRepository repository, CampFacade camps, SiteService sites,
                             TownyHook towny, BuildBridge builds) {
        this.plugin = plugin;
        this.messages = messages;
        this.registry = registry;
        this.repository = repository;
        this.camps = camps;
        this.sites = sites;
        this.towny = towny;
        this.builds = builds;
        this.returnTickets = new ReturnTickets(plugin);
    }

    public void startTasks() {
        stopTasks();
        long period = Math.max(20,
                plugin.getConfig().getLong("expeditions.expiry-check-seconds", 10) * 20);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, period, period);
        shieldTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getActiveItem().getType() == Material.SHIELD && shieldDenied(player)) {
                    player.clearActiveItem();
                    warnShield(player);
                }
            }
        }, 1, 1);
        for (ActiveExpedition expedition : repository.active()) bar(expedition);
    }

    public void stopTasks() {
        if (task != null) task.cancel();
        if (shieldTask != null) shieldTask.cancel();
        task = shieldTask = null;
        for (BossBar bar : bars.values()) bar.removeAll();
        bars.clear();
        shieldWarnings.clear();
    }

    public StartResult start(Player leader, ExpeditionDefinition definition) {
        CampFacade.CampView camp = camps.owned(leader);
        if (camp == null) return StartResult.NO_CAMP;
        if (plugin.getConfig().getBoolean("camps.require-active-fire", true)
                && camp.burnUntil() <= System.currentTimeMillis()) return StartResult.INACTIVE;
        double radius = 6 + (camp.level() - 1) * 3
                + plugin.getConfig().getDouble("camps.start-radius-extra", 2);
        if (camp.world() == null || !leader.getWorld().equals(camp.world())
                || leader.getLocation().distanceSquared(camp.anchor()) > radius * radius) {
            return StartResult.TOO_FAR;
        }
        if (plugin.getConfig().getBoolean("camps.block-start-while-stash-open", true)
                && camps.stashOpen(camp.owner())) return StartResult.STASH_OPEN;
        if (camp.level() < definition.minCampLevel()) return StartResult.LOW_LEVEL;
        if (repository.byCamp(camp.owner()) != null || preparing.contains(camp.owner())) {
            return StartResult.ACTIVE;
        }
        if (plugin.getConfig().getStringList("generation.blocked-worlds")
                .contains(leader.getWorld().getName())) return StartResult.WORLD_BLOCKED;
        String missing = camps.missing(camp, definition.costs());
        if (!missing.isEmpty()) {
            messages.send(leader, "not-enough-supplies", Map.of("supplies", missing));
            return StartResult.MISSING;
        }

        UUID townId = towny.townId(leader);
        preparing.add(camp.owner());
        messages.send(leader, "preparing");
        sites.findSite(camp.world(), camp.anchor(), definition, site -> {
            preparing.remove(camp.owner());
            if (site == null) {
                messages.send(leader, "site-not-found");
                return;
            }

            long now = System.currentTimeMillis();
            long durationMillis = Math.max(1000, Math.round(definition.durationSeconds() * 1000
                    * builds.expeditionTimeMultiplier(townId)));
            ActiveExpedition expedition = new ActiveExpedition(
                    UUID.randomUUID(), camp.owner(), definition.id(),
                    camp.world().getUID(), camp.world().getName(),
                    BlockPos.of(camp.anchor()), site, now,
                    now + durationMillis);
            List<Player> party = camps.party(camp);
            party.forEach(player -> expedition.participants().add(player.getUniqueId()));

            if (!sites.build(expedition, definition)) {
                repository.remove(expedition);
                repository.save();
                messages.send(leader, "start-failed");
                return;
            }
            if (!camps.consume(camp, definition.costs())) {
                sites.restore(expedition, () -> {
                    repository.remove(expedition);
                    repository.save();
                });
                messages.send(leader, "start-failed");
                return;
            }

            bar(expedition);
            messages.send(leader, "started", Map.of(
                    "expedition", ColorUtil.strip(definition.name()),
                    "party", party.size()));

            boolean teleport = plugin.getConfig()
                    .getBoolean("expeditions.teleport-party-to-site", false);
            Location destination = expedition.siteLocation();
            for (Player player : party) {
                if (!player.equals(leader)) {
                    messages.send(player, "party-member",
                            Map.of("expedition", ColorUtil.strip(definition.name())));
                }
                sendTarget(player, expedition);
                if (teleport && destination != null) player.teleportAsync(destination);
            }
        });
        return StartResult.QUEUED;
    }

    public boolean objective(Player player, BlockPos position) {
        ActiveExpedition expedition = at(position, player.getWorld());
        if (expedition == null || !expedition.objectives().contains(position)
                || expedition.completed().contains(position)) return false;
        if (!expedition.participants().contains(player.getUniqueId())) {
            messages.send(player, "not-participant");
            return true;
        }
        expedition.completed().add(position);
        position.location(player.getWorld()).getBlock().setType(Material.AIR, false);
        repository.changed();
        ExpeditionDefinition definition = registry.get(expedition.definitionId());
        broadcast(expedition, "objective", Map.of(
                "progress", expedition.objectiveProgress(), "goal", definition.goal()));
        updateBar(expedition);
        check(expedition, definition);
        return true;
    }

    public void mobDied(LivingEntity entity) {
        ActiveExpedition expedition = sites.expeditionForMob(entity);
        if (expedition == null || expedition.status() != ExpeditionStatus.ACTIVE) return;
        expedition.spawnedMobs().remove(entity.getUniqueId());
        ExpeditionDefinition definition = registry.get(expedition.definitionId());
        if (definition == null) return;

        Player killer = entity.getKiller();
        if (killer != null && expedition.participants().contains(killer.getUniqueId())) {
            expedition.kills(expedition.kills() + 1);
            repository.changed();
            broadcast(expedition, "mob-progress", Map.of(
                    "progress", expedition.kills(), "goal", definition.mobCount()));
            updateBar(expedition);
            check(expedition, definition);
            return;
        }

        repository.changed();
        if (!plugin.getConfig().getBoolean("combat.respawn-uncredited-deaths", true)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (expedition.status() == ExpeditionStatus.ACTIVE
                    && repository.get(expedition.id()) != null) {
                sites.respawn(expedition, definition, 1);
            }
        });
    }

    public ActiveExpedition expeditionForMob(Entity entity) {
        return sites.expeditionForMob(entity);
    }

    public boolean insideMobArea(ActiveExpedition expedition, Location location) {
        return sites.insideMobArea(expedition, location);
    }

    public boolean preventMobTeleportOutside() {
        return plugin.getConfig().getBoolean("combat.prevent-teleport-outside", true);
    }

    public boolean protectMobFromEnvironment() {
        return plugin.getConfig().getBoolean("combat.protect-from-fall-and-suffocation", true);
    }

    public boolean relocateMob(Entity entity) {
        return sites.relocateMob(entity);
    }

    public void relocateMobNextTick(Entity entity) {
        UUID entityId = entity.getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Entity current = Bukkit.getEntity(entityId);
            if (current != null && current.isValid() && !current.isDead()) {
                sites.relocateMob(current);
            }
        });
    }

    private void check(ActiveExpedition expedition, ExpeditionDefinition definition) {
        if (expedition.objectiveProgress() >= definition.goal()
                && expedition.kills() >= definition.mobCount()) {
            finish(expedition, ExpeditionStatus.COMPLETED);
        }
    }

    public void finish(ActiveExpedition expedition, ExpeditionStatus status) {
        if (expedition.status() != ExpeditionStatus.ACTIVE) return;
        expedition.status(status);
        ExpeditionDefinition definition = registry.get(expedition.definitionId());
        if (status == ExpeditionStatus.COMPLETED) {
            for (UUID participant : expedition.participants()) {
                List<ItemStack> rewards =
                        definition.rewards().stream().map(ItemAmount::stack).toList();
                repository.reward(participant, rewards, definition.moneyPerParticipant());
                Player player = Bukkit.getPlayer(participant);
                if (player != null) {
                    messages.send(player, "completed",
                            Map.of("expedition", ColorUtil.strip(definition.name())));
                    claim(player);
                }
            }
        } else {
            broadcast(expedition, "failed",
                    Map.of("expedition", ColorUtil.strip(definition.name())));
        }

        repository.history(new ExpeditionHistory(
                        expedition.id(), expedition.leaderId(), expedition.definitionId(),
                        expedition.participants().size(), expedition.objectiveProgress(),
                        expedition.kills(), status, System.currentTimeMillis()),
                plugin.getConfig().getInt("expeditions.history-limit", 30));
        BossBar bar = bars.remove(expedition.id());
        if (bar != null) bar.removeAll();
        long returnWindow = Math.max(30, plugin.getConfig().getLong("expeditions.manual-return-window-seconds", 600));
        returnTickets.grant(expedition.participants(), expedition.leaderId(), System.currentTimeMillis() + returnWindow * 1000);
        sites.restore(expedition, () -> {
            repository.remove(expedition);
            repository.save();
        });
    }

    public void claim(Player player) {
        if (!repository.hasReward(player.getUniqueId())) {
            messages.send(player, "no-reward");
            return;
        }
        for (ItemStack item : repository.takeItems(player.getUniqueId())) {
            Map<Integer, ItemStack> extra = player.getInventory().addItem(item);
            if (plugin.getConfig().getBoolean("rewards.drop-overflow-at-player", true)) {
                extra.values().forEach(stack ->
                        player.getWorld().dropItemNaturally(player.getLocation(), stack));
            }
        }
        double amount = repository.money(player.getUniqueId());
        boolean paid = amount <= 0 || towny.reward(player, amount);
        if (paid) repository.clearMoney(player.getUniqueId());
        repository.save();
        messages.send(player, paid ? "claim-success" : "economy-pending",
                Map.of("money", amount));
    }

    public void returnToCamp(Player player) {
        if (!canReturn(player)) { messages.send(player, "return-denied"); return; }
        UUID campOwner = returnCampOwner(player);
        if (campOwner == null) { messages.send(player, "no-return"); return; }
        CampFacade.CampView camp = camps.owned(campOwner);
        if (camp == null || camp.world() == null) { messages.send(player, "return-camp-missing"); return; }
        if (!returning.add(player.getUniqueId())) return;
        try { player.teleportAsync(camp.anchor()).whenComplete((success, error) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                returning.remove(player.getUniqueId());
                if (error == null && Boolean.TRUE.equals(success)) {
                    returnTickets.remove(player.getUniqueId());
                    messages.send(player, "returned");
                } else messages.send(player, "return-failed");
            });
        }); } catch (RuntimeException exception) {
            returning.remove(player.getUniqueId());
            messages.send(player, "return-failed");
        }
    }

    public boolean canReturn(Player player) {
        return ExpeditionPermissions.canReturn(player);
    }

    public boolean hasReturnTarget(Player player) { return returnCampOwner(player) != null; }

    private UUID returnCampOwner(Player player) {
        ActiveExpedition expedition = repository.byLeader(player.getUniqueId());
        if (expedition != null) return expedition.leaderId();
        var ticket = returnTickets.get(player.getUniqueId(), System.currentTimeMillis());
        return ticket == null ? null : ticket.campOwner();
    }

    public boolean shieldDenied(Player player) {
        return ExpeditionPermissions.shieldDenied(player, at(BlockPos.of(player.getLocation()), player.getWorld()) != null);
    }

    public void warnShield(Player player) {
        long now = System.currentTimeMillis();
        if (now - shieldWarnings.getOrDefault(player.getUniqueId(), 0L) < 3000) return;
        shieldWarnings.put(player.getUniqueId(), now);
        messages.send(player, "shield-denied");
    }

    public void sendTarget(Player player, ActiveExpedition expedition) {
        Location target = expedition.siteLocation();
        if (target == null) return;
        long distance = player.getWorld().equals(target.getWorld())
                ? Math.round(player.getLocation().distance(target)) : -1;
        messages.send(player, "target-coordinates", Map.of(
                "world", target.getWorld().getName(),
                "x", target.getBlockX(),
                "y", target.getBlockY(),
                "z", target.getBlockZ(),
                "distance", distance < 0 ? "другой мир" : distance + " блоков"));
    }

    private void tick() {
        long now = System.currentTimeMillis();
        returnTickets.prune(now);
        for (ActiveExpedition expedition : new ArrayList<>(repository.active())) {
            if (now >= expedition.expiresAt()) {
                finish(expedition, ExpeditionStatus.FAILED);
                continue;
            }
            ExpeditionDefinition definition = registry.get(expedition.definitionId());
            if (definition != null) sites.guard(expedition, definition);
            updateBar(expedition);
        }
        repository.saveIfDirty();
    }

    public ActiveExpedition at(BlockPos position, World world) {
        int radius = plugin.getConfig().getInt("generation.protected-radius", 16);
        for (ActiveExpedition expedition : repository.active()) {
            if (!expedition.worldId().equals(world.getUID())) continue;
            int dx = expedition.siteAnchor().x() - position.x();
            int dz = expedition.siteAnchor().z() - position.z();
            if (dx * dx + dz * dz <= radius * radius) return expedition;
        }
        return null;
    }

    public boolean participant(Player player, ActiveExpedition expedition) {
        return expedition != null
                && expedition.participants().contains(player.getUniqueId());
    }

    private void broadcast(ActiveExpedition expedition, String key, Map<String, ?> variables) {
        for (UUID participant : expedition.participants()) {
            Player player = Bukkit.getPlayer(participant);
            if (player != null) messages.send(player, key, variables);
        }
    }

    private void bar(ActiveExpedition expedition) {
        if (!plugin.getConfig().getBoolean("bossbar.enabled", true)) return;
        BarColor color = BarColor.valueOf(
                plugin.getConfig().getString("bossbar.color", "PURPLE"));
        BarStyle style = BarStyle.valueOf(
                plugin.getConfig().getString("bossbar.style", "SEGMENTED_10"));
        BossBar bar = Bukkit.createBossBar("", color, style);
        for (UUID participant : expedition.participants()) {
            Player player = Bukkit.getPlayer(participant);
            if (player != null) bar.addPlayer(player);
        }
        bars.put(expedition.id(), bar);
        updateBar(expedition);
    }

    private void updateBar(ActiveExpedition expedition) {
        BossBar bar = bars.get(expedition.id());
        ExpeditionDefinition definition = registry.get(expedition.definitionId());
        if (bar == null || definition == null) return;
        int total = definition.goal() + definition.mobCount();
        int done = expedition.objectiveProgress()
                + Math.min(expedition.kills(), definition.mobCount());
        bar.setProgress(total == 0 ? 1
                : Math.min(1D, (double) done / total));
        String objective = expedition.objectiveProgress() + "/" + definition.goal()
                + " • " + expedition.kills() + "/" + definition.mobCount()
                + " • " + TimeUtil.format(
                expedition.expiresAt() - System.currentTimeMillis());
        bar.setTitle(ColorUtil.color(plugin.getConfig()
                .getString("bossbar.title", "%expedition% | %objective%")
                .replace("%expedition%", definition.name())
                .replace("%objective%", objective)));
    }

    public ExpeditionRepository repository() {
        return repository;
    }

    public ExpeditionRegistry registry() {
        return registry;
    }
}
