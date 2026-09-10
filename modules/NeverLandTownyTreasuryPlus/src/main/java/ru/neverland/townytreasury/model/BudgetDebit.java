package ru.neverland.townytreasury.model;
import java.util.function.BooleanSupplier;
/** Journal intent before an external debit; never infer or retry an ambiguous outcome. */
public final class BudgetDebit {
    public interface Journal {CityLedger get();void put(CityLedger value)throws Exception;void flush()throws Exception;void fault(Exception error);}
    private BudgetDebit(){}
    public static boolean execute(Journal journal,CityLedger.Pending pending,BooleanSupplier bank,BooleanSupplier observed){
        if(!LedgerEngine.canSpend(journal.get(),pending.category(),pending.amount()))return false;
        try{journal.put(LedgerEngine.prepare(journal.get(),pending));}catch(Exception ex){journal.fault(ex);return false;}
        boolean returned=false;RuntimeException failure=null;
        try{returned=bank.getAsBoolean();}catch(RuntimeException ex){failure=ex;}
        try{journal.flush();if(!observed.getAsBoolean()&&!returned&&failure==null)journal.put(LedgerEngine.cancel(journal.get(),pending.id()));}catch(Exception ex){journal.fault(ex);}
        if(observed.getAsBoolean())return true;
        if(returned||failure!=null)throw new IllegalStateException("Результат списания не подтверждён: нужна сверка счёта "+pending.id(),failure);
        return false;
    }
}
