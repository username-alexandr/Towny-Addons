package ru.neverland.mintexpeditions.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.core.EffectJournal;
import ru.neverland.core.AuditTrail;
import ru.neverland.mintexpeditions.journey.*;
import java.util.concurrent.*;
import ru.neverland.core.InventoryPlan;
import ru.neverland.core.PlayerSaveReceipt;
import ru.neverland.mintexpeditions.integration.BuildBridge;
import ru.neverland.mintexpeditions.integration.CampFacade;
import ru.neverland.mintexpeditions.integration.TownyHook;
import ru.neverland.mintexpeditions.integration.CampFacade.CampView;
import ru.neverland.mintexpeditions.model.ActiveExpedition;
import ru.neverland.mintexpeditions.model.BlockPos;
import ru.neverland.mintexpeditions.model.ExpeditionDefinition;
import ru.neverland.mintexpeditions.model.ExpeditionHistory;
import ru.neverland.mintexpeditions.model.ExpeditionStatus;
import ru.neverland.mintexpeditions.model.ItemAmount;
import ru.neverland.mintexpeditions.service.ReturnTickets.Ticket;
import ru.neverland.mintexpeditions.util.ColorUtil;
import ru.neverland.mintexpeditions.util.TimeUtil;

public final class ExpeditionService {
   private final JavaPlugin plugin;
   private final MessageService messages;
   private final ExpeditionRegistry registry;
   private final ExpeditionRepository repository;
   private final CampFacade camps;
   private final SiteService sites;
   private final TownyHook towny;
   private final BuildBridge builds;
   private final ReturnTickets returnTickets;
   private final Map<UUID, Long> shieldWarnings = new HashMap<>();
   private final Set<UUID> returning = new HashSet<>();
   private final Set<UUID> preparing = new HashSet<>();
   private final Map<UUID, BossBar> bars = new HashMap<>();
   private final RewardDelivery rewardDelivery;
   private final JourneyRepository journeys;
   private final Set<UUID> journeyWork=new HashSet<>();
   private final Set<UUID> journeyIssues=new HashSet<>();
   private final Set<CompletionStage<?>> transitions=new HashSet<>();
   private <T>CompletionStage<T> track(CompletionStage<T> stage){transitions.add(stage);stage.whenComplete((v,e)->{try(var guard=ru.neverland.core.SuiteState.guard()){transitions.remove(stage);}});return stage;}
   private record Participation(boolean survival,long at){}
   private final Map<UUID,Participation> participation=new HashMap<>();
   private boolean stopping;
   private BukkitTask task;
   private BukkitTask shieldTask;

   public ExpeditionService(
      JavaPlugin plugin,
      MessageService messages,
      ExpeditionRegistry registry,
      ExpeditionRepository repository,
      CampFacade camps,
      SiteService sites,
      TownyHook towny,
      BuildBridge builds
   ) {
      this.plugin = plugin;
      this.messages = messages;
      this.registry = registry;
      this.repository = repository;
      this.camps = camps;
      this.sites = sites;
      this.towny = towny;
      this.builds = builds;
      this.returnTickets = new ReturnTickets(plugin);
      this.rewardDelivery=new RewardDelivery(plugin,repository,towny);
      this.journeys=new JourneyRepository(plugin.getDataFolder().toPath().resolve("journeys.yml"),receipt->AuditTrail.record(plugin,receipt));
   }

   public void startTasks() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
      this.stopTasks();
      long period = Math.max(20L, this.plugin.getConfig().getLong("expeditions.expiry-check-seconds", 10L) * 20L);
      this.task = ru.neverland.core.SuiteScheduler.get().runTaskTimer(this.plugin, this::tick, period, period);
      this.shieldTask = ru.neverland.core.SuiteScheduler.get().runTaskTimer(this.plugin, () -> {
         ru.neverland.core.SuiteScheduler.players(this.plugin,player -> {
            observePlayer(player);
            if (player.getActiveItem().getType() == Material.SHIELD && this.shieldDenied(player)) {
               player.clearActiveItem();
               this.warnShield(player);
            }
         });
      }, 1L, 1L);

