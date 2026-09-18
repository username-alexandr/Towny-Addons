package ru.neverland.horseupgrades;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
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
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NeverLandHorseUpgradesPlugin extends JavaPlugin {

    NamespacedKey bookTypeKey;
    NamespacedKey bookLevelKey;
    NamespacedKey rearHorseKey;

    HorseUpgradeListener listener;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        bookTypeKey = new NamespacedKey(this, "upgrade_book");
        bookLevelKey = new NamespacedKey(this, "upgrade_book_level");
        rearHorseKey = new NamespacedKey(this, "rear_horse");

        listener = new HorseUpgradeListener(this);
        getServer().getPluginManager().registerEvents(listener, this);

        HorseCommand command = new HorseCommand(this, listener);
        if (getCommand("nlhorse") != null) {
            getCommand("nlhorse").setExecutor(command);
            getCommand("nlhorse").setTabCompleter(command);
        }

        getLogger().info("NeverLandHorseUpgrades 0.2.0-test enabled.");
    }

    @Override
    public void onDisable() {
        listener.shutdown();
    }

    NamespacedKey upgradeKey(HorseUpgrade upgrade) {
        return new NamespacedKey(this, "upgrade_" + upgrade.id);
    }

    NamespacedKey modifierKey(HorseUpgrade upgrade) {
        return new NamespacedKey(this, "modifier_" + upgrade.id);
    }
}

enum HorseUpgrade {
    SPEED("speed", "Быстрые копыта", 3),
    JUMP("jump", "Высокий прыжок", 3),
    DASH("dash", "Рывок", 3),
    ENDURANCE("endurance", "Выносливость", 3),
    STABILITY("stability", "Стойкость", 3);

    final String id;
    final String displayName;
    final int maxLevel;

    HorseUpgrade(String id, String displayName, int maxLevel) {
        this.id = id;
        this.displayName = displayName;
        this.maxLevel = maxLevel;
    }

    static HorseUpgrade byId(String raw) {
        if (raw == null) return null;
        for (HorseUpgrade upgrade : values()) {
            if (upgrade.id.equalsIgnoreCase(raw) || upgrade.name().equalsIgnoreCase(raw)) return upgrade;
        }
        return null;
    }

    String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            default -> "III";
        };
    }
}

final class HorseUpgradeListener implements Listener {

    private final NeverLandHorseUpgradesPlugin plugin;

    private final Map<UUID, VillagerSelection> selectedVillagers = new ConcurrentHashMap<>();
    private final Set<UUID> waterTasks = ConcurrentHashMap.newKeySet();

    private final Map<UUID, UUID> rearSeats = new ConcurrentHashMap<>();
    private final Set<UUID> rearSeatTasks = ConcurrentHashMap.newKeySet();

    private final Map<UUID, StaminaState> stamina = new ConcurrentHashMap<>();
    private final Set<UUID> sprintLatch = ConcurrentHashMap.newKeySet();

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
        rearSeatTasks.clear();
        rearSeats.clear();
        stamina.clear();
        sprintLatch.clear();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack first = event.getInventory().getFirstItem();
        ItemStack second = event.getInventory().getSecondItem();
        if (!isHorseArmor(first) || second == null || second.getType() != Material.ENCHANTED_BOOK) return;

        HorseUpgrade special = specialBookType(second);
        if (special != null) {
            prepareSpecialAnvil(event, first, second, special);
            return;
        }

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

