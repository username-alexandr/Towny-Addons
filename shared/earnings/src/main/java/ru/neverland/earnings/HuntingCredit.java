package ru.neverland.earnings;

import java.util.*;
import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import ru.neverland.core.SuiteScheduler;

/** Resolve the finishing attack on the victim's region; never inspect a remote shooter's state. */
public final class HuntingCredit {
    private HuntingCredit() {}
    private static final Set<Material> ALLOWED = weapons();
    private static Set<Material> weapons() {
        var result = EnumSet.of(Material.AIR, Material.BOW, Material.CROSSBOW, Material.TRIDENT,
                Material.MACE, Material.FISHING_ROD, Material.SHEARS, Material.BRUSH,
                Material.FLINT_AND_STEEL, Material.CARROT_ON_A_STICK, Material.WARPED_FUNGUS_ON_A_STICK,
                Material.SNOWBALL, Material.EGG, Material.BLUE_EGG, Material.BROWN_EGG,
                Material.WIND_CHARGE, Material.SPLASH_POTION);
        for (String tier : List.of("WOODEN", "STONE", "COPPER", "IRON", "GOLDEN", "DIAMOND", "NETHERITE"))
            for (String tool : List.of("SWORD", "SPEAR", "AXE", "PICKAXE", "SHOVEL", "HOE"))
                result.add(Material.valueOf(tier+"_"+tool));
        return Collections.unmodifiableSet(result);
    }
    public static Set<Material> allowedItems() { return ALLOWED; }
    public static boolean allowed(Material material) { return material != null && ALLOWED.contains(material); }
    private static Material type(ItemStack item) { return item == null ? Material.AIR : item.getType(); }

    public static Player killer(EntityDeathEvent event) { return killer(event, SuiteScheduler::owns); }
    static Player killer(EntityDeathEvent event, Predicate<Entity> owns) {
        if (event.isCancelled() || !(event.getEntity().getLastDamageCause() instanceof EntityDamageByEntityEvent hit)
                || hit.isCancelled() || hit.getFinalDamage() <= 0) return null;
        var source = event.getDamageSource();
        if (!(source.getCausingEntity() instanceof Player player)) return null;
        Entity direct = source.getDirectEntity();
        // Do not reuse a previous player's hit when the actual death was fire, falling, thorns or a trap.
        if (direct == null || direct != hit.getDamager()
                || source.getCausingEntity() != hit.getDamageSource().getCausingEntity()
                || !Objects.equals(source.getDamageType(), hit.getDamageSource().getDamageType())) return null;
        var cause = hit.getCause();
        if (direct == player) {
            if (cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                    && cause != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) return null;
            return owns.test(player) && allowed(type(player.getInventory().getItemInMainHand())) ? player : null;
        }
        if (!(direct instanceof Projectile projectile) || !owns.test(projectile)
                || projectile.getShooter() != player) return null;
        if (projectile instanceof AbstractArrow arrow && cause == EntityDamageEvent.DamageCause.PROJECTILE) {
            // Use the launched weapon, not what the shooter is holding when the arrow lands.
            Material weapon = arrow instanceof Trident ? Material.TRIDENT : type(arrow.getWeapon());
            return Set.of(Material.BOW, Material.CROSSBOW, Material.TRIDENT).contains(weapon) ? player : null;
        }
        if (projectile instanceof Firework firework && firework.isShotAtAngle()
                && (cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION || cause == EntityDamageEvent.DamageCause.PROJECTILE)) return player;
        if (cause == EntityDamageEvent.DamageCause.PROJECTILE
                && (projectile instanceof Snowball || projectile instanceof Egg || projectile instanceof WindCharge)) return player;
        if (projectile instanceof ThrownPotion potion && cause == EntityDamageEvent.DamageCause.MAGIC
                && type(potion.getItem()) == Material.SPLASH_POTION) return player;
        return null;
    }
}
