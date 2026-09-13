package ru.neverland.townybuilds;
import java.util.*;
import java.nio.file.*;
import ru.neverland.townybuilds.shop.*;
import ru.neverland.core.CrimeAccess;
public final class ShopCrimeSmoke {
    static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    public static void main(String[] args)throws Exception{
        var order=new ShopOrder(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"STONE",4,100,1,"PREPARED",1,"sale",false,300);var file=Files.createTempDirectory("shop-crime-").resolve("orders.yml");var journal=new ShopJournal(file);journal.load();journal.put(order);journal=new ShopJournal(file);journal.load();var restored=journal.order(order.id());check(restored.total()==400&&restored.sellerIncome()==300,"gross and net survive restart independently");
        check(restored.paymentStep("COMPLETE",2,"paid").finish().sellerIncome()==300,"state transitions freeze seller net");
        Files.writeString(file,Files.readString(file).replace("    seller-income: 300\n",""));journal.load();check(journal.order(order.id()).sellerIncome()==400,"old orders migrate at full agreed revenue");
        check(CrimeAccess.net(1,5000)==1&&CrimeAccess.net(101,7500)==76&&CrimeAccess.net(100000000000L,5000)==50000000000L,"integer rounding, smallest sale and largest sale");
        boolean denied=false;try{new ShopOrder(order.id(),order.seller(),order.buyer(),"STONE",4,100,1,"PREPARED",1,"sale",false,401);}catch(IllegalArgumentException e){denied=true;}check(denied,"net cannot exceed buyer gross");
        System.out.println("ShopCrimeSmoke OK");
    }
}
