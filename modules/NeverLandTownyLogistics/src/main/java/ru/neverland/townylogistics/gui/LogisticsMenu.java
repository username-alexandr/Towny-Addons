package ru.neverland.townylogistics.gui;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townylogistics.command.LogisticsCommand;
import ru.neverland.townylogistics.integration.BuildsStorage;
import ru.neverland.townylogistics.service.LogisticsService;
import ru.neverland.localization.MaterialLabels;
import java.util.*;
import java.io.IOException;
public final class LogisticsMenu implements Listener {
    private static final class View implements InventoryHolder {final UUID town;final Map<Integer,String[]> actions=new HashMap<>();Inventory inventory;View(UUID town){this.town=town;}public Inventory getInventory(){return inventory;}}
    private final JavaPlugin plugin;private final LogisticsService service;private final LogisticsCommand command;private final MaterialLabels labels=new MaterialLabels();
    public LogisticsMenu(JavaPlugin plugin,LogisticsService service,LogisticsCommand command){this.plugin=plugin;this.service=service;this.command=command;}
    private String label(ItemStack item){if(item==null)return "Все предметы";if(item.hasItemMeta()&&item.getItemMeta().hasDisplayName())return item.getItemMeta().getDisplayName();return labels.name(item.getType().name());}
    private String name(Map<String,BuildsStorage.Depot> depots,String project){var d=depots.get(project);return d==null?"Недоступное здание ("+project+")":d.name();}
    public void open(Player player,String mode,String id,int requested)throws IOException{
        var town=service.town(player);if(town==null||!player.hasPermission("neverlandtownylogistics.use"))return;UUID uuid=town.getUUID();var network=service.network(uuid);var depots=service.depots(uuid);boolean manager=service.manager(player,town);
        View v=new View(uuid);v.inventory=Bukkit.createInventory(v,54,color("&2Логистика города"));List<Runnable> cards=new ArrayList<>();
        if(mode.equals("info")){
            var route=network.route(id);var hub=network.node(route.hub());var h=depots.get(hub.project());var level=service.settings().level(h==null?1:h.level());
            put(v,10,Material.CHEST,"&e"+name(depots,network.node(route.source()).project()),new String[]{"storage",network.node(route.source()).project()},"Отправитель: "+route.source());
            put(v,13,Material.COMPASS,"&fМаршрут: "+route.id(),null,"База: "+name(depots,hub.project()),service.routeStatus(uuid,route.id()),"Лимит курьеров базы: "+level.couriers(),"Груз за рейс: до "+Math.min(route.limit(),level.cargo()),"Погрузка и разгрузка: по "+level.handling()+" с","Скорость: ×"+level.speed(),"Фильтр: "+label(BuildsStorage.decode(route.filter())),"Оставлять у отправителя: "+route.keep());
            put(v,16,Material.BARREL,"&e"+name(depots,network.node(route.target()).project()),new String[]{"storage",network.node(route.target()).project()},"Получатель: "+route.target());
            if(manager){put(v,28,route.enabled()?Material.RED_DYE:Material.LIME_DYE,route.enabled()?"&eПриостановить":"&aВключить",new String[]{route.enabled()?"pause":"resume",route.id()},"Груз в пути продолжит доставку.");
                put(v,30,Material.HOPPER,"&fФильтр по предмету в руке",new String[]{"filter",route.id(),"hand"},"Применится при следующей погрузке.");put(v,31,Material.PAPER,"&fПеревозить всё",new String[]{"filter",route.id(),"all"});
                put(v,32,Material.LEAD,"&fСнять свободных курьеров",new String[]{"dismiss",route.id()},"Для приостановленного маршрута без груза в пути.");put(v,34,Material.BARRIER,"&cУдалить маршрут",new String[]{"delete",route.id()},"Доступно после завершения рейсов.");}
        }else{
            if(mode.equals("nodes"))for(var node:network.nodes().values().stream().sorted(Comparator.comparing(n->n.id())).toList()){int slot=cards.size();cards.add(()->put(v,slot%36,node.project().isBlank()?Material.COMPASS:Material.BARREL,"&e"+node.id(),new String[]{"map"},node.kind().title,node.project().isBlank()?"Промежуточная точка":name(depots,node.project()),"Координаты: "+(int)Math.floor(node.point().x())+", "+(int)Math.floor(node.point().y())+", "+(int)Math.floor(node.point().z())));}
            else if(mode.equals("depots"))for(var d:depots.values().stream().sorted(Comparator.comparing(BuildsStorage.Depot::id)).toList()){int slot=cards.size();cards.add(()->put(v,slot%36,material(d.icon()),"&e"+d.name(),new String[]{"storage",d.id()},"Этап: "+d.level()+" / 5","Слотов: "+d.slots(),"ID: "+d.id(),"Нажмите, чтобы открыть склад."));}
            else if(mode.equals("couriers")){
                var cargo=service.builds().shipments(uuid);for(var job:service.jobs(uuid)){int slot=cards.size();var shipment=cargo.get(job.id());int amount=shipment==null?0:Arrays.stream(shipment.cargo()).filter(Objects::nonNull).mapToInt(ItemStack::getAmount).sum();cards.add(()->put(v,slot%36,Material.LEATHER_BOOTS,"&eКурьер: "+job.route(),new String[]{"info",job.route()},service.status(job),"Груз: "+amount+" предметов","ID: "+job.id()));}
            }else if(mode.equals("cargo"))for(var shipment:service.builds().shipments(uuid).values()){int slot=cards.size();List<String> lore=new ArrayList<>();lore.add(shipment.transit()?"Груз находится у курьера":"Разгрузка подтверждена");lore.add("ID: "+shipment.id());lore.add("Город: "+uuid);for(var item:shipment.cargo())if(item!=null)lore.add(label(item)+" × "+item.getAmount());cards.add(()->put(v,slot%36,Material.CHEST,"&eГруз: "+shipment.route(),null,lore.toArray(String[]::new)));}
            else for(var route:network.routes().values().stream().sorted(Comparator.comparing(t->t.id())).toList()){int slot=cards.size();cards.add(()->put(v,slot%36,route.enabled()?Material.MINECART:Material.GRAY_DYE,"&e"+route.id(),new String[]{"info",route.id()},service.routeStatus(uuid,route.id()),name(depots,network.node(route.source()).project())+" → "+name(depots,network.node(route.target()).project()),"Фильтр: "+label(BuildsStorage.decode(route.filter()))));}
            int pages=Math.max(1,(cards.size()+35)/36),page=Math.max(0,Math.min(pages-1,requested));for(int i=page*36;i<Math.min(cards.size(),(page+1)*36);i++)cards.get(i).run();
            if(cards.isEmpty())put(v,13,Material.BOOK,"&fЗдесь пока пусто",new String[]{"help"},"Создайте точки зданий, свяжите их и задайте маршрут.");
            put(v,40,service.paused()?Material.REDSTONE_BLOCK:Material.COMPASS,service.paused()?"&cРабота приостановлена из-за ошибки":"&fСостояние логистики",null,"Курьеров города: "+service.jobs(uuid).size(),"Страница "+(page+1)+" / "+pages,"NPC ходят только по загруженной территории.");
            if(page>0)put(v,36,Material.ARROW,"&aНазад",new String[]{mode,String.valueOf(page-1)});if(page+1<pages)put(v,44,Material.ARROW,"&aДалее",new String[]{mode,String.valueOf(page+1)});
        }
        put(v,45,Material.MINECART,"&fМаршруты",new String[]{"routes"});put(v,46,Material.CHEST,"&fСклады зданий",new String[]{"depots"});put(v,47,Material.COMPASS,"&fУзлы",new String[]{"nodes"});put(v,48,Material.LEATHER_BOOTS,"&fКурьеры",new String[]{"couriers"});put(v,49,Material.BARREL,"&fГрузы в пути",new String[]{"cargo"});put(v,50,Material.GLOWSTONE_DUST,"&fПоказать сеть",new String[]{"map"});put(v,53,Material.BOOK,"&fКак создать маршрут",new String[]{"help"});
        player.openInventory(v.inventory);
    }
    private Material material(String id){Material value=Material.matchMaterial(id);return value!=null&&value.isItem()?value:Material.BRICKS;}
    private void put(View v,int slot,Material material,String title,String[] action,String... lore){var item=new ItemStack(material);var meta=item.getItemMeta();meta.setDisplayName(color(title));meta.setLore(Arrays.stream(lore).map(s->color("&7"+s)).toList());item.setItemMeta(meta);v.inventory.setItem(slot,item);if(action!=null)v.actions.put(slot,action);}
    private static String color(String text){return ChatColor.translateAlternateColorCodes('&',text);}
    @EventHandler public void click(InventoryClickEvent event){if(!(event.getView().getTopInventory().getHolder() instanceof View v))return;event.setCancelled(true);if(!(event.getWhoClicked() instanceof Player p)||event.getRawSlot()<0||event.getRawSlot()>=54)return;
        var action=v.actions.get(event.getRawSlot());if(action==null)return;Bukkit.getScheduler().runTask(plugin,()->{if(!p.isOnline()||p.getOpenInventory().getTopInventory().getHolder()!=v)return;var t=service.town(p);if(t==null||!t.getUUID().equals(v.town)){p.closeInventory();return;}command.onCommand(p,null,"townylogistics",action);});}
    @EventHandler public void drag(InventoryDragEvent event){if(event.getView().getTopInventory().getHolder() instanceof View)event.setCancelled(true);}
}
