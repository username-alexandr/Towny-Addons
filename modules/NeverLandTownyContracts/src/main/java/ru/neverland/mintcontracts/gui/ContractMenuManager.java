package ru.neverland.mintcontracts.gui;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintcontracts.integration.*;
import ru.neverland.mintcontracts.model.*;
import ru.neverland.mintcontracts.service.*;
import ru.neverland.mintcontracts.util.*;
import java.util.*;
import java.util.function.BiConsumer;

public final class ContractMenuManager implements Listener {
    private final JavaPlugin plugin;private final TownyHook towny;private final ContractService contracts;private final MessageService messages;private final RussianNames names;private final MunicipalDrafts drafts;
    private static final List<String> MOBS=List.of("ZOMBIE","SKELETON","SPIDER","CREEPER","PILLAGER","ENDERMAN","BLAZE","WITHER_SKELETON","DROWNED","HUSK","STRAY","SLIME");
    public ContractMenuManager(JavaPlugin plugin,TownyHook towny,ContractService contracts,MessageService messages,RussianNames names){this.plugin=plugin;this.towny=towny;this.contracts=contracts;this.messages=messages;this.names=names;drafts=new MunicipalDrafts(plugin,towny,contracts,names);}
    public MunicipalDrafts drafts(){return drafts;}
    public void open(Player p){board(p,0);}
    private ContractMenuHolder view(Player p,ContractMenuHolder.Type type,String title){Town t=towny.town(p);if(t==null)throw new IllegalArgumentException("Вы не состоите в городе.");ContractMenuHolder h=new ContractMenuHolder(t.getUUID(),p.getUniqueId(),type);h.inventory(Bukkit.createInventory(h,54,ColorUtil.color(title)));return h;}
    private void show(Player p,ContractMenuHolder h){p.openInventory(h.getInventory());}
    private void button(ContractMenuHolder h,int slot,Material icon,String title,List<String> lore,BiConsumer<Player,Boolean> action){h.getInventory().setItem(slot,item(icon,title,lore));if(action!=null)h.action(slot,action);}
    private void back(ContractMenuHolder h){button(h,49,Material.ARROW,"&fК доске заказов",List.of(),(p,s)->open(p));}
    private void pages(ContractMenuHolder h,int page,int count,java.util.function.BiConsumer<Player,Integer> open){if(page>0)button(h,45,Material.ARROW,"&fПредыдущая страница",List.of(),(p,s)->open.accept(p,page-1));if((page+1)*45<count)button(h,53,Material.ARROW,"&fСледующая страница",List.of(),(p,s)->open.accept(p,page+1));}
    private void board(Player p,int page){ContractMenuHolder h=view(p,ContractMenuHolder.Type.BOARD,"Городские контракты");List<ActiveContract> list=contracts.active(h.townId());page=Math.max(0,Math.min(page,Math.max(0,(list.size()-1)/45)));int slot=0;
        for(ActiveContract c:list.stream().skip(page*45L).limit(45).toList()){ContractDefinition d=contracts.definition(c);button(h,slot++,d.icon(),d.name(),summary(c),(who,s)->detail(who,c.id().toString()));}
        button(h,46,Material.EMERALD,"&aПолучить награду",List.of("&7Ожидает: &f"+contracts.economy().format(contracts.pending(p.getUniqueId())),"&7Спорные выплаты проверяет администратор."),(who,s)->{double amount=contracts.claim(who);messages.send(who,amount>0?"claim-success":amount==0?"no-pending-reward":"economy-error",Map.of("amount",contracts.economy().format(Math.max(0,amount))));open(who);});
        button(h,47,Material.WRITABLE_BOOK,"&bИстория",List.of(),(who,s)->openHistory(who,towny.town(who)));
        button(h,49,Material.CRAFTING_TABLE,"&aСоздать задание",List.of("&7Поставка, охота, дорога, разведка","&7Настройка количества, награды и срока"),(who,s)->create(who));
        button(h,50,Material.BOOK,"&eГотовые шаблоны",List.of(),(who,s)->templates(who,0));
        if(Bukkit.getPluginManager().isPluginEnabled("NeverLandTownyCompanies"))button(h,51,Material.CHEST_MINECART,"&eПредприятия города",List.of("&7Компании могут брать муниципальные заказы"),(who,s)->who.performCommand("company"));
        pages(h,page,list.size(),this::board);show(p,h);
    }
    public void detail(Player p,String id){Town town=towny.town(p);ActiveContract c=town==null?null:contracts.find(town.getUUID(),id);if(c==null)throw new IllegalArgumentException("Заказ уже закрыт или не найден.");ContractMenuHolder h=view(p,ContractMenuHolder.Type.DETAIL,"Условия задания");var d=contracts.definition(c);
        button(h,13,d.icon(),d.name(),summary(c),null);
        if(d.type()==ContractType.DELIVERY)button(h,29,Material.HOPPER,"&aПередать предметы",List.of("&7ЛКМ: один стак","&7Shift + ЛКМ: все подходящие предметы","&7Предметы поступают на городской склад"),(who,shift)->{var current=contracts.find(h.townId(),c.id().toString());if(current==null)throw new IllegalArgumentException("Задание уже закрыто.");var r=contracts.deliver(who,current,shift);switch(r.status()){case BUSY->messages.send(who,"warehouse-busy");case FULL->messages.send(who,"warehouse-full");case UNAVAILABLE->messages.send(who,"warehouse-unavailable");case SUCCESS->{if(r.amount()==0)messages.send(who,"no-items");else messages.send(who,"contributed",Map.of("amount",r.amount(),"item",target(d),"progress",r.progress(),"goal",c.goal()));}}open(who);});
        if(towny.isManager(p,town)&&p.hasPermission("mintcontracts.manage"))button(h,33,Material.RED_DYE,"&cОтменить задание",List.of("&7Открыть подтверждение отмены"),(who,s)->cancel(who,c.id().toString()));back(h);show(p,h);
    }
    public void cancel(Player p,String id){Town town=drafts.manager(p);ActiveContract c=contracts.find(town.getUUID(),id);if(c==null)throw new IllegalArgumentException("Задание не найдено.");ContractMenuHolder h=view(p,ContractMenuHolder.Type.CONFIRM,"Отмена задания");List<String> lore=new ArrayList<>(summary(c));lore.add(contracts.definition(c).type()==ContractType.ROAD?"&eНезавершённая дорога: полный возврат резерва":"&eВыплаты зависят от прогресса и настроек города");
        button(h,13,c.snapshot().icon(),c.snapshot().name(),lore,null);button(h,30,Material.RED_CONCRETE,"&cПодтвердить отмену",List.of(),(who,s)->{Town current=drafts.manager(who);if(!contracts.cancel(current,contracts.find(current.getUUID(),c.id().toString())))throw new IllegalArgumentException("Расчёт или поставка ещё не завершены. Отмена недоступна.");messages.send(who,"cancelled",Map.of("contract",c.snapshot().name()));open(who);});back(h);show(p,h);
    }
    public void openHistory(Player p,Town town){history(p,0);}
    private void history(Player p,int page){ContractMenuHolder h=view(p,ContractMenuHolder.Type.HISTORY,"История контрактов");var list=contracts.repository().history(h.townId());int slot=0;for(var c:list.stream().skip(page*45L).limit(45).toList()){
        var d=contracts.registry().get(c.templateId());String title=c.name()!=null?c.name():d==null?"Заказ №"+c.contractId().toString().substring(0,8):d.name();
        button(h,slot++,c.status()==ContractStatus.SUCCESS?Material.LIME_DYE:Material.YELLOW_DYE,title,List.of("&7Статус: &f"+status(c.status()),"&7Прогресс: &f"+c.progress()+"/"+c.goal(),"&7Начислено: &a"+contracts.economy().format(c.paid()),"&7Возврат назначен: &e"+contracts.economy().format(c.refunded())),null);
    }back(h);pages(h,page,list.size(),this::history);show(p,h);}
    private void templates(Player p,int page){ContractMenuHolder h=view(p,ContractMenuHolder.Type.TEMPLATES,"Шаблоны контрактов");var list=new ArrayList<>(contracts.registry().all());int slot=0;for(var d:list.stream().skip(page*45L).limit(45).toList())button(h,slot++,d.icon(),d.name(),terms(d,null),(who,s)->{drafts.template(who,d);preview(who);});back(h);pages(h,page,list.size(),this::templates);show(p,h);}
    public void create(Player p){drafts.manager(p);ContractMenuHolder h=view(p,ContractMenuHolder.Type.CREATE,"Новое муниципальное задание");
        button(h,10,Material.CHEST,"&aПоставка ресурсов",List.of("&7Возьмите нужный предмет в руку","&7Начальные условия: 1000 шт., 3000 монет, 24 ч."),(who,s)->{drafts.create(who,ContractType.DELIVERY,"hand",1000,"3000",24);preview(who);});
        button(h,12,Material.IRON_SWORD,"&cУничтожение мобов",List.of("&7Выбор моба, количества и награды"),(who,s)->{drafts.create(who,ContractType.MOB_KILL,"ZOMBIE",100,"3000",24);preview(who);});
        button(h,14,Material.STONE_BRICKS,"&eСтроительство дороги",List.of("&7Выделите покрытие на одной высоте:","&f/t contracts pos1", "&f/t contracts pos2", "&7Заказ оплачивается за весь готовый участок"),(who,s)->{var materials=drafts.roadMaterials();if(materials.isEmpty())throw new IllegalArgumentException("Нет разрешённых материалов дороги.");drafts.create(who,ContractType.ROAD,materials.get(0).name(),1,"3000",24);preview(who);});
        button(h,16,Material.FILLED_MAP,"&bРазведка территории",List.of("&7Выделите противоположные углы:","&f/t contracts pos1", "&f/t contracts pos2", "&7Посетите каждый чанк пешком"),(who,s)->{drafts.create(who,ContractType.SCOUT,"AREA",1,"3000",24);preview(who);});back(h);show(p,h);
    }
    public void preview(Player p){var draft=drafts.get(p);var d=draft.definition();ContractMenuHolder h=view(p,ContractMenuHolder.Type.DRAFT,"Проверка условий задания");button(h,13,d.icon(),d.name(),terms(d,draft.area()),null);
        if(d.type()==ContractType.DELIVERY||d.type()==ContractType.MOB_KILL){button(h,19,Material.RED_DYE,"&fКоличество −1 / Shift: −64",List.of(),(who,s)->{drafts.edit(who,s?-64:-1,0,0,null);preview(who);});button(h,20,Material.LIME_DYE,"&fКоличество +1 / Shift: +64",List.of(),(who,s)->{drafts.edit(who,s?64:1,0,0,null);preview(who);});}
        button(h,22,Material.GOLD_NUGGET,"&fНаграда −100 / Shift: −1000",List.of(),(who,s)->{drafts.edit(who,0,s?-1000:-100,0,null);preview(who);});button(h,23,Material.GOLD_INGOT,"&fНаграда +100 / Shift: +1000",List.of(),(who,s)->{drafts.edit(who,0,s?1000:100,0,null);preview(who);});
        button(h,25,Material.CLOCK,"&fСрок +1 ч. / Shift: −1 ч.",List.of(),(who,s)->{drafts.edit(who,0,0,s?-1:1,null);preview(who);});
        if(d.type()==ContractType.DELIVERY)button(h,31,Material.HOPPER,"&eВзять предмет из руки",List.of("&7Сохраняются название и свойства предмета"),(who,s)->{drafts.edit(who,0,0,0,"hand");preview(who);});
        if(d.type()==ContractType.MOB_KILL)button(h,31,Material.ZOMBIE_HEAD,"&eСледующий моб",List.of("&7Другой тип можно указать командой"),(who,s)->{drafts.edit(who,0,0,0,MOBS.get(Math.floorMod(MOBS.indexOf(d.target())+1,MOBS.size())));preview(who);});
        if(d.type()==ContractType.ROAD)button(h,31,Material.STONE_BRICKS,"&eСледующий материал покрытия",List.of(),(who,s)->{var m=drafts.roadMaterials();if(m.isEmpty())throw new IllegalArgumentException("Нет материалов дороги.");drafts.edit(who,0,0,0,m.get(Math.floorMod(m.indexOf(d.icon())+1,m.size())).name());preview(who);});
        button(h,40,Material.LIME_CONCRETE,"&aОпубликовать и зарезервировать награду",List.of("&7Из казны: &e"+contracts.economy().format(d.reward()),"&7Жителям — по вкладу; компании — на её счёт"),(who,s)->{sendActivate(who,d,drafts.confirm(who,draft.revision()));open(who);});back(h);show(p,h);
    }
    public void confirm(Player p){var d=drafts.get(p);sendActivate(p,d.definition(),drafts.confirm(p,d.revision()));open(p);}
    public void sendActivate(Player p,ContractDefinition d,ContractService.ActivateResult r){String key=switch(r){case SUCCESS->"activated";case MAX_ACTIVE->"max-active";case DUPLICATE->"duplicate-template";case NO_MONEY->"not-enough-treasury";case WAREHOUSE_UNAVAILABLE->"warehouse-unavailable";case ECONOMY_ERROR,SAVE_ERROR->"economy-error";};messages.send(p,key,Map.of("contract",d.name(),"reward",contracts.economy().format(d.reward())));}
    private List<String> terms(ContractDefinition d,WorkArea area){List<String> lore=new ArrayList<>();lore.add("&7Тип: &f"+type(d.type()));lore.add("&7Цель: &e"+target(d)+" × "+d.goal());lore.add("&7Резерв из казны: &e"+contracts.economy().format(d.reward()));lore.add("&7Срок: &f"+TimeUtil.format(d.durationSeconds()));
        if(area!=null){World world=Bukkit.getWorld(area.world());lore.add("&7Мир: &f"+(world==null?"Недоступен":world.getName()));lore.add("&7"+(d.type()==ContractType.SCOUT?"Чанки":"Блоки")+": &f"+area.minX()+", "+area.minZ()+" → "+area.maxX()+", "+area.maxZ());if(d.type()==ContractType.ROAD){lore.add("&7Высота покрытия: &f"+area.y());lore.add("&7Опора снизу, два свободных блока сверху");lore.add("&7Проверка после укладки: &f"+Math.max(5,plugin.getConfig().getInt("municipal.road.stable-seconds",60))+" с.");lore.add("&eЧастичная дорога не оплачивается");}else{lore.add("&7На каждом чанке: &f"+Math.max(5,plugin.getConfig().getInt("municipal.scout.dwell-seconds",30))+" с.");lore.add("&7Пешком; телепортация сбрасывает отсчёт");}}return lore;}
    private List<String> summary(ActiveContract c){List<String> lore=new ArrayList<>(terms(c.snapshot(),c.area()));lore.add("&7Прогресс: &a"+c.progress()+"/"+c.goal());lore.add("&7Осталось: &f"+TimeUtil.format(Math.max(0,(c.expiresAt()-System.currentTimeMillis())/1000)));lore.add("&7Исполнитель: &f"+contracts.companyName(c));lore.add("&7ID: &f"+c.shortId());if(!c.funded())lore.add("&eОжидает подтверждения резерва казны");if(contracts.repository().deliveryBusy(c.id()))lore.add("&eОжидает завершения поставки");if(c.settlementStatus()!=null)lore.add("&eОжидает завершения расчёта");return lore;}
    private String target(ContractDefinition d){return d.type()==ContractType.SCOUT?"Уникальные чанки":d.type()==ContractType.DELIVERY?names.item(d.deliveryItem()):d.target().equalsIgnoreCase("ANY")?"Любая подходящая цель":names.value(d.target());}
    private String type(ContractType t){return switch(t){case DELIVERY->"Поставка";case MOB_KILL->"Охота";case BLOCK_BREAK->"Добыча";case FISH->"Рыбалка";case ROAD->"Дорога";case SCOUT->"Разведка";};}
    private String status(ContractStatus s){return switch(s){case SUCCESS->"Выполнен";case CANCELLED->"Отменён";case EXPIRED->"Истёк срок";};}
    private ItemStack item(Material m,String title,List<String> lore){ItemStack s=new ItemStack(m.isItem()&&!m.isAir()?m:Material.PAPER);ItemMeta meta=s.getItemMeta();meta.setDisplayName(ColorUtil.color(title));meta.setLore(lore.stream().map(ColorUtil::color).toList());meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);s.setItemMeta(meta);return s;}
    @EventHandler public void click(InventoryClickEvent e){Inventory inv=e.getView().getTopInventory();if(!(inv.getHolder() instanceof ContractMenuHolder h))return;e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p)||e.getRawSlot()<0||e.getRawSlot()>=inv.getSize())return;int slot=e.getRawSlot();boolean shift=e.isShiftClick();
        Bukkit.getScheduler().runTask(plugin,()->{if(!p.isOnline()||p.getOpenInventory().getTopInventory()!=inv||!p.getUniqueId().equals(h.viewer()))return;Town t=towny.town(p);if(t==null||!t.getUUID().equals(h.townId())||!p.hasPermission("mintcontracts.use")){p.closeInventory();return;}try{h.click(slot,p,shift);}catch(IllegalArgumentException|IllegalStateException ex){p.sendMessage(ColorUtil.color("&6NeverLand &8» &c"+ex.getMessage()));}});
    }
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof ContractMenuHolder)e.setCancelled(true);}
    @EventHandler public void quit(org.bukkit.event.player.PlayerQuitEvent e){drafts.clear(e.getPlayer());}
}
