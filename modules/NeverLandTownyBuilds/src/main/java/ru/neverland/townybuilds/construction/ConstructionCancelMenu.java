package ru.neverland.townybuilds.construction;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.*;
import ru.neverland.townybuilds.integration.TownyHook;
import ru.neverland.townybuilds.util.ColorUtil;
import java.util.*;

public final class ConstructionCancelMenu implements Listener {
    private final JavaPlugin plugin; private final ConstructionRefunds refunds; private final TownyHook towny;
    public ConstructionCancelMenu(JavaPlugin plugin,ConstructionRefunds refunds,TownyHook towny){this.plugin=plugin;this.refunds=refunds;this.towny=towny;}
    private record View(UUID player,UUID town,String project,UUID receipt,boolean admin,int fee) implements InventoryHolder {
        public Inventory getInventory(){throw new UnsupportedOperationException();}
    }
    public void open(Player player,String project) {
        try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
            try {
                var town=towny.town(player);boolean admin=player.isOp()||player.hasPermission("neverlandtownybuilds.admin.cancel");
                if(town==null||!admin&&!refunds.allowed(player,town))throw new IllegalStateException("Отменить стройку может мэр или уполномоченный строитель");
                var lines=refunds.quote(town.getUUID(),project,admin);
                var receipt=refunds.receipt(town.getUUID(),project);
                var inventory=MenuStyle.inventory(plugin,new View(player.getUniqueId(),town.getUUID(),project,receipt.id(),admin,admin?0:refunds.fee()),27,ColorUtil.component("Отмена строительства"));
                inventory.setItem(13,item(Material.BOOK,"&eЧто вернётся городу",lines));
                inventory.setItem(11,item(Material.LIME_CONCRETE,"&aПодтвердить отмену",List.of("Незавершённый этап будет разобран.","Возврат поступит в казну и городской склад.")));
                inventory.setItem(15,item(Material.RED_CONCRETE,"&fПродолжить строительство",List.of("Закрыть это окно")));
                player.openInventory(inventory);
            }catch(Exception ex){player.sendMessage("§e"+ex.getMessage());}
        }
    }
    private ItemStack item(Material material,String title,List<String> lore) {
        var item=new ItemStack(material);var meta=item.getItemMeta();meta.displayName(ColorUtil.component(title));
        meta.lore(MenuStyle.loreComponents(lore.stream().map(s->ColorUtil.component("&7"+s)).toList()));item.setItemMeta(meta);return item;
    }
    @EventHandler public void click(InventoryClickEvent event) {
        if(!(event.getView().getTopInventory().getHolder(false) instanceof View view))return;
        event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player player)||!player.getUniqueId().equals(view.player()))return;
        if(event.getRawSlot()==15){player.closeInventory();return;}
        if(event.getRawSlot()!=11)return;
        player.closeInventory();
        SuiteScheduler.player(plugin,player,current->{
            try {
                if(view.admin()&&!current.isOp()&&!current.hasPermission("neverlandtownybuilds.admin.cancel"))throw new IllegalStateException("Право на полный возврат больше недоступно");
                if(!view.admin()&&view.fee()!=refunds.fee())throw new IllegalStateException("Комиссия изменилась. Откройте подтверждение заново");
                current.sendMessage("§a"+refunds.cancel(view.town(),view.project(),view.receipt(),view.admin(),current,current.getUniqueId().toString()));
            }catch(Exception ex){current.sendMessage("§e"+ex.getMessage());}
        });
    }
    @EventHandler public void drag(InventoryDragEvent event) {
        if(event.getView().getTopInventory().getHolder(false) instanceof View)event.setCancelled(true);
    }
}
