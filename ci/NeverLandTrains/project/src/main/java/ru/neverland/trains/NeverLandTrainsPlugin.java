package ru.neverland.trains;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Powerable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.minecart.PoweredMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

public final class NeverLandTrainsPlugin extends JavaPlugin {

    NamespacedKey trainIdKey;
    NamespacedKey trainIndexKey;

    CopperRailRegistry railRegistry;
    TrainManager trainManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.trainIdKey = new NamespacedKey(this, "train_id");
        this.trainIndexKey = new NamespacedKey(this, "train_index");

        this.railRegistry = new CopperRailRegistry(this);
        this.trainManager = new TrainManager(this, railRegistry);

        TrainListener listener = new TrainListener(this, trainManager, railRegistry);
        getServer().getPluginManager().registerEvents(listener, this);

        TrainCommand command = new TrainCommand(this, trainManager, railRegistry);
        Objects.requireNonNull(getCommand("nltrains")).setExecutor(command);
        Objects.requireNonNull(getCommand("nltrains")).setTabCompleter(command);

        // Purpur bootstrap: restore carts that are currently loaded.
        // All entity mutations after discovery are routed through EntityScheduler.
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Minecart minecart) {
                    trainManager.restoreOrRegister(minecart);
                }
            }
        }

        getLogger().info("NeverLandTrains 0.1.0-test enabled for Purpur 26.2.");
    }

    @Override
    public void onDisable() {
        railRegistry.save();
        trainManager.shutdown();
    }

    void reloadPluginConfig() {
        reloadConfig();
        railRegistry.reload();
        trainManager.reloadSettings();
    }
}

final class TrainManager {

    private final NeverLandTrainsPlugin plugin;
    private final CopperRailRegistry rails;

    private final Map<UUID, TrainState> trains = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> cartToTrain = new ConcurrentHashMap<>();
    private final Map<UUID, CartSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Set<UUID> scheduled = new CopyOnWriteArraySet<>();

    private volatile int maxCarts;
    private volatile double spacing;
    private volatile double manualCap;
    private volatile double poweredCap;
    private volatile double acceleration;
    private volatile double braking;
    private volatile double targetChange;
    private volatile double followerCatchup;
    private volatile double followerMaxCorrection;
    private volatile int hudEvery;

    TrainManager(NeverLandTrainsPlugin plugin, CopperRailRegistry rails) {
        this.plugin = plugin;
        this.rails = rails;
        reloadSettings();
    }

    void reloadSettings() {
        maxCarts = Math.max(2, plugin.getConfig().getInt("train.max-carts", 8));
        spacing = Math.max(0.8, plugin.getConfig().getDouble("train.spacing-blocks", 1.30));
        manualCap = Math.max(8.0, plugin.getConfig().getDouble("train.max-manual-speed-bps", 48.0));
        poweredCap = Math.max(manualCap, plugin.getConfig().getDouble("train.max-powered-speed-bps", 64.0));
        acceleration = Math.max(0.1, plugin.getConfig().getDouble("train.acceleration-bps-per-second", 10.0));
        braking = Math.max(0.1, plugin.getConfig().getDouble("train.braking-bps-per-second", 18.0));
        targetChange = Math.max(0.1, plugin.getConfig().getDouble("train.target-change-bps-per-second", 14.0));
        followerCatchup = Math.max(0.0, plugin.getConfig().getDouble("train.follower-catchup", 0.055));
        followerMaxCorrection = Math.max(0.0, plugin.getConfig().getDouble("train.follower-max-correction", 0.11));
        hudEvery = Math.max(1, plugin.getConfig().getInt("train.hud-every-ticks", 10));
    }

    void shutdown() {
        trains.clear();
        cartToTrain.clear();
        snapshots.clear();
        scheduled.clear();
    }

    void restoreOrRegister(Minecart cart) {
        PersistentDataContainer pdc = cart.getPersistentDataContainer();
        String rawId = pdc.get(plugin.trainIdKey, PersistentDataType.STRING);
        Integer index = pdc.get(plugin.trainIndexKey, PersistentDataType.INTEGER);

        UUID trainId;
        if (rawId != null) {
            try {
                trainId = UUID.fromString(rawId);
            } catch (IllegalArgumentException ex) {
                trainId = UUID.randomUUID();
            }
        } else {
            trainId = UUID.randomUUID();
        }

        int safeIndex = index == null || index < 0 ? 0 : index;
        TrainState state = trains.computeIfAbsent(trainId, TrainState::new);
        state.members.putIfAbsent(safeIndex, cart.getUniqueId());
        cartToTrain.put(cart.getUniqueId(), trainId);

        writeMembership(cart, trainId, safeIndex);
        scheduleCart(cart);
    }

