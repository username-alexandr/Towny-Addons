package ru.neverland.townyideologies.service;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townyideologies.data.DataStore;
import ru.neverland.townyideologies.integration.TownyHook;
import ru.neverland.townyideologies.model.IdeologyDefinition;
import ru.neverland.townyideologies.model.TownIdeology;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class IdeologyService {
    private final JavaPlugin plugin;
    private final TownyHook towny;
    private final DataStore data;
    private final IdeologyRegistry registry;
    private final EconomyService economy;
    private final Map<UUID, Object> locks = new ConcurrentHashMap<>();

    public IdeologyService(JavaPlugin plugin, TownyHook towny, DataStore data,
                           IdeologyRegistry registry, EconomyService economy) {
        this.plugin = plugin;
        this.towny = towny;
        this.data = data;
        this.registry = registry;
        this.economy = economy;
    }

    public Optional<TownIdeology> get(Player player) {
        Town town = towny.town(player);
        return town == null ? Optional.empty() : data.get(town.getUUID());
    }

    public Optional<TownIdeology> get(Town town) {
        return town == null ? Optional.empty() : data.get(town.getUUID());
    }

    public boolean active(Player player, String ideologyId, boolean checkScope) {
        Town town = towny.town(player);
        if (town == null) return false;
        TownIdeology current = data.get(town.getUUID()).orElse(null);
        if (current == null || !current.ideologyId().equalsIgnoreCase(ideologyId)) return false;
        if (!checkScope) return true;
        String scope = plugin.getConfig().getString("settings.bonus-scope." + ideologyId, "GLOBAL");
        Town territory = towny.townAt(player.getLocation());
        return !"TOWN".equalsIgnoreCase(scope) || (territory != null && town.getUUID().equals(territory.getUUID()));
    }

    public PurchaseResult purchase(Player player, String ideologyId) {
        IdeologyDefinition definition = registry.find(ideologyId).orElse(null);
        if (definition == null) return new PurchaseResult(PurchaseStatus.DEFINITION_MISSING, null, 0, 0.0D);
        if (!player.hasPermission("neverlandtownyideologies.select")) {
            return new PurchaseResult(PurchaseStatus.NO_PERMISSION, definition, 0, 0.0D);
        }
        Town town = towny.town(player);
        if (town == null) return new PurchaseResult(PurchaseStatus.NO_TOWN, definition, 0, 0.0D);
        if (!towny.isMayor(player, town)) return new PurchaseResult(PurchaseStatus.NOT_MAYOR, definition, 0, 0.0D);

        synchronized (locks.computeIfAbsent(town.getUUID(), ignored -> new Object())) {
            TownIdeology current = data.get(town.getUUID()).orElse(null);
            PurchaseStatus successStatus;
            int targetLevel;
            double price;
            long selectedAt = System.currentTimeMillis();

            if (current == null) {
                successStatus = PurchaseStatus.SELECTED;
                targetLevel = 1;
                price = definition.selectionPrice();
            } else if (current.ideologyId().equalsIgnoreCase(definition.id())) {
                if (!player.hasPermission("neverlandtownyideologies.upgrade")) {
                    return new PurchaseResult(PurchaseStatus.NO_PERMISSION, definition, current.level(), 0.0D);
                }
                if (current.level() >= IdeologyDefinition.MAX_LEVEL) {
                    return new PurchaseResult(PurchaseStatus.MAX_LEVEL, definition, current.level(), 0.0D);
                }
                successStatus = PurchaseStatus.UPGRADED;
                targetLevel = current.level() + 1;
                price = definition.priceForLevel(targetLevel);
                selectedAt = current.selectedAt();
            } else {
                if (!plugin.getConfig().getBoolean("settings.selection.change-allowed", false)) {
                    return new PurchaseResult(PurchaseStatus.OTHER_SELECTED, definition, current.level(), 0.0D);
                }
                successStatus = PurchaseStatus.CHANGED;
                targetLevel = 1;
                price = definition.selectionPrice();
            }

            if (price < 0.0D) return new PurchaseResult(PurchaseStatus.ECONOMY_ERROR, definition, targetLevel, price);
            String reason = plugin.getConfig().getString("settings.economy.withdraw-reason", "Идеология города")
                    .replace("{ideology}", definition.name()).replace("{level}", Integer.toString(targetLevel));
            EconomyService.Result paid = economy.withdraw(player, town, price, reason);
            if (paid == EconomyService.Result.INSUFFICIENT) {
                return new PurchaseResult(PurchaseStatus.INSUFFICIENT_FUNDS, definition, targetLevel, price);
            }
            if (paid != EconomyService.Result.SUCCESS) {
                return new PurchaseResult(PurchaseStatus.ECONOMY_ERROR, definition, targetLevel, price);
            }
            data.set(new TownIdeology(town.getUUID(), town.getName(), definition.id(), targetLevel, selectedAt));
            data.save();
            return new PurchaseResult(successStatus, definition, targetLevel, price);
        }
    }

    public void adminSet(Town town, String ideologyId, int level) {
        IdeologyDefinition definition = registry.find(ideologyId).orElseThrow();
        data.set(new TownIdeology(town.getUUID(), town.getName(), definition.id(),
                Math.max(1, Math.min(IdeologyDefinition.MAX_LEVEL, level)), System.currentTimeMillis()));
        data.save();
    }

    public void adminReset(Town town) {
        data.remove(town.getUUID());
        data.save();
    }
}
