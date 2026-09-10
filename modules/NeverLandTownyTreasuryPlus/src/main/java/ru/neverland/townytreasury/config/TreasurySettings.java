package ru.neverland.townytreasury.config;
import org.bukkit.configuration.ConfigurationSection;
import ru.neverland.townytreasury.model.*;
import java.util.*;
public record TreasurySettings(Map<Budget,Integer> shares,Map<String,Budget> projects,List<String> taxWords,boolean notifyMayor) {
    public TreasurySettings{shares=Budget.shares(shares);projects=Map.copyOf(projects);taxWords=List.copyOf(taxWords);}
    public static TreasurySettings load(ConfigurationSection c){var shares=new EnumMap<Budget,Integer>(Budget.class);for(var b:Budget.values()){Object n=c.get("income-shares."+b.id());if(!(n instanceof Number value)||value.doubleValue()!=value.intValue())throw new IllegalArgumentException("Неверная доля: "+b.id());shares.put(b,value.intValue());}var projects=new HashMap<String,Budget>();var section=c.getConfigurationSection("upkeep-categories");if(section==null)throw new IllegalArgumentException("Нет категорий обслуживания");for(String id:section.getKeys(false)){if(!id.matches("[a-z0-9_-]{1,64}"))throw new IllegalArgumentException("Неверный ID здания");projects.put(id,Budget.parse(section.getString(id)));}var words=c.getStringList("tax-reason-contains").stream().map(v->v.toLowerCase(Locale.ROOT)).toList();if(words.isEmpty()||words.stream().anyMatch(String::isBlank))throw new IllegalArgumentException("Неверные признаки налоговых операций");if(!(c.get("reports.notify-mayor") instanceof Boolean flag))throw new IllegalArgumentException("Неверное уведомление об отчёте");return new TreasurySettings(shares,projects,words,flag);}
    public Budget category(String value){return value.startsWith("project/")?projects.getOrDefault(value.substring(8),Budget.INFRASTRUCTURE):Budget.parse(value);}
}