    UUID ensureTrain(Minecart cart) {
        UUID known = cartToTrain.get(cart.getUniqueId());
        if (known != null) {
            scheduleCart(cart);
            return known;
        }
        restoreOrRegister(cart);
        return cartToTrain.get(cart.getUniqueId());
    }

    String couple(Minecart first, Minecart second) {
        if (first.getUniqueId().equals(second.getUniqueId())) {
            return "Нельзя сцепить вагонетку саму с собой.";
        }

        UUID firstId = ensureTrain(first);
        UUID secondId = ensureTrain(second);
        if (firstId.equals(secondId)) {
            return "Эти вагонетки уже находятся в одном составе.";
        }

        TrainState a = trains.get(firstId);
        TrainState b = trains.get(secondId);
        if (a == null || b == null) {
            return "Не удалось получить состояние состава.";
        }

        List<UUID> aOrder = a.orderedMembers();
        List<UUID> bOrder = b.orderedMembers();

        if (aOrder.size() + bOrder.size() > maxCarts) {
            return "Максимальная длина состава: " + maxCarts + " вагонеток.";
        }

        boolean firstIsTail = first.getUniqueId().equals(aOrder.get(aOrder.size() - 1));
        boolean secondIsHead = second.getUniqueId().equals(bOrder.get(0));
        boolean secondIsTail = second.getUniqueId().equals(bOrder.get(bOrder.size() - 1));
        boolean firstIsHead = first.getUniqueId().equals(aOrder.get(0));

        UUID keepId;
        TrainState keep;
        TrainState remove;
        List<UUID> merged = new ArrayList<>();

        if (firstIsTail && secondIsHead) {
            keepId = a.id;
            keep = a;
            remove = b;
            merged.addAll(aOrder);
            merged.addAll(bOrder);
        } else if (secondIsTail && firstIsHead) {
            keepId = b.id;
            keep = b;
            remove = a;
            merged.addAll(bOrder);
            merged.addAll(aOrder);
        } else {
            return "Сцеплять можно хвост одного состава с головой другого.";
        }

        keep.replaceMembers(merged);
        keep.targetBps = Math.min(Math.max(a.targetBps, b.targetBps), manualCap);
        trains.remove(remove.id);

        rewriteMembership(keepId, merged);
        return "Состав сцеплен: " + merged.size() + "/" + maxCarts + " вагонеток.";
    }

    String uncoupleBefore(Minecart cart) {
        UUID trainId = ensureTrain(cart);
        TrainState old = trains.get(trainId);
        if (old == null) return "Состав не найден.";

        List<UUID> order = old.orderedMembers();
        int pos = order.indexOf(cart.getUniqueId());
        if (pos <= 0) {
            return "Перед этой вагонеткой нет сцепки.";
        }

        List<UUID> front = new ArrayList<>(order.subList(0, pos));
        List<UUID> rear = new ArrayList<>(order.subList(pos, order.size()));

        old.replaceMembers(front);
        rewriteMembership(old.id, front);

        UUID newId = UUID.randomUUID();
        TrainState split = new TrainState(newId);
        split.replaceMembers(rear);
        split.targetBps = old.targetBps;
        trains.put(newId, split);
        rewriteMembership(newId, rear);

        return "Состав разделён: " + front.size() + " + " + rear.size() + " вагонеток.";
    }

    void removeCart(Minecart cart) {
        UUID trainId = cartToTrain.remove(cart.getUniqueId());
        snapshots.remove(cart.getUniqueId());
        scheduled.remove(cart.getUniqueId());

        if (trainId == null) return;
        TrainState state = trains.get(trainId);
        if (state == null) return;

        List<UUID> order = state.orderedMembers();
        int idx = order.indexOf(cart.getUniqueId());
        if (idx < 0) return;

        List<UUID> front = new ArrayList<>(order.subList(0, idx));
        List<UUID> rear = new ArrayList<>(order.subList(idx + 1, order.size()));

        if (front.isEmpty() && rear.isEmpty()) {
            trains.remove(trainId);
            return;
        }

        if (!front.isEmpty()) {
            state.replaceMembers(front);
            rewriteMembership(state.id, front);
        } else {
            trains.remove(trainId);
        }

        if (!rear.isEmpty()) {
            UUID rearId = UUID.randomUUID();
            TrainState rearState = new TrainState(rearId);
            rearState.targetBps = state.targetBps;
            rearState.replaceMembers(rear);
            trains.put(rearId, rearState);
            rewriteMembership(rearId, rear);
        }
    }

