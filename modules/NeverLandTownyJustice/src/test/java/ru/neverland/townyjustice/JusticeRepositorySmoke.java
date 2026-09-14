package ru.neverland.townyjustice;
import java.util.*;import java.nio.file.*;
public final class JusticeRepositorySmoke {
    interface Work{void run()throws Exception;}static void check(boolean b){if(!b)throw new AssertionError();}static void denied(Work w)throws Exception{boolean hit=false;try{w.run();}catch(Exception e){hit=true;}check(hit);}
    static JusticeCase fine(){return new JusticeCase(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),JusticeCase.Kind.FINE,1000,0,"Test offence",1,999999,JusticeCase.Phase.OPEN,null,null,null,null,0,0);}
    public static void main(String[] args)throws Exception{
        var dir=Files.createTempDirectory("justice-store");var file=dir.resolve("data.yml");var r=new JusticeRepository(file);r.load();var c=fine();r.put(c,null);var p=new JusticePayment(UUID.randomUUID(),c.id(),c.town(),c.subject(),JusticePayment.Kind.FINE,c.amount(),JusticePayment.Step.READY,2,0);r.put(c.payment(p.id(),JusticeCase.Phase.PAYING),p);var copy=new JusticeRepository(file);copy.load();check(copy.state().equals(r.state()));
        denied(()->new JusticeRepository.State(Map.of(c.id(),c.phase(JusticeCase.Phase.PAID)),Map.of(),Map.of()));denied(()->new JusticeRepository.State(Map.of(c.id(),c),Map.of(p.id(),p),Map.of()));
        byte[] good=Files.readAllBytes(file);Files.writeString(file,new String(good,java.nio.charset.StandardCharsets.UTF_8).replace("amount: 1000","amount: 1000.5"));denied(copy::load);check(!copy.writable()&&copy.state().equals(r.state()));denied(()->copy.put(c,null));Files.write(file,good);copy.load();check(copy.writable());
        Files.delete(file);Files.createDirectory(file);Files.writeString(file.resolve("block"),"x");var before=r.state();denied(()->r.put(p.step(JusticePayment.Step.DEBIT_PENDING,10)));check(!r.writable()&&r.state().equals(before));
        var w=new JusticeCase(UUID.randomUUID(),c.town(),c.subject(),c.issuer(),JusticeCase.Kind.WARRANT,500,2,"Wanted offence",1,999999,JusticeCase.Phase.WANTED,null,null,null,null,0,0);denied(()->new JusticeRepository.State(Map.of(w.id(),w),Map.of(),Map.of()));
        var fund=new JusticePayment(UUID.randomUUID(),w.id(),w.town(),w.subject(),JusticePayment.Kind.BOUNTY_FUND,500,JusticePayment.Step.COMPLETE,1,2);var funded=w.payment(fund.id(),JusticeCase.Phase.WANTED);new JusticeRepository.State(Map.of(w.id(),funded),Map.of(fund.id(),fund),Map.of());
        denied(()->new JusticeRepository.State(Map.of(w.id(),funded.phase(JusticeCase.Phase.CANCELLED)),Map.of(fund.id(),fund),Map.of()));var refund=new JusticePayment(UUID.randomUUID(),w.id(),w.town(),w.subject(),JusticePayment.Kind.BOUNTY_REFUND,500,JusticePayment.Step.READY,3,0);var refunding=funded.settlement(refund.id(),JusticeCase.Phase.REFUNDING);new JusticeRepository.State(Map.of(w.id(),refunding),Map.of(fund.id(),fund,refund.id(),refund),Map.of());
        denied(()->new JusticeRepository.State(Map.of(w.id(),refunding),Map.of(fund.id(),fund.step(JusticePayment.Step.DEBIT_PENDING,2),refund.id(),refund),Map.of()));
        System.out.println("Justice repository: restart, strict corruption, frozen failed writes and escrow consistency PASS");
    }
}
