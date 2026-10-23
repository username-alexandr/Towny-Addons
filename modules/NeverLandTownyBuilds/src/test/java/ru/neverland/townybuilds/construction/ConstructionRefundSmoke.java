package ru.neverland.townybuilds.construction;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.townybuilds.storage.StockMath;
import java.util.*;

public final class ConstructionRefundSmoke {
    static int checks;
    static void ok(boolean yes,String message){checks++;if(!yes)throw new AssertionError(message);}
    static final class Stack extends ItemStack {
        int amount=1;final Material type;
        Stack(Material type,int amount){this.type=type;this.amount=amount;}
        public Material getType(){return type;}public int getAmount(){return amount;}public void setAmount(int n){amount=n;}
        public int getMaxStackSize(){return 64;}public Stack clone(){return new Stack(type,amount);}
        public boolean isSimilar(ItemStack other){return other!=null&&other.getType()==type;}
    }
    static ConstructionReceipt receipt(List<ConstructionReceipt.Item> items) {
        return new ConstructionReceipt(UUID.randomUUID(),1,12345,items,Map.of(),"ACTIVE",0,List.of(),0,"",false,items.size());
    }
    static final class Store implements ConstructionSettlement.Store {
        ConstructionReceipt value;ItemStack[] stock=new ItemStack[1];int commits,failAt;boolean busy;
        Store(ConstructionReceipt value){this.value=value;}
        public ConstructionReceipt receipt(){return value;}public ItemStack[] storage(){return StockMath.copy(stock);}public boolean busy(){return busy;}
        public void commit(ConstructionReceipt next,ItemStack[] items)throws Exception {
            if(++commits==failAt)throw new java.io.IOException("injected disk fault");
            value=next;if(items!=null)stock=StockMath.copy(items);
        }
    }
    public static void main(String[] args)throws Exception {
        var source=new Stack(Material.STONE,1);var paid=receipt(List.of(new ConstructionReceipt.Item(source,100)));
        var admin=paid.cancel(0,"admin");ok(admin.refundCents()==12345&&admin.remaining().get(0).amount()==100,"administrator full refund");
        var user=paid.cancel(10,"mayor");ok(user.refundCents()==11110&&user.remaining().get(0).amount()==90,"commission and floor rounding");
        ok(ConstructionReceipt.refund(Long.MAX_VALUE,10)>0,"no overflow");
        ok(ConstructionReceipt.refund(1,10)==0&&ConstructionReceipt.refund(100,100)==0,"fractional items cannot be emitted");
        try{admin.cancel(0,"double");throw new AssertionError("duplicate cancel");}catch(IllegalStateException expected){checks++;}
        source.setAmount(55);ok(paid.paidItems().get(0).sample().getAmount()==1,"receipt owns item snapshots");
        var excess=paid.remaining(List.of(new ConstructionReceipt.Item(source,7))).cancel(10,"mayor");
        ok(excess.remaining().stream().mapToInt(ConstructionReceipt.Item::amount).sum()==97,"unused surplus is returned without commission");
        var store=new Store(admin.state("REFUND_READY"));int[] calls={0};
        ConstructionSettlement.credit(store,r->{calls[0]++;return true;});
        ConstructionSettlement.credit(store,r->{calls[0]++;return true;});
        ok(calls[0]==1&&store.value.state().equals("REFUND_PAID"),"repeat credit cannot pay twice");
        store.busy=true;ConstructionSettlement.resources(store);ok(store.stock[0]==null,"open warehouse is not overwritten");
        store.busy=false;store.failAt=store.commits+1;
        try{ConstructionSettlement.resources(store);throw new AssertionError("disk fault expected");}catch(java.io.IOException expected){checks++;}
        ok(store.stock[0]==null&&store.value.remaining().get(0).amount()==100,"failed write keeps both warehouse and remaining unchanged");
        store.failAt=0;ConstructionSettlement.resources(store);
        ok(store.stock[0].getAmount()==64&&store.value.remaining().get(0).amount()==36,"full warehouse retains overflow");
        var recovered=new Store(store.value);recovered.stock=StockMath.copy(store.stock);
        ConstructionSettlement.resources(recovered);ok(recovered.value.remaining().get(0).amount()==36,"restart with full warehouse does not duplicate");
        recovered.stock[0]=null;ConstructionSettlement.resources(recovered);
        ok(recovered.stock[0].getAmount()==36&&recovered.value.remaining().isEmpty(),"overflow drains after space is freed");
        ConstructionSettlement.resources(recovered);ok(recovered.stock[0].getAmount()==36,"repeat resource processing is idempotent");
        var fault=new Store(admin.state("REFUND_READY"));fault.failAt=1;calls[0]=0;
        try{ConstructionSettlement.credit(fault,r->{calls[0]++;return true;});}catch(java.io.IOException expected){checks++;}
        ok(calls[0]==0,"no bank call before durable intent");
        fault.failAt=3;
        try{ConstructionSettlement.credit(fault,r->{calls[0]++;return true;});}catch(java.io.IOException expected){checks++;}
        ok(calls[0]==1&&fault.value.state().equals("REFUND_PAYING"),"post-credit crash requires review");
        var restarted=new Store(fault.value);ConstructionSettlement.credit(restarted,r->{calls[0]++;return true;});
        ok(calls[0]==1,"unknown bank outcome is not retried after restart");
        var throwing=new Store(admin.state("REFUND_READY"));
        try{ConstructionSettlement.credit(throwing,r->{throw new IllegalStateException("provider failed");});}catch(IllegalStateException expected){checks++;}
        ok(throwing.value.state().equals("REFUND_PAYING"),"ambiguous provider failure stays reviewable");
        var refused=new Store(admin.state("REFUND_READY"));ConstructionSettlement.credit(refused,r->false);
        ok(refused.value.state().equals("REFUND_READY"),"definite rejection can retry");
        var empty=receipt(List.of()).cancel(0,"admin");var yaml=new YamlConfiguration();empty.save(yaml.createSection("receipt"));
        var parsed=new YamlConfiguration();parsed.loadFromString(yaml.saveToString());
        ok(ConstructionReceipt.load(parsed.getConfigurationSection("receipt")).equals(empty),"receipt state persists through YAML restart");
        parsed.set("receipt.paid-cents",-1);
        try{ConstructionReceipt.load(parsed.getConfigurationSection("receipt"));throw new AssertionError("bad money accepted");}catch(IllegalArgumentException expected){checks++;}
        System.out.println("ConstructionRefundSmoke: "+checks+" checks passed");
    }
}