    TrainState getStateFor(Minecart cart) {
        UUID id = cartToTrain.get(cart.getUniqueId());
        return id == null ? null : trains.get(id);
    }

    void scheduleCart(Minecart cart) {
        UUID cartId = cart.getUniqueId();
        if (!scheduled.add(cartId)) return;

        cart.getScheduler().runAtFixedRate(
                plugin,
                task -> tickCart(cart, task),
                () -> scheduled.remove(cartId),
                1L,
                1L
        );
    }

    private void tickCart(Minecart cart, ScheduledTask task) {
        if (!cart.isValid() || cart.isDead()) {
            scheduled.remove(cart.getUniqueId());
            snapshots.remove(cart.getUniqueId());
            task.cancel();
            return;
        }

        UUID trainId = cartToTrain.get(cart.getUniqueId());
        if (trainId == null) {
            restoreOrRegister(cart);
            trainId = cartToTrain.get(cart.getUniqueId());
        }

        TrainState state = trains.get(trainId);
        if (state == null) {
            task.cancel();
            scheduled.remove(cart.getUniqueId());
            return;
        }

        List<UUID> order = state.orderedMembers();
        int index = order.indexOf(cart.getUniqueId());
        if (index < 0) {
            task.cancel();
            scheduled.remove(cart.getUniqueId());
            return;
        }

        updateDriverControl(cart, state);

        if (index == 0) {
            tickLeader(cart, state);
        } else {
            tickFollower(cart, state, order, index);
        }

        updateSnapshot(cart);

        if (cart.getTicksLived() % 20 == 0 && cart instanceof PoweredMinecart powered) {
            updateLocomotiveName(powered);
        }
    }

    private void updateDriverControl(Minecart cart, TrainState state) {
        for (Entity passenger : cart.getPassengers()) {
            if (!(passenger instanceof Player player)) continue;
            Input input = player.getCurrentInput();
            Vector look = player.getLocation().getDirection().setY(0);
            if (look.lengthSquared() < 0.0001) look = new Vector(1, 0, 0);
            look.normalize();

            state.control = new DriverControl(
                    player.getUniqueId(),
                    input.isForward(),
                    input.isBackward(),
                    input.isSneak(),
                    input.isSprint(),
                    look.getX(),
                    look.getZ(),
                    System.nanoTime()
            );

            if (cart.getTicksLived() % hudEvery == 0) {
                int fuelTicks = totalFuelTicks(state);
                String fuel = fuelTicks > 0 ? " • Топливо " + (fuelTicks / 20) + "с" : "";
                player.sendActionBar(Component.text(
                        String.format(Locale.ROOT,
                                "Поезд %.1f б/с • выбрано %.1f б/с%s",
                                state.currentBps,
                                state.targetBps,
                                fuel
                        ),
                        NamedTextColor.GOLD
                ));
            }
            return;
        }
    }

