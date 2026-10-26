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
    public UUID townId(){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {return townId;} }public UUID viewer(){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {return viewer;} }public Type type(){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {return type;} }
    public void inventory(Inventory value){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {inventory=value;} }@Override public Inventory getInventory(){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {return inventory;} }
    public void action(int slot,BiConsumer<Player,Boolean> action){ try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {actions.put(slot,action);} }
    public void click(int slot,Player player,boolean shift){ try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {var action=actions.get(slot);if(action!=null)action.accept(player,shift);} }
}
