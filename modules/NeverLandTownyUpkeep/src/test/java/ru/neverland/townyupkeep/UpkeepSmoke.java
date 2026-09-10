package ru.neverland.townyupkeep;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.townyupkeep.config.UpkeepSettings;
import ru.neverland.townyupkeep.data.UpkeepRepository;
import ru.neverland.townyupkeep.model.*;
import ru.neverland.townyupkeep.model.Entry.*;
import java.util.*;
import java.nio.file.*;
import java.io.*;
import java.math.BigDecimal;
public final class UpkeepSmoke {
    private static void check(boolean b,String text){if(!b)throw new AssertionError(text);}
    private interface Attempt{void run()throws Exception;}
    private static void fails(Attempt a,String text)throws Exception{try{a.run();}catch(Exception expected){return;}throw new AssertionError(text);}
    private static YamlConfiguration read(String file)throws Exception{var y=new YamlConfiguration();try(var in=UpkeepSmoke.class.getResourceAsStream("/"+file)){y.load(new InputStreamReader(Objects.requireNonNull(in),java.nio.charset.StandardCharsets.UTF_8));}return y;}
    private static final class Fixture implements PaymentProcessor.Store,PaymentProcessor.Gateway {
        final Key key=new Key(UUID.randomUUID(),"guard");Entry saved;int writes,failWrite=-1,withdrawals,consumes,refunds;boolean available=true,money=true,accepted=true,throwAfterCharge=false,failSettle=false;String receipt="NONE";long balance=10000,coins=10000;
        Fixture(long money){saved=new Entry(false,10,"Оплата",new Invoice(UUID.randomUUID(),new Cost(money,Map.of("food",3000L)),3600,Phase.PREPARED));}
        public Entry get(Key key){return saved;}
        public void put(Key key,Entry e)throws Exception{if(++writes==failWrite)throw new IOException("injected journal failure");saved=e;}
        public boolean reserve(UUID id,UUID town,Map<String,Long> amounts){if(!receipt.equals("NONE"))return !receipt.equals("RELEASED");if(!available||balance<amounts.get("food"))return false;balance-=amounts.get("food");receipt="HELD";return true;}
        public void settle(UUID id,boolean consume)throws Exception{if(failSettle)throw new IOException("injected settlement failure");String wanted=consume?"CONSUMED":"RELEASED";if(receipt.equals(wanted))return;check(receipt.equals("HELD"),"cannot settle missing or opposite receipt");receipt=wanted;if(consume)consumes++;else{refunds++;balance+=3000;}}
        public boolean canPay(UUID town,long cents){return money&&coins>=cents;}
        public boolean withdraw(UUID town,long cents,UUID bill)throws Exception{withdrawals++;if(!accepted)return false;coins-=cents;if(throwAfterCharge)throw new IOException("economy failed after transfer");return true;}
        public void forget(UUID id){}
        PaymentProcessor processor(){return new PaymentProcessor(this,this);}
        void run()throws Exception{processor().process(key,10,300);}
    }
    public static void main(String[] args)throws Exception{
        var defaults=UpkeepSettings.load(read("config.yml"),read("buildings.yml"));check(defaults.profiles().size()==91,"91 building/wonder tariffs");check(defaults.grace()==3600&&defaults.period()==3600,"hourly billing and grace");
        for(var p:defaults.profiles().values()){check(!p.name().equals(p.id()),"Russian name "+p.id());for(int level=1;level<=5;level++){var cost=p.cost().multiply(level,defaults.maximum());check(cost.money()>=p.cost().money(),"monotonic level cost");}}
        var small=defaults.factor(1,1,1);var large=defaults.factor(200,100,200);check(large.compareTo(small)>0,"larger cities cost more");check(defaults.factor(Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE).equals(defaults.maximum()),"size factor capped without overflow");
        check(defaults.profiles().get("fortress_wall").cost().resources().get("stone")==4000,"wall consumes stone");check(defaults.profiles().get("pumping_station").cost().money()==800,"pump monetary upkeep");check(defaults.profiles().get("guard").cost().resources().get("food")==3000,"guard food upkeep");
        check(new Cost(1,Map.of("stone",1L)).multiply(1,new BigDecimal("1.005")).equals(new Cost(2,Map.of("stone",2L))),"ceil costs at ledger precision");
        fails(()->new Cost(-1,Map.of()),"negative money");fails(()->new Cost(0,Map.of("energy",1L)),"unknown resources rejected");fails(()->Cost.parse("NaN",2),"NaN");fails(()->Cost.parse("0.001",2),"sub-cent money");
        var invalid=read("config.yml");invalid.set("size.maximum",Double.POSITIVE_INFINITY);fails(()->UpkeepSettings.load(invalid,read("buildings.yml")),"nonfinite factor");
        var ok=new Fixture(1500);ok.run();check(ok.saved.active()&&ok.saved.invoice()==null&&ok.saved.due()==3610,"successful paid interval");check(ok.coins==8500&&ok.balance==7000&&ok.consumes==1,"one payment");ok.run();check(ok.withdrawals==1,"active replay never charges");
        var stone=new Fixture(0);stone.run();check(stone.saved.active()&&stone.withdrawals==0&&stone.consumes==1,"resource-only billing");
        var missing=new Fixture(1500);missing.available=false;missing.run();check(!missing.saved.active()&&missing.withdrawals==0&&missing.balance==10000,"insufficient resources spends nothing");
        var poor=new Fixture(1500);poor.money=false;poor.run();check(!poor.saved.active()&&poor.balance==10000&&poor.coins==10000&&poor.refunds==1,"insufficient money refunds resource reservation");
        var rejected=new Fixture(1500);rejected.accepted=false;rejected.run();check(!rejected.saved.active()&&rejected.refunds==1&&rejected.withdrawals==1,"rejected external transaction refunds once");rejected.run();check(rejected.refunds==1,"refund replay idempotent");
        var pending=new Fixture(1500);pending.throwAfterCharge=true;fails(pending::run,"injected post-charge crash");check(pending.saved.invoice().phase()==Phase.MONEY_PENDING,"ambiguous charge stays pending");pending.run();check(pending.withdrawals==1&&pending.balance==7000&&!pending.saved.active(),"restart cannot guess or repeat money withdrawal");pending.processor().resolve(pending.key,true);pending.run();check(pending.saved.active()&&pending.withdrawals==1&&pending.consumes==1,"admin paid reconciliation never charges again");
        var cancelled=new Fixture(1500);cancelled.saved=cancelled.saved.phase(Phase.MONEY_PENDING);cancelled.reserve(cancelled.saved.invoice().id(),cancelled.key.town(),Map.of("food",3000L));cancelled.processor().resolve(cancelled.key,false);cancelled.run();check(cancelled.balance==10000&&cancelled.withdrawals==0&&!cancelled.saved.active(),"admin unpaid reconciliation releases escrow");
        for(int fail=1;fail<=3;fail++){var f=new Fixture(1500);f.failWrite=fail;fails(f::run,"journal failure at write "+fail);f.failWrite=-1;int charged=f.withdrawals;f.run();if(f.saved.invoice()!=null&&f.saved.invoice().phase()==Phase.MONEY_PENDING){check(f.withdrawals==charged,"pending no replay");f.processor().resolve(f.key,true);f.run();}check(f.saved.active()&&f.withdrawals==1&&f.coins==8500&&f.balance==7000&&f.consumes==1,"restart at each journal boundary preserves money/resources "+fail);}
        for(int fail=1;fail<=2;fail++){var f=new Fixture(1500);f.money=false;f.failWrite=fail;fails(f::run,"refund journal failure "+fail);f.failWrite=-1;f.run();check(!f.saved.active()&&f.balance==10000&&f.refunds==1&&f.withdrawals==0,"refund crash recovery exactly once "+fail);}
        var settle=new Fixture(1500);settle.failSettle=true;fails(settle::run,"settlement failure");check(settle.saved.invoice().phase()==Phase.MONEY_PAID,"money result durable before resources commit");settle.failSettle=false;settle.run();check(settle.withdrawals==1&&settle.saved.active(),"settlement replay without money debit");
        var directory=Files.createTempDirectory("upkeep-smoke");var file=directory.resolve("upkeep.yml");var repo=new UpkeepRepository(file);repo.load();var bill=new Fixture(1500);repo.save(10,Map.of(bill.key,bill.saved));var restarted=new UpkeepRepository(file);restarted.load();check(restarted.clock()==10&&restarted.entries().equals(repo.entries()),"restart invoice exact, no offline clock advance");
        Files.delete(file);Files.createDirectory(file);Files.writeString(file.resolve("keep"),"keep");fails(()->repo.save(11,Map.of()),"atomic replace failure");check(repo.clock()==10&&!repo.entries().isEmpty(),"write failure leaves authoritative memory intact");
        var bad=directory.resolve("bad.yml");Files.writeString(bad,"schema: 1\ntowns: [broken");var broken=new UpkeepRepository(bad);fails(broken::load,"strict YAML");fails(()->broken.save(1,Map.of(bill.key,bill.saved)),"no overwrite after corrupt load");check(Files.readString(bad).contains("[broken"),"corrupt source preserved");
        System.out.println("UpkeepSmoke OK: 91 tariffs, scaling, grace, partial shortages, refusals, every journal boundary, ambiguous money recovery, resource refunds and atomic persistence");
    }
}
