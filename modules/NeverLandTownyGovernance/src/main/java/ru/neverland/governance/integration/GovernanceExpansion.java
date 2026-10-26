package ru.neverland.governance.integration;

import com.palmergames.bukkit.towny.object.Town;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.neverland.governance.TownyGovernance;
import ru.neverland.governance.model.OfficeHolder;
import ru.neverland.governance.service.GovernanceService;

import java.util.List;
import java.util.Locale;

public final class GovernanceExpansion extends PlaceholderExpansion {
    private final TownyGovernance plugin; private final TownyHook towny; private final GovernanceService governance;
    public GovernanceExpansion(TownyGovernance plugin, TownyHook towny, GovernanceService governance) { this.plugin = plugin; this.towny = towny; this.governance = governance; }
    @Override public @NotNull String getIdentifier() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return "townygovernance"; } }
    @Override public @NotNull String getAuthor() { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { return "Alexander Sokolov"; } }
    @Override public @NotNull String getVersion() { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { return plugin.getPluginMeta().getVersion(); } }
    @Override public boolean persist() { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) { return true; } }
    @Override public @Nullable String onRequest(OfflinePlayer offline, @NotNull String params) { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {
        if (!(offline instanceof Player player)) return ""; Town town = towny.town(player); if (town == null) return "";
        String key = params.toLowerCase(Locale.ROOT);
        if (key.startsWith("has_law_")) return String.valueOf(governance.hasLaw(town.getUUID(), key.substring(8)));
        if (key.startsWith("office_")) {
            List<OfficeHolder> holders = governance.data(town).offices().getOrDefault(key.substring(7), List.of());
            return holders.isEmpty() ? "—" : String.join(", ", holders.stream().map(OfficeHolder::residentName).toList());
        }
        return switch (key) {
            case "town" -> town.getName(); case "active_laws" -> String.valueOf(governance.data(town).activeLaws().size());
            case "open_votes" -> String.valueOf(governance.open(town).size()); case "council_size" -> String.valueOf(governance.council(town).size());
            case "construction_cost" -> percent(governance.constructionCost(town.getUUID())); case "ideology_cost" -> percent(governance.ideologyCost(town.getUUID()));
            case "ideology_experience" -> percent(governance.ideologyExperience(town.getUUID())); case "tax" -> String.valueOf(town.getTaxes());
            case "tax_mode" -> town.isTaxPercentage() ? "Процент" : "Фиксированный"; default -> null;
        };
    } }
    private String percent(double value) { return Math.round(value * 100) + "%"; }
}
