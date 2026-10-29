package ru.neverland.townydiplomacy.aid;

import java.util.*;
import java.util.concurrent.*;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import ru.neverland.core.*;
import ru.neverland.townydiplomacy.*;

/** Optional political evidence. It never writes the old treaty registry or changes combat rules. */
public final class AidService {
    private final NeverLandTownyDiplomacy plugin;private final DiplomacyService diplomacy;private final AidRepository repository;
    private record Observation(AidFact fact,Set<UUID> pacts){Observation{pacts=Set.copyOf(pacts);}}
    private final ArrayBlockingQueue<Observation> pending=new ArrayBlockingQueue<>(512);
    private Set<UUID> stablePacts=Set.of();
    private AidSettings settings=new AidSettings(true,30,30,20);private String configIssue="";
    private boolean loaded,stopping,gap;private String issue="Сведения о помощи загружаются";private CompletionStage<?> operation=CompletableFuture.completedFuture(null);private CompletionStage<Void> closed;
    public AidService(NeverLandTownyDiplomacy plugin,DiplomacyService diplomacy){this.plugin=plugin;this.diplomacy=diplomacy;repository=new AidRepository(plugin.getDataFolder().toPath().resolve("mutual-aid.yml"),receipt->AuditTrail.record(plugin,receipt),AidService::auditNames);}
    private static AuditRecord auditNames(AuditRecord r){try(var guard=SuiteState.guard()){return new AuditRecord(r.id(),r.at(),r.module(),r.kind(),r.outcome(),r.operation(),auditName(r.actor()),auditName(r.from()),auditName(r.to()),r.asset(),r.quantity(),r.money(),r.details());}}
    private static AuditRecord.Party auditName(AuditRecord.Party p){String name=p.name();if(p.type().equals("TOWN"))name=AuditTrail.town(UUID.fromString(p.id())).name();else if(p.type().equals("PLAYER"))name=AuditTrail.player(UUID.fromString(p.id())).name();return new AuditRecord.Party(p.type(),p.id(),name,p.town());}
    public AidRepository repository(){return repository;}
    public AidSettings settings(){return settings;}
    public void configure(){try{settings=AidSettings.load(plugin.getConfig());configIssue="";}catch(RuntimeException error){configIssue=error.getMessage();plugin.getLogger().warning("Новые обязательства помощи приостановлены: "+configIssue);}}
    public void start(){
        configure();
        operation=repository.load().thenCompose(before->{try(var guard=SuiteState.guard()){
            var next=before.neutral("Перерыв в наблюдении: перезапуск; прежние квитанции сохранены");
            for(var pact:before.pacts().values())if(pact.phase()==AidPact.Phase.ACCEPTING||pact.phase()==AidPact.Phase.FINALIZING||repository.interruptedActivations().contains(pact.id())||pact.phase()==AidPact.Phase.ACTIVE&&!mayors(pact))next=next.put(pact.phase(AidPact.Phase.ENDED,pact.accepter(),"Подтверждение прервано перезапуском"),"SYSTEM","INTERRUPTED_ACCEPTANCE",System.currentTimeMillis());
            return repository.commit(before,next);
        }}).whenComplete((state,error)->{try(var guard=SuiteState.guard()){if(error!=null)failure(error);else{loaded=true;issue="";stablePacts=eligible(System.currentTimeMillis());}}});
        SuiteScheduler.get().runTaskTimer(plugin,this::tick,1,10);
    }
    public boolean ready(){try(var guard=SuiteState.guard()){return loaded&&settings.enabled()&&configIssue.isEmpty()&&!stopping&&!gap&&issue.isEmpty()&&repository.writable()&&repository.state().calls().size()<2000&&diplomacy.healthy()&&!SuiteState.frozen(plugin.getName());}}
    public String status(){try(var guard=SuiteState.guard()){if(!configIssue.isEmpty())return configIssue;if(!settings.enabled())return "Взаимопомощь отключена администратором без последствий";if(!issue.isEmpty())return issue;if(repository.state().calls().size()>=2000)return "История вызовов заполнена; новые нападения не оцениваются до архивирования";if(gap)return "Наблюдение остановлено: очередь переполнена; вызовы не оцениваются";if(!diplomacy.healthy())return "Дипломатия недоступна; вызовы не оцениваются";if(stopping||SuiteState.frozen(plugin.getName()))return "Помощь приостановлена без последствий";return repository.busy()?"Изменение сохраняется":"Реестр помощи доступен";}}
    private boolean idle(){return loaded&&!stopping&&repository.writable()&&!repository.busy()&&operation.toCompletableFuture().isDone();}
    private void requireReady(){if(!ready())throw new IllegalArgumentException(status());if(!idle())throw new IllegalArgumentException("Предыдущее решение сохраняется. Повторите через секунду");}
    private void track(CompletionStage<?> stage){operation=stage;stage.whenComplete((v,error)->{try(var guard=SuiteState.guard()){if(error!=null)failure(error);else stablePacts=eligible(System.currentTimeMillis());}});}
    private void failure(Throwable error){issue="Реестр помощи требует проверки; прежние договоры продолжают действовать";plugin.getLogger().log(java.util.logging.Level.SEVERE,issue,error);}
    private static String actor(CommandSender sender){return sender instanceof Player p?p.getUniqueId().toString():"CONSOLE:"+sender.getName();}
    private void authority(CommandSender sender,Town town){
        if(!diplomacy.manages(sender,town,TreatyType.ALLIANCE)||!sender.hasPermission("neverlandtownydiplomacy.admin")&&(!(sender instanceof Player p)||town.getMayor()==null||!town.getMayor().getUUID().equals(p.getUniqueId())))throw new IllegalArgumentException("Взаимопомощь подписывает мэр своего города или администратор");
    }
    public boolean canManage(CommandSender sender,Town town){try(var guard=SuiteState.guard()){try{authority(sender,town);return true;}catch(RuntimeException unavailable){return false;}}}
    private Treaty alliance(UUID first,UUID second,long now){return diplomacy.repository().all().values().stream().filter(t->t.type()==TreatyType.ALLIANCE&&t.pair(first,second)&&t.active(now)).findFirst().orElseThrow(()->new IllegalArgumentException("Сначала заключите действующий союз между городами"));}
    private boolean validAlliance(AidPact pact,long now){
        var treaty=diplomacy.repository().all().get(pact.alliance());return diplomacy.healthy()&&treaty!=null&&treaty.type()==TreatyType.ALLIANCE&&treaty.pair(pact.first(),pact.second())&&treaty.active(now)&&TownyAPI.getInstance().getTown(pact.first())!=null&&TownyAPI.getInstance().getTown(pact.second())!=null;
    }
    private boolean mayors(AidPact pact){var a=TownyAPI.getInstance().getTown(pact.first());var b=TownyAPI.getInstance().getTown(pact.second());return a!=null&&b!=null&&a.getMayor()!=null&&b.getMayor()!=null&&a.getMayor().getUUID().equals(pact.firstMayor())&&b.getMayor().getUUID().equals(pact.secondMayor());}
    private Set<UUID> eligible(long now){if(!diplomacy.healthy()||gap)return Set.of();var ids=new HashSet<UUID>();for(var p:repository.state().pacts().values())if(p.live(now)&&mayors(p)&&validAlliance(p,now))ids.add(p.id());return Set.copyOf(ids);}
    public CompletionStage<AidPact> offer(CommandSender sender,Town first,Town second,int days,int minutes,int damage){try(var guard=SuiteState.guard()){
        requireReady();authority(sender,first);if(second==null||first.equals(second)||first.getMayor()==null||second.getMayor()==null)throw new IllegalArgumentException("Выберите другой существующий город с мэром");
        if(days<1||days>365||minutes<1||minutes>120||damage<1||damage>100)throw new IllegalArgumentException("Срок 1–365 дней, ответ 1–120 минут, помощь 1–100 единиц урона");
        long now=System.currentTimeMillis();var treaty=alliance(first.getUUID(),second.getUUID(),now);long ends=Math.min(now+days*86_400_000L,treaty.phase()==Treaty.Phase.TERMINATING?treaty.noticeUntil():treaty.expires());
        if(ends-now<120_000)throw new IllegalArgumentException("Союз скоро заканчивается; сначала продлите отношения");
        var before=repository.state();if(before.pacts().values().stream().anyMatch(p->p.party(first.getUUID())&&p.party(second.getUUID())&&p.phase()!=AidPact.Phase.ENDED&&p.endsAt()>now))throw new IllegalArgumentException("Между городами уже есть открытое соглашение помощи");
        if(before.pacts().size()>=2000||before.calls().size()>=2000)throw new IllegalArgumentException("Реестр помощи заполнен; история сохранена, обратитесь к администратору");
        var pact=new AidPact(UUID.randomUUID(),treaty.id(),first.getUUID(),second.getUUID(),first.getMayor().getUUID(),second.getMayor().getUUID(),actor(sender),"",now,Math.min(now+86_400_000,ends-1),ends,minutes*60_000L,damage*1000L,AidPact.Phase.OFFERED,"Политическое обещание без депозита и автоматических санкций");
        var stage=repository.commit(before,before.put(pact,actor(sender),"OFFER",now)).thenCompose(v->validateSigned(sender,first,pact,false));track(stage);return stage;
    }}
    /** ACCEPTING is durable but has no effect. Revalidate current authority and exact alliance before activation. */
    public CompletionStage<AidPact> decide(CommandSender sender,Town town,UUID id,String action){try(var guard=SuiteState.guard()){
        requireReady();authority(sender,town);var before=repository.state();var p=before.pacts().get(id);if(p==null||!p.party(town.getUUID()))throw new IllegalArgumentException("Соглашение своего города не найдено");long now=System.currentTimeMillis();AidPact next;
        if(action.equals("accept")){if(p.phase()!=AidPact.Phase.OFFERED||!p.second().equals(town.getUUID())||now>=p.offerUntil()||!mayors(p)||!validAlliance(p,now))throw new IllegalArgumentException("Предложение устарело; нужны текущие мэры и действующий союз");next=p.phase(AidPact.Phase.ACCEPTING,actor(sender),"Подпись проверяется перед вступлением в силу");}
        else if(action.equals("reject")){if(p.phase()!=AidPact.Phase.OFFERED||!p.second().equals(town.getUUID()))throw new IllegalArgumentException("Отклонить можно входящее предложение");next=p.phase(AidPact.Phase.ENDED,p.accepter(),"Входящее предложение отклонено");}
        else if(action.equals("end")){if(p.phase()==AidPact.Phase.ENDED)throw new IllegalArgumentException("Соглашение уже завершено");next=p.phase(AidPact.Phase.ENDED,p.accepter(),"Соглашение прекращено стороной; вызовы не оцениваются");}
        else throw new IllegalArgumentException("Решение: accept, reject или end");
        var updated=before.put(next,actor(sender),action.toUpperCase(Locale.ROOT),now);if(next.phase()==AidPact.Phase.ENDED)updated=neutralPact(updated,id,next.note());
        var stage=repository.commit(before,updated).thenCompose(v->action.equals("accept")?validateSigned(sender,town,next,true):CompletableFuture.completedFuture(next));track(stage);return stage;
    }}
    private CompletionStage<AidPact> validateSigned(CommandSender sender,Town town,AidPact pact,boolean activate){try(var guard=SuiteState.guard()){
        boolean valid=!stopping&&!SuiteState.frozen(plugin.getName())&&mayors(pact)&&validAlliance(pact,System.currentTimeMillis())&&System.currentTimeMillis()<pact.offerUntil();try{authority(sender,town);}catch(RuntimeException denied){valid=false;}
        if(valid&&!activate)return CompletableFuture.completedFuture(pact);var before=repository.state();var next=pact.phase(valid?AidPact.Phase.FINALIZING:AidPact.Phase.ENDED,pact.accepter(),valid?"Обе подписи сохраняются; соглашение ещё не вступило в силу":"Полномочия, союз или срок изменились до подтверждения записи");
        final boolean validated=valid;return repository.commit(before,before.put(next,actor(sender),valid?"SIGNED":"SIGNATURE_INVALIDATED",System.currentTimeMillis())).thenCompose(v->{
            if(!validated)return CompletableFuture.completedFuture(next); // FINALIZING never has an effect, including after crash. Check again before the definitive ACTIVE decision.
            try(var again=SuiteState.guard()){boolean accepted=!stopping&&settings.enabled()&&configIssue.isEmpty()&&!SuiteState.frozen(plugin.getName())&&validAlliance(next,System.currentTimeMillis())&&mayors(next)&&System.currentTimeMillis()<next.offerUntil();try{authority(sender,town);}catch(RuntimeException denied){accepted=false;}if(accepted){var state=repository.state();var active=next.phase(AidPact.Phase.ACTIVE,next.accepter(),"Обе стороны подписали; помощь подтверждается только событиями сервера");return repository.commit(state,state.put(active,actor(sender),"ACTIVATED",System.currentTimeMillis())).thenApply(v2->active);}var state=repository.state();var ended=next.phase(AidPact.Phase.ENDED,next.accepter(),"Условия изменились при сохранении подписи");return repository.commit(state,neutralPact(state.put(ended,"SYSTEM","SIGNATURE_INVALIDATED",System.currentTimeMillis()),ended.id(),ended.note())).thenApply(v2->ended);}
        });
    }}
    private static AidState neutralPact(AidState state,UUID pact,String note){var calls=new HashMap<>(state.calls());for(var c:state.calls().values())if(c.pact().equals(pact)&&c.status()==AidCall.Status.OPEN)calls.put(c.id(),c.close(AidCall.Status.UNASSESSED,note));return new AidState(state.pacts(),calls,state.history());}
    public void observe(AidFact fact){try(var guard=SuiteState.guard()){if(!ready())return;var active=new HashSet<>(eligible(System.currentTimeMillis()));active.retainAll(stablePacts);if(active.isEmpty())return;if(!pending.offer(new Observation(fact,active)))gap=true;}}
    public AidState visibleState(){try(var guard=SuiteState.guard()){var state=repository.state();return ready()?state:state.neutral(status());}}
    private void tick(){
        if(!idle())return;var before=repository.state();long now=System.currentTimeMillis();AidState next=before;
        if(!ready()){pending.clear();next=before.neutral(status());}
        else {
            var facts=new ArrayList<Observation>();pending.drainTo(facts,64);facts.sort(Comparator.comparingLong((Observation o)->o.fact().at()).thenComparing(o->o.fact().id()));var eligible=eligible(now);
            for(var observation:facts){var approved=new HashSet<>(observation.pacts());approved.retainAll(eligible);next=next.observe(observation.fact(),approved);}
            if(pending.isEmpty())next=next.expire(now,eligible);
            for(var p:next.pacts().values())if(p.phase()==AidPact.Phase.OFFERED&&(now>=p.offerUntil()||!mayors(p))||p.phase()==AidPact.Phase.ACTIVE&&(now>=p.endsAt()||!mayors(p)||!validAlliance(p,now)))next=neutralPact(next.put(p.phase(AidPact.Phase.ENDED,p.accepter(),"Срок, мэр или союз изменились; автоматических последствий нет"),"SYSTEM","CLOSED_NEUTRALLY",now),p.id(),"Соглашение закончилось; помощь не оценивалась");
        }
        if(!next.equals(before)){var captured=next;track(repository.commit(before,next).thenAccept(saved->{if(plugin.isEnabled()&&!stopping)SuiteScheduler.get().runTask(plugin,()->notifyChanges(before,captured));}));}
    }
    private void notifyChanges(AidState before,AidState after){
        for(var c:after.calls().values()){var old=before.calls().get(c.id());if(old!=null&&old.status()==c.status())continue;String text=old==null?"Союзнику "+DiplomacyMenus.name(c.beneficiary())+" нужна помощь против "+DiplomacyMenus.name(c.enemy())+" до "+AidCommands.when(c.deadline())+". Условия: /diplomacy aid":"Взаимопомощь: "+AidCommands.status(c)+". /diplomacy aid";for(UUID id:List.of(c.helper(),c.beneficiary())){var t=TownyAPI.getInstance().getTown(id);if(t!=null)for(var r:t.getResidents()){var p=org.bukkit.Bukkit.getPlayer(r.getUUID());if(p!=null)SuiteScheduler.entity(plugin,p,()->DiplomacyCommand.tell(p,text));}}}
    }
    public CompletionStage<Void> closeAsync(){try(var guard=SuiteState.guard()){
        if(closed!=null)return closed;stopping=true;pending.clear();closed=operation.handle((v,error)->null).thenCompose(v->{try(var state=SuiteState.guard()){if(repository.writable()&&!repository.busy()){var before=repository.state();return repository.commit(before,before.neutral("Наблюдение остановлено администратором или сервером"));}return CompletableFuture.completedFuture(repository.state());}}).thenCompose(v->repository.closeAsync());return closed;
    }}
    public List<ActivityAdmin.Target> adminTargets(){try(var guard=SuiteState.guard()){return visibleState().calls().values().stream().filter(c->c.status()==AidCall.Status.OPEN).sorted(Comparator.comparing(AidCall::id)).map(c->new ActivityAdmin.Target(c.id().toString(),"Помощь "+DiplomacyMenus.name(c.helper())+" → "+DiplomacyMenus.name(c.beneficiary()),Set.of("status","cancel"),(action,minutes)->{if(action.equals("status"))return status()+"; подтверждено "+c.confirmedMilliDamage()/1000.0+" / "+c.requiredMilliDamage()/1000.0;if(!idle()||!repository.writable())throw new IllegalArgumentException("Запись недоступна или занята");var state=repository.state();var live=state.calls().get(c.id());if(live==null||live.status()!=AidCall.Status.OPEN)throw new IllegalArgumentException("Вызов уже изменился");var calls=new HashMap<>(state.calls());calls.put(live.id(),live.close(AidCall.Status.UNASSESSED,"Административная отмена без последствий"));track(repository.commit(state,new AidState(state.pacts(),calls,state.history())));return "Нейтральная отмена сохраняется; обновите меню";})).toList();}}
}
