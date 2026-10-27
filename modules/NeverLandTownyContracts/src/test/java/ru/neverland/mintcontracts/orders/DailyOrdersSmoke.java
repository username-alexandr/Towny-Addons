package ru.neverland.mintcontracts.orders;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.earnings.RewardLedger;
import ru.neverland.mintcontracts.orders.PublicOrders.Template;
import ru.neverland.mintcontracts.orders.PublicOrders.Type;

/** Calendar boundary, durable selection, migration and payout-budget regressions. */
public final class DailyOrdersSmoke {
    private static int checks;
    private static void ok(boolean result, String message) { checks++; if (!result) throw new AssertionError(message); }
    private interface Attempt { void run() throws Exception; }
    private static void rejects(Attempt attempt, String message) throws Exception {
        try { attempt.run(); } catch (Exception expected) { checks++; return; }
        throw new AssertionError(message);
    }
    private static final class Time extends Clock {
        Instant now = Instant.parse("2026-10-20T20:59:59.999Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        public Instant instant() { return now; }
    }
    private static DailyOrderBoard board(Path path, List<Template> catalog, int size, Map<OrderRarity,Integer> weights, Time time, int seed) throws Exception {
        return new DailyOrderBoard(path,catalog,size,weights,time,new Random(seed));
    }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("nlt-daily-orders"); Time time = new Time();
        var weights = new EnumMap<OrderRarity,Integer>(OrderRarity.class);
        for (var rarity : OrderRarity.values()) weights.put(rarity, rarity.defaultWeight);
        var catalog = new ArrayList<Template>();
        for (Type type : Type.values()) for (OrderRarity rarity : OrderRarity.values()) for (int i=0;i<2;i++) {
            String target = switch (type) { case DELIVERY -> "WHEAT"; case MINING -> "IRON_ORE"; case HUNTING -> "ZOMBIE"; case FISHING -> "COD"; };
            catalog.add(new Template(type.name().toLowerCase()+"_"+rarity.name().toLowerCase()+i,"Задание "+i,type,target,16,12000,7200000,rarity));
        }
        Path file = root.resolve("board.yml");
        var daily = board(file,catalog,12,weights,time,1); var first = daily.snapshot();
        ok(first.day().equals(LocalDate.of(2026,10,20)), "23:59:59.999 Moscow still previous day");
        ok(first.nextRefresh().equals(Instant.parse("2026-10-20T21:00:00Z")), "midnight Moscow is 21:00 UTC");
        ok(first.templates().size()==12 && first.templates().stream().map(Template::id).distinct().count()==12, "12 unique orders");
        ok(first.templates().stream().map(Template::type).distinct().count()==4, "all four activities represented");
        byte[] saved=Files.readAllBytes(file);
        ok(daily.snapshot().equals(first) && Arrays.equals(saved,Files.readAllBytes(file)), "reads do not reroll or rewrite selection");
        var restarted=board(file,catalog,12,weights,time,999);
        ok(restarted.snapshot().equals(first), "restart with another RNG retains exact terms and rarities");
        time.now=Instant.parse("2026-10-20T21:00:00Z"); var second=restarted.snapshot();
        ok(second.day().equals(LocalDate.of(2026,10,21)), "rollover at exact midnight Moscow");
        ok(!second.templates().equals(first.templates()), "new day produces new selection");
        ok(board(file,catalog,12,weights,time,30).snapshot().equals(second), "new selection persisted before use");
        time.now=Instant.parse("2026-10-20T20:59:59Z");
        ok(restarted.snapshot().equals(second), "backwards clock cannot reroll an already visited day");
        time.now=Instant.parse("2026-10-25T10:00:00Z");
        ok(restarted.snapshot().day().equals(LocalDate.of(2026,10,25)), "downtime skips directly to current day");

