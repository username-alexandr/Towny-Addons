package ru.neverland.morstownstick.gui;

import com.palmergames.bukkit.towny.object.Town;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.MenuStyle;
import ru.neverland.core.SuiteScheduler;
import ru.neverland.core.SuiteState;
import ru.neverland.morstownstick.integration.TownyFacade;
import ru.neverland.morstownstick.model.CellKey;
import ru.neverland.morstownstick.plan.ExpansionPlan;
import ru.neverland.morstownstick.service.BfsPlanner;
import ru.neverland.morstownstick.service.ClaimService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Local 9x5 plot map. Selection is a proposal; only ClaimService and Towny can claim or charge. */
public final class ClaimMapMenu implements Listener {
    private static final class View implements InventoryHolder {
        final UUID viewer, town;
        final String world;
        final Map<Integer, Runnable> actions = new HashMap<>();
        Inventory inventory;
        View(UUID viewer, UUID town, String world) { this.viewer=viewer; this.town=town; this.world=world; }
        @Override public Inventory getInventory() { return inventory; }
    }
    private final JavaPlugin plugin;
    private final TownyFacade towny;
    private final ClaimService claims;

    public ClaimMapMenu(JavaPlugin plugin,TownyFacade towny,ClaimService claims){this.plugin=plugin;this.towny=towny;this.claims=claims;}

