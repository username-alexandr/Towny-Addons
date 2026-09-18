package ru.neverland.horseupgrades;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityAirChangeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.HorseInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NeverLandHorseUpgradesPlugin extends JavaPlugin {

    HorseUpgradeListener listener;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        listener = new HorseUpgradeListener(this);
        getServer().getPluginManager().registerEvents(listener, this);

        HorseCommand command = new HorseCommand(this, listener);
        if (getCommand("nlhorse") != null) {
            getCommand("nlhorse").setExecutor(command);
            getCommand("nlhorse").setTabCompleter(command);
        }

        getLogger().info("NeverLandHorseUpgrades 0.1.0-test enabled.");
    }

    @Override
    public void onDisable() {
        listener.shutdown();
    }
}

final class HorseUpgradeListener implements Listener {

    private final NeverLandHorseUpgradesPlugin plugin;
    private final Map<UUID, VillagerSelection> selectedVillagers = new ConcurrentHashMap<>();
    private final Set<UUID> waterTasks = ConcurrentHashMap.newKeySet();

    private static final Set<Enchantment> ALLOWED = Set.of(
            Enchantment.PROTECTION,
            Enchantment.FIRE_PROTECTION,
            Enchantment.BLAST_PROTECTION,
            Enchantment.PROJECTILE_PROTECTION,
            Enchantment.FEATHER_FALLING,
            Enchantment.THORNS,
            Enchantment.RESPIRATION,
            Enchantment.DEPTH_STRIDER
    );

    HorseUpgradeListener(NeverLandHorseUpgradesPlugin plugin) {
        this.plugin = plugin;
    }

