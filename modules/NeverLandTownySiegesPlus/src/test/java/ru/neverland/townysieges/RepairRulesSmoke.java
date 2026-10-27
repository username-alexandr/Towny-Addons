package ru.neverland.townysieges;
public final class RepairRulesSmoke {
    static int checks;static void ok(boolean b){if(!b)throw new AssertionError();checks++;}
    static void bad(Runnable r){try{r.run();throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}}
    public static void main(String[] args){
        ok(RepairRules.cost(100000,500,1000)==50000);ok(RepairRules.cost(550000,400,800)==275000);
        ok(RepairRules.cost(100000,0,1000)==0);ok(RepairRules.cost(100000,1000,1000)==100000);
        ok(RepairRules.cost(1,1,1000)==1);ok(RepairRules.cost(0,500,1000)==0);ok(RepairRules.cost(Long.MAX_VALUE,1,1)==Long.MAX_VALUE);
        bad(()->RepairRules.cost(-1,1,1));bad(()->RepairRules.cost(100,-1,100));bad(()->RepairRules.cost(100,101,100));bad(()->RepairRules.cost(100,Double.NaN,100));bad(()->RepairRules.cost(100,10,0));
        var base=new LootSettings(true,1000,1000);var rules=new LootDefense(200,7500,true);ok(rules.quote(0,0,base).effective().moneyBps()==1000);ok(rules.quote(5,0,base).effective().moneyBps()==900);ok(rules.quote(30,.2,base).effective().moneyBps()==250);
        int money=10000,resources=10000;for(int level=0;level<=30;level++){var q=rules.quote(level,.1,base);ok(q.effective().moneyBps()<=money&&q.effective().resourcesBps()<=resources);money=q.effective().moneyBps();resources=q.effective().resourcesBps();}
        ok(new LootDefense(200,7500,false).quote(5,.2,base).effective().moneyBps()==900);bad(()->rules.quote(-1,0,base));bad(()->rules.quote(1,Double.NaN,base));
        System.out.println("RepairRulesSmoke PASS: "+checks+" assertions; exact proportional repair and monotonic protected loot");
    }
}
