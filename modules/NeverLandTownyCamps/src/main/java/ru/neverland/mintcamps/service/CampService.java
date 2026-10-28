package ru.neverland.mintcamps.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintcamps.data.CampRepository;
import ru.neverland.mintcamps.data.CostStore;
import ru.neverland.mintcamps.integration.TownyHook;
import ru.neverland.mintcamps.gui.CampInventoryHolder;
import ru.neverland.mintcamps.model.BlockPos;
import ru.neverland.mintcamps.model.Camp;
import ru.neverland.mintcamps.model.Placement;
import ru.neverland.mintcamps.model.StyleDefinition;
import ru.neverland.mintcamps.util.DirectionUtil;
import ru.neverland.mintcamps.util.TimeUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CampService {
    private final JavaPlugin plugin;
    private final CampRepository repository;
    private final CostStore costs;
    private final ResourceService resources;
    private final MessageService messages;
    private final StyleRegistry styles;
    private final StructureService structures;
    private final StructureGenerator generator;
    private final TownyHook towny;
    private final RussianItemNames itemNames;
    private HologramService holograms;

    public CampService(JavaPlugin plugin, CampRepository repository, CostStore costs, ResourceService resources,
                       MessageService messages, StyleRegistry styles, StructureService structures,
                       StructureGenerator generator, TownyHook towny, RussianItemNames itemNames) {
        this.plugin = plugin;
        this.repository = repository;
        this.costs = costs;
        this.resources = resources;
        this.messages = messages;
        this.styles = styles;
        this.structures = structures;
        this.generator = generator;
        this.towny = towny;
        this.itemNames = itemNames;
    }

    public void holograms(HologramService value) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        this.holograms = value;
    } }

    public boolean create(Player player) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        if (repository.get(player.getUniqueId()).isPresent()) {
            messages.send(player, "already-has-camp");
            return false;
        }
        World world = player.getWorld();
        if (plugin.getConfig().getStringList("settings.placement.blocked-worlds").stream()
                .anyMatch(name -> name.equalsIgnoreCase(world.getName()))) {
            placementFailure(player, "placement.blocked-world");
            return false;
        }
        BlockFace facing = DirectionUtil.cardinal(player.getLocation().getYaw());
        int centerX = player.getLocation().getBlockX() + facing.getModX() * 3;
        int centerZ = player.getLocation().getBlockZ() + facing.getModZ() * 3;
        if (!ru.neverland.core.WorldTasks.ownsBox(new Location(world,centerX,player.getY(),centerZ),16)) { messages.send(player,"placement.unsafe");return false; }
        Surface surface = findSurface(world, centerX, centerZ, player.getLocation().getBlockY(), facing, 1);
        if (surface == null) {
            placementFailure(player, "placement.unsafe");
            return false;
        }
        if (surface.maxY() - surface.minY() > plugin.getConfig().getInt("settings.placement.max-surface-slope", 1)) {
            placementFailure(player, "placement.uneven");
            return false;
        }
        if (surface.maxY() < plugin.getConfig().getInt("settings.placement.minimum-y", -60)) {
            placementFailure(player, "placement.too-low");
            return false;
        }
        Location anchorLocation = new Location(world, centerX + 0.5, surface.maxY(), centerZ + 0.5);
        if (repository.isTooClose(anchorLocation, plugin.getConfig().getInt("settings.placement.minimum-distance", 32))) {
            placementFailure(player, "placement.too-close");
            return false;
        }
        TownyHook.PlacementResult townyResult = towny.canPlace(player, anchorLocation);
        if (townyResult != TownyHook.PlacementResult.ALLOWED) {
            placementFailure(player, townyResult == TownyHook.PlacementResult.CLAIMED
                    ? "placement.towny-claimed" : "placement.foreign-town");
            return false;
        }

        long now = System.currentTimeMillis();
        StyleDefinition style = styles.detect(world, world.getBiome(centerX, surface.maxY(), centerZ));
        Camp camp = new Camp(player.getUniqueId(), player.getName(), world.getUID(), world.getName(),
                new BlockPos(centerX, surface.maxY(), centerZ), facing, 1, style.id(), now,
                now + plugin.getConfig().getLong("settings.lifetime.initial-burn-seconds", 1800L) * 1000L,
                plugin.getConfig().getBoolean("settings.protection.holy-barrier-default", true), stashSize(1));
        List<Placement> placements = placements(camp, 1);
        StructureService.Validation validation = structures.validate(camp, placements, false);
        if (validation != StructureService.Validation.OK) {
            placementFailure(player, validation == StructureService.Validation.WATER ? "placement.water" : "placement.obstructed");
            return false;
        }
        List<ItemStack> cost = costs.get(1);
        List<ResourceService.Missing> missing = resources.missing(player, cost);
        if (!missing.isEmpty()) {
            messages.send(player, "placement.not-enough-resources", Map.of("resources", renderMissing(missing)));
            return false;
        }
        boolean paid = false;
        try {
            structures.capture(camp, placements);
            if (!resources.withdraw(player, cost)) return false;
            paid = true;
            structures.apply(camp, placements);
            repository.put(camp);
            repository.save();
            messages.send(player, "placement.success", Map.of("level", levelName(1),
                    "time", TimeUtil.formatMillis(camp.remainingBurnMillis(now))));
            sound(player, "settings.sounds.create", Sound.BLOCK_CAMPFIRE_CRACKLE);
            return true;
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("Ошибка установки лагеря " + player.getName() + ": " + exception.getMessage());
            safeRestore(camp);
            if (paid) resources.refund(player, cost);
            repository.remove(camp);
            messages.send(player, "placement.failed", Map.of("reason", messages.raw("placement.internal-error")));
            return false;
        }
    } }

    public boolean upgrade(Player player) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
        Camp camp = repository.get(player.getUniqueId()).orElse(null);
        if (camp == null) {
            messages.send(player, "no-camp");
            return false;
        }
        if (!camp.ownerId().equals(player.getUniqueId())) {
            messages.send(player, "not-owner");
            return false;
        }
        if (camp.level() >= 3) {
            messages.send(player, "upgrade.max-level");
            return false;
        }
        if (!ru.neverland.core.WorldTasks.ownsBox(camp.anchorLocation(),16) || repository.stashBusy(camp.ownerId())) { player.sendMessage("§eПодойдите к лагерю и закройте его склад.");return false; }
        int oldLevel = camp.level();
        int nextLevel = oldLevel + 1;
        List<ItemStack> cost = costs.get(nextLevel);
        List<ResourceService.Missing> missing = resources.missing(player, cost);
        if (!missing.isEmpty()) {
            messages.send(player, "upgrade.not-enough-resources", Map.of("resources", renderMissing(missing)));
            return false;
        }
        List<Placement> oldPlacements = placements(camp, oldLevel);
        List<Placement> nextPlacements = placements(camp, nextLevel);
        boolean paid = false;
        try {
            List<ItemStack> workstationItems = structures.drainPlacedContainers(camp);
            resources.refund(player, workstationItems);
            structures.restoreCurrent(camp);
            World world = camp.world().orElse(null);
            Surface surface = world == null ? null : findSurface(world, camp.anchor().x(), camp.anchor().z(),
                    camp.anchor().y(), camp.facing(), nextLevel);
            int allowedSlope = plugin.getConfig().getInt("settings.placement.max-surface-slope", 1);
            if (surface == null || surface.maxY() - surface.minY() > allowedSlope
                    || Math.abs(surface.maxY() - camp.anchor().y()) > allowedSlope) {
                structures.apply(camp, oldPlacements);
                messages.send(player, "upgrade.obstructed");
                return false;
            }
            StructureService.Validation validation = structures.validate(camp, nextPlacements, false);
            if (validation != StructureService.Validation.OK) {
                structures.apply(camp, oldPlacements);
                messages.send(player, "upgrade.obstructed");
                return false;
            }
            structures.capture(camp, nextPlacements);
            if (!resources.withdraw(player, cost)) {
                structures.apply(camp, oldPlacements);
                return false;
            }
            paid = true;
            camp.level(nextLevel);
            camp.stash(camp.stash(), stashSize(nextLevel));
            structures.apply(camp, nextPlacements);
            repository.put(camp);
            repository.save();
            messages.send(player, "upgrade.success", Map.of("level", levelName(nextLevel), "slots", stashSize(nextLevel)));
            sound(player, "settings.sounds.upgrade", Sound.UI_TOAST_CHALLENGE_COMPLETE);
            return true;
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("Ошибка улучшения лагеря " + player.getName() + ": " + exception.getMessage());
            try {
                structures.restoreCurrent(camp);
                camp.level(oldLevel);
                structures.apply(camp, oldPlacements);
            } catch (RuntimeException rollbackError) {
                plugin.getLogger().severe("Не удалось вернуть прежний уровень лагеря: " + rollbackError.getMessage());
            }
            if (paid) resources.refund(player, cost);
            messages.send(player, "upgrade.internal-error");
            return false;
        }
    } }

    public boolean pack(Player player) { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {
        Camp camp = repository.get(player.getUniqueId()).orElse(null);
        if (camp == null) {
            messages.send(player, "no-camp");
            return false;
        }
        if (!pack(camp, player, false)) {
            messages.send(player, "pack.failed");
            return false;
        }
        messages.send(player, "pack.success");
        sound(player, "settings.sounds.pack", Sound.BLOCK_WOOD_BREAK);
        return true;
    } }

    public boolean pack(Camp camp, Player receiver, boolean expired) { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {
        try {
            if ((!expired&&receiver!=null&&!ru.neverland.core.SuiteScheduler.owns(receiver)) || !ru.neverland.core.WorldTasks.ownsBox(camp.anchorLocation(),16) || repository.stashBusy(camp.ownerId())) return false;
            if (repository.get(camp.ownerId()).orElse(null) != camp) return false;
            List<ItemStack> savedItems = new ArrayList<>(camp.nonEmptyStash());
            savedItems.addAll(structures.drainPlacedContainers(camp));
            structures.restoreAll(camp);
            removeHologram(camp);
            Location drop = camp.anchorLocation();
            for (ItemStack item : savedItems) {
                if (receiver != null && !expired) {
                    for (ItemStack leftover : receiver.getInventory().addItem(item).values()) {
                        receiver.getWorld().dropItemNaturally(receiver.getLocation(), leftover);
                    }
                } else if (drop != null) {
                    drop.getWorld().dropItemNaturally(drop.clone().add(0.5, 1.2, 0.5), item);
                }
            }
            repository.remove(camp);
            repository.save();
            if (expired && receiver != null) messages.send(receiver, "pack.expired", Map.of("owner", camp.ownerName()));
            return true;
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("Ошибка сворачивания лагеря " + camp.ownerName() + ": " + exception.getMessage());
            return false;
        }
    } }

    public boolean trust(Player owner, UUID targetId, String targetName) { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {
        Camp camp = repository.get(owner.getUniqueId()).orElse(null);
        if (camp == null) { messages.send(owner, "no-camp"); return false; }
        if (owner.getUniqueId().equals(targetId)) { messages.send(owner, "self-trust"); return false; }
        if (camp.trusted().containsKey(targetId)) { messages.send(owner, "already-trusted", Map.of("player", targetName)); return false; }
        int max = maxTrusted(camp.level());
        long temporary=camp.guests().entrySet().stream().filter(e->!e.getKey().equals(targetId)&&e.getValue().active(System.currentTimeMillis())).count();
        if (camp.trusted().size()+temporary >= max) { messages.send(owner, "trust-full", Map.of("max", max)); return false; }
        camp.trusted().put(targetId, targetName);
        repository.markDirty();
        repository.saveIfDirty();
        messages.send(owner, "trusted-added", Map.of("player", targetName));
        Player online = Bukkit.getPlayer(targetId);
        if (online != null) messages.send(online, "trust-notify-added", Map.of("owner", owner.getName()));
        return true;
    } }

    public boolean untrust(Player owner, UUID targetId, String targetName) { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {
        Camp camp = repository.get(owner.getUniqueId()).orElse(null);
        if (camp == null) { messages.send(owner, "no-camp"); return false; }
        if (camp.trusted().remove(targetId) == null) {
            messages.send(owner, "not-in-trust", Map.of("player", targetName));
            return false;
        }
        repository.markDirty();
        repository.saveIfDirty();
        messages.send(owner, "trusted-removed", Map.of("player", targetName));
        Player online = Bukkit.getPlayer(targetId);
        if (online != null) messages.send(online, "trust-notify-removed", Map.of("owner", owner.getName()));
        return true;
    } }

    public boolean guest(Player owner,UUID target,String name,java.util.Set<ru.neverland.mintcamps.model.GuestAccess.Right> rights,int minutes){try(var guard=ru.neverland.core.SuiteState.guard()){
        Camp camp=repository.get(owner.getUniqueId()).orElseThrow(()->new IllegalArgumentException("Сначала создайте свой лагерь"));
        if(target.equals(owner.getUniqueId()))throw new IllegalArgumentException("Владелец уже имеет все права");
        if(camp.trusted().containsKey(target))throw new IllegalArgumentException("Игрок имеет полный постоянный доступ. Сначала уберите его: /camp untrust "+name);
        if(minutes<1||minutes>10080)throw new IllegalArgumentException("Срок гостевого доступа: от 1 до 10080 минут (7 дней)");
        long now=System.currentTimeMillis();var previous=camp.guests().get(target);
        long active=camp.guests().entrySet().stream().filter(e->!e.getKey().equals(target)&&e.getValue().active(now)).count();
        if(active+camp.trusted().size()>=maxTrusted(camp.level()))throw new IllegalArgumentException("В лагере не осталось мест для гостей");
        var grant=new ru.neverland.mintcamps.model.GuestAccess(name,now,Math.addExact(now,minutes*60000L),rights);
        camp.guests().put(target,grant);repository.markDirty();
        try{repository.saveOrThrow();}catch(java.io.IOException error){if(previous==null)camp.guests().remove(target);else camp.guests().put(target,previous);throw new IllegalStateException("Права не изменены: не удалось сохранить лагерь",error);}
        ru.neverland.core.AuditTrail.record(plugin,"grant",UUID.randomUUID().toString(),"CAMP_GUEST","GRANTED",ru.neverland.core.AuditTrail.player(owner.getUniqueId()),ru.neverland.core.AuditTrail.player(owner.getUniqueId()),ru.neverland.core.AuditTrail.player(target),"",0,"","expires="+grant.expiresAt()+"; rights="+rights);
        owner.sendMessage("§aДоступ для "+name+" сохранён на "+minutes+" мин.: "+rights.stream().map(r->r.label).sorted().collect(java.util.stream.Collectors.joining(", ")));
        return true;
    }}
    public boolean revokeGuest(Player owner,UUID target){try(var guard=ru.neverland.core.SuiteState.guard()){
        Camp camp=repository.get(owner.getUniqueId()).orElseThrow(()->new IllegalArgumentException("У вас нет лагеря"));
        var previous=camp.guests().remove(target);if(previous==null)throw new IllegalArgumentException("У игрока нет гостевого доступа");repository.markDirty();
        try{repository.saveOrThrow();}catch(java.io.IOException error){camp.guests().put(target,previous);throw new IllegalStateException("Не удалось сохранить отмену доступа",error);}
        ru.neverland.core.AuditTrail.record(plugin,"revoke",UUID.randomUUID().toString(),"CAMP_GUEST","REVOKED",ru.neverland.core.AuditTrail.player(owner.getUniqueId()),ru.neverland.core.AuditTrail.player(owner.getUniqueId()),ru.neverland.core.AuditTrail.player(target),"",0,"","");
        owner.sendMessage("§eГостевой доступ отменён.");return true;
    }}

    public void toggleBarrier(Player player) { try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {
        Camp camp = repository.get(player.getUniqueId()).orElse(null);
        if (camp == null) { messages.send(player, "no-camp"); return; }
        camp.mobsDisabled(!camp.mobsDisabled());
        repository.markDirty();
        messages.send(player, camp.mobsDisabled() ? "barrier.enabled" : "barrier.disabled");
    } }

    public void updateCampfire(Camp camp) { try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) {
        World world = camp.world().orElse(null);
        if (world == null) return;
        if (!ru.neverland.core.WorldTasks.ownsBox(camp.anchorLocation(),16)) { ru.neverland.core.SuiteScheduler.region(plugin,camp.anchorLocation(),()->{ if(ru.neverland.core.WorldTasks.ownsBox(camp.anchorLocation(),16))updateCampfire(camp); });return; }
        migrateRaisedCampfire(camp);
        BlockPos pos = DirectionUtil.rotate(camp.anchor(), camp.facing(), StructureGenerator.CAMPFIRE_RELATIVE);
        Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
        if (block.getBlockData() instanceof Lightable lightable) {
            boolean shouldBeLit = camp.burnUntil() > System.currentTimeMillis();
            if (lightable.isLit() != shouldBeLit) {
                lightable.setLit(shouldBeLit);
                block.setBlockData(lightable, false);
            }
        }
    } }

    public void migrateRaisedCampfires() { try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) {
        int migrated = 0;
        for (Camp camp : repository.all()) {
            if (camp.anchorLocation()!=null) ru.neverland.core.SuiteScheduler.region(plugin,camp.anchorLocation(),()->{ if(ru.neverland.core.WorldTasks.ownsBox(camp.anchorLocation(),16)&&migrateRaisedCampfire(camp))repository.saveIfDirty(); });
        }
        if (migrated > 0) {
            repository.saveIfDirty();
            plugin.getLogger().info("Исправлена высота очага у лагерей: " + migrated + ".");
        }
    } }

    public int stashSize(int level) { try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) {
        return plugin.getConfig().getInt("settings.levels." + level + ".stash-size", level * 9);
    } }

    public int maxTrusted(int level) { try (var suiteGuard12 = ru.neverland.core.SuiteState.guard()) {
        return plugin.getConfig().getInt("settings.levels." + level + ".max-trusted", level * 2);
    } }

    public String levelName(int level) { try (var suiteGuard13 = ru.neverland.core.SuiteState.guard()) {
        return messages.formatConfig(plugin.getConfig().getString("settings.levels." + level + ".name", "Уровень " + level));
    } }

    public String styleName(Camp camp) { try (var suiteGuard14 = ru.neverland.core.SuiteState.guard()) {
        return styles.find(camp.styleId()).map(value -> messages.formatConfig(value.name())).orElse(camp.styleId());
    } }

    private List<Placement> placements(Camp camp, int level) {
        return structures.placements(camp, id -> styles.find(id).orElseThrow(), level);
    }

    private boolean migrateRaisedCampfire(Camp camp) {
        try {
            List<Placement> correctedCommon = placements(camp, camp.level()).stream()
                    .filter(placement -> placement.relative().y() == 0
                            && Math.abs(placement.relative().x()) + Math.abs(placement.relative().z()) <= 1)
                    .toList();
            if (!structures.migrateRaisedCommonBlocks(camp, correctedCommon)) return false;
            repository.markDirty();
            return true;
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Не удалось исправить высоту очага лагеря " + camp.ownerName()
                    + ": " + exception.getMessage());
            return false;
        }
    }

    private Surface findSurface(World world, int centerX, int centerZ, int playerY, BlockFace facing, int level) {
        StyleDefinition style = styles.detect(world, world.getBiome(centerX, playerY, centerZ));
        List<Placement> placements = generator.generate(level, style);
        Set<Long> columns = new HashSet<>();
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (Placement placement : placements) {
            if (placement.relative().y() != 0) continue;
            BlockPos absolute = DirectionUtil.rotate(new BlockPos(centerX, 0, centerZ), facing, placement.relative());
            long key = ((long) absolute.x() << 32) ^ (absolute.z() & 0xffffffffL);
            if (!columns.add(key)) continue;
            Integer surface = surfaceAt(world, absolute.x(), absolute.z(), playerY);
            if (surface == null) return null;
            min = Math.min(min, surface);
            max = Math.max(max, surface);
        }
        return min == Integer.MAX_VALUE ? null : new Surface(min, max);
    }

    private Integer surfaceAt(World world, int x, int z, int aroundY) {
        int upper = Math.min(world.getMaxHeight() - 2, aroundY + 5);
        int lower = Math.max(world.getMinHeight(), aroundY - 8);
        for (int y = upper; y >= lower; y--) {
            Block ground = world.getBlockAt(x, y, z);
            Block above = world.getBlockAt(x, y + 1, z);
            if (ground.getType().isSolid() && !above.getType().isSolid()) return y + 1;
        }
        return null;
    }

    private void placementFailure(Player player, String reasonPath) {
        messages.send(player, "placement.failed", Map.of("reason", messages.raw(reasonPath)));
    }

    private String renderMissing(List<ResourceService.Missing> missing) {
        List<String> parts = new ArrayList<>();
        for (ResourceService.Missing entry : missing) {
            parts.add(itemNames.name(entry.item()) + " ×" + (entry.required() - entry.available()));
        }
        return String.join(", ", parts);
    }

    private void removeHologram(Camp camp) {
        if (holograms != null) holograms.remove(camp);
        else camp.hologramId(null);
    }

    private void safeRestore(Camp camp) {
        try { structures.restoreAll(camp); } catch (RuntimeException ignored) { }
    }

    private void sound(Player player, String path, Sound fallback) {
        Sound sound;
        try { sound = Sound.valueOf(plugin.getConfig().getString(path, fallback.name())); }
        catch (IllegalArgumentException exception) { sound = fallback; }
        player.playSound(player.getLocation(), sound, 1.0F, 1.0F);
    }

    private record Surface(int minY, int maxY) { }
}
