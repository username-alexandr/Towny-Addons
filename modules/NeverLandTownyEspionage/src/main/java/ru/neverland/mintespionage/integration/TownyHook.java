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
    public Resident resident(Player player){return TownyAPI.getInstance().getResident(player);}
    public Resident resident(UUID id){return TownyAPI.getInstance().getResident(id);}
    public Town town(Player player){Resident resident=resident(player);return resident==null?null:resident.getTownOrNull();}
    public Town townOf(UUID playerId){Resident resident=resident(playerId);return resident==null?null:resident.getTownOrNull();}
    public Town town(UUID id){return TownyAPI.getInstance().getTown(id);}
    public Town town(String name){return TownyAPI.getInstance().getTown(name);}
    public Collection<Town> towns(){return TownyAPI.getInstance().getTowns();}
    public boolean isManager(Player player,Town town){
        Resident resident=resident(player);if(resident==null||town==null||!town.equals(resident.getTownOrNull()))return false;
        if(plugin.getConfig().getBoolean("management.mayor",true)&&town.isMayor(resident))return true;
        for(String rank:plugin.getConfig().getStringList("management.allowed-town-ranks"))if(resident.hasTownRank(rank))return true;
        return false;
    }
    public boolean register(String name,CommandExecutor executor){return TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN,name,executor);}
    public void unregister(String name){TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN,name);}
    public void notifyTown(Town town,String message){if(town==null)return;for(Resident resident:town.getResidents()){Player player=Bukkit.getPlayer(resident.getUUID());if(player!=null)player.sendMessage(message);}}
    public String mayor(Town town){return town==null||town.getMayor()==null?"неизвестен":town.getMayor().getName();}
    public String nation(Town town){
        Object nation=invoke(town,"getNationOrNull");if(nation==null)return "не состоит в нации";Object name=invoke(nation,"getName");return name==null?"неизвестна":name.toString();
    }
    public String relation(Town first,Town second){
        if(first==null||second==null)return "неизвестно";
        Object result=invoke(first,"isAlliedWith",new Class<?>[]{Town.class},second);if(Boolean.TRUE.equals(result))return "союзники";
        result=invoke(first,"hasAlly",new Class<?>[]{Town.class},second);if(Boolean.TRUE.equals(result))return "союзники";
        result=invoke(first,"isEnemy",new Class<?>[]{Town.class},second);if(Boolean.TRUE.equals(result))return "противники";
        result=invoke(first,"hasEnemy",new Class<?>[]{Town.class},second);if(Boolean.TRUE.equals(result))return "противники";
        return "нейтральные";
    }
    public List<String> diplomaticNames(Town town,String method){
        Object nation=invoke(town,"getNationOrNull");Object raw=invoke(nation,method);if(!(raw instanceof Collection<?> values))return List.of();
        List<String> names=new ArrayList<>();for(Object value:values){Object name=invoke(value,"getName");if(name!=null)names.add(name.toString());}return names;
    }
    public boolean flag(Town town,String method){return Boolean.TRUE.equals(invoke(town,method));}
    private Object invoke(Object source,String method){return invoke(source,method,new Class<?>[0]);}
    private Object invoke(Object source,String method,Class<?>[] types,Object...args){
        if(source==null)return null;try{Method found=source.getClass().getMethod(method,types);return found.invoke(source,args);}catch(ReflectiveOperationException|RuntimeException ignored){return null;}
    }
}