        Path configBoard=root.resolve("edited.yml");
        var original=board(configBoard,catalog,12,weights,time,2).snapshot();
        var replacement=List.of(new Template("replacement","Новые условия",Type.MINING,"IRON_ORE",99,42000,21600000,OrderRarity.RARE));
        var changed=board(configBoard,replacement,1,weights,time,2);
        ok(changed.snapshot().equals(original), "config edits/removals/size changes do not replace current day");
        time.now=time.now.plusSeconds(86400);
        ok(changed.snapshot().templates().equals(replacement), "next day applies new catalog and size");
        var zero=new EnumMap<OrderRarity,Integer>(weights);zero.replaceAll((r,w)->r==OrderRarity.LEGENDARY?1:0);
        var legendary=board(root.resolve("legendary.yml"),catalog,45,zero,time,3).snapshot();
        ok(legendary.templates().size()==8 && legendary.templates().stream().allMatch(t->t.rarity()==OrderRarity.LEGENDARY), "zero weights excluded and short pool not duplicated");
        zero.replaceAll((r,w)->0);rejects(()->board(root.resolve("zero.yml"),catalog,12,zero,time,3), "all-zero weights rejected");
        rejects(()->board(root.resolve("large.yml"),catalog,46,weights,time,3), "footer slots cannot be overwritten");
        Path corrupt=root.resolve("corrupt.yml");Files.writeString(corrupt,"schema: 999\n");
        rejects(()->board(corrupt,catalog,12,weights,time,3), "unknown board schema fails closed");
        ok(Files.readString(corrupt).equals("schema: 999\n"), "corrupt file not replaced with new selection");
        Path bad=root.resolve("bad-parent");Files.writeString(bad,"block directory creation");
        var unwritable=board(bad.resolve("board.yml"),catalog,12,weights,time,3);
        rejects(unwritable::snapshot, "failed durable save does not offer an unsaved board");
        rejects(unwritable::snapshot, "disk error blocks subsequent attempts instead of rerolling");

        var defaults=new YamlConfiguration();
        try(var input=DailyOrdersSmoke.class.getResourceAsStream("/orders.yml");var reader=new java.io.InputStreamReader(Objects.requireNonNull(input),java.nio.charset.StandardCharsets.UTF_8)){defaults.load(reader);}
        ok(defaults.getConfigurationSection("templates").getKeys(false).size()==230,"bundled catalog has 230 orders");
        var real=OrderCatalog.templates(defaults);
        for(var rarity:OrderRarity.values())ok(real.stream().filter(t->t.rarity()==rarity).count()==46,"forty-six orders of "+rarity);
        for(var type:Type.values())ok(real.stream().filter(t->t.type()==type).count()==(type==Type.DELIVERY?170:20),"building deliveries added without reducing other activities: "+type);
        ok(defaults.getInt("limits.player-24h")==3000&&defaults.getInt("board-size")==18,"new-install defaults offer larger board and budget");
        ok(real.stream().allMatch(t->t.cents()<=PublicOrders.money(defaults,"limits.player-24h",3000)),"every default reward fits player cap");
        var labels=new ru.neverland.localization.MaterialLabels();
        ok(real.stream().allMatch(t->!labels.name(t.target()).contains("Неизвестный")&&!labels.name(t.target()).equals(t.target())),"all catalog targets have Russian labels");
        var old=new YamlConfiguration();old.loadFromString("board-size: 4\nrefresh-seconds: 3600\nlimits:\n  player-24h: 987\ntemplates:\n  wheat_delivery:\n    title: Мой заказ\n    reward: 777\n    amount: 17\n  custom_job:\n    enabled: false\n");
        ok(OrderCatalog.migrate(old,defaults),"legacy config migrated");
        ok(old.getInt("board-size")==18&&!old.contains("refresh-seconds"),"hourly default migrates directly to 18 daily slots");
        ok(old.getInt("limits.player-24h")==987&&old.getInt("templates.wheat_delivery.reward")==777&&old.getInt("templates.wheat_delivery.amount")==17,"custom limits/rewards/requirements preserved");
        ok(old.getString("templates.wheat_delivery.title").equals("Мой заказ")&&old.contains("templates.custom_job"),"custom titles/templates preserved");
        ok(old.getConfigurationSection("templates").getKeys(false).size()==231,"missing defaults added without deleting custom entries");
        old.set("templates.coal_mining",null);String after=old.saveToString();
        ok(!OrderCatalog.migrate(old,defaults)&&old.saveToString().equals(after),"migration is one-time and respects later deletions");
        Path legacyFile=root.resolve("orders.yml");Files.writeString(legacyFile,"original configuration\n");
        OrderCatalog.saveMigration(legacyFile,old);
        ok(Files.readString(root.resolve("orders.pre-catalog-v4.yml")).equals("original configuration\n"),"original configuration backed up for this migration");

