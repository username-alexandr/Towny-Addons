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
        ok(defaults.getConfigurationSection("templates").getKeys(false).size()==40,"bundled catalog has 40 orders");
        var real=OrderCatalog.templates(defaults);
        for(var rarity:OrderRarity.values())ok(real.stream().filter(t->t.rarity()==rarity).count()==8,"eight orders of "+rarity);
        ok(real.stream().allMatch(t->t.cents()<=150000),"every default reward fits player cap");
        var old=new YamlConfiguration();old.loadFromString("board-size: 4\nrefresh-seconds: 3600\nlimits:\n  player-24h: 987\ntemplates:\n  wheat_delivery:\n    title: Мой заказ\n    reward: 777\n    amount: 17\n  custom_job:\n    enabled: false\n");
        ok(OrderCatalog.migrate(old,defaults),"legacy config migrated");
        ok(old.getInt("board-size")==12&&!old.contains("refresh-seconds"),"hourly default becomes 12 daily slots");
        ok(old.getInt("limits.player-24h")==987&&old.getInt("templates.wheat_delivery.reward")==777&&old.getInt("templates.wheat_delivery.amount")==17,"custom limits/rewards/requirements preserved");
        ok(old.getString("templates.wheat_delivery.title").equals("Мой заказ")&&old.contains("templates.custom_job"),"custom titles/templates preserved");
        ok(old.getConfigurationSection("templates").getKeys(false).size()==41,"missing defaults added without deleting custom entries");
        old.set("templates.coal_mining",null);String after=old.saveToString();
        ok(!OrderCatalog.migrate(old,defaults)&&old.saveToString().equals(after),"migration is one-time and respects later deletions");
        Path legacyFile=root.resolve("orders.yml");Files.writeString(legacyFile,"original configuration\n");
        OrderCatalog.saveMigration(legacyFile,old);
        ok(Files.readString(root.resolve("orders.pre-daily-v2.yml")).equals("original configuration\n"),"original configuration backed up");

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
        System.out.println("DAILY_ORDERS_SMOKE_PASS: "+checks+" checks");
    }
}
