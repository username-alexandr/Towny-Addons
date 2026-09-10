package ru.neverland.townyresearch.service;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
public final class Ui {
    private Ui(){}
    public static String color(String text){return ChatColor.translateAlternateColorCodes('&',text);}
    public static void tell(CommandSender sender,String text){sender.sendMessage(color("&8[&aNeverLand &8• &fИсследования&8] &r"+text));}
    public static String amount(long thousandths){return java.math.BigDecimal.valueOf(thousandths,3).stripTrailingZeros().toPlainString();}
    public static String percent(double n){return java.math.BigDecimal.valueOf(n*100).stripTrailingZeros().toPlainString()+"%";}
    public static Material icon(String name){var m=Material.matchMaterial(name);return m==null||!m.isItem()||m.isAir()?Material.REDSTONE:m;}
}
