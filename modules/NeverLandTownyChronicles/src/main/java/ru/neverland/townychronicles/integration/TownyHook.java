package ru.neverland.townychronicles.integration;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.UUID;

public final class TownyHook {
    public Resident resident(Player player){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {return TownyAPI.getInstance().getResident(player);} }public Resident resident(UUID id){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {return TownyAPI.getInstance().getResident(id);} }
    public Town town(Player player){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {Resident resident=resident(player);return resident==null?null:resident.getTownOrNull();} }public Town townOf(UUID id){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {Resident resident=resident(id);return resident==null?null:resident.getTownOrNull();} }
    public Town town(UUID id){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {return TownyAPI.getInstance().getTown(id);} }public Town town(String name){ try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {return TownyAPI.getInstance().getTown(name);} }public Collection<Town> towns(){ try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {return TownyAPI.getInstance().getTowns();} }
    public boolean register(String name,CommandExecutor executor){ try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {return TownyCommandAddonAPI.addSubCommand(TownyCommandAddonAPI.CommandType.TOWN,name,executor);} }public void unregister(String name){ try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) {TownyCommandAddonAPI.removeSubCommand(TownyCommandAddonAPI.CommandType.TOWN,name);} }
    public java.util.OptionalDouble availableBalance(Town town){ try (var guard = ru.neverland.core.SuiteState.guard()) {
        if (town == null || !com.palmergames.bukkit.towny.TownyEconomyHandler.isActive()) return java.util.OptionalDouble.empty();
        try { double value=town.getAccount().getHoldingBalance();return Double.isFinite(value)&&value>=0?java.util.OptionalDouble.of(value):java.util.OptionalDouble.empty(); }
        catch(RuntimeException exception){return java.util.OptionalDouble.empty();}
    } }
    public double balance(Town town){return availableBalance(town).orElse(0);}
}
