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
    private final JavaPlugin plugin;private final Map<Account,AccountObserver> watched=new IdentityHashMap<>();private BukkitTask task;
    public BankAudit(JavaPlugin plugin){this.plugin=plugin;plugin.getServer().getPluginManager().registerEvents(this,plugin);task=plugin.getServer().getScheduler().runTaskTimer(plugin,this::scan,1,100);}
    private void scan(){try{if(!TownyEconomyHandler.isActive())return;Set<Account> live=Collections.newSetFromMap(new IdentityHashMap<>());var api=TownyAPI.getInstance();for(var t:List.copyOf(api.getTowns()))live.add(t.getAccount());for(var n:List.copyOf(api.getNations()))live.add(n.getAccount());for(var r:List.copyOf(TownyUniverse.getInstance().getResidents()))live.add(r.getAccount());for(var a:live)attach(a);for(var a:new ArrayList<>(watched.keySet()))if(!live.contains(a))a.removeObserver(watched.remove(a));}catch(Exception ex){plugin.getLogger().log(java.util.logging.Level.SEVERE,"AUDIT GAP: не удалось подключить наблюдение банков",ex);}}
    private void attach(Account account){if(account==null||watched.containsKey(account))return;var observer=new AccountObserver(){
        public void withdrew(Account source,double amount,String reason){capture(source,amount,reason,false);}
        public void deposited(Account source,double amount,String reason){capture(source,amount,reason,true);}
        private void capture(Account source,double amount,String reason,boolean incoming){if(source!=account)return;try{if(!Double.isFinite(amount)||amount<=0)return;var party=AuditTrail.account(account);var unknown=AuditRecord.Party.unknown();AuditTrail.bank(plugin,"BANK_LEG",incoming?"CREDIT":"DEBIT",unknown,incoming?unknown:party,incoming?party:unknown,amount,reason==null?"":reason);}catch(Exception ex){plugin.getLogger().log(java.util.logging.Level.SEVERE,"AUDIT GAP: банковское наблюдение",ex);}}
    };account.addObserver(observer);watched.put(account,observer);}
    @EventHandler(priority=EventPriority.MONITOR)public void transfer(TownyTransactionEvent e){var t=e.getTransaction();if(!t.hasSenderAccount()||!t.hasReceiverAccount())return;try{if(!Double.isFinite(t.getAmount())||t.getAmount()<=0)return;var actor=t.getSendingPlayer();AuditTrail.bank(plugin,"BANK_TRANSFER","COMPLETED",actor==null?AuditRecord.Party.unknown():AuditTrail.player(actor.getUniqueId()),AuditTrail.account(t.getSendingAccount()),AuditTrail.account(t.getReceivingAccount()),t.getAmount(),"Towny payTo: обе стороны подтверждены; тип="+t.getType());}catch(Exception ex){plugin.getLogger().log(java.util.logging.Level.SEVERE,"AUDIT GAP: перевод Towny",ex);}}
    @EventHandler(priority=EventPriority.MONITOR)public void bankCommand(BankTransactionEvent e){var t=e.getTransaction();var actor=t.getSendingPlayer();if(actor==null)return;try{AuditTrail.bank(plugin,"BANK_COMMAND","OBSERVED",AuditTrail.player(actor.getUniqueId()),AuditTrail.account(t.getSendingAccount()),AuditTrail.account(t.getReceivingAccount()),t.getAmount(),"Towny: "+t.getType()+"; счёт="+e.getAccount().getName());}catch(Exception ex){plugin.getLogger().log(java.util.logging.Level.SEVERE,"AUDIT GAP: команда банка Towny",ex);}}
    @EventHandler(priority=EventPriority.MONITOR)public void town(com.palmergames.bukkit.towny.event.NewTownEvent e){attach(e.getTown().getAccount());}
    @EventHandler(priority=EventPriority.MONITOR)public void nation(com.palmergames.bukkit.towny.event.NewNationEvent e){attach(e.getNation().getAccount());}
    @EventHandler(priority=EventPriority.MONITOR)public void resident(com.palmergames.bukkit.towny.event.resident.NewResidentEvent e){attach(e.getResident().getAccount());}
    public void stop(){if(task!=null)task.cancel();watched.forEach(Account::removeObserver);watched.clear();}
}
