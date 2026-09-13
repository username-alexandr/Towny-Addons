package ru.neverland.minttrade;

import java.util.*;
import java.nio.file.*;
import ru.neverland.core.ReputationOutcome;
import ru.neverland.minttrade.contract.*;
import static ru.neverland.minttrade.contract.SupplyContract.*;

public final class SupplyReputationSmoke {
    static int checks;
    static final class Store implements SupplyProcessor.Store {
        SupplyContract value;Map<String,ReputationOutcome> outcomes=new LinkedHashMap<>();
        Store(SupplyContract c){value=c;}
        public SupplyContract get(UUID id){return value;}
        public void put(SupplyContract c){value=c;}
        public void put(SupplyContract c,List<ReputationOutcome> events){for(var e:events){var prior=outcomes.putIfAbsent(e.id(),e);check(prior==null||prior.equals(e),"retry keeps exact event payload");}value=c;}
    }
    public static void main(String[] args)throws Exception {
        var c=SupplyContractSmoke.proposal().accept(SupplyContractSmoke.BUYER,SupplyContractSmoke.START);
        long due=c.nextDue(),retry=30_000,grace=86_400_000;
        var store=new Store(c);var bank=new SupplyContractSmoke.Bank();bank.goods=0;
        advance(store,bank,due,retry,grace);check(store.outcomes.isEmpty(),"grace period without breach");
        advance(store,bank,due+grace,retry,grace);
        check(store.outcomes.size()==1,"one late confirmed stock shortage");
        var incident=store.outcomes.values().iterator().next();
        check(incident.subject().equals(c.terms().seller()) && incident.rule().equals("SUPPLY_MISSED"),"seller alone is responsible");
        for(int i=1;i<8;i++)advance(store,bank,due+grace+i*retry,retry,grace);
        check(store.outcomes.size()==1,"retries of same scheduled party never add incidents");
        for(String gate:List.of("bank unavailable","API unavailable","budget unavailable")) {
            store=new Store(c);bank=new SupplyContractSmoke.Bank();bank.gate=gate;
            advance(store,bank,due+grace,retry,grace);check(store.outcomes.isEmpty(),"technical readiness failure never counts as breach");
        }
        store=new Store(c);bank=new SupplyContractSmoke.Bank();bank.busy=true;advance(store,bank,due+grace,retry,grace);check(store.outcomes.isEmpty(),"busy stock is not a breach");
        store=new Store(c);bank=new SupplyContractSmoke.Bank();bank.bankRefuse=true;
        for(int i=0;i<7;i++)advance(store,bank,due+grace,retry,grace);check(store.outcomes.isEmpty(),"undifferentiated bank refusal is not a breach");
        store=new Store(c);bank=new SupplyContractSmoke.Bank();
        for(int i=0;i<7;i++)advance(store,bank,due,retry,grace);
        check(store.value.deliveries()==1 && store.outcomes.size()==2 && store.outcomes.values().stream().allMatch(o->o.rule().equals("SUPPLY_COMPLETED")),"completion rewards both parties only after delivery and payment");
        check(store.value.terms().cents()==c.terms().cents() && bank.buyer==300000 && bank.seller==200000,"signed supply price and money conservation unchanged");
        Path dir=Files.createTempDirectory("supply-outbox-");
        try {
            Path file=dir.resolve("data.yml");var repo=new SupplyRepository(file);repo.load();
            repo.put(store.value,List.copyOf(store.outcomes.values()));var reopened=new SupplyRepository(file);reopened.load();
            check(reopened.get(c.terms().id()).deliveries()==1 && reopened.pendingReputation()==2,"source completion and pending outcomes survive same YAML restart");
            Files.delete(file);Files.createDirectory(file);Files.writeString(file.resolve("keep"),"original");
            boolean failed=false;try{reopened.put(c,List.of(incident));}catch(Exception ex){failed=true;}
            check(failed && !reopened.writable() && reopened.get(c.terms().id()).deliveries()==1,"failed paired commit freezes unsaved outcomes");
        }finally{try(var paths=Files.walk(dir)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
        System.out.println("SupplyReputationSmoke OK: "+checks+" checks");
    }
    static void advance(Store store,SupplyProcessor.Gateway gateway,long now,long retry,long grace)throws Exception{SupplyProcessor.advance(store.value.terms().id(),now,retry,grace,store,gateway);}
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
}
