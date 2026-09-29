package ru.neverland.townypower.service;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
public final class Ui {
    private Ui(){}
    public static String color(String text){ synchronized (ru.neverland.core.SuiteState.lock()) {return ChatColor.translateAlternateColorCodes('&',text);} }
    public static void tell(CommandSender sender,String text){ synchronized (ru.neverland.core.SuiteState.lock()) {sender.sendMessage(color("&8[&aNeverLand &8• &fЭнергия&8] &r"+text));} }
    public static Material icon(String name){ synchronized (ru.neverland.core.SuiteState.lock()) {var m=Material.matchMaterial(name);return m==null||!m.isItem()||m.isAir()?Material.REDSTONE:m;} }
}
