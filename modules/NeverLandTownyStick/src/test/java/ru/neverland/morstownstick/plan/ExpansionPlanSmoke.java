package ru.neverland.morstownstick.plan;
import ru.neverland.morstownstick.model.CellKey;
import java.util.*;
public final class ExpansionPlanSmoke {
    static int checks;static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
    interface Work{void run()throws Exception;}static void denied(Work work)throws Exception{try{work.run();}catch(IllegalArgumentException|IllegalStateException expected){checks++;return;}throw new AssertionError("accepted invalid transition");}
    public static ExpansionPlan draft(){return new ExpansionPlan(new UUID(1,1),new UUID(2,2),new UUID(3,3),new UUID(4,4),1,1000,1000,List.of(new CellKey("world",1,0),new CellKey("world",2,0)),25,true,1,ExpansionPlan.Status.DRAFT,null,List.of(),"quote");}
    public static void main(String[] args)throws Exception{
        var d=draft();denied(()->d.approve(d.author(),1,1001));denied(()->d.approve(d.mayor(),2,1001));denied(()->d.intent(d.mayor(),1,1001));
        var a=d.approve(d.mayor(),1,1001);check(a.status()==ExpansionPlan.Status.APPROVED&&a.open(),"approval does not execute");denied(()->a.approve(d.mayor(),1,1002));
        var i=a.intent(d.mayor(),1,1002);denied(()->i.intent(d.mayor(),1,1003));denied(()->i.cancel(1003));
        var partial=i.result(List.of(d.cells().get(0)),"one owned",1003);check(partial.status()==ExpansionPlan.Status.REVIEW&&partial.open(),"partial ownership requires review");denied(()->partial.intent(d.mayor(),1,1004));
        var resolved=partial.reconcile(partial.observedOwned(),"checked payment",1004);check(resolved.status()==ExpansionPlan.Status.RECONCILED&&!resolved.open(),"resolution closes without replay");
        var done=i.result(d.cells(),"observed",1003);check(done.status()==ExpansionPlan.Status.OBSERVED&&!done.open(),"all ownership observed separately from payment");denied(()->done.intent(d.mayor(),1,1004));check(a.cancel(1003).status()==ExpansionPlan.Status.CANCELLED,"unlaunched approval cancellation safe");
        denied(()->new ExpansionPlan(d.id(),d.town(),d.author(),d.mayor(),1,1000,1001,Collections.nCopies(65,d.cells().get(0)),0,false,1,ExpansionPlan.Status.DRAFT,null,List.of(),""));
        denied(()->new ExpansionPlan(d.id(),d.town(),d.author(),d.mayor(),1,1000,1001,d.cells(),Double.NaN,false,1,ExpansionPlan.Status.DRAFT,null,List.of(),""));
        denied(()->new ExpansionPlan(d.id(),d.town(),d.author(),d.mayor(),1,1000,1001,d.cells(),0,false,1,ExpansionPlan.Status.APPROVED,d.author(),List.of(),""));
        var snapshot=new ExpansionRepository.Snapshot(Map.of(d.id(),d));var copy=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.StringReader(ExpansionRepository.encode(snapshot)));check(ExpansionRepository.decode(copy).equals(snapshot),"exact schema roundtrip");
        copy.set("plans."+d.id()+".economy",null);try{ExpansionRepository.decode(copy);throw new AssertionError("missing economic condition accepted");}catch(java.io.IOException expected){checks++;}
        var duplicate=new ExpansionPlan(new UUID(5,5),d.town(),d.author(),d.mayor(),1,1000,1000,d.cells(),25,true,1,ExpansionPlan.Status.DRAFT,null,List.of(),"");denied(()->snapshot.with(duplicate));
        System.out.println("ExpansionPlanSmoke PASS "+checks);
    }
}
