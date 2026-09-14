package ru.neverland.townyjustice;
import java.util.UUID;
import static ru.neverland.townyjustice.JusticePayment.Step.*;
/** Persist intent before either bank call; ambiguous results require explicit reconciliation. */
public final class JusticePayments {
    public interface Store{JusticePayment get(UUID id);void put(JusticePayment payment)throws Exception;}
    public interface Bank{boolean debit(JusticePayment p)throws Exception;boolean credit(JusticePayment p)throws Exception;}
    private JusticePayments(){}
    public static void advance(UUID id,long now,long retry,Store store,Bank bank)throws Exception{
        var p=store.get(id);if(p==null)throw new IllegalArgumentException("Платёж не найден");
        if(p.step()==READY){if(p.debitRequired()){store.put(p.step(DEBIT_PENDING,now));boolean result=bank.debit(p);store.put(p.step(result?DEBITED:CANCELLED,result?0:now));}else store.put(p.step(DEBITED,0));return;}
        if(p.step()==DEBITED){if(!p.creditRequired()){store.put(p.step(COMPLETE,now));return;}if(p.check()!=0&&now-p.check()<retry)return;store.put(p.step(CREDIT_PENDING,now));boolean result=bank.credit(p);store.put(p.step(result?COMPLETE:DEBITED,now));}
    }
    public static JusticePayment resolve(JusticePayment p,String result,long now){return switch(result){
        case "debit-applied"->{if(p.step()!=DEBIT_PENDING||!p.debitRequired())throw new IllegalArgumentException("Нет спорного списания");yield p.step(DEBITED,0);}
        case "debit-not-applied"->{if(p.step()!=DEBIT_PENDING||!p.debitRequired())throw new IllegalArgumentException("Нет спорного списания");yield p.step(CANCELLED,now);}
        case "credit-applied"->{if(p.step()!=CREDIT_PENDING||!p.creditRequired())throw new IllegalArgumentException("Нет спорного зачисления");yield p.step(COMPLETE,now);}
        case "credit-not-applied"->{if(p.step()!=CREDIT_PENDING||!p.creditRequired())throw new IllegalArgumentException("Нет спорного зачисления");yield p.step(DEBITED,0);}
        default->throw new IllegalArgumentException("Решения: debit-applied, debit-not-applied, credit-applied, credit-not-applied");};}
}
