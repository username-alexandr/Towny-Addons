package ru.neverland.townyarmy.tactics;

import java.util.*;import java.util.concurrent.*;
import org.bukkit.*;import org.bukkit.entity.Player;import org.bukkit.plugin.java.JavaPlugin;
import com.palmergames.bukkit.towny.TownyAPI;
import ru.neverland.core.*;import ru.neverland.townyarmy.*;
import static ru.neverland.townyarmy.tactics.TacticsBook.*;

/** Optional roles/orders/equipment. Core roster remains independent of a damaged feature file. */
public final class TacticsService {
 final JavaPlugin plugin;final ArmyService army;final TacticsRepository repository;final KitDelivery delivery;TacticsSettings settings;
 private final ThreadPoolExecutor audit;private final CompletableFuture<Void> auditClosed=new CompletableFuture<>();
 private CompletionStage<?> maintenance=CompletableFuture.completedFuture(null);private CompletionStage<Void> closing;boolean stopping;private long lastError;
 public TacticsService(JavaPlugin plugin,ArmyService army,TacticsRepository repository,TacticsSettings settings){this.plugin=plugin;this.army=army;this.repository=repository;this.settings=settings;
  audit=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1),r->{var t=new Thread(r,"nlt-army-tactics-audit");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy()){@Override protected void terminated(){auditClosed.complete(null);}};delivery=new KitDelivery(this);
 }
 public State state(){return repository.state();}public boolean available(){return !stopping&&settings.enabled()&&repository.writable()&&army.healthy();}public boolean busy(){return repository.busy()||delivery.busy()||!maintenance.toCompletableFuture().isDone();}public TacticsSettings settings(){return settings;}
 public void reload(TacticsSettings next){try(var guard=SuiteState.guard()){if(busy())throw new IllegalArgumentException("Дождитесь сохранения военного журнала");settings=next;}}
 void gate(){if(!available())throw new IllegalArgumentException("Специальности, приказы и выдача комплектов временно недоступны. Состав и запасы армии сохранены");if(busy())throw new IllegalArgumentException("Военный журнал сохраняет предыдущее изменение. Повторите через секунду");}
 public Role role(UUID town,UUID player){return state().role(town,player);}
 ArmyModel.Soldier participant(Player player)throws Exception{if(!player.hasPermission("neverlandtownyarmy.use"))throw new IllegalArgumentException("Нет доступа к армии");var soldier=army.participant(player);if(!soldier.oath())throw new IllegalArgumentException("Сначала примите присягу");return soldier;}
 UUID commander(Player player,ArmyModel.Rank minimum){if(!player.hasPermission("neverlandtownyarmy.use"))throw new IllegalArgumentException("Нет доступа к армии");return army.commander(player,minimum);}
 CompletionStage<State> commit(State expected,State next){var pending=repository.commit(expected,next);pending.whenComplete((v,error)->{if(error!=null)report(error);});return pending;}
 private CompletionStage<State> save(State next){var pending=commit(state(),next);maintenance=pending;return pending;}
 void report(Throwable error){try(var guard=SuiteState.guard()){long now=System.currentTimeMillis();if(now-lastError>60000){lastError=now;plugin.getLogger().log(java.util.logging.Level.WARNING,"Дополнительная операция армии ожидает подтверждения; её квитанция сохранена",error);}}}
 static Entry entry(UUID town,UUID actor,UUID subject,String action,String reason){return new Entry(UUID.randomUUID(),town,actor,subject,action,reason,System.currentTimeMillis(),false);}
 public CompletionStage<State> assign(Player actor,UUID target,Role role)throws Exception{try(var guard=SuiteState.guard()){
  gate();UUID town=commander(actor,ArmyModel.Rank.CAPTAIN);var soldier=army.repository().state().soldiers().get(target);if(soldier==null||!soldier.town().equals(town)||!soldier.serving()||!soldier.oath())throw new IllegalArgumentException("Выберите бойца своего города после присяги");
  if(!army.executive(actor,town)){var own=army.repository().state().soldiers().get(actor.getUniqueId());if(own==null||own.rank().ordinal()<=soldier.rank().ordinal())throw new IllegalArgumentException("Специальность старшего по званию меняет мэр или министр обороны");}
  var resident=TownyAPI.getInstance().getResident(target);if(resident==null||resident.getTownOrNull()==null||!town.equals(resident.getTownOrNull().getUUID())||!new ArmyBridge().eligible(town,target))throw new IllegalArgumentException("Боец больше не имеет права служить в этом городе");
  if(state().pending(target).isPresent())throw new IllegalArgumentException("Сначала завершите выдачу комплекта этому бойцу");if(state().role(town,target)==role)throw new IllegalArgumentException("Эта специальность уже назначена");var profile=new Profile(town,target,role,actor.getUniqueId(),System.currentTimeMillis());return save(state().role(profile,entry(town,actor.getUniqueId(),target,"ARMY_ROLE",role.title()+": назначение военной специальности")));
 }}
 public CompletionStage<State> createOrder(Player actor,Task task,String unit,String description)throws Exception{try(var guard=SuiteState.guard()){
  gate();UUID town=commander(actor,ArmyModel.Rank.LIEUTENANT);long now=System.currentTimeMillis();if(state().orders().values().stream().filter(o->o.town().equals(town)&&o.phase()==OrderPhase.OPEN&&o.expires()>now).count()>=settings.activeOrders())throw new IllegalArgumentException("Сначала завершите один из действующих приказов");var point=actor.getLocation();var order=new Order(UUID.randomUUID(),town,actor.getUniqueId(),task,unit,new Point(point.getWorld().getUID(),point.getX(),point.getY(),point.getZ()),settings.arrivalRadius(),description,now,Math.addExact(now,settings.orderDuration()),OrderPhase.OPEN,Set.of(),Set.of(),1);return save(state().order(order,0,entry(town,actor.getUniqueId(),actor.getUniqueId(),"ARMY_ORDER","Новый приказ: "+task.title()+"; "+description)));
 }}
 public CompletionStage<State> mark(Player actor,UUID id,long revision,boolean arrived)throws Exception{try(var guard=SuiteState.guard()){
  gate();var soldier=participant(actor);var order=state().orders().get(id);if(order==null||!order.town().equals(soldier.town())||!order.unit().equals("all")&&!order.unit().equals(soldier.unit().id()))throw new IllegalArgumentException("Приказ адресован другому подразделению");if(order.revision()!=revision)throw new IllegalArgumentException("Приказ изменился; откройте его заново");var at=actor.getLocation();if(arrived&&!order.point().contains(at.getWorld().getUID(),at.getX(),at.getY(),at.getZ(),order.radius()))throw new IllegalArgumentException("Для отметки прибытия подойдите к точке приказа");return save(state().order(order.mark(actor.getUniqueId(),arrived,System.currentTimeMillis()),revision,entry(order.town(),actor.getUniqueId(),actor.getUniqueId(),"ARMY_ORDER",arrived?"Боец прибыл к точке приказа":"Боец подтвердил получение приказа")));
 }}
 public CompletionStage<State> closeOrder(Player actor,UUID id,long revision,boolean complete)throws Exception{try(var guard=SuiteState.guard()){
  gate();UUID town=commander(actor,ArmyModel.Rank.LIEUTENANT);var order=state().orders().get(id);if(order==null||!town.equals(order.town()))throw new IllegalArgumentException("Приказ другого города");return save(state().order(order.close(complete?OrderPhase.COMPLETED:OrderPhase.CANCELLED),revision,entry(town,actor.getUniqueId(),actor.getUniqueId(),"ARMY_ORDER",complete?"Командир отметил задачу выполненной":"Командир отменил приказ без штрафов")));
 }}
 public CompletionStage<Kit> requestKit(Player player){return delivery.request(player);}public CompletionStage<Boolean> reconcile(Player player,UUID id){return delivery.reconcile(player,id);}
 public CompletionStage<State> adminResolve(Player actor,UUID id,long expectedPhaseAt,boolean delivered,String reason){try(var guard=SuiteState.guard()){
  gate();if(!actor.hasPermission("neverlandtownyarmy.admin"))throw new IllegalArgumentException("Нет административных прав");reason=ArmyModel.reason(reason);var kit=state().kits().get(id);if(kit==null||kit.phaseAt()!=expectedPhaseAt||kit.phase()!=KitPhase.ARMED&&kit.phase()!=KitPhase.REVIEW)throw new IllegalArgumentException("Квитанция изменилась или уже сверена");
  if(!army.equipmentReceipt(kit.town(),id,kit.player(),kit.cost()).equals("SPENT"))throw new IllegalArgumentException("Не найдена точная квитанция расхода запасов; требуется проверка данных");
  return save(state().kit(id,delivered?KitPhase.COMPLETE:KitPhase.CANCEL_PENDING,"Администратор: "+reason,System.currentTimeMillis(),actor.getUniqueId()));
 }}
 public void pulse(){try(var guard=SuiteState.guard()){
  if(stopping||busy()||!repository.writable())return;
  try{if(delivery.recover())return;long now=System.currentTimeMillis();for(var order:state().orders().values())if(order.phase()==OrderPhase.OPEN&&order.expires()<=now){save(state().order(order.close(OrderPhase.EXPIRED),order.revision(),entry(order.town(),ArmyModel.SYSTEM,ArmyModel.SYSTEM,"ARMY_ORDER","Срок приказа истёк; штрафы не начисляются")));return;}
   var entries=state().history().stream().filter(e->!e.audited()).limit(32).toList();if(entries.isEmpty())return;var ids=new HashSet<UUID>();var rows=new ArrayList<AuditRecord>();for(var e:entries){ids.add(e.id());String kind=e.action().startsWith("ARMY_KIT_")?"ARMY_KIT":e.action();String outcome=e.action().endsWith("_REVIEW")?"REVIEW":e.action().endsWith("_CANCELLED")?"CANCELLED":e.action().endsWith("_COMPLETE")?"DELIVERED":e.action().startsWith("ARMY_KIT_")?"PENDING":"CONFIRMED";var kit=state().kits().get(e.operation());String detail=e.action()+"; "+e.reason();if(kit!=null)detail+="; комплект="+kit.role().title()+"; стоимость (тысячные доли ресурса)="+new TreeMap<>(kit.cost());rows.add(new AuditRecord(AuditRecord.id(plugin.getName(),e.id().toString(),"tactics"),e.at(),plugin.getName(),kind,outcome,e.operation().toString(),e.actor().equals(ArmyModel.SYSTEM)?AuditRecord.Party.system():AuditTrail.player(e.actor()),AuditTrail.town(e.town()),e.subject().equals(ArmyModel.SYSTEM)?AuditRecord.Party.system():AuditTrail.player(e.subject()),"",0,"",detail));}
   maintenance=CompletableFuture.runAsync(()->{for(var row:rows)if(!AuditTrail.record(plugin,row))throw new CompletionException(new java.io.IOException("Журнал армии не подтверждён"));},audit).thenCompose(v->{try(var locked=SuiteState.guard()){return commit(state(),state().acknowledge(ids));}});maintenance.whenComplete((v,error)->{if(error!=null)report(error);});
  }catch(Exception error){report(error);}
 }}
 public CompletionStage<Void> close(){try(var guard=SuiteState.guard()){
  if(closing!=null)return closing;stopping=true;var pending=maintenance;closing=delivery.close().handle((v,error)->error).thenCompose(error->pending.handle((v,other)->error==null?other:error)).thenCompose(error->{audit.shutdown();return auditClosed.thenCompose(v->repository.close()).thenCompose(v->error==null?CompletableFuture.completedFuture(null):CompletableFuture.failedFuture(error));});return closing;
 }}
}
