package ru.neverland.morstownstick.integration;

import com.palmergames.bukkit.towny.Towny;
import com.palmergames.bukkit.towny.TownyEconomyHandler;
import com.palmergames.bukkit.towny.command.TownCommand;
import com.palmergames.bukkit.towny.exceptions.TownyException;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.WorldCoord;
import com.palmergames.bukkit.towny.tasks.TownClaim;
import com.palmergames.bukkit.towny.utils.AreaSelectionUtil;
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
    private final ClaimBiomePreflight biomes;

    public TownyClaimAdapter(JavaPlugin plugin) {
        this.plugin = plugin;
        this.biomes = new ClaimBiomePreflight(plugin);
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
    public java.util.concurrent.CompletionStage<ClaimBiomePreflight.Verdict> checkBiomes(List<WorldCoord> requested) {
        return biomes.check(requested);
    }

    public List<WorldCoord> validateOnly(Player player,Town town,List<WorldCoord> requested,ClaimBiomePreflight.Verdict biomes)throws TownyException {
        invoke(catchRuined,player);invoke(catchBankrupt,town);
        List<WorldCoord> accepted=vetSelection(player,town,requested,biomes);
        invoke(vetAllowed,town,false,accepted);return List.copyOf(accepted);
    }

    @FunctionalInterface public interface FinalCheck {void check()throws TownyException;}
    public List<WorldCoord> validateAndCharge(Player player,Town town,List<WorldCoord> requested,ClaimBiomePreflight.Verdict biomes,FinalCheck beforePayment)throws TownyException {
        invoke(catchRuined, player);
        invoke(catchBankrupt, town);
        List<WorldCoord> accepted = vetSelection(player,town,requested,biomes);
        invoke(vetAllowed, town, false, accepted);
        invoke(firePreClaim, player, town, false, accepted);
        if(accepted.size()!=requested.size()||!new java.util.HashSet<>(accepted).equals(new java.util.HashSet<>(requested)))throw new IllegalStateException("Towny изменил состав плана; оплата не запрашивается");
        beforePayment.check();
        invoke(vetPayment, player, town, false, accepted);
        return new ArrayList<>(accepted);
    }

    @SuppressWarnings("unchecked")
    private List<WorldCoord> vetSelection(Player player,Town town,List<WorldCoord> requested,ClaimBiomePreflight.Verdict biomes)throws TownyException {
        if(!biomes.isCurrent())throw new IllegalStateException("Правила биомов Towny изменились во время проверки. Откройте план заново");
        if(!com.palmergames.bukkit.towny.TownySettings.isUnwantedBiomeClaimingEnabled()
                && !com.palmergames.bukkit.towny.TownySettings.isOceanClaimingBlocked())
            return (List<WorldCoord>)invoke(vetSelection,player,town,new ArrayList<>(requested));
        // Towny's proximity and wilderness filters are reused unchanged. The
        // biome filter was performed on each selected region by the preflight.
        List<WorldCoord> accepted=AreaSelectionUtil.filterOutTownOwnedBlocks(new ArrayList<>(requested));
        if(!biomes.allowed().containsAll(accepted))
            throw new IllegalStateException("Биом одного из участков запрещён правилами Towny; деньги не списаны");
        if(accepted.isEmpty())throw new IllegalStateException("Не осталось свободных участков для захвата");
        accepted=AreaSelectionUtil.filterInvalidProximityToHomeblock(accepted,town);
        if(accepted.isEmpty())throw new IllegalStateException("Участки слишком близко к центру другого города");
        accepted=AreaSelectionUtil.filterInvalidProximityTownBlocks(accepted,town);
        if(accepted.isEmpty())throw new IllegalStateException("Участки слишком близко к другому городу");
        return accepted;
    }

    public boolean canAfford(Town town, int count) throws TownyException { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        if (!TownyEconomyHandler.isActive()) return true;
        if (town.getAccount() == null) return false;
        double cost = count == 1 ? town.getTownBlockCost() : town.getTownBlockCostN(count);
        return town.getAccount().canPayFromHoldings(cost);
    } }

    public void claimAsync(Player player, Town town, List<WorldCoord> accepted, Consumer<List<WorldCoord>> callback) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
        List<WorldCoord> mutable = new ArrayList<>(accepted);
        // TownClaim's constructor reads player.getLocation(): caller is on the entity scheduler.
        TownClaim task = new TownClaim(Towny.getPlugin(), player, town, mutable, false, true, false);
        ru.neverland.core.SuiteScheduler.get().runTaskAsynchronously(plugin, () -> {
            task.run();
            ru.neverland.core.SuiteScheduler.entity(plugin, player, () -> callback.accept(List.copyOf(mutable)));
        });
    } }

    /** Construction belongs to the entity thread; execution follows Towny's native async contract. */
    public void claimObserved(Player player,Town town,List<WorldCoord> accepted,java.util.function.BiConsumer<List<WorldCoord>,Throwable> callback){
        List<WorldCoord> mutable=new ArrayList<>(accepted);
        TownClaim task=new TownClaim(Towny.getPlugin(),player,town,mutable,false,true,false);
        ru.neverland.core.SuiteScheduler.get().runTaskAsynchronously(plugin,()->{
            Throwable error=null;try{task.run();}catch(Throwable failure){error=failure;}
            final Throwable outcome=error;
            ru.neverland.core.SuiteScheduler.get().runTask(plugin,()->callback.accept(List.copyOf(mutable),outcome));
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
