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
   }

   public void startTasks() {
      this.stopTasks();
      long period = Math.max(20L, this.plugin.getConfig().getLong("expeditions.expiry-check-seconds", 10L) * 20L);
      this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, period, period);
      this.shieldTask = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
         for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getActiveItem().getType() == Material.SHIELD && this.shieldDenied(player)) {
               player.clearActiveItem();
               this.warnShield(player);
            }
         }
      }, 1L, 1L);

      for (ActiveExpedition expedition : this.repository.active()) {
         if (expedition.status() == ExpeditionStatus.ACTIVE) {
            this.bar(expedition);
         } else {
            this.sites.restore(expedition, () -> {
               this.repository.remove(expedition);
               this.repository.save();
            });
         }
      }
   }

   public void stopTasks() {
      if (this.task != null) {
         this.task.cancel();
      }

      if (this.shieldTask != null) {
         this.shieldTask.cancel();
      }

      this.task = this.shieldTask = null;

      for (BossBar bar : this.bars.values()) {
         bar.removeAll();
      }

      this.bars.clear();
      this.shieldWarnings.clear();
   }

   public ExpeditionService.StartResult start(Player leader, ExpeditionDefinition definition) {
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
               this.preparing.add(camp.owner());
               this.messages.send(leader, "preparing");
               this.sites
                  .findSite(
                     camp.world(),
                     camp.anchor(),
                     definition,
                     site -> {
                        this.preparing.remove(camp.owner());
                        if (site == null) {
                           this.messages.send(leader, "site-not-found");
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
                           List<Player> party = this.camps.party(camp);
                           party.forEach(playerx -> expedition.participants().add(playerx.getUniqueId()));
                           if (!this.sites.build(expedition, definition)) {
                              this.repository.remove(expedition);
                              this.repository.save();
                              this.messages.send(leader, "start-failed");
                           } else if (!this.camps.consume(camp, definition.costs())) {
                              this.sites.restore(expedition, () -> {
                                 this.repository.remove(expedition);
                                 this.repository.save();
                              });
                              this.messages.send(leader, "start-failed");
                           } else {
                              this.bar(expedition);
                              this.messages.send(leader, "started", Map.of("expedition", ColorUtil.strip(definition.name()), "party", party.size()));
                              boolean teleport = this.plugin.getConfig().getBoolean("expeditions.teleport-party-to-site", false);
                              Location destination = expedition.siteLocation();

                              for (Player player : party) {
                                 if (!player.equals(leader)) {
                                    this.messages.send(player, "party-member", Map.of("expedition", ColorUtil.strip(definition.name())));
                                 }

                                 this.sendTarget(player, expedition);
                                 if (teleport && destination != null) {
                                    player.teleportAsync(destination);
                                 }
                              }
                           }
                        }
                     }
                  );
               return ExpeditionService.StartResult.QUEUED;
            }
         }
      }
   }

   public boolean objective(Player player, BlockPos position) {
      ActiveExpedition expedition = this.at(position, player.getWorld());
      if (expedition == null || !expedition.objectives().contains(position) || expedition.completed().contains(position)) {
         return false;
      } else if (!expedition.participants().contains(player.getUniqueId())) {
         this.messages.send(player, "not-participant");
         return true;
      } else {
         expedition.completed().add(position);
         position.location(player.getWorld()).getBlock().setType(Material.AIR, false);
         this.repository.changed();
         ExpeditionDefinition definition = this.registry.get(expedition.definitionId());
         this.broadcast(expedition, "objective", Map.of("progress", expedition.objectiveProgress(), "goal", definition.goal()));
         this.updateBar(expedition);
         this.check(expedition, definition);
         return true;
      }
   }

   public void mobDied(LivingEntity entity) {
      ActiveExpedition expedition = this.sites.expeditionForMob(entity);
      if (expedition != null && expedition.status() == ExpeditionStatus.ACTIVE) {
         expedition.spawnedMobs().remove(entity.getUniqueId());
         ExpeditionDefinition definition = this.registry.get(expedition.definitionId());
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
                  Bukkit.getScheduler().runTask(this.plugin, () -> {
                     if (expedition.status() == ExpeditionStatus.ACTIVE && this.repository.get(expedition.id()) != null) {
                        this.sites.respawn(expedition, definition, 1);
                     }
                  });
               }
            }
         }
      }
   }

   public ActiveExpedition expeditionForMob(Entity entity) {
      return this.sites.expeditionForMob(entity);
   }

   public boolean insideMobArea(ActiveExpedition expedition, Location location) {
      return this.sites.insideMobArea(expedition, location);
   }

   public boolean preventMobTeleportOutside() {
      return this.plugin.getConfig().getBoolean("combat.prevent-teleport-outside", true);
   }

   public boolean protectMobFromEnvironment() {
      return this.plugin.getConfig().getBoolean("combat.protect-from-fall-and-suffocation", true);
   }

   public boolean relocateMob(Entity entity) {
      return this.sites.relocateMob(entity);
   }

   public void relocateMobNextTick(Entity entity) {
      UUID entityId = entity.getUniqueId();
      Bukkit.getScheduler().runTask(this.plugin, () -> {
         Entity current = Bukkit.getEntity(entityId);
         if (current != null && current.isValid() && !current.isDead()) {
            this.sites.relocateMob(current);
         }
      });
   }

   private void check(ActiveExpedition expedition, ExpeditionDefinition definition) {
      if (expedition.objectiveProgress() >= definition.goal() && expedition.kills() >= definition.mobCount()) {
         this.finish(expedition, ExpeditionStatus.COMPLETED);
      }
   }

   public void finish(ActiveExpedition expedition, ExpeditionStatus status) {
      if (!this.repository.writable()) {
         throw new IllegalStateException("Хранилище экспедиций недоступно");
      } else if (expedition.status() == ExpeditionStatus.ACTIVE) {
         ExpeditionDefinition definition = this.registry.get(expedition.definitionId());
         ArrayList<RewardBatch> batches = new ArrayList<>();
         if (status == ExpeditionStatus.COMPLETED) {
            if (definition == null) {
               throw new IllegalStateException("Определение экспедиции удалено; сначала восстановите настройки награды");
            }

            for (UUID participant : expedition.participants()) {
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

         if (status != ExpeditionStatus.COMPLETED) {
            this.broadcast(expedition, "failed", Map.of("expedition", definition == null ? expedition.definitionId() : ColorUtil.strip(definition.name())));
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
         this.repository.save();
         if (status == ExpeditionStatus.COMPLETED) {
            for (UUID participant : expedition.participants()) {
               Player player = Bukkit.getPlayer(participant);
               if (player != null) {
                  this.messages.send(player, "completed", Map.of("expedition", ColorUtil.strip(definition.name())));
                  this.claim(player);
               }
            }
         }

         BossBar bar = this.bars.remove(expedition.id());
         if (bar != null) {
            bar.removeAll();
         }

         long returnWindow = Math.max(30L, this.plugin.getConfig().getLong("expeditions.manual-return-window-seconds", 600L));
         this.returnTickets.grant(expedition.participants(), expedition.leaderId(), System.currentTimeMillis() + returnWindow * 1000L);
         this.sites.restore(expedition, () -> {
            this.repository.remove(expedition);
            this.repository.save();
         });
      }
   }

   public void claim(Player player) {
      if (!Bukkit.isPrimaryThread()) {
         throw new IllegalStateException("Награды требуют основного потока");
      } else if (!this.repository.hasReward(player.getUniqueId())) {
         this.messages.send(player, "no-reward");
      } else if (player.isOnline() && player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
         try {
            this.repository.saveIfDirty();
            boolean complete = true;
            double paid = 0.0;
            EffectJournal journal = this.repository.effects();

            rewardBatches:
            for (RewardBatch batch : this.repository.rewards(player.getUniqueId())) {
               if (batch.money() > 0.0) {
                  boolean already = journal.state(batch.moneyId()) == EffectJournal.State.DONE;
                  if (!journal.execute(
                     batch.moneyId(),
                     "Награда " + batch.id() + " игроку " + batch.owner() + ": " + batch.money(),
                     () -> this.towny.reward(player, batch.money(), batch.moneyId())
                  )) {
                     complete = false;
                     continue;
                  }

                  if (!already) {
                     paid += batch.money();
                  }
               }

               List<ItemStack> items = batch.items();

               for (int i = 0; i < items.size(); i++) {
                  UUID id = batch.itemId(i);
                  if (journal.state(id) != EffectJournal.State.DONE) {
                     if (journal.state(id) == EffectJournal.State.PENDING) {
                        throw new IllegalStateException("Выдача " + id + " требует сверки администратора");
                     }

                     ItemStack item = items.get(i);
                     ItemStack[] plan = InventoryPlan.insert(player.getInventory().getStorageContents(), item);
                     if (plan == null) {
                        complete = false;
                        continue rewardBatches;
                     }

                     journal.execute(id, "Предмет награды " + batch.id() + " игроку " + batch.owner() + " #" + i, () -> {
                        PlayerSaveReceipt saved = PlayerSaveReceipt.before(player);
                        player.getInventory().setStorageContents(plan);
                        saved.save(player);
                        return true;
                     });
                  }
               }

               this.repository.completeReward(batch);
            }

            this.repository.pruneEffects();
            if (complete && !this.repository.hasReward(player.getUniqueId())) {
               this.messages.send(player, "claim-success", Map.of("money", paid));
            } else {
               player.sendMessage("Часть награды ожидает выдачи. Освободите место и используйте /expedition claim; уже полученное не повторится.");
            }
         } catch (Exception var14) {
            this.plugin.getLogger().log(Level.WARNING, "Выдача награды остановлена для " + player.getUniqueId(), (Throwable)var14);
            player.sendMessage("Выдача остановлена: " + var14.getMessage() + ". Сохранённую награду можно получить после восстановления.");
         }
      } else {
         player.sendMessage("Награды доступны в выживании или приключении.");
      }
   }

   public void returnToCamp(Player player) {
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
                           Bukkit.getScheduler().runTask(this.plugin, () -> {
                              this.returning.remove(player.getUniqueId());
                              if (error == null && Boolean.TRUE.equals(success)) {
                                 this.returnTickets.remove(player.getUniqueId());
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
   }

   public boolean canReturn(Player player) {
      return ExpeditionPermissions.canReturn(player);
   }

   public boolean hasReturnTarget(Player player) {
      return this.returnCampOwner(player) != null;
   }

   private UUID returnCampOwner(Player player) {
      ActiveExpedition expedition = this.repository.byLeader(player.getUniqueId());
      if (expedition != null) {
         return expedition.leaderId();
      } else {
         Ticket ticket = this.returnTickets.get(player.getUniqueId(), System.currentTimeMillis());
         return ticket == null ? null : ticket.campOwner();
      }
   }

   public boolean shieldDenied(Player player) {
      return ExpeditionPermissions.shieldDenied(player, this.at(BlockPos.of(player.getLocation()), player.getWorld()) != null);
   }

   public void warnShield(Player player) {
      long now = System.currentTimeMillis();
      if (now - this.shieldWarnings.getOrDefault(player.getUniqueId(), 0L) >= 3000L) {
         this.shieldWarnings.put(player.getUniqueId(), now);
         this.messages.send(player, "shield-denied");
      }
   }

   public void sendTarget(Player player, ActiveExpedition expedition) {
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
   }

   private void tick() {
      long now = System.currentTimeMillis();
      this.returnTickets.prune(now);

      for (ActiveExpedition expedition : new ArrayList<>(this.repository.active())) {
         if (now >= expedition.expiresAt()) {
            this.finish(expedition, ExpeditionStatus.FAILED);
         } else {
            ExpeditionDefinition definition = this.registry.get(expedition.definitionId());
            if (definition != null) {
               this.sites.guard(expedition, definition);
            }

            this.updateBar(expedition);
         }
      }

      this.repository.saveIfDirty();
   }

   public ActiveExpedition at(BlockPos position, World world) {
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
   }

   public boolean participant(Player player, ActiveExpedition expedition) {
      return expedition != null && expedition.participants().contains(player.getUniqueId());
   }

   private void broadcast(ActiveExpedition expedition, String key, Map<String, ?> variables) {
      for (UUID participant : expedition.participants()) {
         Player player = Bukkit.getPlayer(participant);
         if (player != null) {
            this.messages.send(player, key, variables);
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
               bar.addPlayer(player);
            }
         }

         this.bars.put(expedition.id(), bar);
         this.updateBar(expedition);
      }
   }

   private void updateBar(ActiveExpedition expedition) {
      BossBar bar = this.bars.get(expedition.id());
      ExpeditionDefinition definition = this.registry.get(expedition.definitionId());
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
            + TimeUtil.format(expedition.expiresAt() - System.currentTimeMillis());
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

   public ExpeditionRepository repository() {
      return this.repository;
   }

   public ExpeditionRegistry registry() {
      return this.registry;
   }

   public static enum StartResult {
      QUEUED,
      NO_CAMP,
      INACTIVE,
      TOO_FAR,
      STASH_OPEN,
      LOW_LEVEL,
      ACTIVE,
      MISSING,
      WORLD_BLOCKED;
   }
}
