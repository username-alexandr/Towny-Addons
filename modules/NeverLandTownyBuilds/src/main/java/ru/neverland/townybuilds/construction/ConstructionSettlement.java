package ru.neverland.townybuilds.construction;

import org.bukkit.inventory.ItemStack;
import ru.neverland.townybuilds.storage.MunicipalDeposits;
import ru.neverland.townybuilds.storage.StockMath;
import java.util.*;

/** Persist-before-bank and atomic receipt+warehouse writes, exercised with fault-injecting stores. */
public final class ConstructionSettlement {
    private ConstructionSettlement() {}
    public interface Store {
        ConstructionReceipt receipt();
        ItemStack[] storage();
        boolean busy();
        void commit(ConstructionReceipt receipt, ItemStack[] storage) throws Exception;
    }
    @FunctionalInterface public interface Bank { boolean deposit(ConstructionReceipt receipt) throws Exception; }

    public static void credit(Store store, Bank bank) throws Exception {
        ConstructionReceipt before=store.receipt();
        if(!before.state().equals("REFUND_READY"))return;
        var paying=before.state("REFUND_PAYING");store.commit(paying,null);
        boolean paid=bank.deposit(paying);
        store.commit(paying.state(paid?"REFUND_PAID":"REFUND_READY"),null);
    }
    public static void resources(Store store) throws Exception {
        ConstructionReceipt before=store.receipt();
        if(!Set.of("ACTIVE","COMPLETE","REFUND_PAID").contains(before.state())||before.remaining().isEmpty()||store.busy())return;
        ItemStack[] work=StockMath.copy(store.storage());List<ConstructionReceipt.Item> remaining=new ArrayList<>();
        boolean changed=false;
        for(var item:before.remaining()) {
            int take=Math.min(item.amount(),MunicipalDeposits.capacity(work,item.sample()));
            if(take>0) {var stack=item.sample();stack.setAmount(take);if(!StockMath.insert(work,new ItemStack[]{stack}))throw new IllegalStateException("Склад изменился");changed=true;}
            if(take<item.amount())remaining.add(new ConstructionReceipt.Item(item.sample(),item.amount()-take));
        }
        if(changed)store.commit(before.remaining(remaining),work);
    }
}
