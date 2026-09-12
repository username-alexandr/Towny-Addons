package ru.neverland.runtime;

import com.mojang.authlib.GameProfile;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.logging.Level;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.spigotmc.SpigotConfig;
import ru.neverland.core.ApiServices;
import ru.neverland.core.AtomicFiles;
import ru.neverland.core.Diagnostics;
import ru.neverland.core.EffectJournal;
import ru.neverland.core.PlayerSaveReceipt;
import ru.neverland.mintexpeditions.MintTownyExpeditions;
import ru.neverland.mintexpeditions.service.ExpeditionRepository;
import ru.neverland.mintexpeditions.service.ExpeditionService;
import ru.neverland.mintexpeditions.service.RewardBatch;
import ru.neverland.minttrade.integration.BuildBridge;
import ru.neverland.minttrade.model.Caravan;
import ru.neverland.minttrade.model.CaravanStatus;
import ru.neverland.minttrade.service.TradeService;
import ru.neverland.townybuilds.api.BuildingStorageApi;
import ru.neverland.townybuilds.api.WarehouseApi;
import ru.neverland.townybuilds.data.DataStore;
import ru.neverland.townybuilds.storage.StockMath;

public final class ReliabilityRuntimeProbe extends JavaPlugin {
   // Journals are internal to each add-on's loader; the fixture must not link their shared implementation types.
   private static Journal journal(Object repository) throws Exception {
      return new Journal(repository.getClass().getMethod("effects").invoke(repository));
   }
   private interface Effect { boolean apply() throws Exception; }
   private record Journal(Object target) {
      Object call(String name,Class<?>[] signature,Object... args) throws Exception {
         try { return target.getClass().getMethod(name,signature).invoke(target,args); }
         catch (InvocationTargetException ex) { if(ex.getCause() instanceof Exception e)throw e; if(ex.getCause() instanceof Error e)throw e;throw ex; }
      }
      String state(UUID id) throws Exception { return ((Enum<?>)call("state",new Class<?>[]{UUID.class},id)).name(); }
      void resolve(UUID id,boolean applied) throws Exception { call("resolve",new Class<?>[]{UUID.class,boolean.class},id,applied); }
      boolean execute(UUID id,String description,Effect effect) throws Exception {
         Class<?> action=Class.forName(target.getClass().getName()+"$Action",true,target.getClass().getClassLoader());
         Object callback=Proxy.newProxyInstance(action.getClassLoader(),new Class<?>[]{action},(p,m,a)->effect.apply());
         return Boolean.TRUE.equals(call("execute",new Class<?>[]{UUID.class,String.class,action},id,description,callback));
      }
   }

   private final UUID seller = id("seller");
   private final UUID buyer = id("buyer");
   private final UUID toll = id("toll");
   private final UUID actor = id("actor");
   private final UUID caravanId = id("caravan");
   private final UUID rewardId = id("reward");
   private final UUID pendingReward = id("pending-reward");
   private DataStore data;
   private WarehouseApi warehouse;
   private TradeService trade;
   private ExpeditionService expedition;
   private Path folder;
   private int checks;
   private Player nativePlayer;
   private Player player;
   private World world;

   private static UUID id(String var0) {
      return UUID.nameUUIDFromBytes(("reliability-0221:" + var0).getBytes(StandardCharsets.UTF_8));
   }

