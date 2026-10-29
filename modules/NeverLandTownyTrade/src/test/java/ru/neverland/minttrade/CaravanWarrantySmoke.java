package ru.neverland.minttrade;
import java.util.*;
import org.bukkit.inventory.ItemStack;
import ru.neverland.minttrade.model.*;
import ru.neverland.minttrade.guarantee.GuaranteeTerms;

public final class CaravanWarrantySmoke {
    static int checks;static void check(boolean b,String why){if(!b)throw new AssertionError(why);checks++;}
    static final class Item extends ItemStack {Item(){super();}@Override public Item clone(){return new Item();}}
    static Caravan caravan(){var c=new Caravan(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"stone",new Item(),64,64,100,110,Map.of(UUID.randomUUID(),10D),List.of(),1000,61000,31000,true,false,CaravanStatus.ACTIVE);c.restoreGuarantee(new GuaranteeTerms(2000,100,60000),60000,0,-1,-1);return c;}
    public static void main(String[] args){
        var c=caravan();c.delay(300000);check(c.experiencedNaturalDelay(61000)==0,"no late time before planned route end");check(c.experiencedNaturalDelay(181000)==120000,"only experienced road delay counts");
        c.editTimer("pause",0,181000);check(c.experiencedNaturalDelay(781000)==120000,"paused time cannot grow compensation");c.editTimer("resume",0,781000);check(c.experiencedNaturalDelay(781000)==120000,"resuming shifts original route and excludes pause");
        c.settleGuarantee(781000);check(c.compensationCents()==100&&c.sellerPayment()==99,"partial actual delay frozen before warehouse/payment waits");c.settleGuarantee(1781000);check(c.compensationCents()==100,"later bank or warehouse outage cannot inflate compensation");
        var clone=c.copy();clone.remainingCargo(1);check(c.remainingCargo()==64,"detached clone protects acknowledged cargo");check(clone.compensationCents()==100&&clone.guarantee().equals(c.guarantee()),"clone preserves frozen money terms");
        var edited=caravan();edited.delay(300000);edited.editTimer("extend",10,181000);edited.settleGuarantee(10000000);check(edited.compensationCents()==100,"administrative extension does not create more compensation");
        var restarted=caravan();restarted.delay(300000);restarted.editTimer("restart",0,181000);restarted.settleGuarantee(10000000);check(restarted.compensationCents()==100,"admin restart preserves only already experienced delay");
        var fully=caravan();fully.delay(3_000_000);fully.settleGuarantee(10_000_000);check(fully.compensationCents()==2000&&fully.sellerPayment()==80,"maximum refund is funded seller share");check(fully.sellerPayment()+fully.compensationCents()/100.0+fully.tariffs().values().stream().mapToDouble(Double::doubleValue).sum()==fully.escrow(),"tariffs stay outside warranty allocation");
        var restored=caravan();restored.restoreGuarantee(c.guarantee(),c.guaranteedRouteDuration(),c.naturalDelayMillis(),c.frozenNaturalDelay(),c.finalCompensation());check(restored.compensationCents()==100,"saved frozen delay reproduces exact money");boolean failed=false;try{restored.restoreGuarantee(c.guarantee(),60000,300000,120000,101);}catch(IllegalArgumentException e){failed=true;}check(failed,"corrupt compensation inconsistent with recorded delay rejected");
        var early=caravan();early.delay(300000);early.settleGuarantee(50000);check(early.compensationCents()==0,"forced early arrival does not pay unexperienced delay");
        var legacy=new Caravan(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"old",new Item(),64,64,100,100,Map.of(),List.of(),1000,61000,31000,true,false,CaravanStatus.ACTIVE);legacy.delay(300000);legacy.settleGuarantee(10000000);check(legacy.compensationCents()==0&&legacy.sellerPayment()==100,"old caravan never gains retroactive compensation");
        System.out.println("CaravanWarrantySmoke PASS "+checks+" checks");
    }
}
