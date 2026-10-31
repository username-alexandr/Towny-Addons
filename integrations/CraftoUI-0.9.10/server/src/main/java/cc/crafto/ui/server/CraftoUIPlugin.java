package cc.crafto.ui.server;

import cc.crafto.ui.*;
import com.google.gson.*;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static cc.crafto.ui.Protocol.*;

public final class CraftoUIPlugin extends JavaPlugin implements Listener, CraftoUIApi {
    private volatile ConfigLoader.Catalog catalog;
    private final ConcurrentMap<UUID,Connection> connections=new ConcurrentHashMap<>();
    private final ConcurrentMap<String,OwnedMenu> externalMenus=new ConcurrentHashMap<>();
    private final ConcurrentMap<String,OwnedAction> externalActions=new ConcurrentHashMap<>();
    private final ConcurrentMap<String,OwnedProvider> providers=new ConcurrentHashMap<>();
    private final AtomicLong revision=new AtomicLong(1);
    private final AtomicBoolean reloading=new AtomicBoolean();
    private volatile boolean stopping;
    private ContractHudBridge contractHudBridge;
    private CityDataBridge cityDataBridge;
    private CivicWorkbench civicWorkbench;
    private final CommandWorkbench commandWorkbench=new CommandWorkbench(this);
    private static final long SESSION_NANOS=TimeUnit.MINUTES.toNanos(15);
    private record OwnedMenu(Plugin owner,JsonObject menu){}
    private record OwnedAction(Plugin owner,String permission,ActionHandler handler){}
    private record OwnedProvider(Plugin owner,DataProvider provider){}
    private record QuickTarget(String menu,String component,String link){}
    private record ReturnTarget(String screen,org.bukkit.inventory.Inventory inventory,String module,String holder,String title,boolean reusable){
        String key(){return inventory==null?"screen:"+screen:"native:"+module+":"+holder+":"+title;}
    }
    private static final class Connection {
        final Player player;
        final RateGate gate=new RateGate(15,8,System.nanoTime());
        volatile boolean ready;
        volatile boolean nativeMenus,contractsHud,navigation,personal,commands,cityScreen,civic,buildingPreview,placementControls;
        final AtomicBoolean placementPending=new AtomicBoolean();String placementState="";long placementSent;
        final CivicWorkbench.State civicState=new CivicWorkbench.State();
        final CommandWorkbench.State commandState=new CommandWorkbench.State();
        String directoryToken="";long directoryRevision,directoryAt,directoryRequestAt;
        final Map<String,QuickTarget> directory=new LinkedHashMap<>();
        final AtomicBoolean cityPending=new AtomicBoolean();
        long cityAt;boolean summarySent;CitySnapshot lastCity;
        java.util.List<String> summary=java.util.List.of();
        final MenuHistory<ReturnTarget> history=new MenuHistory<>();
        String navigationToken="";
        long navigationAt,pendingBackUntil;
        ReturnTarget pendingBackTarget;
        final AtomicBoolean hudPending=new AtomicBoolean();
        String lastHud="";
        long hudSent;
        int hudEntries;
        long helloAt;
        Session session;
        Connection(Player p){player=p;}
    }
    private static final class Session {
        final String token=UUID.randomUUID().toString(),menu;
        final long revision,created=System.nanoTime();
        long sequence;
        final Map<String,String> values=new HashMap<>();
        ScheduledTask refresh;
        boolean providerPending,townMember;String visibleComponents="";
        Session(String m,long r){menu=m;revision=r;}
    }
    @Override public void onEnable() {
        try {
            for(String file:List.of("command-ui.json","quick-actions.json","actions.json","menus/main.json","menus/profile.json","menus/help.yml","themes/classic.json","themes/dark.json","addons.json","menus/hub.json","menus/town.json","menus/economy.json","menus/adventures.json","menus/society.json","menus/management.json","menus/development.json","menus/security.json")) {
                if(!Files.exists(getDataFolder().toPath().resolve(file)))saveResource(file,false);
            }
            DefaultMenuMigration.apply(getDataFolder().toPath());
            catalog=ConfigLoader.load(getDataFolder().toPath());
        }catch(Exception e){getLogger().log(java.util.logging.Level.SEVERE,"CraftoUI configuration failed",e);getServer().getPluginManager().disablePlugin(this);return;}
        getServer().getMessenger().registerIncomingPluginChannel(this,CHANNEL,(ch,p,data)->receive(p,data));
        getServer().getMessenger().registerOutgoingPluginChannel(this,CHANNEL);
        getServer().getMessenger().registerOutgoingPluginChannel(this,"BungeeCord");
        getServer().getPluginManager().registerEvents(this,this);
        getServer().getPluginManager().registerEvents(new AddonBridge(this),this);
        getServer().getServicesManager().register(CraftoUIApi.class,this,this,ServicePriority.Normal);
        Objects.requireNonNull(getCommand("craftoui")).setExecutor(this::command);
        Objects.requireNonNull(getCommand("craftoui")).setTabCompleter((s,c,a,args)->args.length==1?List.of("open","actions","close","status","reload"):List.of());
        contractHudBridge=new ContractHudBridge(this);
        cityDataBridge=new CityDataBridge(this);
        civicWorkbench=new CivicWorkbench(this);civicWorkbench.start();
        getServer().getAsyncScheduler().runAtFixedRate(this,t->connections.values().forEach(c->{if(c.ready&&c.personal)own(c.player,()->requestCity(c));}),2,10,TimeUnit.SECONDS);
        getServer().getGlobalRegionScheduler().runAtFixedRate(this,t->connections.values().forEach(c->{
            if(c.ready&&c.placementControls&&c.placementPending.compareAndSet(false,true)){
                var task=c.player.getScheduler().run(this,ignored->{try{if(connections.get(c.player.getUniqueId())==c)placementState(c);}finally{c.placementPending.set(false);}},()->c.placementPending.set(false));
                if(task==null)c.placementPending.set(false);
            }
        }),5,5);
        getLogger().info("CraftoUI enabled; optional client, protocol "+VERSION);
    }
    @Override public void onDisable() {
        stopping=true;
        if(civicWorkbench!=null)civicWorkbench.stop();
        // Entity tasks are cancelled by the platform when their owning plugin disables.
        connections.clear(); externalMenus.clear();externalActions.clear();providers.clear();
        getServer().getServicesManager().unregisterAll(this);
        getServer().getAsyncScheduler().cancelTasks(this);
        getServer().getGlobalRegionScheduler().cancelTasks(this);
    }
    @EventHandler public void join(PlayerJoinEvent e){connections.put(e.getPlayer().getUniqueId(),new Connection(e.getPlayer()));}
    // /orders remains owned by Contracts. Its inventory is rendered by AddonBridge,
    // exactly like the same command opened through a configured menu or search.
    @EventHandler public void quit(PlayerQuitEvent e){if(civicWorkbench!=null)civicWorkbench.departed(e.getPlayer().getUniqueId());Connection c=connections.remove(e.getPlayer().getUniqueId());if(c!=null){clear(c);if(cityDataBridge!=null&&c.lastCity!=null)cityDataBridge.departed(e.getPlayer().getUniqueId(),c.lastCity);}}
    @EventHandler public void pluginDisabled(PluginDisableEvent e){if(e.getPlugin()!=this)unregister(e.getPlugin());}
    private void own(Player p,Runnable run){if(!stopping)p.getScheduler().run(this,t->{if(!stopping&&p.isOnline())run.run();},null);}
    private void reply(CommandSender sender,String text) {
        if(sender instanceof Player p)own(p,()->p.sendMessage(text));
        else getServer().getGlobalRegionScheduler().execute(this,()->sender.sendMessage(text));
    }
    private boolean command(CommandSender sender,Command command,String label,String[] args) {
        String sub=args.length==0?"open":args[0].toLowerCase(Locale.ROOT);
        if(sub.equals("reload")) {
            if(!sender.hasPermission("craftoui.admin.reload")){sender.sendMessage("Недостаточно прав.");return true;}
            if(!reloading.compareAndSet(false,true)){sender.sendMessage("Перезагрузка уже выполняется.");return true;}
            getServer().getAsyncScheduler().runNow(this,t->{
                try{var loaded=ConfigLoader.load(getDataFolder().toPath());if(stopping)return;catalog=loaded;invalidate();reply(sender,"CraftoUI: конфигурации обновлены.");}
                catch(Exception e){getLogger().warning("Reload rejected: "+e.getMessage());reply(sender,"Ошибка конфигурации. Сохранена предыдущая версия; подробности в консоли.");}
                finally{reloading.set(false);}
            });return true;
        }
        if(!(sender instanceof Player p)){sender.sendMessage("Используйте команду в игре; из консоли доступно /craftoui reload.");return true;}
        switch(sub) {
            case "open" -> openMenu(p,args.length>1?args[1]:(catalog.menus().containsKey("hub")?"hub":"main"));
            case "actions" -> own(p,()->{Connection c=connections.get(p.getUniqueId());if(c!=null&&c.ready&&c.commands)send(p,Kind.COMMANDS,object("op","show"));else p.sendMessage("Для действий в UI нужен CraftoUI 0.6.0. Обычные команды аддонов доступны без мода.");});
            case "close" -> closeMenu(p);
            case "status" -> own(p,()->status(p));
            default -> sender.sendMessage("/craftoui [open <меню>|close|status|reload]");
        }return true;
    }
    AddonCatalog addonCatalog(){return catalog.addons();}
    private void status(Player p){
        p.sendMessage("CraftoUI server "+getDescription().getVersion());
        Connection c=connections.get(p.getUniqueId());
        if(c==null||!c.ready){p.sendMessage("CraftoUI: клиентский мод не обнаружен. Проверьте профиль лаунчера и переподключитесь.");return;}
        p.sendMessage("CraftoUI: клиент подключён.");
        if(!c.contractsHud){p.sendMessage("Панель контрактов не поддерживается этим клиентом. Установите клиент CraftoUI 0.3.0 или новее.");return;}
        p.sendMessage("Клиент поддерживает панель контрактов.");
        var adapter=contractHudBridge.status();p.sendMessage(adapter.message());
        for(String permission:List.of("craftoui.use","craftoui.contracts","neverland.orders.use")){
            if(!p.hasPermission(permission)){p.sendMessage("Панель недоступна: нет права "+permission+".");return;}
        }
        if(!adapter.ready())return;
        if(c.hudSent==0){p.sendMessage("Ожидается первая отправка заданий.");return;}
        if(System.nanoTime()-c.hudSent>TimeUnit.SECONDS.toNanos(20)){p.sendMessage("Данные панели давно не отправлялись; проверьте журнал сервера.");return;}
        if(c.hudEntries==0){p.sendMessage("Активных личных заказов для панели нет. Проверьте /orders mine.");return;}
        p.sendMessage("Отправлено заданий на панель: "+c.hudEntries+". Закройте меню; проверьте видимость панели в настройках CraftoUI и F1.");
    }
    JsonObject theme(String id){return catalog.themes().getOrDefault(id,catalog.themes().get("dark")).deepCopy();}
    boolean hasBuildingPreview(UUID id){Connection c=connections.get(id);return c!=null&&c.ready&&c.buildingPreview;}
    void buildingPreview(Player player,JsonObject body){if(hasBuildingPreview(player.getUniqueId()))send(player,Kind.BUILD_PREVIEW,body);}
    void resumeTownCreation(Player player,TownyConfirmBridge.Pending pending){
        Connection c=connections.get(player.getUniqueId());if(c==null||!c.ready||!c.commands)return;
        try{JsonObject message=commandWorkbench.resumedTownCreation(player,c.commandState,catalog.commands(),pending);
            if(message!=null){message.add("theme",theme(catalog.addons().theme()));send(player,Kind.COMMANDS,message);}
        }catch(Exception error){getLogger().log(java.util.logging.Level.WARNING,"Town creation confirmation could not resume",error);}
    }
    void styleConfirmed(Player player){
        Connection c=connections.get(player.getUniqueId());
        if(c!=null&&c.commandState.awaitingStyle!=null)c.commandState.styleConfirmedUntil=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
    }
    boolean hasNativeMenus(UUID id){Connection c=connections.get(id);return c!=null&&c.ready&&c.nativeMenus;}
    void nativeMenu(Player p,JsonObject body,org.bukkit.inventory.Inventory inventory,AddonCatalog.Entry entry,boolean editable){
        Connection c=connections.get(p.getUniqueId());if(c==null||!c.ready||!c.nativeMenus)return;
        if(c.navigation){
            var target=new ReturnTarget("",inventory,entry.id(),inventory.getHolder(false).getClass().getName(),str(body,"title",""),NativeBack.reusable(inventory.getHolder(false).getClass().getName(),editable));
            if(c.pendingBackUntil>System.nanoTime()){
                ReturnTarget expected=c.pendingBackTarget;c.history.back();
                c.pendingBackUntil=0;c.pendingBackTarget=null;
                // Some addons hard-code Back to their root. Keep the actual previous screen instead.
                if(expected!=null&&expected.inventory()!=null&&expected.reusable()&&!expected.key().equals(target.key())&&canReturn(p,expected)){
                    p.openInventory(expected.inventory());return;
                }
            }
            c.pendingBackUntil=0;c.pendingBackTarget=null;
            navigate(c,target,body);
            body.addProperty("backLabel",c.history.previous()!=null||NativeBack.slot(inventory,entry.plugin(),editable)>=0?"Назад":"В игру");
        }
        clear(c);send(p,Kind.NATIVE_MENU,body);
    }
    void nativePresentation(Player player,JsonObject body){if(hasNativeMenus(player.getUniqueId()))send(player,Kind.NATIVE_MENU,body);}
    void nativeClosed(Player p,org.bukkit.inventory.Inventory inventory){
        Connection c=connections.get(p.getUniqueId());if(c==null)return;
        p.getScheduler().run(this,t->{
            if(connections.get(p.getUniqueId())!=c)return;
            ReturnTarget current=c.history.current();
            if(current!=null&&current.inventory()==inventory&&p.getOpenInventory().getTopInventory().getType()==org.bukkit.event.inventory.InventoryType.CRAFTING)resetNavigation(c);
        },null);
    }
    private void resetNavigation(Connection c){c.history.clear();c.navigationToken="";c.pendingBackUntil=0;c.pendingBackTarget=null;}
    private void navigate(Connection c,ReturnTarget target,JsonObject body){
        if(!c.navigation)return;
        if(System.nanoTime()-c.navigationAt>SESSION_NANOS)resetNavigation(c);
        c.history.visit(target.key(),target);c.navigationAt=System.nanoTime();c.navigationToken=UUID.randomUUID().toString();
        body.addProperty("navigation",c.navigationToken);body.addProperty("backLabel",c.history.previous()==null?"В игру":"Назад");
    }
    void civicAnchor(Player p){Connection c=connections.get(p.getUniqueId());if(c!=null&&c.civic)navigate(c,new ReturnTarget("__civic",null,"","","",false),new JsonObject());}
    boolean civicOrdersAvailable(Player p){
        Connection c=connections.get(p.getUniqueId());
        if(c==null||!c.civic||!p.hasPermission("craftoui.use")||!p.hasPermission("craftoui.city")||!p.hasPermission("neverland.orders.use"))return false;
        var command=getServer().getPluginCommand("neverlandtownycontracts:orders");
        if(command==null||!command.getPlugin().isEnabled()||!command.getPlugin().getName().equals("NeverLandTownyContracts"))return false;
        try{var api=command.getExecutor().getClass();api.getMethod("uiSnapshot",Player.class);api.getMethod("uiExecute",Player.class,String.class,String.class,String.class);return true;}
        catch(ReflectiveOperationException e){return false;}
    }
    private boolean canReturn(Player player,ReturnTarget target){
        if(target.screen().equals("__civic"))return player.hasPermission("craftoui.use")&&player.hasPermission("craftoui.city");
        if(target.inventory()==null){JsonObject definition=menu(target.screen());return definition!=null&&allowed(player,definition);}
        // Builds checks editor access at its command entry, so cached editor returns need the same gate.
        if(target.holder().startsWith("ru.neverland.townybuilds.gui.EditorManager$")&&!player.hasPermission("neverlandtownybuilds.admin.editor"))return false;
        var entry=catalog.addons().entries().get(target.module());
        return entry!=null&&catalog.addons().enabled()&&getServer().getPluginManager().isPluginEnabled(entry.plugin())&&entry.holders().contains(target.holder())&&player.hasPermission(entry.holderPermission(target.holder()));
    }
    private void goBack(Connection c,String token){
        if(!c.navigation||token.isEmpty()||!token.equals(c.navigationToken)||System.nanoTime()-c.navigationAt>SESSION_NANOS||!c.player.hasPermission("craftoui.use"))return;
        ReturnTarget current=c.history.current();if(current==null)return;
        Player p=c.player;
        if(current.inventory()!=null){
            if(p.getOpenInventory().getTopInventory()!=current.inventory()||!canReturn(p,current))return;
            if(!p.getItemOnCursor().isEmpty()){p.sendMessage("Сначала положите предмет с курсора в ячейку.");return;}
        }else if(c.session==null||!c.session.menu.equals(current.screen()))return;
        ReturnTarget previous=c.history.previous();
        if(previous!=null&&!canReturn(p,previous)){p.sendMessage("Предыдущее меню больше недоступно.");return;}
        if(current.inventory()!=null&&(previous==null||previous.inventory()!=null)){
            var entry=catalog.addons().entries().get(current.module());
            boolean editable=entry.inventoryHolders().contains(current.holder())||entry.inventoryTitleWords().stream().anyMatch(w->current.title().toLowerCase(Locale.ROOT).contains(w));
            int slot=NativeBack.slot(current.inventory(),entry.plugin(),editable);
            if(slot>=0){
                if(c.pendingBackUntil>System.nanoTime())return;
                c.pendingBackUntil=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
                c.pendingBackTarget=previous;
                send(p,Kind.BACK,object("navigation",c.navigationToken,"slot",slot));return;
            }
        }
        if(previous==null){resetNavigation(c);if(current.inventory()!=null)p.closeInventory();else closeNow(c,"");return;}
        if(previous.inventory()!=null&&!previous.reusable()){
            var entry=catalog.addons().entries().get(previous.module());String reopen=entry==null?"":NativeBack.reopen(entry.plugin(),previous.holder());
            if(reopen.isEmpty()){p.sendMessage("Предыдущее окно нужно открыть заново через меню аддона.");return;}
            String[] parts=reopen.split(" ",2);String command=entry.plugin().toLowerCase(Locale.ROOT)+":"+parts[0];
            var registered=getServer().getPluginCommand(command);
            if(registered==null||!registered.getPlugin().getName().equals(entry.plugin())||!registered.testPermissionSilent(p))return;
            c.history.back();c.navigationToken="";clear(c);p.performCommand(command+(parts.length==1?"":" "+parts[1]));return;
        }
        c.history.back();c.navigationToken="";
        if(previous.screen().equals("__civic")){p.closeInventory();send(p,Kind.CIVIC,object("op","resume"));}
        else if(previous.inventory()==null)openNow(c,previous.screen());
        else {clear(c);p.openInventory(previous.inventory());}
    }
    Set<UUID> contractHudAudience(){
        if(stopping)return Set.of();
        Set<UUID> ids=new HashSet<>();connections.forEach((id,c)->{if(c.ready&&c.contractsHud)ids.add(id);});return Set.copyOf(ids);
    }
    void contractHudUpdate(Map<UUID,List<PublicOrderReader.Order>> state){
        if(stopping)return;
        connections.forEach((id,c)->{
            if(!c.ready||!c.contractsHud||!c.hudPending.compareAndSet(false,true))return;
            Runnable retired=()->c.hudPending.set(false);
            try{
                var task=c.player.getScheduler().run(this,t->{
                    try{
                        if(stopping||connections.get(id)!=c||!c.ready||!c.contractsHud||!c.player.isOnline())return;
                        List<ContractHudState.Entry> entries=new ArrayList<>();
                        if(c.player.hasPermission("craftoui.use")&&c.player.hasPermission("craftoui.contracts")&&c.player.hasPermission("neverland.orders.use")){
                            for(var order:state.getOrDefault(id,List.of())){
                                int progress=order.progress();boolean inventory=!order.deliveryMaterial().isEmpty();
                                var status=order.status();
                                if(inventory&&status==ContractHudState.Status.ACTIVE){
                                    Material material=Material.matchMaterial(order.deliveryMaterial());if(material==null||!material.isItem())continue;
                                    var sample=new org.bukkit.inventory.ItemStack(material);long total=0;
                                    for(var item:c.player.getInventory().getStorageContents())if(item!=null&&item.isSimilar(sample))total+=item.getAmount();
                                    progress=(int)Math.min(order.goal(),total);if(progress>=order.goal())status=ContractHudState.Status.READY;
                                }
                                String targetKey;
                                if(order.type().equals("HUNTING")){
                                    try{targetKey=org.bukkit.entity.EntityType.valueOf(order.target()).translationKey();}catch(IllegalArgumentException e){continue;}
                                }else {Material target=Material.matchMaterial(order.target());if(target==null)continue;targetKey=target.translationKey();}
                                entries.add(new ContractHudState.Entry(order.id(),order.title(),progress,order.goal(),inventory,status,targetKey,order.type()));
                            }
                        }
                        JsonObject body=new ContractHudState(entries).json();String value=JSON.toJson(body);long now=System.nanoTime();
                        if(!value.equals(c.lastHud)||now-c.hudSent>=TimeUnit.SECONDS.toNanos(8)){
                            send(c.player,Kind.CONTRACTS,body);c.lastHud=value;c.hudSent=now;c.hudEntries=entries.size();
                        }
                    }finally{retired.run();}
                },retired);
                if(task==null)retired.run();
            }catch(IllegalStateException e){retired.run();}
        });
    }
    private void receive(Player p,byte[] bytes) {
        Connection c=connections.get(p.getUniqueId());
        if(c==null||bytes.length>MAX_BYTES||!c.gate.allow(System.nanoTime()))return;
        final Message m;
        try{m=decode(bytes);}catch(IllegalArgumentException ignored){return;}
        own(p,()->{if(connections.get(p.getUniqueId())==c)try{handle(c,m);}catch(Exception e){getLogger().warning("Rejected UI request: "+e.getClass().getSimpleName());}});
    }
    private void handle(Connection c,Message m) throws IOException {
        Player p=c.player;JsonObject b=m.body();
        if(m.kind()==Kind.HELLO) {
            long now=System.nanoTime();if(c.helloAt!=0&&now-c.helloAt<TimeUnit.SECONDS.toNanos(3))return;c.helloAt=now;
            boolean supported=integer(b,"min",0)<=VERSION&&integer(b,"max",0)>=VERSION;
            if(!supported)clear(c);
            c.ready=supported;
            c.placementControls=supported&&b.has("features")&&b.get("features").isJsonArray()&&b.getAsJsonArray("features").contains(new JsonPrimitive("placement-controls-v1"));c.placementState="";
            c.buildingPreview=supported&&b.has("features")&&b.get("features").isJsonArray()&&b.getAsJsonArray("features").contains(new JsonPrimitive("building-preview-v1"));
            c.nativeMenus=supported&&b.has("features")&&b.get("features").isJsonArray()&&b.getAsJsonArray("features").contains(new JsonPrimitive("native-menus-v1"));
            c.contractsHud=supported&&b.has("features")&&b.get("features").isJsonArray()&&b.getAsJsonArray("features").contains(new JsonPrimitive("contracts-hud-v1"));
            c.personal=supported&&b.has("features")&&b.get("features").isJsonArray()&&b.getAsJsonArray("features").contains(new JsonPrimitive("personal-v1"));
            c.commands=supported&&b.has("features")&&b.getAsJsonArray("features").contains(new JsonPrimitive("commands-v1"));c.commandState.clear();
            c.civic=c.personal&&b.getAsJsonArray("features").contains(new JsonPrimitive("civic-v1"));
            c.cityScreen=c.personal&&b.getAsJsonArray("features").contains(new JsonPrimitive("city-screen-v1"));
            c.directory.clear();c.directoryToken="";
            c.navigation=supported&&b.has("features")&&b.get("features").isJsonArray()&&b.getAsJsonArray("features").contains(new JsonPrimitive("menu-back-v1"));
            resetNavigation(c);
            c.lastHud="";c.hudSent=0;c.hudEntries=0;
            send(p,Kind.WELCOME,object("accepted",supported,"protocol",VERSION,"townMember",hasTown(p),"home",catalog.menus().containsKey("hub")?"hub":"main","features",supported?List.of("personal-v1","commands-v1","city-screen-v1","civic-v1","placement-controls-v1"):List.of(),"message",supported?"":"Версии CraftoUI несовместимы."));
            if(c.personal)requestCity(c);
            return;
        }
        if(!c.ready)return;
        if(m.kind()==Kind.PLACEMENT){if(c.placementControls){PlacementBridge.move(this,p,b);placementState(c);}return;}
        if(m.kind()==Kind.COMMANDS){
            if(!c.commands)return;
            if(str(b,"op","").equals("return")){
                if(!p.hasPermission("craftoui.use")||!p.hasPermission("craftoui.commands")||!p.getItemOnCursor().isEmpty())return;
                var entry=catalog.addons().entries().values().stream().filter(e->e.plugin().equals(str(b,"module",""))).findFirst();
                if(entry.isPresent()){var a=catalog.actions().get("addons:"+entry.get().id());if(a!=null&&allowed(p,a))runAction(c,c.session==null?new Session("",revision.get()):c.session,0,a);}
                else openNow(c,"hub");return;
            }
            commandWorkbench.handle(p,c.commandState,catalog.commands(),b,response->{if(str(response,"op","").equals("list")||str(response,"op","").equals("form"))response.add("theme",theme(catalog.addons().theme()));send(p,Kind.COMMANDS,response);},()->clear(c));return;
        }
        if(m.kind()==Kind.CIVIC){if(c.civic)civicWorkbench.handle(p,c.civicState,b,response->{response.add("theme",theme(catalog.addons().theme()));send(p,Kind.CIVIC,response);});return;}
        if(m.kind()==Kind.DIRECTORY){if(c.personal)directory(c);return;}
        if(m.kind()==Kind.LAUNCH){if(c.personal)launch(c,b);return;}
        if(m.kind()==Kind.DASHBOARD){if(c.personal)requestCity(c);return;}
        if(m.kind()==Kind.BACK){goBack(c,str(b,"navigation",""));return;}
        if(m.kind()==Kind.REQUEST){if(c.session==null&&p.getOpenInventory().getTopInventory().getType()==org.bukkit.event.inventory.InventoryType.CRAFTING)resetNavigation(c);openNow(c,str(b,"menu","main"));return;}
        Session s=c.session;
        if(s==null||!s.token.equals(str(b,"session","")))return;
        if(m.kind()==Kind.CLOSE){clear(c);resetNavigation(c);return;}
        if(m.kind()!=Kind.ACTION)return;
        if(s.revision!=revision.get()||s.revision!=b.get("revision").getAsLong()||System.nanoTime()-s.created>SESSION_NANOS){closeNow(c,"Меню устарело. Откройте его снова.");return;}
        long seq=b.get("sequence").getAsLong();
        if(seq<=s.sequence||seq-s.sequence>1000)return;s.sequence=seq;
        if(!p.hasPermission("craftoui.use")){closeNow(c,"Недостаточно прав.");return;}
        JsonObject menu=menu(s.menu);
        if(menu==null||!allowed(p,menu)){closeNow(c,"Меню недоступно.");return;}
        String id=str(b,"component","");
        JsonObject node=eligible(menu.getAsJsonArray("components"),id,p);
        if(node==null||!str(node,"type","").equals("button")){String reason=unavailable(p,MenuSchema.find(menu,id));result(c,s,seq,false,reason.isEmpty()?"Доступ изменился. Откройте раздел заново.":reason,"");return;}
        if(c.navigation&&id.equals("back")&&str(node,"text","").strip().toLowerCase(Locale.ROOT).startsWith("назад")){goBack(c,c.navigationToken);return;}
        String actionId=str(node,"action","");
        OwnedAction external=externalActions.get(actionId);
        JsonObject action=catalog.actions().get(actionId);
        try {
            if(external!=null) {
                if(!external.owner().isEnabled()||!permission(p,external.permission())){result(c,s,seq,false,"Недостаточно прав.","");return;}
                JsonObject args=node.has("arguments")?node.getAsJsonObject("arguments").deepCopy():new JsonObject();
                ActionResult r=external.handler().execute(new ActionContext(p,this,s.menu,id,args));
                result(c,s,seq,r!=null&&r.success(),r==null?"Действие не вернуло результат.":r.message(),"");
            }else if(action!=null&&allowed(p,action))runAction(c,s,seq,action);
            else result(c,s,seq,false,unavailable(p,action),"");
        }catch(Exception e){getLogger().log(java.util.logging.Level.WARNING,"Action failed: "+actionId,e);result(c,s,seq,false,"Не удалось выполнить действие.","");}
    }
    private void placementState(Connection c){
        if(!c.ready||!c.placementControls||!c.player.isOnline())return;
        var state=PlacementBridge.state(this,c.player);String json=state.toString();long now=System.nanoTime();
        if(!json.equals(c.placementState)||bool(state,"active",false)&&now-c.placementSent>=1_000_000_000L){send(c.player,Kind.PLACEMENT,state);c.placementState=json;c.placementSent=now;}
    }
    private boolean searchable(JsonObject action){
        return action!=null&&(Set.of("open","civic").contains(str(action,"type",""))||str(action,"type","").equals("commands")&&!str(action,"form","").isBlank()||str(action,"type","").equals("player-command")&&bool(action,"closeBefore",false));
    }
    private void directory(Connection c){
        long now=System.nanoTime();if(now-c.directoryRequestAt<TimeUnit.MILLISECONDS.toNanos(700))return;c.directoryRequestAt=now;
        String previousToken=c.directoryToken;var previousDirectory=new LinkedHashMap<>(c.directory);boolean reusable=!previousToken.isEmpty()&&c.directoryRevision==revision.get()&&now-c.directoryAt<TimeUnit.MINUTES.toNanos(5);
        c.directory.clear();c.directoryToken="";var index=new NavigationDirectory();JsonArray entries=index.entries;
        if(c.player.hasPermission("craftoui.use")){
            for(var e:new TreeMap<>(catalog.quickLinks()).entrySet())if(allowed(c.player,e.getValue())){
                String key="link:"+e.getKey();c.directory.put(key,new QuickTarget("","",e.getKey()));
                index.add(key,str(e.getValue(),"label",e.getKey()),str(e.getValue(),"keywords",""),NavigationPolicy.destination(e.getValue()));
            }
            Map<String,JsonObject> menus=new TreeMap<>(catalog.menus());externalMenus.forEach((id,e)->{if(e.owner().isEnabled())menus.put(id,e.menu());});
            Set<String> actions=new HashSet<>();
            for(var e:menus.entrySet()){
                if(entries.size()>=96)break;var m=e.getValue();if(!allowed(c.player,m)||!bool(m,"searchable",true))continue;
                String menuKey="menu:"+e.getKey();c.directory.put(menuKey,new QuickTarget(e.getKey(),"",""));
                index.add(menuKey,str(m,"title",e.getKey()),e.getKey(),"menu:"+e.getKey());
                MenuSchema.visit(m.getAsJsonArray("components"),n->{
                    if(entries.size()>=96||!str(n,"type","").equals("button")||eligible(m.getAsJsonArray("components"),str(n,"id",""),c.player)==null)return;
                    String actionId=str(n,"action","");var action=catalog.actions().get(actionId);
                    // Only navigation actions; API handlers, rewards, purchases and arbitrary client commands are excluded.
                    if(externalActions.containsKey(actionId)||!searchable(action)||(!str(action,"type","").equals("open")&&!actionId.startsWith("addons:")&&!actionId.startsWith("quick:")&&!bool(action,"discoverable",false))||!allowed(c.player,action)||!actions.add(actionId))return;
                    if(str(action,"type","").equals("open"))return; // Menu destinations are already listed once with their own titles.
                    String id=str(n,"id","");String key=str(n,"directoryId","button:"+e.getKey()+"/"+id);
                    if(c.directory.containsKey(key))return;
                    c.directory.put(key,new QuickTarget(e.getKey(),id,""));
                    String label=MenuSchema.bind(str(n,"text",id),snapshot(c.player));
                    index.add(key,label.length()>100?label.substring(0,100):label,str(m,"title","")+" "+str(action,"command","")+" "+id+" "+str(n,"tooltip",""),NavigationPolicy.destination(action));
                });
            }
        }
        c.directoryToken=UUID.randomUUID().toString();c.directoryRevision=revision.get();c.directoryAt=now;
        JsonObject response=object("token",c.directoryToken,"entries",entries,"townMember",hasTown(c.player));
        while(true){try{encode(Kind.DIRECTORY,response);break;}catch(IllegalArgumentException e){if(entries.isEmpty())throw e;var removed=entries.remove(entries.size()-1).getAsJsonObject();c.directory.remove(str(removed,"id",""));response.add("entries",entries);}}
        // Only entries actually sent may be launched, including their retained aliases.
        Set<String> delivered=new HashSet<>();for(var value:entries){var entry=value.getAsJsonObject();delivered.add(str(entry,"id",""));for(var alias:entry.getAsJsonArray("aliases"))delivered.add(alias.getAsString());}
        c.directory.keySet().retainAll(delivered);
        // Refreshing an unchanged search must not invalidate a click already in flight.
        if(reusable&&previousDirectory.equals(c.directory)){c.directoryToken=previousToken;response.addProperty("token",previousToken);}
        send(c.player,Kind.DIRECTORY,response);
    }
    private void launch(Connection c,JsonObject body)throws IOException{
        String key=str(body,"id","");
        if(!c.player.hasPermission("craftoui.use")||!c.directoryToken.equals(str(body,"token",""))||c.directoryToken.isEmpty()||c.directoryRevision!=revision.get()||System.nanoTime()-c.directoryAt>TimeUnit.MINUTES.toNanos(5))return;
        QuickTarget target=c.directory.get(key);if(target==null)return;
        // Refuse navigation over a carried stack. The actual addon still checks membership/rank at command entry.
        if(!c.player.getItemOnCursor().isEmpty()){c.player.sendMessage("Сначала положите предмет с курсора в ячейку.");return;}
        JsonObject action=null;
        if(!target.link().isEmpty())action=catalog.quickLinks().get(target.link());
        else {
            JsonObject m=menu(target.menu());if(m==null||!allowed(c.player,m))return;
            if(target.component().isEmpty()){c.directoryToken="";openNow(c,target.menu());return;}
            JsonObject node=eligible(m.getAsJsonArray("components"),target.component(),c.player);if(node==null)return;
            action=catalog.actions().get(str(node,"action",""));
        }
        if(!searchable(action)||!allowed(c.player,action))return;
        if(str(action,"type","").equals("open")){var m=menu(str(action,"menu",""));if(m==null||!allowed(c.player,m))return;}
        c.directoryToken="";
        runAction(c,c.session==null?new Session("",revision.get()):c.session,0,action);
    }
    private void requestCity(Connection c){
        if(cityDataBridge==null||!c.personal||!c.ready||!c.player.hasPermission("craftoui.use")||!c.player.hasPermission("craftoui.city"))return;
        long now=System.nanoTime();if(now-c.cityAt<TimeUnit.SECONDS.toNanos(8)||!c.cityPending.compareAndSet(false,true))return;c.cityAt=now;
        UUID id=c.player.getUniqueId();
        CityDataBridge.Access access=cityDataBridge.capture(c.player);
        cityDataBridge.load(id,access,!c.summarySent).whenComplete((snapshot,error)->own(c.player,()->{
            c.cityPending.set(false);if(connections.get(id)!=c||!c.ready||!c.personal)return;
            if(!c.player.hasPermission("craftoui.use")||!c.player.hasPermission("craftoui.city"))return;
            if(error!=null){getLogger().warning("City snapshot unavailable: "+error.getClass().getSimpleName());return;}
            // Membership and permission may have changed while the disk read was in flight.
            if(!cityDataBridge.capture(c.player).equals(access))return;
            c.lastCity=snapshot.current();if(!c.summarySent)c.summary=snapshot.summary();
            send(c.player,Kind.DASHBOARD,object("city",snapshot.current().json(),"townMember",hasTown(c.player),"summary",c.summary,"notify",!c.summarySent));c.summarySent=true;
        }));
    }
    private void runAction(Connection c,Session s,long seq,JsonObject a)throws IOException {
        Player p=c.player;
        switch(str(a,"type","")) {
            case "open" -> openNow(c,str(a,"menu","main"));
            case "close" -> {closeNow(c,"");resetNavigation(c);}
            case "message" -> result(c,s,seq,true,str(a,"message","Готово."),"");
            case "refresh" -> {update(c,s);if(c.session==s)loadProviders(c,s);result(c,s,seq,true,"Данные обновлены.","");}
            case "settings" -> result(c,s,seq,true,"","settings");
            case "commands" -> {if(c.commands&&p.hasPermission("craftoui.commands"))send(p,Kind.COMMANDS,object("op","show","module",str(a,"module",""),"form",str(a,"form","")));else result(c,s,seq,false,"Нет доступа к командам","");}
            case "city" -> {
                if(!c.cityScreen||!p.hasPermission("craftoui.city")){result(c,s,seq,false,"Экран города недоступен.","");return;}
                result(c,s,seq,true,"","city");requestCity(c);
            }
            case "civic" -> {
                if(!c.civic||!p.hasPermission("craftoui.city")){result(c,s,seq,false,"Обновите CraftoUI или проверьте доступ к городскому интерфейсу.","");return;}
                send(p,Kind.CIVIC,object("op","show","route",str(a,"route","city")));
            }
            case "player-command" -> {
                String cmd=str(a,"command","");if(cmd.startsWith("/"))cmd=cmd.substring(1);
                if(bool(a,"closeBefore",false))closeNow(c,"");
                boolean ok=p.performCommand(cmd);result(c,s,seq,ok,ok?"":"Команда недоступна.","");
                if(bool(a,"closeBefore",false))p.getScheduler().runDelayed(this,t->{
                    if(connections.get(p.getUniqueId())==c&&c.session==null&&p.getOpenInventory().getTopInventory().getType()==org.bukkit.event.inventory.InventoryType.CRAFTING)resetNavigation(c);
                },null,2);
            }
            case "proxy" -> {
                if(!bool(a,"enabled",false)){result(c,s,seq,false,"Переход пока недоступен.","");return;}
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
                out.writeUTF("Connect");out.writeUTF(str(a,"server",""));p.sendPluginMessage(this,"BungeeCord",bytes.toByteArray());
                result(c,s,seq,true,"Запрошен переход на сервер.","");
            }
        }
    }
    private static boolean permission(Player p,String perm){return perm.isBlank()||p.hasPermission(perm);}
    private boolean hasTown(Player p){return TownMembership.hasTown(this,p);}
    private boolean missingTown(Player p,JsonObject n){return n!=null&&NavigationPolicy.townMismatch(n,hasTown(p));}
    TownyConfirmBridge townyConfirmations(){return civicWorkbench.confirmations();}
    private String unavailable(Player p,JsonObject n){
        String reason=ActionAvailability.reason(n,hasTown(p),p::hasPermission,name->getServer().getPluginManager().isPluginEnabled(name));
        if(!reason.isEmpty())return reason;
        if(str(n,"type","").equals("open"))return ActionAvailability.reason(menu(str(n,"menu","")),hasTown(p),p::hasPermission,name->getServer().getPluginManager().isPluginEnabled(name));
        if(str(n,"type","").equals("city")){Connection c=connections.get(p.getUniqueId());if(c==null||!c.cityScreen)return "Обновите CraftoUI в лаунчере, чтобы открыть экран города.";if(!p.hasPermission("craftoui.city"))return "Просмотр сводки города отключён для вашей роли.";}
        if(str(n,"type","").equals("civic")){Connection c=connections.get(p.getUniqueId());if(c==null||!c.civic)return "Обновите CraftoUI в лаунчере.";if(!p.hasPermission("craftoui.city"))return "Городской интерфейс недоступен для вашей роли.";}
        return "";
    }
    private boolean allowed(Player p,JsonObject n){
        if(missingTown(p,n))return false;
        if(str(n,"type","").equals("open")){JsonObject target=menu(str(n,"menu",""));if(target==null||missingTown(p,target)||!permission(p,str(target,"permission","")))return false;}
        if(str(n,"type","").equals("city")){Connection c=connections.get(p.getUniqueId());if(c==null||!c.cityScreen||!p.hasPermission("craftoui.city"))return false;}
        if(str(n,"type","").equals("civic")){Connection c=connections.get(p.getUniqueId());if(c==null||!c.civic||!p.hasPermission("craftoui.city"))return false;}
        if(str(n,"type","").equals("commands")){Connection c=connections.get(p.getUniqueId());if(c==null||!c.commands||!p.hasPermission("craftoui.commands"))return false;}
        return bool(n,"enabled",true)&&permission(p,str(n,"permission",""))&&(str(n,"requiredPlugin","").isBlank()||getServer().getPluginManager().isPluginEnabled(str(n,"requiredPlugin","")));}
    private JsonObject eligible(JsonArray nodes,String id,Player p) {
        for(JsonElement e:nodes){JsonObject n=e.getAsJsonObject();if(!allowed(p,n))continue;if(str(n,"id","").equals(id))return n;if(n.has("children")){JsonObject found=eligible(n.getAsJsonArray("children"),id,p);if(found!=null)return found;}}return null;
    }
    private JsonObject menu(String id){OwnedMenu e=externalMenus.get(id);return e!=null&&e.owner().isEnabled()?e.menu():catalog.menus().get(id);}
    @Override public boolean hasClient(UUID id){Connection c=connections.get(id);return c!=null&&c.ready;}
    @Override public void openMenu(Player p,String id){own(p,()->{
        Connection c=connections.get(p.getUniqueId());
        if(c==null||!c.ready){p.sendMessage("Для этого меню нужен CraftoUI в лаунчере. Остальные функции сервера доступны как обычно.");return;}
        openNow(c,id);
    });}
    private void openNow(Connection c,String id) {
        Player p=c.player;JsonObject m=menu(id);
        if(!p.hasPermission("craftoui.use")||m==null||!allowed(p,m)){p.sendMessage("Меню недоступно.");return;}
        Session s=new Session(id,revision.get());s.townMember=hasTown(p);s.values.putAll(snapshot(p));
        JsonObject screen=m.deepCopy();screen.remove("permission");screen.remove("providers");
        screen.add("components",visible(m.getAsJsonArray("components"),p));s.visibleComponents=screen.get("components").toString();
        JsonObject theme=catalog.themes().getOrDefault(str(m,"theme","classic"),catalog.themes().get("classic"));
        if(theme==null){p.sendMessage("Тема меню не найдена.");return;}
        JsonObject body=object("session",s.token,"revision",s.revision,"screen",screen,"theme",theme,"data",s.values,"townMember",s.townMember);
        // Include navigation overhead in the size check before changing the player's screen/history.
        if(c.navigation){body.addProperty("navigation",UUID.randomUUID().toString());body.addProperty("backLabel","Назад");}
        try{encode(Kind.OPEN,body);}catch(IllegalArgumentException ex){getLogger().warning("Menu too large: "+id);p.sendMessage("Меню слишком большое для отправки.");return;}
        c.pendingBackUntil=0;c.pendingBackTarget=null;
        navigate(c,new ReturnTarget(id,null,"","","",true),body);
        if(p.getOpenInventory().getType()!=org.bukkit.event.inventory.InventoryType.CRAFTING&&p.getOpenInventory().getType()!=org.bukkit.event.inventory.InventoryType.CREATIVE)p.closeInventory();
        clear(c);c.session=s;send(p,Kind.OPEN,body);
        s.refresh=p.getScheduler().runAtFixedRate(this,t->update(c,s),()->{},20,20);
        loadProviders(c,s);
    }
    private JsonArray visible(JsonArray nodes,Player p) {return visible(nodes,p,new HashSet<>(),"");}
    private JsonArray visible(JsonArray nodes,Player p,Set<String> destinations,String inherited) {
        JsonArray out=new JsonArray();
        for(JsonElement e:nodes){JsonObject n=e.getAsJsonObject().deepCopy();String reason=inherited.isEmpty()?unavailable(p,n):inherited;boolean enabled=reason.isEmpty();
            String action=str(n,"action","");JsonObject definition=externalActions.containsKey(action)?null:catalog.actions().get(action);
            if(missingTown(p,n)||missingTown(p,definition))continue;
            if(definition!=null&&str(definition,"type","").equals("open")&&missingTown(p,menu(str(definition,"menu",""))))continue;
            if(!action.isBlank()&&enabled){
                OwnedAction ext=externalActions.get(action);JsonObject a=catalog.actions().get(action);
                reason=ext!=null?(!ext.owner().isEnabled()?"Этот раздел временно выключен. Попробуйте позже.":!permission(p,ext.permission())?"Вашей роли не разрешено это действие. Обратитесь к администратору.":""):unavailable(p,a);
                enabled=reason.isEmpty();
            }
            if(!enabled)n.addProperty("disabledReason",reason);else n.remove("disabledReason");
            if(!enabled&&bool(n,"hideWhenDenied",false))continue;
            String destination=NavigationPolicy.destination(definition);if(enabled&&!destination.isBlank()&&!destinations.add(destination))continue;
            n.remove("clientRoute"); // Only the validated action definition may assign this hint.
            if(enabled&&(destination.equals("city")||destination.equals("client:settings")))n.addProperty("clientRoute",destination);
            n.addProperty("enabled",enabled);n.remove("action");n.remove("arguments");n.remove("permission");n.remove("hideWhenDenied");n.remove("requiredPlugin");n.remove("requiresTown");n.remove("requiresNoTown");
            if(n.has("children"))n.add("children",visible(n.getAsJsonArray("children"),p,destinations,reason));out.add(n);
        }return out;
    }
    private Map<String,String> snapshot(Player p) {
        Map<String,String> d=new HashMap<>();
        d.put("player.name",p.getName());d.put("player.uuid",p.getUniqueId().toString());d.put("player.status","Онлайн");
        d.put("player.level",Integer.toString(p.getLevel()));d.put("player.exp",Float.toString(p.getExp()));
        d.put("player.health",String.format(Locale.ROOT,"%.0f",p.getHealth()));d.put("player.food",Integer.toString(p.getFoodLevel()));
        d.put("player.world",p.getWorld().getName());d.put("player.gamemode",switch(p.getGameMode()){case SURVIVAL->"Выживание";case CREATIVE->"Творческий";case ADVENTURE->"Приключение";case SPECTATOR->"Наблюдатель";});
        d.put("player.group","—");
        if(getServer().getPluginManager().isPluginEnabled("LuckPerms"))LuckPermsBridge.fill(p,d);
        return d;
    }
    private void update(Connection c,Session s) {
        if(c.session!=s||stopping)return;
        if(s.revision!=revision.get()||System.nanoTime()-s.created>SESSION_NANOS||!c.player.hasPermission("craftoui.use")){closeNow(c,"");return;}
        JsonObject m=menu(s.menu);if(m==null||!allowed(c.player,m)){closeNow(c,"");return;}
        boolean member=hasTown(c.player);
        var components=visible(m.getAsJsonArray("components"),c.player);String encoded=components.toString();
        if(member!=s.townMember||!encoded.equals(s.visibleComponents)){
            s.townMember=member;s.visibleComponents=encoded;
            send(c.player,Kind.PATCH,object("session",s.token,"revision",s.revision,"data",Map.of(),"townMember",member,"components",components));
        }
        patch(c,s,snapshot(c.player));
    }
    private void patch(Connection c,Session s,Map<String,String> values) {
        if(c.session!=s)return;Map<String,String> changes=new HashMap<>();
        values.forEach((k,v)->{if(k.length()<=96&&v!=null&&v.length()<=512&&!Objects.equals(s.values.get(k),v))changes.put(k,v);});
        if(changes.isEmpty())return;
        JsonObject body=object("session",s.token,"revision",s.revision,"data",changes);
        try{encode(Kind.PATCH,body);s.values.putAll(changes);send(c.player,Kind.PATCH,body);}catch(IllegalArgumentException e){getLogger().warning("Provider update too large; ignored");}
    }
    private void loadProviders(Connection c,Session s) {
        if(s.providerPending||providers.isEmpty())return;s.providerPending=true;
        PlayerSnapshot snapshot=new PlayerSnapshot(c.player.getUniqueId(),c.player.getName(),s.values);
        var registered=Map.copyOf(providers);
        getServer().getAsyncScheduler().runNow(this,task->{
            List<CompletableFuture<Map<String,String>>> futures=new ArrayList<>();
            registered.forEach((namespace,entry)->{
                try {
                    CompletableFuture<Map<String,String>> f=entry.provider().load(snapshot).toCompletableFuture()
                        .orTimeout(3,TimeUnit.SECONDS).handle((values,error)->{
                            Map<String,String> out=new HashMap<>();
                            if(error==null&&values!=null){int count=0;for(var e:values.entrySet()){if(count++>=64)break;if(e.getKey().matches("[a-zA-Z0-9_.-]{1,64}")&&e.getValue()!=null&&e.getValue().length()<=512)out.put(namespace+"."+e.getKey(),e.getValue());}}
                            return out;
                        });futures.add(f);
                }catch(Exception ex){getLogger().warning("Provider failed: "+namespace);}
            });
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).whenComplete((unused,error)->{
                Map<String,String> combined=new HashMap<>();for(var f:futures)combined.putAll(f.getNow(Map.of()));
                own(c.player,()->{if(c.session==s&&s.revision==revision.get()){s.providerPending=false;patch(c,s,combined);}});
            });
        });
    }
    @Override public void refresh(Player p){own(p,()->{Connection c=connections.get(p.getUniqueId());if(c!=null&&c.session!=null){update(c,c.session);if(c.session!=null)loadProviders(c,c.session);}});}
    @Override public void closeMenu(Player p){own(p,()->{Connection c=connections.get(p.getUniqueId());if(c!=null){closeNow(c,"");resetNavigation(c);}});}
    private void clear(Connection c){Session s=c.session;c.session=null;if(s!=null&&s.refresh!=null)s.refresh.cancel();}
    private void closeNow(Connection c,String message){Session s=c.session;clear(c);if(s!=null)send(c.player,Kind.CLOSE,object("session",s.token,"message",message));}
    private void result(Connection c,Session s,long seq,boolean success,String message,String clientAction){if(c.session==s)send(c.player,Kind.RESULT,object("session",s.token,"sequence",seq,"success",success,"message",message==null?"":message,"clientAction",clientAction));}
    private void send(Player p,Kind kind,JsonObject body){if(p.isOnline())p.sendPluginMessage(this,CHANNEL,encode(kind,body));}
    private void invalidate(){revision.incrementAndGet();connections.values().forEach(c->own(c.player,()->{c.commandState.clear();c.civicState.clear();closeNow(c,"Меню обновлено. Откройте его снова.");resetNavigation(c);}));}
    @Override public void registerMenu(Plugin owner,JsonObject definition){
        Objects.requireNonNull(owner);JsonObject copy=definition.deepCopy();MenuSchema.validate(copy);String id=str(copy,"id","");
        if(catalog.menus().containsKey(id))throw new IllegalArgumentException("Menu id is reserved");
        externalMenus.compute(id,(k,old)->{if(old!=null&&old.owner()!=owner)throw new IllegalArgumentException("Menu owned by another plugin");return new OwnedMenu(owner,copy);});invalidate();
    }
    @Override public void registerAction(Plugin owner,String id,String perm,ActionHandler handler){
        Objects.requireNonNull(owner);Objects.requireNonNull(handler);MenuSchema.requireId(id);
        if(!id.contains(":"))throw new IllegalArgumentException("API actions require a namespace");
        externalActions.compute(id,(k,old)->{if(old!=null&&old.owner()!=owner)throw new IllegalArgumentException("Action owned by another plugin");return new OwnedAction(owner,Objects.requireNonNull(perm),handler);});invalidate();
    }
    @Override public void registerDataProvider(Plugin owner,String namespace,DataProvider provider){
        Objects.requireNonNull(owner);Objects.requireNonNull(provider);
        if(!namespace.matches("[a-z][a-z0-9_]{0,23}")||namespace.equals("player"))throw new IllegalArgumentException("Invalid/reserved namespace");
        providers.compute(namespace,(k,old)->{if(old!=null&&old.owner()!=owner)throw new IllegalArgumentException("Provider owned by another plugin");return new OwnedProvider(owner,provider);});invalidate();
    }
    @Override public void unregister(Plugin owner){
        boolean changed=externalMenus.entrySet().removeIf(e->e.getValue().owner()==owner);
        changed|=externalActions.entrySet().removeIf(e->e.getValue().owner()==owner);changed|=providers.entrySet().removeIf(e->e.getValue().owner()==owner);if(changed&&!stopping)invalidate();
    }
}
