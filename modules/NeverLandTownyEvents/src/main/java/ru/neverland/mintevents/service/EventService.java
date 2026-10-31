package ru.neverland.mintevents.service;

import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import ru.neverland.mintevents.api.EventSnapshot;
import ru.neverland.mintevents.api.MintTownyEventsApi;
import ru.neverland.mintevents.integration.TownyHook;
import ru.neverland.mintevents.model.ActiveEvent;
import ru.neverland.mintevents.model.EventDefinition;
import ru.neverland.mintevents.model.EventMode;
import ru.neverland.mintevents.model.HistoryEntry;
import ru.neverland.mintevents.util.ColorUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class EventService implements MintTownyEventsApi {
    private final JavaPlugin plugin;
    private final TownyHook towny;
    private final EventRegistry registry;
    private final EventRepository repository;
    private final DevelopmentService development;
    private final MessageService messages;
    private final FireService fires;
    private final ru.neverland.mintevents.scenarios.ScenarioService scenarios;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, Long> raidFailureWarnings = new HashMap<>();
    private RaidCatalog raidCatalog;
    private RaidScaling raidScaling;
    private final ru.neverland.mintevents.integration.CustomRaidMobs customMobs;
    private final NamespacedKey raidGenerationKey;
    private final NamespacedKey raidPointsKey;
    private final NamespacedKey raidRangedMultiplierKey;
    private final Map<UUID, Long> raidSpawnAttempts = new HashMap<>();
    private final NamespacedKey raidMobKey;
    private final NamespacedKey legacyRaidMobKey;
    private BukkitTask tickTask;
    private BukkitTask randomTask;
    private BukkitTask saveTask;
    private long effectsTick;
    private long seasonalWarning;

    public EventService(JavaPlugin plugin, TownyHook towny, EventRegistry registry,
                        EventRepository repository, DevelopmentService development,
                        MessageService messages) {
        this.plugin = plugin;
        this.towny = towny;
        this.registry = registry;
        this.repository = repository;
        this.development = development;
        this.messages = messages;
        this.fires = new FireService(plugin, towny);
        this.raidMobKey = new NamespacedKey(plugin, "raid_town");
        this.raidGenerationKey = new NamespacedKey(plugin, "raid_generation");
        this.raidPointsKey = new NamespacedKey(plugin, "raid_points");
        this.raidRangedMultiplierKey = new NamespacedKey(plugin, "raid_ranged_multiplier");
        this.customMobs = new ru.neverland.mintevents.integration.CustomRaidMobs(plugin);
        this.raidCatalog = new RaidCatalog(plugin);
        this.raidScaling = new RaidScaling(plugin);
        this.legacyRaidMobKey = NamespacedKey.fromString("minttownyevents:raid_town");
        this.scenarios=new ru.neverland.mintevents.scenarios.ScenarioService(plugin,new ru.neverland.mintevents.scenarios.ScenarioService.Sources(){
            public ru.neverland.mintevents.scenarios.ScenarioService.Active active(UUID town){return scenarioActive(town);}
            public ru.neverland.mintevents.scenarios.ScenarioBook.Source observe(ru.neverland.mintevents.scenarios.ScenarioBook.Scenario row){return scenarioSource(row);}
        });
    }

    public void start() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        stopTasks();
        scenarios.start();
        repository.initializeRaidVictories(registry.all().stream().filter(d -> d.mode() == EventMode.RAID)
                .map(EventDefinition::id).collect(java.util.stream.Collectors.toSet()));
        for (ActiveEvent event : repository.active().values()) {
            EventDefinition definition = definition(event);
            if (definition != null && definition.mode() == EventMode.RAID && event.raid() == null) {
                ActiveEvent upgraded = new ActiveEvent(event.townId(), event.eventId(), event.startedAt(),
                        Math.max(event.endsAt(), System.currentTimeMillis() + definition.durationSeconds() * 1000),
                        event.progress(), event.goal(), event.protection(), 0,event.instanceId());
                upgraded.raid(new ru.neverland.mintevents.model.RaidState());
                repository.put(upgraded);
            }
        }
ru.neverland.core.WorldTasks.eachEntity(plugin,this::validateLoadedRaidEntity);
        long tickPeriod = Math.max(1, plugin.getConfig().getLong("runtime.tick-seconds", 1)) * 20;
        tickTask = ru.neverland.core.SuiteScheduler.get().runTaskTimer(plugin, this::tick, 20, tickPeriod);
        saveTask = ru.neverland.core.SuiteScheduler.get().runTaskTimer(plugin, repository::saveIfDirty, 1200, 1200);
        if (plugin.getConfig().getBoolean("scheduler.enabled", true)) {
            long delay = Math.max(1, plugin.getConfig().getLong("scheduler.startup-delay-minutes", 10)) * 1200;
            long period = Math.max(1, plugin.getConfig().getLong("scheduler.check-interval-minutes", 5)) * 1200;
            randomTask = ru.neverland.core.SuiteScheduler.get().runTaskTimer(plugin, this::randomCheck, delay, period);
        }
    } }

    public void shutdown() { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        stopTasks();
        scenarios.prepareClose();
        fires.clearVisuals();
        bars.values().forEach(BossBar::removeAll);
        bars.clear();
        for(Town town : towny.towns()) {
            long remaining = shieldRemainingMillis(town.getUUID());
            if(remaining > 0 && repository.shield(town.getUUID()) == null) repository.shield(town.getUUID(), System.currentTimeMillis() + remaining);
        }
        repository.save();
        // Full shutdown has no ticking regions. Saved raid state recreates unfinished
        // enemies; generation validation removes old bodies on the next chunk load.
        if(!Bukkit.isStopping())for(ActiveEvent event : repository.active().values()) cleanupRaidMobs(event.townId());
    } }

    private void stopTasks() {
        if (tickTask != null) tickTask.cancel();
        if (randomTask != null) randomTask.cancel();
        if (saveTask != null) saveTask.cancel();
        tickTask = randomTask = saveTask = null;
    }

    public boolean startEvent(Town town, EventDefinition definition) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { return startEvent(town, definition, false); } }
    public boolean startEvent(Town town, EventDefinition definition, boolean force) { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {
        if (town == null || definition == null || repository.active(town.getUUID()) != null) return false;
        if (!force && shieldRemainingMillis(town.getUUID()) > 0) return false;
        if (definition.mode() == EventMode.FIRE && hasFireDamage(town.getUUID())) return false;
        long now = System.currentTimeMillis();
        int residents = Math.max(1, town.getResidents().size());
        int goal = definition.baseGoal() + definition.goalPerResident() * residents;
        double protection = development.protection(town.getUUID(), definition);
        ActiveEvent active = new ActiveEvent(town.getUUID(), definition.id(), now,
                now + definition.durationSeconds() * 1000, 0, goal, protection, 0);
        if (definition.mode() == EventMode.RAID) {
            var profile = raidPreview(town);
            active.raid(new ru.neverland.mintevents.model.RaidState(profile, raidScaling.plan(raidCatalog, profile)));
        }
        repository.put(active);
        repository.save();
        scenarios.register(scenarioActive(town.getUUID()));
        announce(town, "event-start-town", definition, active);
        updateBossBar(town, definition, active);
        if (definition.mode() == EventMode.RAID) {
            Location anchor = raidAnchor(town);
            if (plugin.getConfig().getBoolean("runtime.announce-to-town", true))
                for (Player player : onlineResidents(town)) messages.send(player, "raid-difficulty-town",
                        Map.of("difficulty", active.raid().profile().label()));
            if (anchor != null) maybeRaidWave(town, anchor, active, 1.0 - active.protection(), now, false);
        }
        return true;
    } }

    public ru.neverland.mintevents.scenarios.ScenarioService scenarios(){return scenarios;}
    private ru.neverland.mintevents.scenarios.ScenarioService.Active scenarioActive(UUID townId){
        ActiveEvent active=repository.active(townId);if(active==null)return null;var town=town(townId);if(town==null)return null;var definition=definition(active);
        return new ru.neverland.mintevents.scenarios.ScenarioService.Active(active.instanceId(),townId,town==null?"Удалённый город":town.getName(),active.eventId(),active.startedAt(),active.endsAt(),active.progress(),active.goal(),active.paused()||definition==null,definition==null?"":definition.mode().name());
    }
    private ru.neverland.mintevents.scenarios.ScenarioBook.Source scenarioSource(ru.neverland.mintevents.scenarios.ScenarioBook.Scenario row){
        var live=scenarioActive(row.town());if(live!=null&&live.id().equals(row.id()))return new ru.neverland.mintevents.scenarios.ScenarioBook.Source(live.paused()?ru.neverland.mintevents.scenarios.ScenarioBook.SourceState.PAUSED:ru.neverland.mintevents.scenarios.ScenarioBook.SourceState.RUNNING,live.progress(),live.goal());
        for(var old:repository.history(row.town()))if(row.id().equals(old.instanceId()))return new ru.neverland.mintevents.scenarios.ScenarioBook.Source(old.success()?ru.neverland.mintevents.scenarios.ScenarioBook.SourceState.SUCCESS:ru.neverland.mintevents.scenarios.ScenarioBook.SourceState.FAILURE,old.progress(),old.goal());
        return new ru.neverland.mintevents.scenarios.ScenarioBook.Source(ru.neverland.mintevents.scenarios.ScenarioBook.SourceState.MISSING,0,1);
    }
    public double cropCancelChance(UUID townId){try(var guard=ru.neverland.core.SuiteState.guard()){
        var active=repository.active(townId);var definition=definition(active);if(active==null||definition==null||active.paused()||active.endsAt()<=System.currentTimeMillis()||(definition.mode()!=EventMode.DROUGHT&&definition.mode()!=EventMode.FLOOD))return 0;
        double base=definition.mode()==EventMode.FLOOD?plugin.getConfig().getDouble("gameplay.flood-base-crop-cancel-chance",.60):plugin.getConfig().getDouble("gameplay.drought-base-crop-cancel-chance",.70);double original=Math.max(0,Math.min(1,base*(1-active.protection())));double factor=scenarios.effects(active.instanceId()).crops();
        return factor==1?original:ru.neverland.mintevents.scenarios.ScenarioBook.loss(original,factor);
    }}
    @Override public long shieldRemainingMillis(UUID townId) { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {
        ru.neverland.core.ApiServices.primaryThread();
        Town city = town(townId); if(city == null) return 0;
        Long explicit = repository.shield(townId);
        long registered = city.getRegistered();
        if(registered > 0 && registered < 100_000_000_000L) registered *= 1000;
        long until = explicit != null ? explicit : registered > 0 ? Math.addExact(registered, 86_400_000L) : 0;
        return Math.max(0, until - System.currentTimeMillis());
    } }
    public void townCreated(Town town) { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {
        if(repository.shield(town.getUUID()) != null) return;
        long registered = town.getRegistered();
        if(registered > 0 && registered < 100_000_000_000L) registered *= 1000;
        long until = Math.addExact(registered > 0 ? registered : System.currentTimeMillis(), 86_400_000L);
        repository.shield(town.getUUID(), until);
        for(Player player : onlineResidents(town)) player.sendMessage("§bЩит новичка: город защищён от городских событий на 24 часа.");
    } }
    public void shield(Town town, long hours) { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {
        if(hours < 0 || hours > 720) throw new IllegalArgumentException("Щит: от 0 до 720 часов");
        repository.shield(town.getUUID(), hours == 0 ? 0 : System.currentTimeMillis() + hours * 3_600_000L);
    } }
    @Override public boolean paused(UUID townId) { try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) { ru.neverland.core.ApiServices.primaryThread(); ActiveEvent event = active(townId); return event != null && event.paused(); } }
    public java.util.List<ru.neverland.core.ActivityAdmin.Target> adminTargets() { try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) {
        ru.neverland.core.ApiServices.primaryThread();
        var targets=new java.util.ArrayList<ru.neverland.core.ActivityAdmin.Target>();
        for(Town town:towny.towns()) {
            ActiveEvent event=active(town.getUUID());if(event==null)continue;
            String id=town.getUUID()+"/"+event.startedAt()+"/"+event.eventId();
            var actions=new java.util.HashSet<>(java.util.Set.of("status","cancel","restart","extend",event.paused()?"resume":"pause"));
            targets.add(new ru.neverland.core.ActivityAdmin.Target(id,town.getName()+" / "+event.eventId()+" / "+(event.paused()?"пауза":"активно"),actions,(action,minutes)->{
                ActiveEvent current=active(town.getUUID());
                if(current==null||current.startedAt()!=event.startedAt()||!current.eventId().equals(event.eventId()))throw new IllegalArgumentException("Событие уже заменено");
                if(action.equals("status"))return town.getName()+" / "+current.eventId()+" / "+current.progress()+"/"+current.goal()+" / осталось "+current.secondsLeft(System.currentTimeMillis())+" сек."+(current.paused()?" / пауза":"");
                boolean changed=switch(action){case "cancel"->cancel(town);case "restart"->restart(town);case "extend"->extend(town,minutes);case "pause"->pause(town,true);case "resume"->pause(town,false);default->false;};
                if(!changed)throw new IllegalArgumentException("Действие уже недоступно; обновите меню");
                return town.getName()+": "+action+(action.equals("cancel")?" — без поражения и штрафов":"");
            }));
        }
        return java.util.List.copyOf(targets);
    } }
    public boolean cancel(Town town) { try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) {
        ActiveEvent event = town == null ? null : active(town.getUUID()); if(event == null) return false;
        repository.replace(event, null, System.currentTimeMillis());
        clearRuntime(event);
        for(Player player : onlineResidents(town)) player.sendMessage("§eСобытие отменено администратором. Поражение и штрафы не начислены.");
        plugin.getLogger().info("Нейтральная отмена: " + town.getName() + " / " + event.eventId());
        return true;
    } }
    public boolean pause(Town town, boolean pause) { try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) {
        ActiveEvent old = town == null ? null : active(town.getUUID());
        if(old == null || old.paused() == pause) return false;
        long now = System.currentTimeMillis();
        ActiveEvent next = old.reschedule(pause ? old.endsAt() : Math.addExact(old.endsAt(), Math.max(0, now-old.pausedAt())), pause ? now : 0);
        replaceRuntime(old, next); return true;
    } }
    /** Restart keeps contributions and defeated raid enemies; only the timer is restarted. */
    public boolean restart(Town town) { try (var suiteGuard12 = ru.neverland.core.SuiteState.guard()) {
        ActiveEvent old = town == null ? null : active(town.getUUID()); EventDefinition definition = definition(old);
        if(old == null || definition == null) return false;
        ActiveEvent next = old.reschedule(Math.addExact(System.currentTimeMillis(), definition.durationSeconds()*1000L), 0);
        next.lastRaidWave(0); replaceRuntime(old, next); return true;
    } }
    public boolean extend(Town town, long minutes) { try (var suiteGuard13 = ru.neverland.core.SuiteState.guard()) {
        if(minutes < 1 || minutes > 10080) throw new IllegalArgumentException("Продление: от 1 до 10080 минут");
        ActiveEvent old = town == null ? null : active(town.getUUID()); if(old == null) return false;
        repository.replace(old, old.reschedule(Math.addExact(old.endsAt(), minutes*60_000L), old.pausedAt()), 0);
        return true;
    } }
    private void replaceRuntime(ActiveEvent old, ActiveEvent next) {
        if(old.raid() != null) {
            var yaml = new org.bukkit.configuration.file.YamlConfiguration(); old.raid().save(yaml);
            next.raid(ru.neverland.mintevents.model.RaidState.load(yaml));
        }
        repository.replace(old, next, 0); clearRuntime(old);
    }
    private void clearRuntime(ActiveEvent event) {
        cleanupRaidMobs(event.townId()); fires.finish(event.townId());
        raidFailureWarnings.remove(event.townId()); raidSpawnAttempts.remove(event.townId());
        BossBar bar = bars.remove(event.townId()); if(bar != null) bar.removeAll();
    }

    public boolean resolve(Town town, boolean success) { try (var suiteGuard14 = ru.neverland.core.SuiteState.guard()) {
        if (town == null) return false;
        ActiveEvent active = repository.active(town.getUUID());
        if (active == null) return false;
        resolveInternal(town, active, success);
        return true;
    } }

    private void resolveInternal(Town town, ActiveEvent active, boolean success) {
        EventDefinition definition = registry.get(active.eventId());
        long now = System.currentTimeMillis();
        repository.complete(active, success,
                plugin.getConfig().getInt("runtime.history-limit-per-town", 20), now);
        // Persist terminal state and its reputation receipt before external completion effects.
        repository.save();repository.flushReputation();
        cleanupRaidMobs(active.townId());
        fires.finish(active.townId());
        int repairs = fires.damage(active.townId()).size();
        if (repairs > 0) for (Player player : onlineResidents(town)) player.sendMessage("§eПосле пожара нужно восстановить блоков: §f" + repairs + "§e. Откройте §f/t events repairs");
        raidFailureWarnings.remove(active.townId());
        raidSpawnAttempts.remove(active.townId());
        BossBar bar = bars.remove(active.townId());
        if (bar != null) bar.removeAll();
        if (definition != null) {
            announce(town, success ? "event-success-town" : "event-failed-town", definition, active);
            runCommands(town, definition, active, success ? definition.successCommands() : definition.failureCommands());
        }
        saveNow();
    }

    public int contribute(Town town, int points) { try (var suiteGuard15 = ru.neverland.core.SuiteState.guard()) {
        ActiveEvent event = town == null ? null : repository.active(town.getUUID());
        if (event == null || event.paused()) return -1;
        int value = event.addProgress(points);
        repository.changed();
        EventDefinition definition = registry.get(event.eventId());
        if (definition != null) updateBossBar(town, definition, event);
        if (event.completed()) {
            resolveInternal(town, event, true);
        } else saveNow();
        return value;
    } }

    public void creditRaidKill(LivingEntity entity) { try (var suiteGuard16 = ru.neverland.core.SuiteState.guard()) {
        String owner = raidOwner(entity);
        if (owner == null) return;
        entity.getPersistentDataContainer().remove(raidMobKey);
        if (legacyRaidMobKey != null) entity.getPersistentDataContainer().remove(legacyRaidMobKey);
        UUID townId;
        try { townId = UUID.fromString(owner); } catch (IllegalArgumentException ignored) { return; }
        ActiveEvent event = repository.active(townId);
        EventDefinition definition = definition(event);
        if (event == null || event.paused() || event.raid() == null || definition == null || definition.mode() != EventMode.RAID
                || !event.raid().generation().toString().equals(entity.getPersistentDataContainer().get(raidGenerationKey, PersistentDataType.STRING))) return;
        // Calculate before removing the final enemy: finished() becomes true after the last death.
        Player killer = entity.getKiller();
        int points = RaidKillCredit.points(event, definition.mode(), killer != null,
                entity.getPersistentDataContainer().getOrDefault(raidPointsKey, PersistentDataType.INTEGER, 0), System.currentTimeMillis());
        if (event.raid().died(entity.getUniqueId()) == null) return;
        if (event.raid().clear()) event.lastRaidWave(System.currentTimeMillis());
        repository.changed();
        Town town = town(townId);
        if (points > 0 && town != null) {
            int progress = event.addProgress(points);
            messages.send(killer, "raid-kill-progress", Map.of("town", town.getName(), "points", points,
                    "progress", progress, "goal", event.goal(), "wave", event.raid().wave()));
        }
        if (town != null && event.completed()) resolveInternal(town, event, true);
        else saveNow();
    } }

    /** Removes stale raid bodies even if their chunk was unloaded during shutdown/cleanup. */
    public void validateLoadedRaidEntity(Entity entity) { try (var suiteGuard17 = ru.neverland.core.SuiteState.guard()) {
        String owner = raidOwner(entity);
        if (owner == null) return;
        ActiveEvent active = null;
        try { active = repository.active(UUID.fromString(owner)); } catch (IllegalArgumentException ignored) { }
        if (active == null || active.paused() || active.raid() == null
                || !active.raid().generation().toString().equals(entity.getPersistentDataContainer().get(raidGenerationKey, PersistentDataType.STRING))
                || !active.raid().owns(entity.getUniqueId())) entity.remove();
    } }

    private void tick() {
        repository.flushReputation();
        long now = System.currentTimeMillis();
        long refresh = Math.max(1, plugin.getConfig().getLong("runtime.effects-refresh-seconds", 8)) * 1000;
        boolean applyEffects = now - effectsTick >= refresh;
        if (applyEffects) effectsTick = now;
        for (ActiveEvent active : repository.active().values()) {
            Town town = town(active.townId());
            EventDefinition definition = registry.get(active.eventId());
            if (town == null || definition == null || active.paused()) continue;
            active.protection(development.protection(active.townId(), definition));
            if (active.completed()) {
                resolveInternal(town, active, true);
            } else if (now >= active.endsAt()) {
                resolveInternal(town, active, false);
            } else {
                updateBossBar(town, definition, active);
                if (definition.mode() == EventMode.FIRE) fires.tick(town, active, applyEffects, new ArrayList<>(onlineResidents(town)));
                if (applyEffects) applyGameplay(town, definition, active, now);
            }
        }
    }

    private void applyGameplay(Town town, EventDefinition definition, ActiveEvent active, long now) {
        double severity = 1.0 - active.protection();
        if (definition.mode() == EventMode.RAID) {
            Location anchor = raidAnchor(town);
            if (anchor != null) maybeRaidWave(town, anchor, active, severity, now, false);
            return;
        }
        for (Player player : onlineResidents(town)) ru.neverland.core.SuiteScheduler.entity(plugin,player,()->{
            Town current = towny.townAt(player.getLocation());
            if (repository.active(town.getUUID())!=active || active.paused() || current == null || !current.getUUID().equals(town.getUUID())) return;
            int duration = (int) Math.max(60, plugin.getConfig().getLong("runtime.effects-refresh-seconds", 8) * 30);
            switch (definition.mode()) {
                case EPIDEMIC -> {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, duration,
                            severity >= 0.65 ? 1 : 0, true, false, true));
                    if (severity >= 0.45) player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                            duration, 0, true, false, true));
                }
                case FIRE -> {
                    if (fires.near(player)) player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, duration,
                            severity >= 0.7 ? 1 : 0, true, false, true));
                }
                case FESTIVAL -> {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, duration, 0, true, true, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 0, true, true, true));
                }
                case RAID -> { }
                case DROUGHT, FLOOD -> { }
            }
        });
    }

    private int maybeRaidWave(Town town, Location anchor, ActiveEvent event, double severity, long now,
                              boolean ignoreInterval) {
        if (!ru.neverland.core.SuiteScheduler.owns(anchor)) { ru.neverland.core.SuiteScheduler.region(plugin,anchor,()->{ if(repository.active(event.townId())==event&&!event.paused())maybeRaidWave(town,anchor,event,severity,System.currentTimeMillis(),ignoreInterval); });return 0;}
        var state = event.raid();
        if (state == null || state.finished()) return 0;
        long interval = Math.max(1, plugin.getConfig().getLong("gameplay.raid-wave-interval-seconds", 60)) * 1000;
        if (state.clear()) {
            if (!ignoreInterval && state.wave() > 0 && now - event.lastRaidWave() < interval) return 0;
            state.begin(state.waveDefinition(state.wave() + 1, raidCatalog).mobs());
            repository.changed();
            if (plugin.getConfig().getBoolean("gameplay.raid-announce-wave", true)) {
                String text = messages.formatText(messages.raw("raid-wave-town"), Map.of("town", town.getName(),
                        "count", state.remaining(), "wave", state.wave(), "waves", 10), true);
                for (Player player : onlineResidents(town)) player.sendMessage(text);
            }
        }
        int cap = Math.max(1, plugin.getConfig().getInt("gameplay.raid-max-alive-mobs-per-town", 18));
        if (state.next() == null || state.aliveCount() >= cap) return 0;
        long retry = Math.max(1, plugin.getConfig().getLong("gameplay.raid-failed-retry-seconds", 10)) * 1000;
        if (now - raidSpawnAttempts.getOrDefault(event.townId(), 0L) < retry) return 0;
        raidSpawnAttempts.put(event.townId(), now);
        Location location = findRaidSpawn(town, anchor, false);
        if (location == null && plugin.getConfig().getBoolean("gameplay.raid-allow-liquid-fallback", true)) location = findRaidSpawn(town, anchor, true);
        int spawned = 0;
        if (location != null) while (state.next() != null && state.aliveCount() < cap) {
            if (!spawnRaidMob(town, location, event, raidCatalog.mob(state.next()))) break;
            spawned++;
        }
        if (spawned == 0) logRaidFailure(town, location, 1, 0, retry, now);
        else raidFailureWarnings.remove(town.getUUID());
        repository.changed();
        saveNow();
        return spawned;
    }

    private boolean spawnRaidMob(Town town, Location location, ActiveEvent event, RaidCatalog.MobSpec spec) {
        if (spec == null) {
            plugin.getLogger().warning("Набег: моб удалён из raids.yml, но остался в активной волне: " + event.raid().next());
            return false;
        }
        Entity spawned = null;
        try {
            // Tag before Towny checks the spawn. Models are applied to this same already-tagged base entity.
            spawned = location.getWorld().spawnEntity(location, spec.type(), CreatureSpawnEvent.SpawnReason.CUSTOM, entity -> {
                entity.getPersistentDataContainer().set(raidMobKey, PersistentDataType.STRING, town.getUUID().toString());
                entity.getPersistentDataContainer().set(raidGenerationKey, PersistentDataType.STRING, event.raid().generation().toString());
                entity.getPersistentDataContainer().set(raidPointsKey, PersistentDataType.INTEGER, spec.points());
                entity.getPersistentDataContainer().set(raidRangedMultiplierKey, PersistentDataType.DOUBLE,
                        event.raid().waveDefinition(event.raid().wave(), raidCatalog).damageMultiplier() * (1 - event.protection() * .4));
                entity.setCustomName(ColorUtil.color("&#FF5E6C" + spec.name()));
                entity.setCustomNameVisible(false);
                entity.setPersistent(true);
                if (entity instanceof LivingEntity living) living.setRemoveWhenFarAway(false);
            });
            if (!(spawned instanceof LivingEntity living) || !living.isValid() || living.isDead()) {
                if (spawned != null) spawned.remove();
                return false;
            }
            if (blockedBody(living)) { living.remove(); return false; }
            customMobs.apply(living, spec, event.raid().waveDefinition(event.raid().wave(), raidCatalog), event.protection());
            if (blockedBody(living)) { living.remove(); return false; }
            if (living instanceof Mob mob) {
                Player target = nearestTownPlayer(town, location);
                if (target != null) mob.setTarget(target);
            }
            event.raid().spawned(living.getUniqueId());
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (spawned != null) spawned.remove();
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            plugin.getLogger().warning("Набег: " + spec.id() + " не создан: " + cause.getMessage());
            return false;
        }
    }

    public double raidRangedMultiplier(Entity entity) { try (var suiteGuard18 = ru.neverland.core.SuiteState.guard()) {
        return isRaidMob(entity) ? entity.getPersistentDataContainer().getOrDefault(raidRangedMultiplierKey, PersistentDataType.DOUBLE, 1.0) : 1.0;
    } }

    private boolean blockedBody(LivingEntity entity) {
        BoundingBox body = entity.getBoundingBox().clone().expand(-0.01);
        World world = entity.getWorld();
        // Use block shapes, not isSolid: slabs, fences and low roofs can trap a large/custom mob.
        for (int x = (int) Math.floor(body.getMinX()); x <= (int) Math.floor(body.getMaxX()); x++)
            for (int y = (int) Math.floor(body.getMinY()); y <= (int) Math.floor(body.getMaxY()); y++)
                for (int z = (int) Math.floor(body.getMinZ()); z <= (int) Math.floor(body.getMaxZ()); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    for (BoundingBox shape : block.getCollisionShape().getBoundingBoxes()) {
                        if (shape.clone().shift(x, y, z).overlaps(body)) return true;
                    }
                }
        return false;
    }

    private Location findRaidSpawn(Town town, Location anchor, boolean allowLiquidFloor) {
        World world = anchor.getWorld();
        if (world == null) return null;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int minRadius = Math.max(1, plugin.getConfig().getInt("gameplay.raid-spawn-min-radius", 4));
        int maxRadius = Math.max(minRadius, plugin.getConfig().getInt("gameplay.raid-spawn-max-radius", 18));
        int attempts = Math.max(8, plugin.getConfig().getInt("gameplay.raid-spawn-attempts", 32));
        for (int attempt = 0; attempt < attempts; attempt++) {
            double angle = random.nextDouble(Math.PI * 2);
            int radius = random.nextInt(minRadius, maxRadius + 1);
            int x = anchor.getBlockX() + (int) Math.round(Math.cos(angle) * radius);
            int z = anchor.getBlockZ() + (int) Math.round(Math.sin(angle) * radius);
            Location location = safeRaidLocation(world, x, z, town, allowLiquidFloor);
            if (location != null) return location;
        }

        List<TownBlock> blocks = new ArrayList<>(town.getTownBlocks());
        blocks.sort(Comparator.comparingDouble(block -> townBlockDistance(block, anchor)));
        int blockAttempts = Math.max(1, plugin.getConfig().getInt("gameplay.raid-townblock-attempts", 6));
        int maxBlocks = Math.max(1, plugin.getConfig().getInt("gameplay.raid-max-townblocks-scanned", 64));
        int scanned = 0;
        for (TownBlock townBlock : blocks) {
            if (scanned++ >= maxBlocks) break;
            World blockWorld = townBlock.getWorldCoord().getBukkitWorld();
            if (blockWorld == null || !blockWorld.equals(world)) continue;
            BoundingBox box = townBlock.getWorldCoord().getBoundingBox();
            int minX = (int) Math.floor(box.getMinX()) + 1;
            int maxX = (int) Math.ceil(box.getMaxX()) - 2;
            int minZ = (int) Math.floor(box.getMinZ()) + 1;
            int maxZ = (int) Math.ceil(box.getMaxZ()) - 2;
            for (RaidSpawnPlanner.Column probe : RaidSpawnPlanner.blockProbes(minX, maxX, minZ, maxZ)) {
                Location location = safeRaidLocation(world, probe.x(), probe.z(), town, allowLiquidFloor);
                if (location != null) return location;
            }
            for (int attempt = 0; attempt < blockAttempts; attempt++) {
                int x = maxX <= minX ? minX : random.nextInt(minX, maxX + 1);
                int z = maxZ <= minZ ? minZ : random.nextInt(minZ, maxZ + 1);
                Location location = safeRaidLocation(world, x, z, town, allowLiquidFloor);
                if (location != null) return location;
            }
        }
        return null;
    }

    private Location safeRaidLocation(World world, int x, int z, Town town, boolean allowLiquidFloor) {
        if (!ru.neverland.core.WorldTasks.ownsBox(new Location(world,x,0,z),2) || !belongsToTown(town, world, x, z)) return null;
        Location center = new Location(world, x + 0.5, world.getMinHeight() + 1, z + 0.5);
        if (!world.getWorldBorder().isInside(center)) return null;
        int scanDown = Math.max(4, plugin.getConfig().getInt("gameplay.raid-vertical-scan-down", 16));
        int scanUp = Math.max(2, plugin.getConfig().getInt("gameplay.raid-vertical-scan-up", 6));
        Set<Integer> heights = new HashSet<>();
        heights.add(world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES));
        heights.add(world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE));
        heights.add(world.getHighestBlockYAt(x, z, HeightMap.OCEAN_FLOOR));
        for (int highest : heights) {
            for (int y : RaidSpawnPlanner.verticalCandidates(highest, world.getMinHeight(),
                    world.getMaxHeight(), scanDown, scanUp)) {
                Block ground = world.getBlockAt(x, y - 1, z);
                Block feet = world.getBlockAt(x, y, z);
                Block head = world.getBlockAt(x, y + 1, z);
                boolean floor = (!ground.isLiquid() && ground.getType().isSolid())
                        || (allowLiquidFloor && ground.isLiquid());
                if (floor && feet.isPassable() && !feet.isLiquid() && head.isPassable() && !head.isLiquid()) {
                    return new Location(world, x + 0.5, y, z + 0.5);
                }
            }
        }
        return null;
    }

    private boolean belongsToTown(Town town, World world, int x, int z) {
        for (TownBlock block : town.getTownBlocks()) {
            World blockWorld = block.getWorldCoord().getBukkitWorld();
            if (blockWorld != null && blockWorld.equals(world)
                    && block.getWorldCoord().containsCoordinate(x, z)) return true;
        }
        return false;
    }

    private void logRaidFailure(Town town, Location spawnLocation, int rejected, int invalidTypes,
                                long retry, long now) {
        long interval = Math.max(10, plugin.getConfig().getLong("gameplay.raid-warning-interval-seconds", 60)) * 1000;
        long previous = raidFailureWarnings.getOrDefault(town.getUUID(), 0L);
        if (now - previous < interval) return;
        raidFailureWarnings.put(town.getUUID(), now);
        String reason;
        if (spawnLocation == null) {
            reason = "не найдена поверхность в принадлежащих городу участках";
        } else if (invalidTypes > 0 && rejected == 0) {
            reason = "в config.yml указаны неподдерживаемые типы мобов";
        } else {
            reason = "точка найдена, но создание мобов отклонено сервером, сложностью мира или защитным плагином";
        }
        plugin.getLogger().warning("Набег: город " + town.getName() + " — " + reason
                + "; следующая попытка через " + (retry / 1000) + " сек. Повтор предупреждения не чаще "
                + (interval / 1000) + " сек.");
    }

    private enum RaidSpawnResult { SPAWNED, REJECTED, INVALID_TYPE }

    private double townBlockDistance(TownBlock block, Location anchor) {
        BoundingBox box = block.getWorldCoord().getBoundingBox();
        double dx = (box.getMinX() + box.getMaxX()) * 0.5 - anchor.getX();
        double dz = (box.getMinZ() + box.getMaxZ()) * 0.5 - anchor.getZ();
        return dx * dx + dz * dz;
    }

    private Location raidAnchor(Town town) {
        for (Player player : onlineResidents(town)) {
            Location currentPosition=ru.neverland.core.WorldTasks.position(player);if(currentPosition==null)continue;
            Town current = towny.townAt(currentPosition);
            if (current != null && current.getUUID().equals(town.getUUID())) return currentPosition;
        }
        Location spawn = town.getSpawnOrNull();
        if (spawn != null && spawn.getWorld() != null) return spawn;
        TownBlock home = town.getHomeBlockOrNull();
        if (home == null && !town.getTownBlocks().isEmpty()) home = town.getTownBlocks().iterator().next();
        if (home == null) return null;
        BoundingBox box = home.getWorldCoord().getBoundingBox();
        World world = home.getWorldCoord().getBukkitWorld();
        return world == null ? null : new Location(world, (box.getMinX() + box.getMaxX()) * 0.5,
                world.getMinHeight() + 1,
                (box.getMinZ() + box.getMaxZ()) * 0.5);
    }

    private Player nearestTownPlayer(Town town, Location location) {
        Player nearest = null;
        double distance = Double.MAX_VALUE;
        for (Player player : onlineResidents(town)) {
            if (!ru.neverland.core.SuiteScheduler.owns(player) || !player.getWorld().equals(location.getWorld())) continue;
            double current = player.getLocation().distanceSquared(location);
            if (current > 4096) continue;
            if (current < distance) { distance = current; nearest = player; }
        }
        return nearest;
    }

    public int forceRaidWave(Town town) { try (var suiteGuard19 = ru.neverland.core.SuiteState.guard()) {
        ActiveEvent event = town == null ? null : repository.active(town.getUUID());
        EventDefinition definition = event == null ? null : registry.get(event.eventId());
        if (event == null || event.paused() || definition == null || definition.mode() != EventMode.RAID) return -1;
        Location anchor = raidAnchor(town);
        return anchor == null ? 0 : maybeRaidWave(town, anchor, event,
                1.0 - event.protection(), System.currentTimeMillis(), true);
    } }

    private int raidMobCount(UUID townId) {var event=repository.active(townId);return event==null||event.raid()==null?0:event.raid().aliveCount();}

    private void cleanupRaidMobs(UUID townId) {ru.neverland.core.WorldTasks.eachEntityCleanup(plugin,entity->{if(townId.toString().equals(raidOwner(entity)))entity.remove();});}

    @Override public double productionMultiplier(UUID townId,String building) { try (var suiteGuard20 = ru.neverland.core.SuiteState.guard()) {
        ru.neverland.core.ApiServices.primaryThread();
        if(townId==null||building==null)throw new IllegalArgumentException("Город и постройка обязательны");
        if(!building.equals("agrarian_complex"))return 1;
        ActiveEvent active=repository.active(townId);EventDefinition definition=definition(active);
        if(active==null||active.paused()||definition==null||active.endsAt()<=System.currentTimeMillis())return 1;
        double original=WeatherEconomy.production(definition.mode(),active.protection(),
                plugin.getConfig().getDouble("seasonal.drought-output-loss",.50),
                plugin.getConfig().getDouble("seasonal.flood-output-loss",.40));
        double factor=scenarios.effects(active.instanceId()).output();return factor==1?original:1-ru.neverland.mintevents.scenarios.ScenarioBook.loss(1-original,factor);
    } }

    private void randomCheck() {
        if (repository.active().size() >= Math.max(1, plugin.getConfig().getInt("scheduler.max-active-events", 3))) return;
        if (ThreadLocalRandom.current().nextDouble() > plugin.getConfig().getDouble("scheduler.chance-per-check", 0.35)) return;
        long cooldown = Math.max(0, plugin.getConfig().getLong("scheduler.town-cooldown-hours", 12)) * 3_600_000;
        boolean requireOnline = plugin.getConfig().getBoolean("scheduler.require-online-resident", true);
        long now = System.currentTimeMillis();
        List<Town> eligible = new ArrayList<>();
        for (Town town : towny.towns()) {
            if (repository.active(town.getUUID()) != null || shieldRemainingMillis(town.getUUID()) > 0) continue;
            if (now - repository.lastEventAt(town.getUUID()) < cooldown) continue;
            if (requireOnline && onlineResidents(town).isEmpty()) continue;
            eligible.add(town);
        }
        List<EventDefinition> definitions = new ArrayList<>(registry.all());
        if (eligible.isEmpty() || definitions.isEmpty()) return;
        Collections.shuffle(eligible);
        Town selected = eligible.get(0);
        try {
            double[] weights = new double[definitions.size()];
            for (int i=0;i<weights.length;i++) weights[i] = ru.neverland.core.SeasonsAccess.eventWeight(selected.getUUID(), definitions.get(i).mode().name());
            int choice = WeatherEconomy.choose(weights,ThreadLocalRandom.current().nextDouble());
            if(choice>=0)startEvent(selected,definitions.get(choice));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            if(now-seasonalWarning>=60000){seasonalWarning=now;plugin.getLogger().warning("Выбор события отложен: календарь недоступен: "+e.getMessage());}
        }
    }

    private void updateBossBar(Town town, EventDefinition definition, ActiveEvent event) {
        if (!plugin.getConfig().getBoolean("runtime.bossbar", true)) return;
        BossBar bar = bars.computeIfAbsent(town.getUUID(), ignored -> Bukkit.createBossBar("",
                definition.bossBarColor(), BarStyle.SEGMENTED_10, new BarFlag[0]));
        bar.setColor(definition.bossBarColor());
        bar.setProgress(event.progressRatio());
        bar.setTitle(ColorUtil.color(definition.name() + " &8— &f" + (event.raid() == null
                ? event.progress() + "/" + event.goal()
                : "Волна " + event.raid().wave() + "/10 · врагов: " + event.raid().remaining() + " · очков: " + event.progress())));
        bar.removeAll();
        for (Player player : onlineResidents(town)) bar.addPlayer(player);
    }

    private void announce(Town town, String key, EventDefinition definition, ActiveEvent active) {
        if (!plugin.getConfig().getBoolean("runtime.announce-to-town", true)) return;
        Map<String, Object> placeholders = placeholders(town, definition, active);
        String text = messages.formatText(messages.raw(key), placeholders, false);
        for (Player player : onlineResidents(town)) player.sendMessage(text);
    }

    private void runCommands(Town town, EventDefinition definition, ActiveEvent event, List<String> commands) {
        Map<String, Object> placeholders = placeholders(town, definition, event);
        var prepared=new java.util.ArrayList<String>();
        for (String raw : commands) {
            String command = raw;
            for (Map.Entry<String, Object> entry : placeholders.entrySet()) {
                command = command.replace("%" + entry.getKey() + "%", String.valueOf(entry.getValue()));
            }
            prepared.add(command);
        }
        ru.neverland.core.CommandOutbox.enqueue(plugin,"event:"+event.townId()+":"+event.eventId()+":"+event.startedAt(),prepared);
    }

    private Map<String, Object> placeholders(Town town, EventDefinition definition, ActiveEvent event) {
        return Map.of("town", town.getName(), "event", ColorUtil.strip(definition.name()),
                "description", definition.description(), "progress", event.progress(), "goal", event.goal(),
                "protection", Math.round(event.protection() * 100));
    }

    public Collection<Player> onlineResidents(Town town) { try (var suiteGuard21 = ru.neverland.core.SuiteState.guard()) {
        List<Player> result = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Town playerTown = towny.town(player);
            if (playerTown != null && playerTown.getUUID().equals(town.getUUID())) result.add(player);
        }
        return result;
    } }

    public Town town(UUID id) { try (var suiteGuard22 = ru.neverland.core.SuiteState.guard()) {
        for (Town town : towny.towns()) if (town.getUUID().equals(id)) return town;
        return null;
    } }

    public ActiveEvent active(UUID townId) { try (var suiteGuard23 = ru.neverland.core.SuiteState.guard()) { return repository.active(townId); } }
    public EventDefinition definition(ActiveEvent event) { try (var suiteGuard24 = ru.neverland.core.SuiteState.guard()) { return event == null ? null : registry.get(event.eventId()); } }
    @Override public long raidVictories(UUID townId) { try (var suiteGuard25 = ru.neverland.core.SuiteState.guard()) { ru.neverland.core.ApiServices.primaryThread(); return repository.raidVictories(townId); } }

    public List<HistoryEntry> history(UUID townId) { try (var suiteGuard26 = ru.neverland.core.SuiteState.guard()) { return repository.history(townId); } }
    public NamespacedKey raidMobKey() { try (var suiteGuard27 = ru.neverland.core.SuiteState.guard()) { return raidMobKey; } }
    public boolean isRaidMob(Entity entity) { try (var suiteGuard28 = ru.neverland.core.SuiteState.guard()) { return raidOwner(entity) != null; } }
    private String raidOwner(Entity entity) { if (entity == null) return null; String value = entity.getPersistentDataContainer().get(raidMobKey, PersistentDataType.STRING); return value != null || legacyRaidMobKey == null ? value : entity.getPersistentDataContainer().get(legacyRaidMobKey, PersistentDataType.STRING); }
    public EventRegistry registry() { try (var suiteGuard29 = ru.neverland.core.SuiteState.guard()) { return registry; } }
    public DevelopmentService development() { try (var suiteGuard30 = ru.neverland.core.SuiteState.guard()) { return development; } }
    public RaidScaling.Profile raidPreview(Town town) { try (var suiteGuard31 = ru.neverland.core.SuiteState.guard()) {
        if (!raidScaling.enabled()) return raidScaling.profile(Map.of(), false);
        var snapshot = development.completedBuildings(town.getUUID());
        return raidScaling.profile(snapshot.levels(), snapshot.available());
    } }
    public FireService fires() { try (var suiteGuard32 = ru.neverland.core.SuiteState.guard()) { return fires; } }
    public boolean fireActive(UUID townId) { try (var suiteGuard33 = ru.neverland.core.SuiteState.guard()) {
        EventDefinition definition = definition(active(townId));
        return definition != null && !active(townId).paused() && definition.mode() == EventMode.FIRE;
    } }
    public boolean fireUnfinished(UUID townId) { try (var suiteGuard34 = ru.neverland.core.SuiteState.guard()) {
        EventDefinition definition = definition(active(townId));
        return definition != null && definition.mode() == EventMode.FIRE;
    } }
    @Override public boolean requiresRepair(UUID world, int x, int y, int z) { try (var suiteGuard35 = ru.neverland.core.SuiteState.guard()) {
        return !fires.repository().writable() || fires.requiresRepair(world, x, y, z);
    } }
    @Override public boolean hasFireDamage(UUID town) { try (var suiteGuard36 = ru.neverland.core.SuiteState.guard()) {
        return !fires.repository().writable() || !fires.damage(town).isEmpty();
    } }
    @Override public boolean hasFireDamageInArea(UUID town, UUID world, int minX, int minZ, int maxX, int maxZ) { try (var suiteGuard37 = ru.neverland.core.SuiteState.guard()) {
        if (!fires.repository().writable()) return true;
        int size=com.palmergames.bukkit.towny.TownySettings.getTownBlockSize();
        return fires.damage(town).stream().anyMatch(d->d.world().equals(world)
            && Math.floorDiv(d.x(),size)>=Math.floorDiv(minX,size) && Math.floorDiv(d.x(),size)<=Math.floorDiv(maxX,size)
            && Math.floorDiv(d.z(),size)>=Math.floorDiv(minZ,size) && Math.floorDiv(d.z(),size)<=Math.floorDiv(maxZ,size));
    } }
    public void extinguish(Player player, Location water) { try (var suiteGuard38 = ru.neverland.core.SuiteState.guard()) {
        if (!player.hasPermission("mintevents.contribute")) return;
        Town town = towny.town(player);
        if (town == null || !fireActive(town.getUUID())) return;
        int count = fires.extinguish(player, water);
        if (count > 0) {
            int points = count * Math.max(1, Math.min(100, plugin.getConfig().getInt("gameplay.fire.extinguish-points", 8)));
            contribute(town, points);
            player.sendMessage("§aПотушено очагов: §f" + count + " §7· §a+" + points + " очков защиты");
        }
    } }

    public void reloadRuntime() { try (var suiteGuard39 = ru.neverland.core.SuiteState.guard()) {
        fires.reload();
        var nextCatalog = new RaidCatalog(plugin);
        var nextScaling = new RaidScaling(plugin);
        raidCatalog = nextCatalog;
        raidScaling = nextScaling;
        scenarios.reload();
        development.clearCache();
        start();
    } }

    private void saveNow() {
        if (plugin.getConfig().getBoolean("runtime.save-immediately", true)) repository.save();
    }

    @Override
    public Optional<EventSnapshot> activeEvent(UUID townId) { try (var suiteGuard40 = ru.neverland.core.SuiteState.guard()) {ru.neverland.core.ApiServices.primaryThread();
        ActiveEvent event = repository.active(townId);
        EventDefinition definition = definition(event);
        if (event == null || event.paused() || definition == null) return Optional.empty();
        return Optional.of(new EventSnapshot(townId, event.eventId(), ColorUtil.strip(definition.name()),
                event.endsAt(), event.raid() == null ? event.progress() : event.raid().wave() - (event.raid().clear() ? 0 : 1),
                event.raid() == null ? event.goal() : 10, event.protection()));
    } }

    @Override
    public boolean addProgress(UUID townId, int points, String source) { try (var suiteGuard41 = ru.neverland.core.SuiteState.guard()) {ru.neverland.core.ApiServices.primaryThread();
        Town town = town(townId);
        return town != null && points > 0 && contribute(town, points) >= 0;
    } }

    @Override
    public double protection(UUID townId) { try (var suiteGuard42 = ru.neverland.core.SuiteState.guard()) {ru.neverland.core.ApiServices.primaryThread();
        ActiveEvent event = repository.active(townId);
        return event == null || event.paused() ? 0 : event.protection();
    } }
}
