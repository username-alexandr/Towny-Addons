package ru.neverland.minttrade.service;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.minttrade.model.ExportDefinition;
import ru.neverland.minttrade.util.ColorUtil;

import java.text.DecimalFormat;

public final class EconomyService {
    private final JavaPlugin plugin;
    private final DecimalFormat format = new DecimalFormat("#,##0.##");
    public EconomyService(JavaPlugin plugin) { this.plugin = plugin; }
    public boolean canWithdraw(Town town, double amount) {
        try { return town != null && ru.neverland.integration.TreasuryAccess.canSpend(town,"infrastructure",amount); } catch (RuntimeException exception) { return false; }
    }
    public boolean withdraw(Town town, double amount, ExportDefinition definition) {
        if (amount <= 0) return true;
        try { return town != null && ru.neverland.integration.TreasuryAccess.withdraw(town,"infrastructure","trade",amount,reason("economy.reserve-reason",definition)); }
        catch (RuntimeException exception) { return false; }
    }
    public boolean deposit(Town town, double amount, ExportDefinition definition, String path) {
        if (amount <= 0) return true;
        try { return town != null && ru.neverland.integration.TreasuryAccess.deposit(town,"infrastructure",path.equals("economy.tariff-reason")?"tariff":path.equals("economy.pending-reason")?"pending":"trade",path.equals("economy.refund-reason"),amount,reason(path,definition)); }
        catch (RuntimeException exception) { return false; }
    }
    public double balance(Town town) { try { return town == null ? 0 : town.getAccount().getHoldingBalance(); } catch (RuntimeException exception) { return 0; } }
    public String format(double amount) { return format.format(amount); }
    private String reason(String path, ExportDefinition definition) {
        return ColorUtil.strip(plugin.getConfig().getString(path, "Городская торговля")
                .replace("%export%", definition == null ? "экспорт" : ColorUtil.strip(definition.name())));
    }
}
