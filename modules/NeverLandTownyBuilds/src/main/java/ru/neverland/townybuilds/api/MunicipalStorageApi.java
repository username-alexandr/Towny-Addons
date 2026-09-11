package ru.neverland.townybuilds.api;
import java.util.UUID;
import java.io.IOException;
import org.bukkit.inventory.ItemStack;
/** Main-thread deposits with receipts saved atomically beside warehouse contents. */
public interface MunicipalStorageApi extends ru.neverland.core.ApiContract {
    @Override default java.util.Set<String> capabilities() { return java.util.Set.of("acknowledge", "capacity", "deposit"); }
    int capacity(UUID town,ItemStack sample);
    String deposit(UUID town,UUID operation,ItemStack sample,int amount)throws IOException;
    void acknowledge(UUID town,UUID operation)throws IOException;
}