    private void tickLeader(Minecart cart, TrainState state) {
        Location location = cart.getLocation();
        Block railBlock = findRailBlock(location);
        CopperRailRegistry.RailData railData = railBlock == null ? null : rails.get(railBlock);

        String currentRailKey = railBlock == null ? null : rails.keyOf(railBlock);

        if (state.stationTicks > 0) {
            state.stationTicks--;
            state.targetBps = 0.0;
            if (state.stationTicks == 0) {
                state.targetBps = Math.min(state.resumeAfterStationBps, manualCap);
            }
        } else if (railData != null && railData.station()) {
            if (!Objects.equals(state.lastStationKey, currentRailKey)) {
                state.resumeAfterStationBps = railData.speedBps() > 0.0
                        ? railData.speedBps()
                        : Math.max(state.targetBps, rails.defaultSpeed());
                state.stationTicks = railData.stopTicks();
                state.targetBps = 0.0;
                state.lastStationKey = currentRailKey;
            }
        } else {
            if (state.lastStationKey != null && !Objects.equals(state.lastStationKey, currentRailKey)) {
                state.lastStationKey = null;
            }

            if (railData != null) {
                state.targetBps = railData.speedBps();
            } else {
                DriverControl control = state.control;
                if (control != null && control.isFresh()) {
                    double delta = targetChange / 20.0;
                    if (control.sneak()) {
                        state.targetBps = 0.0;
                    } else {
                        if (control.forward()) state.targetBps += delta;
                        if (control.backward()) state.targetBps -= delta;
                    }
                }
            }
        }

        boolean hasFuel = hasFueledLocomotive(state);
        boolean onPoweredRail = isPoweredRail(railBlock);

        double cap = (hasFuel && onPoweredRail) ? poweredCap : manualCap;
        state.maxBps = cap;
        state.targetBps = clamp(state.targetBps, 0.0, cap);

        DriverControl control = state.control;
        boolean hasDriver = control != null && control.isFresh();
        boolean automaticRail = railData != null;
        boolean activelyDriven = hasDriver || hasFuel || onPoweredRail || automaticRail || state.stationTicks > 0;

        cart.setMaxSpeed(cap / 20.0);
        cart.setSlowWhenEmpty(false);

        Vector velocity = cart.getVelocity();
        double horizontal = Math.hypot(velocity.getX(), velocity.getZ());
        double currentBps = horizontal * 20.0;

        if (!activelyDriven) {
            state.currentBps = currentBps;
            return;
        }

        double wanted = state.targetBps;
        double rate = wanted >= currentBps ? acceleration : braking;
        double nextBps = approach(currentBps, wanted, rate / 20.0);
        double nextBlocksPerTick = nextBps / 20.0;

        Vector direction = new Vector(velocity.getX(), 0, velocity.getZ());
        if (direction.lengthSquared() < 0.0004 && control != null) {
            direction = new Vector(control.lookX(), 0, control.lookZ());
        }
        if (direction.lengthSquared() < 0.0004) {
            direction = location.getDirection().setY(0);
        }
        if (direction.lengthSquared() < 0.0004) {
            state.currentBps = currentBps;
            return;
        }

        direction.normalize().multiply(nextBlocksPerTick);
        direction.setY(velocity.getY());
        cart.setVelocity(direction);
        state.currentBps = nextBps;
    }

    private void tickFollower(Minecart cart, TrainState state, List<UUID> order, int index) {
        UUID previousId = order.get(index - 1);
        CartSnapshot previous = snapshots.get(previousId);
        if (previous == null) return;
        if (!previous.worldId().equals(cart.getWorld().getUID())) return;

        Location here = cart.getLocation();
        double dx = previous.x() - here.getX();
        double dz = previous.z() - here.getZ();
        double distance = Math.hypot(dx, dz);
        if (distance < 0.0001) return;

        Vector current = cart.getVelocity();
        Vector base = new Vector(previous.vx(), 0, previous.vz());
        Vector toward = new Vector(dx, 0, dz).normalize();

        double baseSpeed = Math.max(base.length(), state.currentBps / 20.0);
        if (base.lengthSquared() > 0.0001) {
            base.normalize().multiply(baseSpeed);
        } else {
            base = toward.clone().multiply(baseSpeed);
        }

        double error = distance - spacing;
        if (error > 0.0) {
            double correction = Math.min(followerMaxCorrection, error * followerCatchup);
            base.add(toward.clone().multiply(correction));
        } else if (error < -0.20) {
            base.multiply(Math.max(0.35, 1.0 + error * 0.22));
        }

        double maxTickSpeed = Math.max(0.4, state.maxBps / 20.0);
        double horizontal = Math.hypot(base.getX(), base.getZ());
        if (horizontal > maxTickSpeed) {
            base.multiply(maxTickSpeed / horizontal);
        }

        base.setY(current.getY());
        cart.setMaxSpeed(maxTickSpeed);
        cart.setSlowWhenEmpty(false);
        cart.setVelocity(base);
    }

    private void updateSnapshot(Minecart cart) {
        Location location = cart.getLocation();
        Vector velocity = cart.getVelocity();
        int fuel = cart instanceof PoweredMinecart powered ? powered.getFuel() : -1;

        snapshots.put(cart.getUniqueId(), new CartSnapshot(
                cart.getWorld().getUID(),
                location.getX(),
                location.getY(),
                location.getZ(),
                velocity.getX(),
                velocity.getY(),
                velocity.getZ(),
                fuel,
                cart instanceof PoweredMinecart
        ));
    }

