package ru.neverland.townycitizens;

import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import java.time.Instant;
import java.util.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import ru.neverland.core.MenuStyle;
import ru.neverland.townycitizens.model.*;

public final class CitizensMenus implements Listener {
    private record Holder(UUID viewer, Inventory back) implements InventoryHolder { public Inventory getInventory() { return null; } }
    private final NeverLandTownyCitizens plugin; private final CitizensService service;
    public CitizensMenus(NeverLandTownyCitizens plugin, CitizensService service) { this.plugin = plugin; this.service = service; }
    public void open(Player viewer, Town town, Resident resident) {
        var previous = viewer.getOpenInventory().getTopInventory();
        var back = previous.getHolder() instanceof Holder old ? old.back() : MenuStyle.previousMenu(viewer);
        var menu = MenuStyle.inventory(plugin, new Holder(viewer.getUniqueId(), back), 45, "Гражданство • " + town.getName());
        UUID t = town.getUUID(), r = resident.getUUID(); var status = service.effective(t, r);
        long expiry = service.expiresAt(t, r);
        menu.setItem(4, item(Material.WRITABLE_BOOK, "&f" + resident.getName(), List.of("&7Город: &f" + town.getName(),
                "&7Статус: &e" + status.title(), "&7Срок: &f" + (expiry == 0 ? "Бессрочно" : Instant.ofEpochMilli(expiry).toString()),
                "", "&7Множитель городского налога: &f×" + service.taxMultiplier(t, r))));
        int slot = 10;
        for (var candidate : CitizenshipStatus.values()) {
            var policy = service.settings().policies().get(candidate);
            menu.setItem(slot++, item(switch (candidate) { case CITIZEN -> Material.EMERALD; case TEMPORARY -> Material.CLOCK; case FOREIGNER -> Material.COMPASS; case HONORARY -> Material.NETHER_STAR; },
                    (candidate == status ? "&a● " : "&f") + candidate.title(), List.of("&7Налог: &f×" + policy.tax(),
                            "&7Голосование: &f" + (policy.rights().contains(CitizenRight.VOTE) ? "для жителей города" : "недоступно"),
                            "&7Назначает мэр или администратор.")));
        }
        slot = 19;
        for (var right : CitizenRight.values()) {
            boolean allowed = service.allows(t, r, right.name());
            menu.setItem(slot++, item(allowed ? Material.LIME_DYE : Material.GRAY_DYE, (allowed ? "&a✓ " : "&c× ") + right.title(),
                    List.of(allowed ? "&7Статус разрешает это действие." : "&7Этот статус не даёт доступа.", "&7Также нужны обычные права Towny.")));
        }
        menu.setItem(36, item(Material.ARROW, back == null ? "&fЗакрыть" : "&fНазад", List.of("&7Вернуться к предыдущему меню.")));
        menu.setItem(40, item(Material.BOOK, "&eКак изменить статус", List.of("&7Обратитесь к мэру города.", "", "&fНажмите для списка команд.")));
        viewer.openInventory(menu);
    }
    private ItemStack item(Material material, String name, List<String> lore) {
        var item = new ItemStack(material); var meta = item.getItemMeta();
        meta.setDisplayName(MenuStyle.nameLegacy(name)); meta.setLore(MenuStyle.loreStrings(lore)); item.setItemMeta(meta); return item;
    }
    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true); if (!(event.getWhoClicked() instanceof Player p) || !p.getUniqueId().equals(holder.viewer())) return;
        if (event.getRawSlot() == 36) org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> MenuStyle.returnTo(p, holder.back()));
        if (event.getRawSlot() == 40) CitizensCommand.help(p);
    }
    @EventHandler public void drag(InventoryDragEvent event) { if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true); }
}
