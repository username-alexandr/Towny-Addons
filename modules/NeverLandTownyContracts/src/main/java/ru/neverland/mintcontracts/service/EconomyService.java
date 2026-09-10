package ru.neverland.mintcontracts.service;

import com.palmergames.bukkit.towny.TownyEconomyHandler;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.economy.Account;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintcontracts.integration.TownyHook;
import ru.neverland.mintcontracts.model.ContractDefinition;

import java.text.DecimalFormat;
import java.util.UUID;

public final class EconomyService {
    private static final DecimalFormat FALLBACK = new DecimalFormat("#,##0.00");
    private final JavaPlugin plugin;
    private final TownyHook towny;
    public EconomyService(JavaPlugin plugin, TownyHook towny) { this.plugin = plugin; this.towny = towny; }
    public boolean canReserve(Town town, double amount) {
        try { return amount <= 0 || ru.neverland.integration.TreasuryAccess.canSpend(town,"infrastructure",amount); }
        catch (RuntimeException exception) { return false; }
    }
    public boolean reserve(Town town, ContractDefinition definition) {
        if (definition.reward() <= 0) return true;
        String reason = reason("economy.reserve-reason", definition);
        try { return ru.neverland.integration.TreasuryAccess.withdraw(town,"infrastructure","contracts",definition.reward(),reason); }
        catch (RuntimeException exception) { return false; }
    }
    public boolean pay(UUID residentId, double amount, ContractDefinition definition) {
        if (amount <= 0) return true;
        try {
            Resident resident = towny.resident(residentId);
            Account account = resident == null ? null : resident.getAccount();
            return account != null && account.deposit(amount, reason("economy.payout-reason", definition));
        } catch (RuntimeException exception) { return false; }
    }
    public boolean refund(Town town, double amount, ContractDefinition definition) {
        if (amount <= 0) return true;
        try { return town != null && ru.neverland.integration.TreasuryAccess.deposit(town,"infrastructure","contracts",true,amount,reason("economy.refund-reason",definition)); }
        catch (RuntimeException exception) { return false; }
    }
    private String reason(String path, ContractDefinition definition) {
        return plugin.getConfig().getString(path, "Городской заказ").replace("%contract%", definition.id());
    }
    public String format(double amount) {
        try { return TownyEconomyHandler.isActive() ? TownyEconomyHandler.getFormattedBalance(amount) : FALLBACK.format(amount); }
        catch (RuntimeException exception) { return FALLBACK.format(amount); }
    }
    public String balance(Town town) {
        try { return town == null ? format(0) : town.getAccount().getHoldingFormattedBalance(); }
        catch (RuntimeException exception) { return format(0); }
    }
}
