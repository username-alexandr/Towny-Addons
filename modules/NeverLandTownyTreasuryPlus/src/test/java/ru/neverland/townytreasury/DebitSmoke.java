package ru.neverland.townytreasury;
import ru.neverland.townytreasury.model.*;
import java.util.*;
public final class DebitSmoke {
    private static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    private static final class Fixture implements BudgetDebit.Journal {
        CityLedger city=LedgerEngine.mode(CityLedger.initial(100000,Map.of(Budget.CONSTRUCTION,40,Budget.ARMY,20,Budget.INFRASTRUCTURE,25,Budget.SOCIAL,15),1000),true,"Мэр");
        CityLedger.Pending pending=new CityLedger.Pending(UUID.randomUUID(),1000,1000,Budget.ARMY,"upkeep","Штаб");boolean seen,bankResult=true,throwAfter,preFail,postFail;int calls,faults;
        public CityLedger get(){return city;}public void put(CityLedger value)throws Exception{if(preFail)throw new java.io.IOException("disk before money");city=value;}
        public void flush()throws Exception{if(postFail)throw new java.io.IOException("disk after money");if(seen)city=LedgerEngine.observe(city,pending.amount(),false,pending.category(),pending.source(),false,pending.at(),pending.id());}
        public void fault(Exception e){faults++;}
        boolean run(){return BudgetDebit.execute(this,pending,()->{calls++;if(throwAfter)throw new IllegalStateException("bank callback failed");return bankResult;},()->seen);}
    }
    public static void run(){
        var paid=new Fixture();paid.seen=true;check(paid.run()&&paid.calls==1&&paid.city.pending()==null&&paid.city.balance()==99000,"observed debit finishes once");
        var refused=new Fixture();refused.bankResult=false;check(!refused.run()&&refused.city.pending()==null&&refused.city.balance()==100000,"refused bank does not consume budget");
        var pre=new Fixture();pre.preFail=true;check(!pre.run()&&pre.calls==0&&pre.faults==1,"failed intent persistence never reaches bank");
        var post=new Fixture();post.seen=true;post.postFail=true;check(post.run()&&post.calls==1&&post.city.pending()!=null&&post.faults==1,"post-debit disk fault returns paid and retains intent");check(!post.run()&&post.calls==1,"pending intent prohibits a second debit");
        var ambiguous=new Fixture();ambiguous.throwAfter=true;boolean threw=false;try{ambiguous.run();}catch(IllegalStateException expected){threw=true;}check(threw&&ambiguous.city.pending()!=null&&ambiguous.calls==1,"unobserved bank exception requires reconciliation");check(!ambiguous.run()&&ambiguous.calls==1,"ambiguous debit never auto retries");
        var closed=new Fixture();closed.seen=true;closed.bankResult=false;check(closed.run()&&closed.city.balance()==99000,"closed-economy secondary failure cannot undo actual observed debit");
        var laterObserver=new Fixture();laterObserver.seen=true;laterObserver.throwAfter=true;check(laterObserver.run()&&laterObserver.city.pending()==null,"later observer exception still reports actual debit");
        System.out.println("DebitSmoke OK: bank rejection, intent failure, post-debit failure, callback exceptions, closed economy outcome and no ambiguous retry");
    }
}
