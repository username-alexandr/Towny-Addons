package ru.neverland.townycontrol;

import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.economy.*;
import com.palmergames.bukkit.towny.event.economy.*;
import org.bukkit.event.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.core.*;
import java.util.*;

/** Observations are not inferred transfers. A paired Towny event is a separate completed transfer. */
public final class BankAudit implements Listener {
    private final JavaPlugin plugin;private final Map<Account,AccountObserver> watched=new IdentityHashMap<>();private BukkitTask task;private final BankAuditQueue queue;
    private volatile Map<Account,AuditRecord.Party> parties=Map.of();private volatile Map<UUID,AuditRecord.Party> actors=Map.of();
    public BankAudit(JavaPlugin plugin){this.plugin=plugin;this.queue=new BankAuditQueue(plugin);plugin.getServer().getPluginManager().registerEvents(this,plugin);task=ru.neverland.core.SuiteScheduler.get().runTaskTimer(plugin,this::scan,1,100);}
    private void scan(){try{if(!TownyEconomyHandler.isActive())return;Set<Account> live=Collections.newSetFromMap(new IdentityHashMap<>());var api=TownyAPI.getInstance();for(var t:List.copyOf(api.getTowns()))live.add(t.getAccount());for(var n:List.copyOf(api.getNations()))live.add(n.getAccount());for(var r:List.copyOf(TownyUniverse.getInstance().getResidents()))live.add(r.getAccount());var captured=new IdentityHashMap<Account,AuditRecord.Party>();var people=new HashMap<UUID,AuditRecord.Party>();for(var a:live){var p=AuditTrail.account(a);captured.put(a,p);if(p.type().equals("PLAYER")&&!p.id().isBlank())people.put(UUID.fromString(p.id()),p);}parties=Collections.unmodifiableMap(captured);actors=Map.copyOf(people);for(var a:live)attach(a);for(var a:new ArrayList<>(watched.keySet()))if(!live.contains(a))a.removeObserver(watched.remove(a));}catch(Exception ex){plugin.getLogger().log(java.util.logging.Level.SEVERE,"AUDIT GAP: не удалось подключить наблюдение банков",ex);}}
    private void attach(Account account){if(account==null||watched.containsKey(account))return;
        if(!parties.containsKey(account)){var copy=new IdentityHashMap<Account,AuditRecord.Party>(parties);var party=AuditTrail.account(account);copy.put(account,party);parties=Collections.unmodifiableMap(copy);if(party.type().equals("PLAYER")&&!party.id().isBlank()){var people=new HashMap<>(actors);people.put(UUID.fromString(party.id()),party);actors=Map.copyOf(people);}}
        var observer=new AccountObserver(){
        public void withdrew(Account source,double amount,String reason){ capture(source,amount,reason,false); }
        public void deposited(Account source,double amount,String reason){ capture(source,amount,reason,true); }
        private void capture(Account source,double amount,String reason,boolean incoming){if(source!=account)return;try{if(!Double.isFinite(amount)||amount<=0)return;var party=party(account);var unknown=AuditRecord.Party.unknown();BankAudit.this.capture("BANK_LEG",incoming?"CREDIT":"DEBIT",unknown,incoming?unknown:party,incoming?party:unknown,amount,reason==null?"":reason);}catch(Exception ex){plugin.getLogger().log(java.util.logging.Level.SEVERE,"AUDIT GAP: банковское наблюдение",ex);}}
    };account.addObserver(observer);watched.put(account,observer);}
    @EventHandler(priority=EventPriority.MONITOR)public void transfer(TownyTransactionEvent e){ var t=e.getTransaction();if(!t.hasSenderAccount()||!t.hasReceiverAccount())return;try{if(!Double.isFinite(t.getAmount())||t.getAmount()<=0)return;var actor=t.getSendingPlayer();capture("BANK_TRANSFER","COMPLETED",actor==null?AuditRecord.Party.unknown():actor(actor.getUniqueId()),party(t.getSendingAccount()),party(t.getReceivingAccount()),t.getAmount(),"Towny payTo: обе стороны подтверждены; тип="+t.getType());}catch(Exception ex){plugin.getLogger().log(java.util.logging.Level.SEVERE,"AUDIT GAP: перевод Towny",ex);} }
    @EventHandler(priority=EventPriority.MONITOR)public void bankCommand(BankTransactionEvent e){ var t=e.getTransaction();var actor=t.getSendingPlayer();if(actor==null)return;try{capture("BANK_COMMAND","OBSERVED",actor(actor.getUniqueId()),party(t.getSendingAccount()),party(t.getReceivingAccount()),t.getAmount(),"Towny: "+t.getType()+"; счёт="+e.getAccount().getName());}catch(Exception ex){plugin.getLogger().log(java.util.logging.Level.SEVERE,"AUDIT GAP: команда банка Towny",ex);} }
    @EventHandler(priority=EventPriority.MONITOR)public void town(com.palmergames.bukkit.towny.event.NewTownEvent e){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {if(TownyEconomyHandler.isActive())attach(e.getTown().getAccount());} }
    @EventHandler(priority=EventPriority.MONITOR)public void nation(com.palmergames.bukkit.towny.event.NewNationEvent e){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {if(TownyEconomyHandler.isActive())attach(e.getNation().getAccount());} }
    @EventHandler(priority=EventPriority.MONITOR)public void resident(com.palmergames.bukkit.towny.event.resident.NewResidentEvent e){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {if(TownyEconomyHandler.isActive())attach(e.getResident().getAccount());} }
    private AuditRecord.Party party(Account account){return account==null?AuditRecord.Party.unknown():parties.getOrDefault(account,AuditRecord.Party.unknown());}
    private AuditRecord.Party actor(UUID id){return actors.getOrDefault(id,new AuditRecord.Party("PLAYER",id.toString(),"Неизвестный игрок",""));}
    private void capture(String kind,String outcome,AuditRecord.Party actor,AuditRecord.Party from,AuditRecord.Party to,double amount,String details){
        String operation=UUID.randomUUID().toString();String bounded=details==null?"":details.length()>4096?details.substring(0,4096)+" [описание сокращено]":details;
        queue.submit(new AuditRecord(AuditRecord.id(plugin.getName(),operation,"bank"),System.currentTimeMillis(),plugin.getName(),kind,outcome,operation,actor,from,to,"",0,AuditTrail.money(amount),bounded));
    }
    public String status(){return queue.status();}
    public java.util.concurrent.CompletionStage<Void> closeAsync(){stop();return queue.closeAsync();}
    public void stop(){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {if(task!=null)task.cancel();watched.forEach(Account::removeObserver);watched.clear();} }
}
