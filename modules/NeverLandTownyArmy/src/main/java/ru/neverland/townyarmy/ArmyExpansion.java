package ru.neverland.townyarmy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import com.palmergames.bukkit.towny.TownyAPI;
public final class ArmyExpansion extends PlaceholderExpansion {
    private final ArmyService service; private final String version;
    public ArmyExpansion(ArmyService service, String version) { this.service = service; this.version = version; }
    @Override public String getIdentifier() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return "townyarmy"; } }
    @Override public String getAuthor() { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { return "Alexander Sokolov"; } }
    @Override public String getVersion() { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { return version; } }
    @Override public boolean persist() { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) { return true; } }
    @Override public String onRequest(OfflinePlayer player, String key) { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {
        if (player == null || !ru.neverland.core.SuiteState.held() || !service.healthy()) return "—";
        try {
            var s = service.repository().state().soldiers().get(player.getUniqueId());
            if (key.equals("rank")) return s == null || !s.serving() ? "Нет" : service.settings().ranks().get(s.rank()).title();
            if (key.equals("unit")) return s == null || !s.serving() ? "Нет" : service.settings().units().get(s.unit()).title();
            if (key.equals("duty")) return service.onDuty(player.getUniqueId()) ? "Да" : "Нет";
            if (key.equals("training")) return s == null ? "0" : Integer.toString(s.training());
            var resident = TownyAPI.getInstance().getResident(player.getUniqueId()); var town = resident == null ? null : resident.getTownOrNull();
            if (town == null) return "0"; var g = service.garrison(town.getUUID()).orElseThrow();
            return switch (key) { case "active", "capacity", "reserve" -> g.get(key).toString(); case "readiness", "score" -> String.format(java.util.Locale.ROOT, "%.1f", g.get(key)); default -> null; };
        } catch (Exception | LinkageError e) { return "—"; }
    } }
}
