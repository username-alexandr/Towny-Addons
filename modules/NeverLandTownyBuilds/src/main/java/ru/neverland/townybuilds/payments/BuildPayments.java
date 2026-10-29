package ru.neverland.townybuilds.payments;

import java.util.*;
import java.util.concurrent.*;
import java.math.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import com.palmergames.bukkit.towny.object.Town;
import ru.neverland.core.*;
import ru.neverland.townybuilds.data.*;
import ru.neverland.townybuilds.construction.*;
import ru.neverland.townybuilds.integration.*;
import ru.neverland.townybuilds.model.*;
import ru.neverland.townybuilds.util.ItemCodec;
import static ru.neverland.townybuilds.payments.BuildPayment.*;

/** File ACK → exact Treasury ACK → core ACK; legacy ambiguous receipts never enter this protocol. */
public final class BuildPayments {
    private final JavaPlugin plugin;
    private final DataStore data;
    private final TownyHook towny;
    private final ConstructionService construction;
    private final ArchaeologyBridge archaeology;
    private final BuildTreasury bank=new BuildTreasury();
    private CompletionStage<?> active=CompletableFuture.completedFuture(null);
    private CompletionStage<Void> close;
    private BukkitTask task;
    private volatile boolean closing;
    /** Queue waits may be cancelled on disable; submitted file/bank effects must drain. */
    private CompletableFuture<Void> waitingRegion;
    private long warned;
    private int cursor;
    private final ThreadPoolExecutor auditWriter=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1),r->{var thread=new Thread(r,"NeverLand-build-payment-audit");thread.setDaemon(true);return thread;},new ThreadPoolExecutor.AbortPolicy());
    public BuildPayments(JavaPlugin plugin,DataStore data,TownyHook towny,ConstructionService construction,ArchaeologyBridge archaeology){this.plugin=plugin;this.data=data;this.towny=towny;this.construction=construction;this.archaeology=archaeology;}
    public void start(){if(task!=null)task.cancel();task=SuiteScheduler.get().runTaskTimer(plugin,this::pulse,5,5);}
    public boolean available(){return !closing&&data.writable()&&!data.storageEditing()&&active.toCompletableFuture().isDone();}
    public boolean withoutPhysicalSite(UUID id){var p=data.payments().get(id);return p!=null&&p.terms().kind()!=Kind.PHYSICAL;}
    public String refundCategory(UUID id){var p=data.payments().get(id);return p==null?"construction":p.terms().category();}
    public boolean refundBacked(UUID town,String project,ConstructionReceipt receipt)throws ReflectiveOperationException {
        var p=data.payments().get(receipt.id());if(p==null)return true; // Historical verified receipts retain their original manual protocol.
        if(!p.terms().town().equals(town)||!p.terms().project().equals(project)||p.terms().cents()!=receipt.paidCents())throw new IllegalStateException("Возврат не соответствует оплате");
        if(receipt.refundCents()==0)return true;
        var status=bank.status(p.terms());return "PAID".equals(status.get("status"))&&Boolean.TRUE.equals(status.get("backingAvailable"));
    }
    public boolean treasuryAvailable(){return bank.available();}
    public boolean pending(UUID town,String project){return data.payments().values().stream().anyMatch(p->p.pending()&&p.terms().town().equals(town)&&p.terms().project().equals(project));}
    public static long cents(double amount){if(!Double.isFinite(amount)||amount<0)throw new IllegalArgumentException("Некорректная сумма");return BigDecimal.valueOf(amount).movePointRight(2).setScale(0,RoundingMode.UNNECESSARY).longValueExact();}
    private void requireNew(UUID town,String project,long cents){if(!available()||pending(town,project))throw new IllegalStateException("Дождитесь завершения предыдущей оплаты, сохранения или закрытия городского склада");if(data.payments().size()>=MAX)throw new IllegalStateException("Журнал оплат заполнен; новые покупки приостановлены, начатые продолжаются");if(cents>0&&!bank.available())throw new IllegalStateException("Оплата недоступна: нужен работающий NeverLandTownyTreasuryPlus с подтверждаемыми платежами");}
    public UUID begin(Player actor,Town town,ProjectDefinition project,LevelDefinition level,ConstructionPreparation preparation,ResourceFund fund,String reason)throws Exception {
        long amount=cents(level.money());requireNew(town.getUUID(),project.id(),amount);
        var state=data.town(town.getUUID());var prior=state.constructionReceipts().get(project.id());
        if(prior!=null&&(!Set.of("ACTIVE","COMPLETE","DONE").contains(prior.state())||!prior.remaining().isEmpty()))throw new IllegalStateException("Дождитесь предыдущего возврата");
        var artifacts=project.type()==ProjectType.WONDER?archaeology.refundableArtifacts(project.id()):List.<ConstructionReceipt.Item>of();
        List<ConstructionReceipt.Item> items=new ArrayList<>();
        for(var required:level.resources()){
            var actual=fund.entries().stream().filter(e->ru.neverland.townybuilds.service.ResourceMatcher.matches(e.template(),required)).findFirst().orElseThrow();
            items.add(new ConstructionReceipt.Item(actual.template(),required.getAmount()));
        }
        var surplus=fund.surplus(level.resources()).stream().map(e->new ConstructionReceipt.Item(e.template(),e.amount())).toList();
        var receipt=(preparation==null?new ConstructionReceipt(UUID.randomUUID(),level.level(),amount,items,Map.of(),"ACTIVE",0,List.of(),0,actor.getUniqueId().toString(),false,items.size()):construction.captureReceipt(preparation,level.money(),items)).remaining(surplus).state("TREASURY_PENDING");
        var savedArtifacts=new ArrayList<Artifact>();for(var item:artifacts)savedArtifacts.add(new Artifact(ItemCodec.encodeSingle(item.sample()),item.amount()));
        var commands=level.commands().stream().map(c->c.replace("{player}",actor.getName()).replace("{town}",town.getName()).replace("{level}",String.valueOf(level.level()))).toList();
        var terms=new Terms(receipt.id(),town.getUUID(),actor.getUniqueId(),actor.getName(),preparation==null?Kind.VIRTUAL:Kind.PHYSICAL,project.id(),level.level()-1,level.level(),amount,amount==0?"construction":bank.category("construction"),"construction",reason,System.currentTimeMillis(),level.bonusBlocks(),commands,project.type()==ProjectType.WONDER,savedArtifacts);
        var payment=new BuildPayment(terms,Phase.PREPARED);var oldSite=state.constructionSite(project.id());var oldFunds=state.resourceFunds();boolean oldArmy=state.armyEstablished();var draft=state.placements().get(project.id());
        if(preparation!=null)preparation.site().adminPaused(true);
        track(data.commitAsync(()->{data.payment(payment);state.receipt(project.id(),receipt);state.clearResourceFund(project.id());if(preparation!=null){state.setConstructionSite(preparation.site());state.placement(project.id(),null);}},()->{data.restorePayment(terms.id(),null);state.receipt(project.id(),prior);state.loadResourceFunds(oldFunds);if(oldSite==null)state.removeConstructionSite(project.id());else state.setConstructionSite(oldSite);state.restoreArmyEstablished(oldArmy);state.placement(project.id(),draft);}));
        return terms.id();
    }
    public UUID insure(Player actor,Town town,double amount)throws Exception {
        long value=cents(amount);if(value<=0)throw new IllegalArgumentException("Сумма должна быть положительной");requireNew(town.getUUID(),"insurance_chamber",value);
        var terms=new Terms(UUID.randomUUID(),town.getUUID(),actor.getUniqueId(),actor.getName(),Kind.INSURANCE,"insurance_chamber",0,0,value,bank.category("social"),"insurance","Пополнение страхового резерва",System.currentTimeMillis(),0,List.of(),false,List.of());
        var p=new BuildPayment(terms,Phase.PREPARED);track(data.commitAsync(()->data.payment(p),()->data.restorePayment(terms.id(),null)));return terms.id();
    }
    private CompletionStage<Void> change(BuildPayment before,BuildPayment after,Runnable apply,Runnable undo){
        return data.commitAsync(()->{if(!before.equals(data.payments().get(before.terms().id())))throw new IllegalStateException("Оплата уже изменилась");apply.run();data.payment(after);},()->{undo.run();data.restorePayment(before.terms().id(),before);});
    }
    private CompletionStage<Void> change(BuildPayment before,Phase next){return change(before,before.phase(next),()->{},()->{});}
    private ConstructionReceipt receipt(BuildPayment p){var town=data.existingTown(p.terms().town());var r=town==null?null:town.constructionReceipts().get(p.terms().project());if(r==null||!r.id().equals(p.terms().id()))throw new IllegalStateException("Квитанция стройки отсутствует или заменена");return r;}
    private CompletionStage<Void> receipt(BuildPayment before,Phase next,ConstructionReceipt after){var town=data.town(before.terms().town());var old=receipt(before);return change(before,before.phase(next),()->town.receipt(before.terms().project(),after),()->town.receipt(before.terms().project(),old));}
    private void pulse(){
        if(closing||!available()||SuiteState.frozen(plugin.getName()))return;
        var pending=data.payments().values().stream().filter(p->p.pending()||p.needsAudit()).sorted(Comparator.comparing(p->p.terms().id())).toList();if(pending.isEmpty())return;
        var p=pending.get(Math.floorMod(cursor++,pending.size()));var t=p.terms();
        try{
            if(p.needsAudit()){audit(p);return;}
            if(t.kind()!=Kind.INSURANCE){var r=receipt(p);if(r.cancelling()&&Set.of(Phase.PAID,Phase.ARTIFACT_PENDING,Phase.READY).contains(p.phase())){track(change(p,Phase.REFUNDING));return;}}
            switch(p.phase()){
                case PREPARED->{
                    if(t.cents()==0){paid(p);return;}
                    var known=bank.status(t);CompletionStage<Map<?,?>> response="ABSENT".equals(known.get("status"))?bank.request(t):CompletableFuture.completedFuture(known);
                    track(response.thenCompose(row->{try(var guard=SuiteState.guard()){
                        if(closing)return CompletableFuture.completedFuture(null);
                        return switch((String)row.get("status")){case "PAID"->paidStage(p);case "REJECTED"->rejected(p);case "PREPARED","INVOKING","REVIEW"->CompletableFuture.completedFuture(null);default->CompletableFuture.failedFuture(new IllegalStateException("Неизвестный ответ казны"));};
                    }}));
                }
                case PAID->{if(!t.artifacts().isEmpty())armArtifacts(p);else track(change(p,Phase.READY));}
                case ARTIFACT_PENDING,EFFECTS_PENDING->{/* Unknown external effect after restart: explicit administrator reconciliation only. */}
                case READY->{if(towny.town(t.town())!=null)apply(p);}
                case APPLIED->effects(p);
                case REFUNDING->{if(receipt(p).state().equals("DONE"))track(change(p,Phase.REFUNDED));}
                default->{}
            }
        }catch(Exception error){warn(error);}
    }
    private void audit(BuildPayment payment){
        var t=payment.terms();String key="payment:"+payment.auditKey();
        var actor=new AuditRecord.Party("PLAYER",t.actor().toString(),t.actorName(),t.town().toString());
        var town=new AuditRecord.Party("TOWN",t.town().toString(),t.town().toString(),t.town().toString());
        var escrow=new AuditRecord.Party("ESCROW",t.id().toString(),"Оплата городского проекта",t.town().toString());
        var receipt=new AuditRecord(AuditRecord.id(plugin.getName(),t.id().toString(),key),payment.phaseAt(),plugin.getName(),"CONSTRUCTION_REFUND",payment.auditOutcome(),t.id().toString(),actor,town,escrow,"",0,AuditTrail.cents(t.cents()),"project="+t.project()+"; kind="+t.kind()+"; phase="+payment.auditKey());
        track(CompletableFuture.runAsync(()->{if(SuiteState.held())throw new IllegalStateException("Аудит под общей блокировкой");if(!AuditTrail.record(plugin,receipt))throw new IllegalStateException("Аудит оплаты пока не сохранён");},auditWriter).thenCompose(v->{try(var guard=SuiteState.guard()){return change(payment,payment.auditConfirmed(),()->{},()->{});}}));
    }
    private void paid(BuildPayment p){track(paidStage(p));}
    private CompletionStage<Void> paidStage(BuildPayment p){return p.terms().kind()==Kind.INSURANCE?change(p,Phase.PAID):receipt(p,Phase.PAID,receipt(p).state("DEBIT_CONFIRMED"));}
    private CompletionStage<Void> rejected(BuildPayment p){
        if(p.terms().kind()==Kind.INSURANCE)return change(p,Phase.REJECTED);
        var r=receipt(p);var cancelled=r.paid(0,r.paidItems()).state("ACTIVE").cancel(0,"Казна отклонила оплату").state("REFUND_PAID");
        return receipt(p,Phase.REJECTED,cancelled);
    }
    private void armArtifacts(BuildPayment p)throws Exception {
        var r=receipt(p);var items=new ArrayList<>(r.paidItems());for(var a:p.terms().artifacts())items.add(new ConstructionReceipt.Item(ItemCodec.decodeSingle(a.item()),a.amount()));
        var armed=r.paid(r.paidCents(),items).state("ARTIFACT_PENDING");
        track(receipt(p,Phase.ARTIFACT_PENDING,armed).thenCompose(v->{try(var guard=SuiteState.guard()){
            if(closing)return CompletableFuture.completedFuture(null);var current=data.payments().get(p.terms().id());
            try{if(archaeology.consumeRecorded(p.terms().town(),p.terms().project(),p.terms().actor()))return receipt(current,Phase.READY,armed.state("DEBIT_CONFIRMED"));
                var cancel=armed.paid(armed.paidCents(),r.paidItems()).state("ACTIVE").cancel(0,"Музей отклонил передачу");if(p.terms().kind()==Kind.VIRTUAL)cancel=cancel.state("REFUND_READY");return receipt(current,Phase.REFUNDING,cancel);
            }catch(Exception failure){warn(failure);return CompletableFuture.completedFuture(null);}
        }}));
    }
    private void apply(BuildPayment p)throws Exception {
        var t=p.terms();var town=data.town(t.town());
        if(t.kind()==Kind.INSURANCE){double old=town.insuranceReserve();long before=cents(old),after=Math.addExact(before,t.cents());if(after>100_000_000_000L)throw new IllegalStateException("Страховой резерв достиг предела; перевод сохранён для сверки");track(change(p,p.phase(Phase.APPLIED),()->town.setInsuranceReserve(after/100.0),()->town.setInsuranceReserve(old)));return;}
        var r=receipt(p);
        if(t.kind()==Kind.PHYSICAL){activatePhysical(p,r);return;}
        if(town.level(t.project())!=t.previousLevel())throw new IllegalStateException("Уровень здания изменён; оплату нужно сверить");
        boolean army=town.armyEstablished();track(change(p,p.phase(Phase.APPLIED),()->{town.setLevel(t.project(),t.level());town.receipt(t.project(),r.state("COMPLETE"));},()->{town.setLevel(t.project(),t.previousLevel());town.restoreArmyEstablished(army);town.receipt(t.project(),r);}));
    }
    private void activatePhysical(BuildPayment p,ConstructionReceipt r){
        var t=p.terms();var site=data.town(t.town()).constructionSite(t.project());if(site==null||!site.active())throw new IllegalStateException("Площадка оплаченной стройки изменена");
        var world=Bukkit.getWorld(site.worldId());if(world==null||!world.isChunkLoaded(site.originX()>>4,site.originZ()>>4))return;
        var finished=new CompletableFuture<Void>();active=finished;waitingRegion=finished;
        RaidRepairAccess.query(t.town(),t.project()).whenComplete((debt,error)->{
            if(error!=null){finished.completeExceptionally(error);return;}if(closing){finished.complete(null);return;}
            try{SuiteScheduler.region(plugin,site.location(world,new BlockOffset(0,0,0)),()->{
                try{
                    if(closing||!p.equals(data.payments().get(t.id()))||!data.writable()||!RaidRepairAccess.permits(debt)){finished.complete(null);return;}
                    waitingRegion=null;
                    if(!construction.preparePaidSite(t.town(),site,r)){finished.complete(null);return;}
                    var state=data.town(t.town());boolean paused=site.adminPaused();
                    change(p,p.phase(Phase.APPLIED),()->{site.adminPaused(false);state.receipt(t.project(),r.state("ACTIVE"));},()->{site.adminPaused(paused);state.receipt(t.project(),r);}).whenComplete((value,failure)->{if(failure!=null)finished.completeExceptionally(failure);else finished.complete(null);});
                }catch(Throwable failure){finished.completeExceptionally(failure);}
            });}catch(Throwable failure){finished.completeExceptionally(failure);}
        });track(finished);
    }
    private void effects(BuildPayment p){
        var t=p.terms();if(t.kind()!=Kind.VIRTUAL){track(change(p,Phase.COMPLETE));message(t,"Оплата подтверждена. "+(t.kind()==Kind.PHYSICAL?"Строительство доступно.":"Страховой резерв пополнен."));return;}
        int count=t.commands().size()+1;
        if(p.effectIndex()>=count){track(change(p,Phase.COMPLETE));message(t,"Улучшение здания сохранено.");return;}
        if(p.effectIndex()==0&&t.bonusBlocks()==0){track(change(p,p.skipEmptyBonus(),()->{},()->{}));return;}
        var armed=p.phase(Phase.EFFECTS_PENDING);
        track(change(p,armed,()->{},()->{}).thenCompose(v->{
            var result=new CompletableFuture<Void>();try(var guard=SuiteState.guard()){if(closing){result.complete(null);return result;}waitingRegion=result;}
            Bukkit.getGlobalRegionScheduler().run(plugin,task->{
                boolean allowed;try(var guard=SuiteState.guard()){allowed=!closing&&armed.equals(data.payments().get(t.id()))&&!SuiteState.frozen(plugin.getName());if(waitingRegion==result)waitingRegion=null;}
                if(!allowed){result.complete(null);return;}
                try{
                    // Foreign Towny/command I/O runs outside Suite. An interrupted invocation remains EFFECTS_PENDING.
                    boolean success=true;
                    if(armed.effectIndex()==0){Town owner=towny.town(t.town());if(owner==null)throw new IllegalStateException("Город удалён");synchronized(owner){owner.setBonusBlocks(Math.addExact(owner.getBonusBlocks(),t.bonusBlocks()));owner.save();}}
                    else{String command=t.commands().get(armed.effectIndex()-1);success=Bukkit.dispatchCommand(Bukkit.getConsoleSender(),command.startsWith("/")?command.substring(1):command);}
                    if(!success){result.complete(null);return;}
                    try(var guard=SuiteState.guard()){if(closing){result.complete(null);return;}change(armed,armed.effectConfirmed(),()->{},()->{}).whenComplete((x,error)->{if(error==null)result.complete(null);else result.completeExceptionally(error);});}
                }catch(Throwable failure){result.completeExceptionally(failure);}
            });return result;
        }));
    }
    public void admin(CommandSender sender,String[] args){try(var guard=SuiteState.guard()){
        if(!sender.isOp()&&!sender.hasPermission("neverlandtownybuilds.admin")){sender.sendMessage("§cНет прав.");return;}
        try{
            if(args.length==1||args.length==2&&args[1].equals("list")){data.payments().values().stream().sorted(Comparator.comparing((BuildPayment p)->!p.pending()).thenComparing(p->p.terms().created())).limit(40).forEach(p->sender.sendMessage(p.terms().id()+" / "+p.terms().town()+" / "+p.terms().project()+" / "+p.phase()+" / "+AuditTrail.cents(p.terms().cents())));return;}
            if(args.length==5&&args[1].equals("resolve-effect")&&args[4].equals("confirm")){
                var p=data.payments().get(UUID.fromString(args[2]));if(p==null||p.phase()!=Phase.EFFECTS_PENDING)throw new IllegalStateException("Нет незавершённого внешнего эффекта");
                if(!Set.of("applied","unpaid").contains(args[3]))throw new IllegalArgumentException("Укажите applied или unpaid после сверки");
                var next=args[3].equals("applied")?p.effectConfirmed():p.phase(Phase.APPLIED);track(change(p,next,()->{},()->{}));sender.sendMessage("§eРешение сохраняется. Повтор разрешён только после подтверждения отсутствия эффекта.");return;
            }
            sender.sendMessage("/townybuilds payments list | resolve-effect <UUID> <applied|unpaid> confirm");
            sender.sendMessage("Банковские операции сверяются через /townytreasury resolve-request. Артефакты — через /townybuilds refund resolve.");
        }catch(Exception error){sender.sendMessage("§e"+error.getMessage());}
    }}
    private void track(CompletionStage<?> stage){active=stage;stage.whenComplete((v,error)->{if(error!=null)warn(error);});}
    private void warn(Throwable error){if(System.currentTimeMillis()-warned<60000)return;warned=System.currentTimeMillis();plugin.getLogger().log(java.util.logging.Level.WARNING,"Оплата здания ожидает восстановления или сверки; сохранённый UUID не меняется",error);}
    private void message(Terms terms,String text){if(closing)return;var actor=Bukkit.getPlayer(terms.actor());if(actor!=null)SuiteScheduler.entity(plugin,actor,()->actor.sendMessage("§e[Город] "+text));}
    public CompletionStage<Void> prepareClose(){try(var guard=SuiteState.guard()){if(close==null){closing=true;if(task!=null)task.cancel();if(waitingRegion!=null){waitingRegion.complete(null);waitingRegion=null;}close=active.handle((v,e)->null).thenApply(v->{auditWriter.shutdown();return null;});}return close;}}
}
