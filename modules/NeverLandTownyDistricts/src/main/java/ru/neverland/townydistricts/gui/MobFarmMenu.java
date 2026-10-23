package ru.neverland.townydistricts.gui;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Coord;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.*;
import ru.neverland.townydistricts.api.MobFarmPlot;
import ru.neverland.townydistricts.service.MobFarmService;
import java.util.*;

public final class MobFarmMenu implements CommandExecutor,TabCompleter,Listener {
    private static final class View implements InventoryHolder {
        final UUID player;final Map<Integer,Runnable> actions=new HashMap<>();Inventory inventory;
        View(Player p){player=p.getUniqueId();}public Inventory getInventory(){return inventory;}
    }
    private final JavaPlugin plugin;private final MobFarmService farms;
    public MobFarmMenu(JavaPlugin plugin,MobFarmService farms){this.plugin=plugin;this.farms=farms;}
    private void access(Player p){farms.requireEnabled();if(!p.hasPermission("neverlandtownydistricts.use")&&!MobFarmService.admin(p))throw new IllegalStateException("Нет доступа к карте города");}
    private View view(Player p,String title,int size){var v=new View(p);v.inventory=MenuStyle.inventory(plugin,v,size,Component.text(title));return v;}
    private void button(View v,int slot,Material icon,String name,List<String> lore,Runnable action){
        var stack=new ItemStack(icon);var meta=stack.getItemMeta();meta.displayName(Component.text(name));meta.lore(MenuStyle.loreComponents(lore.stream().map(Component::text).toList()));stack.setItemMeta(meta);
        v.inventory.setItem(slot,stack);if(action!=null)v.actions.put(slot,action);
    }
    public void open(Player p,boolean list,int requestedPage){synchronized(SuiteState.lock()){
        access(p);var town=farms.viewTown(p);if(town==null)throw new IllegalStateException("Вступите в город, чтобы открыть его карту");
        var v=view(p,list?"Город • зоны ферм":"Город • защита от мобов",54);
        if(list){
            var zones=farms.plots(town.getUUID()).stream().filter(i->i.mobsAllowed()||i.protectionDisabled()).toList();
            int pages=Math.max(1,(zones.size()+44)/45),page=Math.max(0,Math.min(pages-1,requestedPage));
            for(int i=page*45;i<Math.min(zones.size(),(page+1)*45);i++)plot(v,i%45,p,zones.get(i));
            if(zones.isEmpty())button(v,22,Material.SHIELD,"Зон с разрешёнными мобами нет",List.of("Выберите нужный участок на карте."),null);
            if(page>0)button(v,45,Material.ARROW,"Назад",List.of(),()->open(p,true,page-1));
            if(page+1<pages)button(v,53,Material.ARROW,"Далее",List.of(),()->open(p,true,page+1));
            button(v,46,Material.MAP,"Карта рядом со мной",List.of("Страница списка: "+(page+1)+" / "+pages),()->open(p,false,0));
        }else{
            var location=p.getLocation();int size=Coord.getCellSize(),x=Math.floorDiv(location.getBlockX(),size),z=Math.floorDiv(location.getBlockZ(),size);
            for(int dz=-2;dz<=2;dz++)for(int dx=-4;dx<=4;dx++){
                int slot=(dz+2)*9+dx+4;var info=farms.snapshot(farms.block(p.getWorld().getUID(),x+dx,z+dz)).orElse(null);
                if(info!=null&&info.townId().equals(town.getUUID()))plot(v,slot,p,info);
                else button(v,slot,Material.GRAY_STAINED_GLASS_PANE,dx==0&&dz==0?"Вы здесь • за пределами города":"Не принадлежит этому городу",List.of("Участок "+(x+dx)+", "+(z+dz)),null);
            }
            button(v,46,Material.BOOK,"Все зоны с разрешёнными мобами",List.of("Список участков во всех мирах"),()->open(p,true,0));
            button(v,53,Material.SUNFLOWER,"Обновить карту",List.of("Север сверху, восток справа"),()->open(p,false,0));
        }
        button(v,47,Material.GLOWSTONE_DUST,"Показать границы рядом",List.of("Оранжевые — мобы разрешены","Зелёные — защита включена","Серые — правило задаёт мир"),()->{p.closeInventory();farms.show(p);});
        button(v,49,Material.COMPASS,"Мой текущий участок",List.of("Посмотреть настройку и изменить её"),()->{
            var b=TownyAPI.getInstance().getTownBlock(p.getLocation());if(b==null)throw new IllegalStateException("Здесь нет городского участка");confirm(p,farms.prepare(p,b,!b.getPermissions().mobs));});
        button(v,50,Material.PAPER,"Как читать карту",List.of("Оранжевый — мобы разрешены","Зелёный — защита от мобов включена","Управление: мэр, помощник, администратор","Настройка не включает взрывы и PvP"),null);
        p.openInventory(v.inventory);
    }}
    private void plot(View v,int slot,Player p,MobFarmPlot info){
        var location=p.getLocation();boolean here=info.worldId().equals(p.getWorld().getUID())&&Math.floorDiv(location.getBlockX(),info.blockSize())==info.plotX()&&Math.floorDiv(location.getBlockZ(),info.blockSize())==info.plotZ();
        var lore=new ArrayList<String>();lore.add(info.description());lore.add("Мир: "+info.worldName());
        lore.add("X: "+info.minBlockX()+"…"+info.maxBlockX()+"; Z: "+info.minBlockZ()+"…"+info.maxBlockZ());
        lore.add("Размер: "+info.blockSize()+" × "+info.blockSize()+" блоков");
        if(info.protectionDisabled())lore.add("Мобы могут появляться и атаковать игроков");
        var town=TownyAPI.getInstance().getTown(info.townId());boolean manage=farms.manager(p,town);
        lore.add(manage?"Нажмите, чтобы изменить с подтверждением":"Настройку меняет руководство города");
        button(v,slot,info.status().equals("PROTECTED")?Material.LIME_STAINED_GLASS_PANE:info.protectionDisabled()?Material.ORANGE_STAINED_GLASS_PANE:Material.LIGHT_GRAY_STAINED_GLASS_PANE,
            (here?"Вы здесь • ":"")+"Участок "+info.plotX()+", "+info.plotZ(),lore,manage?()->{
                var b=farms.block(info.worldId(),info.plotX(),info.plotZ());
                if(b==null||b.getTownOrNull()==null||!info.townId().equals(b.getTownOrNull().getUUID()))throw new IllegalStateException("Владелец участка изменился");
                confirm(p,farms.prepare(p,b,!b.getPermissions().mobs));}:null);
    }
    private void confirm(Player p,MobFarmService.Confirmation c){
        var v=view(p,c.allow()?"Разрешить мобов здесь?":"Включить защиту от мобов?",27);
        button(v,13,Material.BOOK,"Только выбранный участок",List.of("Участок: "+c.x()+", "+c.z(),"Размер: "+c.size()+" × "+c.size()+" блоков",
            c.allow()?"Здесь смогут появляться враждебные мобы.":"Towny снова сможет удалять здесь мобов.",
            c.allow()?"Огородите ферму: игроки могут пострадать.":"Работа фермы может остановиться.","Взрывы, огонь, PvP и права строительства сохраняются."),null);
        button(v,11,Material.LIME_CONCRETE,"Подтвердить",List.of(),()->{farms.apply(p,c);open(p,false,0);});
        button(v,15,Material.RED_CONCRETE,"Оставить как есть",List.of(),()->open(p,false,0));p.openInventory(v.inventory);
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){synchronized(SuiteState.lock()){
        try{
            if(!(sender instanceof Player p))throw new IllegalStateException("Откройте карту в игре: /t farm");access(p);
            String action=args.length==0?"map":args[0].toLowerCase(Locale.ROOT);
            switch(action){
                case "map"->open(p,false,0);
                case "list"->open(p,true,args.length>1?Integer.parseInt(args[1])-1:0);
                case "show"->{farms.show(p);}
                case "status"->{var l=p.getLocation();var info=farms.at(p.getWorld().getUID(),l.getBlockX(),l.getBlockZ()).orElseThrow(()->new IllegalStateException("Здесь нет городского участка"));p.sendMessage("§e"+info.description()+" §7(участок "+info.plotX()+", "+info.plotZ()+")");}
                case "allow","protect"->{var b=TownyAPI.getInstance().getTownBlock(p.getLocation());confirm(p,farms.prepare(p,b,action.equals("allow")));}
                default->throw new IllegalStateException("/t farm — карта; allow — разрешить мобов; protect — включить защиту; list — список; show — границы; status — текущее правило");
            }
        }catch(RuntimeException ex){sender.sendMessage("§e"+ex.getMessage());}
        return true;
    }}
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){return args.length==1?List.of("map","list","show","status","allow","protect").stream().filter(s->s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList():List.of();}
    @EventHandler public void click(InventoryClickEvent e){synchronized(SuiteState.lock()){
        if(!(e.getView().getTopInventory().getHolder(false) instanceof View v))return;e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player p)||!v.player.equals(p.getUniqueId()))return;
        var action=v.actions.get(e.getRawSlot());if(action==null)return;
        SuiteScheduler.player(plugin,p,current->{if(current.getOpenInventory().getTopInventory().getHolder(false)!=v)return;
            try{access(current);action.run();}catch(RuntimeException ex){current.closeInventory();current.sendMessage("§e"+ex.getMessage());}});
    }}
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder(false) instanceof View)e.setCancelled(true);}
    public void stop(){SuiteScheduler.cleanupPlayers(plugin,p->{if(p.getOpenInventory().getTopInventory().getHolder(false) instanceof View)p.closeInventory();});}
}
