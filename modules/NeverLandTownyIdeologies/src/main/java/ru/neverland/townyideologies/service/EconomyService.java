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

    public Result withdraw(Player player, Town town, double amount, String reason) {
        if (amount <= 0.0D) return Result.SUCCESS;
        String mode = plugin.getConfig().getString("settings.economy.mode", "TOWN_BANK").toUpperCase(Locale.ROOT);
        try {
            return switch (mode) {
                case "TOWN_BANK" -> withdrawTown(town, amount, reason);
                case "PLAYER_VAULT" -> withdrawVault(player, amount);
                case "ITEM" -> withdrawItem(player, amount);
                case "COMMAND" -> withdrawCommand(player, amount);
                default -> {
                    plugin.getLogger().severe("Неизвестный режим валюты: " + mode);
                    yield Result.ERROR;
                }
            };
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().severe("Ошибка списания валюты в режиме " + mode + ": " + exception.getMessage());
            return Result.ERROR;
        }
    }

    public String currencyName() {
        return plugin.getConfig().getString("settings.economy.currency-name", "монет");
    }

    public String format(double value) {
        String pattern = plugin.getConfig().getString("settings.economy.number-format", "#,##0.##");
        DecimalFormat format = new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.forLanguageTag("ru-RU")));
        return format.format(value);
    }

    private Result withdrawTown(Town town, double amount, String reason) {
        if (town == null || town.getAccount() == null) return Result.ERROR;
        if (!town.getAccount().canPayFromHoldings(amount)) return Result.INSUFFICIENT;
        return town.getAccount().withdraw(amount, reason) ? Result.SUCCESS : Result.ERROR;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Result withdrawVault(Player player, double amount) throws ReflectiveOperationException {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return Result.ERROR;
        Class economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
        RegisteredServiceProvider registration = Bukkit.getServicesManager().getRegistration(economyClass);
        if (registration == null || registration.getProvider() == null) return Result.ERROR;
        Object economy = registration.getProvider();
        Method has = economyClass.getMethod("has", OfflinePlayer.class, double.class);
        if (!(Boolean) has.invoke(economy, player, amount)) return Result.INSUFFICIENT;
        Object response = economyClass.getMethod("withdrawPlayer", OfflinePlayer.class, double.class)
                .invoke(economy, player, amount);
        boolean success = (Boolean) response.getClass().getMethod("transactionSuccess").invoke(response);
        return success ? Result.SUCCESS : Result.ERROR;
    }

    private Result withdrawItem(Player player, double amount) {
        int required = (int) Math.ceil(amount);
        ItemStack currency = itemsAdder.item(plugin.getConfig().getString("settings.economy.item.itemsadder-id", ""), 1);
        if (currency == null) {
            Material material = Material.matchMaterial(plugin.getConfig().getString("settings.economy.item.material", "GOLD_NUGGET"));
            if (material == null || material.isAir()) return Result.ERROR;
            currency = new ItemStack(material);
        }
        int found = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && stack.isSimilar(currency)) found += stack.getAmount();
        }
        if (found < required) return Result.INSUFFICIENT;
        int remaining = required;
        for (int slot = 0; slot < player.getInventory().getStorageContents().length && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack == null || !stack.isSimilar(currency)) continue;
            int remove = Math.min(stack.getAmount(), remaining);
            remaining -= remove;
            if (stack.getAmount() == remove) player.getInventory().setItem(slot, null);
            else stack.setAmount(stack.getAmount() - remove);
        }
        return Result.SUCCESS;
    }

    private Result withdrawCommand(Player player, double amount) throws ReflectiveOperationException {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return Result.ERROR;
        String placeholder = plugin.getConfig().getString("settings.economy.command.balance-placeholder", "");
        String command = plugin.getConfig().getString("settings.economy.command.withdraw-command", "");
        if (placeholder.isBlank() || command.isBlank()) return Result.ERROR;
        Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
        String rendered = (String) papi.getMethod("setPlaceholders", OfflinePlayer.class, String.class)
                .invoke(null, player, placeholder);
        double balance = parseNumber(rendered);
        if (balance < amount) return Result.INSUFFICIENT;
        String amountRaw = Double.toString(amount).replaceAll("\\.0$", "");
        String prepared = command.replace("{player}", player.getName()).replace("{amount}", format(amount))
                .replace("{amount_raw}", amountRaw);
        return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), prepared) ? Result.SUCCESS : Result.ERROR;
    }

    private double parseNumber(String text) {
        String cleaned = text == null ? "" : text.replace(" ", "").replace(',', '.')
                .replaceAll("[^0-9.\\-]", "");
        if (cleaned.isBlank() || cleaned.equals("-") || cleaned.equals(".")) return 0.0D;
        return Double.parseDouble(cleaned);
    }
}
