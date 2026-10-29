package ru.neverland.morstownstick.service;

import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.*;
import ru.neverland.morstownstick.integration.*;
import ru.neverland.morstownstick.model.CellKey;
import ru.neverland.morstownstick.plan.*;
import java.util.*;
import java.util.concurrent.*;

/** Saved city expansion proposals. Towny remains the sole claimant and bank caller. */
public final class ClaimService {
    private final JavaPlugin plugin;private final TownyFacade towny;private final TownyClaimAdapter adapter;
    private final SelectionService selections;private final BorderCache borders;private final ExpansionRepository repository;
    private final Map<UUID,UUID> active=new HashMap<>();private boolean closing;
    public ClaimService(JavaPlugin plugin,TownyFacade towny,TownyClaimAdapter adapter,SelectionService selections,MessageService messages,BorderCache borders){
        this.plugin=plugin;this.towny=towny;this.adapter=adapter;this.selections=selections;this.borders=borders;
        repository=new ExpansionRepository(plugin.getDataFolder().toPath().resolve("expansion-plans.yml"),r->AuditTrail.record(plugin,r));
        repository.load().whenComplete((value,error)->{if(error!=null)plugin.getLogger().log(java.util.logging.Level.SEVERE,"Планы расширения приостановлены; исходные данные сохранены",error);});
    }
    public ExpansionRepository repository(){return repository;}
    public boolean isBusy(UUID player){try(var guard=SuiteState.guard()){return active.containsValue(player);}}
    public void playerQuit(UUID player){/* Do not erase a durable intent or replay it when the player returns. */}
    public CompletionStage<Void> prepareClose(){try(var guard=SuiteState.guard()){closing=true;active.clear();return repository.prepareClose();}}
    public void shutdown(){prepareClose();}
    public void awaitClosed(){repository.awaitClosed();}
    private void writable(){if(closing||!repository.ready()||repository.busy())throw new IllegalStateException("Планы загружаются, сохраняются или требуют проверки администратора");if(!active.isEmpty())throw new IllegalStateException("Дождитесь завершения текущего присоединения земли");if(!plugin.getConfig().getBoolean("claim.enabled",true))throw new IllegalStateException("Планы расширения временно отключены. Сохранённые планы не потеряны");}
    private Town permitted(Player player,boolean mayor,int count){
        if(!player.hasPermission("morstownstick.claim"))throw new IllegalStateException("У вас нет права создавать и согласовывать планы расширения");
        Town town=towny.town(player);if(town==null)throw new IllegalStateException("Сначала вступите в город");
        if(mayor?!town.getMayor().getUUID().equals(player.getUniqueId()):!towny.isMayorOrAssistant(player))throw new IllegalStateException(mayor?"Это действие доступно только действующему мэру":"Это действие доступно мэру и его заместителям");
        if(plugin.getConfig().getBoolean("claim.respect-towny-claim-permissions",true)&&(!player.hasPermission("towny.command.town.claim.town")||(count>1&&!player.hasPermission("towny.command.town.claim.town.multiple"))))throw new IllegalStateException("Towny не разрешает вам присоединять такое количество участков");
        return town;
    }
    private record Quote(List<CellKey> cells,double amount,boolean economy,int townSize,UUID mayor){}
    private Quote quote(Player player,Town town,Collection<CellKey> chosen)throws Exception{
        int maximum=Math.max(1,Math.min(64,plugin.getConfig().getInt("plans.max-chunks",64)));
        if(chosen.isEmpty()||chosen.size()>maximum)throw new IllegalStateException("Выберите от 1 до "+maximum+" участков для одного плана");
        for(var cell:chosen){if(!towny.isClaimableWorld(cell))throw new IllegalStateException("В мире "+cell.world()+" присоединение запрещено");if(!towny.isWilderness(cell))throw new IllegalStateException("Участок ["+cell.x()+", "+cell.z()+"] уже занят. Измените план");}
        var order=BfsPlanner.plan(chosen,towny.ownedCells(town));if(!order.unreachable().isEmpty())throw new IllegalStateException("Все участки должны соединяться с границей города");
        if(!town.hasUnlimitedClaims()&&town.availableTownBlocks()<chosen.size())throw new IllegalStateException("Городу не хватает доступных участков для всего плана");
        boolean economy=TownySettings.isUsingEconomy();if(economy&&(!TownyEconomyHandler.isActive()||TownyEconomyHandler.activeAdapter()==null))throw new IllegalStateException("Экономика Towny недоступна. Присоединение не запущено");
        var accepted=adapter.validateOnly(player,town,order.ordered().stream().map(CellKey::worldCoord).toList());
        if(accepted.size()!=chosen.size()||!new HashSet<>(accepted.stream().map(CellKey::from).toList()).equals(new HashSet<>(chosen)))throw new IllegalStateException("Towny отклонил часть участков. Измените план");
        double amount=economy?(chosen.size()==1?town.getTownBlockCost():town.getTownBlockCostN(chosen.size())):0;
        if(!Double.isFinite(amount)||amount<0)throw new IllegalStateException("Towny вернул некорректную стоимость земли");
        return new Quote(order.ordered(),amount,economy,town.getNumTownBlocks(),town.getMayor().getUUID());
    }
    private void recheck(Player player,ExpansionPlan plan)throws Exception{
        Town town=permitted(player,true,plan.cells().size());if(!town.getUUID().equals(plan.town()))throw new IllegalStateException("План принадлежит другому городу");
        var now=quote(player,town,plan.cells());if(!now.mayor().equals(plan.mayor())||Double.compare(now.amount(),plan.estimate())!=0||now.economy()!=plan.economy()||now.townSize()!=plan.townSize()||!now.cells().equals(plan.cells()))throw new IllegalStateException("Мэр, границы или цена изменились. Обновите план и согласуйте новую версию");
        if(!adapter.canAfford(town,plan.cells().size()))throw new IllegalStateException("В казне недостаточно денег для всего плана");
    }
    private ExpansionPlan find(UUID id){var plan=repository.state().plans().get(id);if(plan==null)throw new IllegalStateException("План не найден");return plan;}
    private ExpansionPlan own(Player player,UUID id){var plan=find(id);Town town=towny.town(player);if(town==null||!town.getUUID().equals(plan.town()))throw new IllegalStateException("План принадлежит другому городу");return plan;}
    public CompletionStage<ExpansionPlan> create(Player player)throws Exception{try(var guard=SuiteState.guard()){
        writable();var selected=selections.snapshot(player.getUniqueId());Town town=permitted(player,false,selected.size());
        for(var p:repository.state().plans().values())if(p.town().equals(town.getUUID())&&p.open())throw new IllegalStateException("У города уже есть открытый план: "+p.id()+". Используйте /t stick show "+p.id());
        var q=quote(player,town,selected);long now=System.currentTimeMillis();var plan=new ExpansionPlan(UUID.randomUUID(),town.getUUID(),player.getUniqueId(),q.mayor(),1,now,now,q.cells(),q.amount(),q.economy(),q.townSize(),ExpansionPlan.Status.DRAFT,null,List.of(),"Смета готова. Деньги не списаны; требуется согласование мэра");return save(player,plan);
    }}
    public CompletionStage<ExpansionPlan> revise(Player player,UUID id,int revision)throws Exception{try(var guard=SuiteState.guard()){
        writable();var old=own(player,id);old.requireRevision(revision);Town town=permitted(player,false,old.cells().size());if(old.status()!=ExpansionPlan.Status.DRAFT&&old.status()!=ExpansionPlan.Status.APPROVED)throw new IllegalStateException("Этот план нельзя изменять");if(!old.author().equals(player.getUniqueId())&&!town.getMayor().getUUID().equals(player.getUniqueId()))throw new IllegalStateException("Изменить план может его автор или мэр");
        var selected=selections.snapshot(player.getUniqueId());var q=quote(player,town,selected.isEmpty()?old.cells():selected);permitted(player,false,q.cells().size());var next=new ExpansionPlan(old.id(),old.town(),old.author(),q.mayor(),Math.addExact(old.revision(),1),old.created(),System.currentTimeMillis(),q.cells(),q.amount(),q.economy(),q.townSize(),ExpansionPlan.Status.DRAFT,null,List.of(),"Новая смета; прежнее согласование снято");return save(player,next);
    }}
    public CompletionStage<ExpansionPlan> approve(Player player,UUID id,int revision)throws Exception{try(var guard=SuiteState.guard()){
        writable();var p=own(player,id);p.requireRevision(revision);recheck(player,p);return save(player,p.approve(player.getUniqueId(),revision,System.currentTimeMillis()));
    }}
    public CompletionStage<ExpansionPlan> cancel(Player player,UUID id,int revision){try(var guard=SuiteState.guard()){
        writable();var p=own(player,id);p.requireRevision(revision);Town town=permitted(player,false,p.cells().size());if(!p.author().equals(player.getUniqueId())&&!town.getMayor().getUUID().equals(player.getUniqueId()))throw new IllegalStateException("Отменить план может его автор или мэр");return save(player,p.cancel(System.currentTimeMillis()));
    }}
    public CompletionStage<ExpansionPlan> execute(Player player,UUID id,int revision)throws Exception{try(var guard=SuiteState.guard()){
        writable();var p=own(player,id);p.requireRevision(revision);recheck(player,p);var intent=p.intent(player.getUniqueId(),revision,System.currentTimeMillis());active.put(id,player.getUniqueId());
        var committed=save(player,intent);committed.whenComplete((value,error)->{try(var locked=SuiteState.guard()){
            if(error!=null){active.remove(id);return;}if(closing)return;
            SuiteScheduler.entityLater(plugin,player,()->launch(player,intent),()->observe(intent,null,"Мэр вышел до запуска. Требуется сверка; повтор запрещён"),1);
        }});return committed;
    }}
    private void launch(Player player,ExpansionPlan intent){
        if(closing||!active.containsKey(intent.id()))return;
        try{
            if(!player.isOnline())throw new IllegalStateException("Мэр вышел до запуска");
            if(!plugin.getConfig().getBoolean("claim.enabled",true))throw new IllegalStateException("Модуль присоединения отключён до запуска");
            recheck(player,intent);Town town=towny.town(player);
            var accepted=adapter.validateAndCharge(player,town,intent.cells().stream().map(CellKey::worldCoord).toList(),()->{try{recheck(player,intent);}catch(com.palmergames.bukkit.towny.exceptions.TownyException error){throw error;}catch(Exception error){throw new IllegalStateException(error.getMessage(),error);}});
            if(accepted.size()!=intent.cells().size()||!new HashSet<>(accepted.stream().map(CellKey::from).toList()).equals(new HashSet<>(intent.cells())))throw new IllegalStateException("Состав участков изменился при проверке Towny");
            adapter.claimObserved(player,town,accepted,(ignored,error)->observe(intent,player,error==null?"Towny закончил обработку. Владение проверено; списание средств этим подтверждением не доказано":"Towny завершился с ошибкой. Проверьте участки и банковский журнал"));
        }catch(Exception error){observe(intent,player,"Присоединение не подтверждено: "+String.valueOf(error.getMessage()));}
    }
    private List<CellKey> owned(ExpansionPlan p){Town town=TownyAPI.getInstance().getTown(p.town());return town==null?List.of():p.cells().stream().filter(cell->towny.isOwnedBy(cell,town)).toList();}
    private void observe(ExpansionPlan intent,Player player,String reason){try(var guard=SuiteState.guard()){
        if(closing||!active.containsKey(intent.id()))return;
        var found=owned(intent);var next=intent.result(found,clip(reason),System.currentTimeMillis());
        save(player,next).whenComplete((saved,error)->{try(var locked=SuiteState.guard()){active.remove(intent.id());if(error!=null){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Результат расширения требует ручной сверки "+intent.id(),error);return;}selections.removeAll(intent.author(),found);borders.invalidate(intent.town());if(player!=null)reply(player,()->show(player,saved.id()));}});
    }}
    public CompletionStage<ExpansionPlan> reconcile(CommandSender admin,UUID id,String reason){try(var guard=SuiteState.guard()){
        if(!admin.hasPermission("morstownstick.admin"))throw new IllegalStateException("Нет прав администратора");writable();var p=find(id);if(reason.isBlank()||reason.length()>600)throw new IllegalStateException("Укажите результат сверки (1–600 символов)");return save(admin,p.reconcile(owned(p),"Администратор "+admin.getName()+": "+reason,System.currentTimeMillis()));
    }}
    private CompletionStage<ExpansionPlan> save(CommandSender actor,ExpansionPlan plan){
        var previous=repository.state();var party=actor instanceof Player p?AuditTrail.player(p.getUniqueId()):new AuditRecord.Party("ADMIN","",actor==null?"Система":actor.getName(),"");
        var city=AuditTrail.town(plan.town());var record=new AuditRecord(AuditRecord.id(plugin.getName(),plan.id().toString(),plan.revision()+":"+plan.status()+":"+plan.updated()),plan.updated(),plugin.getName(),"CITY_EXPANSION",plan.status().name(),plan.id().toString(),party,city,city,"",0,"","Версия "+plan.revision()+"; участков "+plan.cells().size()+"; смета "+AuditTrail.money(plan.estimate())+"; наблюдается владение "+plan.observedOwned().size()+". "+plan.note());
        return repository.commit(previous,previous.with(plan),record).thenApply(v->plan);
    }
    public void start(Player player){SuiteScheduler.entity(plugin,player,()->{try{var town=towny.town(player);if(town!=null)for(var p:repository.state().plans().values())if(p.town().equals(town.getUUID())&&p.open()){show(player,p.id());return;}respond(player,create(player));}catch(Exception e){tell(player,e.getMessage());}});}
    public void command(Player player,String[] args){try{
        if(args.length==0||args[0].equalsIgnoreCase("plan")||args[0].equalsIgnoreCase("claim")){start(player);return;}
        String action=args[0].toLowerCase(Locale.ROOT);if(action.equals("plans")){list(player);return;}
        if(args.length<2)throw new IllegalStateException("Укажите полный ID плана из /t stick plans");UUID id=UUID.fromString(args[1]);
        if(action.equals("show")){show(player,id);return;}if(args.length!=3)throw new IllegalStateException("Укажите ID и номер версии из /t stick show "+id);int revision=Integer.parseInt(args[2]);
        respond(player,switch(action){case "approve"->approve(player,id,revision);case "execute"->execute(player,id,revision);case "revise"->revise(player,id,revision);case "cancel"->cancel(player,id,revision);default->throw new IllegalStateException("Неизвестное действие плана");});
    }catch(Exception error){tell(player,error.getMessage());}}
    public void respond(CommandSender sender,CompletionStage<ExpansionPlan> future){future.whenComplete((plan,error)->reply(sender,()->{if(error!=null)tell(sender,"Изменение не подтверждено: "+root(error).getMessage());else if(sender instanceof Player p)show(p,plan.id());else tell(sender,"Результат сверки сохранён: "+plan.id());}));}
    public void list(Player player){try(var guard=SuiteState.guard()){Town town=towny.town(player);if(town==null){tell(player,"Сначала вступите в город");return;}var plans=repository.state().plans().values().stream().filter(p->p.town().equals(town.getUUID())).sorted(Comparator.comparingLong(ExpansionPlan::updated).reversed()).limit(10).toList();tell(player,"Планы города (последние 10):");for(var p:plans)tell(player,p.id()+" • версия "+p.revision()+" • "+label(p));if(plans.isEmpty())tell(player,"Выберите участки палкой, затем /t stick plan");}}
    public void show(Player player,UUID id){try(var guard=SuiteState.guard()){
        var p=own(player,id);tell(player,"План "+p.id()+" • версия "+p.revision()+" • "+label(p));tell(player,"Участков: "+p.cells().size()+". Оценка цены: "+AuditTrail.money(p.estimate())+(p.economy()?" монет из казны":" (экономика Towny выключена)"));
        tell(player,"Оплату выполняет Towny; смета не подтверждает списание. "+p.note());
        for(var c:p.cells())tell(player,c.world()+" ["+c.x()+", "+c.z()+"]");
        if(p.status()==ExpansionPlan.Status.DRAFT)tell(player,"Мэр: /t stick approve "+id+" "+p.revision());
        if(p.status()==ExpansionPlan.Status.APPROVED)tell(player,"Мэр: /t stick execute "+id+" "+p.revision());
        if(p.status()==ExpansionPlan.Status.DRAFT||p.status()==ExpansionPlan.Status.APPROVED){tell(player,"Обновить смету: /t stick revise "+id+" "+p.revision());tell(player,"Отменить без запуска: /t stick cancel "+id+" "+p.revision());}
    }}
    private String label(ExpansionPlan p){return switch(p.status()){case DRAFT->"Ожидает согласования мэра";case APPROVED->"Согласовано; ещё не запущено";case INTENT->active.containsKey(p.id())?"Присоединение выполняется":"Нужна сверка администратора; повтор запрещён";case REVIEW->"Нужна сверка администратора";case OBSERVED->"Все участки присоединены; платеж проверяется отдельно";case CANCELLED->"Отменено до запуска";case RECONCILED->"Администратор завершил сверку";};}
    private void reply(CommandSender sender,Runnable action){if(closing||sender==null||!plugin.isEnabled())return;SuiteScheduler.reply(plugin,sender,action);}
    private void tell(CommandSender sender,String value){sender.sendMessage("§a[Территории] §f"+value);}
    private static Throwable root(Throwable e){while(e.getCause()!=null)e=e.getCause();return e;}
    private static String clip(String value){return value.length()>1000?value.substring(0,1000):value;}
}
