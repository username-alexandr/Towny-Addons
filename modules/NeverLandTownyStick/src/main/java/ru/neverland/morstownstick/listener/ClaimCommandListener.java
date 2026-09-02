package ru.neverland.morstownstick.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import ru.neverland.morstownstick.service.ClaimService;
import ru.neverland.morstownstick.service.SelectionService;

import java.util.Locale;

public final class ClaimCommandListener implements Listener {
    private final SelectionService selections;
    private final ClaimService claims;

    public ClaimCommandListener(SelectionService selections, ClaimService claims) {
        this.selections = selections;
        this.claims = claims;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String normalized = event.getMessage().trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (!normalized.equals("/t claim") && !normalized.equals("/town claim")) return;
        if (selections.count(event.getPlayer().getUniqueId()) == 0) return;
        event.setCancelled(true);
        claims.start(event.getPlayer());
    }
}