      for (ActiveExpedition expedition : List.copyOf(this.repository.active())) {
         var journey=journeys.get(expedition.id());if(expedition.planned()&&expedition.status()==ExpeditionStatus.ACTIVE&&journeys.ready()&&journey!=null&&journey.state()==Journey.State.CLOSED){finishCommitted(expedition,ExpeditionStatus.valueOf(journey.note()));continue;}
         if (expedition.status() == ExpeditionStatus.ACTIVE) {
            this.bar(expedition);
         } else {
            this.sites.restore(expedition, () -> {
               this.repository.remove(expedition);
               this.repository.saveAsync();
            });
         }
      }
   } }

   public void stopTasks() { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
      if (this.task != null) {
         this.task.cancel();
      }

      if (this.shieldTask != null) {
         this.shieldTask.cancel();
      }

      this.task = this.shieldTask = null;

      for (BossBar bar : this.bars.values()) {
         for(Player player:List.copyOf(bar.getPlayers()))ru.neverland.core.SuiteScheduler.cleanupPlayer(plugin,player,p->bar.removePlayer(p));
      }

      this.bars.clear();
      this.shieldWarnings.clear();
      this.participation.clear();
   } }

   public ExpeditionService.StartResult start(Player leader, ExpeditionDefinition definition){return start(leader,definition,null);}
   public ExpeditionService.StartResult start(Player leader, ExpeditionDefinition definition,Journey.Branch branch) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
      if(stopping||!repository.writable()||!leader.hasPermission("mintexpeditions.start"))return StartResult.UNAVAILABLE;
      if(branch!=null&&(!plugin.getConfig().getBoolean("journeys.enabled",true)||!journeys.ready()||journeys.busy()))return StartResult.UNAVAILABLE;
      CampView camp = this.camps.owned(leader);
      if (camp == null) {
         return ExpeditionService.StartResult.NO_CAMP;
      } else if (this.plugin.getConfig().getBoolean("camps.require-active-fire", true) && camp.burnUntil() <= System.currentTimeMillis()) {
         return ExpeditionService.StartResult.INACTIVE;
      } else {
         double radius = 6 + (camp.level() - 1) * 3 + this.plugin.getConfig().getDouble("camps.start-radius-extra", 2.0);
         if (camp.world() == null || !leader.getWorld().equals(camp.world()) || leader.getLocation().distanceSquared(camp.anchor()) > radius * radius) {
            return ExpeditionService.StartResult.TOO_FAR;
         } else if (this.plugin.getConfig().getBoolean("camps.block-start-while-stash-open", true) && this.camps.stashOpen(camp.owner())) {
            return ExpeditionService.StartResult.STASH_OPEN;
         } else if (camp.level() < definition.minCampLevel()) {
            return ExpeditionService.StartResult.LOW_LEVEL;
         } else if (this.repository.byCamp(camp.owner()) != null || this.preparing.contains(camp.owner())) {
            return ExpeditionService.StartResult.ACTIVE;
         } else if (this.plugin.getConfig().getStringList("generation.blocked-worlds").contains(leader.getWorld().getName())) {
            return ExpeditionService.StartResult.WORLD_BLOCKED;
         } else {
            String missing = this.camps.missing(camp, definition.costs());
            if (!missing.isEmpty()) {
               this.messages.send(leader, "not-enough-supplies", Map.of("supplies", missing));
               return ExpeditionService.StartResult.MISSING;
            } else {
               UUID townId = this.towny.townId(leader);
               List<Player> party=this.camps.party(camp);
               this.preparing.add(camp.owner());
               this.messages.send(leader, "preparing");
               this.sites
                  .findSite(
                     camp.world(),
                     camp.anchor(),
                     definition,
                     site -> {
                        if (site == null) {
                           this.preparing.remove(camp.owner());
                           ru.neverland.core.SuiteScheduler.entity(plugin,leader,()->this.messages.send(leader,"site-not-found"));
                        } else {
                           long now = System.currentTimeMillis();
                           long durationMillis = Math.max(
                              1000L, Math.round(definition.durationSeconds() * 1000L * this.builds.expeditionTimeMultiplier(townId))
                           );
                           ActiveExpedition expedition = new ActiveExpedition(
                              UUID.randomUUID(),
                              camp.owner(),
                              definition.id(),
                              camp.world().getUID(),
                              camp.world().getName(),
                              BlockPos.of(camp.anchor()),
                              site,
                              now,
                              now + durationMillis
                           );
                           party.forEach(playerx->expedition.participants().add(playerx.getUniqueId()));expedition.planned(branch!=null);
                           CompletionStage<?> accepted=CompletableFuture.completedFuture(null);
                           if(branch!=null){var journey=new Journey(expedition.id(),definition,branch,expedition.participants(),Map.of(),Map.of(),Journey.State.ACTIVE,"");accepted=journeys.commit(null,journey,leader.getUniqueId(),"created","Порядок: "+branch.title()+"; цели "+definition.goal()+"; враги "+definition.mobCount()+"; личная награда "+definition.moneyPerParticipant()+"; нужен хотя бы один подтверждённый вклад");}
                           accepted.thenCompose(v->{var result=new CompletableFuture<Boolean>();ru.neverland.core.WorldTasks.loaded(plugin,site.location(camp.world()),()->sites.buildAsync(expedition,definition).whenComplete((value,failure)->{if(failure==null)result.complete(value);else result.completeExceptionally(failure);}));return result;}).whenComplete((built,error)->{
                              if(error!=null){try(var guard=ru.neverland.core.SuiteState.guard()){preparing.remove(camp.owner());if(!expedition.timer().paused())expedition.timer(expedition.timer().edit("pause",0,System.currentTimeMillis()));}plugin.getLogger().log(Level.SEVERE,"Подготовка экспедиции требует сверки; припасы повторно не снимаются",error);return;}
                              if(!plugin.isEnabled()||stopping)return;
                              if(!built){ru.neverland.core.SuiteScheduler.get().runTask(plugin,()->{preparing.remove(camp.owner());repository.remove(expedition);repository.saveAsync();});ru.neverland.core.SuiteScheduler.entity(plugin,leader,()->messages.send(leader,"start-failed"));return;}
                              ru.neverland.core.SuiteScheduler.entity(plugin,leader,()->{
                                 var current=camps.owned(leader);boolean valid=leader.isOnline()&&leader.hasPermission("mintexpeditions.start")&&current!=null&&current.owner().equals(camp.owner())&&current.world()!=null&&current.anchor().equals(camp.anchor())&&current.level()>=definition.minCampLevel()&&!camps.stashOpen(camp.owner())&&(!plugin.getConfig().getBoolean("camps.require-active-fire",true)||current.burnUntil()>System.currentTimeMillis())&&leader.getWorld().equals(current.world())&&leader.getLocation().distanceSquared(current.anchor())<=radius*radius;
                                 boolean consumed=false;try{if(valid)consumed=camps.consumeChecked(current,definition.costs());}catch(java.io.IOException uncertain){preparing.remove(camp.owner());expedition.siteReady(false,"Списание припасов не подтверждено; нужна ручная сверка");if(!expedition.timer().paused())expedition.timer(expedition.timer().edit("pause",0,System.currentTimeMillis()));repository.changed();repository.saveAsync();leader.sendMessage("Запуск остановлен для проверки припасов. Повторного списания не будет.");plugin.getLogger().log(Level.SEVERE,"Неизвестный результат списания припасов: "+expedition.id(),uncertain);return;}
                                 if(!consumed){preparing.remove(camp.owner());sites.restore(expedition,()->{repository.remove(expedition);repository.saveAsync();});messages.send(leader,"start-failed");return;}
                                 expedition.siteReady(true,"");repository.changed();repository.saveAsync().whenComplete((saved,failure)->{
                                    if(failure!=null){try(var guard=ru.neverland.core.SuiteState.guard()){expedition.siteReady(false,"Припасы списаны; запуск требует сверки");if(!expedition.timer().paused())expedition.timer(expedition.timer().edit("pause",0,System.currentTimeMillis()));}plugin.getLogger().log(Level.SEVERE,"Запуск экспедиции не подтверждён после припасов",failure);return;}
                                    if(!plugin.isEnabled()||stopping)return;ru.neverland.core.SuiteScheduler.get().runTask(plugin,()->{preparing.remove(camp.owner());bar(expedition);});
                                    boolean teleport=plugin.getConfig().getBoolean("expeditions.teleport-party-to-site",false);Location destination=expedition.siteLocation();
                                    for(Player player:party)ru.neverland.core.SuiteScheduler.entity(plugin,player,()->{messages.send(player,player.getUniqueId().equals(camp.owner())?"started":"party-member",Map.of("expedition",ColorUtil.strip(definition.name()),"party",party.size()));sendTarget(player,expedition);if(teleport&&destination!=null)player.teleportAsync(destination);});
                                 });
                              });
                           });
                        }
                     }
                  );
               return ExpeditionService.StartResult.QUEUED;
            }
         }
      }
   } }

   public boolean objective(Player player, BlockPos position) { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {
      ActiveExpedition expedition = this.at(position, player.getWorld());
      if (expedition == null || preparing.contains(expedition.leaderId()) || !expedition.siteReady() || expedition.timer().paused() || !expedition.objectives().contains(position) || expedition.completed().contains(position)) {
         return false;
      } else if (!expedition.participants().contains(player.getUniqueId())) {
         this.messages.send(player, "not-participant");
         return true;
      } else {
         if(expedition.planned())return journeyObjective(player,expedition,position);
         expedition.completed().add(position);
         position.location(player.getWorld()).getBlock().setType(Material.AIR, false);
         this.repository.changed();
         ExpeditionDefinition definition = this.definition(expedition);
         this.broadcast(expedition, "objective", Map.of("progress", expedition.objectiveProgress(), "goal", definition.goal()));
         this.updateBar(expedition);
         this.check(expedition, definition);
         return true;
      }
   } }

   public void mobDied(LivingEntity entity) { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {
      ActiveExpedition expedition = this.sites.expeditionForMob(entity);
      if (expedition != null && !preparing.contains(expedition.leaderId()) && expedition.siteReady() && !expedition.timer().paused() && expedition.status() == ExpeditionStatus.ACTIVE) {
         if(expedition.planned()){journeyKill(entity,expedition);return;}
         if(!expedition.spawnedMobs().remove(entity.getUniqueId()))return;
         ExpeditionDefinition definition = this.definition(expedition);
         if (definition != null) {
            Player killer = entity.getKiller();
            if (killer != null && expedition.participants().contains(killer.getUniqueId())) {
               expedition.kills(expedition.kills() + 1);
               this.repository.changed();
               this.broadcast(expedition, "mob-progress", Map.of("progress", expedition.kills(), "goal", definition.mobCount()));
               this.updateBar(expedition);
               this.check(expedition, definition);
            } else {
               this.repository.changed();
               if (this.plugin.getConfig().getBoolean("combat.respawn-uncredited-deaths", true)) {
                  ru.neverland.core.SuiteScheduler.get().runTask(this.plugin, () -> {
                     if (expedition.status() == ExpeditionStatus.ACTIVE && this.repository.get(expedition.id()) != null) {
                        this.sites.respawn(expedition, definition, 1);
                     }
                  });
               }
            }
         }
      }
   } }

   public ActiveExpedition expeditionForMob(Entity entity) { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {
      return this.sites.expeditionForMob(entity);
   } }

   public boolean insideMobArea(ActiveExpedition expedition, Location location) { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {
      return this.sites.insideMobArea(expedition, location);
   } }

   public boolean preventMobTeleportOutside() { try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {
      return this.plugin.getConfig().getBoolean("combat.prevent-teleport-outside", true);
   } }

   public boolean protectMobFromEnvironment() { try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) {
      return this.plugin.getConfig().getBoolean("combat.protect-from-fall-and-suffocation", true);
   } }

   public boolean relocateMob(Entity entity) { try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) {
      return this.sites.relocateMob(entity);
   } }

   public void relocateMobNextTick(Entity entity) { try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) {
      UUID entityId = entity.getUniqueId();
      ru.neverland.core.SuiteScheduler.entity(this.plugin, entity, () -> {
         Entity current = ru.neverland.core.WorldTasks.find(entityId);
         if (current != null && current.isValid() && !current.isDead()) {
            this.sites.relocateMob(current);
         }
      });
   } }

   public ExpeditionDefinition definition(ActiveExpedition expedition){
      if(!expedition.planned())return registry.get(expedition.definitionId());
      var journey=journeys.get(expedition.id());return journey==null?null:journey.frozen();
   }
   public Journey journey(ActiveExpedition expedition){return expedition==null?null:journeys.get(expedition.id());}
   public ActiveExpedition participantExpedition(UUID player){return repository.active().stream().filter(e->e.status()==ExpeditionStatus.ACTIVE&&e.participants().contains(player)).findFirst().orElse(null);}
   public boolean journeyUnavailable(ActiveExpedition expedition){var j=journeys.get(expedition.id());return expedition.planned()&&(!plugin.getConfig().getBoolean("journeys.enabled",true)||!journeys.ready()||journeys.busy()||j==null||j.state()!=Journey.State.ACTIVE||journeyWork.contains(expedition.id())||journeyIssues.contains(expedition.id()));}
   public boolean pausedMob(ActiveExpedition expedition){var j=journeys.get(expedition.id());return !expedition.siteReady()||preparing.contains(expedition.leaderId())||expedition.timer().paused()||expedition.planned()&&(journeyUnavailable(expedition)||j.stage()!=Journey.Action.KILL);}
   public String journeyStatus(ActiveExpedition expedition){if(expedition==null||!expedition.planned())return "Обычная экспедиция: цели и враги доступны одновременно";if(!plugin.getConfig().getBoolean("journeys.enabled",true))return "Поэтапные походы приостановлены администратором без провала.";if(journeyIssues.contains(expedition.id()))return "Поход приостановлен: условия и подтверждения требуют сверки администратора.";if(!journeys.ready())return "Поход приостановлен: журнал требует проверки. Прогресс сохранён, провала нет.";if(journeys.busy()||journeyWork.contains(expedition.id()))return "Подтверждённое действие сохраняется. Подождите секунду.";var j=journeys.get(expedition.id());return j==null?"Нет сохранённых условий похода; нужна проверка администратора":j.stageText();}
   public CompletionStage<Void> role(Player player,Journey.Role role){try(var guard=ru.neverland.core.SuiteState.guard()){
      var expedition=participantExpedition(player.getUniqueId());if(expedition==null||!expedition.planned()||journeyUnavailable(expedition)||!player.hasPermission("mintexpeditions.use"))return CompletableFuture.failedFuture(new IllegalArgumentException("Выбор роли сейчас недоступен"));
      var before=journeys.get(expedition.id());return journeys.commit(before,before.role(player.getUniqueId(),role),player.getUniqueId(),"role:"+UUID.randomUUID(),"Добровольная роль: "+(role==null?"без роли":role.title())).thenApply(v->null);
   }}
   private boolean journeyObjective(Player player,ActiveExpedition expedition,BlockPos position){
      if(journeyUnavailable(expedition)){player.sendMessage(journeyStatus(expedition));return true;}
      var before=journeys.get(expedition.id());if(before.stage()!=Journey.Action.OBJECTIVE){player.sendMessage("Эта цель пока недоступна. "+before.stageText());return true;}
      if(player.getGameMode()!=GameMode.SURVIVAL&&player.getGameMode()!=GameMode.ADVENTURE){player.sendMessage("Вклад в поход засчитывается в режиме выживания.");return true;}
      if(position.location(player.getWorld()).getBlock().getType()!=before.frozen().objectiveMaterial())return true;
      String key="o:"+position.key();applyContribution(expedition,before,before.credit(key,Journey.Action.OBJECTIVE,player.getUniqueId(),System.currentTimeMillis()),player.getUniqueId(),key,position);return true;
   }
   public void observePlayer(Player player){if(!ru.neverland.core.SuiteScheduler.owns(player))return;var expedition=participantExpedition(player.getUniqueId());if(expedition!=null&&expedition.planned())participation.put(player.getUniqueId(),new Participation(player.getGameMode()==GameMode.SURVIVAL||player.getGameMode()==GameMode.ADVENTURE,System.currentTimeMillis()));else participation.remove(player.getUniqueId());}
   private boolean survivalParticipant(UUID player){var known=participation.get(player);return known!=null&&known.survival()&&System.currentTimeMillis()-known.at()<3000;}
   private void journeyKill(LivingEntity mob,ActiveExpedition expedition){
      if(!expedition.spawnedMobs().contains(mob.getUniqueId()))return;
      var before=journeys.get(expedition.id());Player killer=mob.getKiller();
      if(journeyUnavailable(expedition)||before.stage()!=Journey.Action.KILL||killer==null||!expedition.participants().contains(killer.getUniqueId())||!survivalParticipant(killer.getUniqueId())){expedition.spawnedMobs().remove(mob.getUniqueId());repository.changed();return;}
      // Killer identity is captured from the server's death attribution; no remote inventory/location reads.
      String key="m:"+mob.getUniqueId();if(before.contributions().containsKey(key))return;
      applyContribution(expedition,before,before.credit(key,Journey.Action.KILL,killer.getUniqueId(),System.currentTimeMillis()),killer.getUniqueId(),key,null);
   }
   private void applyContribution(ActiveExpedition expedition,Journey before,Journey after,UUID actor,String key,BlockPos objective){
      journeyWork.add(expedition.id());
      track(journeys.commit(before,after,actor,"contribution:"+key,"Вклад "+key+"; роль "+before.roles().get(actor)+"; "+after.objectives()+" целей, "+after.kills()+" врагов").thenCompose(saved->{try(var guard=ru.neverland.core.SuiteState.guard()){
         reconcile(expedition,saved);repository.changed();return repository.saveAsync();
      }})).whenComplete((saved,error)->{
         if(error!=null){try(var guard=ru.neverland.core.SuiteState.guard()){journeyWork.remove(expedition.id());if(!expedition.timer().paused())expedition.timer(expedition.timer().edit("pause",0,System.currentTimeMillis()));}plugin.getLogger().log(Level.SEVERE,"Вклад похода требует сверки; награды не выдаются",error);return;}
         if(!plugin.isEnabled()||stopping)return;
         Runnable confirmed=()->{journeyWork.remove(expedition.id());updateBar(expedition);for(UUID id:expedition.participants()){var player=Bukkit.getPlayer(id);if(player!=null)ru.neverland.core.SuiteScheduler.entity(plugin,player,()->player.sendMessage(after.stageText()));}check(expedition,after.frozen());};
         if(objective!=null)ru.neverland.core.WorldTasks.loaded(plugin,objective.location(expedition.world()),()->{var block=objective.location(expedition.world()).getBlock();if(block.getType()==after.frozen().objectiveMaterial())block.setType(Material.AIR,false);confirmed.run();});
         else ru.neverland.core.SuiteScheduler.get().runTask(plugin,confirmed);
      });
   }
   private void reconcile(ActiveExpedition expedition,Journey journey){
      if(!expedition.participants().equals(journey.participants()))throw new IllegalArgumentException("Состав похода не совпадает с сохранённым согласием");
      expedition.kills(journey.kills());expedition.completed().clear();
      for(var c:journey.contributions().values())if(c.action()==Journey.Action.OBJECTIVE){var position=BlockPos.parse(c.key().substring(2));if(!expedition.objectives().contains(position))throw new IllegalArgumentException("Квитанция не принадлежит целям похода");expedition.completed().add(position);}else expedition.spawnedMobs().remove(UUID.fromString(c.key().substring(2)));
   }

   private void check(ActiveExpedition expedition, ExpeditionDefinition definition) {
      if (definition!=null&&(!expedition.planned()||!journeyUnavailable(expedition))&&expedition.objectiveProgress() >= definition.goal() && expedition.kills() >= definition.mobCount()) {
         this.finish(expedition, ExpeditionStatus.COMPLETED);
      }
   }

   public void finish(ActiveExpedition expedition, ExpeditionStatus status){try(var guard=ru.neverland.core.SuiteState.guard()){
      if(!expedition.planned()){finishCommitted(expedition,status);return;}
      if(expedition.status()!=ExpeditionStatus.ACTIVE)return;
      var journey=journeys.get(expedition.id());
      if(status==ExpeditionStatus.COMPLETED&&(journeyUnavailable(expedition)||journey==null||!journey.complete()))throw new IllegalStateException("Поход ещё не завершён либо журнал требует проверки; доступна нейтральная отмена");
      if(!journeys.ready()){if(status==ExpeditionStatus.CANCELLED){finishCommitted(expedition,status);return;}throw new IllegalStateException("Поход приостановлен без провала: журнал требует проверки");}
      if(journeys.busy()||journeyWork.contains(expedition.id()))throw new IllegalStateException("Дождитесь сохранения предыдущего шага");
      if(journey==null){if(status==ExpeditionStatus.CANCELLED){finishCommitted(expedition,status);return;}throw new IllegalStateException("Условия похода отсутствуют");}
      journeyWork.add(expedition.id());track(journeys.commit(journey,journey.state(Journey.State.CLOSED,status.name()),null,"closed:"+status,"Итог похода: "+status).thenCompose(saved->finishCommitted(expedition,status))).whenComplete((saved,error)->{try(var state=ru.neverland.core.SuiteState.guard()){journeyWork.remove(expedition.id());if(error!=null){if(!expedition.timer().paused())expedition.timer(expedition.timer().edit("pause",0,System.currentTimeMillis()));plugin.getLogger().log(Level.SEVERE,"Итог похода не подтверждён; провал и награда не применены",error);}}});
   }}
   private CompletionStage<Void> finishCommitted(ActiveExpedition expedition, ExpeditionStatus status) { try (var suiteGuard12 = ru.neverland.core.SuiteState.guard()) {
      if (!this.repository.writable()) {
         throw new IllegalStateException("Хранилище экспедиций недоступно");
      } else if (expedition.status() == ExpeditionStatus.ACTIVE) {
         ExpeditionDefinition definition = this.definition(expedition);
         if(expedition.planned()&&status==ExpeditionStatus.COMPLETED){var journey=journeys.get(expedition.id());if(journey==null||!journey.complete())throw new IllegalStateException("Поход ещё не завершён либо его журнал требует проверки; используйте нейтральную отмену");}
         ArrayList<RewardBatch> batches = new ArrayList<>();
         if (status == ExpeditionStatus.COMPLETED) {
            if (definition == null) {
               throw new IllegalStateException("Определение экспедиции удалено; сначала восстановите настройки награды");
            }

            for (UUID participant : expedition.participants()) {
               if(expedition.planned()&&!journeys.get(expedition.id()).contributed(participant))continue;
               batches.add(
                  new RewardBatch(
                     EffectJournal.id("expedition-reward:" + expedition.id() + ":" + participant),
                     participant,
                     definition.rewards().stream().map(ItemAmount::stack).toList(),
                     definition.moneyPerParticipant()
                  )
               );
            }
         }

         expedition.status(status);

         for (RewardBatch batch : batches) {
            this.repository.reward(batch.id(), batch.owner(), batch.items(), batch.money());
         }


         this.repository
            .history(
               new ExpeditionHistory(
                  expedition.id(),
                  expedition.leaderId(),
                  expedition.definitionId(),
                  expedition.participants().size(),
                  expedition.objectiveProgress(),
                  expedition.kills(),
                  status,
                  System.currentTimeMillis()
               ),
               this.plugin.getConfig().getInt("expeditions.history-limit", 30)
            );
         return this.repository.saveAsync().whenComplete((saved,error)->{
            if(error!=null){plugin.getLogger().log(Level.SEVERE,"Завершение экспедиции требует сверки; награды не выданы",error);return;}
            if(!plugin.isEnabled()||stopping)return;
            ru.neverland.core.SuiteScheduler.get().runTask(plugin,()->{
               if(status==ExpeditionStatus.CANCELLED)broadcast(expedition,"admin-cancelled",Map.of());
               if(status==ExpeditionStatus.FAILED)broadcast(expedition,"failed",Map.of("expedition",definition==null?expedition.definitionId():ColorUtil.strip(definition.name())));
               if(status==ExpeditionStatus.COMPLETED)for(UUID participant:expedition.participants()){
                  Player player=Bukkit.getPlayer(participant);if(player!=null)ru.neverland.core.SuiteScheduler.entity(plugin,player,()->{messages.send(player,"completed",Map.of("expedition",ColorUtil.strip(definition.name())));claim(player);});
               }
               BossBar bar=bars.remove(expedition.id());if(bar!=null)for(Player player:List.copyOf(bar.getPlayers()))ru.neverland.core.SuiteScheduler.entity(plugin,player,()->bar.removePlayer(player));
               long returnWindow=Math.max(30L,plugin.getConfig().getLong("expeditions.manual-return-window-seconds",600L));
               returnTickets.grantAsync(expedition.participants(),expedition.leaderId(),System.currentTimeMillis()+returnWindow*1000L).whenComplete((v,e)->{if(e!=null)plugin.getLogger().warning("Возврат экспедиции не подтверждён: "+expedition.id());});
               sites.restore(expedition,()->{repository.remove(expedition);repository.saveAsync();});
            });
         });
      }
      return CompletableFuture.completedFuture(null);
   } }

   public void claim(Player player) {
      if(!ru.neverland.core.SuiteScheduler.owns(player)){ru.neverland.core.SuiteScheduler.entity(plugin,player,()->claim(player));return;}
      if(!repository.hasReward(player.getUniqueId())){messages.send(player,"no-reward");return;}
      rewardDelivery.claim(player).whenComplete((done,error)->{if(!stopping&&plugin.isEnabled())ru.neverland.core.SuiteScheduler.entity(plugin,player,()->{
         if(error==null&&done)player.sendMessage("Сохранённая награда экспедиции выдана.");
         else player.sendMessage("Выдача не завершена. Сохранённая награда остаётся в очереди; освободите место или попросите администратора проверить начисление.");
      });if(error!=null)plugin.getLogger().log(Level.WARNING,"Награда требует проверки: "+player.getUniqueId(),error);});
   }

   public java.util.concurrent.CompletionStage<Void> readyAsync(){return returnTickets.readyAsync().thenCompose(v->journeys.load().handle((loaded,error)->{if(error!=null)plugin.getLogger().log(Level.SEVERE,"Поэтапные походы приостановлены; обычные экспедиции работают",error);else try(var guard=ru.neverland.core.SuiteState.guard()){for(var expedition:repository.active())if(expedition.planned()){var journey=journeys.get(expedition.id());if(journey!=null)try{reconcile(expedition,journey);}catch(RuntimeException invalid){journeyIssues.add(expedition.id());if(!expedition.timer().paused())expedition.timer(expedition.timer().edit("pause",0,System.currentTimeMillis()));plugin.getLogger().log(Level.SEVERE,"Подтверждения похода требуют сверки: "+expedition.id(),invalid);}}}return (Void)null;}));}
   public java.util.concurrent.CompletionStage<Void> prepareClose(){try(var guard=ru.neverland.core.SuiteState.guard()){stopping=true;stopTasks();var pending=CompletableFuture.allOf(transitions.stream().map(s->s.handle((v,e)->null).toCompletableFuture()).toArray(CompletableFuture[]::new));return pending.thenCompose(v->rewardDelivery.prepareClose()).thenCompose(v->returnTickets.prepareClose()).thenCompose(v->journeys.prepareClose()).thenCompose(v->repository.prepareClose());}}

   public void returnToCamp(Player player) { try (var suiteGuard14 = ru.neverland.core.SuiteState.guard()) {
      if (!this.canReturn(player)) {
         this.messages.send(player, "return-denied");
      } else {
         UUID campOwner = this.returnCampOwner(player);
         if (campOwner == null) {
            this.messages.send(player, "no-return");
         } else {
            CampView camp = this.camps.owned(campOwner);
            if (camp != null && camp.world() != null) {
               if (this.returning.add(player.getUniqueId())) {
                  try {
                     player.teleportAsync(camp.anchor()).whenComplete((success, error) -> {
                        if (this.plugin.isEnabled()) {
                           ru.neverland.core.SuiteScheduler.entity(this.plugin, player, () -> {
                              this.returning.remove(player.getUniqueId());
                              if (error == null && Boolean.TRUE.equals(success)) {
                                 this.returnTickets.removeAsync(player.getUniqueId());
                                 this.messages.send(player, "returned");
                              } else {
                                 this.messages.send(player, "return-failed");
                              }
                           });
                        }
                     });
                  } catch (RuntimeException var5) {
                     this.returning.remove(player.getUniqueId());
                     this.messages.send(player, "return-failed");
                  }
               }
            } else {
               this.messages.send(player, "return-camp-missing");
            }
         }
      }
   } }

   public boolean canReturn(Player player) { try (var suiteGuard15 = ru.neverland.core.SuiteState.guard()) {
      return ExpeditionPermissions.canReturn(player);
   } }

   public boolean hasReturnTarget(Player player) { try (var suiteGuard16 = ru.neverland.core.SuiteState.guard()) {
      return this.returnCampOwner(player) != null;
   } }

   private UUID returnCampOwner(Player player) {
      ActiveExpedition expedition = this.repository.byLeader(player.getUniqueId());
      if (expedition != null) {
         return expedition.leaderId();
      } else {
         Ticket ticket = this.returnTickets.get(player.getUniqueId(), System.currentTimeMillis());
         return ticket == null ? null : ticket.campOwner();
      }
   }

   public boolean shieldDenied(Player player) { try (var suiteGuard17 = ru.neverland.core.SuiteState.guard()) {
      return ExpeditionPermissions.shieldDenied(player, this.at(BlockPos.of(player.getLocation()), player.getWorld()) != null);
   } }

   public void warnShield(Player player) { try (var suiteGuard18 = ru.neverland.core.SuiteState.guard()) {
      long now = System.currentTimeMillis();
      if (now - this.shieldWarnings.getOrDefault(player.getUniqueId(), 0L) >= 3000L) {
         this.shieldWarnings.put(player.getUniqueId(), now);
         this.messages.send(player, "shield-denied");
      }
   } }

   public void sendTarget(Player player, ActiveExpedition expedition) { try (var suiteGuard19 = ru.neverland.core.SuiteState.guard()) {
      Location target = expedition.siteLocation();
      if (target != null) {
         long distance = player.getWorld().equals(target.getWorld()) ? Math.round(player.getLocation().distance(target)) : -1L;
         this.messages
            .send(
               player,
               "target-coordinates",
               Map.of(
                  "world",
                  target.getWorld().getName(),
                  "x",
                  target.getBlockX(),
                  "y",
                  target.getBlockY(),
                  "z",
                  target.getBlockZ(),
                  "distance",
                  distance < 0L ? "другой мир" : distance + " блоков"
               )
            );
      }
   } }

   private void tick() {
      long now = System.currentTimeMillis();
      participation.values().removeIf(p->now-p.at()>3000);
      this.returnTickets.pruneAsync(now);

      for (ActiveExpedition expedition : new ArrayList<>(this.repository.active())) {
         if(expedition.planned()&&journeyUnavailable(expedition)){if((!journeys.ready()||!plugin.getConfig().getBoolean("journeys.enabled",true))&&!expedition.timer().paused()){expedition.timer(expedition.timer().edit("pause",0,now));repository.changed();}this.updateBar(expedition);continue;}
         if (!expedition.siteReady()||expedition.timer().paused()) { this.updateBar(expedition); continue; }
         if(expedition.planned()&&journeys.get(expedition.id())!=null&&journeys.get(expedition.id()).complete()){check(expedition,definition(expedition));continue;}
         if (now >= expedition.expiresAt()) {
            this.finish(expedition, ExpeditionStatus.FAILED);
         } else {
            ExpeditionDefinition definition = this.definition(expedition);
            if (definition != null) {
               this.sites.guard(expedition, definition);
            }

            this.updateBar(expedition);
         }
      }

      this.repository.saveIfDirtyAsync();
   }

   public ActiveExpedition at(BlockPos position, World world) { try (var suiteGuard20 = ru.neverland.core.SuiteState.guard()) {
      int radius = this.plugin.getConfig().getInt("generation.protected-radius", 16);

      for (ActiveExpedition expedition : this.repository.active()) {
         if (expedition.worldId().equals(world.getUID())) {
            int dx = expedition.siteAnchor().x() - position.x();
            int dz = expedition.siteAnchor().z() - position.z();
            if (dx * dx + dz * dz <= radius * radius) {
               return expedition;
            }
         }
      }

      return null;
   } }

   public boolean participant(Player player, ActiveExpedition expedition) { try (var suiteGuard21 = ru.neverland.core.SuiteState.guard()) {
      return expedition != null && expedition.participants().contains(player.getUniqueId());
   } }

   private void broadcast(ActiveExpedition expedition, String key, Map<String, ?> variables) {
      for (UUID participant : expedition.participants()) {
         Player player = Bukkit.getPlayer(participant);
         if (player != null) {
            ru.neverland.core.SuiteScheduler.entity(plugin,player,()->this.messages.send(player,key,variables));
         }
      }
   }

   private void bar(ActiveExpedition expedition) {
      if (this.plugin.getConfig().getBoolean("bossbar.enabled", true)) {
         BarColor color = BarColor.valueOf(this.plugin.getConfig().getString("bossbar.color", "PURPLE"));
         BarStyle style = BarStyle.valueOf(this.plugin.getConfig().getString("bossbar.style", "SEGMENTED_10"));
         BossBar bar = Bukkit.createBossBar("", color, style, new BarFlag[0]);

         for (UUID participant : expedition.participants()) {
            Player player = Bukkit.getPlayer(participant);
            if (player != null) {
               ru.neverland.core.SuiteScheduler.entity(plugin,player,()->{if(bars.get(expedition.id())==bar)bar.addPlayer(player);});
            }
         }

         this.bars.put(expedition.id(), bar);
         this.updateBar(expedition);
      }
   }

   private void updateBar(ActiveExpedition expedition) {
      BossBar bar = this.bars.get(expedition.id());
      ExpeditionDefinition definition = this.definition(expedition);
      if (bar != null && definition != null) {
         int total = definition.goal() + definition.mobCount();
         int done = expedition.objectiveProgress() + Math.min(expedition.kills(), definition.mobCount());
         bar.setProgress(total == 0 ? 1.0 : Math.min(1.0, (double)done / total));
         String objective = expedition.objectiveProgress()
            + "/"
            + definition.goal()
            + " • "
            + expedition.kills()
            + "/"
            + definition.mobCount()
            + " • "
            + (expedition.timer().paused()?"Пауза • ":"")+TimeUtil.format(expedition.timer().remaining(System.currentTimeMillis()));
         bar.setTitle(
            ColorUtil.color(
               this.plugin
                  .getConfig()
                  .getString("bossbar.title", "%expedition% | %objective%")
                  .replace("%expedition%", definition.name())
                  .replace("%objective%", objective)
            )
         );
      }
   }

   public ExpeditionRepository repository() { try (var suiteGuard22 = ru.neverland.core.SuiteState.guard()) {
      return this.repository;
   } }

   public ExpeditionRegistry registry() { try (var suiteGuard23 = ru.neverland.core.SuiteState.guard()) {
      return this.registry;
   } }

   public static enum StartResult {
      QUEUED,
      UNAVAILABLE,
      NO_CAMP,
      INACTIVE,
      TOO_FAR,
      STASH_OPEN,
      LOW_LEVEL,
      ACTIVE,
      MISSING,
      WORLD_BLOCKED;
   }

    public java.util.List<ru.neverland.core.ActivityAdmin.Target> adminTargets() { try (var suiteGuard24 = ru.neverland.core.SuiteState.guard()) {
        ru.neverland.core.ApiServices.primaryThread();
        return repository.active().stream().map(c -> new ru.neverland.core.ActivityAdmin.Target(c.id().toString(),c.definitionId()+" / "+c.leaderId(),
                c.status()==ExpeditionStatus.ACTIVE ? ru.neverland.core.ActivityAdmin.TIMED : java.util.Set.of("status"), (action,minutes) -> {
            ru.neverland.core.ApiServices.primaryThread();
            if(action.equals("status"))return c.id()+" | "+c.definitionId()+" / "+c.leaderId()+" | "+c.timer().describe(System.currentTimeMillis());
            if(!(c.status()==ExpeditionStatus.ACTIVE))throw new IllegalStateException("Задача уже завершается или ожидает расчёта; используйте штатную сверку");
            if(action.equals("cancel")){finish(c,ExpeditionStatus.CANCELLED); return "Задача отменена без провала; выполненные действия сохранены";}
            var before=c.timer();
            try{c.timer(before.edit(action,minutes,System.currentTimeMillis())); repository.saveAsync();}
            catch(Exception ex){c.timer(before); throw ex;}
            return c.id()+" | "+c.timer().describe(System.currentTimeMillis());
        })).toList();
    } }

}