    boolean addFuel(PoweredMinecart cart, Player player, Material material) {
        int add = plugin.getConfig().getInt("locomotive.fuels." + material.name(), 0);
        if (add <= 0) return false;

        int max = Math.max(20, plugin.getConfig().getInt("locomotive.max-fuel-ticks", 72000));
        cart.setFuel(Math.min(max, Math.max(0, cart.getFuel()) + add));

        Vector push = player.getLocation().getDirection().setY(0);
        if (push.lengthSquared() > 0.0001) {
            push.normalize();
            cart.setPushX(push.getX());
            cart.setPushZ(push.getZ());
        }

        if (player.getGameMode() != GameMode.CREATIVE) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            hand.subtract(1);
        }

        ensureTrain(cart);
        updateLocomotiveName(cart);
        player.sendActionBar(Component.text(
                "Локомотив: топлива " + (cart.getFuel() / 20) + " сек.",
                NamedTextColor.GOLD
        ));
        return true;
    }

    private void updateLocomotiveName(PoweredMinecart cart) {
        int seconds = Math.max(0, cart.getFuel() / 20);
        cart.customName(Component.text(
                "Локомотив • " + seconds + "с",
                seconds > 0 ? NamedTextColor.GOLD : NamedTextColor.GRAY
        ));
        cart.setCustomNameVisible(true);
    }

    private boolean hasFueledLocomotive(TrainState state) {
        for (UUID member : state.orderedMembers()) {
            CartSnapshot snapshot = snapshots.get(member);
            if (snapshot != null && snapshot.powered() && snapshot.fuelTicks() > 0) {
                return true;
            }
        }
        return false;
    }

    private int totalFuelTicks(TrainState state) {
        int total = 0;
        for (UUID member : state.orderedMembers()) {
            CartSnapshot snapshot = snapshots.get(member);
            if (snapshot != null && snapshot.powered() && snapshot.fuelTicks() > 0) {
                total += snapshot.fuelTicks();
            }
        }
        return total;
    }

    private boolean isPoweredRail(Block block) {
        if (block == null || block.getType() != Material.POWERED_RAIL) return false;
        BlockData data = block.getBlockData();
        return data instanceof Powerable powerable && powerable.isPowered();
    }

    private Block findRailBlock(Location location) {
        Block same = location.getBlock();
        if (isRail(same.getType())) return same;
        Block below = location.clone().subtract(0, 1, 0).getBlock();
        return isRail(below.getType()) ? below : null;
    }

    private boolean isRail(Material material) {
        return material == Material.RAIL
                || material == Material.POWERED_RAIL
                || material == Material.DETECTOR_RAIL
                || material == Material.ACTIVATOR_RAIL;
    }

    String describe(Minecart cart) {
        UUID trainId = ensureTrain(cart);
        TrainState state = trains.get(trainId);
        if (state == null) return "Состав не найден.";
        int fuel = totalFuelTicks(state);
        return "Состав " + shortId(trainId)
                + " • вагонеток " + state.orderedMembers().size() + "/" + maxCarts
                + " • скорость " + String.format(Locale.ROOT, "%.1f", state.currentBps) + " б/с"
                + " • выбрано " + String.format(Locale.ROOT, "%.1f", state.targetBps) + " б/с"
                + (fuel > 0 ? " • топливо " + (fuel / 20) + "с" : "");
    }

    int trainCount() {
        return trains.size();
    }

    int scheduledCartCount() {
        return scheduled.size();
    }

    private String shortId(UUID uuid) {
        return uuid.toString().substring(0, 8);
    }

    private void rewriteMembership(UUID trainId, List<UUID> order) {
        for (int i = 0; i < order.size(); i++) {
            UUID cartId = order.get(i);
            cartToTrain.put(cartId, trainId);
            final int index = i;
            withCart(cartId, cart -> {
                writeMembership(cart, trainId, index);
                scheduleCart(cart);
            });
        }
    }

    private void writeMembership(Minecart cart, UUID trainId, int index) {
        PersistentDataContainer pdc = cart.getPersistentDataContainer();
        pdc.set(plugin.trainIdKey, PersistentDataType.STRING, trainId.toString());
        pdc.set(plugin.trainIndexKey, PersistentDataType.INTEGER, index);
    }

    private void withCart(UUID cartId, Consumer<Minecart> action) {
        Entity entity = Bukkit.getEntity(cartId);
        if (!(entity instanceof Minecart cart)) return;
        cart.getScheduler().execute(plugin, () -> action.accept(cart), null, 1L);
    }

    private static double approach(double current, double target, double maxDelta) {
        if (current < target) return Math.min(target, current + maxDelta);
        return Math.max(target, current - maxDelta);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record CartSnapshot(
            UUID worldId,
            double x,
            double y,
            double z,
            double vx,
            double vy,
            double vz,
            int fuelTicks,
            boolean powered
    ) {}

    record DriverControl(
            UUID playerId,
            boolean forward,
            boolean backward,
            boolean sneak,
            boolean sprint,
            double lookX,
            double lookZ,
            long updatedNanos
    ) {
        boolean isFresh() {
            return System.nanoTime() - updatedNanos < 350_000_000L;
        }
    }
}

