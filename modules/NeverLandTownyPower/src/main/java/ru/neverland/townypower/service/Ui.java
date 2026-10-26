package ru.neverland.townypower.service;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
public final class Ui {
    private Ui(){}
    public static String color(String text){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {return ChatColor.translateAlternateColorCodes('&',text);} }
    public static void tell(CommandSender sender,String text){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {sender.sendMessage(color("&8[&aNeverLand &8• &fЭнергия&8] &r"+text));} }
    public static Material icon(String name){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {var m=Material.matchMaterial(name);return m==null||!m.isItem()||m.isAir()?Material.REDSTONE:m;} }
}