   private void check(boolean var1, String var2) {
      this.checks++;
      if (!var1) {
         throw new AssertionError(var2);
      } else {
         this.getLogger().info("CHECK " + var2);

         try {
            Files.writeString(this.folder.resolve("checks.log"), var2 + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
         } catch (Exception var4) {
            throw new IllegalStateException(var4);
         }
      }
   }

   private void near(double var1, double var3, String var5) {
      this.check(Math.abs(var1 - var3) < 0.001, var5 + ": " + var3);
   }

   private void fail(ReliabilityRuntimeProbe.Action var1, String var2) throws Exception {
      try {
         var1.run();
      } catch (Exception var4) {
         this.check(true, var2);
         return;
      }

      throw new AssertionError(var2);
   }

   private static Object field(Object var0, String var1) throws Exception {
      Field var2 = var0.getClass().getDeclaredField(var1);
      var2.setAccessible(true);
      return var2.get(var0);
   }

   private int stock(UUID var1) {
      return StockMath.count(this.data.town(var1).storage(), new ItemStack(Material.IRON_INGOT));
   }

   private double balance(UUID var1) {
      return TownyAPI.getInstance().getTown(var1).getAccount().getHoldingBalance();
   }

   private int diamonds() {
      return Arrays.stream(this.player.getInventory().getStorageContents())
         .filter(Objects::nonNull)
         .filter(var0 -> var0.getType() == Material.DIAMOND)
         .mapToInt(ItemStack::getAmount)
         .sum();
   }

   public void onEnable() {
      if (Boolean.getBoolean("neverland.runtimeProbe")
         && Files.isRegularFile(Path.of("ALLOW_DISPOSABLE_RELIABILITY_PROBE"))
         && "127.0.0.1".equals(this.getServer().getIp())) {
         this.getServer().getScheduler().runTaskLater(this, this::run, 100L);
      } else {
         this.getServer().getPluginManager().disablePlugin(this);
      }
   }

   private void run() {
      try {
         this.folder = this.getDataFolder().toPath();
         Files.createDirectories(this.folder);
         this.world = (World)Bukkit.getWorlds().getFirst();
         this.check(
            Arrays.stream(Bukkit.getPluginManager().getPlugins()).filter(var0 -> var0.getName().startsWith("NeverLandTowny") && var0.isEnabled()).count()
               == 27L,
            "27 add-ons enabled"
         );
         this.data = (DataStore)field(Bukkit.getPluginManager().getPlugin("NeverLandTownyBuilds"), "dataStore");
         this.warehouse = (WarehouseApi)Bukkit.getServicesManager().load(WarehouseApi.class);
         this.trade = (TradeService)field(Bukkit.getPluginManager().getPlugin("NeverLandTownyTrade"), "trade");
         this.expedition = ((MintTownyExpeditions)Bukkit.getPluginManager().getPlugin("NeverLandTownyExpeditions")).service();
         this.metadata();
         this.actor();
         if (!Files.exists(this.folder.resolve("restart-ready"))) {
            this.first();
            Files.writeString(this.folder.resolve("first-passed.txt"), "PASS " + this.checks + " assertions\n");
            Files.writeString(this.folder.resolve("restart-ready"), "ready\n");
            this.getLogger().info("RELIABILITY_FIRST_PASS " + this.checks);
            this.getServer().getScheduler().runTaskLater(this, () -> Runtime.getRuntime().halt(23), 40L);
         } else {
            this.restart();
            Files.writeString(this.folder.resolve("restart-passed.txt"), "PASS " + this.checks + " assertions\n");
            this.getLogger().info("RELIABILITY_RESTART_PASS " + this.checks);
            this.getServer().getScheduler().runTaskLater(this, () -> Bukkit.shutdown(), 20L);
         }
      } catch (Throwable var4) {
         Throwable var1 = var4;
         this.getLogger().log(Level.SEVERE, "RELIABILITY_FAIL", var4);

         try {
            StringWriter var2 = new StringWriter();
            var1.printStackTrace(new PrintWriter(var2));
            Files.writeString(this.folder.resolve("failed.txt"), var2.toString());
         } catch (Exception var3) {
         }

         this.getServer().getScheduler().runTaskLater(this, () -> Bukkit.shutdown(), 20L);
      }
   }

   private void metadata() throws Exception {
      int var1 = 0;

      for (Class var3 : Bukkit.getServicesManager().getKnownServices()) {
         if (var3.getName().startsWith("ru.neverland.")) {
            Object var4 = Bukkit.getServicesManager().load(var3);
            if (var4 != null) {
               Set var5 = (Set)var3.getMethod("capabilities").invoke(var4);
               TreeSet var6 = new TreeSet();

               for (Method var10 : var3.getMethods()) {
                  if (!Modifier.isStatic(var10.getModifiers())
                     && var10.getDeclaringClass() != Object.class
                     && !var10.getDeclaringClass().getName().equals("ru.neverland.core.ApiContract")
                     && !Set.of("apiVersion", "capabilities").contains(var10.getName())) {
                     var6.add(var10.getName());
                  }
               }

               this.check(var5.equals(var6), "API capabilities: " + var3.getSimpleName());
               var1++;
            }
         }
      }

      this.check(var1 >= 20, "public services registered: " + var1);
      this.check(
         ApiServices.connect("NeverLandTownyBuilds", BuildingStorageApi.class.getName(), 1, "pickup", "unload", "acknowledge").ready(),
         "BuildingStorage mutations compatible"
      );
      this.check(
         ApiServices.connect(
               "NeverLandTownyResources", "ru.neverland.townyresources.api.TownyResourcesApi", 1, "reserveResources", "settleResources", "forgetReservation"
            )
            .ready(),
         "resource reservations compatible"
      );
      this.check(ApiServices.connect("MissingProbe", "missing.Api", 1).state() == ApiServices.State.NOT_INSTALLED, "absent dependency distinguished");
      this.check(
         ApiServices.connect("NeverLandTownyBuilds", WarehouseApi.class.getName(), 99, "transfer").state() == ApiServices.State.INCOMPATIBLE_API,
         "incompatible major distinguished"
      );
      this.check(ApiServices.connect("NeverLandTownyBuilds", WarehouseApi.class.getName(), 1, "transfer").ready(), "compatible service recovers");
   }

   private void actor() throws Exception {
      ServerPlayer var1 = new ServerPlayer(
         ((CraftServer)Bukkit.getServer()).getServer(),
         ((CraftWorld)this.world).getHandle(),
         new GameProfile(this.actor, "ReliabilityActor"),
         ClientInformation.createDefault()
      );
      this.nativePlayer = var1.getBukkitEntity();
      this.nativePlayer.loadData();
      this.player = (Player)Proxy.newProxyInstance(Player.class.getClassLoader(), new Class[]{Player.class}, (var1x, var2, var3) -> {
         if (var2.getName().equals("isOnline")) {
            return true;
         } else if (var2.getName().equals("sendMessage")) {
            return null;
         } else {
            try {
               return var2.invoke(this.nativePlayer, var3);
            } catch (InvocationTargetException var5) {
               throw var5.getCause();
            }
         }
      });
   }

   private Town town(String var1, UUID var2) throws Exception {
      TownyUniverse var3 = TownyUniverse.getInstance();
      var3.newTownInternal(var1, var2);
      Town var4 = TownyAPI.getInstance().getTown(var2);
      Resident var5 = var3.getDataSource().newResident(var1 + "Mayor", id(var1 + "mayor"));
      var5.setTown(var4);
      var4.setMayor(var5);
      var5.save();
      var4.save();
      return var4;
   }

   private void first() throws Exception {
      Town var1 = this.town("ReliabilitySeller", this.seller);
      Town var2 = this.town("ReliabilityBuyer", this.buyer);
      this.town("ReliabilityToll", this.toll);
      Resident var3 = TownyUniverse.getInstance().getDataSource().newResident("ReliabilityActor", this.actor);
      var3.save();
      var2.getAccount().deposit(1000.0, "Runtime fixture");
      this.getConfig().set("buyer-before", this.balance(this.buyer));
      this.getConfig().set("seller-before", this.balance(this.seller));
      this.getConfig().set("toll-before", this.balance(this.toll));
      this.getConfig().set("actor-before", var3.getAccount().getHoldingBalance());
      this.saveConfig();
      this.nativePlayer.getInventory().clear();
      this.check(this.diamonds()==0,"fixture inventory cleared");
      this.nativePlayer.saveData();
      Files.writeString(
         this.folder.resolve("player-context.txt"),
         "Player storage context: world="
            + this.world.getWorldFolder()
            + ", container="
            + Bukkit.getWorldContainer()
            + ", receipt="
            + field(PlayerSaveReceipt.before(this.player), "file")
            + ", code="
            + PlayerSaveReceipt.class.getProtectionDomain().getCodeSource().getLocation()
      );
      ExpeditionRepository var4 = this.expedition.repository();
      var4.reward(this.rewardId, this.actor, List.of(new ItemStack(Material.DIAMOND, 3)), 100.0);
      var4.save();
      this.expedition.claim(this.player);
      this.check(!var4.hasReward(this.actor) && this.diamonds() == 3, "native inventory reward completed; remaining="+var4.hasReward(this.actor)+", diamonds="+this.diamonds());
      var4.reward(id("completed-queue-one"), this.actor, List.of(), 0.0);
      var4.reward(id("completed-queue-two"), this.actor, List.of(), 0.0);
      var4.save();
      this.expedition.claim(this.player);
      this.check(!var4.hasReward(this.actor), "one claim removes every completed batch");
      var4.load();
      this.check(!var4.hasReward(this.actor), "completed batch removal survives reload");
      this.near(this.getConfig().getDouble("actor-before") + 100.0, var3.getAccount().getHoldingBalance(), "real Towny personal reward");
      var4.reward(this.pendingReward, this.actor, List.of(new ItemStack(Material.DIAMOND, 2)), 0.0);
      var4.save();
      boolean var5 = SpigotConfig.disablePlayerDataSaving;
      SpigotConfig.disablePlayerDataSaving = true;

      try {
         this.expedition.claim(this.player);
      } finally {
         SpigotConfig.disablePlayerDataSaving = var5;
      }

      RewardBatch var6 = (RewardBatch)var4.rewards(this.actor).getFirst();
      this.check(journal(var4).state(var6.itemId(0)).equals("PENDING"), "silent native player-save failure remains pending");
      this.check(this.diamonds() == 5, "uncertain in-memory inventory retained for reconciliation");
      ItemStack var7 = new ItemStack(Material.IRON_INGOT);
      this.warehouse.transfer(id("seed"), this.seller, var7, 100, true);
      this.warehouse.transfer(id("seed"), this.seller, var7, 100, true);
      this.check(this.stock(this.seller) == 100, "warehouse replay has no extra stock");
      this.fail(() -> this.warehouse.transfer(id("seed"), this.seller, var7, 99, true), "warehouse ID binds exact amount");
      Path var8 = Bukkit.getPluginManager().getPlugin("NeverLandTownyBuilds").getDataFolder().toPath().resolve("town-data.yml");
      Path var9 = var8.resolveSibling("probe-backup.yml");
      Files.move(var8, var9);
      Files.createDirectory(var8);
      Files.writeString(var8.resolve("keep"), "keep");
      this.fail(() -> this.warehouse.transfer(id("disk-fault"), this.seller, var7, 7, true), "warehouse write fault surfaced");
      this.check(!this.data.writable(), "warehouse blocked after write failure");
      Files.delete(var8.resolve("keep"));
      Files.delete(var8);
      Files.move(var9, var8);
      this.data.load();
      this.check(
         this.stock(this.seller) == 100 && !this.warehouse.transferred(id("disk-fault"), this.seller),
         "disk restore preserves original stock without false receipt"
      );
      this.data.town(this.seller).setLevel("market", 2);
      this.data.town(this.seller).setLevel("celestial_orrery", 1);
      this.data.saveOrThrow();
      this.check(new BuildBridge((JavaPlugin)Bukkit.getPluginManager().getPlugin("NeverLandTownyTrade")).marketLevel(this.seller)==0,
         "Trade honors unavailable maintenance/power");
      withOperationalProviders(() -> {
      this.check(
         new BuildBridge((JavaPlugin)Bukkit.getPluginManager().getPlugin("NeverLandTownyTrade")).marketLevel(this.seller) == 2,
         "Trade reads public building level"
      );
      this.check(
         new ru.neverland.mintexpeditions.integration.BuildBridge((JavaPlugin)Bukkit.getPluginManager().getPlugin("NeverLandTownyExpeditions"))
               .expeditionTimeMultiplier(this.seller)
            > 1.0,
         "Expeditions reads public orrery bonus"
      );
      });
      Caravan var10 = new Caravan(
         this.caravanId,
         this.seller,
         this.buyer,
         "probe_iron",
         var7,
         50,
         50,
         100.0,
         110.0,
         Map.of(this.toll, 10.0),
         List.of(),
         System.currentTimeMillis(),
         System.currentTimeMillis() + 3600000L,
         System.currentTimeMillis() + 10000L,
         false,
         true,
         CaravanStatus.ACTIVE
      );
      this.trade.repository().add(var10);
      this.trade.repository().save();
      this.warehouse.transfer(var10.operation("take"), this.seller, var7, 50, false);
      this.fail(
         () -> journal(this.trade.repository())
            .execute(
               var10.operation("debit"),
               "Списание каравана " + var10.id() + " город " + this.buyer + ": " + var10.escrow(),
               () -> {
                  this.check(
                     this.trade.economy().transfer(var2, var10.escrow(), this.trade.definitionOf(var10), "debit", var10.operation("debit"), false),
                     "native caravan debit"
                  );
                  throw new IllegalStateException("Injected lost bank reply");
               }
            ),
         "lost bank reply persisted"
      );
      this.check(this.stock(this.seller) == 50 && this.stock(this.buyer) == 0, "pending caravan retains exact reserved cargo");
      this.near(this.getConfig().getDouble("buyer-before") - 110.0, this.balance(this.buyer), "buyer debited once before hard stop");
      this.benchmark();
      Diagnostics.show(Bukkit.getConsoleSender());
   }

   @SuppressWarnings({"rawtypes","unchecked"})
   private void withOperationalProviders(Action action) throws Exception {
      // A narrow provider fixture checks the positive bridge path; real providers are restored immediately.
      java.util.List<Object[]> installed=new java.util.ArrayList<>();
      try {
         for(String[] spec:new String[][]{{"NeverLandTownyUpkeep","ru.neverland.townyupkeep.api.TownyUpkeepApi","active"},
            {"NeverLandTownyPower","ru.neverland.townypower.api.TownyPowerApi","powered"}}){
            var owner=Bukkit.getPluginManager().getPlugin(spec[0]);Class type=Class.forName(spec[1],true,owner.getClass().getClassLoader());
            Object stub=Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},(proxy,method,args)->{
               if(method.isDefault())return java.lang.reflect.InvocationHandler.invokeDefault(proxy,method,args);
               if(method.getName().equals(spec[2]))return true;
               return null;
            });
            Bukkit.getServicesManager().register(type,stub,this,org.bukkit.plugin.ServicePriority.Highest);installed.add(new Object[]{type,stub});
         }
         action.run();
      } finally {for(Object[] value:installed)Bukkit.getServicesManager().unregister((Class)value[0],value[1]);}
   }

   private void benchmark() throws Exception {
      ArrayList var1 = new ArrayList();
      var1.add("journal_entries,samples,median_ms,p95_ms,max_ms");

      for (int var3 : List.of(1, 100, 1000)) {
         Path var4 = this.folder.resolve("benchmark-" + var3 + ".yml");
         YamlConfiguration var5 = new YamlConfiguration();
         var5.set("schema", 1);
         var5.createSection("effects");

         for (int var6 = 0; var6 < var3; var6++) {
            String var7 = "effects." + id("benchmark-" + var6);
            var5.set(var7 + ".description", "Сохранённая операция склада и казны " + var6);
            var5.set(var7 + ".state", "DONE");
         }

         AtomicFiles.write(var4, var5::saveToString);
         EffectJournal var13 = new EffectJournal(var4);
         var13.load();
         ArrayList var14 = new ArrayList();

         for (int var8 = 0; var8 < 23; var8++) {
            long var9 = System.nanoTime();
            var13.execute(id("measure-" + var3 + "-" + var8), "Измерение " + var8, () -> true);
            double var11 = (System.nanoTime() - var9) / 1000000.0;
            if (var8 >= 3) {
               var14.add(var11);
            }
         }

         Collections.sort(var14);
         var1.add(String.format(Locale.ROOT, "%d,%d,%.3f,%.3f,%.3f", var3, var14.size(), var14.get(10), var14.get(18), var14.get(19)));
      }

      Files.write(this.folder.resolve("journal-latency.csv"), var1);
   }

   private void restart() throws Exception {
      this.check(this.diamonds() == 3, "hard restart restores only confirmed native inventory");
      ExpeditionRepository var1 = this.expedition.repository();
      RewardBatch var2 = (RewardBatch)var1.rewards(this.actor).getFirst();
      this.check(
         var2.id().equals(this.pendingReward) && journal(var1).state(var2.itemId(0)).equals("PENDING"),
         "pending item receipt survives hard restart"
      );
      this.expedition.claim(this.player);
      this.check(this.diamonds() == 3, "ambiguous item never automatically repeats");
      journal(var1).resolve(var2.itemId(0), false);
      this.expedition.claim(this.player);
      this.check(this.diamonds() == 5 && !var1.hasReward(this.actor), "verified undelivered reward completes once");
      this.near(
         this.getConfig().getDouble("actor-before") + 100.0,
         TownyAPI.getInstance().getResident(this.actor).getAccount().getHoldingBalance(),
         "personal money not replayed"
      );
      Caravan var3 = this.trade.repository().findCaravan(this.caravanId.toString());
      this.check(var3 != null && var3.settlement().equals("PREPARED"), "pending caravan recovered");
      this.near(this.getConfig().getDouble("buyer-before") - 110.0, this.balance(this.buyer), "hard restart does not repeat buyer debit");
      this.check(this.stock(this.seller) == 50 && this.stock(this.buyer) == 0, "warehouse reservation survives process death");
      journal(this.trade.repository()).resolve(var3.operation("debit"), true);
      Method var4 = TradeService.class.getDeclaredMethod("advance", Caravan.class);
      var4.setAccessible(true);
      var4.invoke(this.trade, var3);
      this.check(this.trade.forceComplete(var3), "reconciled caravan completes actual delivery and credits");
      this.check(this.stock(this.seller) == 50 && this.stock(this.buyer) == 50, "cargo conserved after recovery");
      this.near(this.getConfig().getDouble("seller-before") + 100.0, this.balance(this.seller), "seller credited exactly once");
      this.near(this.getConfig().getDouble("toll-before") + 10.0, this.balance(this.toll), "transit credited exactly once");
      this.trade.forceComplete(var3);
      this.check(this.stock(this.buyer) == 50, "repeated completion cannot unload twice");
      this.near(this.getConfig().getDouble("buyer-before") - 110.0, this.balance(this.buyer), "buyer never charged again");
      this.nativePlayer.loadData();
      this.check(this.diamonds() == 5, "final item delivery persisted in native playerdata");
      Diagnostics.show(Bukkit.getConsoleSender());
   }

   private interface Action {
      void run() throws Exception;
   }
}
