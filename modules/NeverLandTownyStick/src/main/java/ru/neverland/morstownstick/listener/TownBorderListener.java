package ru.neverland.morstownstick.listener;

import com.palmergames.bukkit.towny.event.TownClaimEvent;
import com.palmergames.bukkit.towny.event.town.TownUnclaimEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import ru.neverland.morstownstick.service.BorderCache;

public final class TownBorderListener implements Listener {
    private final BorderCache cache;

    public TownBorderListener(BorderCache cache) {
        this.cache = cache;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClaim(TownClaimEvent event) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyStick")){ru.neverland.core.ModuleGate.stopEvent(event);return;}
        if (event.getTownBlock().getTownOrNull() != null) cache.invalidate(event.getTownBlock().getTownOrNull().getUUID());
    } }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUnclaim(TownUnclaimEvent event) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyStick")){ru.neverland.core.ModuleGate.stopEvent(event);return;}
        if (event.getTown() != null) cache.invalidate(event.getTown().getUUID());
    } }
}
