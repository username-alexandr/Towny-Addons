package ru.neverland.mintespionage.integration;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class TownyHook {
    private final JavaPlugin plugin;
    public TownyHook(JavaPlugin plugin){this.plugin=plugin;}
    public Resident resident(Player player){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {return TownyAPI.getInstance().getResident(player);} }
    public Resident resident(UUID id){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {return TownyAPI.getInstance().getResident(id);} }
    public Town town(Player player){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {Resident resident=resident(player);return resident==null?null:resident.getTownOrNull();} }
    public Town townOf(UUID playerId){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {Resident resident=resident(playerId);return resident==null?null:resident.getTownOrNull();} }
    public Town town(UUID id){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {return TownyAPI.getInstance().getTown(id);} }
    public Town town(String name){ try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {return TownyAPI.getInstance().getTown(name);} }
    public Collection<Town> towns(){ try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {return TownyAPI.getInstance().getTowns();} }
    public boolean isManager(Player player,Town town){ try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {
        Resident resident=resident(player);if(resident==null||town==null||!town.equals(resident.getTownOrNull()))return false;
        boolean legacy=plugin.getConfig().getStringList("management.allowed-town-ranks").stream().anyMatch(resident::hasTownRank);
        if(town.isMayor(resident))return plugin.getConfig().getBoolean("management.mayor",true)||legacy;
        return ru.neverland.core.CouncilAccess.check(player,town.getUUID(),"espionage",legacy);
    } }
    public boolean register(String name,CommandExecutor executor){ try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) {return TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN,name,executor);} }
    public void unregister(String name){ try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) {TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN,name);} }
    public void notifyTown(Town town,String message){ try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) {if(town==null)return;for(Resident resident:town.getResidents()){Player player=Bukkit.getPlayer(resident.getUUID());if(player!=null)ru.neverland.core.SuiteScheduler.entity(plugin,player,()->player.sendMessage(message));}} }
    public String mayor(Town town){ try (var suiteGuard12 = ru.neverland.core.SuiteState.guard()) {return town==null||town.getMayor()==null?"неизвестен":town.getMayor().getName();} }
    public String nation(Town town){ try (var suiteGuard13 = ru.neverland.core.SuiteState.guard()) {
        Object nation=invoke(town,"getNationOrNull");if(nation==null)return "не состоит в нации";Object name=invoke(nation,"getName");return name==null?"неизвестна":name.toString();
    } }
    public String relation(Town first,Town second){ try (var suiteGuard14 = ru.neverland.core.SuiteState.guard()) {
        if(first==null||second==null)return "неизвестно";
        var diplomatic=ru.neverland.core.DiplomacyAccess.relations(first.getUUID(),second.getUUID());
        if(diplomatic.contains("UNAVAILABLE"))return "дипломатия недоступна";
        if(diplomatic.contains("VASSALAGE"))return "вассалитет";
        if(diplomatic.contains("ALLIANCE"))return "союзники";
        if(diplomatic.contains("NONAGGRESSION"))return "пакт о ненападении";
        if(diplomatic.contains("GUARANTEE"))return "гарантия независимости";
        if(diplomatic.contains("EMBARGO")||diplomatic.contains("SANCTIONS"))return "дипломатические ограничения";
        if(diplomatic.contains("TRADE"))return "торговый договор";
        Object result=invoke(first,"isAlliedWith",new Class<?>[]{Town.class},second);if(Boolean.TRUE.equals(result))return "союзники";
        result=invoke(first,"hasAlly",new Class<?>[]{Town.class},second);if(Boolean.TRUE.equals(result))return "союзники";
        result=invoke(first,"isEnemy",new Class<?>[]{Town.class},second);if(Boolean.TRUE.equals(result))return "противники";
        result=invoke(first,"hasEnemy",new Class<?>[]{Town.class},second);if(Boolean.TRUE.equals(result))return "противники";
        return "нейтральные";
    } }
    public List<String> diplomaticNames(Town town,String method){ try (var suiteGuard15 = ru.neverland.core.SuiteState.guard()) {
        Object nation=invoke(town,"getNationOrNull");Object raw=invoke(nation,method);if(!(raw instanceof Collection<?> values))return List.of();
        List<String> names=new ArrayList<>();for(Object value:values){Object name=invoke(value,"getName");if(name!=null)names.add(name.toString());}return names;
    } }
    public boolean flag(Town town,String method){ try (var suiteGuard16 = ru.neverland.core.SuiteState.guard()) {return Boolean.TRUE.equals(invoke(town,method));} }
    private Object invoke(Object source,String method){return invoke(source,method,new Class<?>[0]);}
    private Object invoke(Object source,String method,Class<?>[] types,Object...args){
        if(source==null)return null;try{Method found=source.getClass().getMethod(method,types);return found.invoke(source,args);}catch(ReflectiveOperationException|RuntimeException ignored){return null;}
    }
}