        var v2=new YamlConfiguration();v2.loadFromString(defaults.saveToString());
        for(String id:new ArrayList<>(v2.getConfigurationSection("templates").getKeys(false)))
            if(v2.getInt("templates."+id+".introduced-in",2)>=3)v2.set("templates."+id,null);
        v2.set("catalog-version",2);v2.set("board-size",12);v2.set("limits.player-24h",1500);
        v2.set("templates.coal_mining",null);v2.set("templates.wheat_delivery.reward",333);
        v2.set("templates.sawmill_delivery.enabled",false);v2.set("rarity-weights.COMMON",17);
        ok(OrderCatalog.migrate(v2,defaults),"installed v2 migrates to expanded catalog");
        ok(v2.getInt("catalog-version")==4&&v2.getInt("board-size")==18&&v2.getInt("limits.player-24h")==3000,"previous standard settings raised once");
        ok(!v2.contains("templates.coal_mining")&&v2.contains("templates.tropical_collection"),"additions do not resurrect previously removed templates");
        ok(v2.getInt("templates.wheat_delivery.reward")==333&&v2.getInt("rarity-weights.COMMON")==17,"existing terms and selection weights unchanged");
        ok(!v2.getBoolean("templates.sawmill_delivery.enabled")&&!v2.contains("templates.sawmill_delivery.title"),"custom template with new built-in ID is not overwritten");
        v2.set("templates.tropical_collection",null);v2.set("limits.player-24h",1500);String changedV3=v2.saveToString();
        ok(!OrderCatalog.migrate(v2,defaults)&&v2.saveToString().equals(changedV3),"later manual removals and cap adjustments survive restarts");
        var custom=new YamlConfiguration();custom.loadFromString("catalog-version: 2\nboard-size: 9\nlimits:\n  player-24h: 5000\n  server-24h: 75000\n");
        OrderCatalog.migrate(custom,defaults);
        ok(custom.getInt("board-size")==9&&custom.getInt("limits.player-24h")==5000&&custom.getInt("limits.server-24h")==75000,"custom board and payout limits preserved");
        var smallServer=new YamlConfiguration();smallServer.loadFromString("catalog-version: 2\nlimits:\n  player-24h: 1500\n  server-24h: 2000\n");
        OrderCatalog.migrate(smallServer,defaults);
        ok(smallServer.getInt("limits.player-24h")==2000&&smallServer.getInt("limits.server-24h")==2000,"default cap upgrade cannot exceed a custom server ceiling");
        var missing=new YamlConfiguration();missing.set("catalog-version",2);OrderCatalog.migrate(missing,defaults);
        ok(missing.getInt("limits.player-24h")==3000&&missing.getInt("board-size")==18,"missing old defaults acquire new defaults");
        var v3=new YamlConfiguration();v3.loadFromString(defaults.saveToString());
        for(String id:new ArrayList<>(v3.getConfigurationSection("templates").getKeys(false)))if(v3.getInt("templates."+id+".introduced-in",2)==4)v3.set("templates."+id,null);
        v3.set("catalog-version",3);v3.set("limits.player-24h",5000);v3.set("limits.server-24h",500000);v3.set("board-size",12);v3.set("templates.wheat_delivery",null);
        ok(OrderCatalog.migrate(v3,defaults),"building orders migrate installed v3");
        ok(v3.getInt("limits.player-24h")==5000&&v3.getInt("limits.server-24h")==500000&&v3.getInt("board-size")==12,"v4 preserves personal/server limits and custom board size");
        ok(!v3.contains("templates.wheat_delivery")&&v3.contains("templates.build_oak_log_common"),"migration adds new deliveries without resurrecting deleted old orders");
        var construction=real.stream().filter(t->t.id().startsWith("build_")).toList();
        ok(construction.size()==150&&construction.stream().allMatch(t->t.type()==Type.DELIVERY&&t.amount()<=2048),"150 physically deliverable building orders fit ordinary inventory");
        for(var material:construction.stream().map(Template::target).distinct().toList()){
            var tiers=construction.stream().filter(t->t.target().equals(material)).sorted(Comparator.comparing(Template::rarity)).toList();
            ok(tiers.size()==5,"all rarities for "+material);for(int n=1;n<tiers.size();n++)ok(tiers.get(n).amount()>tiers.get(n-1).amount()&&tiers.get(n).cents()>tiers.get(n-1).cents(),"quantity and reward rise by rarity: "+material);
        }
        var future=new YamlConfiguration();future.set("catalog-version",5);
        rejects(()->OrderCatalog.migrate(future,defaults),"newer config schema is not silently downgraded");

