package ru.neverland.mintcontracts.orders;

import java.io.*;
import java.util.*;
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
    private final List<Template> templates=new ArrayList<>();private final IncomeRuntime income;private final ActionGuard guard;
    private final int maximum,boardSize;private final long refresh;private boolean fault;
    public PublicOrders(JavaPlugin plugin)throws Exception{
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
        if(!active())throw new IllegalArgumentException("Заказы приостановлены; прогресс сохранён");
        if(orders.values().stream().filter(o->o.player().equals(p.getUniqueId())&&open(o)).count()>=maximum)throw new IllegalArgumentException("Одновременно разрешено "+maximum+" заказа");
        long cycle=System.currentTimeMillis()/refresh;if(orders.values().stream().anyMatch(o->o.player().equals(p.getUniqueId())&&o.template().equals(template)&&o.cycle()==cycle))throw new IllegalArgumentException("Этот заказ уже принят в текущем обновлении доски");
        Template t=board().stream().filter(v->v.id().equals(template)).findFirst().orElseThrow(()->new IllegalArgumentException("Заказ отсутствует на доске"));
        Order o=new Order(UUID.randomUUID(),p.getUniqueId(),t.id(),t.title(),t.type(),t.target(),t.amount(),0,t.cents(),t.duration(),cycle,"ACTIVE");orders.put(o.id(),o);save();p.sendMessage("Принят: "+t.title()+"; /orders mine");
    }
    private void progress(Player p,Type type,String target){if(!active()||!ActionGuard.survival(p)||!p.hasPermission("neverland.orders.use"))return;
        // One action advances one order, never three copies of the same objective.
        Order o=orders.values().stream().filter(v->v.player().equals(p.getUniqueId())&&open(v)&&v.type()==type&&v.target().equals(target)&&v.progress()<v.goal()).findFirst().orElse(null);if(o==null)return;
        o=o.progress(o.progress()+1);orders.put(o.id(),o);try{save();if(o.progress()==o.goal())p.sendMessage("Заказ выполнен: "+o.title()+". /orders claim "+o.id());}catch(IOException ex){plugin.getLogger().severe(ex.getMessage());}
    }
    private void claim(Player p,UUID id)throws Exception{
        if(!active())throw new IllegalArgumentException("Заказы приостановлены");Order o=orders.get(id);if(o==null||!o.player().equals(p.getUniqueId()))throw new IllegalArgumentException("Заказ не найден");
        var existing=income.ledger().get(id);if(existing!=null){recover(p);p.sendMessage("Начисление "+id+": "+income.ledger().get(id).state());return;}
        if(!open(o))throw new IllegalArgumentException("Заказ уже закрыт");
        if(o.type()==Type.DELIVERY){
            if(!com.palmergames.bukkit.towny.TownyEconomyHandler.isActive())throw new IllegalArgumentException("Экономика не подключена. Ресурсы остаются у вас.");
            var sample=new ItemStack(Material.valueOf(o.target()));int total=0;for(ItemStack item:p.getInventory().getStorageContents())if(item!=null&&item.isSimilar(sample))total+=item.getAmount();
            if(total<o.goal())throw new IllegalArgumentException("Нужно "+o.goal()+" обычных "+o.target()+"; именные и изменённые предметы не принимаются");
            income.ledger().reserve(id,p.getUniqueId(),o.cents(),"orders:"+o.template(),true);
            var receipt=PlayerSaveReceipt.before(p);int left=o.goal();ItemStack[] contents=p.getInventory().getStorageContents();
            for(int i=0;i<contents.length&&left>0;i++)if(contents[i]!=null&&contents[i].isSimilar(sample)){int n=Math.min(left,contents[i].getAmount());contents[i]=contents[i].clone();contents[i].setAmount(contents[i].getAmount()-n);left-=n;if(contents[i].getAmount()==0)contents[i]=null;}
            p.getInventory().setStorageContents(contents);p.getPersistentDataContainer().set(receiptKey(id),PersistentDataType.BYTE,(byte)1);receipt.save(p);
            income.ledger().release(id,"saved player inventory "+p.getUniqueId());
        }else{
            if(o.progress()<o.goal())throw new IllegalArgumentException("Заказ ещё не выполнен");income.ledger().reserve(id,p.getUniqueId(),o.cents(),"orders:"+o.template(),false);
        }
        p.sendMessage("Начисление сохранено: "+AuditTrail.cents(o.cents())+" монет. ID "+id+". Выплата поступит на личный счёт.");
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
    @EventHandler(priority=EventPriority.MONITOR) public void kill(EntityDeathEvent e){synchronized(SuiteState.lock()){if(guard.hunted(e))progress(e.getEntity().getKiller(),Type.HUNTING,e.getEntityType().name());}}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void fish(PlayerFishEvent e){synchronized(SuiteState.lock()){if(e.getState()==PlayerFishEvent.State.CAUGHT_FISH&&e.getCaught() instanceof Item item&&SuiteScheduler.owns(item)){Player player=e.getPlayer();String target=item.getItemStack().getType().name();SuiteScheduler.entity(plugin,player,()->{if(!e.isCancelled())progress(player,Type.FISHING,target);});}}}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){synchronized(SuiteState.lock()){
        try{
            if(args.length>0&&args[0].equalsIgnoreCase("admin")){if(!sender.isOp()&&!sender.hasPermission("neverland.orders.admin"))throw new IllegalArgumentException("Нет прав администратора");income.admin(sender,Arrays.copyOfRange(args,1,args.length));return true;}
            if(!(sender instanceof Player p))throw new IllegalArgumentException("Игровая доска: /orders. Сверка: /orders admin");if(!p.hasPermission("neverland.orders.use"))throw new IllegalArgumentException("Нет прав на заказы");
            String action=args.length==0?"board":args[0].toLowerCase(Locale.ROOT);
            switch(action){
                case "board" -> menu(p);
                case "accept" -> {if(args.length!=2)throw new IllegalArgumentException("/orders accept <заказ>");accept(p,args[1]);}
                case "mine" -> {p.sendMessage("Ваши заказы; доступно за 24ч: "+AuditTrail.cents(income.ledger().remaining(p.getUniqueId())));orders.values().stream().filter(o->o.player().equals(p.getUniqueId())&&o.state().equals("ACTIVE")).sorted(Comparator.comparingLong(Order::cycle).reversed()).limit(20).forEach(o->{var payment=income.ledger().get(o.id());p.sendMessage(o.id()+" | "+o.title()+" | "+o.progress()+"/"+o.goal()+" | "+(payment==null?o.remaining()/60000+" мин.; /orders claim <ID>":payment.state()));});}
                case "claim" -> {if(args.length!=2)throw new IllegalArgumentException("/orders claim <UUID>");claim(p,UUID.fromString(args[1]));}
                case "cancel" -> {if(args.length!=2)throw new IllegalArgumentException("/orders cancel <UUID>");var o=orders.get(UUID.fromString(args[1]));if(o==null||!o.player().equals(p.getUniqueId())||!open(o))throw new IllegalArgumentException("Активный заказ не найден");orders.put(o.id(),o.state("CANCELLED"));save();p.sendMessage("Отменён без штрафа.");}
                default -> p.sendMessage("/orders; /orders accept <заказ>; /orders mine; /orders claim <UUID>; /orders cancel <UUID>");
            }
        }catch(Exception ex){sender.sendMessage("§c"+ex.getMessage());}return true;
    }}
    private record Board(Map<Integer,String> slots) implements InventoryHolder{public Inventory getInventory(){return null;}}
    private void menu(Player p){Map<Integer,String> slots=new HashMap<>();var board=board();for(int i=0;i<board.size();i++)slots.put(i,board.get(i).id());Inventory inv=Bukkit.createInventory(new Board(Map.copyOf(slots)),54,"Государственные заказы");for(int i=0;i<board.size();i++){Template t=board.get(i);var item=new ItemStack(switch(t.type()){case DELIVERY->Material.CHEST;case MINING->Material.IRON_PICKAXE;case HUNTING->Material.IRON_SWORD;case FISHING->Material.FISHING_ROD;});var meta=item.getItemMeta();meta.setDisplayName("§6"+t.title());meta.setLore(List.of("§7"+t.target()+" × "+t.amount(),"§aНаграда: "+AuditTrail.cents(t.cents()),"§7Срок: "+t.duration()/60000+" мин. работы модуля","§eНажмите для принятия","§7Ваши заказы: /orders mine"));item.setItemMeta(meta);inv.setItem(i,item);}p.openInventory(inv);}
    @EventHandler public void click(InventoryClickEvent e){if(!(e.getView().getTopInventory().getHolder() instanceof Board board))return;e.setCancelled(true);if(e.getWhoClicked() instanceof Player p&&board.slots().containsKey(e.getRawSlot()))synchronized(SuiteState.lock()){try{accept(p,board.slots().get(e.getRawSlot()));}catch(Exception ex){p.sendMessage("§c"+ex.getMessage());}}}
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Board)e.setCancelled(true);}
    @Override public List<String> onTabComplete(CommandSender s,Command c,String a,String[] args){synchronized(SuiteState.lock()){if(args.length==1)return List.of("board","accept","mine","claim","cancel","admin");if(args.length==2&&args[0].equals("accept"))return board().stream().map(Template::id).toList();if(args.length==2&&Set.of("claim","cancel").contains(args[0])&&s instanceof Player p)return orders.values().stream().filter(o->o.player().equals(p.getUniqueId())&&open(o)).map(o->o.id().toString()).toList();return List.of();}}
}
