package ru.neverland.townycompanies;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static ru.neverland.townycompanies.CompanyData.*;
import static ru.neverland.townycompanies.ContractAccounting.*;

public final class ContractAccountingSmoke {
    static int checks;static final UUID COMPANY=UUID.randomUUID(),TOWN=UUID.randomUUID(),ACTOR=UUID.randomUUID();
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    static void reject(Runnable work,String message){try{work.run();throw new AssertionError(message);}catch(IllegalArgumentException expected){checks++;}}
    static Report report(UUID contract){return new Report(reportId(COMPANY,contract),COMPANY,TOWN,contract,"Поставка досок","Дубовые доски",64,10000,Budget.unknown(),null,"",ACTOR,1,1);}
    static Company company(){return new Company(COMPANY,TOWN,"Test Company",Kind.SHOP,ACTOR,Map.of(ACTOR,Role.OWNER),Map.of(),null,0,100000,0,100000,1,false);}
    static CompanyLedger.State bank(Map<UUID,Payment> payments,Receipt receipt){var bankPayments=new HashMap<>(payments);if(receipt!=null&&receipt.refund()>0)bankPayments.put(receipt.contract(),new Payment(receipt.contract(),COMPANY,TOWN,Purpose.REFUND,receipt.refund(),Phase.DONE,1));return new CompanyLedger.State(Map.of(COMPANY,company()),bankPayments,receipt==null?Map.of():Map.of(receipt.contract(),receipt));}
    static State await(CompletionStage<State> stage)throws Exception{return stage.toCompletableFuture().get(10,TimeUnit.SECONDS);}
    public static void main(String[] args)throws Exception{
        var first=report(UUID.randomUUID());var second=report(UUID.randomUUID());var state=put(put(State.empty(),first,0),second,0);var emptyBank=bank(Map.of(),null);
        var unknown=summary(state,first,emptyBank);check(unknown.plannedMargin()==null&&unknown.received()==null&&unknown.accountedMargin()==null,"unknown costs and unpaid revenue never become a fictitious profit");
        var edited=first.edit(new Budget(2000L,1000L,500L),2000L,"Закуплены у жителей",ACTOR,2);state=put(state,edited,1);check(summary(state,edited,emptyBank).plannedMargin()==6500,"all three explicit budget categories determine planned margin");
        var wage=new Payment(UUID.randomUUID(),COMPANY,ACTOR,Purpose.SALARY,1000,Phase.DONE,1);var tax=new Payment(UUID.randomUUID(),COMPANY,TOWN,Purpose.TAX,500,Phase.DONE,1);UUID allocation=UUID.randomUUID();var allocated=allocate(state,first.id(),2,wage,allocation,600,ACTOR,3);check(allocated(allocated,wage.id())==600,"a partial actual salary payment can be allocated once");
        check(allocate(allocated,first.id(),2,wage,allocation,600,ACTOR,3)==allocated,"same allocation proof is idempotent even with original report revision");
        var original=allocated;reject(()->allocate(original,first.id(),3,wage,allocation,601,ACTOR,3),"same ID cannot claim different amount");
        var split=allocate(allocated,second.id(),1,wage,UUID.randomUUID(),400,ACTOR,4);check(allocated(split,wage.id())==1000,"split across different contracts conserves paid source amount");var full=split;reject(()->allocate(full,second.id(),2,wage,UUID.randomUUID(),1,ACTOR,5),"already allocated payment cannot fund another cost report");
        reject(()->allocate(original,first.id(),3,wage.phase(Phase.PENDING),UUID.randomUUID(),1,ACTOR,5),"unverified pending bank operation cannot be accounted as spent");
        var deposit=new Payment(UUID.randomUUID(),COMPANY,ACTOR,Purpose.DEPOSIT,1000,Phase.DONE,1);reject(()->allocate(original,first.id(),3,deposit,UUID.randomUUID(),1,ACTOR,5),"owner deposits are not contract costs");
        var foreign=new Payment(UUID.randomUUID(),UUID.randomUUID(),ACTOR,Purpose.SALARY,1000,Phase.DONE,1);reject(()->allocate(original,first.id(),3,foreign,UUID.randomUUID(),1,ACTOR,5),"other company payment cannot be attributed to a report");
        var withTax=allocate(split,first.id(),3,tax,UUID.randomUUID(),500,ACTOR,6);var receipt=new Receipt(first.contract(),COMPANY,TOWN,9000,1000);var paid=bank(Map.of(wage.id(),wage,tax.id(),tax),receipt);var calculated=summary(withTax,withTax.reports().get(first.id()),paid);check(calculated.received()==9000&&calculated.refund()==1000&&calculated.accountedMargin()==5900,"actual company payout excludes refund; declared materials and allocated salary/tax determine accounted remainder");
        check(calculated.unallocatedWages()==0&&calculated.unallocatedTaxes()==0,"payment remainders are shared across all reports, not double counted per contract");
        var pending=bank(Map.of(wage.id(),wage.phase(Phase.PENDING),tax.id(),tax),receipt);var lost=summary(withTax,withTax.reports().get(first.id()),pending);check(lost.unverified()==1&&lost.accountedMargin()==null&&lost.paidWages()==0,"changed or missing source receipt makes the report explicitly incomplete");
        var reversed=reverse(withTax,allocation,4,ACTOR,7);check(allocated(reversed,wage.id())==400&&reversed.allocations().get(allocation).reversed(),"undo retains original record and releases only accounting capacity");
        check(paid.equals(bank(Map.of(wage.id(),wage,tax.id(),tax),receipt)),"accounting and undo never change authoritative company funds or receipts");
        var restored=allocate(reversed,first.id(),5,wage,UUID.randomUUID(),600,ACTOR,8);check(allocated(restored,wage.id())==1000,"released part can be deliberately reassigned without a second bank transfer");
        var finalState=restored;reject(()->put(finalState,edited,1),"stale budget update cannot overwrite newer accounting");
        reject(()->new Budget(-1L,0L,0L),"negative cost rejected");reject(()->new Report(first.id(),COMPANY,TOWN,first.contract(),"x","x",1,100,Budget.unknown(),1L,"",ACTOR,1,1),"declared material cost requires explanation");
        Path root=Files.createTempDirectory("neverland-contract-accounting-");var file=root.resolve("accounts.yml");var store=new ContractAccountsStore(file);store.load();await(store.commit(State.empty(),restored));store.prepareClose();store.awaitClosed();var reopened=new ContractAccountsStore(file);reopened.load();check(reopened.state().equals(restored),"all original estimates, allocations and reversed entries survive real file restart");reopened.prepareClose();reopened.awaitClosed();
        var broken=root.resolve("blocked.yml");var failed=new ContractAccountsStore(broken);failed.load();Files.createDirectory(broken);boolean error=false;try{await(failed.commit(State.empty(),state));}catch(ExecutionException expected){error=true;}check(error&&failed.state().equals(State.empty())&&!failed.writable(),"actual atomic replacement failure publishes no unconfirmed cost state");failed.prepareClose();failed.awaitClosed();
        var corrupt=root.resolve("corrupt.yml");Files.writeString(corrupt,"schema: 99\n");var invalid=new ContractAccountsStore(corrupt);boolean refused=false;try{invalid.load();}catch(java.io.IOException expected){refused=true;}check(refused&&!invalid.writable(),"unknown saved schema is rejected without silent reset");invalid.prepareClose();invalid.awaitClosed();
        for(int i=1;i<=10000;i++){var baseline=put(put(State.empty(),first,0),second,0);long part=i%1000+1;var one=allocate(baseline,first.id(),1,wage,UUID.randomUUID(),part,ACTOR,1);if(part<1000)one=allocate(one,second.id(),1,wage,UUID.randomUUID(),1000-part,ACTOR,1);if(allocated(one,wage.id())!=1000)throw new AssertionError("source amount not conserved");}
        System.out.println("ContractAccountingSmoke PASS "+checks+" checks and 10000 payment splits");
    }
}
