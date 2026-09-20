package ru.neverland.townyachievements.service;

import org.bukkit.*;
import org.bukkit.block.banner.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import ru.neverland.core.*;
import java.util.*;

/** A repeatable decorative pattern licence; exchanges one blank banner for one decorated banner. */
public final class BannerRewards {
    private BannerRewards() { }
    public static void apply(Plugin plugin, AchievementService service, Player player, String id) {
        var progress = service.reward(player, id); String style = progress.definition().reward().banner();
        if (style.isEmpty()) throw new IllegalArgumentException("У достижения нет знамени");
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() != Material.WHITE_BANNER || held.getAmount() != 1 || held.hasItemMeta())
            throw new IllegalArgumentException("Возьмите в основную руку ровно одно чистое белое знамя");
        var item = new ItemStack(Material.WHITE_BANNER); var meta = (BannerMeta) item.getItemMeta();
        DyeColor color = switch (style) {
            case "wealth" -> DyeColor.YELLOW; case "city" -> DyeColor.LIME; case "guard" -> DyeColor.RED;
            case "architect" -> DyeColor.LIGHT_BLUE; default -> DyeColor.PURPLE;
        };
        PatternType symbol = switch (style) {
            case "wealth", "wonder" -> PatternType.FLOWER; case "city" -> PatternType.RHOMBUS;
            case "guard" -> PatternType.CROSS; default -> PatternType.BRICKS;
        };
        meta.setPatterns(List.of(new Pattern(color, PatternType.BASE), new Pattern(DyeColor.WHITE, symbol), new Pattern(DyeColor.BLACK, PatternType.BORDER)));
        meta.setDisplayName("§6Знамя: " + progress.definition().name());
        var town = service.requireTown(player);
        meta.setLore(List.of("§7Город: " + town.getName(), "§7Памятное городское знамя", "§8Копии разрешены; бонусов предмет не даёт"));
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "achievement"), PersistentDataType.STRING, id);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "town"), PersistentDataType.STRING, town.getUUID().toString());
        item.setItemMeta(meta); player.getInventory().setItemInMainHand(item);
        AuditTrail.record(plugin, "banner", UUID.randomUUID().toString(), "ACHIEVEMENT_REWARD", "COMPLETED", AuditTrail.player(player.getUniqueId()),
                AuditTrail.town(town.getUUID()), AuditTrail.player(player.getUniqueId()), id, 1, "", "Нанесён памятный узор на белое знамя");
        player.sendMessage("§aГородской узор нанесён на знамя.");
    }
}
