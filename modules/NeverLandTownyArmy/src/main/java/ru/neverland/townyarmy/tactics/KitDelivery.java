package ru.neverland.townyarmy.tactics;

import java.util.*;import java.util.concurrent.*;
import org.bukkit.*;import org.bukkit.entity.Player;import org.bukkit.inventory.ItemStack;import org.bukkit.inventory.meta.PotionMeta;import org.bukkit.potion.PotionType;
import ru.neverland.core.*;
import static ru.neverland.townyarmy.tactics.TacticsBook.*;

/** One in-flight inventory effect. An uncertain player save is never automatically replayed or refunded. */
final class KitDelivery {
 private record Plan(Player player,Kit kit,ItemStack[] before,ItemStack[] after,PlayerSaveEvidence.Prepared proof){}
 private final TacticsService service;private final PlayerSaveEvidence evidence;private boolean running,stopping,touched;
 private CompletionStage<?> pipeline=CompletableFuture.completedFuture(null);private CompletionStage<Void> closing;
 private final Set<CompletableFuture<?>> scheduled=new HashSet<>();
 private final ScheduledThreadPoolExecutor deadlines=new ScheduledThreadPoolExecutor(1,r->{var t=new Thread(r,"nlt-army-kit-deadline");t.setDaemon(true);return t;});
 private UUID recoveryCursor;private long recoveryAfter;
 KitDelivery(TacticsService service){this.service=service;evidence=new PlayerSaveEvidence(service.plugin);deadlines.setRemoveOnCancelPolicy(true);}
 boolean busy(){return running;}
 private static ItemStack[] copy(ItemStack[] items){return Arrays.stream(items).map(i->i==null?null:i.clone()).toArray(ItemStack[]::new);}
 private List<ItemStack> items(Role role){var result=new ArrayList<ItemStack>();for(var spec:new TreeMap<>(service.settings.loadouts().get(role).items()).entrySet()){
  boolean healing=spec.getKey().equals("SPLASH_POTION_HEALING");Material material=healing?Material.SPLASH_POTION:Material.valueOf(spec.getKey());if(!material.isItem()||material.isAir())throw new IllegalArgumentException("Этот материал нельзя выдать: "+material);
  var prototype=new ItemStack(material);if(healing){var meta=(PotionMeta)prototype.getItemMeta();meta.setBasePotionType(PotionType.HEALING);prototype.setItemMeta(meta);}int left=spec.getValue();while(left>0){var item=prototype.clone();int count=Math.min(left,item.getMaxStackSize());item.setAmount(count);result.add(item);left-=count;if(result.size()>16)throw new IllegalArgumentException("Комплект занимает больше 16 стопок; исправьте конфигурацию");}
 }return List.copyOf(result);}
 private void ready(Player player)throws Exception{
  if(stopping||!service.available()||!service.plugin.isEnabled()||!player.isOnline())throw new IllegalArgumentException("Выдача приостановлена; её квитанция сохранена");if(player.getGameMode()!=GameMode.SURVIVAL&&player.getGameMode()!=GameMode.ADVENTURE)throw new IllegalArgumentException("Комплект выдаётся только в режиме выживания или приключения");if(!player.hasPermission("neverlandtownyarmy.kit")||!player.hasPermission("neverlandtownyarmy.use"))throw new IllegalArgumentException("Нет права получать армейские комплекты");var soldier=service.participant(player);if(!service.army.atBase(soldier.town(),player.getLocation()))throw new IllegalArgumentException("Получить комплект можно на военной базе своего города");
 }
 CompletionStage<Kit> request(Player player){try(var guard=SuiteState.guard()){
  service.gate();ready(player);var soldier=service.participant(player);Role role=service.role(soldier.town(),player.getUniqueId());var before=copy(player.getInventory().getStorageContents());var after=copy(before);var output=items(role);for(var item:output){after=InventoryPlan.insert(after,item);if(after==null)throw new IllegalArgumentException("Освободите место для всего комплекта; запасы пока не списаны");}
  long now=System.currentTimeMillis();var kit=new Kit(UUID.randomUUID(),soldier.town(),soldier.resident(),role,service.settings.loadouts().get(role).cost(),output.stream().map(i->Base64.getEncoder().encodeToString(i.serializeAsBytes())).toList(),now,service.settings.kitCooldown(),service.settings.kitsPerDay(),KitPhase.PLANNED,now,"Получение комплекта: "+role.title());var proof=evidence.prepare(player,kit.id());var plan=new Plan(player,kit,before,after,proof);var state=service.state();var next=state.plan(kit,TacticsService.entry(kit.town(),kit.player(),kit.player(),"ARMY_KIT_PLANNED",kit.reason()));running=true;touched=false;
  var result=new CompletableFuture<Kit>();
  pipeline=service.commit(state,next).thenCompose(v->onPlayer(player,()->{current(plan);return ()->null;})).thenCompose(v->{try(var locked=SuiteState.guard()){return service.army.fundEquipment(kit.town(),kit.id(),kit.player(),kit.cost());}catch(Exception e){return CompletableFuture.failedFuture(e);}}).thenCompose(funded->{if(!funded)return CompletableFuture.failedFuture(new IllegalArgumentException("В военных запасах не хватает ресурсов для комплекта"));return phase(kit.id(),KitPhase.FUNDED,"Военные запасы выделены");}).thenCompose(v->onPlayer(player,()->{current(plan);return ()->null;})).thenCompose(v->phase(kit.id(),KitPhase.ARMED,"Выдача подготовлена; повторять без сверки нельзя")).thenCompose(v->onPlayer(player,()->{
    current(plan);touched=true;player.getInventory().setStorageContents(copy(plan.after()));return ()->evidence.saveAndVerify(player,plan.proof());
  })).thenCompose(v->v).thenCompose(saved->{if(!Boolean.TRUE.equals(saved))return CompletableFuture.failedFuture(new IllegalStateException("Сохранение инвентаря не подтверждено"));return phase(kit.id(),KitPhase.COMPLETE,"Комплект выдан, сохранение игрока подтверждено");}).handle((saved,error)->{if(error==null)return CompletableFuture.completedFuture(saved);return failed(kit,error);}).thenCompose(v->v).whenComplete((saved,error)->{try(var locked=SuiteState.guard()){running=false;if(error!=null)result.completeExceptionally(error);else{var complete=service.state().kits().get(kit.id());if(complete!=null&&complete.phase()==KitPhase.COMPLETE)result.complete(complete);else result.completeExceptionally(new IllegalArgumentException(complete==null?"Выдача не подтверждена":complete.reason()));}}});
  return result;
 }catch(Exception error){return CompletableFuture.failedFuture(error);}}
 private void current(Plan plan)throws Exception{ready(plan.player());var soldier=service.participant(plan.player());if(!soldier.town().equals(plan.kit().town())||service.role(soldier.town(),soldier.resident())!=plan.kit().role()||!Arrays.equals(plan.before(),plan.player().getInventory().getStorageContents()))throw new IllegalArgumentException("Город, специальность или инвентарь изменились до выдачи");}
 private CompletionStage<State> phase(UUID id,KitPhase next,String reason){try(var guard=SuiteState.guard()){var state=service.state();return service.commit(state,state.kit(id,next,reason,System.currentTimeMillis(),state.kits().get(id).player()));}}
 private CompletionStage<State> failed(Kit kit,Throwable error){try(var guard=SuiteState.guard()){
  var known=service.state().kits().get(kit.id());if(known==null||!service.repository.writable())return CompletableFuture.failedFuture(error);if(touched){service.report(error);return phase(kit.id(),KitPhase.REVIEW,"Сохранение выдачи неизвестно. Повтор запрещён до сверки");}
  var cancelled=phase(kit.id(),KitPhase.CANCEL_PENDING,"Выдача не начиналась; требуется точный возврат военных запасов");return cancelled.thenCompose(v->refund(kit.id()));
 }}
 private CompletionStage<State> refund(UUID id){try(var guard=SuiteState.guard()){
  var kit=service.state().kits().get(id);if(kit==null||kit.phase()!=KitPhase.CANCEL_PENDING)throw new IllegalStateException("Не подтверждена отмена комплекта");
  // Never interpret unavailable core storage as absence of a debit.
  service.army.equipmentReceipt(kit.town(),id,kit.player(),kit.cost());return service.army.returnEquipment(kit.town(),id,kit.player(),kit.cost()).thenCompose(v->phase(id,KitPhase.CANCELLED,"Выдача отменена; военные запасы возвращены полностью"));
 }catch(Exception error){return CompletableFuture.failedFuture(error);}}
 @FunctionalInterface private interface Action<T>{T run()throws Exception;}
 /** Prepare under a short nonblocking ledger scope, then save native player data without that scope. */
 private <T>CompletionStage<T> onPlayer(Player player,Action<Action<T>> prepare){try(var guard=SuiteState.guard()){
  var result=new CompletableFuture<T>();if(stopping||!service.plugin.isEnabled()){result.completeExceptionally(new IllegalStateException("Выдача приостановлена до перезапуска"));return result;}scheduled.add(result);
  var deadline=deadlines.schedule(()->{try(var locked=SuiteState.guard()){if(scheduled.contains(result))result.completeExceptionally(new IllegalStateException("Игрок не ответил на выдачу вовремя; проверьте квитанцию"));}},10,TimeUnit.SECONDS);
  result.whenComplete((v,e)->{try(var locked=SuiteState.guard()){scheduled.remove(result);deadline.cancel(false);}});
  dispatch(player,result,prepare,deadline);return result;
 }}
 private <T>void dispatch(Player player,CompletableFuture<T> result,Action<Action<T>> prepare,ScheduledFuture<?> deadline){
  try{var task=player.getScheduler().runDelayed(service.plugin,ignored->{
   var effect=new java.util.concurrent.atomic.AtomicReference<Action<T>>();
   if(!SuiteState.tryRun("Army kit owner admission",()->{
    if(result.isDone())return;
    try{if(stopping||!service.plugin.isEnabled()||SuiteState.frozen(service.plugin.getName()))throw new IllegalStateException("Выдача приостановлена до изменения инвентаря");effect.set(prepare.run());scheduled.remove(result);deadline.cancel(false);}catch(Throwable error){result.completeExceptionally(error);}
   })){dispatch(player,result,prepare,deadline);return;}
   if(effect.get()!=null)try{if(SuiteState.held())throw new IllegalStateException("Сохранение игрока не должно удерживать общий журнал");result.complete(effect.get().run());}catch(Throwable error){result.completeExceptionally(error);}
  },()->result.completeExceptionally(new IllegalArgumentException("Игрок вышел до выдачи комплекта")),1);
  if(task==null)result.completeExceptionally(new IllegalArgumentException("Поток игрока недоступен"));
  }catch(Throwable error){result.completeExceptionally(error);}
 }
 boolean recover(){if(stopping||running||System.currentTimeMillis()<recoveryAfter||service.army.busy()||!service.army.healthy())return false;var candidates=new ArrayList<>(new TreeMap<>(service.state().kits()).values());if(recoveryCursor!=null){int offset=0;while(offset<candidates.size()&&candidates.get(offset).id().compareTo(recoveryCursor)<=0)offset++;Collections.rotate(candidates,-offset);}for(var kit:candidates){
  if(kit.terminal()||kit.phase()==KitPhase.REVIEW)continue;recoveryCursor=kit.id();running=true;touched=false;
  CompletionStage<?> work;if(kit.phase()==KitPhase.ARMED)work=phase(kit.id(),KitPhase.REVIEW,"Перезапуск во время выдачи: нужна проверка сохранения игрока");else if(kit.phase()==KitPhase.CANCEL_PENDING)work=refund(kit.id());else work=phase(kit.id(),KitPhase.CANCEL_PENDING,"Выдача не начиналась до остановки; возврат без штрафа").thenCompose(v->refund(kit.id()));
  pipeline=work.whenComplete((v,error)->{try(var guard=SuiteState.guard()){running=false;if(error!=null){recoveryAfter=System.currentTimeMillis()+5000;service.report(error);}}});return true;
 }return false;}
 CompletionStage<Boolean> reconcile(Player player,UUID id){try(var guard=SuiteState.guard()){
  service.gate();var kit=service.state().kits().get(id);if(kit==null||!kit.player().equals(player.getUniqueId()))throw new IllegalArgumentException("Квитанция принадлежит другому бойцу");if(kit.phase()==KitPhase.COMPLETE)return CompletableFuture.completedFuture(true);if(kit.phase()!=KitPhase.ARMED&&kit.phase()!=KitPhase.REVIEW)throw new IllegalArgumentException("Эта выдача не требует проверки инвентаря");var proof=evidence.prepare(player,id);running=true;
  var result=evidence.verify(proof).thenCompose(saved->{if(!Boolean.TRUE.equals(saved))return CompletableFuture.completedFuture(false);return phase(id,KitPhase.COMPLETE,"При сверке найдена точная квитанция сохранения игрока").thenApply(v->true);});pipeline=result.whenComplete((v,error)->{try(var locked=SuiteState.guard()){running=false;if(error!=null)service.report(error);}});return result;
 }catch(Exception error){return CompletableFuture.failedFuture(error);}}
 CompletionStage<Void> close(){try(var guard=SuiteState.guard()){
  if(closing!=null)return closing;stopping=true;deadlines.shutdownNow();for(var waiting:List.copyOf(scheduled))waiting.completeExceptionally(new IllegalStateException("Выдача остановлена до изменения инвентаря"));closing=pipeline.handle((v,error)->null).thenCompose(v->evidence.prepareClose());return closing;
 }}
}
