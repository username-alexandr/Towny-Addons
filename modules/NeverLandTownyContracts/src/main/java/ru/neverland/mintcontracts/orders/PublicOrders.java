package ru.neverland.mintcontracts.orders;

import java.io.*;
import java.util.*;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.*;
import ru.neverland.earnings.*;
import ru.neverland.mintcontracts.service.RussianNames;

/** Independent, server-funded board. Never calls the city contract service or treasury. */
public final class PublicOrders implements Listener,CommandExecutor,TabCompleter {
    public enum Type { DELIVERY,MINING,HUNTING,FISHING }
    public record Template(String id,String title,Type type,String target,int amount,long cents,long duration){}
    public record Order(UUID id,UUID player,String template,String title,Type type,String target,int goal,int progress,long cents,long remaining,long cycle,String state){
        Order progress(int n){return new Order(id,player,template,title,type,target,goal,Math.min(goal,n),cents,remaining,cycle,state);}
        Order time(long n){return new Order(id,player,template,title,type,target,goal,progress,cents,n,cycle,n==0&&progress<goal?"EXPIRED":state);}
        Order state(String s){return new Order(id,player,template,title,type,target,goal,progress,cents,remaining,cycle,s);}
    }
    private final JavaPlugin plugin;private final java.nio.file.Path file;private final Map<UUID,Order> orders=new LinkedHashMap<>();
    private final List<Template> templates=new ArrayList<>();private final IncomeRuntime income;private final ActionGuard guard;private final RussianNames names;
    private final int maximum,boardSize;private final long refresh;private boolean fault;
    public PublicOrders(JavaPlugin plugin)throws Exception{
        this(plugin,new RussianNames(plugin));
    }
    public PublicOrders(JavaPlugin plugin,RussianNames names)throws Exception{
        this.names=names;
        this.plugin=plugin;file=plugin.getDataFolder().toPath().resolve("public-orders.yml");
        if(!plugin.getDataFolder().toPath().resolve("orders.yml").toFile().exists())plugin.saveResource("orders.yml",false);
        var cfg=SafeYaml.load(plugin.getDataFolder().toPath().resolve("orders.yml"));
        maximum=positive(cfg,"maximum-active",3,3);boardSize=positive(cfg,"board-size",4,54);refresh=positive(cfg,"refresh-seconds",3600,604800)*1000L;
        income=new IncomeRuntime(plugin,"order-payments",money(cfg,"limits.player-24h",1500),money(cfg,"limits.server-24h",100000),cfg.getBoolean("enabled",true));
        var section=cfg.getConfigurationSection("templates");if(section==null)throw new IllegalArgumentException("Нет templates в orders.yml");
        for(String id:section.getKeys(false)){
            if(!id.matches("[a-z0-9_]{1,48}"))throw new IllegalArgumentException("Неверный ID заказа");var s=section.getConfigurationSection(id);Type type=Type.valueOf(s.getString("type",""));String target=s.getString("target","");
            if(type==Type.HUNTING){EntityType.valueOf(target);}else{Material m=Material.valueOf(target);if(type==Type.MINING&&!ActionGuard.ore(m))throw new IllegalArgumentException("MINING допускает только руды");if(type==Type.FISHING&&!Set.of("COD","SALMON","PUFFERFISH","TROPICAL_FISH").contains(target))throw new IllegalArgumentException("FISHING допускает рыбу");}
            templates.add(new Template(id,s.getString("title",id),type,target,positive(s,"amount",1,100000),money(s,"reward",50),positive(s,"duration-seconds",7200,604800)*1000L));
        }
        if(templates.isEmpty())throw new IllegalArgumentException("Доска пуста");
        load();guard=new ActionGuard(plugin,"order-blocks.log");plugin.getServer().getPluginManager().registerEvents(guard,plugin);plugin.getServer().getPluginManager().registerEvents(this,plugin);
        var command=Objects.requireNonNull(plugin.getCommand("orders"));command.setExecutor(this);command.setTabCompleter(this);
        income.start();SuiteScheduler.get().runTaskTimer(plugin,this::tick,200,200);
    }
    private static int positive(org.bukkit.configuration.ConfigurationSection c,String k,int d,int max){int n=SafeYaml.intValue(c,k,d);if(n<=0||n>max)throw new IllegalArgumentException(k+": 1.."+max);return n;}
    public static long money(org.bukkit.configuration.ConfigurationSection c,String k,int d){double v=SafeYaml.doubleValue(c,k,d);if(v<=0||v>1_000_000||Math.abs(v*100-Math.rint(v*100))>0.00001)throw new IllegalArgumentException("Неверная сумма "+k);return Math.round(v*100);}
    private void load()throws IOException{
        var y=SafeYaml.load(file);if(y.contains("schema")&&SafeYaml.intValue(y,"schema")!=1)throw new IOException("Неизвестная схема orders");var s=SafeYaml.section(y,"orders");
        if(s!=null)for(String key:s.getKeys(false)){var r=s.getConfigurationSection(key);var o=new Order(UUID.fromString(key),UUID.fromString(SafeYaml.text(r,"player")),SafeYaml.text(r,"template"),SafeYaml.text(r,"title"),Type.valueOf(SafeYaml.text(r,"type")),SafeYaml.text(r,"target"),SafeYaml.intValue(r,"goal"),SafeYaml.intValue(r,"progress"),SafeYaml.longValue(r,"cents"),SafeYaml.longValue(r,"remaining"),SafeYaml.longValue(r,"cycle"),SafeYaml.text(r,"state"));if(o.goal()<=0||o.progress()<0||o.progress()>o.goal()||o.cents()<=0||o.remaining()<0||!Set.of("ACTIVE","CANCELLED","EXPIRED").contains(o.state()))throw new IOException("Повреждён заказ "+key);orders.put(o.id(),o);}
        AtomicFiles.loaded(file);
    }
    private void save()throws IOException{
        var y=new YamlConfiguration();y.set("schema",1);
        for(var o:orders.values()){String p="orders."+o.id()+".";y.set(p+"player",o.player().toString());y.set(p+"template",o.template());y.set(p+"title",o.title());y.set(p+"type",o.type().name());y.set(p+"target",o.target());y.set(p+"goal",o.goal());y.set(p+"progress",o.progress());y.set(p+"cents",o.cents());y.set(p+"remaining",o.remaining());y.set(p+"cycle",o.cycle());y.set(p+"state",o.state());}
        try{AtomicFiles.write(file,y::saveToString);}catch(IOException ex){fault=true;throw ex;}
    }
    private boolean active(){return !fault&&income.enabled();}
    private boolean open(Order o){return o.state().equals("ACTIVE")&&income.ledger().get(o.id())==null;}
    private List<Template> board(){var list=new ArrayList<>(templates);Collections.shuffle(list,new Random(System.currentTimeMillis()/refresh));return list.subList(0,Math.min(boardSize,list.size()));}
    private void tick(){synchronized(SuiteState.lock()){if(!active())return;boolean changed=false;
        for(Order o:List.copyOf(orders.values()))if(open(o)&&o.progress()<o.goal()){orders.put(o.id(),o.time(Math.max(0,o.remaining()-10000)));changed=true;}
        if(changed)try{save();}catch(IOException e){plugin.getLogger().severe(e.getMessage());}
    }}
    private void accept(Player p,String template)throws IOException{
        if(!p.hasPermission("neverland.orders.use"))throw new IllegalArgumentException("Нет прав на заказы");
        if(!active())throw new IllegalArgumentException("Заказы приостановлены; прогресс сохранён");
        if(orders.values().stream().filter(o->o.player().equals(p.getUniqueId())&&open(o)).count()>=maximum)throw new IllegalArgumentException("Одновременно разрешено "+maximum+" заказа");
        long cycle=System.currentTimeMillis()/refresh;if(orders.values().stream().anyMatch(o->o.player().equals(p.getUniqueId())&&o.template().equals(template)&&o.cycle()==cycle))throw new IllegalArgumentException("Этот заказ уже принят в текущем обновлении доски");
        Template t=board().stream().filter(v->v.id().equals(template)).findFirst().orElseThrow(()->new IllegalArgumentException("Заказ отсутствует на доске"));
        Order o=new Order(UUID.randomUUID(),p.getUniqueId(),t.id(),t.title(),t.type(),t.target(),t.amount(),0,t.cents(),t.duration(),cycle,"ACTIVE");orders.put(o.id(),o);save();p.sendMessage(Component.text("Заказ принят: ",NamedTextColor.GREEN).append(Component.text(t.title(),NamedTextColor.WHITE)));showOrder(p,o);p.sendMessage(button("Мои заказы","/orders mine","Показать прогресс и действия по заказам",NamedTextColor.YELLOW));
    }
    private void progress(Player p,Type type,String target){if(!active()||!ActionGuard.survival(p)||!p.hasPermission("neverland.orders.use"))return;
        // One action advances one order, never three copies of the same objective.
        Order o=orders.values().stream().filter(v->v.player().equals(p.getUniqueId())&&open(v)&&v.type()==type&&v.target().equals(target)&&v.progress()<v.goal()).findFirst().orElse(null);if(o==null)return;
        o=o.progress(o.progress()+1);orders.put(o.id(),o);try{save();if(o.progress()==o.goal()){p.sendMessage(Component.text("Заказ выполнен: ",NamedTextColor.GREEN).append(Component.text(o.title(),NamedTextColor.WHITE)));showOrder(p,o);}}catch(IOException ex){plugin.getLogger().severe(ex.getMessage());}
    }
    private void claim(Player p,UUID id)throws Exception{
        if(!active())throw new IllegalArgumentException("Заказы приостановлены");Order o=orders.get(id);if(o==null||!o.player().equals(p.getUniqueId()))throw new IllegalArgumentException("Заказ не найден");
        var existing=income.ledger().get(id);if(existing!=null){recover(p);showOrder(p,o);return;}
        if(!open(o))throw new IllegalArgumentException("Заказ уже закрыт");
        if(o.type()==Type.DELIVERY){
            if(!income.ready(p.getUniqueId()))throw new IllegalArgumentException("Экономика не подключена. Ресурсы остаются у вас.");
            var sample=new ItemStack(Material.valueOf(o.target()));int total=0;for(ItemStack item:p.getInventory().getStorageContents())if(item!=null&&item.isSimilar(sample))total+=item.getAmount();
            if(total<o.goal())throw new IllegalArgumentException("Для сдачи нужно: "+names.value(o.target())+" × "+o.goal()+". В инвентаре: "+total+". Именные и изменённые предметы не принимаются.");
            var receipt=PlayerSaveReceipt.before(p);
            income.ledger().reserve(id,p.getUniqueId(),o.cents(),"orders:"+o.template(),true);
            int left=o.goal();ItemStack[] contents=p.getInventory().getStorageContents();
            for(int i=0;i<contents.length&&left>0;i++)if(contents[i]!=null&&contents[i].isSimilar(sample)){int n=Math.min(left,contents[i].getAmount());contents[i]=contents[i].clone();contents[i].setAmount(contents[i].getAmount()-n);left-=n;if(contents[i].getAmount()==0)contents[i]=null;}
            p.getInventory().setStorageContents(contents);p.getPersistentDataContainer().set(receiptKey(id),PersistentDataType.BYTE,(byte)1);receipt.save(p);
            income.ledger().release(id,"saved player inventory "+p.getUniqueId());
        }else{
            if(o.progress()<o.goal())throw new IllegalArgumentException("Заказ ещё не выполнен");income.ledger().reserve(id,p.getUniqueId(),o.cents(),"orders:"+o.template(),false);
        }
        p.sendMessage(Component.text("Награда ожидает зачисления на личный счёт: "+AuditTrail.cents(o.cents())+" монет.",NamedTextColor.GREEN));showOrder(p,o);
    }
    private NamespacedKey receiptKey(UUID id){return new NamespacedKey(plugin,"order_"+id.toString().replace("-",""));}
    private void recover(Player p)throws IOException{for(var e:income.ledger().all())if(e.player().equals(p.getUniqueId())&&e.state()==RewardLedger.State.HELD&&p.getPersistentDataContainer().has(receiptKey(e.id()),PersistentDataType.BYTE)){
        // A failed save in this session must be confirmed again before releasing escrow.
        var receipt=PlayerSaveReceipt.before(p);receipt.save(p);income.ledger().release(e.id(),"recovered player receipt");
    }}
    @EventHandler public void join(PlayerJoinEvent e){SuiteScheduler.entity(plugin,e.getPlayer(),()->{try{recover(e.getPlayer());}catch(Exception ex){plugin.getLogger().severe("Доставка ожидает сверки: "+ex.getMessage());}});}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void mine(BlockBreakEvent e){synchronized(SuiteState.lock()){
        if(!ActionGuard.ore(e.getBlock().getType()))return;boolean natural=guard.untouched(e.getBlock());guard.deny(e.getBlock());if(natural&&e.isDropItems()){Player player=e.getPlayer();Material material=e.getBlock().getType();Location location=e.getBlock().getLocation();SuiteScheduler.region(plugin,location,()->{if(!e.isCancelled()&&location.getBlock().getType()!=material)SuiteScheduler.entity(plugin,player,()->progress(player,Type.MINING,material.name()));});}
    }}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void kill(EntityDeathEvent e){synchronized(SuiteState.lock()){
        if(!guard.hunted(e))return;Player player=e.getEntity().getKiller();String target=e.getEntityType().name();
        SuiteScheduler.entity(plugin,player,()->{if(!e.isCancelled())progress(player,Type.HUNTING,target);});
    }}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void fish(PlayerFishEvent e){synchronized(SuiteState.lock()){if(e.getState()==PlayerFishEvent.State.CAUGHT_FISH&&e.getCaught() instanceof Item item&&SuiteScheduler.owns(item)){Player player=e.getPlayer();String target=item.getItemStack().getType().name();SuiteScheduler.entity(plugin,player,()->{if(!e.isCancelled())progress(player,Type.FISHING,target);});}}}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){synchronized(SuiteState.lock()){
        try{
            if(args.length>0&&args[0].equalsIgnoreCase("admin")){if(!sender.isOp()&&!sender.hasPermission("neverland.orders.admin"))throw new IllegalArgumentException("Нет прав администратора");income.admin(sender,Arrays.copyOfRange(args,1,args.length));return true;}
            if(!(sender instanceof Player p))throw new IllegalArgumentException("Игровая доска: /orders. Сверка: /orders admin");if(!p.hasPermission("neverland.orders.use"))throw new IllegalArgumentException("Нет прав на заказы");
            String action=args.length==0?"board":args[0].toLowerCase(Locale.ROOT);
            switch(action){
                case "board" -> menu(p);
                case "accept" -> {if(args.length!=2)throw new IllegalArgumentException("/orders accept <заказ>");accept(p,args[1]);}
                case "mine" -> showMine(p);
                case "claim" -> {if(args.length!=2)throw new IllegalArgumentException("/orders claim <UUID>");claim(p,UUID.fromString(args[1]));}
                case "cancel" -> {if(args.length!=2)throw new IllegalArgumentException("/orders cancel <UUID>");var o=orders.get(UUID.fromString(args[1]));if(o==null||!o.player().equals(p.getUniqueId())||!open(o))throw new IllegalArgumentException("Активный заказ не найден");orders.put(o.id(),o.state("CANCELLED"));save();p.sendMessage("Отменён без штрафа.");}
                default -> p.sendMessage("/orders; /orders accept <заказ>; /orders mine; /orders claim <UUID>; /orders cancel <UUID>");
            }
        }catch(Exception ex){sender.sendMessage("§c"+ex.getMessage());}return true;
    }}
    private record Board(Map<Integer,String> slots) implements InventoryHolder{public Inventory getInventory(){return null;}}
    private void menu(Player p){
        Map<Integer,String> slots=new HashMap<>();var board=board();
        for(int i=0;i<board.size();i++)slots.put(i,board.get(i).id());
        Inventory inv=MenuStyle.inventory(plugin,new Board(Map.copyOf(slots)),54,"Государственные заказы");
        for(int i=0;i<board.size();i++)inv.setItem(i,icon(board.get(i)));
        p.openInventory(inv);
    }
    private ItemStack icon(Template t){
        var item=new ItemStack(switch(t.type()){case DELIVERY->Material.CHEST;case MINING->Material.IRON_PICKAXE;case HUNTING->Material.IRON_SWORD;case FISHING->Material.FISHING_ROD;});
        var meta=item.getItemMeta();
        meta.setDisplayName(MenuStyle.nameLegacy("§6"+t.title()));
        meta.setLore(MenuStyle.loreStrings(List.of(
                "§7Задание: §f"+task(t.type()),
                "§7Цель: §f"+names.value(t.target())+" × "+t.amount(),
                "§7Награда: §a"+AuditTrail.cents(t.cents())+" монет",
                "§7Время на выполнение: §f"+duration(t.duration()),
                "", "§7При остановке сервера или паузе заказов",
                "§7оставшееся время сохраняется.",
                "§7Выход из игры не останавливает таймер.",
                "§eНажмите, чтобы принять заказ", "§7Ваши заказы: /orders mine")));
        item.setItemMeta(meta);
        // Explicitly hide even default sword/tool attributes on modern Paper/Folia.
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY,TooltipDisplay.tooltipDisplay()
                .addHiddenComponents(DataComponentTypes.ATTRIBUTE_MODIFIERS).build());
        return item;
    }
    private static String task(Type type){return switch(type){
        case DELIVERY->"Доставить ресурсы";case MINING->"Добыть руду";
        case HUNTING->"Победить мобов";case FISHING->"Поймать рыбу";
    };}
    private static String duration(long millis){
        long seconds=Math.max(0,(millis+999)/1000);
        if(seconds<60)return seconds+" сек.";
        long minutes=(seconds+59)/60,days=minutes/1440,hours=minutes%1440/60;
        var parts=new ArrayList<String>();
        if(days>0)parts.add(days+" д.");if(hours>0)parts.add(hours+" ч.");
        if(minutes%60>0)parts.add(minutes%60+" мин.");return String.join(" ",parts);
    }
    private static Component button(String label,String command,String hint,NamedTextColor color){
        return Component.text("["+label+"]",color).clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hint,NamedTextColor.WHITE)));
    }
    private static boolean settled(RewardLedger.Entry entry){
        return entry!=null&&(entry.state()==RewardLedger.State.PAID||entry.state()==RewardLedger.State.VOID);
    }
    private void showMine(Player p){
        var mine=new ArrayList<>(orders.values().stream().filter(o->o.player().equals(p.getUniqueId())).toList());
        Collections.reverse(mine);
        p.sendMessage(Component.empty());
        p.sendMessage(Component.text("Ваши государственные заказы",NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        line(p,"Активных заказов",mine.stream().filter(this::open).count()+" из "+maximum,NamedTextColor.WHITE);
        line(p,"Остаток лимита наград за 24 ч",AuditTrail.cents(income.ledger().remaining(p.getUniqueId()))+" монет",NamedTextColor.GREEN);
        if(!active())p.sendMessage(Component.text("Заказы на паузе. Прогресс и оставшееся время сохранены.",NamedTextColor.YELLOW));
        var current=mine.stream().filter(o->o.state().equals("ACTIVE")&&!settled(income.ledger().get(o.id()))).limit(20).toList();
        if(current.isEmpty())p.sendMessage(Component.text("У вас пока нет заказов в работе.",NamedTextColor.GRAY));
        else current.forEach(o->showOrder(p,o));
        var history=mine.stream().filter(o->!o.state().equals("ACTIVE")||settled(income.ledger().get(o.id()))).limit(3).toList();
        if(!history.isEmpty()){
            p.sendMessage(Component.empty());p.sendMessage(Component.text("Недавние заказы",NamedTextColor.GOLD));
            history.forEach(o->{var payment=income.ledger().get(o.id());p.sendMessage(orderTitle(o).append(Component.text(" — "+status(o,payment),NamedTextColor.GRAY)));});
        }
        p.sendMessage(Component.empty());p.sendMessage(button("Доска заказов","/orders","Выбрать новый заказ",NamedTextColor.YELLOW));
    }
    private static void line(Player p,String label,String value,NamedTextColor color){
        p.sendMessage(Component.text("  "+label+": ",NamedTextColor.GRAY).append(Component.text(value,color)));
    }
    private static Component orderTitle(Order o){
        return Component.text(o.title(),NamedTextColor.WHITE).decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.copyToClipboard(o.id().toString()))
                .hoverEvent(HoverEvent.showText(Component.text("ID для администратора:\n"+o.id()+"\nНажмите, чтобы скопировать.",NamedTextColor.GRAY)));
    }
    private void showOrder(Player p,Order o){
        var payment=income.ledger().get(o.id());
        p.sendMessage(Component.empty());p.sendMessage(orderTitle(o));
        line(p,task(o.type()),names.value(o.target())+" × "+o.goal(),NamedTextColor.WHITE);
        if(payment==null&&o.type()==Type.DELIVERY){
            var sample=new ItemStack(Material.valueOf(o.target()));int total=0;
            for(var item:p.getInventory().getStorageContents())if(item!=null&&item.isSimilar(sample))total+=item.getAmount();
            line(p,"В инвентаре",total+" из "+o.goal(),total>=o.goal()?NamedTextColor.GREEN:NamedTextColor.YELLOW);
        }else if(payment==null)line(p,"Прогресс",o.progress()+" из "+o.goal(),o.progress()>=o.goal()?NamedTextColor.GREEN:NamedTextColor.YELLOW);
        line(p,"Награда",AuditTrail.cents(o.cents())+" монет",NamedTextColor.GREEN);
        line(p,"Статус",status(o,payment),NamedTextColor.WHITE);
        if(payment!=null||!open(o))return;
        if(o.progress()<o.goal())line(p,"Осталось",duration(o.remaining()),NamedTextColor.WHITE);
        Component actions=Component.text("  ");
        if(o.type()==Type.DELIVERY||o.progress()>=o.goal())actions=actions.append(button(
                o.type()==Type.DELIVERY?"Сдать ресурсы":"Получить награду","/orders claim "+o.id(),
                o.type()==Type.DELIVERY?"Сдать обычные ресурсы из инвентаря и получить награду":"Зачислить награду на личный счёт",NamedTextColor.GREEN)).append(Component.space());
        actions=actions.append(Component.text("[Отменить]",NamedTextColor.YELLOW)
                .clickEvent(ClickEvent.suggestCommand("/orders cancel "+o.id()))
                .hoverEvent(HoverEvent.showText(Component.text("Отмена без штрафа. Нажмите и отправьте подставленную команду для подтверждения.",NamedTextColor.WHITE))));
        p.sendMessage(actions);
    }
    private static String status(Order o,RewardLedger.Entry payment){
        if(payment!=null)return switch(payment.state()){
            case HELD->"Сдача ресурсов ожидает проверки";
            case PENDING->"Награда ожидает зачисления";
            case SENDING->"Награда зачисляется";
            case REVIEW->"Выплату проверяет администратор";
            case PAID->"Награда получена: "+AuditTrail.cents(payment.cents())+" монет";
            case VOID->"Выплата отменена при сверке";
        };
        return switch(o.state()){
            case "CANCELLED"->"Отменён без штрафа";case "EXPIRED"->"Срок истёк";
            default->o.progress()>=o.goal()?"Выполнен — получите награду":o.type()==Type.DELIVERY?"Ожидает сдачи ресурсов":"В работе";
        };
    }
    @EventHandler public void click(InventoryClickEvent e){if(!(e.getView().getTopInventory().getHolder() instanceof Board board))return;e.setCancelled(true);if(e.getWhoClicked() instanceof Player p&&board.slots().containsKey(e.getRawSlot()))synchronized(SuiteState.lock()){try{accept(p,board.slots().get(e.getRawSlot()));}catch(Exception ex){p.sendMessage("§c"+ex.getMessage());}}}
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Board)e.setCancelled(true);}
    @Override public List<String> onTabComplete(CommandSender s,Command c,String a,String[] args){synchronized(SuiteState.lock()){if(args.length==1)return List.of("board","accept","mine","claim","cancel","admin");if(args.length==2&&args[0].equals("accept"))return board().stream().map(Template::id).toList();if(args.length==2&&Set.of("claim","cancel").contains(args[0])&&s instanceof Player p)return orders.values().stream().filter(o->o.player().equals(p.getUniqueId())&&open(o)).map(o->o.id().toString()).toList();return List.of();}}
}
