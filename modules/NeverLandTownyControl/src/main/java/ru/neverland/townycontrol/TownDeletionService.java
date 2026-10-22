package ru.neverland.townycontrol;

import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.event.*;
import com.palmergames.bukkit.towny.event.town.TownPreUnclaimEvent;
import com.palmergames.bukkit.towny.object.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.TileState;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.neverland.core.*;
import static ru.neverland.townycontrol.DeletionFiles.*;

/** Town deletion is authorized once, planned without writes, then applied only to the captured plots. */
final class TownDeletionService implements Listener,TabExecutor {
    static final String PERMISSION="neverlandtownycontrol.towns.delete";
    enum Mode { FULL, REGISTERED }
    record Offer(UUID token,UUID town,String actor,Mode mode,String fingerprint,long expires) {}
    record ListedBlock(Plot plot,int x,int y,int z,String project,String material) {}
    private final NeverLandTownyControl plugin;private final TerrainProtection protection;private final Path root;
    private final Map<String,Offer> offers=new HashMap<>();private final Map<UUID,Job> jobs=new LinkedHashMap<>();
    private final Map<PluginCommand,CommandExecutor> original=new HashMap<>();private final Map<PluginCommand,CommandExecutor> wrappers=new HashMap<>();
    private final Set<UUID> executing=new HashSet<>();
    private static final class Job {
        UUID id,town;String name,actor,mode,fingerprint,state="PREPARING",error="";
        List<Plot> plots=new ArrayList<>();List<String> hashes=new ArrayList<>();int chunk,cursor;long changed,skipped,total;
        Path directory;boolean busy;
        boolean reserves(){return Set.of("PREPARING","ARMED","WAITING_DELETE","APPLYING","REVIEW").contains(state);}
    }
    TownDeletionService(NeverLandTownyControl plugin)throws IOException{
        this.plugin=plugin;root=plugin.getDataFolder().toPath().resolve("town-removal/jobs");Files.createDirectories(root);
        protection=new TerrainProtection(plugin);loadJobs();
        var cmd=Objects.requireNonNull(plugin.getCommand("nltdelete"));cmd.setExecutor(this);cmd.setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this,plugin);wrapTowny("town");wrapTowny("townyadmin");
        SuiteScheduler.get().runTask(plugin,()->{for(var job:jobs.values())if(job.state.equals("APPLYING"))apply(job);});
    }
    void stop(){for(var entry:original.entrySet())if(entry.getKey().getExecutor()==wrappers.get(entry.getKey()))entry.getKey().setExecutor(entry.getValue());offers.clear();}
    static String actor(CommandSender sender){return sender instanceof Player p?p.getUniqueId().toString():"console:"+sender.getName();}
    static boolean administrator(CommandSender sender){return sender.isOp()||sender.hasPermission(PERMISSION);}
    private void authorized(CommandSender sender,Town town,Mode mode){
        if(town==null)throw new IllegalArgumentException("Город уже удалён");
        if(administrator(sender))return;
        if(mode!=Mode.FULL||!(sender instanceof Player p)||town.getMayor()==null||!town.getMayor().getUUID().equals(p.getUniqueId())||!TownyUniverse.getInstance().getPermissionSource().testPermission(p,"towny.command.town.delete"))throw new IllegalArgumentException("Удалить этот город может только его мэр или администратор.");
    }
    private static Town town(UUID id){var t=TownyAPI.getInstance().getTown(id);if(t==null)throw new IllegalArgumentException("Город уже удалён");return t;}
    private static Town named(String name){var t=TownyAPI.getInstance().getTown(name);if(t==null)throw new IllegalArgumentException("Город не найден: "+name);return t;}
    static String fingerprint(Town town){
        var claims=town.getTownBlocks().stream().map(b->TerrainProtection.plot(b).key()+":"+b.getClaimedAt()).sorted().toList();
        return town.getUUID()+"|"+(town.getMayor()==null?"":town.getMayor().getUUID())+"|"+String.join(",",claims);
    }
    private void wrapTowny(String name){
        var owner=Bukkit.getPluginManager().getPlugin("Towny");if(!(owner instanceof org.bukkit.plugin.java.JavaPlugin java))throw new IllegalStateException("Towny недоступен");
        var command=Objects.requireNonNull(java.getCommand(name));var delegate=command.getExecutor();original.put(command,delegate);
        CommandExecutor wrapper=(sender,cmd,label,args)->{
            synchronized(SuiteState.lock()){
                try{
                    if(name.equals("town")&&args.length>=1&&Set.of("delete","del").contains(args[0].toLowerCase(Locale.ROOT))){
                        if(args.length>2)throw new IllegalArgumentException("/town delete [город]");
                        Town target=args.length==2?named(args[1]):sender instanceof Player p?TownyAPI.getInstance().getTown(p):null;
                        if(target==null)throw new IllegalArgumentException("Укажите город");request(sender,target,Mode.FULL);return true;
                    }
                    if(name.equals("townyadmin")&&args.length>=3&&args[0].equalsIgnoreCase("town")&&args[2].equalsIgnoreCase("delete")){
                        if(!administrator(sender)&&!TownyUniverse.getInstance().getPermissionSource().testPermission(sender,"towny.command.townyadmin.town.delete"))throw new IllegalArgumentException("Нет права удаления города");
                        request(sender,named(args[1]),Mode.FULL);return true;
                    }
                }catch(Exception ex){sender.sendMessage("§c"+ex.getMessage());return true;}
            }
            return delegate.onCommand(sender,cmd,label,args);
        };wrappers.put(command,wrapper);command.setExecutor(wrapper);
    }
    List<String> towns(){return TownyAPI.getInstance().getTowns().stream().map(Town::getName).sorted().toList();}
    void request(CommandSender sender,Town town,Mode mode)throws Exception{
        authorized(sender,town,mode);protection.verify();
        if(jobs.values().stream().anyMatch(j->j.town.equals(town.getUUID())&&j.reserves()))throw new IllegalStateException("Удаление этого города уже начато. Посмотреть состояние: /nltdelete status");
        int max=plugin.getConfig().getInt("town-removal.maximum-plots",256);
        if(town.getNumTownBlocks()>max)throw new IllegalArgumentException("Город слишком большой для удаления одной командой. Обратитесь к администрации.");
        var names=new TreeSet<String>();if(mode==Mode.REGISTERED)listed(town).forEach(b->names.add(b.project()));
        var offer=new Offer(UUID.randomUUID(),town.getUUID(),actor(sender),mode,fingerprint(town),System.currentTimeMillis()+60000);offers.put(actor(sender),offer);
        sender.sendMessage("§6Удалить город «"+town.getName()+"»?");
        if(mode==Mode.FULL){
            sender.sendMessage("§cТерритория вернётся к виду, который был до того, как её занял город.");
            sender.sendMessage("§cДома, фермы и другие постройки, появившиеся здесь позже, могут исчезнуть.");
        }else{
            sender.sendMessage("§aОбычные базы и ландшафт останутся. Будут разобраны только городские здания из списка ниже.");
            sender.sendMessage("§eЗдания: "+(names.isEmpty()?"список пуст — все постройки останутся":String.join(", ",names)));
        }
        sender.sendMessage("§eСундуки и вещи в них останутся на месте.");
        if(TownySettings.getTownRuinsEnabled())sender.sendMessage("§eСначала город станет руинами. Территория изменится после его окончательного удаления.");
        sender.sendMessage("§7Нажмите кнопку ниже. На подтверждение — одна минута.");
        sender.sendMessage(Component.text("[Да, удалить город]",NamedTextColor.RED).clickEvent(ClickEvent.runCommand("/nltdelete confirm "+offer.token()))
            .append(Component.text("   [Отмена]",NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("/nltdelete cancel"))));
        if(!(sender instanceof Player))sender.sendMessage("§7Подтвердить командой: /nltdelete confirm "+offer.token());
    }
    private List<ListedBlock> listed(Town town)throws Exception{
        Object value=ApiServices.call("NeverLandTownyBuilds","ru.neverland.townybuilds.api.TownyBuildsApi","registeredBuildingBlocks",new Class<?>[]{UUID.class},town.getUUID());
        if(!(value instanceof List<?> list))throw new IllegalStateException("Builds не вернул список блоков");
        var result=new ArrayList<ListedBlock>();
        for(Object b:list){var c=b.getClass();UUID world=(UUID)c.getMethod("worldId").invoke(b);int x=(int)c.getMethod("x").invoke(b),y=(int)c.getMethod("y").invoke(b),z=(int)c.getMethod("z").invoke(b);
            result.add(new ListedBlock(new Plot(world,Math.floorDiv(x,16),Math.floorDiv(z,16)),x,y,z,(String)c.getMethod("project").invoke(b),(String)c.getMethod("material").invoke(b)));
        }return List.copyOf(result);
    }
    private void confirm(CommandSender sender,UUID token)throws Exception{
        Offer offer=offers.get(actor(sender));
        if(offer==null||!offer.token().equals(token)||offer.expires()<System.currentTimeMillis())throw new IllegalArgumentException("Эта кнопка больше не действует. Начните удаление заново.");
        Town town=town(offer.town());authorized(sender,town,offer.mode());protection.verify();
        if(!offer.fingerprint().equals(fingerprint(town)))throw new IllegalArgumentException("Мэр или территория города изменились. Запросите новое подтверждение.");
        offers.remove(actor(sender));var blocks=offer.mode()==Mode.REGISTERED?listed(town):List.<ListedBlock>of();
        Job job=new Job();job.id=UUID.randomUUID();job.town=town.getUUID();job.name=town.getName();job.actor=actor(sender);job.mode=offer.mode().name();job.fingerprint=offer.fingerprint();job.directory=root.resolve(job.id.toString());
        var claims=List.copyOf(town.getTownBlocks());job.plots=claims.stream().map(TerrainProtection::plot).toList();
        if(jobs.values().stream().anyMatch(j->j.town.equals(job.town)&&j.reserves()))throw new IllegalStateException("Удаление уже готовится");
        save(job);jobs.put(job.id,job);sender.sendMessage("§eПроверяем территорию перед удалением. Пожалуйста, подождите.");
        prepare(job,sender,claims,blocks,0);
    }
    private void prepare(Job job,CommandSender sender,List<TownBlock> claims,List<ListedBlock> listed,int index){
        if(index>=claims.size()){SuiteScheduler.reply(plugin,sender,()->commit(job,sender));return;}
        TownBlock claim=claims.get(index);Plot p=job.plots.get(index);World world=Bukkit.getWorld(p.world());
        if(world==null){fail(job,"Мир участка не загружен",false);tell(sender,"§cУдаление сейчас недоступно. Город и постройки сохранены. Обратитесь к администрации.");return;}
        SuiteScheduler.region(plugin,new Location(world,p.x()*16,world.getMinHeight(),p.z()*16),()->{
            try{
                if(!job.state.equals("PREPARING")||!job.fingerprint.equals(fingerprint(town(job.town))))throw new IllegalStateException("Территория города изменилась");
                var chunk=world.getChunkAt(p.x(),p.z());var snapshot=chunk.getChunkSnapshot(false,false,false);int min=world.getMinHeight(),max=world.getMaxHeight();
                var tiles=new HashSet<String>();for(var state:chunk.getTileEntities())tiles.add(state.getX()+":"+state.getY()+":"+state.getZ());
                SuiteScheduler.get().runTaskAsynchronously(plugin,()->{
                    try{
                        var changes=new LinkedHashMap<String,Change>();
                        if(job.mode.equals("FULL")){
                            Baseline baseline=protection.baseline(claim,job.town,min,max);
                            for(int z=0;z<16;z++)for(int x=0;x<16;x++)for(int y=max-1;y>min;y--){
                                String before=snapshot.getBlockData(x,y,z).getAsString(),after=baseline.at(x,y,z);
                                addChange(changes,tiles,new Change(p.x()*16+x,y,p.z()*16+z,before,after));
                            }
                        }else for(var b:listed)if(b.plot().equals(p)&&b.y()>min&&b.y()<max){
                            var data=snapshot.getBlockData(b.x()&15,b.y(),b.z()&15);
                            if(data.getMaterial().name().equals(b.material()))addChange(changes,tiles,new Change(b.x(),b.y(),b.z(),data.getAsString(),"minecraft:air"));
                        }
                        var plan=new ChunkPlan(p,min,max,List.copyOf(changes.values()));
                        SuiteScheduler.get().runTask(plugin,()->finishPlan(job,sender,claims,listed,index,plan));
                    }catch(Exception ex){SuiteScheduler.get().runTask(plugin,()->{fail(job,ex.getMessage(),false);preparationFailed(sender,ex);});}
                });
            }catch(Exception ex){fail(job,ex.getMessage(),false);preparationFailed(sender,ex);}
        });
    }
    private void finishPlan(Job job,CommandSender sender,List<TownBlock> claims,List<ListedBlock> listed,int index,ChunkPlan raw){
        try{
            if(!job.state.equals("PREPARING"))return;
            // Parse every target before removing the town. Legacy snapshots can omit default properties
            // or contain block names that the current Minecraft version no longer understands.
            var canonical=new HashMap<String,String>();var changes=new ArrayList<Change>();
            for(var c:raw.changes()){
                String after=canonical.computeIfAbsent(c.after(),s->Bukkit.createBlockData(s).getAsString());
                if(!c.before().equals(after))changes.add(new Change(c.x(),c.y(),c.z(),c.before(),after));
            }
            var plan=new ChunkPlan(raw.plot(),raw.minY(),raw.maxY(),changes);
            validateColumns(plan,plugin.getConfig().getInt("town-removal.maximum-column-removals",32));
            if(job.total+changes.size()>plugin.getConfig().getInt("town-removal.maximum-changed-blocks",1000000))throw new IllegalStateException("План слишком большой");
            SuiteScheduler.get().runTaskAsynchronously(plugin,()->{
                try{
                    Path path=job.directory.resolve(index+".gz");savePlan(path,plan);String hash=sha(path);
                    SuiteScheduler.get().runTask(plugin,()->{
                        try{if(!job.state.equals("PREPARING"))return;job.hashes.add(hash);job.total+=changes.size();save(job);prepare(job,sender,claims,listed,index+1);}
                        catch(Exception ex){fail(job,ex.getMessage(),false);preparationFailed(sender,ex);}
                    });
                }catch(Exception ex){SuiteScheduler.get().runTask(plugin,()->{fail(job,ex.getMessage(),false);preparationFailed(sender,ex);});}
            });
        }catch(Exception ex){fail(job,ex.getMessage(),false);preparationFailed(sender,ex);}
    }
    private static void addChange(Map<String,Change> result,Set<String> tiles,Change c){
        String key=c.x()+":"+c.y()+":"+c.z();if(c.before().equals(c.after())||tiles.contains(key)||protectedBlock(c.before())||protectedBlock(c.after()))return;result.put(key,c);
    }
    static void validateColumns(ChunkPlan plan,int maximum){
        if(maximum<1||maximum>256)throw new IllegalArgumentException("maximum-column-removals: 1..256");
        var air=Set.of("minecraft:air","minecraft:cave_air","minecraft:void_air");
        var counts=new HashMap<String,Integer>();for(var c:plan.changes())if(!air.contains(material(c.before()))&&air.contains(material(c.after()))){
            String column=c.x()+":"+c.z();if(counts.merge(column,1,Integer::sum)>maximum)throw new IllegalArgumentException("Остановлен опасный вертикальный снос в "+column+". Проверьте снимок, используйте registered для сохранения базы.");
        }
    }
    private void commit(Job job,CommandSender sender){
        try{
            if(!job.state.equals("PREPARING"))return;Town town=town(job.town);authorized(sender,town,Mode.valueOf(job.mode));protection.verify();
            if(sender instanceof Player p&&!p.isOnline())throw new IllegalStateException("Инициатор вышел из игры");
            if(!job.fingerprint.equals(fingerprint(town)))throw new IllegalStateException("Мэр или территория города изменились");
            job.state="ARMED";save(job);executing.add(job.town);
            try{TownyUniverse.getInstance().getDataSource().removeTown(town,administrator(sender)?DeleteTownEvent.Cause.ADMIN_COMMAND:DeleteTownEvent.Cause.COMMAND,sender);}
            finally{executing.remove(job.town);}
            var remaining=TownyAPI.getInstance().getTown(job.town);
            if(remaining==null){job.state="APPLYING";save(job);apply(job);sender.sendMessage(job.mode.equals("FULL")?"§aГород удалён. Возвращаем территорию к прежнему виду.":"§aГород удалён. Разбираем городские здания; обычные базы сохраняются.");}
            else if(remaining.isRuined()){job.state="WAITING_DELETE";save(job);sender.sendMessage("§eГород стал руинами. Территория изменится после его окончательного удаления.");}
            else {job.state="CANCELLED";save(job);sender.sendMessage("§eУдаление отменено. Город и постройки сохранены.");}
            audit(job,"REQUESTED");
        }catch(Exception ex){boolean removed=TownyAPI.getInstance().getTown(job.town)==null;fail(job,ex.getMessage(),removed);tell(sender,sender instanceof Player?(removed?"§cГород удалён, но изменение территории остановлено. Обратитесь к администрации.":"§cУдаление остановлено. Город и постройки сохранены. Обратитесь к администрации."):"§cУдаление остановлено: "+ex.getMessage());}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void before(PreDeleteTownEvent event){synchronized(SuiteState.lock()){
        if(event.getCause().isCommand()&&!executing.contains(event.getTown().getUUID())){event.setCancelled(true);event.setCancelMessage("Удаление требует предупреждения: используйте /town delete заново.");}
    }}
    @EventHandler(priority=EventPriority.MONITOR) public void deleted(DeleteTownEvent event){synchronized(SuiteState.lock()){
        for(var job:jobs.values())if(job.town.equals(event.getTownUUID())&&job.state.equals("WAITING_DELETE"))try{job.state="APPLYING";save(job);apply(job);}catch(Exception ex){fail(job,ex.getMessage(),true);}
    }}
    @EventHandler(priority=EventPriority.MONITOR) public void reclaimed(com.palmergames.bukkit.towny.event.town.TownReclaimedEvent event){synchronized(SuiteState.lock()){
        for(var job:jobs.values())if(job.town.equals(event.getTown().getUUID())&&job.state.equals("WAITING_DELETE"))try{job.state="CANCELLED";job.error="Город восстановлен из руин";save(job);audit(job,"CANCELLED");}catch(Exception ex){fail(job,ex.getMessage(),true);}
    }}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void beforeRuins(com.palmergames.bukkit.towny.event.town.TownPreRuinedEvent event){synchronized(SuiteState.lock()){
        // Towny falls through to PreDeleteTownEvent after cancelling ruin creation.
        if(event.getCause()==DeleteTownEvent.Cause.COMMAND&&!executing.contains(event.getTown().getUUID()))event.setCancelled(true);
    }}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void preClaim(TownPreClaimEvent event){synchronized(SuiteState.lock()){
        Plot plot=TerrainProtection.plot(event.getTownBlock());if(jobs.values().stream().anyMatch(j->j.reserves()&&j.plots.contains(plot))){event.setCancelled(true);event.setCancelMessage("Участок ожидает завершения удаления города (%s/%s). Обратитесь к администратору.");}
    }}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void preUnclaim(TownPreUnclaimEvent event){synchronized(SuiteState.lock()){
        if(event.getTown()!=null&&jobs.values().stream().anyMatch(j->j.town.equals(event.getTown().getUUID())&&j.reserves())){event.setCancelled(true);event.setCancelMessage("Готовится удаление города; изменение границ временно остановлено.");}
    }}
    @EventHandler public void quit(PlayerQuitEvent event){synchronized(SuiteState.lock()){
        String actor=actor(event.getPlayer());offers.remove(actor);
        for(var job:jobs.values())if(job.actor.equals(actor)&&job.state.equals("PREPARING"))fail(job,"Инициатор вышел из игры до удаления города",false);
    }}
    private void apply(Job job){
        if(job.busy||!job.state.equals("APPLYING"))return;
        if(TownyAPI.getInstance().getTown(job.town)!=null){fail(job,"Город существует: автоматический откат запрещён",true);return;}
        if(job.chunk>=job.plots.size()){try{job.state="DONE";save(job);audit(job,"COMPLETED");}catch(Exception ex){fail(job,ex.getMessage(),true);}return;}
        job.busy=true;int part=job.chunk;Path path=job.directory.resolve(part+".gz");
        SuiteScheduler.get().runTaskAsynchronously(plugin,()->{
            try{
                if(!sha(path).equals(job.hashes.get(part)))throw new IOException("Контрольная сумма плана не совпадает");
                var plan=loadPlan(path);if(!plan.plot().equals(job.plots.get(part)))throw new IOException("План относится к другому участку");
                World world=Bukkit.getWorld(plan.plot().world());if(world==null)throw new IOException("Мир не загружен");
                SuiteScheduler.region(plugin,new Location(world,plan.plot().x()*16,plan.minY(),plan.plot().z()*16),()->applyBatch(job,plan,world));
            }catch(Exception ex){SuiteScheduler.get().runTask(plugin,()->fail(job,ex.getMessage(),true));}
        });
    }
    private void applyBatch(Job job,ChunkPlan plan,World world){
        try{
            if(!job.state.equals("APPLYING")){job.busy=false;return;}
            if(job.cursor>plan.changes().size())throw new IllegalStateException("Повреждён указатель прогресса плана");
            if(world.getMinHeight()!=plan.minY()||world.getMaxHeight()!=plan.maxY())throw new IllegalStateException("Высота мира изменилась");
            if(TownyAPI.getInstance().getTownBlock(new Location(world,plan.plot().x()*16,plan.minY(),plan.plot().z()*16))!=null)throw new IllegalStateException("Участок снова занят городом; откат остановлен");
            int end=Math.min(plan.changes().size(),job.cursor+256);
            for(int i=job.cursor;i<end;i++){
                var c=plan.changes().get(i);var block=world.getBlockAt(c.x(),c.y(),c.z());
                var decision=decide(c,block.getBlockData().getAsString(),block.getState() instanceof TileState,false);
                if(decision==Decision.APPLY){var after=Bukkit.createBlockData(c.after());if(after.getMaterial().isAir()&&block.getState() instanceof TileState){job.skipped++;continue;}block.setBlockData(after,false);job.changed++;}
                else if(decision==Decision.SKIP)job.skipped++;
            }
            job.cursor=end;if(end==plan.changes().size()){job.chunk++;job.cursor=0;}
            // Persist progress before scheduling the next batch. Replays compare against 'before' and are idempotent.
            save(job);job.busy=false;
            if(job.chunk<job.plots.size()&&job.cursor>0){job.busy=true;SuiteScheduler.regionLater(plugin,new Location(world,plan.plot().x()*16,plan.minY(),plan.plot().z()*16),()->applyBatch(job,plan,world),1);}
            else SuiteScheduler.get().runTask(plugin,()->apply(job));
        }catch(Exception ex){fail(job,ex.getMessage(),true);}
    }
    private void fail(Job job,String error,boolean review){job.busy=false;job.state=review?"REVIEW":"CANCELLED";job.error=String.valueOf(error);try{save(job);audit(job,"STOPPED");}catch(Exception ex){plugin.getLogger().severe("Не сохранено состояние удаления "+job.id+": "+ex.getMessage());}plugin.getLogger().warning("Удаление "+job.name+" остановлено: "+error);}
    private void tell(CommandSender sender,String message){SuiteScheduler.reply(plugin,sender,()->sender.sendMessage(message));}
    private void preparationFailed(CommandSender sender,Exception error){tell(sender,sender instanceof Player?"§cНе удалось подготовить удаление. Город и постройки сохранены. Обратитесь к администрации.":"§cГород сохранён. Подготовка остановлена: "+error.getMessage());}
    private static String playerStatus(Job job){return switch(job.state){
        case "PREPARING","ARMED"->"Проверяем территорию перед удалением.";
        case "WAITING_DELETE"->"Город стал руинами и ожидает окончательного удаления.";
        case "APPLYING"->job.mode.equals("FULL")?"Город удалён. Возвращаем территорию к прежнему виду.":"Город удалён. Разбираем городские здания.";
        case "DONE"->"Удаление города и изменение территории завершены.";
        case "CANCELLED"->"Удаление остановлено. Оставшиеся изменения отменены.";
        default->"Удаление приостановлено. Обратитесь к администрации.";
    };}
    private void audit(Job job,String status){AuditTrail.record(plugin,"town-delete",job.id+":"+status,"ADMIN_TOWN_DELETE",status,new AuditRecord.Party("ACTOR",job.actor,job.actor,""),new AuditRecord.Party("TOWN",job.town.toString(),job.name,""),AuditRecord.Party.unknown(),"",job.changed,"",job.mode+"; skipped="+job.skipped+"; "+job.error);}
    private void save(Job job)throws IOException{
        var y=new YamlConfiguration();y.set("schema",1);y.set("id",job.id.toString());y.set("town",job.town.toString());y.set("name",job.name);y.set("actor",job.actor);y.set("mode",job.mode);y.set("fingerprint",job.fingerprint);y.set("state",job.state);y.set("error",job.error);
        y.set("chunk",job.chunk);y.set("cursor",job.cursor);y.set("changed",job.changed);y.set("skipped",job.skipped);y.set("total",job.total);y.set("hashes",job.hashes);
        for(int i=0;i<job.plots.size();i++){var p=job.plots.get(i);y.set("plots."+i+".world",p.world().toString());y.set("plots."+i+".x",p.x());y.set("plots."+i+".z",p.z());}
        AtomicFiles.write(job.directory.resolve("job.yml"),y::saveToString);
    }
    private void loadJobs()throws IOException{
        try(var directories=Files.list(root)){for(Path directory:directories.filter(Files::isDirectory).toList()){
            Path file=directory.resolve("job.yml");if(!Files.exists(file))continue;var y=SafeYaml.load(file);if(SafeYaml.intValue(y,"schema")!=1)throw new IOException("Неизвестный журнал удаления");
            Job j=new Job();j.directory=directory;j.id=UUID.fromString(SafeYaml.text(y,"id"));if(!directory.getFileName().toString().equals(j.id.toString()))throw new IOException("Неверный ID журнала");j.town=UUID.fromString(SafeYaml.text(y,"town"));j.name=SafeYaml.text(y,"name");j.actor=SafeYaml.text(y,"actor");j.mode=Mode.valueOf(SafeYaml.text(y,"mode")).name();j.fingerprint=SafeYaml.text(y,"fingerprint");j.state=SafeYaml.text(y,"state");j.error=y.getString("error","");
            if(!Set.of("PREPARING","ARMED","WAITING_DELETE","APPLYING","DONE","CANCELLED","REVIEW").contains(j.state))throw new IOException("Неверное состояние удаления");
            var section=y.getConfigurationSection("plots");if(section!=null)for(int i=0;i<section.getKeys(false).size();i++){String k="plots."+i+".";j.plots.add(new Plot(UUID.fromString(SafeYaml.text(y,k+"world")),SafeYaml.intValue(y,k+"x"),SafeYaml.intValue(y,k+"z")));}
            j.hashes=new ArrayList<>(y.getStringList("hashes"));j.chunk=y.getInt("chunk");j.cursor=y.getInt("cursor");j.changed=y.getLong("changed");j.skipped=y.getLong("skipped");j.total=y.getLong("total");
            if(j.chunk<0||j.chunk>j.plots.size()||j.cursor<0||j.cursor>2_000_000)throw new IOException("Повреждён прогресс удаления");
            if(j.state.equals("PREPARING"))j.state="CANCELLED";
            if(Set.of("ARMED","WAITING_DELETE").contains(j.state)&&TownyAPI.getInstance().getTown(j.town)==null)j.state="APPLYING";
            else if(j.state.equals("ARMED"))j.state="REVIEW";
            else if(j.state.equals("WAITING_DELETE")&&!TownyAPI.getInstance().getTown(j.town).isRuined()){j.state="CANCELLED";j.error="Город восстановлен из руин";}
            if(j.state.equals("APPLYING")&&j.hashes.size()!=j.plots.size())throw new IOException("Неполный план удаления");
            AtomicFiles.loaded(file);jobs.put(j.id,j);save(j);
        }}
    }
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args){synchronized(SuiteState.lock()){
        try{
            if(args.length==1&&args[0].equalsIgnoreCase("cancel")){var removed=offers.remove(actor(sender));sender.sendMessage(removed!=null?"§aУдаление отменено. Город сохранён.":"§eНет удаления, которое можно отменить этой кнопкой. Посмотреть состояние: /nltdelete status");return true;}
            if(args.length==2&&args[0].equalsIgnoreCase("confirm")){confirm(sender,UUID.fromString(args[1]));return true;}
            if(args.length==1&&args[0].equalsIgnoreCase("status")){boolean found=false;for(var j:jobs.values())if(administrator(sender)||j.actor.equals(actor(sender))){found=true;sender.sendMessage(administrator(sender)?"§e"+j.name+" §f"+j.state+"; изменено="+j.changed+", пропущено="+j.skipped+"; "+j.id+(j.error.isEmpty()?"":"; "+j.error):"§e"+j.name+" §f"+playerStatus(j));}if(!found)sender.sendMessage("§eВы ещё не запускали удаление города.");return true;}
            if(args.length==2&&Set.of("resume","abandon").contains(args[0].toLowerCase(Locale.ROOT))){
                if(!administrator(sender))throw new IllegalArgumentException("Нет права "+PERMISSION);Job j=jobs.get(UUID.fromString(args[1]));if(j==null||!j.state.equals("REVIEW"))throw new IllegalArgumentException("Операция не ожидает сверки");
                if(args[0].equalsIgnoreCase("abandon")){j.state="CANCELLED";save(j);audit(j,"ABANDONED");sender.sendMessage("§eОставшиеся изменения отменены. Уже применённые изменения не обращены.");}
                else {if(TownyAPI.getInstance().getTown(j.town)!=null)throw new IllegalStateException("Город существует; продолжение запрещено");if(j.hashes.size()!=j.plots.size())throw new IllegalStateException("План неполный");j.state="APPLYING";j.error="";save(j);apply(j);}return true;
            }
            if(args.length==2&&Set.of("full","registered").contains(args[1].toLowerCase(Locale.ROOT))){if(!administrator(sender))throw new IllegalArgumentException("Нет права "+PERMISSION);request(sender,named(args[0]),Mode.valueOf(args[1].toUpperCase(Locale.ROOT)));return true;}
            sender.sendMessage(administrator(sender)?"§fМэр: /town delete. Администратор: /nltdelete <город> <full|registered>. /nltdelete status | cancel | confirm <код> | resume|abandon <ID>":"§fУдалить свой город: /town delete. Посмотреть состояние удаления: /nltdelete status.");
        }catch(Exception ex){sender.sendMessage("§c"+ex.getMessage());}return true;
    }}
    @Override public List<String> onTabComplete(CommandSender sender,Command cmd,String label,String[] args){synchronized(SuiteState.lock()){
        if(!administrator(sender))return args.length==1?List.of("confirm","cancel","status"):List.of();
        if(args.length==1){var values=new ArrayList<>(towns());values.addAll(List.of("status","confirm","cancel","resume","abandon"));return values.stream().filter(v->v.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).toList();}
        return args.length==2&&TownyAPI.getInstance().getTown(args[0])!=null?List.of("full","registered"):List.of();
    }}
}
