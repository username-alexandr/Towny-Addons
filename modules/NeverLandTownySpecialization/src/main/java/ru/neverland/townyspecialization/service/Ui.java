package ru.neverland.townyspecialization.service;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
public final class Ui {
    private Ui(){}
    public static String color(String text){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {return ChatColor.translateAlternateColorCodes('&',text);} }
    public static void tell(CommandSender sender,String text){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {sender.sendMessage(color(ru.neverland.core.EditableMessages.decorate(Ui.class,"&8[&aNeverLand &8• &fСпециализация&8] &r",text)));} }
    public static String amount(long thousandths){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {return java.math.BigDecimal.valueOf(thousandths,3).stripTrailingZeros().toPlainString();} }
    public static String percent(double n){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {return java.math.BigDecimal.valueOf(n*100).stripTrailingZeros().toPlainString()+"%";} }
    public static Material icon(String name){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {var m=Material.matchMaterial(name);return m==null||!m.isItem()||m.isAir()?Material.REDSTONE:m;} }
}
