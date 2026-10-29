package ru.neverland.mintexpeditions.gui;
import java.util.*;
import org.bukkit.inventory.*;
import ru.neverland.mintexpeditions.model.ExpeditionDefinition;
import ru.neverland.mintexpeditions.journey.Journey;

/** Each view owns its frozen offer; later reloads cannot silently replace clicked conditions. */
public final class ExpeditionMenuHolder implements InventoryHolder {
    public enum Page { BOARD, CHOOSE, CONFIRM, ACTIVE }
    final UUID owner,expedition;final Page page;final Map<Integer,ExpeditionDefinition> offers;final ExpeditionDefinition definition;final Journey.Branch branch;
    public ExpeditionMenuHolder(){this(null,Page.BOARD,Map.of(),null,null,null);}
    public ExpeditionMenuHolder(UUID owner,Page page,Map<Integer,ExpeditionDefinition> offers,ExpeditionDefinition definition,Journey.Branch branch,UUID expedition){this.owner=owner;this.page=page;this.offers=Map.copyOf(offers);this.definition=definition;this.branch=branch;this.expedition=expedition;}
    @Override public Inventory getInventory(){return null;}
}
