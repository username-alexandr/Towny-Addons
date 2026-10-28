package ru.neverland.townysieges;

import java.util.*;
import java.util.concurrent.*;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import ru.neverland.core.*;

/** Army raids when SiegeWar is absent; existing SiegeWar battle windows remain authoritative otherwise. */
public final class RaidController {
    public record Permit(UUID actor,UUID attacker,UUID defender,UUID raid,long issued,boolean siegeWar){}
    private final NeverLandTownySiegesPlus plugin;
    private final SiegeService siege;
    private final BuildsAccess diplomacy=new BuildsAccess();
    private final RaidStore store;
    private volatile TntSettings settings;
    // Immutable authority is published by the storage worker even when notification schedulers are paused.
    private volatile List<RaidStore.Raid> raids=List.of();
    private volatile boolean loaded,closed;
    private TntRaidEngine engine;
    public RaidController(NeverLandTownySiegesPlus plugin,SiegeService siege,TntSettings settings) {
        this.plugin=plugin;this.siege=siege;this.settings=settings;
        store=new RaidStore(plugin.getDataFolder().toPath().resolve("raids.db"),e->plugin.getLogger().log(java.util.logging.Level.SEVERE,"Хранилище рейдов недоступно; разрушение приостановлено",e));
        store.ready().thenCompose(v->store.raids()).whenComplete((rows,error)->{
            if(error==null&&!closed){raids=rows;loaded=true;}
        });
    }
    public RaidStore store(){return store;}
    public TntSettings settings(){return settings;}
    public void engine(TntRaidEngine value){engine=value;}
    public void reload(TntSettings next){settings=next;}
    public boolean ready(){return !closed&&loaded&&settings.enabled()&&store.healthy()&&plugin.isEnabled()&&!SuiteState.frozen(plugin.getName());}
    public boolean healthy(){try{return ready()&&army().ready();}catch(Exception|LinkageError unavailable){return false;}}
    private boolean siegeWarInstalled(){if(plugin.camps()!=null&&plugin.camps().settings().enabled())return false;var p=Bukkit.getPluginManager().getPlugin("SiegeWar");return p!=null;}
    private ApiServices.Connection army()throws Exception {
        var api=ApiServices.require("NeverLandTownyArmy","ru.neverland.townyarmy.api.TownyArmyApi","healthy","isMobilized","serviceRecord");
        if(!Boolean.TRUE.equals(api.invoke("healthy",new Class<?>[]{})))throw new IllegalStateException("Армия временно недоступна");
        return api;
    }
    boolean mobilized(UUID player)throws Exception{return Boolean.TRUE.equals(army().invoke("isMobilized",new Class<?>[]{UUID.class},player));}
    private boolean commander(Player player,Town town)throws Exception {
        if(player.hasPermission("neverlandtownysiegesplus.admin")||town.isMayor(TownyAPI.getInstance().getResident(player.getUniqueId())))return true;
        Object row=((Optional<?>)army().invoke("serviceRecord",new Class<?>[]{UUID.class},player.getUniqueId())).orElse(null);
        return row instanceof Map<?,?> map&&town.getUUID().equals(map.get("town"))&&"general".equals(map.get("rank"));
    }
    void relations(Town own,Town target)throws Exception {
        if(own==null||target==null||own==target)throw new IllegalArgumentException("Нужен другой существующий город");
        if(own.isAlliedWith(target)||target.isAlliedWith(own)||own.isNeutral()||target.isNeutral()
                ||diplomacy.protectedByTreaty(own.getUUID(),target.getUUID())||diplomacy.protectedByTreaty(target.getUUID(),own.getUUID()))
            throw new IllegalArgumentException("Союзный, мирный или нейтральный город нельзя атаковать");
        if(NewcomerProtection.remaining(plugin,own.getUUID())>0||NewcomerProtection.remaining(plugin,target.getUUID())>0)
            throw new IllegalArgumentException("Рейд недоступен, пока у одного из городов действует щит новичка");
    }
    private long online(UUID town) {return Bukkit.getOnlinePlayers().stream().filter(p->{var t=SiegeService.town(p.getUniqueId());return t!=null&&t.getUUID().equals(town);}).count();}
    private void defenders(Town town){if(online(town.getUUID())<settings.defendersOnline())throw new IllegalArgumentException("В городе недостаточно защитников в сети");}
    public Optional<RaidStore.Raid> raid(UUID attacker,UUID defender,long now) {
        return raids.stream().filter(r->r.attacker().equals(attacker)&&r.defender().equals(defender)&&r.live(now)).findFirst();
    }
    /** Immutable declarations; no foreign entity state or world blocks are read here. */
    public List<RaidStore.Raid> declarations(){
        if(!ready()||siegeWarInstalled()||openWars().isEmpty())return List.of();try{army();}catch(Exception|LinkageError unavailable){return List.of();}long now=System.currentTimeMillis();
        return raids.stream().filter(r->{if(!r.live(now))return false;try{relations(TownyAPI.getInstance().getTown(r.attacker()),TownyAPI.getInstance().getTown(r.defender()));return plugin.camps()==null||plugin.camps().active(r.attacker(),r.defender());}catch(Exception|LinkageError denied){return false;}}).toList();
    }
    boolean knownDeclaration(UUID defender){return !closed&&settings.enabled()&&raids.stream().anyMatch(r->r.defender().equals(defender)&&r.live(System.currentTimeMillis()));}
    List<RaidStore.Raid> openWars(){return raids.stream().filter(r->r.live(System.currentTimeMillis())).toList();}
    boolean battle(RaidStore.Raid war){try{return declarations().stream().anyMatch(r->r.id().equals(war.id())&&r.fighting(System.currentTimeMillis()))&&online(war.defender())>=settings.defendersOnline();}catch(Exception|LinkageError denied){return false;}}
    public boolean managed(UUID defender) {
        if(closed||!settings.enabled()||!plugin.isEnabled())return false;
        if(raids.stream().anyMatch(r->r.defender().equals(defender)&&r.live(System.currentTimeMillis())))return true;
        if(!siegeWarInstalled())return false;
        try{var town=TownyAPI.getInstance().getTown(defender);if(town==null)return false;var c=siege.war().context(town,null);return c.active()&&c.battle();}
        catch(Exception|LinkageError unavailable){return false;}
    }
    /** Called on the actor's entity thread. The permit contains no live entity references. */
    public Permit authorize(Player actor,UUID defender)throws Exception {
        if(!ready()||!SuiteScheduler.owns(actor)||!actor.isOnline()||actor.isDead()||actor.getGameMode()!=GameMode.SURVIVAL
                ||!actor.hasPermission("neverlandtownysiegesplus.raid.use"))throw new IllegalArgumentException("Боевой TNT сейчас недоступен");
        Town own=SiegeService.town(actor.getUniqueId()),target=TownyAPI.getInstance().getTown(defender);
        relations(own,target);defenders(target);
        if(!mobilized(actor.getUniqueId()))throw new IllegalArgumentException("Для боевого TNT нужна мобилизация в армии города");
        long now=System.currentTimeMillis();
        if(siegeWarInstalled()) {
            var context=siege.war().context(target,actor);
            if(!context.active()||!context.battle()||!context.side().equals("ATTACKERS"))throw new IllegalArgumentException("Боевой TNT доступен атакующим во время боевой сессии SiegeWar");
            return new Permit(actor.getUniqueId(),own.getUUID(),defender,siege.war().identity(target),now,true);
        }
        var raid=raid(own.getUUID(),defender,now).orElseThrow(()->new IllegalArgumentException("Сначала объявите рейд: /sieges raid <город>"));
        if(plugin.camps()!=null&&!plugin.camps().active(own.getUUID(),defender))throw new IllegalArgumentException("Осадный лагерь недоступен");
        if(!raid.fighting(now))throw new IllegalArgumentException("Ещё идёт подготовка к рейду");
        return new Permit(actor.getUniqueId(),own.getUUID(),defender,raid.id(),now,false);
    }
    /** Recheck after database work, on the block region; no foreign entity state is read. */
    public boolean valid(Permit permit) {
        if(!ready()||System.currentTimeMillis()-permit.issued()>5000||Bukkit.getPlayer(permit.actor())==null)return false;
        try {
            Town own=SiegeService.town(permit.actor()),target=TownyAPI.getInstance().getTown(permit.defender());
            if(own==null||!own.getUUID().equals(permit.attacker()))return false;
            relations(own,target);defenders(target);if(!mobilized(permit.actor()))return false;
            if(permit.siegeWar()) {var c=siege.war().context(target,null);return siegeWarInstalled()&&c.active()&&c.battle()&&siege.war().identity(target).equals(permit.raid());}
            return !siegeWarInstalled()&&(plugin.camps()==null||plugin.camps().active(own.getUUID(),target.getUUID()))&&raid(own.getUUID(),target.getUUID(),System.currentTimeMillis()).filter(r->r.id().equals(permit.raid())&&r.fighting(System.currentTimeMillis())).isPresent();
        }catch(Exception|LinkageError unavailable){return false;}
    }
    public boolean physicalGate(Player player,UUID defender) {
        try{return authorize(player,defender)!=null;}catch(Exception|LinkageError denied){return false;}
    }
    public void declare(Player actor,String name)throws Exception {
        if(!ready())throw new IllegalArgumentException("Рейды отключены или хранилище ещё загружается");
        if(siegeWarInstalled())throw new IllegalArgumentException("Войнами управляет SiegeWar. Боевой TNT работает в его боевых сессиях");
        if(!actor.hasPermission("neverlandtownysiegesplus.raid.use"))throw new IllegalArgumentException("Нет прав на рейды");
        Town own=SiegeService.town(actor.getUniqueId()),target=TownyAPI.getInstance().getTown(name);
        relations(own,target);defenders(target);
        if(plugin.repairs()!=null&&plugin.repairs().busy(target.getUUID()))throw new IllegalArgumentException("У города завершается оплаченный ремонт. Повторите объявление чуть позже");
        if(actor.getGameMode()!=GameMode.SURVIVAL||!mobilized(actor.getUniqueId())||!commander(actor,own))
            throw new IllegalArgumentException("Рейд объявляет мобилизованный мэр или генерал");
        if(plugin.camps()!=null&&plugin.camps().settings().enabled()){
            plugin.camps().muster(actor,target,()->{try{validateCommander(actor,own,target);defenders(target);if(plugin.repairs()!=null&&plugin.repairs().busy(target.getUUID()))throw new IllegalArgumentException("У города завершается ремонт");start(actor,own,target);}catch(Exception error){reply(actor,error.getMessage());}});return;
        }
        start(actor,own,target);
    }
    void validateCommander(Player actor,Town own,Town target)throws Exception{
        if(!ready())throw new IllegalArgumentException("Рейды сейчас недоступны");relations(own,target);var current=SiegeService.town(actor.getUniqueId());
        if(current==null||!current.getUUID().equals(own.getUUID())||!actor.hasPermission("neverlandtownysiegesplus.raid.use")||actor.getGameMode()!=GameMode.SURVIVAL||!mobilized(actor.getUniqueId())||!commander(actor,own))throw new IllegalArgumentException("Нужен мобилизованный мэр или генерал своего города");
    }
    private void start(Player actor,Town own,Town target){
        long now=System.currentTimeMillis(),start=now+settings.warmupSeconds()*1000L,end=start+settings.durationSeconds()*1000L;
        var raid=new RaidStore.Raid(UUID.randomUUID(),own.getUUID(),target.getUUID(),actor.getUniqueId(),now,start,end,end+settings.cooldownSeconds()*1000L,"OPEN");
        store.start(raid).whenComplete((rows,error)->{if(error==null&&!closed)raids=rows;publish(()->{
            if(error!=null){reply(actor,"&e"+problem(error));return;}
            if(plugin.camps()!=null&&!plugin.camps().active(own.getUUID(),target.getUUID())){cancel(Bukkit.getConsoleSender(),target.getUUID());reply(actor,"Лагерь потерян до начала рейда");return;}
            AuditTrail.record(plugin,"declare",raid.id().toString(),"RAID_DECLARED","READY",AuditTrail.player(actor.getUniqueId()),AuditTrail.town(own.getUUID()),AuditTrail.town(target.getUUID()),"",0,"","Подготовка "+settings.warmupSeconds()+" сек.; бой "+settings.durationSeconds()+" сек.");
            notifyTown(own.getUUID(),"Рейд на "+target.getName()+" объявлен. Подготовка: "+settings.warmupSeconds()+" сек. Боевой TNT — только для мобилизованных.");
            notifyTown(target.getUUID(),"Город "+own.getName()+" объявил рейд. Подготовка: "+settings.warmupSeconds()+" сек. Проверьте ворота и оборону.");
        });});
    }
    public void cancel(CommandSender sender,UUID town) {
        var cancelled=raids.stream().filter(r->(r.attacker().equals(town)||r.defender().equals(town))&&r.live(System.currentTimeMillis())).toList();
        store.cancel(town,sender.getName(),System.currentTimeMillis()).whenComplete((rows,error)->{if(error==null&&!closed)raids=rows;publish(()->{
            if(error!=null){reply(sender,"&e"+problem(error));return;}for(var raid:cancelled)AuditTrail.record(plugin,"cancel",raid.id().toString(),"RAID_CANCELLED","CANCELLED",sender instanceof Player p?AuditTrail.player(p.getUniqueId()):AuditRecord.Party.system(),AuditTrail.town(raid.attacker()),AuditTrail.town(raid.defender()),"",0,"","Нейтральная остановка рейда");reply(sender,"Рейд остановлен без поражения города и списания наград.");notifyTown(town,"Администратор остановил рейд.");
        });});
    }
    public void status(CommandSender sender) {
        reply(sender,"TNT: "+(healthy()?"готов":"отключён/армия или хранилище недоступны")+"; источник рейдов: "+(siegeWarInstalled()?"SiegeWar":"армия города"));
        for(var r:raids)if(r.live(System.currentTimeMillis())) {
            var target=TownyAPI.getInstance().getTown(r.defender());var attacker=TownyAPI.getInstance().getTown(r.attacker());
            reply(sender,(attacker==null?r.attacker():attacker.getName())+" → "+(target==null?r.defender():target.getName())+": "+(r.fighting(System.currentTimeMillis())?"идёт бой":"подготовка"));
        }
    }
    public String phase(UUID town){return raids.stream().filter(r->(r.attacker().equals(town)||r.defender().equals(town))&&r.live(System.currentTimeMillis())).findFirst().map(r->r.fighting(System.currentTimeMillis())?"Идёт армейский рейд":"Подготовка к армейскому рейду").orElse("Армейских рейдов сейчас нет");}
    public void history(CommandSender sender,UUID town) {
        store.history(town).whenComplete((rows,error)->publish(()->{if(error!=null){reply(sender,problem(error));return;}if(rows.isEmpty())reply(sender,"Записей рейдов пока нет");for(var row:rows)reply(sender,new java.util.Date(row.time())+" "+row.action()+"; инициатор="+row.actor()+"; блоков="+row.blocks());}));
    }
    void notifyTown(UUID town,String text){SuiteScheduler.players(plugin,p->{var own=SiegeService.town(p.getUniqueId());if(own!=null&&own.getUUID().equals(town))SiegeCommand.tell(p,text);});}
    public void reply(CommandSender sender,String text){SuiteScheduler.reply(plugin,sender,()->SiegeCommand.tell(sender,text));}
    private void publish(Runnable action){if(!closed&&plugin.isEnabled()&&!Bukkit.isStopping())SuiteScheduler.get().runTask(plugin,action);}
    static String problem(Throwable error){while(error.getCause()!=null&&(error instanceof CompletionException||error instanceof ExecutionException))error=error.getCause();return error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();}
    public void close(){closed=true;if(engine!=null)engine.close();store.close();}
}