final class TrainState {

    final UUID id;
    final NavigableMap<Integer, UUID> members = new ConcurrentSkipListMap<>();

    volatile double targetBps = 0.0;
    volatile double currentBps = 0.0;
    volatile double maxBps = 48.0;

    volatile int stationTicks = 0;
    volatile double resumeAfterStationBps = 0.0;
    volatile String lastStationKey;

    volatile TrainManager.DriverControl control;

    TrainState(UUID id) {
        this.id = id;
    }

    @SuppressWarnings("unchecked")
    TrainManager.DriverControl control() {
        return (TrainManager.DriverControl) control;
    }

    List<UUID> orderedMembers() {
        return new ArrayList<>(members.values());
    }

    synchronized void replaceMembers(List<UUID> order) {
        members.clear();
        for (int i = 0; i < order.size(); i++) {
            members.put(i, order.get(i));
        }
    }
}

final class CopperRailRegistry {

    private final NeverLandTrainsPlugin plugin;
    private final File file;
    private YamlConfiguration yaml;

    private final Map<String, RailData> rails = new ConcurrentHashMap<>();

    CopperRailRegistry(NeverLandTrainsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "copper-rails.yml");
        reload();
    }

    synchronized void reload() {
        rails.clear();
        yaml = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection section = yaml.getConfigurationSection("rails");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            double speed = section.getDouble(key + ".speed-bps", defaultSpeed());
            boolean station = section.getBoolean(key + ".station", false);
            int stop = section.getInt(key + ".stop-ticks",
                    plugin.getConfig().getInt("copper-rails.station-stop-ticks", 60));
            rails.put(key, new RailData(speed, station, Math.max(1, stop)));
        }
    }

    synchronized void save() {
        if (yaml == null) yaml = new YamlConfiguration();
        yaml.set("rails", null);

        for (Map.Entry<String, RailData> entry : rails.entrySet()) {
            String path = "rails." + entry.getKey();
            RailData data = entry.getValue();
            yaml.set(path + ".speed-bps", data.speedBps());
            yaml.set(path + ".station", data.station());
            yaml.set(path + ".stop-ticks", data.stopTicks());
        }

        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Cannot save copper-rails.yml: " + ex.getMessage());
        }
    }

    RailData get(Block block) {
        if (block == null || block.getType() != Material.RAIL) return null;
        return rails.get(keyOf(block));
    }

    String keyOf(Block block) {
        return block.getWorld().getUID()
                + "_" + block.getX()
                + "_" + block.getY()
                + "_" + block.getZ();
    }

    double defaultSpeed() {
        return plugin.getConfig().getDouble("copper-rails.default-speed-bps", 16.0);
    }

    RailData create(Block block) {
        RailData data = new RailData(defaultSpeed(), false,
                Math.max(1, plugin.getConfig().getInt("copper-rails.station-stop-ticks", 60)));
        rails.put(keyOf(block), data);
        save();
        return data;
    }

    RailData cycleSpeed(Block block) {
        RailData current = rails.computeIfAbsent(keyOf(block), ignored ->
                new RailData(defaultSpeed(), false,
                        Math.max(1, plugin.getConfig().getInt("copper-rails.station-stop-ticks", 60))));

        List<Double> cycle = plugin.getConfig().getDoubleList("copper-rails.speed-cycle-bps");
        if (cycle.isEmpty()) {
            cycle = List.of(8.0, 16.0, 24.0, 32.0, 40.0, 48.0);
        }

        int next = 0;
        for (int i = 0; i < cycle.size(); i++) {
            if (Math.abs(cycle.get(i) - current.speedBps()) < 0.01) {
                next = (i + 1) % cycle.size();
                break;
            }
        }

        RailData updated = new RailData(cycle.get(next), current.station(), current.stopTicks());
        rails.put(keyOf(block), updated);
        save();
        return updated;
    }

    RailData toggleStation(Block block) {
        RailData current = rails.computeIfAbsent(keyOf(block), ignored ->
                new RailData(defaultSpeed(), false,
                        Math.max(1, plugin.getConfig().getInt("copper-rails.station-stop-ticks", 60))));
        RailData updated = new RailData(current.speedBps(), !current.station(), current.stopTicks());
        rails.put(keyOf(block), updated);
        save();
        return updated;
    }

    RailData setSpeed(Block block, double speed) {
        RailData current = rails.computeIfAbsent(keyOf(block), ignored ->
                new RailData(defaultSpeed(), false,
                        Math.max(1, plugin.getConfig().getInt("copper-rails.station-stop-ticks", 60))));
        RailData updated = new RailData(Math.max(0.0, speed), current.station(), current.stopTicks());
        rails.put(keyOf(block), updated);
        save();
        return updated;
    }

    boolean remove(Block block) {
        boolean removed = rails.remove(keyOf(block)) != null;
        if (removed) save();
        return removed;
    }

    int size() {
        return rails.size();
    }

    record RailData(double speedBps, boolean station, int stopTicks) {}
}

