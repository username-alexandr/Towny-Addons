package ru.neverland.mintcontracts.orders;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.*;
import ru.neverland.mintcontracts.service.RussianNames;

/** Readable, read-only warehouse menus; changes require explicit commands with a written reason. */
public final class ResourceAdmin implements Listener {
    public static final String VIEW="neverland.orders.resources",MANAGE="neverland.orders.resources.manage";
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.of("Europe/Moscow"));
    private final JavaPlugin plugin;private final RussianNames names;private final ServerMaterials materials;
    private static final class Screen implements InventoryHolder {
        final UUID owner;final Map<Integer,Runnable> actions=new HashMap<>();Inventory inventory;
        Screen(Player p){owner=p.getUniqueId();}public Inventory getInventory(){return inventory;}
    }
    public ResourceAdmin(JavaPlugin plugin,RussianNames names,ServerMaterials materials){this.plugin=plugin;this.names=names;this.materials=materials;plugin.getServer().getPluginManager().registerEvents(this,plugin);}
    private static boolean allowed(CommandSender sender,String permission){return sender.isOp()||sender.hasPermission(permission)||sender.hasPermission("neverland.orders.admin");}
    private void require(CommandSender sender,String permission){if(!allowed(sender,permission))throw new IllegalArgumentException("Нет права "+permission);}
    private <T> void result(CommandSender sender,CompletionStage<T> future,Consumer<T> success){result(sender,future,false,success);}
    private <T> void result(CommandSender sender,CompletionStage<T> future,boolean opensMenu,Consumer<T> success){
        Inventory previous=opensMenu&&sender instanceof Player player?player.getOpenInventory().getTopInventory():null;
        future.whenComplete((value,error)->{
        Throwable cause=error;while(cause!=null&&cause.getCause()!=null)cause=cause.getCause();String problem=cause==null?null:cause.getMessage()==null?cause.getClass().getSimpleName():cause.getMessage();
        SuiteScheduler.reply(plugin,sender,()->{if(!allowed(sender,VIEW))return;if(problem!=null)sender.sendMessage("§cСклад: "+problem);else if(previous==null||sender instanceof Player p&&p.isOnline()&&p.getOpenInventory().getTopInventory()==previous)success.accept(value);});
    });}
    public void command(CommandSender sender,String[] args){
        require(sender,VIEW);String action=args.length==0?"menu":args[0].toLowerCase(Locale.ROOT);int page=1;
        if(args.length==2&&Set.of("balance","history","allocations","projects").contains(action))page=Integer.parseInt(args[1]);final int currentPage=page;
        if(page<1||page>100000)throw new IllegalArgumentException("Неверная страница");
        switch(action){
            case "menu"->{if(sender instanceof Player player)home(player);else sender.sendMessage("/orders admin resources balance | history [страница] | allocations [страница] | export");}
            case "balance"->result(sender,materials.balances(),true,rows->{if(sender instanceof Player p)balances(p,rows,currentPage);else{sender.sendMessage("Серверный склад: материал — получено / доступно");rows.stream().skip((currentPage-1L)*20).limit(20).forEach(r->sender.sendMessage(names.value(r.material())+": "+r.received()+" / "+r.available()));}});
            case "history"->result(sender,materials.history(page),true,rows->{if(sender instanceof Player p)history(p,rows,currentPage);else rows.forEach(r->sender.sendMessage(r.id()+" | "+r.playerName()+" | "+names.value(r.material())+" × "+r.amount()+" | "+state(r.state())));});
            case "allocations"->result(sender,materials.allocations(page),true,rows->{if(sender instanceof Player p)allocations(p,rows,currentPage);else rows.forEach(r->sender.sendMessage(r.id()+" | "+r.project()+" | "+state(r.state())+" | "+r.items()));});
            case "projects"->result(sender,materials.projects(page),true,rows->{if(sender instanceof Player p)projects(p,rows,currentPage);else rows.forEach(r->sender.sendMessage(r.id()+" | "+r.title()+" | "+state(r.state())+" | "+r.needs()));});
            case "demand"->result(sender,materials.demand(),rows->{sender.sendMessage("§eЕщё нужно для открытых строек:");rows.forEach((material,amount)->sender.sendMessage(names.value(material)+" × "+amount));if(rows.isEmpty())sender.sendMessage("§aВсе материалы есть либо открытых проектов нет.");});
            case "project-history"->{if(args.length!=2)throw new IllegalArgumentException("/orders admin resources project-history <проект>");result(sender,materials.projectHistory(args[1]),rows->{sender.sendMessage("§eИстория проекта "+args[1]);rows.forEach(sender::sendMessage);});}
            case "plan"->{
                require(sender,MANAGE);if(SuiteState.frozen(plugin.getName()))throw new IllegalArgumentException("Серверный склад на паузе");
                if(args.length<5||!args[3].equals("confirm"))throw new IllegalArgumentException("/orders admin resources plan <ID> <STONE=100,OAK_LOG=64> confirm <название стройки>");
                var costs=new TreeMap<String,Long>();for(String pair:args[2].split(",",-1)){String[] fields=pair.split("=",-1);if(fields.length!=2)throw new IllegalArgumentException("Материалы: STONE=100,OAK_LOG=64");String material=Material.valueOf(fields[0].toUpperCase(Locale.ROOT)).name();if(costs.putIfAbsent(material,Long.parseLong(fields[1]))!=null)throw new IllegalArgumentException("Материал повторяется");}
                String title=String.join(" ",Arrays.copyOfRange(args,4,args.length));result(sender,materials.plan(args[1],title,sender.getName(),costs),r->{projectAudit(sender,r.id(),"PLAN",r.title());sender.sendMessage("§aСмета «"+r.title()+"» сохранена. Недостающие материалы чаще появятся в новых подборках. Проверка: /orders admin resources demand");});
            }
            case "reserve-project","cancel-project"->{
                require(sender,MANAGE);if(SuiteState.frozen(plugin.getName()))throw new IllegalArgumentException("Серверный склад на паузе");
                if(args.length<4||!args[2].equals("confirm"))throw new IllegalArgumentException("/orders admin resources "+action+" <проект> confirm <основание>");
                String reason=String.join(" ",Arrays.copyOfRange(args,3,args.length));
                if(action.equals("reserve-project"))result(sender,materials.reserveProject(args[1],sender.getName(),reason),r->{projectAudit(sender,args[1],"RESERVE",reason);sender.sendMessage("§aМатериалы обеспечены. Резерв: "+r.id()+". После завершения стройки используйте consume; если стройка отменена — return.");});
                else result(sender,materials.cancelProject(args[1],sender.getName(),reason),r->{projectAudit(sender,r.id(),"CANCEL",reason);sender.sendMessage("§eСмета отменена без потери материалов.");});
            }
            case "export"->result(sender,materials.export(),path->sender.sendMessage("§aИстория сдач сохранена: "+path.getFileName()+" в NeverLandTownyContracts/resource-exports"));
            case "allocate"->{
                if(SuiteState.frozen(plugin.getName()))throw new IllegalArgumentException("Серверный склад на паузе");
                require(sender,MANAGE);if(args.length<6||!args[4].equals("confirm"))throw new IllegalArgumentException("/orders admin resources allocate <проект> <материал> <количество> confirm <основание>");
                String material=Material.valueOf(args[2].toUpperCase(Locale.ROOT)).name();long amount=Long.parseLong(args[3]);String reason=String.join(" ",Arrays.copyOfRange(args,5,args.length));
                UUID id=UUID.randomUUID();result(sender,materials.allocate(id,args[1],sender.getName(),reason,Map.of(material,amount)),r->sender.sendMessage("§aМатериалы зарезервированы для "+r.project()+". ID: "+r.id()+". Физические предметы не выдаются; резерв учитывает стройку."));
            }
            case "consume","return"->{
                if(SuiteState.frozen(plugin.getName()))throw new IllegalArgumentException("Серверный склад на паузе");
                require(sender,MANAGE);if(args.length<4||!args[2].equals("confirm"))throw new IllegalArgumentException("/orders admin resources "+action+" <ID резерва> confirm <основание>");
                String reason=String.join(" ",Arrays.copyOfRange(args,3,args.length));if(reason.trim().length()<8)throw new IllegalArgumentException("Укажите основание (не менее 8 символов)");
                result(sender,materials.allocation(UUID.fromString(args[1]),action,sender.getName()+": "+reason),r->sender.sendMessage("§aРезерв "+r.id()+": "+state(r.state())));
            }
            default->sender.sendMessage("/orders admin resources [balance|history|allocations|projects|demand|project-history|plan|reserve-project|cancel-project|export|allocate|consume|return]");
        }
    }
    private Screen screen(Player player,String title){require(player,VIEW);var s=new Screen(player);s.inventory=MenuStyle.inventory(plugin,s,54,title);for(int i=36;i<54;i++)s.inventory.setItem(i,item(Material.GRAY_STAINED_GLASS_PANE," ",List.of()));button(s,45,Material.ARROW,"Назад",List.of("Серверный склад"),()->home(player));button(s,49,Material.BARRIER,"Закрыть",List.of(),player::closeInventory);return s;}
    private ItemStack item(Material icon,String title,List<String> lore){var item=new ItemStack(icon);var meta=item.getItemMeta();meta.setDisplayName(MenuStyle.nameLegacy(title));meta.setLore(MenuStyle.loreStrings(lore));item.setItemMeta(meta);return item;}
    private void button(Screen s,int slot,Material icon,String title,List<String> lore,Runnable action){s.inventory.setItem(slot,item(icon,title,lore));if(action!=null)s.actions.put(slot,action);}
    private void page(Screen s,Player player,String mode,int page,boolean more){if(page>1)button(s,47,Material.ARROW,"Предыдущая страница",List.of(),()->command(player,new String[]{mode,Integer.toString(page-1)}));button(s,48,Material.PAPER,"Страница "+page,List.of(),null);if(more)button(s,51,Material.ARROW,"Следующая страница",List.of(),()->command(player,new String[]{mode,Integer.toString(page+1)}));}
    private void home(Player player){var s=screen(player,"Серверный склад");s.actions.remove(45);s.inventory.setItem(45,null);
        button(s,10,Material.BARREL,"Запас материалов",List.of("Получено от игроков и доступно для строек"),()->command(player,new String[]{"balance"}));
        button(s,12,Material.WRITABLE_BOOK,"История сдач",List.of("Игрок, заказ, дата, материал и количество"),()->command(player,new String[]{"history"}));
        button(s,14,Material.ANVIL,"Резервы строек",List.of("Материалы для спавна и других проектов"),()->command(player,new String[]{"allocations"}));
        button(s,16,Material.MAP,"Выгрузить историю",List.of("Создать CSV для сверки"),()->command(player,new String[]{"export"}));
        button(s,22,Material.BRICKS,"Серверные стройки",List.of("Сметы спавна и общих построек","Недостающие материалы влияют на новые заказы"),()->command(player,new String[]{"projects"}));
        button(s,30,Material.BOOK,"Как использовать запас",List.of("Резерв: /orders admin resources allocate", "Завершить стройку: consume", "Вернуть неиспользованный резерв: return", "Изменения требуют confirm и основания"),null);player.openInventory(s.inventory);
    }
    private void balances(Player p,List<ServerMaterials.Balance> rows,int number){if(number<1||number>100000)throw new IllegalArgumentException("Неверная страница");var s=screen(p,"Склад • Материалы");int slot=0;for(var r:rows.stream().skip((number-1L)*36).limit(36).toList())button(s,slot++,Material.valueOf(r.material()),names.value(r.material()),List.of("§7Получено: §f"+r.received(),"§7Доступно для стройки: §a"+r.available(),"§7В резервах и использовано: §f"+(r.received()-r.available())),null);page(s,p,"balance",number,rows.size()>number*36L);p.openInventory(s.inventory);}
    private void history(Player p,List<ServerMaterials.Delivery> rows,int number){var s=screen(p,"Склад • История сдач");int slot=0;for(var r:rows)button(s,slot++,Material.valueOf(r.material()),names.value(r.material())+" × "+r.amount(),List.of("§7Игрок: §f"+r.playerName(),"§7Заказ: §f"+r.template(),"§7Редкость: §f"+OrderRarity.valueOf(r.rarity()).label,"§7Награда: §a"+AuditTrail.cents(r.cents()),"§7Дата: §f"+TIME.format(Instant.ofEpochMilli(r.created())),"§7Статус: §f"+state(r.state()),"§7ID: §f"+r.id()),()->p.sendMessage("ID поставки: "+r.id()));page(s,p,"history",number,rows.size()==20);p.openInventory(s.inventory);}
    private void allocations(Player p,List<ServerMaterials.Allocation> rows,int number){var s=screen(p,"Склад • Резервы строек");int slot=0;for(var r:rows){var lore=new ArrayList<String>();lore.add("§7Статус: §f"+state(r.state()));lore.add("§7Администратор: §f"+r.actor());lore.add("§7Основание: §f"+r.reason());for(var e:r.items().entrySet())lore.add("§7"+names.value(e.getKey())+": §f"+e.getValue());lore.add("§7ID: §f"+r.id());button(s,slot++,Material.CHEST,r.project(),lore,()->p.sendMessage("ID резерва: "+r.id()));}page(s,p,"allocations",number,rows.size()==20);p.openInventory(s.inventory);}
    private void projects(Player player,List<ServerMaterials.Project> rows,int number){var s=screen(player,"Серверные стройки");int slot=0;for(var row:rows){var lore=new ArrayList<String>();lore.add("Статус: "+state(row.state()));lore.add("ID: "+row.id());lore.add("Автор: "+row.actor());row.needs().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->lore.add(names.value(e.getKey())+" × "+e.getValue()));lore.add("Нажмите: история и команды проекта");button(s,slot++,Material.BRICKS,row.title(),lore,()->{command(player,new String[]{"project-history",row.id()});player.sendMessage("Резерв всей сметы: /orders admin resources reserve-project "+row.id()+" confirm <основание>");player.sendMessage("Отмена: /orders admin resources cancel-project "+row.id()+" confirm <основание>");});}page(s,player,"projects",number,rows.size()==20);player.openInventory(s.inventory);}
    private void projectAudit(CommandSender sender,String project,String action,String reason){var actor=sender instanceof Player p?AuditTrail.player(p.getUniqueId()):new AuditRecord.Party("CONSOLE","",sender.getName(),"");AuditTrail.record(plugin,action,project,"SERVER_PROJECT",action,actor,AuditRecord.Party.system(),AuditRecord.Party.system(),"",0,"",reason);}
    private static String state(String state){return switch(state){case "OPEN"->"Собираем материалы";case "CANCELLED"->"Отменена";case "ALLOCATED"->"Материалы в резерве или уже использованы";case "PREPARED"->"Передача ожидает подтверждения";case "CONFIRMED"->"Ресурсы приняты";case "VOID"->"Сдача отменена при сверке";case "RESERVED"->"Ожидает использования";case "SPENT"->"Использовано на стройке";case "RETURNED"->"Возвращено в общий запас";default->state;};}
    @EventHandler(priority=EventPriority.LOWEST) public void click(InventoryClickEvent e){if(!(e.getView().getTopInventory().getHolder() instanceof Screen s))return;e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p)||!s.owner.equals(p.getUniqueId())||!allowed(p,VIEW))return;var action=s.actions.get(e.getRawSlot());if(action!=null)try{action.run();}catch(Exception ex){p.sendMessage("§c"+ex.getMessage());}}
    @EventHandler(priority=EventPriority.LOWEST) public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Screen)e.setCancelled(true);}
}
