package ru.neverland.townytreasury.service;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import java.util.*;
public final class Ui {
    private Ui(){}public static String color(String s){return ChatColor.translateAlternateColorCodes('&',s);}
    public static void tell(CommandSender sender,String s){sender.sendMessage(color("&8[&aNeverLand &8• &6Казна&8] &r"+s));}
    public static String source(String key){return switch(key){case "tax"->"Налоги";case "trade"->"Караваны: резерв / продажи";case "tariff"->"Транзитные пошлины";case "construction"->"Строительство";case "upkeep"->"Содержание зданий";case "contracts"->"Городские заказы: резерв";case "ideology"->"Развитие идеологий";case "espionage"->"Разведка";case "insurance"->"Страховой резерв";case "shop"->"Городские лавки";case "pending"->"Отложенные поступления";default->"Прочие операции";};}
    public static String resource(String key){return switch(key){case "wood"->"Древесина";case "stone"->"Камень";case "metal"->"Металл";case "food"->"Продовольствие";case "water"->"Вода";case "materials"->"Стройматериалы";case "knowledge"->"Знания";case "influence"->"Влияние";default->key;};}
}
