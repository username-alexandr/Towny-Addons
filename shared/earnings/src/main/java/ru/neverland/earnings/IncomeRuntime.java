package ru.neverland.earnings;

import java.util.*;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.*;

public final class IncomeRuntime {
    private final JavaPlugin plugin;private final RewardLedger ledger;private final TownyRewardBank bank;
    private boolean enabled;
    public IncomeRuntime(JavaPlugin plugin,String folder,long playerCents,long serverCents,boolean enabled)throws java.io.IOException{
        this.plugin=plugin;this.enabled=enabled;ledger=new RewardLedger(plugin.getDataFolder().toPath().resolve(folder),playerCents,serverCents);ledger.load();bank=new TownyRewardBank(plugin);
    }
    public RewardLedger ledger(){return ledger;}
    public boolean enabled(){return enabled&&!ledger.fault()&&!SuiteState.frozen(plugin.getName());}
    public boolean ready(UUID player){return enabled()&&bank.ready(player);}
    public void start(){SuiteScheduler.get().runTaskTimer(plugin,()->{
        if(!enabled())return;
        for(var e:ledger.pendingBatch(20))try{
            ledger.submit(e.id(),bank);var after=ledger.get(e.id());
            if(after.state()==RewardLedger.State.REVIEW)AuditTrail.record(plugin,"review-required",e.id().toString(),e.source().startsWith("orders:")?"PUBLIC_ORDER_REWARD":"JOB_SALARY","REVIEW",AuditRecord.Party.unknown(),new AuditRecord.Party("SERVER","server","Государство",""),AuditTrail.player(e.player()),"",0,AuditTrail.cents(e.cents()),"Нужна сверка: "+after.history().get(after.history().size()-1));
        }catch(Exception ex){
            plugin.getLogger().severe("Начисление "+e.id()+" отложено: "+ex.getMessage());
            if(ledger.fault())break;
        }
    },20,20);}
    public void admin(CommandSender sender,String[] args)throws Exception{
        if(args.length==0||args[0].equals("status")){for(String s:bank.diagnostics())sender.sendMessage(s);sender.sendMessage("Начисления: enabled="+enabled()+"; fault="+ledger.fault()+"; reserved/paid24h="+AuditTrail.cents(ledger.used(null)));return;}
        if(args[0].equals("list")){
            int page=args.length>1?Integer.parseInt(args[1]):1;if(page<1)throw new IllegalArgumentException("Страница >= 1");
            var rows=ledger.all().stream().sorted(Comparator.comparingLong(RewardLedger.Entry::created).reversed()).toList();
            sender.sendMessage("Начисления, страница "+page+" (всего "+rows.size()+")");
            rows.stream().skip((page-1L)*10).limit(10).forEach(e->sender.sendMessage(e.id()+" "+e.state()+" "+AuditTrail.cents(e.cents())+" игрок="+e.player()+" "+e.source()));return;
        }
        if(args[0].equals("show")&&args.length==2){var e=ledger.get(UUID.fromString(args[1]));if(e==null)throw new IllegalArgumentException("Нет записи");sender.sendMessage(e.toString());return;}
        if(args[0].equals("resolve")&&args.length>=5&&args[3].equals("confirm")){
            ledger.resolve(UUID.fromString(args[1]),args[2],sender.getName(),String.join(" ",Arrays.copyOfRange(args,4,args.length)));AuditTrail.record(plugin,"review",UUID.randomUUID().toString(),"ADMIN_INCOME","COMPLETED",sender instanceof org.bukkit.entity.Player p?AuditTrail.player(p.getUniqueId()):AuditRecord.Party.unknown(),AuditRecord.Party.unknown(),AuditRecord.Party.unknown(),"",0,"",sender.getName()+" "+String.join(" ",args));sender.sendMessage("Решение сверки записано в журнал.");return;
        }
        sender.sendMessage("status | list [страница] | show <UUID> | resolve <UUID> <paid|retry|taken|untouched> confirm <основание>");
    }
}
