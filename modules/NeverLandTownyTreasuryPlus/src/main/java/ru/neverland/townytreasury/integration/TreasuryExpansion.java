package ru.neverland.townytreasury.integration;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import com.palmergames.bukkit.towny.TownyAPI;
import ru.neverland.townytreasury.service.TreasuryService;
import ru.neverland.townytreasury.model.*;
public final class TreasuryExpansion extends PlaceholderExpansion {
    private final TreasuryService service;private final String version;
    public TreasuryExpansion(TreasuryService service,String version){this.service=service;this.version=version;}
    @Override public String getIdentifier(){return "nlttreasury";}@Override public String getAuthor(){return "NeverLand";}@Override public String getVersion(){return version;}@Override public boolean persist(){return true;}
    @Override public String onRequest(OfflinePlayer player,String key){if(player==null)return "";var resident=TownyAPI.getInstance().getResident(player.getUniqueId());var town=resident==null?null:resident.getTownOrNull();var city=town==null?null:service.treasury(town.getUUID()).orElse(null);if(city==null)return "";if(key.equals("balance"))return Money.format(city.balance());if(key.equals("enabled"))return city.enabled()?"Да":"Нет";if(key.equals("status"))return service.fault()?"Учёт приостановлен":city.pending()!=null?"Нужна сверка":"Учёт работает";for(var b:Budget.values())if(b.id().equals(key))return Money.format(city.funds().get(b));var week=city.weeks().getOrDefault(Week.key(System.currentTimeMillis()),Week.empty());return switch(key){case "income"->Money.format(week.totalIncome());case "expenses"->Money.format(week.totalExpense());case "taxes"->Money.format(week.income().getOrDefault("tax",0L));default->null;};}
}
