package ru.neverland.townybuilds.storage;
import org.bukkit.event.inventory.InventoryAction;

/** All ways a click can transfer items across the municipal inventory boundary. */
public final class StorageAccessPolicy {
    private StorageAccessPolicy(){}
    public static boolean allows(InventoryAction action,boolean top,boolean deposit,boolean withdraw){
        return switch(action){
            case NOTHING -> true;
            case COLLECT_TO_CURSOR -> withdraw; // Double-click in either inventory can take town items.
            case MOVE_TO_OTHER_INVENTORY -> top?withdraw:deposit;
            case UNKNOWN -> deposit&&withdraw;
            default -> !top||switch(action){
                case PLACE_ALL,PLACE_ONE,PLACE_SOME -> deposit;
                case PICKUP_ALL,PICKUP_SOME,PICKUP_HALF,PICKUP_ONE,DROP_ALL_SLOT,DROP_ONE_SLOT,CLONE_STACK -> withdraw;
                case SWAP_WITH_CURSOR,HOTBAR_SWAP,HOTBAR_MOVE_AND_READD -> deposit&&withdraw;
                default -> deposit&&withdraw;
            };
        };
    }
}
