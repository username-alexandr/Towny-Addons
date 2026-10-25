package ru.neverland.runtime;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.threadedregions.scheduler.*;
import net.kyori.adventure.text.Component;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import ru.neverland.core.SuiteState;
import cc.crafto.ui.Protocol;
import ru.neverland.townybuilds.construction.*;
import ru.neverland.townybuilds.data.*;
import ru.neverland.townybuilds.service.*;
import ru.neverland.townybuilds.model.*;

/** Disposable real Folia world/inventories/displays/data; players are simulated on the owning region. */
public final class PlacementRuntimeProbe extends JavaPlugin {
    static UUID id(String name){return UUID.nameUUIDFromBytes(("placement-probe-"+name).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    static final UUID TOWN=id("town"),OTHER=id("other"),MAYOR=id("mayor"),MEMBER=id("member");
    JavaPlugin owner;DataStore data;DefinitionRegistry definitions;BuildService builds;PlacementService placement;ConstructionService construction;
    Town town;World world;Actor mayor,member;Path proof;int checks;Deque<Work> steps=new ArrayDeque<>();
    interface Work{void run()throws Exception;}
    static Object field(Object object,String name)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    static Object call(Object object,String name,Class<?>[] types,Object... args)throws Exception{var m=object.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(object,args);}
    void check(boolean condition,String label)throws Exception{if(!condition)throw new AssertionError(label);checks++;Files.writeString(proof.resolve("checks.txt"),"PASS "+label+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);getLogger().info("CHECK "+label);}
    @Override public void onEnable(){
        if(!Files.isRegularFile(Path.of("ALLOW_DISPOSABLE_PLACEMENT_PROBE"))||!getServer().getIp().equals("127.0.0.1")){Bukkit.getPluginManager().disablePlugin(this);return;}
        proof=getDataFolder().toPath();Bukkit.getGlobalRegionScheduler().runDelayed(this,t->{try{
            Files.createDirectories(proof);world=Bukkit.getWorlds().getFirst();List<CompletableFuture<?>> chunks=new ArrayList<>();
            for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)chunks.add(world.getChunkAtAsync(x,z));
            CompletableFuture.allOf(chunks.toArray(CompletableFuture[]::new)).whenComplete((result,error)->{if(error!=null){fail(error);return;}
                Bukkit.getRegionScheduler().runDelayed(this,new Location(world,0,160,0),task->SuiteState.run(()->{try{setup();schedule();}catch(Throwable e){fail(e);}}),20);
            });
        }catch(Throwable e){fail(e);}},35);
    }
    Object session(Actor actor)throws Exception{return ((Map<?,?>)field(placement,"sessions")).get(actor.id);}
    Object projection(Actor actor)throws Exception{return field(session(actor),"projection");}
    void render(Actor actor)throws Exception{Object p=projection(actor);call(p,"render",new Class[]{int.class},30000);check((boolean)call(p,"complete",new Class[0]),"whole building display completed");}
    ItemStack token(Actor actor){for(var item:actor.contents.getContents())if(item!=null&&item.getType()==Material.STRUCTURE_VOID)return item;throw new AssertionError("no blueprint item");}
    int tokens(Actor actor){return Arrays.stream(actor.contents.getContents()).filter(i->i!=null&&i.getType()==Material.STRUCTURE_VOID).mapToInt(ItemStack::getAmount).sum();}
    void select(Actor actor)throws Exception{placement.interact(new PlayerInteractEvent(actor.player,Action.RIGHT_CLICK_BLOCK,token(actor),world.getBlockAt(0,159,0),BlockFace.UP,EquipmentSlot.HAND));}
    void command(Actor actor,String... args){placement.onCommand(actor.player,Bukkit.getPluginCommand("nltplace"),"nltplace",args);}
    void click(Actor actor,int slot){var e=new InventoryClickEvent(actor.view,InventoryType.SlotType.CONTAINER,slot,ClickType.LEFT,InventoryAction.PICKUP_ALL);placement.click(e);if(!e.isCancelled())throw new AssertionError("unsafe menu click");}
    @SuppressWarnings("unchecked") void setup()throws Exception{
        owner=(JavaPlugin)Bukkit.getPluginManager().getPlugin("NeverLandTownyBuilds");check(owner.isEnabled(),"Builds 0.8.32 enabled on native Folia");
        data=(DataStore)field(owner,"dataStore");definitions=(DefinitionRegistry)field(owner,"definitions");placement=(PlacementService)field(owner,"placements");builds=(BuildService)field(placement,"builds");construction=(ConstructionService)field(owner,"construction");placement.stop();
        if(Files.exists(proof.resolve("first-passed.txt"))){
            var state=data.town(TOWN);check(state.placements().get("town_hall").id().toString().equals(Files.readString(proof.resolve("draft-id"))),"unplaced blueprint survives native restart");
            check(state.existingResourceFund("town_hall").count(new ItemStack(Material.STONE))==1,"fund survives native restart");
            check(state.constructionSite("forge")!=null&&state.constructionSite("forge").active(),"confirmed site survives native restart");
            check(!state.placements().containsKey("forge"),"consumed blueprint stays invalid after restart");
            check(state.constructionReceipts().get("forge").paidCents()==0,"free legacy placement receipt persisted");
            Files.writeString(proof.resolve("restart-passed.txt"),"PASS "+checks+" checks\n");Bukkit.shutdown();return;
        }
        var gen=new BuildingBlueprintGenerator();int maximum=0;String largest="";
        var projectionClass=Class.forName("ru.neverland.townybuilds.construction.PlacementProjection",true,owner.getClass().getClassLoader());var surface=projectionClass.getDeclaredMethod("surface",BlueprintPlan.class);surface.setAccessible(true);
        for(String name:gen.supportedProjects())for(int level=1;level<=gen.maximumStage(name);level++){var plan=gen.generate(name,level);int count=((List<?>)surface.invoke(null,plan)).size();if(count>maximum){maximum=count;largest=name+":"+level;}}
        Files.writeString(proof.resolve("display-budget.txt"),largest+"="+maximum+"\n");check(maximum<=8000,"all standard building surfaces fit default display budget");
        var universe=TownyUniverse.getInstance();universe.newTownInternal("PlacementTown",TOWN);universe.newTownInternal("PlacementOther",OTHER);town=TownyAPI.getInstance().getTown(TOWN);
        for(var entry:Map.of(MAYOR,"PlaceMayor",MEMBER,"PlaceMember").entrySet()){var r=universe.getDataSource().newResident(entry.getValue(),entry.getKey());r.setTown(town);r.save();}
        town.setMayor(TownyAPI.getInstance().getResident(MAYOR));
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){var block=new TownBlock(x,z,universe.getWorld(world.getName()));universe.addTownBlock(block);block.setTown(town);if(x==0&&z==0){town.setHomeBlock(block);town.setSpawn(new Location(world,1,160,1));}block.save();}town.save();
        for(int x=-30;x<=30;x++)for(int z=-30;z<=30;z++){checkOwned(x,z);world.getBlockAt(x,159,z).setType(Material.STONE,false);}
        mayor=new Actor(MAYOR);member=new Actor(MEMBER);
        var projects=(Map<String,ProjectDefinition>)field(definitions,"projects");
        var project=new ProjectDefinition("town_hall",ProjectType.BUILDING,"Ратуша",Material.BRICKS,"",0,List.of(),Map.of(1,new LevelDefinition(1,1000,0,List.of(new ItemStack(Material.STONE)),Map.of(),List.of())));projects.put(project.id(),project);
        mayor.contents.setItem(0,new ItemStack(Material.STONE));var contributed=builds.contributeFromPlayer(mayor.player,project);
        check(contributed.status()==ContributionResult.Status.SUCCESS&&tokens(mayor)==1,"last resource contribution automatically gives blueprint");
        check(data.town(TOWN).constructionSite(project.id())==null&&data.town(TOWN).constructionReceipts().isEmpty(),"issuing blueprint does not start or charge construction");
        placement.issue(mayor.player,town,project,1);check(tokens(mayor)==1,"reissuing existing blueprint does not duplicate item");
        UUID draft=data.town(TOWN).placements().get(project.id()).id();mayor.contents.clear();placement.issue(mayor.player,town,project,1);
        check(tokens(mayor)==1&&data.town(TOWN).placements().get(project.id()).id().equals(draft),"lost blueprint reissued without consuming fund");
        select(mayor);check(session(mayor)!=null,"right click creates placement selection");render(mayor);
        List<BlockDisplay> displays=(List<BlockDisplay>)field(projection(mayor),"entities");
        check(displays.stream().allMatch(d->d.isValid()&&!d.isPersistent()&&!d.isVisibleByDefault()),"all real BlockDisplays temporary and private");
        check(displays.stream().mapToDouble(d->d.getTransformation().getTranslation().y).max().orElse(0)>3,"projection includes roof before construction starts");
        check(world.getBlockAt(0,160,0).getType().isAir(),"projection leaves actual world blocks untouched");
        command(mayor,"rotate");check(((PlacementChoice)field(session(mayor),"choice")).facing()==BlockFace.EAST,"blueprint rotates clockwise");render(mayor);
        var before=data.town(TOWN).existingResourceFund(project.id()).count(new ItemStack(Material.STONE));command(mayor,"close");
        check(session(mayor)==null&&displays.stream().noneMatch(Entity::isValid),"closing preview removes real entities");check(data.town(TOWN).existingResourceFund(project.id()).count(new ItemStack(Material.STONE))==before,"preview cancellation keeps all resources");
        // Runtime guard is exercised with no provider: this must stop before a receipt or withdrawal exists.
        select(mayor);render(mayor);command(mayor);click(mayor,40);click(mayor,40);
        steps.add(()->{check(data.town(TOWN).constructionSite(project.id())==null&&data.town(TOWN).constructionReceipts().isEmpty(),"double confirmation with unavailable economy leaves funds and site untouched");
            command(mayor,"close");member.contents.addItem(token(mayor).clone());select(member);render(member);command(member);click(member,40);});
        steps.add(()->{check(data.town(TOWN).constructionSite(project.id())==null,"ordinary resident cannot confirm placement");command(member,"close");
            TownyAPI.getInstance().getResident(MEMBER).setTown(null);TownyAPI.getInstance().getResident(MEMBER).setTown(TownyAPI.getInstance().getTown(OTHER));select(member);check(session(member)==null,"transferred blueprint cannot be used in another town");
            var changed=token(mayor).clone();var meta=changed.getItemMeta();meta.getPersistentDataContainer().set(new NamespacedKey(owner,"placement_token"),org.bukkit.persistence.PersistentDataType.STRING,UUID.randomUUID().toString());changed.setItemMeta(meta);mayor.contents.setItem(0,changed);select(mayor);check(session(mayor)==null,"forged blueprint ID rejected");mayor.contents.clear();placement.issue(mayor.player,town,project,1);
            var choice=new PlacementChoice(world.getUID(),0,world.getMaxHeight()-1,0,BlockFace.NORTH);check(construction.prepare(mayor.player,town,project,0,1,choice).status()!=ConstructionPreparation.Status.READY,"world height bounds rejected before blocks are read");
            var outside=new PlacementChoice(world.getUID(),47,160,47,BlockFace.NORTH);check(construction.prepare(mayor.player,town,project,0,1,outside).status()!=ConstructionPreparation.Status.READY,"footprint outside available city terrain rejected");
            Files.writeString(proof.resolve("draft-id"),draft.toString());
            // Existing unlocked wonder path exercises successful confirmation and receipt consumption without currency emission.
            var legacy=new ProjectDefinition("forge",ProjectType.WONDER,"Legacy placement fixture",Material.BRICKS,"",0,List.of(),Map.of(1,new LevelDefinition(1,1000,0,List.of(),Map.of(),List.of())));projects.put("forge",legacy);data.town(TOWN).setLevel("forge",1);data.saveOrThrow();
            mayor.contents.clear();placement.issue(mayor.player,town,legacy,1);select(mayor);render(mayor);command(mayor);click(mayor,40);click(mayor,40);
        });
        steps.add(()->{check(data.town(TOWN).constructionSite("forge")!=null&&data.town(TOWN).constructionSite("forge").active(),"explicit confirmation starts valid legacy site");check(!data.town(TOWN).placements().containsKey("forge")&&tokens(mayor)==0,"successful confirmation consumes token reference and item");
            check(data.town(TOWN).constructionReceipts().size()==1&&data.town(TOWN).constructionReceipts().get("forge").paidCents()==0,"duplicate confirmation creates exactly one zero-cost receipt");
            var plan=new BuildingBlueprintGenerator().generate("forge",1);var voxels=new ArrayList<BlueprintThumbnail.Voxel>();for(var e:plan.blocks().entrySet()){var b=e.getKey();voxels.add(new BlueprintThumbnail.Voxel(b.x(),b.y(),b.z(),e.getValue().material().createBlockData().getMapColor().asRGB(),1));}
            for(int view=0;view<4;view++){byte[] png=BlueprintThumbnail.render(voxels,view,192);check(png.length<=16000,"schematic view "+view+" fits bounded protocol");Files.write(proof.resolve("forge-"+view+".png"),png);}
            var faultProject=new ProjectDefinition("market",ProjectType.BUILDING,"Fault fixture",Material.STONE,"",0,List.of(),Map.of(1,new LevelDefinition(1,1000,0,List.of(new ItemStack(Material.STONE,2)),Map.of(),List.of())));
            mayor.contents.clear();mayor.contents.addItem(new ItemStack(Material.STONE));data.saveOrThrow();
            Path file=owner.getDataFolder().toPath().resolve("town-data.yml"),backup=file.resolveSibling("probe-good-data.yml");Files.move(file,backup);Files.createDirectory(file);
            try{var result=builds.contributeFromPlayer(mayor.player,faultProject);check(result.status()==ContributionResult.Status.INVENTORY_SYNC_FAILED,"failed fund write aborts contribution");check(mayor.contents.contains(Material.STONE,1),"failed fund write restores player's exact resource");check(!data.writable(),"failed storage blocks new transactions");}
            finally{Files.delete(file);Files.move(backup,file);data.load();}
            check(data.town(TOWN).existingResourceFund("market")==null,"failed contribution creates no durable credit");
            var active=definitions.project("forge");check(builds.contributeFromPlayer(mayor.player,active).status()==ContributionResult.Status.CONSTRUCTION_ACTIVE,"active construction cannot collect a second fund for the same level");
            data.saveOrThrow();universe.getDataSource().saveAll();startUiProbe();});
    }
    Object uiConnection;JavaPlugin uiPlugin;java.lang.reflect.Method loadPreview;
    @SuppressWarnings("unchecked") void startUiProbe()throws Exception{
        uiPlugin=(JavaPlugin)Bukkit.getPluginManager().getPlugin("CraftoUI");check(uiPlugin!=null&&uiPlugin.isEnabled(),"CraftoUI 0.8.0 enables alongside Builds on Folia");
        var type=Class.forName("cc.crafto.ui.server.CraftoUIPlugin$Connection",true,uiPlugin.getClass().getClassLoader());var ctor=type.getDeclaredConstructor(Player.class);ctor.setAccessible(true);uiConnection=ctor.newInstance(mayor.player);
        for(String field:List.of("ready","buildingPreview")){var f=type.getDeclaredField(field);f.setAccessible(true);f.set(uiConnection,true);}
        ((Map<UUID,Object>)field(uiPlugin,"connections")).put(MAYOR,uiConnection);
        ((ru.neverland.townybuilds.gui.MenuManager)field(owner,"uiMenus")).openDetails(mayor.player,definitions.project("town_hall"));
        var bridge=Class.forName("cc.crafto.ui.server.BuildingPreviewBridge",true,uiPlugin.getClass().getClassLoader());loadPreview=bridge.getDeclaredMethod("load",uiPlugin.getClass(),Player.class,Inventory.class,String.class);loadPreview.setAccessible(true);
        loadPreview.invoke(null,uiPlugin,mayor.player,mayor.top,"native-preview-test");steps.add(()->awaitPreview(0));
    }
    void awaitPreview(int attempt)throws Exception{
        if(mayor.packets.size()<4){if(attempt>=40)throw new AssertionError("preview packets not delivered");steps.addFirst(()->awaitPreview(attempt+1));return;}
        Set<Integer> angles=new HashSet<>();for(byte[] packet:mayor.packets){var decoded=Protocol.decode(packet);check(decoded.kind()==Protocol.Kind.BUILD_PREVIEW,"native bridge sends bounded preview message");var body=decoded.body();check(body.get("key").getAsString().equals("native-preview-test"),"image packet bound to current menu key");angles.add(body.get("view").getAsInt());var base64=new StringBuilder();for(var chunk:body.getAsJsonArray("chunks"))base64.append(chunk.getAsString());var image=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(Base64.getDecoder().decode(base64.toString())));check(image.getWidth()==192&&image.getHeight()==192,"actual async thumbnail PNG delivered");}
        check(angles.equals(Set.of(0,1,2,3)),"four preview rotations delivered through CraftoUI");mayor.packets.clear();
        var f=uiConnection.getClass().getDeclaredField("buildingPreview");f.setAccessible(true);f.set(uiConnection,false);loadPreview.invoke(null,uiPlugin,mayor.player,mayor.top,"old-client");
        steps.add(()->{check(mayor.packets.isEmpty(),"clients without preview capability receive no new packet kind");f.set(uiConnection,true);loadPreview.invoke(null,uiPlugin,mayor.player,mayor.top,"stale-menu");mayor.player.closeInventory();});
        steps.add(()->{check(mayor.packets.isEmpty(),"closing menu prevents delayed preview delivery");Files.writeString(proof.resolve("first-passed.txt"),"PASS "+checks+" checks\n");Bukkit.shutdown();});
    }
    void checkOwned(int x,int z){if(!Bukkit.isOwnedByCurrentRegion(world,x>>4,z>>4))throw new AssertionError("region not owned "+x+","+z);}
    void schedule(){if(steps.isEmpty())return;Bukkit.getRegionScheduler().runDelayed(this,new Location(world,0,160,0),t->SuiteState.run(()->{try{steps.removeFirst().run();schedule();}catch(Throwable e){fail(e);}}),4);}
    void fail(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"PROBE_FAIL",e);try{Files.createDirectories(proof);Files.writeString(proof.resolve("failed.txt"),e.toString());}catch(Exception ignored){}Bukkit.shutdown();}
    final class Actor {
        final UUID id;final Player player;final Inventory contents=Bukkit.createInventory(null,36);Inventory top=Bukkit.createInventory(null,9);final InventoryView view;final PlayerInventory inventory;final List<String> messages=new ArrayList<>();final List<byte[]> packets=new ArrayList<>();
        Actor(UUID id){this.id=id;
            inventory=(PlayerInventory)Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(),new Class[]{PlayerInventory.class},(p,m,a)->switch(m.getName()){
                case "getStorageContents","getContents"->contents.getContents();case "setStorageContents","setContents"->{contents.setContents((ItemStack[])a[0]);yield null;}case "getSize"->36;case "firstEmpty"->contents.firstEmpty();case "getItem"->contents.getItem((Integer)a[0]);case "setItem"->{contents.setItem((Integer)a[0],(ItemStack)a[1]);yield null;}case "addItem"->contents.addItem((ItemStack[])a[0]);case "getHeldItemSlot"->0;case "getItemInMainHand"->contents.getItem(0);default->null;});
            EntityScheduler scheduler=(EntityScheduler)Proxy.newProxyInstance(EntityScheduler.class.getClassLoader(),new Class[]{EntityScheduler.class},(p,m,a)->{
                if(m.getName().equals("run"))return Bukkit.getRegionScheduler().run((org.bukkit.plugin.Plugin)a[0],new Location(world,0,160,0),(Consumer<ScheduledTask>)a[1]);if(m.getName().equals("runDelayed"))return Bukkit.getRegionScheduler().runDelayed((org.bukkit.plugin.Plugin)a[0],new Location(world,0,160,0),(Consumer<ScheduledTask>)a[1],(Long)a[3]);throw new UnsupportedOperationException(m.getName());});
            player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class[]{Player.class},(p,m,a)->switch(m.getName()){
                case "getUniqueId"->id;case "getName"->TownyAPI.getInstance().getResident(id).getName();case "getServer"->Bukkit.getServer();case "isOnline","hasPermission"->true;case "isOp","isSneaking"->false;
                case "getWorld"->world;case "getLocation","getEyeLocation"->new Location(world,1,162,1);case "getFacing"->BlockFace.NORTH;case "getGameMode"->GameMode.SURVIVAL;
                case "getScheduler"->scheduler;case "getInventory"->inventory;case "getOpenInventory"->getView();case "openInventory"->{top=(Inventory)a[0];yield getView();}case "closeInventory"->{top=Bukkit.createInventory(null,9);yield null;}
                case "sendMessage"->{if(a!=null&&a.length>0)messages.add(String.valueOf(a[a.length-1]));yield null;}case "sendPluginMessage"->{packets.add(((byte[])a[2]).clone());yield null;}case "showEntity","sendActionBar","playSound"->null;case "hashCode"->id.hashCode();case "equals"->p==a[0];case "toString"->"Simulated player "+id;default->null;});
            view=(InventoryView)Proxy.newProxyInstance(InventoryView.class.getClassLoader(),new Class[]{InventoryView.class},(p,m,a)->switch(m.getName()){
                case "getTopInventory"->top;case "getBottomInventory"->inventory;case "getPlayer"->player;case "getType"->InventoryType.CHEST;case "getCursor"->new ItemStack(Material.AIR);case "countSlots"->top.getSize()+36;case "getTitle","getOriginalTitle"->"Fixture";case "title"->Component.text("Fixture");case "getSlotType"->InventoryType.SlotType.CONTAINER;case "convertSlot"->a[0];case "getInventory"->(Integer)a[0]<top.getSize()?top:inventory;default->null;});
        }
        InventoryView getView(){return view;}
    }
}
