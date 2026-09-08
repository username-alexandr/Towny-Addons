package ru.neverland.mintcamps.service;
import ru.neverland.localization.MaterialNameConfig;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintcamps.model.Camp;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FuelService {
    public enum Status { ADDED, EMPTY_HAND, INVALID, FULL, LIFETIME_LIMIT }

    public record Result(Status status, int items, long addedMillis, long remainingMillis) { }

    private final JavaPlugin plugin;
    private final List<FuelEntry> entries = new ArrayList<>();

    public FuelService(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        entries.clear();
        for (Map<?, ?> map : plugin.getConfig().getMapList("settings.fuel.entries")) {
            Material material = material(map.get("material"));
            Tag<Material> tag = tag(map.get("tag"));
            int seconds = integer(map.get("seconds"), 0);
            Material returned = material(map.get("return-material"));
            if ((material != null || tag != null) && seconds > 0) {
                entries.add(new FuelEntry(material, tag, seconds, returned));
            }
        }
    }

    public Result addFromMainHand(Player player, Camp camp) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir() || held.getAmount() <= 0) return result(Status.EMPTY_HAND, camp, 0, 0);
        FuelEntry entry = entries.stream().filter(value -> value.matches(held.getType())).findFirst().orElse(null);
        if (entry == null) return result(Status.INVALID, camp, 0, 0);

        long now = System.currentTimeMillis();
        long lifeEnd = camp.createdAt() + maximumLifeMillis();
        if (now >= lifeEnd) return result(Status.LIFETIME_LIMIT, camp, 0, 0);
        long levelEnd = now + burnCapMillis(camp.level());
        long maximumEnd = Math.min(lifeEnd, levelEnd);
        long start = Math.max(now, camp.burnUntil());
        if (start >= maximumEnd) return result(lifeEnd <= levelEnd ? Status.LIFETIME_LIMIT : Status.FULL, camp, 0, 0);

        long perItem = entry.seconds() * 1000L;
        int required = (int) Math.ceil((maximumEnd - start) / (double) perItem);
        int consumed = Math.min(held.getAmount(), Math.max(1, required));
        long added = Math.min(maximumEnd - start, consumed * perItem);
        camp.burnUntil(start + added);

        if (consumed >= held.getAmount()) player.getInventory().setItemInMainHand(null);
        else held.setAmount(held.getAmount() - consumed);
        if (entry.returned() != null) {
            ItemStack returns = new ItemStack(entry.returned(), consumed);
            for (ItemStack leftover : player.getInventory().addItem(returns).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
        return result(Status.ADDED, camp, consumed, added);
    }

    public boolean isFuel(ItemStack item) {
        return item != null && !item.getType().isAir() && entries.stream().anyMatch(value -> value.matches(item.getType()));
    }

    public long burnCapMillis(int level) {
        return Math.max(1, plugin.getConfig().getLong("settings.lifetime.burn-cap-seconds." + level, 86400L)) * 1000L;
    }

    public long maximumLifeMillis() {
        return Math.max(1, plugin.getConfig().getLong("settings.lifetime.maximum-seconds", 259200L)) * 1000L;
    }

    private Result result(Status status, Camp camp, int items, long added) {
        return new Result(status, items, added, camp.remainingBurnMillis(System.currentTimeMillis()));
    }

    private Material material(Object value) {
        return value == null ? null : MaterialNameConfig.matchMaterial(String.valueOf(value).toUpperCase(Locale.ROOT));
    }

    private int integer(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    @SuppressWarnings("deprecation")
    private Tag<Material> tag(Object value) {
        if (value == null) return null;
        return switch (String.valueOf(value).toUpperCase(Locale.ROOT)) {
            case "PLANKS" -> Tag.PLANKS;
            case "LOGS" -> Tag.LOGS;
            case "LOGS_THAT_BURN" -> Tag.LOGS_THAT_BURN;
            default -> null;
        };
    }

    private record FuelEntry(Material material, Tag<Material> tag, int seconds, Material returned) {
        boolean matches(Material type) {
            return type == material || tag != null && tag.isTagged(type);
        }
    }
}
