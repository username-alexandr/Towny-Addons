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
    public UUID townId(){ synchronized (ru.neverland.core.SuiteState.lock()) {return townId;} }public UUID viewer(){ synchronized (ru.neverland.core.SuiteState.lock()) {return viewer;} }public Type type(){ synchronized (ru.neverland.core.SuiteState.lock()) {return type;} }
    public void inventory(Inventory value){ synchronized (ru.neverland.core.SuiteState.lock()) {inventory=value;} }@Override public Inventory getInventory(){ synchronized (ru.neverland.core.SuiteState.lock()) {return inventory;} }
    public void action(int slot,BiConsumer<Player,Boolean> action){ synchronized (ru.neverland.core.SuiteState.lock()) {actions.put(slot,action);} }
    public void click(int slot,Player player,boolean shift){ synchronized (ru.neverland.core.SuiteState.lock()) {var action=actions.get(slot);if(action!=null)action.accept(player,shift);} }
}
