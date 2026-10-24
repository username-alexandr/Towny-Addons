package ru.neverland.townybuilds.storage;
import org.bukkit.event.inventory.InventoryAction;
public final class StorageAccessSmoke {
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    public static void main(String[] args){
        for(var a:new InventoryAction[]{InventoryAction.PICKUP_ALL,InventoryAction.PICKUP_HALF,InventoryAction.DROP_ONE_SLOT,InventoryAction.MOVE_TO_OTHER_INVENTORY,InventoryAction.CLONE_STACK}){
            check(!StorageAccessPolicy.allows(a,true,true,false),"deposit-only cannot withdraw using "+a);
            check(StorageAccessPolicy.allows(a,true,false,true),"withdraw permission works using "+a);
        }
        for(var a:new InventoryAction[]{InventoryAction.HOTBAR_SWAP,InventoryAction.HOTBAR_MOVE_AND_READD,InventoryAction.SWAP_WITH_CURSOR})
            check(!StorageAccessPolicy.allows(a,true,true,false)&&!StorageAccessPolicy.allows(a,true,false,true)&&StorageAccessPolicy.allows(a,true,true,true),"exchange requires both rights "+a);
        check(!StorageAccessPolicy.allows(InventoryAction.MOVE_TO_OTHER_INVENTORY,false,false,true),"bottom shift cannot bypass deposit deny");
        check(!StorageAccessPolicy.allows(InventoryAction.COLLECT_TO_CURSOR,false,true,false),"bottom double-click cannot bypass withdrawal deny");
        check(StorageAccessPolicy.allows(InventoryAction.PLACE_ONE,true,true,false),"single-item deposit allowed");
        check(!StorageAccessPolicy.allows(InventoryAction.PLACE_ALL,true,false,true),"withdraw-only cannot deposit");
        check(StorageAccessPolicy.allows(InventoryAction.PICKUP_ALL,false,false,false),"own inventory remains usable");
        check(!StorageAccessPolicy.allows(InventoryAction.UNKNOWN,true,true,false),"unknown inventory action fails closed");
        System.out.println("StorageAccessSmoke PASS: shift, double-click, hotbar, swaps, dropping, pickup, deposit and own inventory");
    }
}
