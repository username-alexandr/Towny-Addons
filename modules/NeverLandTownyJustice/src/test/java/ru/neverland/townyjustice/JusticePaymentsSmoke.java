package ru.neverland.townyjustice;
import java.util.*;
import static ru.neverland.townyjustice.JusticePayment.Step.*;
public final class JusticePaymentsSmoke {
    static void check(boolean value){if(!value)throw new AssertionError();}
    static JusticePayment payment(JusticePayment.Kind kind){return new JusticePayment(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),kind,12000,READY,1,0);}
    static final class Store implements JusticePayments.Store{JusticePayment p;int writes,fail;Store(JusticePayment p){this.p=p;}public JusticePayment get(UUID id){return p;}public void put(JusticePayment next)throws Exception{if(++writes==fail)throw new Exception("disk");p=next;}}
    static final class Bank implements JusticePayments.Bank{int debits,credits;boolean rejectDebit,rejectCredit,lostDebit,lostCredit;public boolean debit(JusticePayment p)throws Exception{debits++;if(lostDebit)throw new Exception("lost debit reply");return !rejectDebit;}public boolean credit(JusticePayment p)throws Exception{credits++;if(lostCredit)throw new Exception("lost credit reply");return !rejectCredit;}}
    static void advance(Store s,Bank b,long now)throws Exception{JusticePayments.advance(s.p.id(),now,10,s,b);}
    public static void main(String[] args)throws Exception{
        for(var kind:JusticePayment.Kind.values()){var s=new Store(payment(kind));var b=new Bank();for(int i=0;i<10;i++)advance(s,b,100+i*20);check(s.p.step()==COMPLETE);check(b.debits==(s.p.debitRequired()?1:0)&&b.credits==(s.p.creditRequired()?1:0));}
        for(int fail=1;fail<=4;fail++){var s=new Store(payment(JusticePayment.Kind.FINE));s.fail=fail;var b=new Bank();try{for(int i=0;i<4;i++)advance(s,b,100+i*20);}catch(Exception expected){}int d=b.debits,c=b.credits;for(int i=0;i<4;i++)advance(s,b,300+i*20);if(fail==1)check(d==0&&b.debits==1&&b.credits==1);if(fail==2)check(s.p.step()==DEBIT_PENDING&&b.debits==d&&b.credits==0);if(fail==3)check(b.debits==1&&c==0&&b.credits==1);if(fail==4)check(s.p.step()==CREDIT_PENDING&&b.debits==1&&b.credits==c);}
        for(boolean debit:List.of(true,false)){var s=new Store(payment(JusticePayment.Kind.FINE));var b=new Bank();b.lostDebit=debit;b.lostCredit=!debit;try{advance(s,b,100);advance(s,b,120);}catch(Exception expected){}for(int i=0;i<5;i++)advance(s,b,200+i*20);check(b.debits==1&&b.credits==(debit?0:1));s.p=JusticePayments.resolve(s.p,debit?"debit-applied":"credit-applied",400);b.lostCredit=false;advance(s,b,500);check(s.p.step()==COMPLETE&&b.debits==1&&b.credits==1);}
        var s=new Store(payment(JusticePayment.Kind.FINE));var b=new Bank();b.rejectDebit=true;advance(s,b,100);advance(s,b,200);check(s.p.step()==CANCELLED&&b.debits==1&&b.credits==0);
        s=new Store(payment(JusticePayment.Kind.FINE));b=new Bank();advance(s,b,100);b.rejectCredit=true;advance(s,b,120);advance(s,b,121);check(b.credits==1&&s.p.step()==DEBITED);b.rejectCredit=false;advance(s,b,131);check(s.p.step()==COMPLETE&&b.debits==1&&b.credits==2);
        boolean rejected=false;try{JusticePayments.resolve(s.p,"credit-not-applied",200);}catch(IllegalArgumentException e){rejected=true;}check(rejected);
        System.out.println("Justice payments: four flows, every persistence boundary, lost replies, negative receipts and retry timing PASS");
    }
}
