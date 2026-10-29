package ru.neverland.townytreasury;
import java.util.*;import ru.neverland.townytreasury.debits.*;import static ru.neverland.townytreasury.debits.AsyncDebit.State.*;
public final class SpendingAdmissionSmoke {
    static final UUID T=UUID.randomUUID(),R=UUID.randomUUID();static int checks;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
    static void denied(Runnable work,String why){try{work.run();}catch(IllegalArgumentException expected){check(true,why);return;}throw new AssertionError(why);}
    static AsyncDebit.Terms terms(UUID town,UUID resident,long cents){return new AsyncDebit.Terms(UUID.randomUUID(),town,"construction","native/build",cents,"Building",resident,"NeverLandTownyBuilds");}
    static AsyncDebit row(UUID town,UUID resident,long cents,AsyncDebit.State state,long at){var mode=resident.equals(SpendingAdmission.SYSTEM)?"SYSTEM":"POLICY";return new AsyncDebit(terms(town,resident,cents),state,at,at,"",false,new SpendingAdmission(mode,0,0,0,0,0,at));}
    public static void main(String[] args){
        long now=10*SpendingAdmission.DAY;var rows=new ArrayList<AsyncDebit>();
        rows.add(row(T,R,1000,PAID,now-100));rows.add(row(T,R,200,PAID,now-SpendingAdmission.DAY+1));rows.add(row(T,R,300,PAID,now-SpendingAdmission.DAY));
        rows.add(row(T,R,400,REVIEW,1));rows.add(row(T,R,500,INVOKING,2));rows.add(row(T,R,600,PREPARED,3));rows.add(row(T,R,9000,REJECTED,now));
        rows.add(row(UUID.randomUUID(),R,9000,PAID,now));rows.add(row(T,UUID.randomUUID(),9000,PAID,now));rows.add(row(T,SpendingAdmission.SYSTEM,9000,PAID,now));
        rows.add(new AsyncDebit(terms(T,R,9000),PAID,now,now,"historical unknown policy"));
        var original=rows.get(0).terms();var refundTerms=new AsyncDebit.Terms(UUID.randomUUID(),T,original.category(),original.source(),1000,"Refund",R,original.owner(),AsyncDebit.Kind.REFUND,original.operation());rows.add(new AsyncDebit(refundTerms,PAID,now,now,"",false,new SpendingAdmission("REFUND",0,0,0,0,0,now)));
        var usage=SpendingAdmission.usage(rows,T,R,now);check(usage.spent()==1200&&usage.reserved()==1500&&usage.total()==2700,"rolling interval, indefinitely held uncertainty, city/person scope, legacy exclusion and refund conservation");
        var policy=new SpendingAdmission("POLICY",7,500,3000,usage.spent(),usage.reserved(),now);check(policy.refusal(300).isEmpty()&&!policy.refusal(301).isEmpty(),"exact remaining daily amount includes pending receipts");
        check(new SpendingAdmission("POLICY",7,500,0,0,0,now).refusal(500).isEmpty()&&!new SpendingAdmission("POLICY",7,500,0,0,0,now).refusal(501).isEmpty(),"single-payment inclusive boundary");
        check(!new SpendingAdmission("POLICY",8,0,100,Long.MAX_VALUE,Long.MAX_VALUE,now).refusal(1).isEmpty(),"overflow cannot bypass exhausted limit");
        check(new SpendingAdmission("POLICY",9,0,0,Long.MAX_VALUE,Long.MAX_VALUE,now).refusal(SpendingAdmission.MAX).isEmpty(),"explicit unlimited remains unlimited");
        check(!new SpendingAdmission("UNAVAILABLE",0,0,0,0,0,now).refusal(1).isEmpty(),"unavailable Council never becomes unlimited");
        var accepted=new AsyncDebit(terms(T,R,300),PREPARED,now,now,"",false,policy);check(accepted.state(INVOKING,now+10,"").state(PAID,now+5,"").admission().equals(policy),"captured rules remain immutable through bank ACK and backward clock");
        check(accepted.state(INVOKING,now+10,"").state(PAID,now+5,"").changedAt()==now+10,"receipt timestamps never move backward");
        check(accepted.auditAck().admission().equals(policy)&&SpendingAdmission.read(policy.view()).equals(policy),"audit ACK and primitive roundtrip preserve evidence");
        denied(()->new AsyncDebit(terms(T,R,301),PREPARED,now,now,"",false,policy),"over-limit receipt cannot be prepared");
        check(new AsyncDebit(terms(T,R,301),REJECTED,now,now,"limit",false,policy).state()==REJECTED,"rejection preserves exact rejected terms");
        denied(()->new AsyncDebit(terms(T,R,300),PREPARED,now+1,now+1,"",false,policy),"admission and accepted intent share exact timestamp");
        denied(()->new AsyncDebit(terms(T,R,300),PREPARED,now,now,"",false,new SpendingAdmission("SYSTEM",0,0,0,0,0,now)),"resident cannot manufacture automated exemption");
        denied(()->new AsyncDebit(terms(T,R,300),PREPARED,now,now,"",false,new SpendingAdmission("UNAVAILABLE",0,0,0,0,0,now)),"unavailable provider cannot leave accepted intent");
        denied(()->new SpendingAdmission("POLICY",1,SpendingAdmission.MAX+1,0,0,0,now),"invalid provider ceiling rejected");
        System.out.println("SpendingAdmissionSmoke PASS "+checks+" checks");
    }
}
