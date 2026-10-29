package ru.neverland.townymarket;

import java.nio.charset.StandardCharsets;
import java.util.*;
import ru.neverland.townymarket.personal.LimitBook;
import static ru.neverland.townymarket.personal.LimitBook.*;

public final class PersonalLimitBookSmoke {
    private static int checks,sequence;private static long transitions;
    private static final UUID BUYER=id("buyer"),SELLER=id("seller"),OTHER=id("other");
    private static UUID id(String text){return UUID.nameUUIDFromBytes(text.getBytes(StandardCharsets.UTF_8));}
    private static UUID next(){return id("ticket-"+(sequence++));}
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private interface Work{void run();}
    private static void denied(Work work,String why){try{work.run();}catch(IllegalArgumentException|NullPointerException|ArithmeticException expected){check(true,why);return;}throw new AssertionError(why);}
    private record Prepared(State state,UUID order,UUID receipt){}
    private static Prepared pending(State state,UUID owner,Side side,String product,int quantity,long unit,long now){UUID order=next(),receipt=next();return new Prepared(prepareOrder(state,order,receipt,owner,side,product,quantity,unit,now),order,receipt);}
    private static Prepared funded(State state,UUID owner,Side side,String product,int quantity,long unit,long now){var p=pending(state,owner,side,product,quantity,unit,now);return new Prepared(resolve(p.state(),p.receipt(),true,now),p.order(),p.receipt());}
    public static void main(String[] args){funding();matching();priority();bounds();checkpoints();randomConservation();System.out.println("PersonalLimitBookSmoke PASS "+checks+" checks and "+transitions+" conserved randomized transitions");}
    private static void funding(){
        var p=pending(empty(),BUYER,Side.BUY,"STONE",10,100,1);check(p.state().orders().get(p.order()).status()==Status.FUNDING&&p.state().cash(BUYER)==0,"pending withdrawal is not spendable or matchable");
        var s=funded(p.state(),SELLER,Side.SELL,"STONE",10,100,2);check(match(s.state(),3,20)==s.state(),"unfunded buy cannot consume confirmed seller goods");
        denied(()->cancel(p.state(),BUYER,p.order(),3),"unresolved funding cannot be refunded");
        var declined=resolve(p.state(),p.receipt(),false,3);check(declined.orders().get(p.order()).status()==Status.CANCELLED&&declined.wallets().isEmpty(),"definite refusal creates no cash or refund");check(resolve(declined,p.receipt(),false,4)==declined,"same reconciliation is idempotent");denied(()->resolve(declined,p.receipt(),true,4),"opposite receipt outcome cannot mint funds");
        denied(()->prepareOrder(p.state(),p.order(),p.receipt(),BUYER,Side.BUY,"STONE",10,100,1),"same request IDs cannot fund twice");
        var applied=resolve(s.state(),p.receipt(),true,3);check(match(applied,4,20).fills().size()==1,"acknowledged funding permits matching");
    }
    private static void matching(){
        var sell=funded(empty(),SELLER,Side.SELL,"OAK_LOG",10,100,10);var buy=funded(sell.state(),BUYER,Side.BUY,"OAK_LOG",6,150,20);var filled=match(buy.state(),21,20);
        check(filled.fills().size()==1&&filled.fills().get(0).unitCents()==100&&filled.fills().get(0).quantity()==6,"older maker sell price and partial quantity are exact");
        check(filled.cash(SELLER)==600&&filled.cash(BUYER)==300&&filled.item(BUYER,"OAK_LOG")==6,"seller proceeds and buyer price improvement backed by original withdrawal");check(filled.orders().get(sell.order()).remaining()==4&&filled.orders().get(buy.order()).status()==Status.FILLED,"only filled quantity leaves escrow");check(match(filled,22,20)==filled,"repeating match cannot duplicate fills");
        var cancelled=cancel(filled,SELLER,sell.order(),22);check(cancelled.item(SELLER,"OAK_LOG")==4&&cancelled.cash(SELLER)==600,"seller cancellation releases only unmatched goods");check(cancel(cancelled,SELLER,sell.order(),23)==cancelled,"repeat cancellation cannot duplicate goods");denied(()->cancel(filled,OTHER,sell.order(),22),"cannot cancel someone else's order");
        var cash=next();var cashPending=claimCash(cancelled,cash,SELLER,600,23);check(cashPending.cash(SELLER)==0&&cashPending.receipts().get(cash).status()==ReceiptStatus.PENDING,"cash claim unavailable while external deposit is pending");denied(()->claimCash(cashPending,next(),SELLER,1,24),"pending withdrawal cannot be claimed twice");
        var failure=resolve(cashPending,cash,false,24);check(failure.cash(SELLER)==600,"definite payout refusal restores same cash reserve");var paidID=next();var paid=resolve(claimCash(failure,paidID,SELLER,600,25),paidID,true,26);check(paid.cash(SELLER)==0&&resolve(paid,paidID,true,27)==paid,"known payout leaves ledger once");
        var itemID=next();var itemPending=claimItems(paid,itemID,BUYER,"OAK_LOG",6,27);check(itemPending.item(BUYER,"OAK_LOG")==0,"pending inventory claim removes availability");var received=resolve(itemPending,itemID,true,28);check(resolve(received,itemID,true,29)==received,"received item claim cannot repeat");
        var restored=new State(received.orders(),received.receipts(),received.wallets(),received.items(),received.fills());check(restored.equals(received),"constructor reconstructs exact settled and unresolved ledger images");
        var wrongWallet=new HashMap<>(filled.wallets());wrongWallet.remove(SELLER);wrongWallet.put(OTHER,600L);denied(()->new State(filled.orders(),filled.receipts(),wrongWallet,filled.items(),filled.fills()),"global cash sum alone cannot redirect another player's proceeds");
        var wrongItems=new HashMap<>(filled.items());wrongItems.remove(BUYER);wrongItems.put(OTHER,Map.of("OAK_LOG",6L));denied(()->new State(filled.orders(),filled.receipts(),filled.wallets(),wrongItems,filled.fills()),"global item sum alone cannot redirect buyer goods");
        var fakeWallet=new HashMap<>(filled.wallets());fakeWallet.put(OTHER,1L);denied(()->new State(filled.orders(),filled.receipts(),fakeWallet,filled.items(),filled.fills()),"unbacked wallet is rejected");
        var duplicate=new ArrayList<>(filled.fills());duplicate.add(filled.fills().get(0));denied(()->new State(filled.orders(),filled.receipts(),filled.wallets(),filled.items(),duplicate),"duplicate historical fill rejected");
    }
    private static void priority(){
        var ownSell=funded(empty(),BUYER,Side.SELL,"GLASS",4,100,10);var ownBuy=funded(ownSell.state(),BUYER,Side.BUY,"GLASS",4,200,20);check(match(ownBuy.state(),21,20)==ownBuy.state(),"self matching is forbidden");
        var otherSell=funded(ownBuy.state(),SELLER,Side.SELL,"GLASS",2,150,30);var matched=match(otherSell.state(),31,20);check(matched.fills().size()==1&&matched.fills().get(0).seller().equals(SELLER)&&matched.fills().get(0).unitCents()==200,"self offer skipped; older buy maker sets execution price");
        var low=funded(empty(),BUYER,Side.BUY,"STONE",2,100,1);var high=funded(low.state(),OTHER,Side.BUY,"STONE",2,200,2);var supply=funded(high.state(),SELLER,Side.SELL,"STONE",2,50,3);var m=match(supply.state(),4,1);check(m.fills().get(0).buyer().equals(OTHER)&&m.orders().get(low.order()).status()==Status.OPEN,"higher buy price has priority over earlier low bid");
        var a=funded(empty(),SELLER,Side.SELL,"STONE",1,200,1);var b=funded(a.state(),OTHER,Side.SELL,"STONE",1,100,2);var c=funded(b.state(),BUYER,Side.BUY,"STONE",1,300,3);check(match(c.state(),4,20).fills().get(0).seller().equals(OTHER),"lower ask price has priority over earlier expensive offer");
        var distinct=funded(empty(),SELLER,Side.SELL,"STONE",1,1,1);var another=funded(distinct.state(),BUYER,Side.BUY,"COBBLESTONE",1,100,2);check(match(another.state(),3,20)==another.state(),"different resources never cross");
        var tooHigh=funded(empty(),SELLER,Side.SELL,"STONE",1,101,1);var tooLow=funded(tooHigh.state(),BUYER,Side.BUY,"STONE",1,100,2);check(match(tooLow.state(),3,20)==tooLow.state(),"non-crossing price limits preserve both reserves");
    }
    private static void bounds(){
        denied(()->pending(empty(),BUYER,Side.BUY,"STONE",3457,1,1),"quantity bounded");denied(()->pending(empty(),BUYER,Side.BUY,"STONE",2,MAX_CENTS,1),"total price cannot overflow supported payment");denied(()->pending(empty(),BUYER,Side.BUY,"STONE",1,0,1),"free order disallowed");denied(()->pending(empty(),BUYER,Side.BUY,"stone",1,1,1),"canonical vanilla material key required");
        State full=empty();for(int i=0;i<MAX_ACTIVE;i++)full=pending(full,BUYER,Side.BUY,"STONE",1,1,i+1).state();var immutable=full;denied(()->pending(immutable,BUYER,Side.BUY,"STONE",1,1,100),"per-player pending plus active order bound");
        denied(()->match(empty(),1,0),"zero work batch rejected");denied(()->match(empty(),1,101),"unbounded work batch rejected");
        var mutable=new HashMap<UUID,Long>();var source=new State(Map.of(),Map.of(),mutable,Map.of(),List.of());mutable.put(BUYER,1L);check(source.wallets().isEmpty(),"state snapshots detach caller maps");
    }
    private static void checkpoints(){
        var source=funded(empty(),BUYER,Side.BUY,"STONE",10,100,1);var returned=cancel(source.state(),BUYER,source.order(),2);UUID paidID=next(),failedID=next(),pendingID=next();
        var paid=resolve(claimCash(returned,paidID,BUYER,300,3),paidID,true,4);var failed=resolve(claimCash(paid,failedID,BUYER,100,5),failedID,false,6);var waiting=claimCash(failed,pendingID,BUYER,50,7);
        var checkpoint=checkpointClaims(waiting,List.of(paidID,failedID),1,"a".repeat(64));check(checkpoint.cash(BUYER)==650&&checkpoint.claimCheckpoint().paidCash().get(BUYER)==300,"checkpoint retains exact cumulative paid cash and available reserve");
        check(checkpoint.receipts().size()==2&&checkpoint.receipts().get(pendingID).status()==ReceiptStatus.PENDING&&checkpoint.receipts().containsKey(source.receipt()),"checkpoint keeps every pending claim and original funding receipt");
        denied(()->checkpointClaims(waiting,List.of(pendingID),1,"b".repeat(64)),"pending claims cannot be pruned");denied(()->checkpointClaims(waiting,List.of(source.receipt()),1,"b".repeat(64)),"funding proofs cannot be pruned");denied(()->checkpointClaims(waiting,List.of(paidID,paidID),1,"b".repeat(64)),"same paid claim cannot enter checkpoint twice");denied(()->checkpointClaims(waiting,List.of(paidID),2,"b".repeat(64)),"archive sequence cannot skip");
        var reconciled=resolve(checkpoint,pendingID,false,8);check(reconciled.cash(BUYER)==700&&reconciled.claimCheckpoint().equals(checkpoint.claimCheckpoint()),"reconciliation preserves earlier archived claims");
        var supply=funded(reconciled,SELLER,Side.SELL,"STONE",10,1,9);var withdrawn=cancel(supply.state(),SELLER,supply.order(),10);UUID itemID=next();var received=resolve(claimItems(withdrawn,itemID,SELLER,"STONE",4,11),itemID,true,12);var compact=checkpointClaims(received,List.of(pendingID,itemID),2,"c".repeat(64));check(compact.item(SELLER,"STONE")==6&&compact.claimCheckpoint().paidItems().get(SELLER).get("STONE")==4,"item checkpoint retains recipient and quantity");
        var forged=new ClaimCheckpoint(2,"c".repeat(64),Map.of(OTHER,300L),compact.claimCheckpoint().paidItems());denied(()->new State(compact.orders(),compact.receipts(),compact.wallets(),compact.items(),compact.fills(),forged),"same aggregate checkpoint cannot redirect another owner's paid cash");
        var wrongItems=new ClaimCheckpoint(2,"c".repeat(64),compact.claimCheckpoint().paidCash(),Map.of(OTHER,Map.of("STONE",4L)));denied(()->new State(compact.orders(),compact.receipts(),compact.wallets(),compact.items(),compact.fills(),wrongItems),"same aggregate checkpoint cannot redirect another owner's received items");
        var nextBuy=funded(compact,BUYER,Side.BUY,"STONE",1,50,13);check(nextBuy.state().claimCheckpoint().equals(compact.claimCheckpoint()),"new orders preserve checkpoint");check(match(nextBuy.state(),14,20).claimCheckpoint().equals(compact.claimCheckpoint()),"matching preserves checkpoint");
    }
    private static void randomConservation(){
        var random=new Random(0x4e657665724cL);var owners=List.of(BUYER,SELLER,OTHER,id("four"),id("five"));var products=List.of("STONE","OAK_LOG","WHITE_WOOL");
        for(int sample=0;sample<100;sample++){
            State state=empty();long now=1;
            for(int i=0;i<80;i++){
                var owner=owners.get(random.nextInt(owners.size()));var side=random.nextBoolean()?Side.BUY:Side.SELL;var p=pending(state,owner,side,products.get(random.nextInt(products.size())),1+random.nextInt(200),1+random.nextInt(500),now++);state=p.state();transitions++;
                state=resolve(state,p.receipt(),random.nextInt(8)!=0,now++);transitions++;state=match(state,now++,1+random.nextInt(20));transitions++;
                if(random.nextBoolean()){var open=state.orders().values().stream().filter(o->o.status()==Status.OPEN).toList();if(!open.isEmpty()){var order=open.get(random.nextInt(open.size()));state=cancel(state,order.owner(),order.id(),now++);transitions++;}}
            }
            for(var order:List.copyOf(state.orders().values()))if(order.status()==Status.OPEN){state=cancel(state,order.owner(),order.id(),now++);transitions++;}
            for(var entry:List.copyOf(state.wallets().entrySet())){long left=entry.getValue();while(left>0){long amount=Math.min(MAX_CENTS,left);UUID receipt=next();state=claimCash(state,receipt,entry.getKey(),amount,now++);state=resolve(state,receipt,true,now++);transitions+=2;left-=amount;}}
            for(var entry:List.copyOf(state.items().entrySet()))for(var stock:entry.getValue().entrySet()){long left=stock.getValue();while(left>0){int amount=(int)Math.min(MAX_QUANTITY,left);UUID receipt=next();state=claimItems(state,receipt,entry.getKey(),stock.getKey(),amount,now++);state=resolve(state,receipt,true,now++);transitions+=2;left-=amount;}}
            check(state.wallets().isEmpty()&&state.items().isEmpty()&&state.orders().values().stream().noneMatch(o->o.status()==Status.OPEN||o.status()==Status.FUNDING),"random book fully settles all backed reserves "+sample);
        }
    }
}
