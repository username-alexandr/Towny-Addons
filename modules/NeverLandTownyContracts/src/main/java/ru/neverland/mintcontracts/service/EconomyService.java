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
    public boolean canReserve(Town town, double amount) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        try { return amount <= 0 || TownyEconomyHandler.isActive() && ru.neverland.integration.TreasuryAccess.canSpend(town,"infrastructure",amount); }
        catch (RuntimeException exception) { return false; }
    } }
    public String format(double amount) { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {
        try { return TownyEconomyHandler.isActive() ? TownyEconomyHandler.getFormattedBalance(amount) : FALLBACK.format(amount); }
        catch (RuntimeException exception) { return FALLBACK.format(amount); }
    } }
    public String balance(Town town) { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {
        try { return town == null || !TownyEconomyHandler.isActive() ? format(0) : town.getAccount().getHoldingFormattedBalance(); }
        catch (RuntimeException exception) { return format(0); }
    } }
}
