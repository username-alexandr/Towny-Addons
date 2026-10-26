package ru.neverland.townyupkeep.service;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import ru.neverland.townyupkeep.model.Cost;
import java.util.*;
public final class Ui {
    private Ui(){}
    private static final Map<String,String> NAMES=Map.of("wood","Древесина","stone","Камень","metal","Металл","food","Продовольствие","water","Вода","materials","Стройматериалы","knowledge","Знания","influence","Влияние");
    public static String color(String text){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {return ChatColor.translateAlternateColorCodes('&',text);} }
    public static void tell(CommandSender sender,String text){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {sender.sendMessage(color("&8[&aNeverLand &8• &fОбслуживание&8] &r"+text));} }
    public static List<String> cost(Cost cost){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {var lines=new ArrayList<String>();lines.add("Деньги: "+Cost.format(cost.money(),2));cost.resources().entrySet().stream().sorted(Map.Entry.comparingByKey()).filter(e->e.getValue()>0).forEach(e->lines.add(NAMES.get(e.getKey())+": "+Cost.format(e.getValue(),3)));return lines;} }
    public static String resourceName(String id){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {return NAMES.getOrDefault(id,"Ресурс");} }
    public static Material icon(String name){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {var icon=Material.matchMaterial(name);return icon==null||!icon.isItem()||icon.isAir()?Material.BRICKS:icon;} }
}