            int next = current == incoming && current < max
                    ? current + 1
                    : Math.max(current, incoming);

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
        event.getInventory().setRepairCost(Math.max(1, 2 + applied * 2));
    }

    private void prepareSpecialAnvil(PrepareAnvilEvent event, ItemStack first, ItemStack book, HorseUpgrade upgrade) {
        int incoming = Math.max(1, Math.min(upgrade.maxLevel, specialBookLevel(book)));
        int current = upgradeLevel(first, upgrade);

        int next = current == incoming && current < upgrade.maxLevel
                ? current + 1
                : Math.max(current, incoming);

        next = Math.min(upgrade.maxLevel, next);
        if (next <= current) return;

        ItemStack result = first.clone();
        result.setAmount(1);

        ItemMeta meta = result.getItemMeta();
        meta.getPersistentDataContainer().set(plugin.upgradeKey(upgrade), PersistentDataType.INTEGER, next);

        refreshSpecialLore(meta);
        refreshAttributeModifiers(meta);
        meta.setEnchantmentGlintOverride(true);

        result.setItemMeta(meta);
        event.setResult(result);

        AnvilInventory inventory = event.getInventory();
        inventory.setRepairCost(3 + next * 2);
    }

    ItemStack createUpgradeBook(HorseUpgrade upgrade, int requestedLevel) {
        int level = Math.max(1, Math.min(upgrade.maxLevel, requestedLevel));

        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();

        meta.setDisplayName("§b" + upgrade.displayName + " " + upgrade.roman(level));
        meta.setLore(List.of(
                "§7Специальное улучшение конской брони",
                "§8Соедините с конской бронёй в наковальне.",
                "§bУровень: " + level + "/" + upgrade.maxLevel
        ));

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(plugin.bookTypeKey, PersistentDataType.STRING, upgrade.id);
        pdc.set(plugin.bookLevelKey, PersistentDataType.INTEGER, level);
        meta.setEnchantmentGlintOverride(true);

        book.setItemMeta(meta);
        return book;
    }

    ItemStack createFullyUpgradedTestArmor() {
        ItemStack armor = new ItemStack(Material.DIAMOND_HORSE_ARMOR);
        ItemMeta meta = armor.getItemMeta();

        meta.addEnchant(Enchantment.PROTECTION, 4, true);
        meta.addEnchant(Enchantment.FEATHER_FALLING, 4, true);
        meta.addEnchant(Enchantment.THORNS, 3, true);
        meta.addEnchant(Enchantment.DEPTH_STRIDER, 3, true);

        for (HorseUpgrade upgrade : HorseUpgrade.values()) {
            meta.getPersistentDataContainer().set(
                    plugin.upgradeKey(upgrade),
                    PersistentDataType.INTEGER,
                    upgrade.maxLevel
            );
        }

        refreshSpecialLore(meta);
        refreshAttributeModifiers(meta);
        meta.setEnchantmentGlintOverride(true);

        armor.setItemMeta(meta);
        return armor;
    }

    private void refreshSpecialLore(ItemMeta meta) {
        List<String> lore = meta.hasLore() && meta.getLore() != null
                ? new ArrayList<>(meta.getLore())
                : new ArrayList<>();

        lore.removeIf(line -> line != null && line.startsWith("§b✦ "));

        for (HorseUpgrade upgrade : HorseUpgrade.values()) {
            Integer level = meta.getPersistentDataContainer().get(
                    plugin.upgradeKey(upgrade),
                    PersistentDataType.INTEGER
            );
            if (level != null && level > 0) {
                lore.add("§b✦ " + upgrade.displayName + " " + upgrade.roman(level));
            }
        }

        meta.setLore(lore.isEmpty() ? null : lore);
    }

    private void refreshAttributeModifiers(ItemMeta meta) {
        applyAttributeModifier(
                meta,
                HorseUpgrade.SPEED,
                Attribute.MOVEMENT_SPEED,
                plugin.getConfig().getDouble("special-upgrades.speed.scalar-per-level", 0.08),
                AttributeModifier.Operation.ADD_SCALAR
        );

        applyAttributeModifier(
                meta,
                HorseUpgrade.JUMP,
                Attribute.JUMP_STRENGTH,
                plugin.getConfig().getDouble("special-upgrades.jump.scalar-per-level", 0.10),
                AttributeModifier.Operation.ADD_SCALAR
        );

        applyAttributeModifier(
                meta,
                HorseUpgrade.STABILITY,
                Attribute.KNOCKBACK_RESISTANCE,
                plugin.getConfig().getDouble("special-upgrades.stability.resistance-per-level", 0.15),
                AttributeModifier.Operation.ADD_NUMBER
        );
    }

    private void applyAttributeModifier(
            ItemMeta meta,
            HorseUpgrade upgrade,
            Attribute attribute,
            double amountPerLevel,
            AttributeModifier.Operation operation
    ) {
        NamespacedKey key = plugin.modifierKey(upgrade);

        Collection<AttributeModifier> existing = meta.getAttributeModifiers(attribute);
        if (existing != null) {
            for (AttributeModifier modifier : new ArrayList<>(existing)) {
                if (modifier.getKey().equals(key)) {
                    meta.removeAttributeModifier(attribute, modifier);
                }
            }
        }

        Integer level = meta.getPersistentDataContainer().get(
                plugin.upgradeKey(upgrade),
                PersistentDataType.INTEGER
        );

        if (level == null || level <= 0) return;

        AttributeModifier modifier = new AttributeModifier(
                key,
                amountPerLevel * level,
                operation,
                EquipmentSlotGroup.BODY
        );
        meta.addAttributeModifier(attribute, modifier);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInput(PlayerInputEvent event) {
        Player player = event.getPlayer();
        Input input = event.getInput();

        if (!input.isSprint()) {
            sprintLatch.remove(player.getUniqueId());
            return;
        }

        if (!input.isForward()) return;
        if (!sprintLatch.add(player.getUniqueId())) return;

        if (!(player.getVehicle() instanceof Horse horse)) return;

        ItemStack armor = horse.getInventory().getArmor();
        int dashLevel = upgradeLevel(armor, HorseUpgrade.DASH);
        if (dashLevel <= 0) return;

        tryDash(player, horse, armor, dashLevel);
    }

    private void tryDash(Player player, Horse horse, ItemStack armor, int dashLevel) {
        int enduranceLevel = upgradeLevel(armor, HorseUpgrade.ENDURANCE);

        double maxStamina = plugin.getConfig().getDouble("special-upgrades.endurance.base-max-stamina", 100.0)
                + enduranceLevel
                * plugin.getConfig().getDouble("special-upgrades.endurance.max-stamina-per-level", 20.0);

        double regenPerSecond = plugin.getConfig().getDouble("special-upgrades.endurance.base-regen-per-second", 12.0)
                + enduranceLevel
                * plugin.getConfig().getDouble("special-upgrades.endurance.regen-per-level", 3.0);

        double baseCost = plugin.getConfig().getDouble("special-upgrades.dash.base-stamina-cost", 35.0);
        double reduction = enduranceLevel
                * plugin.getConfig().getDouble("special-upgrades.endurance.cost-reduction-per-level", 0.12);
        double cost = baseCost * Math.max(0.25, 1.0 - reduction);

        long cooldownMillis = Math.max(1,
                plugin.getConfig().getLong("special-upgrades.dash.cooldown-ticks", 60)) * 50L;

        StaminaState state = stamina.computeIfAbsent(
                horse.getUniqueId(),
                ignored -> new StaminaState(maxStamina, System.nanoTime(), 0L)
        );

        synchronized (state) {
            state.refresh(maxStamina, regenPerSecond);

            long nowMillis = System.currentTimeMillis();
            if (nowMillis < state.cooldownUntilMillis) {
                long left = Math.max(1, (state.cooldownUntilMillis - nowMillis + 999) / 1000);
                player.sendActionBar(Component.text(
                        "Рывок: перезарядка " + left + "с • выносливость "
                                + (int) state.value + "/" + (int) maxStamina,
                        NamedTextColor.YELLOW
                ));
                return;
            }

            if (state.value < cost) {
                player.sendActionBar(Component.text(
                        "Недостаточно выносливости: " + (int) state.value + "/" + (int) maxStamina,
                        NamedTextColor.RED
                ));
                return;
            }

            state.value -= cost;
            state.cooldownUntilMillis = nowMillis + cooldownMillis;

            double force = plugin.getConfig().getDouble("special-upgrades.dash.base-force", 0.60)
                    + (dashLevel - 1)
                    * plugin.getConfig().getDouble("special-upgrades.dash.force-per-level", 0.12);

            horse.getScheduler().execute(plugin, () -> {
                if (!horse.isValid() || horse.isDead()) return;

                Vector direction = player.getLocation().getDirection().setY(0);
                if (direction.lengthSquared() < 0.0001) {
                    direction = horse.getLocation().getDirection().setY(0);
                }
                if (direction.lengthSquared() < 0.0001) return;

                direction.normalize().multiply(force);

                Vector velocity = horse.getVelocity();
                velocity.add(direction);
                horse.setVelocity(velocity);
                horse.getWorld().playSound(horse.getLocation(), Sound.ENTITY_HORSE_GALLOP, 1.0f, 1.25f);
            }, null, 1L);

            player.sendActionBar(Component.text(
                    "Рывок " + HorseUpgrade.DASH.roman(dashLevel)
                            + " • выносливость " + (int) state.value + "/" + (int) maxStamina,
                    NamedTextColor.AQUA
            ));
        }
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

            if (rearHorseId(villager) != null || villager.isInsideVehicle()) {
                player.sendActionBar(Component.text("Этот житель уже находится в транспорте.", NamedTextColor.RED));
                return;
            }

            selectedVillagers.put(
                    player.getUniqueId(),
                    new VillagerSelection(villager.getUniqueId(), System.currentTimeMillis())
            );

            player.sendActionBar(Component.text(
                    "Житель выбран. Shift+ПКМ поводком по лошади.",
                    NamedTextColor.GOLD
            ));
            return;
        }

        if (!(event.getRightClicked() instanceof Horse horse)) return;

        event.setCancelled(true);

        Villager rear = findRearVillager(horse);
        if (rear != null) {
            unloadRearSeat(player, horse, rear);
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

        if (plugin.getConfig().getBoolean("villager-transport.require-saddled-horse", false)
                && horse.getInventory().getSaddle() == null) {
            player.sendActionBar(Component.text("Для перевозки нужна осёдланная лошадь.", NamedTextColor.RED));
            return;
        }

        double maxDistance = plugin.getConfig().getDouble("villager-transport.max-selection-distance", 8.0);
        if (!horse.getWorld().equals(villager.getWorld())
                || horse.getLocation().distanceSquared(villager.getLocation()) > maxDistance * maxDistance) {
            player.sendActionBar(Component.text("Житель слишком далеко от лошади.", NamedTextColor.RED));
            return;
        }

        mountRearSeat(player, horse, villager);
    }

    private void mountRearSeat(Player player, Horse horse, Villager villager) {
        rearSeats.put(horse.getUniqueId(), villager.getUniqueId());

        villager.getPersistentDataContainer().set(
                plugin.rearHorseKey,
                PersistentDataType.STRING,
                horse.getUniqueId().toString()
        );

        startRearSeatTask(horse, villager);

        player.sendActionBar(Component.text(
                "Житель размещён позади всадника. Теперь можно ехать вместе.",
                NamedTextColor.GREEN
        ));
        horse.getWorld().playSound(horse.getLocation(), Sound.ENTITY_HORSE_SADDLE, 0.8f, 1.15f);
    }

    private void startRearSeatTask(Horse horse, Villager villager) {
        UUID horseId = horse.getUniqueId();
        if (!rearSeatTasks.add(horseId)) return;

        horse.getScheduler().runAtFixedRate(
                plugin,
                task -> tickRearSeat(horse, villager, task),
                () -> rearSeatTasks.remove(horseId),
                1L,
                Math.max(1L, plugin.getConfig().getLong("villager-transport.rear-seat.sync-period-ticks", 1L))
        );
    }

    private void tickRearSeat(Horse horse, Villager villager, ScheduledTask task) {
        if (!horse.isValid() || horse.isDead() || !villager.isValid() || villager.isDead()) {
            rearSeatTasks.remove(horse.getUniqueId());
            rearSeats.remove(horse.getUniqueId());
            task.cancel();
            return;
        }

        UUID linkedHorse = rearHorseId(villager);
        if (!horse.getUniqueId().equals(linkedHorse)) {
            rearSeatTasks.remove(horse.getUniqueId());
            rearSeats.remove(horse.getUniqueId());
            task.cancel();
            return;
        }

        Location horseLocation = horse.getLocation();
        Vector backwards = horseLocation.getDirection().setY(0);

        if (backwards.lengthSquared() < 0.0001) {
            backwards = new Vector(0, 0, 1);
        }

        backwards.normalize().multiply(
                -plugin.getConfig().getDouble("villager-transport.rear-seat.offset-back", 0.85)
        );

        Location target = horseLocation.clone()
                .add(backwards)
                .add(0, plugin.getConfig().getDouble("villager-transport.rear-seat.offset-up", 0.72), 0);

        target.setYaw(horseLocation.getYaw());
        target.setPitch(0.0f);

        villager.getScheduler().execute(plugin, () -> {
            if (!villager.isValid()) return;
            villager.setVelocity(new Vector(0, 0, 0));
            villager.teleportAsync(target);
        }, null, 1L);
    }

    private void unloadRearSeat(Player player, Horse horse, Villager villager) {
        villager.getPersistentDataContainer().remove(plugin.rearHorseKey);
        rearSeats.remove(horse.getUniqueId());
        rearSeatTasks.remove(horse.getUniqueId());

        Location base = horse.getLocation();
        Vector side = base.getDirection().setY(0);

        if (side.lengthSquared() < 0.0001) side = new Vector(1, 0, 0);
        side.normalize();

        Vector perpendicular = new Vector(-side.getZ(), 0, side.getX()).multiply(1.4);
        Location target = base.clone().add(perpendicular);
        target.setY(base.getY());

        villager.getScheduler().execute(plugin, () -> villager.teleportAsync(target), null, 1L);

        player.sendActionBar(Component.text("Житель высажен рядом с лошадью.", NamedTextColor.GREEN));
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        List<Horse> horses = new ArrayList<>();
        List<Villager> villagers = new ArrayList<>();

        for (Entity entity : event.getEntities()) {
            if (entity instanceof Horse horse) horses.add(horse);
            if (entity instanceof Villager villager) villagers.add(villager);
        }

        for (Villager villager : villagers) {
            UUID horseId = rearHorseId(villager);
            if (horseId == null) continue;

            Entity entity = Bukkit.getEntity(horseId);
            if (entity instanceof Horse horse) {
                rearSeats.put(horseId, villager.getUniqueId());
                startRearSeatTask(horse, villager);
            }
        }

        for (Horse horse : horses) {
            for (Villager villager : villagers) {
                if (horse.getUniqueId().equals(rearHorseId(villager))) {
                    rearSeats.put(horse.getUniqueId(), villager.getUniqueId());
                    startRearSeatTask(horse, villager);
                }
            }
        }
    }

    @EventHandler
    public void onHorseDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Horse horse)) return;

        Villager rear = findRearVillager(horse);
        if (rear == null) return;

        rear.getPersistentDataContainer().remove(plugin.rearHorseKey);
        rearSeats.remove(horse.getUniqueId());
        rearSeatTasks.remove(horse.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        selectedVillagers.remove(event.getPlayer().getUniqueId());
        sprintLatch.remove(event.getPlayer().getUniqueId());
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

    private HorseUpgrade specialBookType(ItemStack book) {
        if (book == null || book.getType() != Material.ENCHANTED_BOOK || !book.hasItemMeta()) return null;
        String raw = book.getItemMeta().getPersistentDataContainer().get(
                plugin.bookTypeKey,
                PersistentDataType.STRING
        );
        return HorseUpgrade.byId(raw);
    }

    private int specialBookLevel(ItemStack book) {
        if (book == null || !book.hasItemMeta()) return 1;
        Integer value = book.getItemMeta().getPersistentDataContainer().get(
                plugin.bookLevelKey,
                PersistentDataType.INTEGER
        );
        return value == null ? 1 : value;
    }

    int upgradeLevel(ItemStack armor, HorseUpgrade upgrade) {
        if (!isHorseArmor(armor) || !armor.hasItemMeta()) return 0;

        Integer value = armor.getItemMeta().getPersistentDataContainer().get(
                plugin.upgradeKey(upgrade),
                PersistentDataType.INTEGER
        );

        return value == null ? 0 : Math.max(0, Math.min(upgrade.maxLevel, value));
    }

    private Villager findRearVillager(Horse horse) {
        UUID villagerId = rearSeats.get(horse.getUniqueId());
        if (villagerId != null) {
            Entity entity = Bukkit.getEntity(villagerId);
            if (entity instanceof Villager villager && villager.isValid()) return villager;
        }

        for (Entity nearby : horse.getNearbyEntities(4, 4, 4)) {
            if (nearby instanceof Villager villager
                    && horse.getUniqueId().equals(rearHorseId(villager))) {
                rearSeats.put(horse.getUniqueId(), villager.getUniqueId());
                return villager;
            }
        }

        return null;
    }

    private UUID rearHorseId(Villager villager) {
        String raw = villager.getPersistentDataContainer().get(
                plugin.rearHorseKey,
                PersistentDataType.STRING
        );

        if (raw == null) return null;

        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            villager.getPersistentDataContainer().remove(plugin.rearHorseKey);
            return null;
        }
    }

    private long timeoutMillis() {
        return Math.max(
                1,
                plugin.getConfig().getLong("villager-transport.selection-timeout-seconds", 30)
        ) * 1000L;
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

    private static final class StaminaState {
        double value;
        long lastRefreshNanos;
        long cooldownUntilMillis;

        StaminaState(double value, long lastRefreshNanos, long cooldownUntilMillis) {
            this.value = value;
            this.lastRefreshNanos = lastRefreshNanos;
            this.cooldownUntilMillis = cooldownUntilMillis;
        }

        void refresh(double max, double regenPerSecond) {
            long now = System.nanoTime();
            double seconds = Math.max(0.0, (now - lastRefreshNanos) / 1_000_000_000.0);
            value = Math.min(max, value + seconds * regenPerSecond);
            lastRefreshNanos = now;
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
                    "NeverLandHorseUpgrades 0.2.0-test • Purpur 26.2",
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

            Player target = resolveTarget(sender, args.length >= 2 ? args[1] : null);
            if (target == null) return true;

            giveOrDrop(target, listener.createFullyUpgradedTestArmor());
            sender.sendMessage(Component.text(
                    "Выдана тестовая конская броня со всеми улучшениями III.",
                    NamedTextColor.GREEN
            ));
            return true;
        }

        if (args[0].equalsIgnoreCase("book")) {
            if (!sender.hasPermission("neverlandhorse.admin")) {
                sender.sendMessage(Component.text("Нет прав.", NamedTextColor.RED));
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage(Component.text(
                        "/nlhorse book <speed|jump|dash|endurance|stability> [уровень] [игрок]",
                        NamedTextColor.YELLOW
                ));
                return true;
            }

            HorseUpgrade upgrade = HorseUpgrade.byId(args[1]);
            if (upgrade == null) {
                sender.sendMessage(Component.text("Неизвестное улучшение.", NamedTextColor.RED));
                return true;
            }

            int level = 1;
            if (args.length >= 3) {
                try {
                    level = Integer.parseInt(args[2]);
                } catch (NumberFormatException ignored) {
                    level = 1;
                }
            }

            String playerName = args.length >= 4 ? args[3] : null;
            Player target = resolveTarget(sender, playerName);
            if (target == null) return true;

            giveOrDrop(target, listener.createUpgradeBook(upgrade, level));
            sender.sendMessage(Component.text(
                    "Выдана книга: " + upgrade.displayName + " " + upgrade.roman(Math.max(1, Math.min(upgrade.maxLevel, level))),
                    NamedTextColor.GREEN
            ));
            return true;
        }

        return false;
    }

    private Player resolveTarget(CommandSender sender, String playerName) {
        if (playerName != null) {
            Player target = Bukkit.getPlayerExact(playerName);
            if (target == null) {
                sender.sendMessage(Component.text("Игрок не найден.", NamedTextColor.RED));
            }
            return target;
        }

        if (sender instanceof Player player) return player;

        sender.sendMessage(Component.text("Укажите игрока.", NamedTextColor.RED));
        return null;
    }

    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        for (ItemStack left : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("status", "give", "book", "reload"), args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("book")) {
            return filter(List.of("speed", "jump", "dash", "endurance", "stability"), args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("book")) {
            return filter(List.of("1", "2", "3"), args[2]);
        }

        if ((args.length == 2 && args[0].equalsIgnoreCase("give"))
                || (args.length == 4 && args[0].equalsIgnoreCase("book"))) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) names.add(player.getName());
            return filter(names, args[args.length - 1]);
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
