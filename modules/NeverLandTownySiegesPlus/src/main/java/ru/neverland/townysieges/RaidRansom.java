package ru.neverland.townysieges;

import java.math.*;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import com.palmergames.bukkit.towny.TownyAPI;
import ru.neverland.core.*;

/** Defender offer, attacker acceptance, funded transfer and terminal war settlement. */
public final class RaidRansom {
    private final NeverLandTownySiegesPlus plugin;
    private final RaidController raids;
    private final RansomStore store;
    private final LootBank bank=new LootBank();
    private final Set<UUID> working=new HashSet<>();
    private boolean polling;
    private long warning;
    public RaidRansom(NeverLandTownySiegesPlus plugin,RaidController raids){this.plugin=plugin;this.raids=raids;store=new RansomStore(raids.store());}
    private boolean active(){return plugin.isEnabled()&&!Bukkit.isStopping()&&!SuiteState.frozen(plugin.getName())&&raids.store().healthy();}
    private void global(Runnable task){if(active())SuiteScheduler.get().runTask(plugin,task);}
    private void warn(Throwable error){long now=System.currentTimeMillis();if(now-warning>60000){warning=now;plugin.getLogger().warning("Выкуп сохранён и ожидает: "+RaidController.problem(error));}}
    private void record(RansomStore.Offer offer,String action,String detail){AuditTrail.record(plugin,"ransom:"+action+":"+offer.state(),offer.id().toString(),"RAID_RANSOM",action,offer.acceptor()==null?AuditTrail.player(offer.proposer()):AuditTrail.player(offer.acceptor()),AuditTrail.town(offer.defender()),AuditTrail.town(offer.attacker()),"",0,AuditTrail.cents(offer.cents()),detail);}
    static long cents(String raw){try{long value=new BigDecimal(raw).movePointRight(2).longValueExact();if(value<1)return -1;return value;}catch(RuntimeException e){return -1;}}
    public void offer(Player player,String amount)throws Exception{
        if(!active()||!raids.ready()||!plugin.getConfig().getBoolean("ransom.enabled",true))throw new IllegalArgumentException("Капитуляция сейчас недоступна");
        var town=SiegeService.town(player.getUniqueId());if(town==null||!town.isMayor(TownyAPI.getInstance().getResident(player.getUniqueId())))throw new IllegalArgumentException("Выкуп предлагает мэр защищающегося города");
        var war=raids.openWars().stream().filter(r->r.defender().equals(town.getUUID())&&r.fighting(System.currentTimeMillis())).findFirst().orElseThrow(()->new IllegalArgumentException("Ваш город сейчас не обороняется"));
        if(!raids.battle(war))throw new IllegalArgumentException("Предложение доступно только во время действующего боя");
        if(!player.hasPermission("neverlandtownysiegesplus.ransom"))throw new IllegalArgumentException("Нет права предлагать выкуп");
        long sum=cents(amount),maximum=cents(String.valueOf(plugin.getConfig().get("ransom.maximum",100000)));
        if(sum<1||maximum<1||sum>maximum)throw new IllegalArgumentException("Выкуп: от 0.01 до "+maximum/100.0+" монет");
        if(!bank.ready(war.defender(),war.attacker()))throw new IllegalArgumentException("Казны Towny/Vault сейчас недоступны");
        if(bank.balance(war.defender())<sum)throw new IllegalArgumentException("В казне не хватает денег для этого предложения");
        long millis=plugin.getConfig().getLong("ransom.offer-seconds",300)*1000L;
        if(millis<1000||millis>3600000)throw new IllegalArgumentException("Неверный срок ransom.offer-seconds");
        UUID actor=player.getUniqueId();store.propose(war.id(),war.defender(),actor,sum,System.currentTimeMillis(),millis).whenComplete((result,error)->global(()->{
            if(error!=null){raids.reply(player,"&e"+RaidController.problem(error));return;}
            record(result,"OFFERED","Предложение мэра; действует до "+new Date(result.expires()));
            raids.notifyTown(result.defender(),"Мэр предлагает капитуляцию за "+money(result.cents())+" монет. До согласия противника война продолжается.");
            raids.notifyTown(result.attacker(),"Защитники предлагают капитуляцию за "+money(result.cents())+" монет. Мэр или генерал: /sieges ransom accept "+result.id()+". Срок до "+new Date(result.expires()));
        }));
    }
    public void accept(Player player,UUID id)throws Exception{
        if(!active()||!raids.ready()||!plugin.getConfig().getBoolean("ransom.enabled",true))throw new IllegalArgumentException("Капитуляция сейчас недоступна");
        var town=SiegeService.town(player.getUniqueId());if(town==null)throw new IllegalArgumentException("Нужен город нападающих");
        var war=raids.openWars().stream().filter(r->r.attacker().equals(town.getUUID())&&r.fighting(System.currentTimeMillis())).findFirst().orElseThrow(()->new IllegalArgumentException("Ваш город сейчас не атакует"));
        if(!raids.battle(war))throw new IllegalArgumentException("Принятие доступно только во время действующего боя");
        if(!player.hasPermission("neverlandtownysiegesplus.ransom")||!raids.commander(player,town))throw new IllegalArgumentException("Предложение принимает мэр или генерал нападающих");
        UUID actor=player.getUniqueId();store.accept(war.id(),id,town.getUUID(),actor,System.currentTimeMillis()).whenComplete((result,error)->{
            if(error==null)raids.block(result.raid());
            global(()->{if(error!=null){raids.reply(player,"&e"+RaidController.problem(error));return;}
                record(result,"ACCEPTED","Предложение принято; бой остановлен до завершения перевода");
                raids.notifyTown(result.defender(),"Выкуп "+money(result.cents())+" принят. Бой приостановлен; казна перечисляет деньги нападающим.");
                raids.notifyTown(result.attacker(),"Выкуп принят. Бой приостановлен до подтверждения перевода.");
            });
        });
    }
    public void status(CommandSender sender){store.pending().whenComplete((rows,error)->global(()->{
        if(error!=null){raids.reply(sender,RaidController.problem(error));return;}
        if(rows.isEmpty()){raids.reply(sender,"Предложений выкупа нет");return;}
        for(var row:rows){var a=TownyAPI.getInstance().getTown(row.attacker());var d=TownyAPI.getInstance().getTown(row.defender());
            raids.reply(sender,(d==null?row.defender():d.getName())+" → "+(a==null?row.attacker():a.getName())+" | "+money(row.cents())+" | "+row.state()+" | "+row.id());
        }
    }));}
    public void show(CommandSender sender,UUID raid){store.get(raid).whenComplete((row,error)->global(()->{
        if(error!=null){raids.reply(sender,RaidController.problem(error));return;}
        raids.reply(sender,row==null?"Квитанция не найдена":row.id()+" | рейд "+row.raid()+" | "+row.state()+" | "+money(row.cents())+" | защитники "+row.defender()+" → нападающие "+row.attacker()+" | "+row.note());
    }));}
    public void resolve(CommandSender admin,UUID raid,UUID id,String leg,boolean occurred,String reason){store.resolve(raid,id,leg,occurred,admin.getName(),reason).whenComplete((row,error)->global(()->{
        if(error!=null){raids.reply(admin,"&e"+RaidController.problem(error));return;}
        if(row.state().equals("VOID"))raids.unblock(raid);
        AuditTrail.record(plugin,"ransom-review:"+leg+":"+System.currentTimeMillis(),row.id().toString(),"RAID_RANSOM_RECONCILE",row.state(),admin instanceof Player p?AuditTrail.player(p.getUniqueId()):AuditRecord.Party.system(),AuditTrail.town(row.defender()),AuditTrail.town(row.attacker()),"",0,AuditTrail.cents(row.cents()),leg+"="+occurred+"; "+reason);
        raids.reply(admin,"Сверка сохранена: "+row.state()+". Проверьте квитанцию и баланс обоих городов.");
    }));}
    public void refund(CommandSender admin,UUID raid,UUID id,String reason){store.refund(raid,id,admin.getName(),reason).whenComplete((row,error)->global(()->{
        if(error!=null){raids.reply(admin,"&e"+RaidController.problem(error));return;}
        record(row,"REFUNDING",row.note());raids.reply(admin,"Возврат защитникам запущен. Квитанция: "+row.id());
    }));}
    private static String money(long cents){return BigDecimal.valueOf(cents,2).toPlainString();}
    public void pulse(){if(!active()||polling)return;polling=true;
        store.pending().whenComplete((rows,error)->global(()->{
            polling=false;if(error!=null){warn(error);return;}
            long now=System.currentTimeMillis();int scheduled=0;for(var row:rows){if(row.state().startsWith("REVIEW"))continue;if(scheduled>=4)break;if(!working.add(row.raid()))continue;scheduled++;
                if(Set.of("OFFERED","ACCEPTED").contains(row.state())&&row.expires()<=now){store.expireOffer(row.raid(),row.id(),now).whenComplete((v,e)->global(()->{working.remove(row.raid());if(e!=null)warn(e);else if(v!=null&&v.state().equals("VOID")){raids.unblock(row.raid());record(v,"VOID","Предложение истекло до списания");}}));continue;}
                drive(row);
            }
        }));
    }
    private void after(CompletionStage<RansomStore.Offer> work,RansomStore.Offer previous){work.whenComplete((next,error)->global(()->{
        if(error!=null){warn(error);working.remove(previous.raid());return;}
        if(next==null){working.remove(previous.raid());return;}
        record(next,next.state(),next.note());working.remove(previous.raid());
    }));}
    private void drive(RansomStore.Offer row){
        try{switch(row.state()){
            case "ACCEPTED"->{
                if(!bank.ready(row.defender(),row.attacker())||bank.balance(row.defender())<row.cents()){working.remove(row.raid());return;}
                store.change(row.raid(),row.id(),"ACCEPTED","DEBITING","Намерение списания сохранено").whenComplete((started,error)->global(()->{
                    if(error!=null){warn(error);working.remove(row.raid());return;}mutate(started,false);
                }));
            }
            case "DEBITED"->{
                if(!bank.ready(row.defender(),row.attacker())){working.remove(row.raid());return;}
                store.change(row.raid(),row.id(),"DEBITED","CREDITING","Намерение зачисления сохранено").whenComplete((started,error)->global(()->{
                    if(error!=null){warn(error);working.remove(row.raid());return;}mutate(started,true);
                }));
            }
            case "FUNDED"->raids.store().surrender(row.raid(),row.acceptor(),System.currentTimeMillis()).whenComplete((wars,error)->{
                if(error==null)raids.replace(wars);
                global(()->{if(error!=null){warn(error);working.remove(row.raid());return;}
                    raids.unblock(row.raid());record(row,"DONE","Деньги зачислены и война закрыта; временные участки освобождены");
                    raids.notifyTown(row.defender(),"Капитуляция завершена. Из казны перечислено "+money(row.cents())+" монет. Рейд окончен.");
                    raids.notifyTown(row.attacker(),"Капитуляция завершена. В казну поступило "+money(row.cents())+" монет. Рейд окончен.");
                    working.remove(row.raid());
                });
            });
            case "REFUNDING"->mutate(row,true,true);
            case "REFUNDED"->raids.store().refundClosed(row.raid(),System.currentTimeMillis()).whenComplete((wars,error)->{
                if(error==null)raids.replace(wars);
                global(()->{if(error!=null){warn(error);working.remove(row.raid());return;}
                    raids.unblock(row.raid());record(row,"VOID","Выкуп возвращён; нейтральная остановка рейда");
                    raids.notifyTown(row.defender(),"Выкуп полностью возвращён в казну. Рейд остановлен без поражения.");
                    raids.notifyTown(row.attacker(),"Выкуп не получен и возвращён защитникам. Рейд остановлен без победы.");
                    working.remove(row.raid());
                });
            });
            default->working.remove(row.raid());
        }}catch(Exception|LinkageError e){warn(e);working.remove(row.raid());}
    }
    private void mutate(RansomStore.Offer row,boolean incoming){mutate(row,incoming,false);}
    private void mutate(RansomStore.Offer row,boolean incoming,boolean refund){
        if(!active()){working.remove(row.raid());return;}
        boolean confirmed=false;String note="Результат банка неизвестен; требуется сверка";
        try{confirmed=bank.mutate(row.id(),row.defender(),refund?row.defender():row.attacker(),row.cents(),incoming,refund?"raid-ransom-refund":"raid-ransom",refund?"Возврат выкупа":"Выкуп за капитуляцию");}
        catch(Exception|LinkageError e){note=RaidController.problem(e);}
        String next=confirmed?(refund?"REFUNDED":incoming?"FUNDED":"DEBITED"):(refund?"REVIEW_REFUND":incoming?"REVIEW_CREDIT":"REVIEW_DEBIT");
        after(store.change(row.raid(),row.id(),row.state(),next,confirmed?"Квитанция Towny подтверждена":note),row);
    }
}
