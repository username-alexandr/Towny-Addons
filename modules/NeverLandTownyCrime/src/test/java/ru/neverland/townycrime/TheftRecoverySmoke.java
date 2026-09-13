package ru.neverland.townycrime;
import java.util.*;
import java.io.IOException;
public final class TheftRecoverySmoke {
    static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    static class Store implements TheftProcessor.Store{CrimeState state;boolean fail;public CrimeState get(UUID town){return state;}public void put(CrimeState s)throws IOException{if(fail)throw new IOException("disk failed");state=s;}}
    static class Resources implements TheftProcessor.Resources{String status="NONE";long balance=1000;int debits;boolean lostReserve,lostConsume,lostForget,insufficient;public String status(UUID id){return status;}
        public boolean reserve(UUID id,UUID town,Map<String,Long> amount)throws IOException{if(insufficient)return false;if(status.equals("NONE")){balance-=amount.get("wood");debits++;status="HELD";}if(lostReserve){lostReserve=false;throw new IOException("lost reserve reply");}return true;}
        public void consume(UUID id)throws IOException{status="CONSUMED";if(lostConsume){lostConsume=false;throw new IOException("lost consume reply");}}
        public void forget(UUID id)throws IOException{status="NONE";if(lostForget){lostForget=false;throw new IOException("lost cleanup reply");}}
    }
    static CrimeState plan(){return new CrimeState(UUID.randomUUID(),80,1000,1000,new CrimeState.Incident(UUID.randomUUID(),"BURGLARY","wood",100,"PLANNED",1,1000));}
    public static void main(String[] args)throws Exception{
        for(String fault:List.of("reserve","consume","save","forget","none")){
            var store=new Store();store.state=plan();UUID town=store.state.town();var resources=new Resources();resources.lostReserve=fault.equals("reserve");resources.lostConsume=fault.equals("consume");resources.lostForget=fault.equals("forget");store.fail=fault.equals("save");
            try{TheftProcessor.resume(town,store,resources);}catch(IOException expected){}store.fail=false;
            for(int i=0;i<6;i++)try{TheftProcessor.resume(town,store,resources);}catch(IOException expected){}
            check(resources.debits==1&&resources.balance==900&&store.state.incident().phase().equals("CLOSED")&&resources.status.equals("NONE"),"recover once after "+fault);
        }
        var store=new Store();store.state=plan();var resources=new Resources();resources.insufficient=true;for(int i=0;i<3;i++)TheftProcessor.resume(store.state.town(),store,resources);check(resources.debits==0&&store.state.incident().phase().equals("CANCELLED"),"protected stock cannot be stolen");
        store.state=plan();resources=new Resources();resources.status="RELEASED";TheftProcessor.resume(store.state.town(),store,resources);check(store.state.incident().phase().equals("SKIPPED"),"cancelled reservation never retried");
        store.state=plan();resources.status="UNKNOWN";boolean denied=false;try{TheftProcessor.resume(store.state.town(),store,resources);}catch(IllegalStateException e){denied=true;}check(denied&&store.state.incident().phase().equals("PLANNED"),"unknown receipt stops without new debit");
        System.out.println("TheftRecoverySmoke OK");
    }
}
