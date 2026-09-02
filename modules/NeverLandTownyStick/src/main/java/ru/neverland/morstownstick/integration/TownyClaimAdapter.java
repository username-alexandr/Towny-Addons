package ru.neverland.morstownstick.integration;

import com.palmergames.bukkit.towny.Towny;
import com.palmergames.bukkit.towny.TownyEconomyHandler;
import com.palmergames.bukkit.towny.command.TownCommand;
import com.palmergames.bukkit.towny.exceptions.TownyException;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.WorldCoord;
import com.palmergames.bukkit.towny.tasks.TownClaim;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Calls Towny's own claim validators and TownClaim task. This deliberately avoids
 * direct database mutation, preserving Towny events, economy hooks and limits.
 */
public final class TownyClaimAdapter {
    private final JavaPlugin plugin;
    private final Method catchRuined;
    private final Method catchBankrupt;
    private final Method vetSelection;
    private final Method vetAllowed;
    private final Method firePreClaim;
    private final Method vetPayment;

    public TownyClaimAdapter(JavaPlugin plugin) {
        this.plugin = plugin;
        try {
            catchRuined = method("catchRuinedTown", Player.class);
            catchBankrupt = method("catchBankruptTownWithLand", Town.class);
            vetSelection = method("vetTownClaimSelection", Player.class, Town.class, List.class);
            vetAllowed = method("vetTownAllowedTheseClaims", Town.class, boolean.class, List.class);
            firePreClaim = method("fireTownPreClaimEventOrThrow", Player.class, Town.class, boolean.class, List.class);
            vetPayment = method("vetTheTownCanPayIfRequired", Player.class, Town.class, boolean.class, List.class);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Towny 0.103.2.0 claim API layout was not found", exception);
        }
    }

    @SuppressWarnings("unchecked")
    public List<WorldCoord> validateAndCharge(Player player, Town town, List<WorldCoord> requested) throws TownyException {
        invoke(catchRuined, player);
        invoke(catchBankrupt, town);
        List<WorldCoord> accepted = (List<WorldCoord>) invoke(vetSelection, player, town, new ArrayList<>(requested));
        invoke(vetAllowed, town, false, accepted);
        invoke(firePreClaim, player, town, false, accepted);
        invoke(vetPayment, player, town, false, accepted);
        return new ArrayList<>(accepted);
    }

    public boolean canAfford(Town town, int count) throws TownyException {
        if (!TownyEconomyHandler.isActive()) return true;
        if (town.getAccount() == null) return false;
        double cost = count == 1 ? town.getTownBlockCost() : town.getTownBlockCostN(count);
        return town.getAccount().canPayFromHoldings(cost);
    }

    public void claimAsync(Player player, Town town, List<WorldCoord> accepted, Consumer<List<WorldCoord>> callback) {
        List<WorldCoord> mutable = new ArrayList<>(accepted);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            new TownClaim(Towny.getPlugin(), player, town, mutable, false, true, false).run();
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(List.copyOf(mutable)));
        });
    }

    private static Method method(String name, Class<?>... parameters) throws ReflectiveOperationException {
        Method method = TownCommand.class.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    private static Object invoke(Method method, Object... arguments) throws TownyException {
        try {
            return method.invoke(null, arguments);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof TownyException townyException) throw townyException;
            throw new IllegalStateException("Towny validator failed", cause);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Towny validator is inaccessible", exception);
        }
    }
}
