package ru.neverland.runtime;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.permissions.PermissibleBase;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;
import com.palmergames.bukkit.towny.*;
import com.palmergames.bukkit.towny.object.*;
import ru.neverland.townyquests.service.QuestService;
import ru.neverland.townyquests.model.*;
import ru.neverland.townyquests.integration.CityBridge;
import ru.neverland.townyquests.api.TownyQuestsApi;
import ru.neverland.townybuilds.api.*;
import ru.neverland.townydistricts.api.TownyDistrictsApi;
import ru.neverland.townydistricts.model.*;
import ru.neverland.townydistricts.model.District;
import ru.neverland.townyresources.api.*;
import ru.neverland.townyresources.model.TownState;
import ru.neverland.townypolicies.service.PoliciesService;

/** Disposable native integration coverage. Public provider responses are controlled test fixtures. */
public final class QuestsRuntimeProbe extends JavaPlugin {
    static final UUID T = UUID.nameUUIDFromBytes("quests-city-038".getBytes()), M = UUID.nameUUIDFromBytes("quests-mayor-038".getBytes());
    Town town; World world; QuestService quests; PoliciesService policies; int checks; boolean paused, stale; double water = 1;
    List<District> districts = new ArrayList<>(); Map<String, Integer> levels = new HashMap<>(); Path proof; Actor actor;
    JavaPlugin plugin(String name) { return (JavaPlugin) Objects.requireNonNull(Bukkit.getPluginManager().getPlugin(name)); }
    void check(boolean value, String label) throws Exception {
        if (!value) throw new AssertionError(label); checks++; Files.writeString(proof.resolve("checks.txt"), label + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
    interface Step { void run() throws Exception; }
    void rejected(Step step, String label) throws Exception { try { step.run(); } catch (Exception expected) { check(true, label); return; } throw new AssertionError(label); }
    @Override public void onEnable() {
        if (!Boolean.getBoolean("neverland.runtimeProbe") || !Files.isRegularFile(Path.of("ALLOW_DISPOSABLE_RELIABILITY_PROBE")) || !"127.0.0.1".equals(getServer().getIp())) {
            Bukkit.getPluginManager().disablePlugin(this); return;
        }
        Bukkit.getScheduler().runTaskLater(this, () -> guard(this::run), 20);
    }
    void guard(Step step) { try { step.run(); } catch (Throwable ex) {
        getLogger().log(java.util.logging.Level.SEVERE, "QUESTS PROBE FAILED", ex);
        try { Files.createDirectories(getDataFolder().toPath()); Files.writeString(getDataFolder().toPath().resolve("failed.txt"), ex.toString()); } catch (Exception ignored) {}
        Bukkit.shutdown();
    } }
    void run() throws Exception {
        proof = getDataFolder().toPath(); Files.createDirectories(proof); world = Bukkit.getWorlds().getFirst();
        quests = (QuestService) Bukkit.getServicesManager().load(TownyQuestsApi.class); policies = (PoliciesService) Bukkit.getServicesManager().load(ru.neverland.townypolicies.api.TownyPoliciesApi.class);
        check(plugin("NeverLandTownyQuests").isEnabled() && plugin("NeverLandTownyPolicies").isEnabled() && plugin("NeverLandTownyControl").isEnabled(), "Quests, Policies and Control load together");
        if (Files.exists(proof.resolve("first-passed.txt"))) { restart(); return; }
        var universe = TownyUniverse.getInstance(); universe.newTownInternal("QuestCity", T); town = TownyAPI.getInstance().getTown(T);
        var mayor = universe.getDataSource().newResident("QuestMayor", M); mayor.setTown(town); town.setMayor(mayor); mayor.save();
        for (int x = 0; x <= 5; x++) { var block = new TownBlock(x, 0, universe.getWorld(world.getName())); universe.addTownBlock(block); block.setTown(town); if (x == 0) town.setHomeBlock(block); block.save(); }
        town.setSpawn(new Location(world, 8, 90, 8)); town.save();
        actor = new Actor(M); policies.refresh(); installProviders();
        check(!quests.unlocked(T, "sanitation_reform"), "new city has no unlocked reform");
        rejected(() -> policies.choose(actor.player, T, "sanitation", "sanitary", 0, policies.settings(), false), "mayor cannot select unearned reform");
        rejected(() -> quests.begin(actor.player, "public_health"), "follow-up requires completed sanitation");
        var command = Bukkit.getPluginCommand("townyquests"); command.execute(actor.player, "townyquests", new String[0]);
        check(actor.top.getSize() == 54, "native project menu opens for resident");
        var click = new InventoryClickEvent(actor.view, InventoryType.SlotType.CONTAINER, 0, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        Bukkit.getPluginManager().callEvent(click); check(click.isCancelled(), "quest menu prevents inventory extraction");
        quests.begin(actor.player, "sanitation"); quests.tick(); check(quests.state(T).get("sanitation").stage() == 1, "real Builds contract completes aqueduct stage");
        var stage = quests.state(T).get("sanitation").definition().stages().get(1); var bridge = new CityBridge();
        check(bridge.observe(town, stage, 180, System.currentTimeMillis()).value() == 5, "real API records supply five connected Towny districts");
        var removed = districts.removeLast(); check(bridge.observe(town, stage, 180, System.currentTimeMillis()).value() == 4, "four districts cannot satisfy five"); districts.add(removed);
        districts.set(4, new District(T, "d5", "Дальний", DistrictType.RESIDENTIAL, Set.of(new Cell(world.getUID(), 7, 0))));
        check(bridge.observe(town, stage, 180, System.currentTimeMillis()).value() == 4, "unclaimed disconnected district is excluded"); districts.set(4, removed);
        water = .5; check(bridge.observe(town, stage, 180, System.currentTimeMillis()).value() == 0, "real resource shortage prevents supply"); water = 1;
        paused = true; check(!bridge.observe(town, stage, 180, System.currentTimeMillis()).ready(), "paused resource provider freezes observation"); paused = false;
        stale = true; check(!bridge.observe(town, stage, 180, System.currentTimeMillis()).ready(), "stale resource cycle freezes observation"); stale = false;
        levels.put("aqueduct", 0); check(bridge.observe(town, stage, 180, System.currentTimeMillis()).value() == 0, "inactive aqueduct supplies no districts"); levels.put("aqueduct", 1);
        quests.control(T, "sanitation", "pause"); quests.tick(); check(quests.state(T).get("sanitation").heldSeconds() == 0, "paused project earns no time");
        quests.control(T, "sanitation", "resume"); quests.tick(); check(quests.state(T).get("sanitation").heldSeconds() == 0, "first resumed observation has no offline credit");
        Bukkit.getScheduler().runTaskLater(this, () -> guard(this::finishWater), 100);
    }
    void finishWater() throws Exception {
        check(quests.state(T).get("sanitation").stage() == 2, "scheduled observations complete water hold online");
        check(!quests.unlocked(T, "sanitation_reform"), "water alone does not unlock reform before baths");
        quests.control(T, "sanitation", "cancel"); check(quests.state(T).get("sanitation").stage() == 2, "native cancellation retains completed city stages");
        quests.begin(actor.player, "sanitation"); levels.put("baths", 1); quests.tick();
        check(quests.unlocked(T, "sanitation_reform"), "final native stage durably unlocks sanitation");
        String before = Files.readString(plugin("NeverLandTownyQuests").getDataFolder().toPath().resolve("quests-data.yml")); quests.tick();
        check(Files.readString(plugin("NeverLandTownyQuests").getDataFolder().toPath().resolve("quests-data.yml")).equals(before), "repeat completion performs no duplicate state write");
        rejected(() -> quests.begin(actor.player, "sanitation"), "finished chain cannot be replayed");
        policies.choose(actor.player, T, "sanitation", "sanitary", 0, policies.settings(), false);
        check(policies.effect(T, "happiness") == 5 && policies.upkeepMultiplier(T, "baths") == 1.1, "native completed project enables matched policy bonus and upkeep");
        check(!quests.unlocked(UUID.randomUUID(), "sanitation_reform"), "other town receives no unlock");
        quests.begin(actor.player, "public_health"); quests.tick(); quests.tick();
        check(quests.state(T).get("public_health").stage() == 2, "second chain starts only after first and advances sequentially");
        quests.control(T, "public_health", "pause"); quests.control(T, "public_health", "restart");
        check(quests.state(T).get("public_health").heldSeconds() == 0 && quests.state(T).get("public_health").status() == CityProject.Status.PAUSED, "native restart retains pause and completed stages");
        var adapter = Bukkit.getPluginCommand("townyquests").getExecutor();
        var targets = (List<?>) adapter.getClass().getMethod("adminMenuTargets", org.bukkit.command.CommandSender.class).invoke(adapter, Bukkit.getConsoleSender());
        check(targets.size() == 1, "common administrative menu discovers only unfinished city project");
        Bukkit.getPluginManager().disablePlugin(plugin("NeverLandTownyQuests"));
        check(policies.effect(T, "happiness") == 0 && policies.upkeepMultiplier(T, "baths") == 1, "disabled Quests removes policy benefit and cost together");
        Bukkit.getPluginCommand("nltmodules").execute(Bukkit.getConsoleSender(), "nltmodules", new String[]{"enable", "Quests"});
        check(!plugin("NeverLandTownyQuests").isEnabled(), "module enable waits for a clean JVM restart");
        Files.writeString(proof.resolve("first-passed.txt"), "checks=" + checks + "\n"); Bukkit.shutdown();
    }
    void restart() throws Exception {
        check(quests.unlocked(T, "sanitation_reform"), "completed unlock survives a new JVM");
        var active = quests.state(T).get("public_health");
        check(active.stage() == 2 && active.heldSeconds() == 0 && active.status() == CityProject.Status.PAUSED, "new JVM preserves paused chain without downtime credit");
        policies.refresh(); check(policies.effect(T, "happiness") == 5 && policies.upkeepMultiplier(T, "baths") == 1.1, "persisted policy resumes after Quests provider returns");
        check(Bukkit.getServicesManager().load(TownyQuestsApi.class).quests(T).size() == 2, "public API exposes both persisted city projects");
        Files.writeString(proof.resolve("restart-passed.txt"), "checks=" + checks + "\n"); Bukkit.shutdown();
    }
    @SuppressWarnings("unchecked") <T> void provider(Class<T> type, InvocationHandler handler) {
        T original = Bukkit.getServicesManager().load(type);
        T proxy = (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, a) -> {
            if (m.getName().equals("apiVersion") || m.getName().equals("capabilities")) return m.invoke(original, a);
            Object result = handler.invoke(p, m, a); return result == UNHANDLED ? m.invoke(original, a) : result;
        });
        Bukkit.getServicesManager().register(type, proxy, this, ServicePriority.Highest);
    }
    static final Object UNHANDLED = new Object();
    void installProviders() {
        for (String id : List.of("aqueduct", "water_tower", "reservoir", "pumping_station", "sewer", "infirmary")) levels.put(id, 1);
        for (int i = 1; i <= 5; i++) districts.add(new District(T, "d" + i, "Район " + i, DistrictType.RESIDENTIAL, Set.of(new Cell(world.getUID(), i, 0))));
        provider(TownyBuildsApi.class, (p, m, a) -> {
            if (a == null || a.length == 0 || !T.equals(a[0])) return UNHANDLED;
            return switch (m.getName()) {
                case "operationalLevel" -> levels.getOrDefault(a[1], 0);
                case "waterNetworkActive" -> true;
                case "buildingFootprints" -> { Map<String, BuildingFootprint> map = new HashMap<>(); levels.forEach((id, level) -> map.put(id, new BuildingFootprint(world.getUID(), 1, 1, 5, 5, level, 1))); yield map; }
                default -> UNHANDLED;
            };
        });
        provider(TownyDistrictsApi.class, (p, m, a) -> m.getName().equals("districts") && T.equals(a[0]) ? List.copyOf(districts) : UNHANDLED);
        provider(TownyResourcesApi.class, (p, m, a) -> {
            if (!m.getName().equals("resources") || !T.equals(a[0])) return UNHANDLED;
            long now = System.currentTimeMillis();
            var state = new TownState(Map.of(), Map.of(), Set.of(), Map.of(), 1, stale ? now - 1000000 : now - 1000, Map.of(), Map.of(), 1, water);
            return Optional.of(new ResourceSnapshot(T, "QuestCity", 20, true, paused, "fixture", now + 60000, state,
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), 1, water));
        });
    }
    final class Actor {
        final Player player; PermissibleBase permissions; Inventory top = Bukkit.createInventory(null, 9), bottom = Bukkit.createInventory(null, 36); InventoryView view;
        Actor(UUID id) {
            player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (p, m, a) -> switch (m.getName()) {
                case "getUniqueId" -> id; case "getName" -> "QuestMayor"; case "getServer" -> Bukkit.getServer(); case "getWorld" -> world;
                case "getLocation" -> new Location(world, 8, 90, 8); case "isOnline" -> true; case "isOp", "isDead" -> false;
                case "hasPermission", "isPermissionSet", "addAttachment", "removeAttachment", "recalculatePermissions", "getEffectivePermissions" -> m.invoke(permissions, a);
                case "getOpenInventory" -> view; case "openInventory" -> { top = (Inventory) a[0]; yield view; } case "closeInventory" -> { top = Bukkit.createInventory(null, 9); yield null; }
                case "hashCode" -> id.hashCode(); case "equals" -> p == a[0]; case "toString" -> "Quest fixture mayor"; default -> null;
            });
            permissions = new PermissibleBase(player);
            view = (InventoryView) Proxy.newProxyInstance(InventoryView.class.getClassLoader(), new Class<?>[]{InventoryView.class}, (p, m, a) -> switch (m.getName()) {
                case "getTopInventory" -> top; case "getBottomInventory" -> bottom; case "getPlayer" -> player; case "getType" -> InventoryType.CHEST;
                case "getCursor" -> new ItemStack(Material.AIR); case "countSlots" -> top.getSize() + 36; case "getTitle", "getOriginalTitle" -> "Fixture";
                case "getSlotType" -> InventoryType.SlotType.CONTAINER; case "convertSlot" -> a[0]; case "getInventory" -> (Integer) a[0] < top.getSize() ? top : bottom; default -> null;
            });
        }
    }
}