    private Town access(Player player){
        if(!player.hasPermission("morstownstick.claim"))throw new IllegalStateException("У вас нет права присоединять участки");
        Town town=towny.town(player);
        if(town==null||town.getMayor()==null||!town.getMayor().getUUID().equals(player.getUniqueId()))throw new IllegalStateException("Карта присоединения доступна только мэру");
        if(!towny.isOwnedBy(towny.cell(player.getLocation().getBlock()),town))throw new IllegalStateException("Для присоединения земли находиться нужно на территории своего города");
        return town;
    }
    private View view(Player player,Town town,String title){
        var v=new View(player.getUniqueId(),town.getUUID(),player.getWorld().getName());
        v.inventory=MenuStyle.inventory(plugin,v,54,Component.text(title));
        return v;
    }
    private void button(View v,int slot,Material icon,String name,List<String> lore,Runnable action){
        var item=new ItemStack(icon);var meta=item.getItemMeta();
        meta.displayName(Component.text(name));meta.lore(MenuStyle.loreComponents(lore.stream().map(Component::text).toList()));
        item.setItemMeta(meta);v.inventory.setItem(slot,item);
        if(action!=null)v.actions.put(slot,action);
    }
    public void open(Player player){try(var guard=SuiteState.guard()){
        Town town=access(player);CellKey current=towny.cell(player.getLocation().getBlock());
        render(player,town,current.x(),current.z(),Set.of());
    }}
    private void render(Player player,Town town,int centerX,int centerZ,Set<CellKey> selected){
        String world=player.getWorld().getName();var owned=towny.ownedCells(town);
        var v=view(player,town,"Город • карта участков");
        for(int dz=-2;dz<=2;dz++)for(int dx=-4;dx<=4;dx++){
            int slot=(dz+2)*9+dx+4;var cell=new CellKey(world,centerX+dx,centerZ+dz);
            boolean own=owned.contains(cell),chosen=selected.contains(cell);
            boolean available=!own&&towny.isClaimableWorld(cell)&&towny.isWilderness(cell);
            boolean edge=cell.neighbors().stream().anyMatch(owned::contains);
            Material icon=chosen?Material.YELLOW_STAINED_GLASS_PANE:own?Material.LIME_STAINED_GLASS_PANE:
                available?(edge?Material.LIGHT_BLUE_STAINED_GLASS_PANE:Material.WHITE_STAINED_GLASS_PANE):Material.RED_STAINED_GLASS_PANE;
            String status=chosen?"Выбран":own?"Ваш город":available?(edge?"У границы":"Свободен"):"Недоступен";
            var lore=new ArrayList<>(List.of("Участок "+cell.x()+", "+cell.z(),status));
            if(cell.x()==Math.floorDiv(player.getLocation().getBlockX(),towny.cellSize())&&cell.z()==Math.floorDiv(player.getLocation().getBlockZ(),towny.cellSize()))lore.add("Вы здесь");
            if(available||chosen)lore.add("Нажмите для выбора или отмены");
            button(v,slot,icon,status,lore,available||chosen?()->toggle(player,centerX,centerZ,selected,cell):null);
        }
        button(v,45,Material.ARROW,"На запад",List.of("Сместить карту на 5 участков"),()->render(player,town,centerX-5,centerZ,selected));
        button(v,46,Material.ARROW,"На север",List.of("Сместить карту на 3 участка"),()->render(player,town,centerX,centerZ-3,selected));
        button(v,47,Material.ARROW,"На юг",List.of("Сместить карту на 3 участка"),()->render(player,town,centerX,centerZ+3,selected));
        button(v,48,Material.ARROW,"На восток",List.of("Сместить карту на 5 участков"),()->render(player,town,centerX+5,centerZ,selected));
        button(v,49,Material.COMPASS,"К моему участку",List.of(),()->{var here=towny.cell(player.getLocation().getBlock());render(player,town,here.x(),here.z(),selected);});
        button(v,50,Material.BOOK,"Как читать карту",List.of("Север сверху, восток справа","Зелёный — город; голубой — свободный у границы","Белый — свободный; жёлтый — выбран","Участки должны соединяться с городом сторонами"),null);
        button(v,51,Material.BARRIER,"Очистить выбор",List.of("Выбрано: "+selected.size()),()->render(player,town,centerX,centerZ,Set.of()));
        var open=claims.openPlan(player);
        if(open.isPresent())button(v,52,Material.WRITABLE_BOOK,"Открыть текущий план",List.of("У города уже есть план расширения","Завершите его прежде чем создавать новый"),()->detail(player,open.get().id()));
        else button(v,52,Material.EMERALD_BLOCK,"Создать план · "+selected.size(),List.of("Цена и условия будут показаны перед оплатой","Сейчас деньги не списываются"),selected.isEmpty()?null:()->create(player,selected));
        button(v,53,Material.OAK_DOOR,"Закрыть",List.of(),player::closeInventory);
        player.openInventory(v.inventory);
    }
    private void toggle(Player player,int x,int z,Set<CellKey> selected,CellKey cell){
        Town town=access(player);var changed=new LinkedHashSet<>(selected);
        if(!changed.remove(cell)){
            int limit=Math.max(1,Math.min(64,plugin.getConfig().getInt("plans.max-chunks",64)));
            int selectionLimit=plugin.getConfig().getInt("selection.max-chunks",-1);
            if(changed.size()>=limit||(selectionLimit>=0&&changed.size()>=selectionLimit))throw new IllegalStateException("Достигнут предел участков в одном плане");
            if(!towny.isClaimableWorld(cell)||!towny.isWilderness(cell))throw new IllegalStateException("Этот участок уже занят или недоступен");
            changed.add(cell);
        }
        if(!changed.isEmpty()&&!BfsPlanner.plan(changed,towny.ownedCells(town)).unreachable().isEmpty())throw new IllegalStateException("Выбранные участки должны соединяться с городом сторонами");
        render(player,town,x,z,Set.copyOf(changed));
    }
    private void create(Player player,Set<CellKey> selected){
        access(player);
        try{result(player,claims.createFromMap(player,selected));}
        catch(Exception error){throw new IllegalStateException(error.getMessage(),error);}
    }
    private void result(Player player,CompletionStage<ExpansionPlan> operation){
        player.closeInventory();
        operation.whenComplete((plan,error)->SuiteScheduler.reply(plugin,player,()->{
            if(error!=null){player.sendMessage("§cПлан не сохранён: "+cause(error).getMessage());return;}
            try{detail(player,plan.id());}catch(RuntimeException ex){player.sendMessage("§c"+ex.getMessage());}
        }));
    }
    private static Throwable cause(Throwable error){while(error.getCause()!=null)error=error.getCause();return error;}
    private void detail(Player player,UUID id){
        Town town=access(player);ExpansionPlan plan=claims.view(player,id);
        var v=view(player,town,"Город • план расширения");
        button(v,13,Material.FILLED_MAP,"План · "+plan.cells().size()+" участков",List.of("Статус: "+status(plan.status()),"Стоимость: "+plan.estimate()+(plan.economy()?" монет":" (экономика выключена)"),plan.economy()?"Оплату выполнит Towny из казны":"Экономика Towny отключена","Списание только после подтверждения","ID: "+plan.id(),"Версия: "+plan.revision()),null);
        for(int i=0;i<Math.min(27,plan.cells().size());i++){
            var cell=plan.cells().get(i);button(v,18+i,Material.YELLOW_STAINED_GLASS_PANE,"Участок "+cell.x()+", "+cell.z(),List.of(cell.world()),null);
        }
        if(plan.status()==ExpansionPlan.Status.DRAFT)button(v,48,Material.LIME_CONCRETE,"Согласовать план",List.of("Towny ещё раз проверит землю, лимит и цену","Платёж не производится"),()->approve(player,id,plan.revision()));
        if(plan.status()==ExpansionPlan.Status.APPROVED)button(v,48,Material.GOLD_BLOCK,"Перейти к оплате и захвату",List.of("Показать последнее подтверждение"),()->confirm(player,id,plan.revision()));
        if(plan.status()==ExpansionPlan.Status.DRAFT||plan.status()==ExpansionPlan.Status.APPROVED)
            button(v,46,Material.BARRIER,"Отменить план",List.of("Присоединение ещё не началось","Оплата не запрашивалась"),()->result(player,claims.cancel(player,id,plan.revision())));
        button(v,50,Material.OAK_DOOR,"Закрыть",List.of(),player::closeInventory);
        button(v,51,Material.MAP,"Вернуться к карте",List.of(),()->open(player));
        player.openInventory(v.inventory);
    }
    private String status(ExpansionPlan.Status status){return switch(status){
        case DRAFT->"Ожидает согласования";case APPROVED->"Согласован, ждёт подтверждения";
        case INTENT->"Присоединение выполняется или требует сверки";case OBSERVED->"Участки присоединены; проверьте платёж";
        case REVIEW->"Нужна сверка администратора";case CANCELLED->"Отменён";case RECONCILED->"Сверка завершена";
    };}
    private void approve(Player player,UUID id,int revision){
        try{result(player,claims.approve(player,id,revision));}
        catch(Exception error){throw new IllegalStateException(error.getMessage(),error);}
    }
    private void confirm(Player player,UUID id,int revision){
        Town town=access(player);var plan=claims.view(player,id);
        if(plan.status()!=ExpansionPlan.Status.APPROVED||plan.revision()!=revision)throw new IllegalStateException("План изменился. Откройте его снова");
        var v=view(player,town,"Подтвердить присоединение?");
        button(v,13,Material.FILLED_MAP,"Проверка перед оплатой",List.of("Участков: "+plan.cells().size(),"Смета: "+plan.estimate()+" монет из казны","Towny повторно проверит цену, права и границы","Если что-то изменилось, захват не начнётся"),null);
        button(v,29,Material.LIME_CONCRETE,"Подтвердить захват",List.of("Списание через Towny и присоединение земли"),()->execute(player,id,revision));
        button(v,33,Material.RED_CONCRETE,"Вернуться",List.of("Без списания"),()->detail(player,id));
        player.openInventory(v.inventory);
    }
    private void execute(Player player,UUID id,int revision){
        access(player);
        try{result(player,claims.execute(player,id,revision));}
        catch(Exception error){throw new IllegalStateException(error.getMessage(),error);}
    }
    @EventHandler public void click(InventoryClickEvent event){try(var guard=SuiteState.guard()){
        if(!(event.getView().getTopInventory().getHolder() instanceof View v))return;
        event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player player)||!player.getUniqueId().equals(v.viewer)||event.getClickedInventory()!=v.inventory||event.getClick()!=org.bukkit.event.inventory.ClickType.LEFT)return;
        var action=v.actions.get(event.getSlot());if(action==null)return;
        SuiteScheduler.player(plugin,player,current->{
            if(current.getOpenInventory().getTopInventory()!=v.inventory)return;
            try{if(!access(current).getUUID().equals(v.town)||!current.getWorld().getName().equals(v.world))throw new IllegalStateException("Мир или город изменился. Откройте карту заново");
                action.run();
            }catch(RuntimeException ex){current.closeInventory();current.sendMessage("§c"+ex.getMessage());}
        });
    }}
    @EventHandler public void drag(InventoryDragEvent event){
        if(event.getView().getTopInventory().getHolder() instanceof View)event.setCancelled(true);
    }
    public void stop(){SuiteScheduler.cleanupPlayers(plugin,player->{if(player.getOpenInventory().getTopInventory().getHolder() instanceof View)player.closeInventory();});}
}
