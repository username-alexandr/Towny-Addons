package ru.neverland.townycouncil;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import java.util.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import ru.neverland.core.MenuStyle;

public final class CouncilMenus implements Listener {
    record Holder(UUID viewer, UUID town, Inventory back) implements InventoryHolder { public Inventory getInventory() { synchronized (ru.neverland.core.SuiteState.lock()) { return null; } } }
    private final NeverLandTownyCouncil plugin; private final CouncilService service;
    private MinisterMenus ministers; public void ministers(MinisterMenus value){ministers=value;}
    public void office(Player p,Town town,MinisterRole role){ministers.open(p,town,role);}
    private AccessMenus access;public void access(AccessMenus value){access=value;}
    private static final int[] SLOTS = {19, 21, 23, 25};
    public CouncilMenus(NeverLandTownyCouncil plugin, CouncilService service) { this.plugin = plugin; this.service = service; }
    static String name(UUID id) { var r = TownyAPI.getInstance().getResident(id); return r == null ? id.toString() : r.getName(); }
    public void open(Player viewer, Town town) { synchronized (ru.neverland.core.SuiteState.lock()) {
        if (!viewer.hasPermission("neverlandtownycouncil.use")) return;
        if(town.equals(service.ownTown(viewer.getUniqueId())) && org.bukkit.Bukkit.getPluginManager().isPluginEnabled("NeverLandTownyGovernance") && !ru.neverland.core.SuiteState.frozen("NeverLandTownyGovernance") && viewer.hasPermission("townygovernance.use")){
            viewer.performCommand("governance council");return;
        }
        var current = viewer.getOpenInventory().getTopInventory();
        var back = current.getHolder() instanceof Holder h ? h.back() : MenuStyle.previousMenu(viewer);
        var menu = MenuStyle.inventory(plugin, new Holder(viewer.getUniqueId(), town.getUUID(), back), 45, "Министры • " + town.getName());
        menu.setItem(4, item(Material.BELL, "&eГородская администрация", List.of("&7Город: &f" + town.getName(),
                "&7Мэр: &f" + (town.getMayor() == null ? "не назначен" : town.getMayor().getName()), "",
                "&7Мэр назначает четырёх министров.", "&7Новый мэр формирует новый состав.",
                service.healthy() ? "&a✓ Управление доступно" : "&c× Права совета приостановлены")));
        int index = 0;
        for (var role : MinisterRole.values()) {
            var a = service.appointment(town.getUUID(), role); var lore = new ArrayList<String>();
            lore.add("&7" + role.description()); lore.add("");
            lore.add(a == null ? "&e○ Должность свободна" : "&7Министр: &f" + name(a.resident()));
            if (a != null) lore.add(service.active(a) ? "&a✓ Полномочия действуют" : "&c× Полномочия приостановлены");
            lore.add("&7Доступ ограничен своим городом."); lore.add(""); lore.add("&fОткрыть должность и её возможности.");
            menu.setItem(SLOTS[index++], item(switch (role) { case ECONOMY -> Material.GOLD_INGOT; case DEFENSE -> Material.SHIELD; case CONSTRUCTION -> Material.BRICKS; case FOREIGN -> Material.WRITABLE_BOOK; }, "&f" + role.title(), lore));
        }
        if(service.mayEditAccess(viewer,town)){menu.setItem(37,item(Material.PLAYER_HEAD,"&eДоступ жителей",List.of("&7Разрешения для каждого жителя")));menu.setItem(38,item(Material.WRITABLE_BOOK,"&eДоступ должностей",List.of("&7Настроить действия министров")));}
        menu.setItem(36, item(Material.ARROW, back == null ? "&fЗакрыть" : "&fНазад", List.of("&7Вернуться к предыдущему меню.")));
        if (org.bukkit.Bukkit.getPluginManager().isPluginEnabled("NeverLandTownyDiplomacy") && viewer.hasPermission("neverlandtownydiplomacy.use"))
            menu.setItem(42, item(Material.COMPASS, "&eДипломатия", List.of("&7Договоры, вассалитет и гарантии.", "&fНажмите — отношения городов.")));
        menu.setItem(40, item(Material.BOOK, "&eПомощь", List.of("&7Назначения, снятие и права.", "&fНажмите — список команд.")));
        menu.setItem(44, item(Material.SUNFLOWER, "&fОбновить", List.of("&7Показать текущий состав.")));
        viewer.openInventory(menu);
    } }
    private static ItemStack item(Material material, String name, List<String> lore) {
        var item = new ItemStack(material); var meta = item.getItemMeta(); meta.setDisplayName(MenuStyle.nameLegacy(name)); meta.setLore(MenuStyle.loreStrings(lore)); item.setItemMeta(meta); return item;
    }
    @EventHandler public void click(InventoryClickEvent e) { synchronized (ru.neverland.core.SuiteState.lock()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyCouncil")){ru.neverland.core.ModuleGate.stopEvent(e);return;}
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || !p.getUniqueId().equals(h.viewer()) || !p.hasPermission("neverlandtownycouncil.use")) return;
        var town = TownyAPI.getInstance().getTown(h.town());
        if (e.getRawSlot() == 36) ru.neverland.core.SuiteScheduler.entity(plugin, p, () -> MenuStyle.returnTo(p, h.back()));
        else if ((e.getRawSlot()==37||e.getRawSlot()==38)&&service.mayEditAccess(p,town)) {boolean roles=e.getRawSlot()==38;ru.neverland.core.SuiteScheduler.entity(plugin,p,()->access.open(p,town,roles,0));}
        else if (e.getRawSlot() == 40) CouncilCommand.help(p);
        else if (e.getRawSlot() == 42 && p.hasPermission("neverlandtownydiplomacy.use")) ru.neverland.core.SuiteScheduler.entity(plugin, p, () -> p.performCommand("townydiplomacy"));
        else if (e.getRawSlot() == 44 && town != null) ru.neverland.core.SuiteScheduler.entity(plugin, p, () -> open(p, town));
        else for (int i = 0; i < SLOTS.length; i++) if (e.getRawSlot() == SLOTS[i]) {
            var role = MinisterRole.values()[i];
            ru.neverland.core.SuiteScheduler.entity(plugin,p,()->ministers.open(p,town,role));
        }
    } }
    @EventHandler public void drag(InventoryDragEvent e) { synchronized (ru.neverland.core.SuiteState.lock()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyCouncil")){ru.neverland.core.ModuleGate.stopEvent(e);return;} if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true); } }
}
