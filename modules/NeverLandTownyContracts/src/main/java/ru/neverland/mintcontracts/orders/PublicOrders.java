package ru.neverland.mintcontracts.orders;

import java.io.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
    public record Template(String id,String title,Type type,String target,int amount,long cents,long duration,OrderRarity rarity){
        public Template(String id,String title,Type type,String target,int amount,long cents,long duration){this(id,title,type,target,amount,cents,duration,OrderRarity.COMMON);}
    }
    public record Order(UUID id,UUID player,String template,String title,Type type,String target,int goal,int progress,long cents,long remaining,long cycle,String state,OrderRarity rarity,String boardDay){
        public Order(UUID id,UUID player,String template,String title,Type type,String target,int goal,int progress,long cents,long remaining,long cycle,String state){this(id,player,template,title,type,target,goal,progress,cents,remaining,cycle,state,OrderRarity.COMMON,"");}
        Order progress(int n){return new Order(id,player,template,title,type,target,goal,Math.min(goal,n),cents,remaining,cycle,state,rarity,boardDay);}
        Order time(long n){return new Order(id,player,template,title,type,target,goal,progress,cents,n,cycle,n==0&&progress<goal?"EXPIRED":state,rarity,boardDay);}
        Order state(String s){return new Order(id,player,template,title,type,target,goal,progress,cents,remaining,cycle,s,rarity,boardDay);}
    }
    private final JavaPlugin plugin;private final java.nio.file.Path file;private final Map<UUID,Order> orders=new LinkedHashMap<>();
    private final DailyOrderBoard dailyBoard;private final IncomeRuntime income;private final ActionGuard guard;private final RussianNames names;
    private final int maximum;private boolean fault;
    public PublicOrders(JavaPlugin plugin)throws Exception{
        this(plugin,new RussianNames(plugin));
    }
    public PublicOrders(JavaPlugin plugin,RussianNames names)throws Exception{
        this.names=names;
        this.plugin=plugin;file=plugin.getDataFolder().toPath().resolve("public-orders.yml");
        if(!plugin.getDataFolder().toPath().resolve("orders.yml").toFile().exists())plugin.saveResource("orders.yml",false);
        var configFile=plugin.getDataFolder().toPath().resolve("orders.yml");var cfg=SafeYaml.load(configFile);
        YamlConfiguration defaults;
        try(var reader=new InputStreamReader(Objects.requireNonNull(plugin.getResource("orders.yml")),StandardCharsets.UTF_8)){defaults=new YamlConfiguration();defaults.load(reader);}
        boolean migrated=OrderCatalog.migrate(cfg,defaults);
        maximum=positive(cfg,"maximum-active",3,3);int boardSize=positive(cfg,"board-size",OrderCatalog.DEFAULT_BOARD_SIZE,45);
        income=new IncomeRuntime(plugin,"order-payments",money(cfg,"limits.player-24h",OrderCatalog.DEFAULT_PLAYER_LIMIT),money(cfg,"limits.server-24h",100000),cfg.getBoolean("enabled",true));
        var templates=OrderCatalog.templates(cfg);OrderCatalog.validateItems(templates);
        long playerLimit=money(cfg,"limits.player-24h",OrderCatalog.DEFAULT_PLAYER_LIMIT),serverLimit=money(cfg,"limits.server-24h",100000);
        if(templates.stream().anyMatch(t->t.cents()>Math.min(playerLimit,serverLimit)))plugin.getLogger().warning("В каталоге есть награда выше лимита выплат: такой заказ нельзя оплатить до изменения limits");
        dailyBoard=new DailyOrderBoard(plugin.getDataFolder().toPath().resolve("orders-board.yml"),templates,boardSize,OrderCatalog.weights(cfg));
        load();OrderCatalog.validateItems(dailyBoard.snapshot().templates());
        if(migrated){OrderCatalog.saveMigration(configFile,cfg);plugin.getLogger().info("Каталог заказов обновлён: шаблонов "+templates.size()+", мест на следующей доске "+boardSize+", личный лимит за 24 ч. "+AuditTrail.cents(playerLimit)+"; резервная копия "+OrderCatalog.MIGRATION_BACKUP);}
        guard=new ActionGuard(plugin,"order-blocks.log");plugin.getServer().getPluginManager().registerEvents(guard,plugin);plugin.getServer().getPluginManager().registerEvents(this,plugin);
        var command=Objects.requireNonNull(plugin.getCommand("orders"));command.setExecutor(this);command.setTabCompleter(this);
        income.start();SuiteScheduler.get().runTaskTimer(plugin,this::tick,200,200);
        SuiteScheduler.get().runTaskTimer(plugin,()->{synchronized(SuiteState.lock()){if(!fault)try{boardSnapshot();}catch(IOException ex){plugin.getLogger().severe(ex.getMessage());}}},20,20);
    }
    private static int positive(org.bukkit.configuration.ConfigurationSection c,String k,int d,int max){int n=SafeYaml.intValue(c,k,d);if(n<=0||n>max)throw new IllegalArgumentException(k+": 1.."+max);return n;}
    public static long money(org.bukkit.configuration.ConfigurationSection c,String k,int d){double v=SafeYaml.doubleValue(c,k,d);if(v<=0||v>1_000_000||Math.abs(v*100-Math.rint(v*100))>0.00001)throw new IllegalArgumentException("Неверная сумма "+k);return Math.round(v*100);}
    private void load()throws IOException{
        var y=SafeYaml.load(file);int schema=SafeYaml.intValue(y,"schema",1);if(schema!=1&&schema!=2)throw new IOException("Неизвестная схема orders");var s=SafeYaml.section(y,"orders");
        if(s!=null)for(String key:s.getKeys(false)){var r=s.getConfigurationSection(key);var o=new Order(UUID.fromString(key),UUID.fromString(SafeYaml.text(r,"player")),SafeYaml.text(r,"template"),SafeYaml.text(r,"title"),Type.valueOf(SafeYaml.text(r,"type")),SafeYaml.text(r,"target"),SafeYaml.intValue(r,"goal"),SafeYaml.intValue(r,"progress"),SafeYaml.longValue(r,"cents"),SafeYaml.longValue(r,"remaining"),SafeYaml.longValue(r,"cycle"),SafeYaml.text(r,"state"),OrderRarity.valueOf(SafeYaml.stringValue(r,"rarity","COMMON")),SafeYaml.stringValue(r,"board-day",""));if(!o.boardDay().isEmpty())LocalDate.parse(o.boardDay());if(o.goal()<=0||o.progress()<0||o.progress()>o.goal()||o.cents()<=0||o.remaining()<0||!Set.of("ACTIVE","CANCELLED","EXPIRED").contains(o.state()))throw new IOException("Повреждён заказ "+key);orders.put(o.id(),o);}
        AtomicFiles.loaded(file);
    }
    private void save()throws IOException{
        var y=new YamlConfiguration();y.set("schema",2);
        for(var o:orders.values()){String p="orders."+o.id()+".";y.set(p+"player",o.player().toString());y.set(p+"template",o.template());y.set(p+"title",o.title());y.set(p+"type",o.type().name());y.set(p+"target",o.target());y.set(p+"goal",o.goal());y.set(p+"progress",o.progress());y.set(p+"cents",o.cents());y.set(p+"remaining",o.remaining());y.set(p+"cycle",o.cycle());y.set(p+"state",o.state());y.set(p+"rarity",o.rarity().name());y.set(p+"board-day",o.boardDay());}
        try{AtomicFiles.write(file,y::saveToString);}catch(IOException ex){fault=true;throw ex;}
    }
    private boolean active(){return !fault&&income.enabled();}
    private boolean open(Order o){return o.state().equals("ACTIVE")&&income.ledger().get(o.id())==null;}
    private DailyOrderBoard.Snapshot boardSnapshot()throws IOException{try{return dailyBoard.snapshot();}catch(IOException ex){fault=true;throw ex;}}
    private Set<String> paidTemplates(UUID player,LocalDate day){
        Set<String> paid=new HashSet<>();String boardDay=day.toString();
        for(Order order:orders.values())if(order.player().equals(player)&&order.boardDay().equals(boardDay)){
            var payment=income.ledger().get(order.id());
            if(payment!=null&&payment.state()==RewardLedger.State.PAID)paid.add(order.template());
        }
        return paid;
    }
    private List<Template> visibleTemplates(UUID player,DailyOrderBoard.Snapshot snapshot){
        var paid=paidTemplates(player,snapshot.day());
        return snapshot.templates().stream().filter(t->!paid.contains(t.id())).toList();
    }
    private void tick(){synchronized(SuiteState.lock()){if(!active())return;boolean changed=false;
        for(Order o:List.copyOf(orders.values()))if(open(o)&&o.progress()<o.goal()){orders.put(o.id(),o.time(Math.max(0,o.remaining()-10000)));changed=true;}
        if(changed)try{save();}catch(IOException e){plugin.getLogger().severe(e.getMessage());}
    }}
    private void accept(Player p,String template)throws IOException{
        accept(p,template,null);
    }
    private void accept(Player p,String template,LocalDate displayedDay)throws IOException{
        if(!p.hasPermission("neverland.orders.use"))throw new IllegalArgumentException("Нет прав на заказы");
        if(!active())throw new IllegalArgumentException("Заказы приостановлены; прогресс сохранён");
        if(orders.values().stream().filter(o->o.player().equals(p.getUniqueId())&&open(o)).count()>=maximum)throw new IllegalArgumentException("Одновременно разрешено "+maximum+" заказа");
        var board=boardSnapshot();
        if(displayedDay!=null&&!displayedDay.equals(board.day()))throw new IllegalArgumentException("Доска обновилась. Откройте /orders и выберите заказ заново.");
        if(orders.values().stream().anyMatch(o->o.player().equals(p.getUniqueId())&&o.template().equals(template)&&o.boardDay().equals(board.day().toString())))throw new IllegalArgumentException("Этот заказ уже принят сегодня. Повторно взять его можно на новой доске после 00:00 МСК.");
        Template t=board.templates().stream().filter(v->v.id().equals(template)).findFirst().orElseThrow(()->new IllegalArgumentException("Заказ отсутствует на доске"));
        Order o=new Order(UUID.randomUUID(),p.getUniqueId(),t.id(),t.title(),t.type(),t.target(),t.amount(),0,t.cents(),t.duration(),board.day().toEpochDay(),"ACTIVE",t.rarity(),board.day().toString());orders.put(o.id(),o);save();p.sendMessage(Component.text("Заказ принят: ",NamedTextColor.GREEN).append(Component.text(t.title(),NamedTextColor.WHITE)));showOrder(p,o);p.sendMessage(button("Мои заказы","/orders mine","Показать прогресс и действия по заказам",NamedTextColor.YELLOW));
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
        Player player=guard.hunter(e);if(player==null)return;String target=e.getEntityType().name();
        HuntingCredit.afterDeath(plugin,e,player,()->progress(player,Type.HUNTING,target));
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
    private record Board(LocalDate day,Map<Integer,String> slots) implements InventoryHolder{public Inventory getInventory(){return null;}}
    private void menu(Player p)throws IOException{
        Map<Integer,String> slots=new HashMap<>();var snapshot=boardSnapshot();var board=visibleTemplates(p.getUniqueId(),snapshot);
        for(int i=0;i<board.size();i++)slots.put(i,board.get(i).id());
        var holder=new Board(snapshot.day(),slots);
        Inventory inv=MenuStyle.inventory(plugin,holder,54,"Государственные заказы");
        for(int i=0;i<board.size();i++)inv.setItem(i,icon(board.get(i)));
        inv.setItem(45,information(Material.BOOK,"§eМои заказы",List.of("§7Прогресс, сдача ресурсов и награды","§eНажмите, чтобы посмотреть")));
        inv.setItem(49,information(Material.CLOCK,"§6Обновление: каждый день в 00:00 МСК",List.of(
                "§7Следующее: §f"+snapshot.day().plusDays(1).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))+" в 00:00 МСК",
                "§7Принятые заказы сохраняют прогресс,","§7награду и оставшееся время.","§7Один заказ с доски — один раз за день.",
                "§7После получения награды заказ","§7скрывается с вашей доски.")));
        inv.setItem(53,rewardBudget(p.getUniqueId()));
        emptyBoard(inv,holder);
        p.openInventory(inv);
        refreshMenu(p,inv,holder);
    }
    private ItemStack rewardBudget(UUID player){
        return information(Material.EMERALD,"§aЛимит наград",List.of(
                "§7Осталось за последние 24 ч.: §a"+AuditTrail.cents(income.ledger().remaining(player))+" монет",
                "§7Лимит восстанавливается постепенно,","§7через 24 часа после каждой выплаты.","§7Обновление доски его не сбрасывает.","§7Одновременно: не более "+maximum+" заказов"));
    }
    private static void emptyBoard(Inventory inv,Board board){
        if(board.slots().isEmpty())inv.setItem(22,information(Material.PAPER,"§aВсе заказы с этой доски выполнены",List.of(
                "§7Новая подборка появится в 00:00 МСК.","§7Затем откройте /orders заново.","§7История наград: /orders mine")));
    }
    private void refreshMenu(Player player,Inventory inv,Board board){
        if(board.slots().isEmpty())return;
        // Payouts arrive asynchronously. Touch this player's open inventory only on their entity scheduler.
        SuiteScheduler.entityLater(plugin,player,()->{
            if(!player.isOnline()||player.getOpenInventory().getTopInventory().getHolder()!=board)return;
            var paid=paidTemplates(player.getUniqueId(),board.day());
            boolean changed=board.slots().entrySet().removeIf(entry->{
                if(!paid.contains(entry.getValue()))return false;
                inv.setItem(entry.getKey(),null);return true;
            });
            if(changed){emptyBoard(inv,board);inv.setItem(53,rewardBudget(player.getUniqueId()));}
            // Keep remaining icons in place so a pending click cannot select a different order.
            refreshMenu(player,inv,board);
        },null,20);
    }
    private static ItemStack information(Material material,String title,List<String> lore){var item=new ItemStack(material);var meta=item.getItemMeta();meta.setDisplayName(MenuStyle.nameLegacy(title));meta.setLore(MenuStyle.loreStrings(lore));item.setItemMeta(meta);return item;}
    private static Material iconMaterial(Type type,OrderRarity rarity){
        return switch(type){
            case HUNTING -> switch(rarity){
                case COMMON->Material.WOODEN_SWORD;case UNCOMMON->Material.STONE_SWORD;
                case RARE->Material.IRON_SWORD;case EPIC->Material.DIAMOND_SWORD;case LEGENDARY->Material.NETHERITE_SWORD;
            };
            case MINING -> switch(rarity){
                case COMMON->Material.WOODEN_PICKAXE;case UNCOMMON->Material.STONE_PICKAXE;
                case RARE->Material.IRON_PICKAXE;case EPIC->Material.DIAMOND_PICKAXE;case LEGENDARY->Material.NETHERITE_PICKAXE;
            };
            case DELIVERY -> switch(rarity){
                case COMMON->Material.WHITE_SHULKER_BOX;case UNCOMMON->Material.LIME_SHULKER_BOX;
                case RARE->Material.LIGHT_BLUE_SHULKER_BOX;case EPIC->Material.PURPLE_SHULKER_BOX;case LEGENDARY->Material.ORANGE_SHULKER_BOX;
            };
            case FISHING -> switch(rarity){
                case COMMON->Material.COD;case UNCOMMON->Material.SALMON;
                case RARE->Material.PUFFERFISH;case EPIC->Material.TROPICAL_FISH;case LEGENDARY->Material.HEART_OF_THE_SEA;
            };
        };
    }
    private ItemStack icon(Template t){
        var item=new ItemStack(iconMaterial(t.type(),t.rarity()));
        var meta=item.getItemMeta();
        meta.setEnchantmentGlintOverride(t.rarity()==OrderRarity.EPIC||t.rarity()==OrderRarity.LEGENDARY);
        meta.setDisplayName(MenuStyle.nameLegacy(t.rarity().legacyColor+t.title()));
        meta.setLore(MenuStyle.loreStrings(List.of(
                "§7Редкость: "+t.rarity().legacyColor+t.rarity().label,
                "§7Сложность: §f"+t.rarity().difficulty,
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
        return Component.text(o.title(),o.rarity().color).decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.copyToClipboard(o.id().toString()))
                .hoverEvent(HoverEvent.showText(Component.text("ID для администратора:\n"+o.id()+"\nНажмите, чтобы скопировать.",NamedTextColor.GRAY)));
    }
    private void showOrder(Player p,Order o){
        var payment=income.ledger().get(o.id());
        p.sendMessage(Component.empty());p.sendMessage(orderTitle(o));
        line(p,"Редкость",o.rarity().label+" · сложность: "+o.rarity().difficulty,o.rarity().color);
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
    @EventHandler public void click(InventoryClickEvent e){if(!(e.getView().getTopInventory().getHolder() instanceof Board board))return;e.setCancelled(true);if(e.getWhoClicked() instanceof Player p)synchronized(SuiteState.lock()){try{if(e.getRawSlot()==45)SuiteScheduler.entity(plugin,p,()->{p.closeInventory();showMine(p);});else if(board.slots().containsKey(e.getRawSlot()))accept(p,board.slots().get(e.getRawSlot()),board.day());}catch(Exception ex){p.sendMessage("§c"+ex.getMessage());}}}
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Board)e.setCancelled(true);}
    @Override public List<String> onTabComplete(CommandSender s,Command c,String a,String[] args){synchronized(SuiteState.lock()){try{if(args.length==1)return List.of("board","accept","mine","claim","cancel","admin");if(args.length==2&&args[0].equals("accept")&&s instanceof Player p)return visibleTemplates(p.getUniqueId(),boardSnapshot()).stream().map(Template::id).toList();if(args.length==2&&Set.of("claim","cancel").contains(args[0])&&s instanceof Player p)return orders.values().stream().filter(o->o.player().equals(p.getUniqueId())&&open(o)).map(o->o.id().toString()).toList();}catch(IOException ex){plugin.getLogger().severe(ex.getMessage());}return List.of();}}
}
