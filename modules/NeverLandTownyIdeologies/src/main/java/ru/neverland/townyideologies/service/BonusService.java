package ru.neverland.townyideologies.service;

import com.palmergames.bukkit.towny.object.Town;
import io.papermc.paper.event.player.PlayerTradeEvent;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.townyideologies.data.DataStore;
import ru.neverland.townyideologies.integration.TownyHook;
import ru.neverland.townyideologies.model.TownIdeology;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class BonusService implements Listener {
    private static final double VANILLA_MINECART_SPEED = 0.4D;

    private final JavaPlugin plugin;
    private final TownyHook towny;
    private final DataStore data;
    private final IdeologyService ideologies;
    private final IdeologyRegistry registry;
    private final NamespacedKey healthKey;
    private final Map<java.util.UUID, Long> combatUntil = new HashMap<>();
    private BukkitTask task;

    public BonusService(JavaPlugin plugin, TownyHook towny, DataStore data,
                        IdeologyService ideologies, IdeologyRegistry registry) {
        this.plugin = plugin;
        this.towny = towny;
        this.data = data;
        this.ideologies = ideologies;
        this.registry = registry;
        this.healthKey = new NamespacedKey(plugin, "health_bonus");
    }

    public void start() {
        stopTask();
        long refresh = Math.max(20L, plugin.getConfig().getLong("settings.effects.refresh-ticks", 40L));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshPlayers, 1L, refresh);
    }

    public void stop() {
        stopTask();
        for (Player player : Bukkit.getOnlinePlayers()) removeHealthBonus(player);
        combatUntil.clear();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent event) {
        Town town = towny.townAt(event.getBlock().getLocation());
        TownIdeology ideology = current(town, "agriculture");
        if (ideology == null) return;
        BlockData newData = event.getNewState().getBlockData();
        if (!(newData instanceof Ageable ageable)) return;
        double multiplier = registry.levelDouble("agriculture", "growth-multiplier", ideology.level(), 1.0D);
        int guaranteed = (int) Math.floor(Math.max(0.0D, multiplier - 1.0D));
        double chance = Math.max(0.0D, multiplier - 1.0D - guaranteed);
        int extra = guaranteed + (ThreadLocalRandom.current().nextDouble() < chance ? 1 : 0);
        if (extra > 0) {
            ageable.setAge(Math.min(ageable.getMaximumAge(), ageable.getAge() + extra));
            event.getNewState().setBlockData(ageable);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSmelt(FurnaceStartSmeltEvent event) {
        Town town = towny.townAt(event.getBlock().getLocation());
        TownIdeology ideology = current(town, "industry");
        if (ideology == null) return;
        double multiplier = registry.levelDouble("industry", "smelt-multiplier", ideology.level(), 1.0D);
        if (multiplier > 1.0D) event.setTotalCookTime(Math.max(1, (int) Math.ceil(event.getTotalCookTime() / multiplier)));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!ideologies.active(event.getPlayer(), "infrastructure", true)) return;
        TownIdeology ideology = ideologies.get(event.getPlayer()).orElse(null);
        if (ideology == null) return;
        int cooldown = Math.max(0, registry.levelInt("infrastructure", "placement-cooldown-ticks", ideology.level(), 0));
        ItemStack used = event.getItemInHand();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.getPlayer().isOnline()) event.getPlayer().setCooldown(used.getType(), cooldown);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMinecartEnter(VehicleEnterEvent event) {
        if (!(event.getVehicle() instanceof Minecart minecart) || !(event.getEntered() instanceof Player player)) return;
        if (!ideologies.active(player, "infrastructure", true)) return;
        TownIdeology ideology = ideologies.get(player).orElse(null);
        if (ideology == null) return;
        double multiplier = registry.levelDouble("infrastructure", "minecart-multiplier", ideology.level(), 1.0D);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isInsideVehicle() && player.getVehicle() == minecart) {
                minecart.setMaxSpeed(VANILLA_MINECART_SPEED * Math.max(1.0D, multiplier));
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMinecartExit(VehicleExitEvent event) {
        if (!(event.getVehicle() instanceof Minecart minecart)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            boolean infrastructurePassenger = minecart.getPassengers().stream()
                    .filter(Player.class::isInstance).map(Player.class::cast)
                    .anyMatch(player -> ideologies.active(player, "infrastructure", true));
            if (!infrastructurePassenger && minecart.isValid()) minecart.setMaxSpeed(VANILLA_MINECART_SPEED);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent event) {
        long seconds = Math.max(1L, plugin.getConfig().getLong("settings.effects.defense-combat-seconds", 15L));
        long until = System.currentTimeMillis() + seconds * 1000L;
        if (event.getEntity() instanceof Player victim && ideologies.active(victim, "defense", true)) {
            combatUntil.put(victim.getUniqueId(), until);
        }
        Player attacker = attacker(event.getDamager());
        if (attacker != null && ideologies.active(attacker, "defense", true)) {
            combatUntil.put(attacker.getUniqueId(), until);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrade(PlayerTradeEvent event) {
        Player player = event.getPlayer();
        if (!ideologies.active(player, "trade", true)) return;
        TownIdeology ideology = ideologies.get(player).orElse(null);
        if (ideology == null) return;
        ItemStack result = event.getTrade().getResult();
        if (result == null || result.getType().isAir()) return;
        double multiplier = registry.levelDouble("trade", "trade-output-multiplier", ideology.level(), 1.0D);
        double expected = result.getAmount() * Math.max(0.0D, multiplier - 1.0D);
        int bonus = (int) Math.floor(expected);
        if (ThreadLocalRandom.current().nextDouble() < expected - bonus) bonus++;
        if (bonus <= 0) return;
        int amount = bonus;
        Bukkit.getScheduler().runTask(plugin, () -> giveTradeBonus(player, result, amount));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        combatUntil.remove(event.getPlayer().getUniqueId());
        removeHealthBonus(event.getPlayer());
    }

    private void refreshPlayers() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshHealth(player);
            Long until = combatUntil.get(player.getUniqueId());
            if (until == null || until < now || !ideologies.active(player, "defense", true)) {
                if (until != null && until < now) combatUntil.remove(player.getUniqueId());
                continue;
            }
            TownIdeology ideology = ideologies.get(player).orElse(null);
            if (ideology != null) applyDefense(player, ideology.level());
        }
    }

    private void refreshHealth(Player player) {
        double hearts = 0.0D;
        if (ideologies.active(player, "healthcare", true)) {
            TownIdeology ideology = ideologies.get(player).orElse(null);
            if (ideology != null) hearts = registry.levelDouble("healthcare", "health-hearts", ideology.level(), 0.0D);
        }
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute == null) return;
        AttributeModifier current = attribute.getModifier(healthKey);
        double healthPoints = Math.max(0.0D, hearts * 2.0D);
        if (healthPoints <= 0.0D) {
            if (current != null) attribute.removeModifier(current);
        } else if (current == null || Math.abs(current.getAmount() - healthPoints) > 0.0001D) {
            if (current != null) attribute.removeModifier(current);
            attribute.addTransientModifier(new AttributeModifier(healthKey, healthPoints, AttributeModifier.Operation.ADD_NUMBER));
        }
        if (player.getHealth() > attribute.getValue()) player.setHealth(attribute.getValue());
    }

    private void removeHealthBonus(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute == null) return;
        AttributeModifier current = attribute.getModifier(healthKey);
        if (current != null) attribute.removeModifier(current);
        if (player.getHealth() > attribute.getValue()) player.setHealth(attribute.getValue());
    }

    private void applyDefense(Player player, int level) {
        int duration = Math.max(40, plugin.getConfig().getInt("settings.effects.duration-ticks", 100));
        boolean ambient = plugin.getConfig().getBoolean("settings.effects.ambient", true);
        boolean particles = plugin.getConfig().getBoolean("settings.effects.particles", false);
        boolean icon = plugin.getConfig().getBoolean("settings.effects.icon", true);
        for (String entry : registry.levelStrings("defense", "effects", level)) {
            String[] parts = entry.split(":", 2);
            PotionEffectType type = Registry.MOB_EFFECT.get(NamespacedKey.minecraft(parts[0].toLowerCase(Locale.ROOT)));
            if (type == null) {
                plugin.getLogger().warning("Неизвестный эффект обороны: " + entry);
                continue;
            }
            int amplifier = 0;
            if (parts.length == 2) {
                try { amplifier = Math.max(0, Integer.parseInt(parts[1])); }
                catch (NumberFormatException ignored) { }
            }
            player.addPotionEffect(new PotionEffect(type, duration, amplifier, ambient, particles, icon));
        }
    }

    private TownIdeology current(Town town, String expected) {
        if (town == null) return null;
        TownIdeology ideology = data.get(town.getUUID()).orElse(null);
        return ideology != null && ideology.ideologyId().equalsIgnoreCase(expected) ? ideology : null;
    }

    private Player attacker(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) return player;
        }
        return null;
    }

    private void giveTradeBonus(Player player, ItemStack source, int amount) {
        if (!player.isOnline()) return;
        int remaining = amount;
        int max = Math.max(1, source.getMaxStackSize());
        while (remaining > 0) {
            ItemStack bonus = source.clone();
            bonus.setAmount(Math.min(max, remaining));
            remaining -= bonus.getAmount();
            for (ItemStack leftover : player.getInventory().addItem(bonus).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
