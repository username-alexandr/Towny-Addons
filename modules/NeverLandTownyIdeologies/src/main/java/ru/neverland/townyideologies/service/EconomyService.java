package ru.neverland.townyideologies.service;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townyideologies.integration.ItemsAdderHook;

import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class EconomyService {
    public enum Result { SUCCESS, INSUFFICIENT, ERROR }

    private final JavaPlugin plugin;
    private final ItemsAdderHook itemsAdder;

    public EconomyService(JavaPlugin plugin, ItemsAdderHook itemsAdder) {
        this.plugin = plugin;
        this.itemsAdder = itemsAdder;
    }

    /** Kept for binary callers; charges must now have a durable reform receipt. */
    @Deprecated public Result withdraw(Player player,Town town,double amount,String reason) {
        plugin.getLogger().warning("Прямое списание идеологии отключено: требуется подтверждённая реформа.");
        return Result.ERROR;
    }

    public String currencyName() { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        return plugin.getConfig().getString("settings.economy.currency-name", "монет");
    } }

    public String format(double value) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
        String pattern = plugin.getConfig().getString("settings.economy.number-format", "#,##0.##");
        DecimalFormat format = new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.forLanguageTag("ru-RU")));
        return format.format(value);
    } }

}
