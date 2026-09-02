package ru.neverland.townytaxes.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import ru.neverland.townytaxes.integration.TownyHook;
import ru.neverland.townytaxes.service.FiscalService;
import ru.neverland.townytaxes.service.MessageService;

public final class RestrictionListener implements Listener {
    private final TownyHook towny;private final FiscalService service;private final MessageService messages;
    public RestrictionListener(TownyHook towny,FiscalService service,MessageService messages){this.towny=towny;this.service=service;this.messages=messages;}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void onCommand(PlayerCommandPreprocessEvent event){if(event.getPlayer().hasPermission("townytaxes.bypass"))return;if(service.commandBlocked(towny.resident(event.getPlayer()),event.getMessage())){event.setCancelled(true);messages.send(event.getPlayer(),"sanction-command-blocked");}}
}
