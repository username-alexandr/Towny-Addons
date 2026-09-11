package ru.neverland.townycompanies;
import java.util.*;
import java.io.IOException;
import java.nio.file.*;
import static ru.neverland.townycompanies.CompanyData.*;

public final class CompaniesSmoke {
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    @FunctionalInterface interface Op{void run()throws Exception;}
    private static void fails(Op op,String label)throws Exception{try{op.run();}catch(Exception expected){return;}throw new AssertionError(label);}
    private static final class Store implements CompanyLedger.Store {
        CompanyLedger.State state=CompanyLedger.State.empty();boolean fail;int writes,failAt=-1;
        public CompanyLedger.State state(){return state;}public boolean writable(){return !fail;}
        public void save(CompanyLedger.State next)throws IOException{writes++;if(fail||writes==failAt)throw new IOException("disk");state=next;}
    }
    private static final class Bank implements CompanyLedger.Bank {int calls;boolean reject,ambiguous;long personal=100_000,town;
        public boolean transfer(Payment p)throws Exception{calls++;if(reject)return false;
            switch(p.purpose()){case DEPOSIT->personal-=p.amount();case WITHDRAW->personal+=p.amount();case TAX,REFUND->town+=p.amount();}
            if(ambiguous)throw new IOException("lost acknowledgement");return true;
        }
    }
    public static void main(String[] args)throws Exception {
        UUID owner=UUID.randomUUID(),town=UUID.randomUUID(),id=UUID.randomUUID();
        Company c=new Company(id,town,"Северная шахта",Kind.MINE,owner,Map.of(owner,Role.OWNER),Map.of(),null,0,0,0,1000,0,false);
        Store store=new Store();Bank bank=new Bank();CompanyLedger ledger=new CompanyLedger(store,bank);ledger.put(c);
        Payment deposit=ledger.begin(id,owner,Purpose.DEPOSIT,20_000,1);ledger.process(deposit.id());check(bank.personal==80_000&&ledger.company(id).balance()==20_000,"deposit conserved");
        ledger.process(deposit.id());check(bank.calls==1,"finished payment not replayed");
        ledger.assess(id,5000,10_000,1000);check(ledger.company(id).debt()==5000&&ledger.company(id).nextTax()==11_000,"one period after downtime");
        ledger.assess(id,5000,50_000,1000);check(ledger.company(id).debt()==5000,"no stacking delinquent taxes");
        var tax=ledger.begin(id,town,Purpose.TAX,5000,50_001);ledger.process(tax.id());check(bank.town==5000&&ledger.company(id).debt()==0&&ledger.company(id).balance()==15_000,"tax credited to city once");
        bank.reject=true;var withdraw=ledger.begin(id,owner,Purpose.WITHDRAW,4000,2);ledger.process(withdraw.id());check(ledger.company(id).balance()==15_000&&bank.personal==80_000,"rejected withdrawal restores reserve");bank.reject=false;
        bank.ambiguous=true;var pending=ledger.begin(id,owner,Purpose.DEPOSIT,1000,3);ledger.process(pending.id());int calls=bank.calls;
        check(store.state().payments().get(pending.id()).phase()==Phase.PENDING&&ledger.company(id).balance()==15_000,"ambiguous debit held");
        ledger=new CompanyLedger(store,bank);ledger.process(pending.id());check(bank.calls==calls,"restart does not repeat debit");ledger.finish(pending.id(),true);check(ledger.company(id).balance()==16_000,"manual reconciliation credits once");
        final CompanyLedger current=ledger;fails(()->current.finish(pending.id(),true),"reconciliation replay refused");bank.ambiguous=false;
        UUID contract=UUID.randomUUID();Receipt receipt=new Receipt(contract,id,town,6000,4000);check(ledger.settle(receipt,4),"escrow accepted");check(ledger.settle(receipt,5),"idempotent escrow replay");check(ledger.company(id).balance()==22_000,"single company reward");
        fails(()->current.settle(new Receipt(contract,id,town,7000,3000),5),"changed payout replay refused");
        ledger.process(contract);ledger.process(contract);check(bank.town==9000,"single city refund");
        check(bank.personal+bank.town+ledger.company(id).balance()==110_000,"money conserved including pre-funded contract escrow");
        // Commit of the bank result fails: durable intent remains PENDING and can be reconciled.
        var lost=ledger.begin(id,owner,Purpose.WITHDRAW,2000,6);store.failAt=store.writes+2;fails(()->current.process(lost.id()),"result write failure");check(store.state().payments().get(lost.id()).phase()==Phase.PENDING,"intent survived result failure");
        calls=bank.calls;new CompanyLedger(store,bank).process(lost.id());check(bank.calls==calls,"no duplicate after result write failure");store.failAt=-1;ledger.finish(lost.id(),true);
        store.fail=true;calls=bank.calls;fails(()->current.begin(id,owner,Purpose.DEPOSIT,100,7),"intent write failure");check(bank.calls==calls,"no bank call before intent saved");store.fail=false;
        // Strict immutable membership, owner and balance constraints.
        check(c.manages(owner)&&!c.manages(UUID.randomUUID()),"roles");fails(()->c.members().clear(),"immutable team");
        fails(()->new Company(UUID.randomUUID(),town,"Другая шахта",Kind.MINE,owner,Map.of(),Map.of(),null,0,0,0,0,0,false),"owner required");
        fails(()->c.funds(-1,0,0),"negative money");fails(()->c.funds(MAX,0,0).funds(MAX+1,0,0),"bounded money");fails(()->cleanName("§4Компания"),"format injection");fails(()->cents("1.001"),"fractional cents");
        check(nameKey("  СЕВЕРНАЯ   шахта ").equals(nameKey(c.name())),"normalized company name");
        var duplicate=new LinkedHashMap<>(ledger.state().companies());duplicate.put(UUID.randomUUID(),c);fails(()->new CompanyLedger.State(duplicate,Map.of(),Map.of()),"mismatched identity rejected");
        // Atomic persistent round-trip with receipt and pending transfer recovery.
        Path dir=Files.createTempDirectory("companies-smoke");Path file=dir.resolve("companies.yml");var repo=new CompanyRepository(file);repo.load();repo.save(ledger.state());var reloaded=new CompanyRepository(file);reloaded.load();check(reloaded.state().equals(ledger.state()),"full ledger round trip");
        String original=Files.readString(file);Files.writeString(file,"companies: [broken\n");fails(reloaded::load,"corrupt YAML refused");check(!reloaded.writable(),"corrupt ledger freezes writes");fails(()->reloaded.save(CompanyLedger.State.empty()),"cannot overwrite corrupt data");
        Files.writeString(file,original.replace("schema: 1","schema: 99"));fails(reloaded::load,"future schema refused");
        Files.writeString(file,original);reloaded.load();check(reloaded.state().receipts().get(contract).equals(receipt),"receipt preserved");
        Object account=new Object(),other=new Object();var evidence=new PaymentEvidence(account,true,"[test]",100);
        evidence.observe(other,true,1,"[test]");evidence.observe(account,false,1,"[test]");evidence.observe(account,true,2,"[test]");evidence.observe(account,true,1,"other");check(!evidence.observed(),"bank evidence identity direction amount token");
        fails(()->evidence.result(true),"success without receipt is ambiguous");evidence.observe(account,true,1,"[test] reason");check(evidence.result(false),"confirmed payment survives false return");
        Files.delete(file);Files.delete(dir);System.out.println("CompaniesSmoke OK: banking, escrow, tax, restart, write failures, roles, persistence and bank evidence");
    }
}