final class TrainListener implements Listener {

    private final NeverLandTrainsPlugin plugin;
    private final TrainManager trains;
    private final CopperRailRegistry rails;
    private final Map<UUID, UUID> couplingSelection = new ConcurrentHashMap<>();

    TrainListener(NeverLandTrainsPlugin plugin, TrainManager trains, CopperRailRegistry rails) {
        this.plugin = plugin;
        this.trains = trains;
        this.rails = rails;
    }

    @EventHandler
    public void onVehicleCreate(VehicleCreateEvent event) {
        Vehicle vehicle = event.getVehicle();
        if (vehicle instanceof Minecart cart) {
            trains.restoreOrRegister(cart);
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof Minecart cart) {
                trains.restoreOrRegister(cart);
            }
        }
    }

    @EventHandler
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (event.getVehicle() instanceof Minecart cart) {
            trains.removeCart(cart);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCartInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Minecart cart)) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        Material type = hand.getType();

        if (type == Material.IRON_CHAIN && player.hasPermission("neverlandtrains.couple")) {
            event.setCancelled(true);

            UUID selected = couplingSelection.get(player.getUniqueId());
            if (selected == null) {
                couplingSelection.put(player.getUniqueId(), cart.getUniqueId());
                trains.ensureTrain(cart);
                player.sendActionBar(Component.text(
                        "Первая вагонетка выбрана. Нажмите цепью по второй.",
                        NamedTextColor.YELLOW
                ));
                return;
            }

            couplingSelection.remove(player.getUniqueId());
            Entity firstEntity = Bukkit.getEntity(selected);
            if (!(firstEntity instanceof Minecart first)) {
                player.sendActionBar(Component.text("Первая вагонетка больше недоступна.", NamedTextColor.RED));
                return;
            }

            String result = trains.couple(first, cart);
            player.sendActionBar(Component.text(result,
                    result.startsWith("Состав сцеплен") ? NamedTextColor.GREEN : NamedTextColor.RED));
            return;
        }

        if (type == Material.SHEARS && player.hasPermission("neverlandtrains.couple")) {
            event.setCancelled(true);
            String result = trains.uncoupleBefore(cart);
            player.sendActionBar(Component.text(result,
                    result.startsWith("Состав разделён") ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
            return;
        }

        if (cart instanceof PoweredMinecart powered) {
            int configured = plugin.getConfig().getInt("locomotive.fuels." + type.name(), 0);
            if (configured > 0) {
                event.setCancelled(true);
                trains.addFuel(powered, player, type);
                return;
            }

            trains.ensureTrain(powered);
            player.sendActionBar(Component.text(
                    "Локомотив • топлива " + Math.max(0, powered.getFuel() / 20) + " сек.",
                    NamedTextColor.GOLD
            ));
        } else {
            trains.ensureTrain(cart);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRailInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.RAIL) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();

        if (hand.getType() == Material.COPPER_INGOT && player.hasPermission("neverlandtrains.rail")) {
            event.setCancelled(true);

            CopperRailRegistry.RailData existing = rails.get(block);
            if (player.isSneaking()) {
                if (rails.remove(block)) {
                    returnItem(player, new ItemStack(Material.COPPER_INGOT));
                    player.sendActionBar(Component.text("Медный апгрейд рельса снят.", NamedTextColor.YELLOW));
                } else {
                    player.sendActionBar(Component.text("Это обычный рельс.", NamedTextColor.GRAY));
                }
                return;
            }

            CopperRailRegistry.RailData data;
            if (existing == null) {
                data = rails.create(block);
                if (player.getGameMode() != GameMode.CREATIVE) {
                    hand.subtract(1);
                }
            } else {
                data = rails.cycleSpeed(block);
            }

            player.sendActionBar(Component.text(
                    "Медный рельс: " + String.format(Locale.ROOT, "%.0f", data.speedBps()) + " б/с"
                            + (data.station() ? " • станция" : ""),
                    NamedTextColor.GOLD
            ));
            return;
        }

        if (hand.getType() == Material.CLOCK
                && player.hasPermission("neverlandtrains.rail")
                && rails.get(block) != null) {
            event.setCancelled(true);
            CopperRailRegistry.RailData data = rails.toggleStation(block);
            player.sendActionBar(Component.text(
                    data.station()
                            ? "Рельс теперь является остановкой станции."
                            : "Остановка станции отключена.",
                    data.station() ? NamedTextColor.GREEN : NamedTextColor.YELLOW
            ));
        }
    }

    private void returnItem(Player player, ItemStack item) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        for (ItemStack left : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }
}

