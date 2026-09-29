package ru.neverland.townyresearch.service;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
public final class Ui {
    private Ui(){}
    public static String color(String text){ synchronized (ru.neverland.core.SuiteState.lock()) {return ChatColor.translateAlternateColorCodes('&',text);} }
    public static void tell(CommandSender sender,String text){ synchronized (ru.neverland.core.SuiteState.lock()) {sender.sendMessage(color("&8[&aNeverLand &8• &fИсследования&8] &r"+text));} }
    public static String amount(long thousandths){ synchronized (ru.neverland.core.SuiteState.lock()) {return java.math.BigDecimal.valueOf(thousandths,3).stripTrailingZeros().toPlainString();} }
    public static String percent(double n){ synchronized (ru.neverland.core.SuiteState.lock()) {return java.math.BigDecimal.valueOf(n*100).stripTrailingZeros().toPlainString()+"%";} }
    public static Material icon(String name){ synchronized (ru.neverland.core.SuiteState.lock()) {var m=Material.matchMaterial(name);return m==null||!m.isItem()||m.isAir()?Material.REDSTONE:m;} }
}
