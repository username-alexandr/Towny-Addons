package ru.neverland.townysieges;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import ru.neverland.core.*;

/** Coordinates immutable SQLite receipts with the public Builds escrow API. */
public final class RaidLoot {
    private final NeverLandTownySiegesPlus plugin;private final RaidController raids;private final RaidLootStore store;
    private LootBank bank=new LootBank();
    private volatile LootDefense defense;private final SiegeService siege;private volatile LootSettings settings;
    private final Set<UUID> working=new HashSet<>();private final Map<UUID,Long> began=new HashMap<>();private long polling;private int cursor;private long lastWarning;
    public RaidLoot(NeverLandTownySiegesPlus plugin,RaidController raids,LootSettings settings,SiegeService siege,LootDefense defense){this.siege=siege;this.defense=defense;this.plugin=plugin;this.raids=raids;this.settings=settings;store=new RaidLootStore(raids.store());}
    public RaidLootStore store(){return store;}public LootSettings settings(){return settings;}public void reload(LootSettings next){settings=next;}public void defense(LootDefense next){defense=next;}
    public LootDefense.Quote protection(UUID town)throws Exception{int levels=siege.defenses(town).stream().mapToInt(row->((Number)row.get("activeLevel")).intValue()).sum();return defense.quote(levels,new BuildsAccess().army(town),settings);}
    public CompletionStage<RaidLootStore.Loot> arm(RaidController.Permit permit,RaidStore.Key core,byte[] book)throws Exception{var quote=protection(permit.defender());return store.arm(permit,core,book,quote.effective(),quote.description());}
    private boolean active(){return plugin.isEnabled()&&!Bukkit.isStopping()&&!SuiteState.frozen(plugin.getName())&&raids.store().healthy();}
    private ApiServices.Connection stock()throws Exception {var api=ApiServices.require("NeverLandTownyBuilds","ru.neverland.townybuilds.api.RaidStorageApi","healthy","core","reserve","settle","status");if(!Boolean.TRUE.equals(api.invoke("healthy",new Class<?>[]{})))throw new IllegalStateException("Склад добычи недоступен");return api;}
    private static int coordinate(Object core,String method)throws Exception{return ((Number)core.getClass().getMethod(method).invoke(core)).intValue();}
    public boolean core(UUID town,Block block){
        if(!settings.enabled()||block.getType()!=Material.LECTERN)return false;
        try{Object core=((Optional<?>)stock().invoke("core",new Class<?>[]{UUID.class},town)).orElse(null);return core!=null&&block.getWorld().getUID().equals(core.getClass().getMethod("worldId").invoke(core))&&block.getX()==coordinate(core,"x")&&block.getY()==coordinate(core,"y")&&block.getZ()==coordinate(core,"z");}
        catch(Exception|LinkageError unavailable){warn(unavailable);return false;}
    }
    private void global(Runnable action){if(!active())return;SuiteScheduler.get().runTask(plugin,action);}
    private <T> void after(CompletionStage<T> future,Consumer<T> next,UUID operation){future.whenComplete((value,error)->{
        if(error!=null){warn(error);global(()->working.remove(operation));return;}
        global(()->{try{next.accept(value);}catch(Exception|LinkageError failure){warn(failure);working.remove(operation);}});
    });}
    public void pulse(){
        if(!active()){working.clear();began.clear();return;}long now=System.currentTimeMillis();working.removeIf(id->now-began.getOrDefault(id,0L)>30000);began.keySet().retainAll(working);if(now-polling<5000)return;polling=now;
        store.list(true).whenComplete((rows,error)->{global(()->{polling=0;if(error!=null){warn(error);return;}
            rows.stream().filter(r->r.state().startsWith("REVIEW")).forEach(this::log);
            for(var row:rows)if(row.state().equals("ARMED")&&now-row.created()>10000||!working.contains(row.id())&&(row.state().equals("DEBITING")||row.state().equals("CREDITING")))store.change(row.id(),row.state(),row.state().equals("ARMED")?"REVIEW_CORE":row.state().equals("DEBITING")?"REVIEW_DEBIT":"REVIEW_CREDIT","interrupted callback; reconciliation required");
            var candidates=rows.stream().filter(r->Set.of("BREACHED","PREPARED","DEBITED","FUNDED").contains(r.state())).toList();if(candidates.isEmpty())return;
            for(int n=0;n<Math.min(2,candidates.size());n++){var row=candidates.get(Math.floorMod(cursor++,candidates.size()));if(working.add(row.id())){began.put(row.id(),now);drive(row);}}
        });if(!active())polling=0;});
    }
    @SuppressWarnings("unchecked") private Map<String,Object> reserve(RaidLootStore.Loot row)throws Exception{return (Map<String,Object>)stock().invoke("reserve",new Class<?>[]{UUID.class,UUID.class,UUID.class,UUID.class,int.class},row.id(),row.defender(),row.attacker(),row.actor(),row.resourcesBps());}
    @SuppressWarnings("unchecked") private Map<String,Object> settle(RaidLootStore.Loot row)throws Exception{return (Map<String,Object>)stock().invoke("settle",new Class<?>[]{UUID.class,boolean.class},row.id(),true);}
    private void advance(RaidLootStore.Loot row,String next,String note){after(store.change(row.id(),row.state(),next,note),this::drive,row.id());}
    private void drive(RaidLootStore.Loot row){
        if(!active()){working.remove(row.id());return;}
        log(row);
        try{switch(row.state()){
            case "BREACHED"->{
                if(row.moneyBps()>0&&!bank.ready(row.defender(),row.attacker())){working.remove(row.id());return;}
                var result=reserve(row);if(!Set.of("RESERVED","DELIVERED").contains(result.get("state"))){working.remove(row.id());return;}
                long available=row.moneyBps()==0?0:bank.balance(row.defender()),target=row.moneyBps()==0?0:bank.balance(row.attacker());
                after(store.prepare(row.id(),LootSettings.share(available,row.moneyBps()),available,target),this::drive,row.id());
            }
            case "PREPARED"->{
                if(row.cents()==0){advance(row,"FUNDED","zero money share");return;}
                if(!bank.ready(row.defender(),row.attacker())){working.remove(row.id());return;}
                long available=bank.balance(row.defender());if(available<row.cents()){after(store.reprice(row.id(),Math.min(row.cents(),LootSettings.share(available,row.moneyBps())),available),this::drive,row.id());return;}
                after(store.bankStart(row.id(),"PREPARED",false,available),armed->money(armed,false),row.id());
            }
            case "DEBITED"->{
                if(!bank.ready(row.defender(),row.attacker())){working.remove(row.id());return;}
                after(store.bankStart(row.id(),"DEBITED",true,bank.balance(row.attacker())),armed->money(armed,true),row.id());
            }
            case "FUNDED"->{
                var result=settle(row);if(!"DELIVERED".equals(result.get("state"))){working.remove(row.id());return;}
                if(!book(row)){working.remove(row.id());return;}
                if(!AuditTrail.record(plugin,"raid-loot",row.id().toString(),"RAID_LOOT_MONEY","COMPLETED",AuditTrail.player(row.actor()),AuditTrail.town(row.defender()),AuditTrail.town(row.attacker()),"",0,AuditTrail.cents(row.cents()),"Кафедра ратуши; склад="+result.get("amount")+"; "+row.policy())){working.remove(row.id());return;}
                after(store.change(row.id(),"FUNDED","DONE","resources="+result.get("amount")),done->{working.remove(done.id());raids.notifyTown(done.attacker(),"Рейд удался: в казну поступило "+(done.cents()/100.0)+" монет, на склад — "+result.get("amount")+" предметов.");raids.notifyTown(done.defender(),"Кафедра ратуши разрушена в рейде. Захвачено "+(done.cents()/100.0)+" монет и "+result.get("amount")+" предметов со склада.");},row.id());
            }
            default->working.remove(row.id());
        }}catch(Exception|LinkageError error){warn(error);working.remove(row.id());}
    }
    private void money(RaidLootStore.Loot row,boolean incoming){
        if(!active()){working.remove(row.id());return;}
        if(!bank.ready(row.defender(),row.attacker())){advance(row,incoming?"DEBITED":"PREPARED","provider unavailable before bank call; not attempted");return;}
        boolean paid=false;String note="provider did not confirm mutation; reconciliation required";
        try{paid=bank.mutate(row,incoming);}catch(Exception|LinkageError error){note=RaidController.problem(error);}
        advance(row,paid?(incoming?"FUNDED":"DEBITED"):(incoming?"REVIEW_CREDIT":"REVIEW_DEBIT"),paid?"Towny receipt confirmed":note);
    }
    private boolean book(RaidLootStore.Loot row)throws Exception{
        if(row.book().length==0)return true;
        ItemStack item=ItemStack.deserializeBytes(row.book());var api=ApiServices.require("NeverLandTownyBuilds","ru.neverland.townybuilds.api.MunicipalStorageApi","deposit");
        UUID operation=UUID.nameUUIDFromBytes(("raid-book:"+row.id()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return "DELIVERED".equals(api.invoke("deposit",new Class<?>[]{UUID.class,UUID.class,ItemStack.class,int.class},row.defender(),operation,item,item.getAmount()));
    }
    public void list(CommandSender sender){store.list(false).whenComplete((rows,error)->global(()->{if(error!=null){raids.reply(sender,RaidController.problem(error));return;}if(rows.isEmpty())raids.reply(sender,"Захватов кафедры пока нет");for(var row:rows)raids.reply(sender,row.id()+" | "+title(row.state())+" | "+row.cents()/100.0+" монет | "+row.defender()+" → "+row.attacker());}));}
    public void status(CommandSender sender,UUID id){store.get(id).whenComplete((row,error)->global(()->{if(error!=null){raids.reply(sender,RaidController.problem(error));return;}if(row==null){raids.reply(sender,"Квитанция не найдена");return;}raids.reply(sender,id+" | "+title(row.state())+" | сумма="+row.cents()/100.0+"; до списания="+row.debitBefore()/100.0+"; до зачисления="+row.creditBefore()/100.0+"; инициатор="+row.actor()+"; "+row.policy()+"; "+row.note());}));}
    public void resolve(CommandSender sender,UUID id,String leg,boolean occurred,String reason){store.resolve(id,leg,occurred,sender.getName(),reason).whenComplete((row,error)->global(()->{if(error==null)AuditTrail.record(plugin,"resolve:"+UUID.randomUUID(),id.toString(),"RAID_LOOT_RECONCILE",row.state(),sender instanceof org.bukkit.entity.Player p?AuditTrail.player(p.getUniqueId()):AuditRecord.Party.system(),AuditTrail.town(row.defender()),AuditTrail.town(row.attacker()),"",0,AuditTrail.cents(row.cents()),leg+"="+occurred+"; "+reason);raids.reply(sender,error==null?"Сверка сохранена: "+title(row.state()):RaidController.problem(error));}));}
    private void log(RaidLootStore.Loot row){if(!row.policy().isBlank())AuditTrail.record(plugin,"loot-policy",row.id().toString(),"RAID_LOOT_PROTECTION","COMPLETED",AuditTrail.player(row.actor()),AuditTrail.town(row.defender()),AuditTrail.town(row.attacker()),"",0,"",row.policy());String kind=row.state().startsWith("REVIEW")?"RAID_LOOT_REVIEW":row.state().equals("BREACHED")?"RAID_CORE":"RAID_LOOT_STATE";AuditTrail.record(plugin,row.state()+":"+row.cents(),row.id().toString(),kind,row.state(),AuditTrail.player(row.actor()),AuditTrail.town(row.defender()),AuditTrail.town(row.attacker()),"",0,AuditTrail.cents(row.cents()),"Кафедра: "+row.core().id()+"; "+row.policy()+"; "+row.note());}
    private static String title(String phase){return switch(phase){case "DONE"->"Добыча выдана";case "VOID"->"Захват не состоялся";case "REVIEW_CORE"->"Нужна сверка разрушения кафедры";case "REVIEW_DEBIT"->"Нужна сверка списания";case "REVIEW_CREDIT"->"Нужна сверка зачисления";case "FUNDED"->"Деньги переведены, ожидается доставка";default->"Добыча сохраняется / ожидает перевода";};}
    private void warn(Throwable error){long now=System.currentTimeMillis();if(now-lastWarning>60000){lastWarning=now;plugin.getLogger().warning("Добыча рейда сохранена; операция ожидает: "+RaidController.problem(error));}}
}
