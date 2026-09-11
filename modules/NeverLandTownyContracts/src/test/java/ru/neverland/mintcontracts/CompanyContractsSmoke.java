package ru.neverland.mintcontracts;
import java.util.*;
import java.nio.file.*;
import ru.neverland.mintcontracts.model.*;
import ru.neverland.mintcontracts.service.ContractRepository;

public final class CompanyContractsSmoke {
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    @FunctionalInterface interface Op{void run()throws Exception;}
    private static void fails(Op op,String label)throws Exception{try{op.run();}catch(Exception expected){return;}throw new AssertionError(label);}
    public static void main(String[] args)throws Exception {
        ContractMathSmoke.main(args);
        UUID town=UUID.randomUUID(),company=UUID.randomUUID(),actor=UUID.randomUUID();
        ActiveContract c=new ActiveContract(UUID.randomUUID(),town,"iron_reserve",1,1000,0,100,100,Map.of());c.companyId(company);c.add(actor,25);
        fails(()->c.companyId(UUID.randomUUID()),"started contract cannot be stolen");c.settlement(ContractStatus.EXPIRED,2500,7500);
        check(c.add(actor,5)==0,"settlement freezes progress");fails(()->c.settlement(ContractStatus.CANCELLED,0,10000),"settlement conditions immutable");fails(()->c.settlement(ContractStatus.EXPIRED,2501,7500),"escrow conservation");
        Path dir=Files.createTempDirectory("company-contract-smoke");Path file=dir.resolve("contract-data.yml");var logger=java.util.logging.Logger.getAnonymousLogger();
        var repo=new ContractRepository(file.toFile(),logger);repo.load();repo.add(c);check(repo.save(),"assignment save");var reload=new ContractRepository(file.toFile(),logger);reload.load();ActiveContract restored=reload.find(town,c.id().toString());
        check(restored.companyId().equals(company)&&restored.progress()==25&&restored.settlementPayout()==2500&&restored.settlementRefund()==7500&&restored.settlementStatus()==ContractStatus.EXPIRED,"durable corporate settlement");
        reload.addHistory(restored,ContractStatus.EXPIRED,25,75,30,1001);check(reload.save(),"history after custody transfer");reload.load();check(reload.active(town).isEmpty()&&reload.history(town).size()==1,"completed assignment no longer active");
        // Legacy contracts have no company or settlement keys and retain their contributions.
        repo=new ContractRepository(file.toFile(),logger);repo.add(new ActiveContract(UUID.randomUUID(),town,"miners_duty",1,1000,10,100,100,Map.of(actor,10)));check(repo.save(),"legacy fixture");
        String legacy=Files.readString(file).replace("schema: 2\n","").replaceAll("(?m)^ +(?:company|settlement-status|settlement-payout|settlement-refund):.*\\R","");Files.writeString(file,legacy);repo.load();check(repo.active(town).get(0).companyId()==null&&repo.active(town).get(0).progress()==10,"legacy migration");
        Files.writeString(file,"towns: wrong\n");var corrupt=new ContractRepository(file.toFile(),logger);fails(corrupt::load,"corrupt data refused");check(!corrupt.save(),"corrupt data not overwritten");
        Files.delete(file);Files.delete(dir);System.out.println("CompanyContractsSmoke OK: ownership, escrow, settlement restart and legacy migration");
    }
}