final class TrainCommand implements CommandExecutor, TabCompleter {

    private final NeverLandTrainsPlugin plugin;
    private final TrainManager trains;
    private final CopperRailRegistry rails;

    TrainCommand(NeverLandTrainsPlugin plugin, TrainManager trains, CopperRailRegistry rails) {
        this.plugin = plugin;
        this.trains = trains;
        this.rails = rails;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(Component.text(
                    "NeverLandTrains: составов " + trains.trainCount()
                            + ", активных вагонеток " + trains.scheduledCartCount()
                            + ", медных рельсов " + rails.size(),
                    NamedTextColor.GOLD
            ));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("neverlandtrains.admin")) {
                sender.sendMessage(Component.text("Нет прав.", NamedTextColor.RED));
                return true;
            }
            plugin.reloadPluginConfig();
            sender.sendMessage(Component.text("NeverLandTrains перезагружен.", NamedTextColor.GREEN));
            return true;
        }

        if (args[0].equalsIgnoreCase("train")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only player.");
                return true;
            }
            Entity vehicle = player.getVehicle();
            if (!(vehicle instanceof Minecart cart)) {
                player.sendMessage(Component.text("Вы должны находиться в вагонетке.", NamedTextColor.YELLOW));
                return true;
            }
            player.sendMessage(Component.text(trains.describe(cart), NamedTextColor.GOLD));
            return true;
        }

        if (args[0].equalsIgnoreCase("rail")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only player.");
                return true;
            }
            if (!sender.hasPermission("neverlandtrains.admin")) {
                sender.sendMessage(Component.text("Нет прав.", NamedTextColor.RED));
                return true;
            }

            Block block = player.getTargetBlockExact(6);
            if (block == null || block.getType() != Material.RAIL) {
                player.sendMessage(Component.text("Посмотрите на обычный рельс.", NamedTextColor.YELLOW));
                return true;
            }

            if (args.length >= 2 && args[1].equalsIgnoreCase("clear")) {
                rails.remove(block);
                player.sendMessage(Component.text("Метка медного рельса удалена.", NamedTextColor.GREEN));
                return true;
            }

            if (args.length >= 2 && args[1].equalsIgnoreCase("station")) {
                CopperRailRegistry.RailData data = rails.toggleStation(block);
                player.sendMessage(Component.text(
                        "Станция: " + (data.station() ? "включена" : "выключена"),
                        NamedTextColor.GREEN
                ));
                return true;
            }

            if (args.length >= 3 && args[1].equalsIgnoreCase("speed")) {
                try {
                    double speed = Double.parseDouble(args[2]);
                    CopperRailRegistry.RailData data = rails.setSpeed(block, speed);
                    player.sendMessage(Component.text(
                            "Скорость рельса: " + data.speedBps() + " б/с",
                            NamedTextColor.GREEN
                    ));
                } catch (NumberFormatException ex) {
                    player.sendMessage(Component.text("Укажите число.", NamedTextColor.RED));
                }
                return true;
            }

            player.sendMessage(Component.text(
                    "/nltrains rail speed <б/с> | station | clear",
                    NamedTextColor.YELLOW
            ));
            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("status", "train", "rail", "reload"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("rail")) {
            return filter(List.of("speed", "station", "clear"), args[1]);
        }
        if (args.length == 3
                && args[0].equalsIgnoreCase("rail")
                && args[1].equalsIgnoreCase("speed")) {
            return filter(List.of("8", "16", "24", "32", "40", "48", "64"), args[2]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> values, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) result.add(value);
        }
        return result;
    }
}