        time.now=Instant.parse("2026-12-31T20:59:59Z");
        var year=board(root.resolve("year.yml"),catalog,12,weights,time,4);
        ok(year.snapshot().nextRefresh().equals(Instant.parse("2026-12-31T21:00:00Z")),"year boundary next refresh");
        time.now=time.now.plusSeconds(1);ok(year.snapshot().day().equals(LocalDate.of(2027,1,1)),"year boundary rotation");
        time.now=Instant.parse("2028-02-28T21:00:00Z");
        ok(year.snapshot().day().equals(LocalDate.of(2028,2,29)),"leap day rotation");
        var order=new PublicOrders.Order(UUID.randomUUID(),UUID.randomUUID(),"retired","Принятый",Type.MINING,"IRON_ORE",30,12,75000,400000,123,"ACTIVE",OrderRarity.EPIC,"2026-10-20");
        var progressed=order.progress(13).time(390000);
        ok(progressed.rarity()==OrderRarity.EPIC&&progressed.boardDay().equals("2026-10-20")&&progressed.cents()==75000&&progressed.goal()==30,"progress and timer retain accepted snapshot");
        time.now=Instant.parse("2026-10-20T20:59:59Z");UUID player=UUID.randomUUID();
        var ledger=new RewardLedger(root.resolve("payments"),150000,10000000,()->time.millis());ledger.load();
        UUID payment=UUID.randomUUID();ledger.reserve(payment,player,120000,"orders:legendary",false);
        ledger.submit(payment,new RewardLedger.Bank(){public boolean ready(UUID id){return true;}public boolean deposit(RewardLedger.Entry e){return true;}});
        time.now=time.now.plusSeconds(1);
        ok(ledger.remaining(player)==30000,"Moscow midnight does not reset rolling payout budget");
        rejects(()->ledger.reserve(UUID.randomUUID(),player,40000,"orders:new-day",false),"next board cannot exceed rolling cap");
        var upgraded=new RewardLedger(root.resolve("payments"),300000,10000000,()->time.millis());upgraded.load();
        ok(upgraded.remaining(player)==180000,"higher limit subtracts already paid income instead of resetting usage");
        var deposits=new java.util.concurrent.atomic.AtomicInteger();
        upgraded.submit(payment,new RewardLedger.Bank(){public boolean ready(UUID id){return true;}public boolean deposit(RewardLedger.Entry e){deposits.incrementAndGet();return true;}});
        ok(deposits.get()==0&&upgraded.get(payment).state()==RewardLedger.State.PAID,"raising cap never replays an already paid reward");
        UUID pending=UUID.randomUUID();upgraded.reserve(pending,player,180000,"orders:after-upgrade",false);
        rejects(()->upgraded.reserve(UUID.randomUUID(),player,1,"orders:above-new-cap",false),"new cap still bounds paid plus pending obligations");
        var higherReload=new RewardLedger(root.resolve("payments"),300000,10000000,()->time.millis());higherReload.load();
        ok(higherReload.remaining(player)==0&&higherReload.get(pending).state()==RewardLedger.State.PENDING,"pending obligation and total usage survive restart under new cap");
        time.now=time.now.plusSeconds(86400-1);
        ok(higherReload.remaining(player)==120000,"paid income ages out after 24 hours while pending remains reserved");
        System.out.println("DAILY_ORDERS_SMOKE_PASS: "+checks+" checks");
    }
}
