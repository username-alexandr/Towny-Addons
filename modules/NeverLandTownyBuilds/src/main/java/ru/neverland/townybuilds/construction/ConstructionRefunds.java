package ru.neverland.townybuilds.construction;

import com.palmergames.bukkit.towny.TownyEconomyHandler;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.economy.Account;
import com.palmergames.bukkit.towny.object.economy.AccountObserver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.core.*;
import ru.neverland.townybuilds.data.*;
import ru.neverland.townybuilds.integration.*;
import ru.neverland.townybuilds.model.*;
import ru.neverland.townybuilds.service.*;
import ru.neverland.townybuilds.storage.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/** Durable cancellation: remove only purchased blocks, then refund once and drain retained cargo into town storage. */
public final class ConstructionRefunds {
    private final JavaPlugin plugin;
    private final DataStore data;
    private final TownyHook towny;
    private final ConstructionService construction;
    private final DefinitionRegistry definitions;
    private final ArchaeologyBridge archaeology;
    private final RussianItemNames names;
    private BukkitTask task;
    private ru.neverland.townybuilds.payments.BuildPayments payments;
    public void payments(ru.neverland.townybuilds.payments.BuildPayments value){payments=value;}
    private long generation, warned;
    private boolean closing;
    private CompletionStage<?> active=CompletableFuture.completedFuture(null);
    private CompletionStage<Void> close;
    private final ThreadPoolExecutor credits=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1),r->{var t=new Thread(r,"NeverLand-build-refund-bank");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    public CompletionStage<Void> prepareClose(){try(var guard=SuiteState.guard()){if(close==null){closing=true;stop();close=active.handle((v,e)->null).thenApply(v->{credits.shutdown();return null;});}return close;}}
    private void track(CompletionStage<?> operation){active=operation;operation.whenComplete((v,error)->{if(error!=null)warn(new IllegalStateException("Возврат ожидает сверки",error));});}

    private final Set<UUID> restoring = new HashSet<>();

    public ConstructionRefunds(JavaPlugin plugin, DataStore data, TownyHook towny, ConstructionService construction,
                               DefinitionRegistry definitions, ArchaeologyBridge archaeology, RussianItemNames names) {
        this.plugin=plugin; this.data=data; this.towny=towny; this.construction=construction;
        this.definitions=definitions; this.archaeology=archaeology; this.names=names;
    }
    public void start() { stop(); task=SuiteScheduler.get().runTaskTimer(plugin,this::tick,20,20); }
    public void stop() { generation++; if(task!=null)task.cancel(); task=null; restoring.clear(); }
    public int fee() { return Math.max(0,Math.min(100,plugin.getConfig().getInt("settings.construction.cancel-fee-percent",10))); }
    public boolean allowed(Player player,Town town) {
        Town current=player==null?null:towny.town(player);
        return town!=null && current!=null && current.getUUID().equals(town.getUUID()) && (towny.isMayor(player,town) || CouncilAccess.check(player,town.getUUID(),"build_cancel",false));
    }
    public ConstructionReceipt receipt(UUID town,String project) { return data.town(town).constructionReceipts().get(project); }
    public boolean blocks(UUID town,String project) { var r=receipt(town,project);return r!=null&&r.blocksConstruction()&&!r.state().equals("DONE"); }

    public List<String> quote(UUID town,String project,boolean admin) {
        var site=data.town(town).constructionSite(project); var r=receipt(town,project);
        if(site==null||!site.active())throw new IllegalStateException("Эта постройка сейчас не строится");
        if(r==null)throw new IllegalStateException("У старой стройки нет квитанции. Администратор должен сверить стоимость: /townybuilds refund legacy");
        if(!r.state().equals("ACTIVE"))throw new IllegalStateException(status(r));
        var result=r.cancel(admin?0:fee(),""); List<String> lines=new ArrayList<>();
        lines.add("В казну: "+AuditTrail.cents(result.refundCents())+" монет");
        lines.add("Комиссия: "+result.feePercent()+"% денег и оплаченных ресурсов");
        lines.add("На склад города:");
        for(var item:result.remaining())lines.add(names.name(item.sample())+" × "+item.amount());
        lines.add("Блоки незавершённого этапа будут убраны.");
        if(site.completedStage()>0)lines.add("Предыдущий уровень здания сохранится.");
        lines.add("Если склад заполнен, остаток дождётся свободного места.");
        return lines;
    }

    public String cancel(UUID townId,String project,UUID expected,boolean admin,Player player,String actor) throws IOException {
        Town town=towny.town(townId); if(town==null)throw new IllegalStateException("Город не найден");
        if(!admin&&!allowed(player,town))throw new IllegalStateException("Отменить стройку может мэр или уполномоченный строитель");
        var state=data.town(townId);var site=state.constructionSite(project);var r=state.constructionReceipts().get(project);
        if(site==null||!site.active())throw new IllegalStateException("Стройка уже завершена или отменена");
        if(r==null)throw new IllegalStateException("Сначала администратор должен сверить стоимость старой стройки");
        if(expected!=null&&!r.id().equals(expected))throw new IllegalStateException("Стройка изменилась. Откройте подтверждение заново");
        if(r.cancelling())return status(r);
        if(!r.state().equals("ACTIVE"))throw new IllegalStateException(status(r));
        var next=r.cancel(admin?0:fee(),actor);
        AuditTrail.require(audit(townId,project,next,"REQUESTED"));
        track(writeAsync(state,project,r,next));
        return "Отмена сохраняется. В казну вернётся "+AuditTrail.cents(next.refundCents())+" монет; ресурсы поступят на склад города.";
    }

    /** Reserve the resource fund durably BEFORE touching Towny's bank. A doubtful debit is never repeated. */
    public boolean begin(Player player,Town town,ProjectDefinition project,LevelDefinition level,
                      ConstructionPreparation preparation,ResourceFund fund,String reason) throws Exception {
        if(payments==null)throw new IllegalStateException("Подтверждаемая оплата недоступна");
        payments.begin(player,town,project,level,preparation,fund,reason);return true;
    }
    private static Map<String,ResourceFund> restoreFund(Map<String,ResourceFund> funds,String project,ResourceFund fund) {
        var copy=new HashMap<>(funds);copy.put(project,fund);return copy;
    }
    public void complete(UUID town,String project) {
        var state=data.town(town);var r=state.constructionReceipts().get(project);
        if(r!=null&&r.state().equals("ACTIVE"))state.receipt(project,r.state("COMPLETE"));
    }
    public void beginFree(Player player,Town town,ConstructionPreparation preparation) {
        String project=preparation.site().projectId();var state=data.town(town.getUUID());
        var prior=receipt(town.getUUID(),project);
        if(prior!=null&&(!prior.state().equals("DONE")||!prior.remaining().isEmpty()))throw new IllegalStateException("Дождитесь завершения предыдущего возврата");
        state.receipt(project,construction.captureReceipt(preparation,0,List.of()));
        construction.commit(preparation,player,town);
    }

    private void tick() {
        if(closing||!data.writable()||!active.toCompletableFuture().isDone())return;
        for(var town:data.towns().values())for(var entry:town.constructionReceipts().entrySet()) {
            var r=entry.getValue();String project=entry.getKey();
            try {
                if(r.state().equals("REMOVING")) {
                    var site=town.constructionSite(project);if(site==null||!site.active())continue;
                    var world=Bukkit.getWorld(site.worldId());if(world==null||!world.isChunkLoaded(site.originX()>>4,site.originZ()>>4)||!restoring.add(r.id()))continue;
                    long epoch=generation;
                    SuiteScheduler.region(plugin,new Location(world,site.originX(),site.originY(),site.originZ()),()->{
                        try { if(epoch!=generation||receipt(town.townId(),project)!=r)return;
                            if(data.writable()&&construction.cancellationTerritoryOwned(town.townId(),site,r)&&construction.restoreCancelled(site,r))track(writeAsync(town,project,r,r.state("REFUND_READY")));
                        }catch(Exception ex){warn(ex);}finally{restoring.remove(r.id());}
                    });
                } else if(r.state().equals("REFUND_READY")) {
                    Town owner=towny.town(town.townId());if(owner==null||(r.refundCents()>0&&!economyReady()))continue;
                    if(payments!=null&&!payments.refundBacked(town.townId(),project,r))continue;
                    String category=payments==null?"construction":payments.refundCategory(r.id());
                    var paying=r.state("REFUND_PAYING");
                    track(writeAsync(town,project,r,paying).thenCompose(v->CompletableFuture.supplyAsync(()->bank(owner,paying,category,"Отмена строительства "+project),credits)).thenCompose(paid->{try(var guard=SuiteState.guard()){
                        return writeAsync(town,project,paying,paying.state("REFUND_PAID")).thenRun(()->audit(town.townId(),project,paying,"MONEY_RETURNED"));
                    }}));return;
                } else if(Set.of("REFUND_PAID","ACTIVE","COMPLETE").contains(r.state())) {
                    if(drain(town,project,r))return;
                }
            }catch(Exception ex){warn(ex);}
        }
    }
    private boolean drain(TownData town,String project,ConstructionReceipt r)throws IOException {
        if(!r.remaining().isEmpty()&&!data.storageBusy(town.townId(),"warehouse")){
            ItemStack[] original=town.storage(),work=StockMath.copy(original);List<ConstructionReceipt.Item> remaining=new ArrayList<>();boolean changed=false;
            for(var item:r.remaining()){
                int take=Math.min(item.amount(),MunicipalDeposits.capacity(work,item.sample()));
                if(take>0){var stack=item.sample();stack.setAmount(take);if(!StockMath.insert(work,new ItemStack[]{stack}))throw new IllegalStateException("Склад изменился");changed=true;}
                if(take<item.amount())remaining.add(new ConstructionReceipt.Item(item.sample(),item.amount()-take));
            }
            if(changed){var next=r.remaining(remaining);track(data.commitAsync(()->{if(town.constructionReceipts().get(project)!=r)throw new IllegalStateException("Квитанция возврата изменена");town.setStorage(work,work.length);town.receipt(project,next);},()->{town.setStorage(original,original.length);town.receipt(project,r);}));return true;}
        }
        if(r.remaining().isEmpty()&&Set.of("REFUND_PAID","COMPLETE").contains(r.state())){
            ConstructionSite old=town.constructionSite(project);ConstructionSite restored=null;
            if(r.state().equals("REFUND_PAID")&&old!=null&&old.completedStage()>0){restored=new ConstructionSite(project,old.worldId(),old.originX(),old.originY(),old.originZ(),old.facing(),old.completedStage(),old.completedStage(),old.completedStage()+1,false,old.architectureVersion());restored.blueprintSet(old.blueprintSet());}
            var replacement=restored;boolean reset=r.state().equals("REFUND_PAID");
            track(data.commitAsync(()->{if(town.constructionReceipts().get(project)!=r)throw new IllegalStateException("Квитанция возврата изменена");if(reset){if(replacement==null)town.removeConstructionSite(project);else town.setConstructionSite(replacement);}town.receipt(project,r.state("DONE"));},()->{if(old==null)town.removeConstructionSite(project);else town.setConstructionSite(old);town.receipt(project,r);}));return true;
        }
        return false;
    }
    private CompletionStage<Void> writeAsync(TownData town,String project,ConstructionReceipt before,ConstructionReceipt after){
        try(var guard=SuiteState.guard()){
            if(data.busy())return data.pendingWrite().thenCompose(v->writeAsync(town,project,before,after));
            return data.commitAsync(()->{if(!before.equals(town.constructionReceipts().get(project)))throw new IllegalStateException("Возврат уже изменён");town.receipt(project,after);},()->town.receipt(project,before));
        }
    }
    private boolean bank(Town town,ConstructionReceipt r,String category,String reason) {
        if(SuiteState.held())throw new IllegalStateException("Банковский возврат запрещён под общей блокировкой");
        long cents=r.refundCents();if(cents==0)return true;
        if(!economyReady())throw new IllegalStateException("Экономика недоступна; возврат ожидает сверки");
        Account account=town.getAccount();String token="[nlt-build:"+r.id()+":refund]";
        var proof=new PaymentEvidence(account,true,token,cents);
        var observer=new AccountObserver(){public void deposited(Account a,double n,String why){proof.observe(a,true,n,why);}public void withdrew(Account a,double n,String why){proof.observe(a,false,n,why);}};
        synchronized(account){account.addObserver(observer);try{
            try{account.deposit(cents/100.0,"[NLT|refund|"+category+"|construction] "+token+" "+reason);}catch(RuntimeException error){if(!proof.observed())throw error;}
            if(!proof.observed())throw new IllegalStateException("Нет точной квитанции частичного возврата; требуется сверка");
            return true;
        }finally{account.removeObserver(observer);}}
    }
    public static boolean economyReady() {
        if(!TownyEconomyHandler.isActive())return false;
        if(!TownyEconomyHandler.getType().name().contains("VAULT"))return true;
        for(Class<?> type:Bukkit.getServicesManager().getKnownServices())if(type.getName().equals("net.milkbowl.vault.economy.Economy")||type.getName().equals("net.milkbowl.vault2.economy.Economy")) {
            var service=Bukkit.getServicesManager().getRegistration(type);if(service==null||!service.getPlugin().isEnabled())continue;
            try{if(Boolean.TRUE.equals(type.getMethod("isEnabled").invoke(service.getProvider())))return true;}catch(ReflectiveOperationException ignored){}
        }
        return false;
    }
    private void write(TownData town,String project,ConstructionReceipt next) throws IOException {
        var old=town.constructionReceipts().get(project);town.receipt(project,next);
        try{data.saveOrThrow();}catch(IOException|RuntimeException ex){town.receipt(project,old);throw ex;}
    }
    private boolean audit(UUID town,String project,ConstructionReceipt r,String step) {
        return AuditTrail.record(plugin,step,r.id().toString(),"CONSTRUCTION_REFUND",step,
                new AuditRecord.Party("ACTOR",r.actor(),r.actor(),""),AuditRecord.Party.unknown(),AuditTrail.town(town),"",0,
                AuditTrail.cents(step.equals("PURCHASED")?r.paidCents():r.refundCents()),project+"; fee="+r.feePercent()+"; resources="+r.paidItems().stream().map(i->i.sample().getType()+"x"+i.amount()).toList()+"; legacy="+r.legacy());
    }
    private void warn(Exception ex) { if(System.currentTimeMillis()-warned>60000){warned=System.currentTimeMillis();plugin.getLogger().warning("Строительство: "+ex.getMessage());} }
    public void admin(CommandSender sender,String[] args) {
        try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
            try {
                if(!sender.isOp()&&!sender.hasPermission("neverlandtownybuilds.admin.cancel"))throw new IllegalStateException("Нет права neverlandtownybuilds.admin.cancel");
                if(args.length==2&&args[1].equalsIgnoreCase("list")) {
                    for(var town:data.towns().values())for(var entry:town.constructionReceipts().entrySet()) {
                        var r=entry.getValue();
                        if(!r.state().equals("DONE"))sender.sendMessage(town.townId()+" / "+entry.getKey()+" / "+r.id()+" — "+status(r)+"; оплачено="+AuditTrail.cents(r.paidCents())+"; возврат="+AuditTrail.cents(r.refundCents())+"; осталось ресурсов="+r.remaining().stream().mapToLong(ConstructionReceipt.Item::amount).sum());
                    }
                    return;
                }
                if(args.length>=4&&args[1].equalsIgnoreCase("legacy")) {
                    Town owner=findTown(args[2]);String project=args[3];TownData state=data.town(owner.getUUID());
                    var site=state.constructionSite(project);if(site==null||!site.active())throw new IllegalStateException("Активная стройка не найдена");
                    if(receipt(owner.getUUID(),project)!=null)throw new IllegalStateException("У стройки уже есть квитанция");
                    var definition=definitions.project(project);if(definition==null||definition.level(site.targetStage())==null)throw new IllegalStateException("Описание проекта недоступно");
                    var level=definition.level(site.targetStage());
                    boolean free=state.level(project)>=site.targetStage();
                    List<ConstructionReceipt.Item> items=new ArrayList<>();
                    if(!free) {
                        for(var item:level.resources())items.add(new ConstructionReceipt.Item(item,item.getAmount()));
                        if(definition.type()==ProjectType.WONDER)items.addAll(archaeology.refundableArtifacts(project));
                    }
                    if(args.length!=6||!args[4].equalsIgnoreCase("confirm")) {
                        sender.sendMessage("Старые цены неизвестны. Ниже ТЕКУЩИЕ требования; сверьте их с оплатой и журналом перед подтверждением.");
                        sender.sendMessage("Текущая цена: "+(free?"0":AuditTrail.money(level.money())));
                        for(var item:items)sender.sendMessage(names.name(item.sample())+" × "+item.amount());
                        sender.sendMessage("После сверки: /townybuilds refund legacy "+owner.getUUID()+" "+project+" confirm <фактически_списанная_сумма>");
                        sender.sendMessage("Если состав ресурсов менялся, сначала восстановите его в конфиге проекта и перезагрузите настройки.");return;
                    }
                    double money=Double.parseDouble(args[5]);if(free&&money!=0)throw new IllegalArgumentException("Повторное размещение открытого Чуда было бесплатным");
                    var r=construction.legacyReceipt(site,money,items);
                    AuditTrail.require(AuditTrail.record(plugin,"LEGACY_ACCEPTED",r.id().toString(),"CONSTRUCTION_REFUND","VERIFIED",new AuditRecord.Party("ADMIN",sender.getName(),sender.getName(),""),AuditRecord.Party.unknown(),AuditTrail.town(owner.getUUID()),"",0,AuditTrail.cents(r.paidCents()),project+"; "+items.stream().map(i->i.sample().getType()+"x"+i.amount()).toList()));
                    write(state,project,r);sender.sendMessage("Квитанция сохранена. Теперь доступна отмена с возвратом.");return;
                }
                if(args.length==6&&args[1].equalsIgnoreCase("resolve")) {
                    Town owner=findTown(args[2]);String project=args[3];UUID id=UUID.fromString(args[4]);String decision=args[5].toLowerCase(Locale.ROOT);
                    var r=receipt(owner.getUUID(),project);if(r==null||!r.id().equals(id))throw new IllegalArgumentException("Квитанция не найдена или заменена");
                    ConstructionReceipt next;
                    if(r.state().equals("REFUND_PAYING")&&Set.of("paid","not-paid").contains(decision)) next=r.state(decision.equals("paid")?"REFUND_PAID":"REFUND_READY");
                    else if(r.state().equals("DEBIT_PENDING")&&Set.of("paid","not-paid").contains(decision)) next=r.paid(decision.equals("paid")?r.paidCents():0,r.paidItems()).state("ACTIVE").cancel(0,sender.getName());
                    else if(r.state().equals("DEBIT_CONFIRMED")&&decision.equals("paid"))next=r.state("ACTIVE").cancel(0,sender.getName());
                    else if(r.state().equals("ARTIFACT_PENDING")&&Set.of("artifacts-paid","artifacts-not-paid").contains(decision))
                        next=r.paid(r.paidCents(),decision.equals("artifacts-paid")?r.paidItems():r.paidItems().subList(0,r.baseItems())).state("ACTIVE").cancel(0,sender.getName());
                    else throw new IllegalArgumentException("Нет соответствующей операции для сверки. paid/not-paid: факт банковской операции; artifacts-paid/artifacts-not-paid: факт списания артефактов.");
                    if(next.state().equals("REMOVING")&&payments!=null&&payments.withoutPhysicalSite(r.id()))next=next.state("REFUND_READY");
                    AuditTrail.require(audit(owner.getUUID(),project,next,"RESOLVED_"+r.state()+"_"+decision+"_"+sender.getName()));
                    write(data.town(owner.getUUID()),project,next);sender.sendMessage("Решение сохранено. "+status(next));return;
                }
                sender.sendMessage("/townybuilds refund list");
                sender.sendMessage("/townybuilds refund legacy <город|UUID> <проект>");
                sender.sendMessage("/townybuilds refund resolve <город|UUID> <проект> <UUID квитанции> <paid|not-paid|artifacts-paid|artifacts-not-paid>");
                sender.sendMessage("Решение resolve принимается только после сверки с журналом банка/музея.");
            }catch(Exception ex){sender.sendMessage("§e"+ex.getMessage());}
        }
    }
    private Town findTown(String name) {
        Town town;
        try{town=towny.town(UUID.fromString(name));}catch(IllegalArgumentException ex){town=com.palmergames.bukkit.towny.TownyAPI.getInstance().getTown(name);}
        if(town==null)throw new IllegalArgumentException("Город не найден");return town;
    }
    public static String status(ConstructionReceipt r) {
        return switch(r.state()) {
            case "TREASURY_PENDING" -> "Ожидается подтверждение казны: "+r.id();
            case "DEBIT_PENDING","ARTIFACT_PENDING","DEBIT_CONFIRMED","REFUND_PAYING" -> "Операция ожидает сверки администратора: "+r.id();
            case "REMOVING" -> "Отмена: подготовка площадки. Подойдите к стройке; контейнеры должны быть пустыми.";
            case "REFUND_READY" -> "Возврат денег ожидает доступности экономики";
            case "REFUND_PAID" -> "Деньги возвращены. Освободите место и закройте склад для получения оставшихся ресурсов.";
            case "DONE","COMPLETE" -> "Операция завершена";
            default -> "Строительство оплачено";
        };
    }
}
