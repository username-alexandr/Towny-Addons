package ru.neverland.mintcontracts.gui;
import org.bukkit.inventory.*;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.function.BiConsumer;
public final class ContractMenuHolder implements InventoryHolder {
    public enum Type { BOARD,HISTORY,TEMPLATES,CREATE,DRAFT,DETAIL,CONFIRM }
    private final UUID townId,viewer;private final Type type;private Inventory inventory;
    private final Map<Integer,BiConsumer<Player,Boolean>> actions=new HashMap<>();
    public ContractMenuHolder(UUID town,UUID viewer,Type type){this.townId=town;this.viewer=viewer;this.type=type;}
    public UUID townId(){return townId;}public UUID viewer(){return viewer;}public Type type(){return type;}
    public void inventory(Inventory value){inventory=value;}@Override public Inventory getInventory(){return inventory;}
    public void action(int slot,BiConsumer<Player,Boolean> action){actions.put(slot,action);}
    public void click(int slot,Player player,boolean shift){var action=actions.get(slot);if(action!=null)action.accept(player,shift);}
}
