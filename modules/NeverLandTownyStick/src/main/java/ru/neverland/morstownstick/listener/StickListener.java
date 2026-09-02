package ru.neverland.morstownstick.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.morstownstick.integration.TownyFacade;
import ru.neverland.morstownstick.model.CellKey;
import ru.neverland.morstownstick.service.ClaimService;
import ru.neverland.morstownstick.service.MessageService;
import ru.neverland.morstownstick.service.SelectionService;
import ru.neverland.morstownstick.service.StickService;

import java.util.Map;

public final class StickListener implements Listener {
    private final JavaPlugin plugin;
    private final TownyFacade towny;
    private final StickService sticks;
    private final SelectionService selections;
    private final ClaimService claims;
    private final MessageService messages;

    public StickListener(JavaPlugin plugin, TownyFacade towny, StickService sticks, SelectionService selections,
                         ClaimService claims, MessageService messages) {
        this.plugin = plugin;
        this.towny = towny;
        this.sticks = sticks;
        this.selections = selections;
        this.claims = claims;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null || !sticks.isSelector(event.getItem())) return;
        event.setCancelled(true);
        var player = event.getPlayer();
        if (!player.hasPermission("morstownstick.stick")) {
            messages.send(player, "no-permission");
            return;
        }
        if (towny.town(player) == null) {
            messages.send(player, "no-town");
            return;
        }
        if (!towny.isMayorOrAssistant(player)) {
            messages.send(player, "not-mayor-or-assistant");
            return;
        }
        if (claims.isBusy(player.getUniqueId())) {
            messages.send(player, "selection-locked");
            return;
        }
        CellKey cell = towny.cell(event.getClickedBlock());
        SelectionService.ToggleResult result = selections.toggle(player.getUniqueId(), cell);
        Map<String, Object> replacements = Map.of("x", cell.x(), "z", cell.z(),
                "count", selections.count(player.getUniqueId()), "max", plugin.getConfig().getInt("selection.max-chunks", -1));
        switch (result) {
            case SELECTED -> messages.send(player, "chunk-selected", replacements);
            case UNSELECTED -> messages.send(player, "chunk-unselected", replacements);
            case LIMIT -> messages.send(player, "max-chunks-reached", replacements);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.getConfig().getBoolean("selection.clear-on-quit", true)) selections.clear(event.getPlayer().getUniqueId());
        claims.playerQuit(event.getPlayer().getUniqueId());
    }
}
