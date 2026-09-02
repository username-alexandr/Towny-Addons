package ru.neverland.townytaxes.listener;

import com.palmergames.bukkit.towny.event.economy.TownyTransactionEvent;
import com.palmergames.bukkit.towny.object.economy.transaction.Transaction;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import ru.neverland.townytaxes.service.FiscalService;

public final class EconomyListener implements Listener {
    private final FiscalService service; public EconomyListener(FiscalService service){this.service=service;}
    @EventHandler(priority=EventPriority.MONITOR) public void onTransaction(TownyTransactionEvent event){Transaction tx=event.getTransaction();if(tx!=null&&tx.hasReceiverAccount())service.onTransaction(tx);}
}
