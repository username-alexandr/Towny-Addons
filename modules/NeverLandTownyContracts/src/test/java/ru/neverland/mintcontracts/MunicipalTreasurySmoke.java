package ru.neverland.mintcontracts;
import java.util.*;
import java.nio.file.*;
import java.io.IOException;
import org.bukkit.Material;
import ru.neverland.mintcontracts.model.*;
import ru.neverland.mintcontracts.service.*;
import ru.neverland.mintcontracts.integration.MunicipalBank;

/** Legacy municipal reserve upgrade: stable ID, durable route, no replay of old unknown effects. */
public final class MunicipalTreasurySmoke {
 static int checks;static void check(boolean b,String why){if(!b)throw new AssertionError(why);checks++;}
 interface Work{void run()throws Exception;}static void refuses(Work work)throws Exception{try{work.run();}catch(Exception expected){checks++;return;}throw new AssertionError("Expected refusal");}
 static final class Store implements MunicipalPayments.Store{MunicipalPayment row;boolean failIntent,failAck;int results;public MunicipalPayment get(UUID id){return row;}public void put(MunicipalPayment p)throws Exception{if(failIntent)throw new IOException("disk");row=p;}public void result(MunicipalPayment p,boolean paid)throws Exception{if(failAck)throw new IOException("ack");row=p;results++;}}
 static final class Bank implements MunicipalPayments.Bank{int polls,transfers;MunicipalPayments.Outcome outcome=MunicipalPayments.Outcome.WAITING;public boolean asynchronous(MunicipalPayment p){return true;}public boolean transfer(MunicipalPayment p){transfers++;throw new AssertionError("Legacy debit must not be invoked");}public MunicipalPayments.Outcome poll(MunicipalPayment p){check(p.phase()==MunicipalPayment.Phase.TREASURY,"route durable before async request");polls++;return outcome;}}
 static Map<String,Object> proof(MunicipalPayment p){return new HashMap<>(Map.ofEntries(Map.entry("operation",p.id().toString()),Map.entry("town",p.town().toString()),Map.entry("cents",p.cents()),Map.entry("category","infrastructure"),Map.entry("source","contracts"),Map.entry("owner","NeverLandTownyContracts"),Map.entry("actor",new UUID(0,0).toString()),Map.entry("reason","[contracts:"+p.id()+"] Муниципальный контракт"),Map.entry("kind","DEBIT"),Map.entry("status","PAID"),Map.entry("backingAvailable",true)));}
 public static void main(String[] ignored)throws Exception{
  UUID town=UUID.randomUUID(),contract=UUID.randomUUID();var reserve=new MunicipalPayment(MunicipalPayment.key(contract,"reserve"),contract,town,town,MunicipalPayment.Kind.RESERVE,12345,MunicipalPayment.Phase.READY,1);var store=new Store();store.row=reserve;var bank=new Bank();var service=new MunicipalPayments(store,bank);
  store.failIntent=true;refuses(()->service.process(reserve.id()));check(bank.polls==0&&bank.transfers==0,"no request before persisted route");store.failIntent=false;service.process(reserve.id());check(store.row.phase()==MunicipalPayment.Phase.TREASURY&&store.results==0,"pending funding cannot publish a funded result");
  bank.outcome=MunicipalPayments.Outcome.REVIEW;service.process(reserve.id());check(store.results==0&&bank.transfers==0,"uncertain treasury result remains pending without legacy fallback");
  bank.outcome=MunicipalPayments.Outcome.PAID;store.failAck=true;refuses(()->service.process(reserve.id()));check(store.row.phase()==MunicipalPayment.Phase.TREASURY,"lost caller ACK retains authoritative lookup route");store.failAck=false;new MunicipalPayments(store,bank).process(reserve.id());check(store.row.phase()==MunicipalPayment.Phase.DONE&&store.results==1&&bank.transfers==0,"restart reconciles PAID once without another bank invocation");int polls=bank.polls;service.process(reserve.id());check(bank.polls==polls&&store.results==1,"terminal reserve is idempotent");
  store.row=reserve.phase(MunicipalPayment.Phase.PENDING);service.process(reserve.id());check(bank.polls==polls,"old unknown PENDING is never converted to a new treasury payment");service.resolve(reserve.id(),false);check(store.row.phase()==MunicipalPayment.Phase.REJECTED,"old reserve still permits explicit not-paid reconciliation");
  store.row=reserve.phase(MunicipalPayment.Phase.TREASURY);refuses(()->service.resolve(reserve.id(),true));bank.outcome=MunicipalPayments.Outcome.REJECTED;service.process(reserve.id());check(store.row.phase()==MunicipalPayment.Phase.REJECTED,"definitive treasury refusal closes unfunded publication");
  var proof=proof(reserve);check(MunicipalBank.proof(reserve,proof)==MunicipalPayments.Outcome.PAID,"exact paid proof accepted");proof.put("backingAvailable",false);check(MunicipalBank.proof(reserve,proof)==MunicipalPayments.Outcome.REVIEW,"refunded backing cannot fund a new municipal escrow");
  for(String key:List.of("operation","town","actor","owner","reason","source","category","kind","cents")){var wrong=proof(reserve);wrong.put(key,key.equals("cents")?12346L:"different");refuses(()->MunicipalBank.proof(reserve,wrong));}
  var c=new ActiveContract(contract,town,"awaiting",1,60001,0,10,123.45,Map.of());c.snapshot(new ContractDefinition("awaiting","Ожидающий заказ",ContractType.MOB_KILL,Material.IRON_SWORD,0,List.of(),"ZOMBIE",null,10,123.45,60));c.funded(false);c.fundingPaused(true);c.timer(c.timer().edit("pause",0,1001));
  Path folder=Files.createTempDirectory("municipal-treasury"),file=folder.resolve("data.yml");var repo=new ContractRepository(file.toFile(),java.util.logging.Logger.getAnonymousLogger());repo.add(c);repo.payment(reserve.phase(MunicipalPayment.Phase.TREASURY));repo.saveOrThrow();var reopened=new ContractRepository(file.toFile(),java.util.logging.Logger.getAnonymousLogger());reopened.load();var restored=reopened.find(town,contract.toString());check(restored!=null&&!restored.funded()&&restored.fundingPaused()&&restored.timer().paused(),"restart keeps unfunded task paused");check(reopened.payments().get(reserve.id()).phase()==MunicipalPayment.Phase.TREASURY,"restart retains exact payment ID and route");
  Files.delete(file);Files.delete(folder);System.out.println("MunicipalTreasurySmoke PASS "+checks+" checks; ACK, replay isolation, exact receipt identity and saved funding pause");
 }
}
