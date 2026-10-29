package ru.neverland.townyideologies;

import java.util.*;
import ru.neverland.townyideologies.reforms.*;
import ru.neverland.townyideologies.data.DataStore;
import static ru.neverland.townyideologies.ReformsSmoke.*;

/** Receipt verification is pure: no PAID boolean without exact frozen payload and current backing. */
public final class ReformTreasurySmoke {
    static int count;
    interface Work{void run()throws Exception;}
    static void test(boolean yes,String why){count++;if(!yes)throw new AssertionError(why);}
    static void reject(Work work,String why)throws Exception{try{work.run();}catch(Exception expected){count++;return;}throw new AssertionError(why);}
    static Map<String,Object> receipt(ReformBank.Request request,String status){return new HashMap<>(Map.ofEntries(Map.entry("operation",request.receipt().toString()),Map.entry("town",request.town().toString()),Map.entry("category","social"),Map.entry("source","ideology"),Map.entry("cents",request.cents()),Map.entry("reason",request.reason()),Map.entry("actor",request.actor().toString()),Map.entry("owner",ReformBank.OWNER),Map.entry("kind","DEBIT"),Map.entry("backingDebit",""),Map.entry("status",status),Map.entry("backingAvailable",true)));}
    public static void main(String[]args)throws Exception{
        var request=new ReformBank.Request(UUID.randomUUID(),UUID.randomUUID(),5000,UUID.randomUUID());
        test(ReformBank.verified(request,Map.of("operation",request.receipt().toString(),"status","ABSENT")).equals("ABSENT"),"absent exact UUID stays unknown and is not a payment");
        for(String status:List.of("PREPARED","INVOKING","PAID","REJECTED","REVIEW"))test(ReformBank.verified(request,receipt(request,status)).equals(status),"exact Treasury receipt preserves "+status);
        for(String field:List.of("operation","town","category","source","cents","reason","actor","owner","kind","backingDebit")){var wrong=receipt(request,"PAID");wrong.put(field,field.equals("cents")?5001L:"wrong");reject(()->ReformBank.verified(request,wrong),"different "+field+" cannot activate paid reform");}
        var refunded=receipt(request,"PAID");refunded.put("backingAvailable",false);test(ReformBank.verified(request,refunded).equals("REVIEW"),"historical PAID after refund cannot fund new benefits");refunded.remove("backingAvailable");test(ReformBank.verified(request,refunded).equals("REVIEW"),"old provider without backing proof cannot activate benefits");
        var fractional=receipt(request,"PAID");fractional.put("cents",5000.1);reject(()->ReformBank.verified(request,fractional),"fractional cents rejected without rounding");var novel=receipt(request,"OTHER");reject(()->ReformBank.verified(request,novel),"unknown gateway state stays closed");
        var reform=reform(5000);var ready=ReformBook.advance(ReformBook.begin(initial(),reform),4000);UUID stable=UUID.randomUUID();var pending=ReformBook.preparePayment(ready,reform.id(),stable,ACTOR,1000,"TREASURY_ASYNC");
        test(DataStore.decode(yaml(DataStore.encode(pending))).equals(pending),"schema3 persists frozen Treasury route with exact identity");var paid=ReformBook.resolve(pending,reform.id(),true,1001);test(paid.reforms().get(reform.id()).attempts().get(0).route().equals("TREASURY_ASYNC")&&paid.reforms().get(reform.id()).attempts().get(0).id().equals(stable),"local paid ACK retains exact original route and UUID");
        var legacy=yaml(DataStore.encode(pending));legacy.set("schema",2);legacy.set("reforms."+reform.id()+".attempts."+stable+".route",null);var old=DataStore.decode(legacy);test(old.reforms().get(reform.id()).attempts().get(0).route().equals("LEGACY_UNKNOWN"),"schema2 pending receipt never becomes a new Treasury request automatically");test(old.towns().equals(pending.towns())&&old.reforms().get(reform.id()).target().equals(reform.target()),"legacy pending migration keeps old effects and frozen target");
        var bad=yaml(DataStore.encode(pending));bad.set("reforms."+reform.id()+".attempts."+stable+".route","UNKNOWN");reject(()->DataStore.decode(bad),"unknown saved payment route cannot be silently migrated");
        System.out.println("ReformTreasurySmoke PASS: "+count+" checks; exact UUID/payload/backing and schema2-to3 route preservation");
    }
}