    void shutdown() {
        selectedVillagers.clear();
        waterTasks.clear();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack first = event.getInventory().getFirstItem();
        ItemStack second = event.getInventory().getSecondItem();
        if (!isHorseArmor(first) || second == null || second.getType() != Material.ENCHANTED_BOOK) return;

        if (!(second.getItemMeta() instanceof EnchantmentStorageMeta bookMeta)) return;
        Map<Enchantment, Integer> stored = bookMeta.getStoredEnchants();
        if (stored.isEmpty()) return;

        ItemStack result = first.clone();
        result.setAmount(1);
        ItemMeta meta = result.getItemMeta();

        boolean changed = false;
        int applied = 0;

        for (Map.Entry<Enchantment, Integer> entry : stored.entrySet()) {
            Enchantment enchantment = entry.getKey();
            if (!ALLOWED.contains(enchantment)) continue;

            int current = meta.getEnchantLevel(enchantment);
            int incoming = Math.max(1, entry.getValue());
            int max = enchantment.getMaxLevel();

            int next;
            if (current == incoming && current < max) {
                next = current + 1;
            } else {
                next = Math.max(current, incoming);
            }
            next = Math.min(max, next);

            if (next > current) {
                meta.addEnchant(enchantment, next, true);
                changed = true;
                applied++;
            }
        }

        if (!changed) return;

        result.setItemMeta(meta);
        event.setResult(result);

        AnvilInventory inventory = event.getInventory();
        inventory.setRepairCost(Math.max(1, 2 + applied * 2));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHorseDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Horse horse)) return;

        ItemStack armor = horse.getInventory().getArmor();
        if (!isHorseArmor(armor)) return;

        double reduction = 0.0;

        reduction += level(armor, Enchantment.PROTECTION)
                * plugin.getConfig().getDouble("horse-armor.protection-per-level", 0.04);

        EntityDamageEvent.DamageCause cause = event.getCause();

        if (isFire(cause)) {
            reduction += level(armor, Enchantment.FIRE_PROTECTION)
                    * plugin.getConfig().getDouble("horse-armor.fire-protection-per-level", 0.08);
        }

        if (cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            reduction += level(armor, Enchantment.BLAST_PROTECTION)
                    * plugin.getConfig().getDouble("horse-armor.blast-protection-per-level", 0.08);
        }

        if (cause == EntityDamageEvent.DamageCause.PROJECTILE) {
            reduction += level(armor, Enchantment.PROJECTILE_PROTECTION)
                    * plugin.getConfig().getDouble("horse-armor.projectile-protection-per-level", 0.08);
        }

        if (cause == EntityDamageEvent.DamageCause.FALL) {
            reduction += level(armor, Enchantment.FEATHER_FALLING)
                    * plugin.getConfig().getDouble("horse-armor.feather-falling-per-level", 0.12);
        }

        double cap = plugin.getConfig().getDouble("horse-armor.max-total-reduction", 0.80);
        reduction = Math.max(0.0, Math.min(cap, reduction));

        if (reduction > 0.0) {
            event.setDamage(event.getDamage() * (1.0 - reduction));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHorseDamagedByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Horse horse)) return;
        if (!(event.getDamager() instanceof LivingEntity attacker)) return;

        ItemStack armor = horse.getInventory().getArmor();
        int thorns = level(armor, Enchantment.THORNS);
        if (thorns <= 0) return;

        double damage = thorns
                * plugin.getConfig().getDouble("horse-armor.thorns-damage-per-level", 0.75);

        attacker.getScheduler().execute(
                plugin,
                () -> attacker.damage(damage, horse),
                null,
                1L
        );
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAirChange(EntityAirChangeEvent event) {
        if (!(event.getEntity() instanceof Horse horse)) return;
        if (!horse.isInWater()) return;

        ItemStack armor = horse.getInventory().getArmor();
        int respiration = level(armor, Enchantment.RESPIRATION);
        if (respiration <= 0) return;

        int max = horse.getMaximumAir();
        int bonus = plugin.getConfig().getInt("horse-armor.respiration-air-bonus-ticks-per-level", 200)
                * respiration;

        if (event.getAmount() < max && horse.getTicksLived() % Math.max(1, bonus / 20) == 0) {
            event.setAmount(Math.min(max, event.getAmount() + 1));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVillagerSelect(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() != leadMaterial()) return;
        if (!player.isSneaking()) return;
        if (!player.hasPermission("neverlandhorse.transport")) return;

        if (event.getRightClicked() instanceof Villager villager) {
            event.setCancelled(true);

            if (plugin.getConfig().getBoolean("villager-transport.require-adult-villager", true)
                    && !villager.isAdult()) {
                player.sendActionBar(Component.text("Можно перевозить только взрослых жителей.", NamedTextColor.RED));
                return;
            }

            if (villager.isInsideVehicle()) {
                player.sendActionBar(Component.text("Этот житель уже находится в транспорте.", NamedTextColor.RED));
                return;
            }

            selectedVillagers.put(player.getUniqueId(),
                    new VillagerSelection(villager.getUniqueId(), System.currentTimeMillis()));

            player.sendActionBar(Component.text(
                    "Житель выбран. Shift+ПКМ поводком по лошади.",
                    NamedTextColor.GOLD
            ));
            return;
        }

        if (event.getRightClicked() instanceof Horse horse) {
            event.setCancelled(true);

            Villager existingPassenger = findVillagerPassenger(horse);
            if (existingPassenger != null) {
                unloadVillager(player, horse, existingPassenger);
                return;
            }

            VillagerSelection selection = selectedVillagers.remove(player.getUniqueId());
            if (selection == null || selection.expired(timeoutMillis())) {
                player.sendActionBar(Component.text("Сначала выберите жителя поводком.", NamedTextColor.YELLOW));
                return;
            }

            Entity entity = Bukkit.getEntity(selection.villagerId());
            if (!(entity instanceof Villager villager) || !villager.isValid()) {
                player.sendActionBar(Component.text("Выбранный житель больше недоступен.", NamedTextColor.RED));
                return;
            }

            if (plugin.getConfig().getBoolean("villager-transport.require-adult-horse", true)
                    && !horse.isAdult()) {
                player.sendActionBar(Component.text("Нужна взрослая лошадь.", NamedTextColor.RED));
                return;
            }

            double maxDistance = plugin.getConfig().getDouble("villager-transport.max-selection-distance", 8.0);
            if (!horse.getWorld().equals(villager.getWorld())
                    || horse.getLocation().distanceSquared(villager.getLocation()) > maxDistance * maxDistance) {
                player.sendActionBar(Component.text("Житель слишком далеко от лошади.", NamedTextColor.RED));
                return;
            }

            if (!horse.getPassengers().isEmpty()) {
                player.sendActionBar(Component.text("Лошадь уже занята.", NamedTextColor.RED));
                return;
            }

            if (plugin.getConfig().getBoolean("villager-transport.require-saddled-horse", false)
                    && horse.getInventory().getSaddle() == null) {
                player.sendActionBar(Component.text("Для перевозки нужна осёдланная лошадь.", NamedTextColor.RED));
                return;
            }

            horse.getScheduler().execute(plugin, () -> {
                if (!horse.isValid() || !villager.isValid()) return;

                boolean mounted = horse.addPassenger(villager);
                if (mounted) {
                    player.sendActionBar(Component.text(
                            "Житель посажен на лошадь. Ведите лошадь поводком.",
                            NamedTextColor.GREEN
                    ));
                    horse.getWorld().playSound(horse.getLocation(), Sound.ENTITY_HORSE_SADDLE, 0.8f, 1.1f);
                } else {
                    player.sendActionBar(Component.text("Не удалось посадить жителя.", NamedTextColor.RED));
                }
            }, null, 1L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        selectedVillagers.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onHorseMove(VehicleMoveEvent event) {
        if (!(event.getVehicle() instanceof Horse horse)) return;
        if (!horse.isInWater()) return;
        rescanDepthStrider(horse);
    }

    void startDepthStriderTask(Horse horse) {
        if (!waterTasks.add(horse.getUniqueId())) return;

        horse.getScheduler().runAtFixedRate(
                plugin,
                task -> tickDepthStrider(horse, task),
                () -> waterTasks.remove(horse.getUniqueId()),
                1L,
                2L
        );
    }

    private void tickDepthStrider(Horse horse, ScheduledTask task) {
        if (!horse.isValid() || horse.isDead()) {
            waterTasks.remove(horse.getUniqueId());
            task.cancel();
            return;
        }

        ItemStack armor = horse.getInventory().getArmor();
        int level = level(armor, Enchantment.DEPTH_STRIDER);

        if (level <= 0) {
            waterTasks.remove(horse.getUniqueId());
            task.cancel();
            return;
        }

        if (!horse.isInWater()) return;

        double bonus = plugin.getConfig().getDouble("horse-armor.depth-strider-water-speed-per-level", 0.08)
                * level;

        Vector velocity = horse.getVelocity();
        Vector horizontal = velocity.clone().setY(0);
        if (horizontal.lengthSquared() < 0.0001) return;

        horizontal.multiply(1.0 + bonus);
        velocity.setX(horizontal.getX());
        velocity.setZ(horizontal.getZ());
        horse.setVelocity(velocity);
    }

    void rescanDepthStrider(Horse horse) {
        if (level(horse.getInventory().getArmor(), Enchantment.DEPTH_STRIDER) > 0) {
            startDepthStriderTask(horse);
        }
    }

    private void unloadVillager(Player player, Horse horse, Villager villager) {
        horse.getScheduler().execute(plugin, () -> {
            horse.removePassenger(villager);

            Location base = horse.getLocation();
            Location target = base.clone().add(base.getDirection().setY(0).normalize().multiply(-1.3));
            target.setY(base.getY());

            villager.getScheduler().execute(plugin, () -> villager.teleportAsync(target), null, 1L);

            player.sendActionBar(Component.text("Житель высажен.", NamedTextColor.GREEN));
        }, null, 1L);
    }

    private Villager findVillagerPassenger(Horse horse) {
        for (Entity passenger : horse.getPassengers()) {
            if (passenger instanceof Villager villager) return villager;
        }
        return null;
    }

    private long timeoutMillis() {
        return Math.max(1, plugin.getConfig().getLong("villager-transport.selection-timeout-seconds", 30)) * 1000L;
    }

    private int level(ItemStack armor, Enchantment enchantment) {
        if (!isHorseArmor(armor)) return 0;
        return armor.getEnchantmentLevel(enchantment);
    }

    private boolean isHorseArmor(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        return item.getType().name().endsWith("_HORSE_ARMOR");
    }

    private boolean isFire(EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.FIRE
                || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                || cause == EntityDamageEvent.DamageCause.LAVA
                || cause == EntityDamageEvent.DamageCause.HOT_FLOOR;
    }

    private Material leadMaterial() {
        Material lead = Material.matchMaterial("LEAD");
        if (lead != null) return lead;
        Material leash = Material.matchMaterial("LEASH");
        return leash == null ? Material.STRING : leash;
    }

    private record VillagerSelection(UUID villagerId, long selectedAtMillis) {
        boolean expired(long timeoutMillis) {
            return System.currentTimeMillis() - selectedAtMillis > timeoutMillis;
        }
    }
}

final class HorseCommand implements CommandExecutor, TabCompleter {

    private final NeverLandHorseUpgradesPlugin plugin;
    private final HorseUpgradeListener listener;

    HorseCommand(NeverLandHorseUpgradesPlugin plugin, HorseUpgradeListener listener) {
        this.plugin = plugin;
        this.listener = listener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(Component.text(
                    "NeverLandHorseUpgrades 0.1.0-test • Purpur 26.2",
                    NamedTextColor.GOLD
            ));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("neverlandhorse.admin")) {
                sender.sendMessage(Component.text("Нет прав.", NamedTextColor.RED));
                return true;
            }
            plugin.reloadConfig();
            sender.sendMessage(Component.text("Конфиг перезагружен.", NamedTextColor.GREEN));
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("neverlandhorse.admin")) {
                sender.sendMessage(Component.text("Нет прав.", NamedTextColor.RED));
                return true;
            }

            Player target;
            if (args.length >= 2) {
                target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(Component.text("Игрок не найден.", NamedTextColor.RED));
                    return true;
                }
            } else if (sender instanceof Player player) {
                target = player;
            } else {
                sender.sendMessage("Укажите игрока.");
                return true;
            }

            ItemStack armor = new ItemStack(Material.DIAMOND_HORSE_ARMOR);
            ItemMeta meta = armor.getItemMeta();
            meta.addEnchant(Enchantment.PROTECTION, 4, true);
            meta.addEnchant(Enchantment.FEATHER_FALLING, 4, true);
            meta.addEnchant(Enchantment.THORNS, 3, true);
            meta.addEnchant(Enchantment.DEPTH_STRIDER, 3, true);
            armor.setItemMeta(meta);

            Map<Integer, ItemStack> overflow = target.getInventory().addItem(armor);
            for (ItemStack left : overflow.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), left);
            }

            sender.sendMessage(Component.text("Тестовая конская броня выдана.", NamedTextColor.GREEN));
            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("status", "give", "reload"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) names.add(player.getName());
            return filter(names, args[1]);
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
